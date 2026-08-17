package com.mochisofts.mata.data.repository

import androidx.room.withTransaction
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultDao
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoRuntimeStateDao
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.logicalDayEnd
import com.mochisofts.mata.domain.model.occursOn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HistoryReconciliationResult
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomHistoryReconciler @Inject constructor(
    private val database: MataDatabase,
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val executionDao: TodoExecutionDao,
    private val periodResultDao: PeriodResultDao,
    private val runtimeStateDao: TodoRuntimeStateDao,
    private val notificationDao: TodoNotificationDao,
    private val settingsRepository: SettingsRepository,
    private val holidayRepository: HolidayRepository,
    private val clock: Clock,
) : HistoryReconciler {
    private val mutex = Mutex()

    override suspend fun reconcile(maxRecords: Int): HistoryReconciliationResult {
        require(maxRecords > 0)
        return mutex.withLock {
            val now = ZonedDateTime.now(clock)
            val weekStart = settingsRepository.weekStart.first()
            val uncategorizedEndHour = settingsRepository.uncategorizedEndHour.first()
            val holidays = holidayRepository.currentSnapshot().dates
            var generated = 0
            var hasMore = false

            for (todo in todoDao.findAllActive()) {
                if (generated >= maxRecords) {
                    hasMore = true
                    break
                }
                val category = todo.categoryId?.let { categoryDao.findById(it) }
                val endHour = category?.endHour ?: uncategorizedEndHour
                val currentLogicalDate = logicalDate(now, endHour)
                val outcome = if (todo.toDomain().recurrenceType.isCountBased) {
                    reconcileCountBased(
                        todo = todo,
                        endHour = endHour,
                        currentLogicalDate = currentLogicalDate,
                        weekStart = weekStart,
                        limit = maxRecords - generated,
                    )
                } else {
                    reconcileOccurrences(
                        todo = todo,
                        endHour = endHour,
                        currentLogicalDate = currentLogicalDate,
                        weekStart = weekStart,
                        limit = maxRecords - generated,
                        holidays = holidays,
                    )
                }
                generated += outcome.generated
                if (outcome.hasMore) {
                    hasMore = true
                    break
                }
            }
            HistoryReconciliationResult(generatedRecords = generated, hasMore = hasMore)
        }
    }

    private suspend fun reconcileOccurrences(
        todo: TodoEntity,
        endHour: Int,
        currentLogicalDate: LocalDate,
        weekStart: DayOfWeek,
        limit: Int,
        holidays: Set<LocalDate>,
    ): ReconcileOutcome = database.withTransaction {
        val runtime = runtimeStateDao.find(todo.id) ?: initialRuntime(todo)
        val domainTodo = todo.toDomain()
        val targetEnd = minOf(todo.endDate?.let(LocalDate::parse) ?: currentLogicalDate.minusDays(1), currentLogicalDate.minusDays(1))
        var cursor = runtime.lastFinalizedLogicalDate?.let(LocalDate::parse)
            ?: domainTodo.startDate.minusDays(1)
        var generated = 0
        var scanned = 0
        val notifications = notificationDao.findForTodo(todo.id)
        val category = todo.categoryId?.let { categoryDao.findById(it) }

        while (cursor.isBefore(targetEnd) && generated < limit && scanned < MAX_SCANNED_ITEMS) {
            val date = cursor.plusDays(1)
            if (domainTodo.occursOn(date, holidays) && executionDao.find(todo.id, date.toString()) == null) {
                val finalizedAt = logicalDayEnd(date, endHour, clock.zone).toInstant().toEpochMilli()
                executionDao.insert(
                    TodoExecutionEntity(
                        id = stableUuid("execution|${todo.id}|$date"),
                        operationId = stableUuid("reconcile|missed|${todo.id}|$date"),
                        todoId = todo.id,
                        logicalDate = date.toString(),
                        status = TodoState.MISSED.code,
                        actedAt = null,
                        finalizedAt = finalizedAt,
                        definitionRevision = todo.definitionRevision,
                        snapshotVersion = HistorySnapshotV1.VERSION,
                        snapshotJson = HistorySnapshotJson.encode(
                            todo = todo,
                            category = category,
                            notifications = notifications,
                            endHour = endHour,
                            weekStart = weekStart,
                            logicalDate = date,
                        ),
                    ),
                )
                generated++
            }
            cursor = date
            scanned++
        }

        val hasMore = cursor.isBefore(targetEnd)
        runtimeStateDao.upsert(
            runtime.copy(
                lastFinalizedLogicalDate = cursor.toString(),
                appliedDefinitionRevision = todo.definitionRevision,
                reconciliationCursorDate = cursor.toString().takeIf { hasMore },
                updatedAt = clock.millis(),
            ),
        )
        ReconcileOutcome(generated, hasMore)
    }

    private suspend fun reconcileCountBased(
        todo: TodoEntity,
        endHour: Int,
        currentLogicalDate: LocalDate,
        weekStart: DayOfWeek,
        limit: Int,
    ): ReconcileOutcome = database.withTransaction {
        val runtime = runtimeStateDao.find(todo.id) ?: initialRuntime(todo)
        val domainTodo = todo.toDomain()
        val previousPeriodEnd = when (domainTodo.recurrenceType) {
            RecurrenceType.WEEKLY_COUNT -> runtime.lastFinalizedWeeklyPeriodEnd
            RecurrenceType.MONTHLY_COUNT -> runtime.lastFinalizedMonthlyPeriodEnd
            else -> null
        }?.let(LocalDate::parse)
        var cursor = previousPeriodEnd?.plusDays(1) ?: domainTodo.startDate
        var finalizedPeriodEnd = previousPeriodEnd
        var generated = 0
        var scanned = 0
        val executions = executionDao.findForTodo(todo.id)
        val notifications = notificationDao.findForTodo(todo.id)
        val category = todo.categoryId?.let { categoryDao.findById(it) }

        while (generated < limit && scanned < MAX_SCANNED_ITEMS) {
            val period = domainTodo.recurrencePeriod(cursor, weekStart) ?: break
            if (!period.endDate.isBefore(currentLogicalDate)) break
            if (periodResultDao.find(todo.id, period.startDate.toString(), period.endDate.toString()) == null) {
                val completedCount = executions.count { execution ->
                    TodoState.fromStoredValue(execution.status) == TodoState.COMPLETED &&
                        LocalDate.parse(execution.logicalDate) in period.startDate..period.endDate
                }
                val finalizedAt = logicalDayEnd(period.endDate, endHour, clock.zone)
                    .toInstant()
                    .toEpochMilli()
                periodResultDao.insert(
                    PeriodResultEntity(
                        id = stableUuid(
                            "period|${todo.id}|${period.startDate}|${period.endDate}",
                        ),
                        todoId = todo.id,
                        periodType = domainTodo.recurrenceType.code,
                        periodStart = period.startDate.toString(),
                        periodEnd = period.endDate.toString(),
                        requiredCount = period.requiredCount,
                        completedCount = completedCount,
                        achieved = completedCount >= period.requiredCount,
                        displayDate = period.endDate.toString(),
                        finalizedAt = finalizedAt,
                        definitionRevision = todo.definitionRevision,
                        snapshotVersion = HistorySnapshotV1.VERSION,
                        snapshotJson = HistorySnapshotJson.encode(
                            todo = todo,
                            category = category,
                            notifications = notifications,
                            endHour = endHour,
                            weekStart = weekStart,
                            periodStart = period.startDate,
                            periodEnd = period.endDate,
                        ),
                    ),
                )
                generated++
            }
            finalizedPeriodEnd = period.endDate
            cursor = period.endDate.plusDays(1)
            scanned++
        }

        val nextPeriod = domainTodo.recurrencePeriod(cursor, weekStart)
        val hasMore = nextPeriod?.endDate?.isBefore(currentLogicalDate) == true &&
            (generated >= limit || scanned >= MAX_SCANNED_ITEMS)
        runtimeStateDao.upsert(
            runtime.copy(
                lastFinalizedWeeklyPeriodEnd = if (
                    domainTodo.recurrenceType == RecurrenceType.WEEKLY_COUNT
                ) {
                    finalizedPeriodEnd?.toString()
                } else {
                    runtime.lastFinalizedWeeklyPeriodEnd
                },
                lastFinalizedMonthlyPeriodEnd = if (
                    domainTodo.recurrenceType == RecurrenceType.MONTHLY_COUNT
                ) {
                    finalizedPeriodEnd?.toString()
                } else {
                    runtime.lastFinalizedMonthlyPeriodEnd
                },
                appliedDefinitionRevision = todo.definitionRevision,
                reconciliationCursorDate = cursor.toString().takeIf { hasMore },
                updatedAt = clock.millis(),
            ),
        )
        ReconcileOutcome(generated, hasMore)
    }

    private fun initialRuntime(todo: TodoEntity) = TodoRuntimeStateEntity(
        todoId = todo.id,
        lastFinalizedLogicalDate = null,
        lastFinalizedWeeklyPeriodEnd = null,
        lastFinalizedMonthlyPeriodEnd = null,
        appliedDefinitionRevision = todo.definitionRevision,
        reconciliationCursorDate = null,
        updatedAt = clock.millis(),
    )

    private data class ReconcileOutcome(val generated: Int, val hasMore: Boolean)

    private companion object {
        const val MAX_SCANNED_ITEMS = 1_000

        fun stableUuid(key: String): String = UUID.nameUUIDFromBytes(
            key.toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }
}
