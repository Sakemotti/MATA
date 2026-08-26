package com.mochisofts.mata.data.repository

import androidx.room.withTransaction
import com.mochisofts.mata.core.common.ValidationError
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.HistoryMonthExecutionRow
import com.mochisofts.mata.data.local.HistoryMonthPeriodRow
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultDao
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.domain.model.HistoryActionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryMonth
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.PeriodHistoryEntry
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.deadlineAt
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.occursOn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.model.summarizeHistoryDay
import com.mochisofts.mata.domain.repository.HistoryRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomHistoryRepository @Inject constructor(
    private val database: MataDatabase,
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val executionDao: TodoExecutionDao,
    private val periodResultDao: PeriodResultDao,
    private val todoRepository: TodoRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val clock: Clock,
) : HistoryRepository {
    private val operationMutex = Mutex()

    override fun observeMonth(startDate: LocalDate, endDate: LocalDate): Flow<HistoryMonth> =
        combine(
            executionDao.observeMonthRows(startDate.toString(), endDate.toString()),
            periodResultDao.observeMonthRows(startDate.toString(), endDate.toString()),
            todoRepository.observeOccurrences(LocalDate.now(clock)),
        ) { executions, periods, currentOccurrences ->
            buildMonth(startDate, endDate, executions, periods, currentOccurrences)
        }

    override fun observeDay(date: LocalDate): Flow<HistoryDay> {
        val currentData = combine(
            todoDao.observeAll(),
            categoryDao.observeAll(),
            settingsRepository.uncategorizedEndHour,
            settingsRepository.weekStart,
        ) { todos, categories, uncategorizedEndHour, weekStart ->
            CurrentHistoryData(todos, categories, uncategorizedEndHour, weekStart)
        }
        return combine(
            executionDao.observeForDate(date.toString()),
            periodResultDao.observeForDate(date.toString()),
            todoRepository.observeOccurrences(LocalDate.now(clock)),
            currentData,
        ) { executions, periods, currentOccurrences, current ->
            buildDay(date, executions, periods, currentOccurrences, current)
        }
    }

    override suspend fun undoAction(executionId: String): Result<HistoryActionUndoToken> =
        runCatching {
            operationMutex.withLock {
                var todoIdToReconcile: String? = null
                val token = database.withTransaction {
                    val execution = executionDao.findById(executionId)
                        ?: throw ValidationException(ValidationError.HISTORY_RECORD_NOT_FOUND)
                    val todo = validateUndoEligibility(execution)
                    executionDao.deleteById(execution.id)
                    if (todo.archivedAt == null) todoIdToReconcile = todo.id
                    execution.toUndoToken()
                }
                todoIdToReconcile?.let { runCatching { notificationScheduler.reconcileTodo(it) } }
                token
            }
        }

    override suspend fun restoreAction(token: HistoryActionUndoToken): Result<Unit> = runCatching {
        operationMutex.withLock {
            var todoIdToReconcile: String? = null
            database.withTransaction {
                if (executionDao.findById(token.id) != null) return@withTransaction
                if (executionDao.find(token.todoId, token.logicalDate.toString()) != null) {
                    throw ValidationException(ValidationError.TODO_ALREADY_ACTED)
                }
                val entity = token.toEntity()
                val todo = validateUndoEligibility(entity)
                executionDao.insert(entity)
                if (todo.archivedAt == null) todoIdToReconcile = todo.id
            }
            todoIdToReconcile?.let { runCatching { notificationScheduler.reconcileTodo(it) } }
        }
    }

    private fun buildMonth(
        startDate: LocalDate,
        endDate: LocalDate,
        executions: List<HistoryMonthExecutionRow>,
        periods: List<HistoryMonthPeriodRow>,
        currentOccurrences: List<TodoOccurrence>,
    ): HistoryMonth {
        val states = executions.groupBy(
            keySelector = { LocalDate.parse(it.logicalDate) },
            valueTransform = { TodoState.fromStoredValue(it.status) },
        ).mapValues { (_, values) -> values.toMutableList() }.toMutableMap()
        currentOccurrences.asSequence()
            .filter { it.state == TodoState.PENDING && !it.todo.recurrenceType.isCountBased }
            .filter { it.logicalDate in startDate..endDate }
            .forEach { occurrence ->
                states.getOrPut(occurrence.logicalDate, ::mutableListOf).add(TodoState.PENDING)
            }
        val achievements = periods.groupBy(
            keySelector = { LocalDate.parse(it.displayDate) },
            valueTransform = HistoryMonthPeriodRow::achieved,
        )
        val dates = states.keys + achievements.keys
        return HistoryMonth(
            summaries = dates.associateWith { date ->
                summarizeHistoryDay(date, states[date].orEmpty(), achievements[date].orEmpty())
            },
        )
    }

    private fun buildDay(
        date: LocalDate,
        executions: List<TodoExecutionEntity>,
        periods: List<PeriodResultEntity>,
        currentOccurrences: List<TodoOccurrence>,
        current: CurrentHistoryData,
    ): HistoryDay {
        val todos = current.todos.associateBy(TodoEntity::id)
        val categories = current.categories.associateBy(CategoryEntity::id)
        val now = ZonedDateTime.now(clock)
        val entries = buildList {
            executions.forEach { execution ->
                val todo = todos[execution.todoId]
                val category = todo?.categoryId?.let(categories::get)
                val snapshot = execution.toSnapshot(todo, category, current.uncategorizedEndHour)
                    ?: return@forEach
                add(
                    HistoryEntry(
                        id = execution.id,
                        todoId = execution.todoId,
                        logicalDate = LocalDate.parse(execution.logicalDate),
                        state = TodoState.fromStoredValue(execution.status),
                        actedAt = execution.actedAt,
                        finalizedAt = execution.finalizedAt,
                        snapshot = snapshot,
                        canUndoAction = TodoState.fromStoredValue(execution.status) in UNDOABLE_STATES &&
                            todo?.let {
                                isUndoEligible(
                                    execution = execution,
                                    todo = it,
                                    categories = categories,
                                    uncategorizedEndHour = current.uncategorizedEndHour,
                                    weekStart = current.weekStart,
                                    now = now,
                                )
                            } == true,
                    ),
                )
            }
            currentOccurrences.asSequence()
                .filter { it.logicalDate == date }
                .filter { it.state == TodoState.PENDING && !it.todo.recurrenceType.isCountBased }
                .forEach { occurrence ->
                    add(
                        occurrence.toPendingHistory(
                            uncategorizedEndHour = current.uncategorizedEndHour,
                            weekStart = current.weekStart,
                        ),
                    )
                }
        }.sortedWith(historyEntryComparator())

        val periodEntries = periods.mapNotNull { period ->
            val todo = todos[period.todoId]
            val category = todo?.categoryId?.let(categories::get)
            val snapshot = period.toSnapshot(todo, category, current.uncategorizedEndHour)
                ?: return@mapNotNull null
            PeriodHistoryEntry(
                id = period.id,
                todoId = period.todoId,
                periodType = com.mochisofts.mata.domain.model.RecurrenceType.fromStoredValue(
                    period.periodType,
                ),
                periodStart = LocalDate.parse(period.periodStart),
                periodEnd = LocalDate.parse(period.periodEnd),
                requiredCount = period.requiredCount,
                completedCount = period.completedCount,
                achieved = period.achieved,
                displayDate = LocalDate.parse(period.displayDate),
                finalizedAt = period.finalizedAt,
                snapshot = snapshot,
            )
        }
        val summary = summarizeHistoryDay(
            date = date,
            states = entries.map(HistoryEntry::state),
            periodAchievements = periodEntries.map(PeriodHistoryEntry::achieved),
        )
        return HistoryDay(date, summary, entries, periodEntries)
    }

    private suspend fun validateUndoEligibility(execution: TodoExecutionEntity): TodoEntity {
        if (TodoState.fromStoredValue(execution.status) !in UNDOABLE_STATES) {
            throw ValidationException(ValidationError.HISTORY_ACTION_NOT_UNDOABLE)
        }
        val todo = todoDao.findById(execution.todoId)
            ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
        val category = todo.categoryId?.let { categoryDao.findById(it) }
        val uncategorizedEndHour = settingsRepository.uncategorizedEndHour.first()
        val weekStart = settingsRepository.weekStart.first()
        if (!isUndoEligible(
                execution = execution,
                todo = todo,
                categories = category?.let { mapOf(it.id to it) }.orEmpty(),
                uncategorizedEndHour = uncategorizedEndHour,
                weekStart = weekStart,
                now = ZonedDateTime.now(clock),
            )
        ) {
            throw ValidationException(ValidationError.HISTORY_ACTION_NOT_UNDOABLE)
        }
        return todo
    }

    private fun isUndoEligible(
        execution: TodoExecutionEntity,
        todo: TodoEntity,
        categories: Map<String, CategoryEntity>,
        uncategorizedEndHour: Int,
        weekStart: DayOfWeek,
        now: ZonedDateTime,
    ): Boolean {
        val category = todo.categoryId?.let(categories::get)
        val endHour = category?.endHour ?: uncategorizedEndHour
        val currentLogicalDate = logicalDate(now, endHour)
        val executionDate = runCatching { LocalDate.parse(execution.logicalDate) }.getOrNull()
            ?: return false
        val domainTodo = todo.toDomain().copy(archivedAt = null)
        return if (domainTodo.recurrenceType.isCountBased) {
            domainTodo.recurrencePeriod(currentLogicalDate, weekStart)?.let { period ->
                executionDate in period.startDate..period.endDate
            } == true
        } else {
            executionDate == currentLogicalDate && domainTodo.occursOn(executionDate)
        }
    }

    private fun TodoExecutionEntity.toSnapshot(
        todo: TodoEntity?,
        category: CategoryEntity?,
        uncategorizedEndHour: Int,
    ): HistoryTodoSnapshot? = snapshotFromJson(snapshotJson)
        ?: todo?.toHistorySnapshot(category, uncategorizedEndHour)

    private fun PeriodResultEntity.toSnapshot(
        todo: TodoEntity?,
        category: CategoryEntity?,
        uncategorizedEndHour: Int,
    ): HistoryTodoSnapshot? = snapshotFromJson(snapshotJson)
        ?: todo?.toHistorySnapshot(category, uncategorizedEndHour)

    private fun snapshotFromJson(value: String): HistoryTodoSnapshot? {
        val snapshot = HistorySnapshotJson.decode(value) ?: return null
        val recurrence = runCatching {
            RecurrenceRuleJson.decode(
                snapshot.recurrenceType,
                snapshot.repeatParamsVersion,
                snapshot.repeatParamsJson,
            )
        }.getOrNull() ?: return null
        return HistoryTodoSnapshot(
            todoId = snapshot.todoId,
            definitionRevision = snapshot.definitionRevision,
            title = snapshot.title,
            description = snapshot.description,
            startDate = LocalDate.parse(snapshot.startDate),
            endDate = snapshot.endDate?.let(LocalDate::parse),
            recurrenceRule = recurrence,
            dueMinutes = snapshot.dueMinutes,
            notifications = snapshot.notifications.mapIndexed { index, notification ->
                TodoNotification(
                    id = "history-$index",
                    relation = NotificationRelation.fromStoredValue(notification.relation),
                    amount = notification.amount,
                    unit = NotificationUnit.fromStoredValue(notification.unit),
                )
            },
            categoryId = snapshot.categoryId,
            categoryName = snapshot.categoryName,
            categoryColorIndex = snapshot.categoryColorIndex,
            categoryIconName = snapshot.categoryIconName,
            categorySortOrder = snapshot.categorySortOrder,
            endHour = snapshot.endHour,
            weekStart = DayOfWeek.of(snapshot.weekStart),
            createdAt = snapshot.createdAt,
        )
    }

    private fun TodoEntity.toHistorySnapshot(
        category: CategoryEntity?,
        uncategorizedEndHour: Int,
    ): HistoryTodoSnapshot {
        val domain = toDomain()
        return HistoryTodoSnapshot(
            todoId = id,
            definitionRevision = definitionRevision,
            title = title,
            description = description,
            startDate = domain.startDate,
            endDate = domain.endDate,
            recurrenceRule = domain.recurrenceRule,
            dueMinutes = dueMinutes,
            notifications = emptyList(),
            categoryId = categoryId,
            categoryName = category?.name,
            categoryColorIndex = category?.colorIndex,
            categoryIconName = category?.iconName,
            categorySortOrder = category?.sortOrder,
            endHour = category?.endHour ?: uncategorizedEndHour,
            weekStart = DayOfWeek.MONDAY,
            createdAt = createdAt,
        )
    }

    private fun TodoOccurrence.toPendingHistory(
        uncategorizedEndHour: Int,
        weekStart: DayOfWeek,
    ) = HistoryEntry(
        id = null,
        todoId = todo.id,
        logicalDate = logicalDate,
        state = TodoState.PENDING,
        actedAt = null,
        finalizedAt = null,
        snapshot = HistoryTodoSnapshot(
            todoId = todo.id,
            definitionRevision = todo.definitionRevision,
            title = todo.title,
            description = todo.description,
            startDate = todo.startDate,
            endDate = todo.endDate,
            recurrenceRule = todo.recurrenceRule,
            dueMinutes = todo.dueMinutes,
            notifications = todo.notifications,
            categoryId = category?.id,
            categoryName = category?.name,
            categoryColorIndex = category?.colorIndex,
            categoryIconName = category?.iconName,
            categorySortOrder = category?.sortOrder,
            endHour = category?.endHour ?: uncategorizedEndHour,
            weekStart = weekStart,
            createdAt = todo.createdAt,
        ),
        canUndoAction = false,
    )

    private fun historyEntryComparator(): Comparator<HistoryEntry> =
        compareBy<HistoryEntry> { it.state.historySectionOrder }
            .thenBy { entry ->
                deadlineAt(
                    entry.logicalDate,
                    entry.snapshot.endHour,
                    entry.snapshot.dueMinutes,
                    clock.zone,
                ).toInstant()
            }
            .thenBy { it.snapshot.categorySortOrder ?: Int.MAX_VALUE }
            .thenBy { it.snapshot.createdAt }
            .thenBy { it.id.orEmpty() }

    private fun TodoExecutionEntity.toUndoToken() = HistoryActionUndoToken(
        id = id,
        operationId = operationId,
        todoId = todoId,
        logicalDate = LocalDate.parse(logicalDate),
        state = TodoState.fromStoredValue(status),
        actedAt = actedAt ?: finalizedAt,
        finalizedAt = finalizedAt,
        definitionRevision = definitionRevision,
        snapshotVersion = snapshotVersion,
        snapshotJson = snapshotJson,
    )

    private fun HistoryActionUndoToken.toEntity() = TodoExecutionEntity(
        id = id,
        operationId = operationId,
        todoId = todoId,
        logicalDate = logicalDate.toString(),
        status = state.code,
        actedAt = actedAt,
        finalizedAt = finalizedAt,
        definitionRevision = definitionRevision,
        snapshotVersion = snapshotVersion,
        snapshotJson = snapshotJson,
    )

    private data class CurrentHistoryData(
        val todos: List<TodoEntity>,
        val categories: List<CategoryEntity>,
        val uncategorizedEndHour: Int,
        val weekStart: DayOfWeek,
    )
}

private val TodoState.historySectionOrder: Int
    get() = when (this) {
        TodoState.MISSED,
        TodoState.PENDING,
        -> 0
        TodoState.SKIPPED -> 1
        TodoState.COMPLETED -> 2
    }

private val UNDOABLE_STATES = setOf(TodoState.COMPLETED, TodoState.SKIPPED)
