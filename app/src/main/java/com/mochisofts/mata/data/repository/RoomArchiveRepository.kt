package com.mochisofts.mata.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import com.mochisofts.mata.core.common.ValidationError
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.data.local.ArchiveHistoryCountRow
import com.mochisofts.mata.data.local.ArchiveHistoryRow
import com.mochisofts.mata.data.local.ArchivedTodoRow
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.PeriodHistoryEntry
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.nextOccurrenceOnOrAfter
import com.mochisofts.mata.domain.model.validateNotifications
import com.mochisofts.mata.domain.repository.ArchiveRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomArchiveRepository @Inject constructor(
    private val todoDao: TodoDao,
    private val executionDao: TodoExecutionDao,
    private val notificationDao: TodoNotificationDao,
    private val todoRepository: TodoRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val clock: Clock,
) : ArchiveRepository {
    private val operationMutex = Mutex()

    override fun pagedTodos(
        query: String,
        sortOrder: ArchiveSortOrder,
    ): Flow<PagingData<ArchivedTodoItem>> {
        val normalizedQuery = query.trim()
        return Pager(PAGING_CONFIG) {
            when (sortOrder) {
                ArchiveSortOrder.NEWEST -> todoDao.pageArchivedNewest(normalizedQuery)
                ArchiveSortOrder.OLDEST -> todoDao.pageArchivedOldest(normalizedQuery)
                ArchiveSortOrder.TITLE -> todoDao.pageArchivedTitle(normalizedQuery)
            }
        }.flow.map { pagingData -> pagingData.map { row -> row.toDomain() } }
    }

    override fun observeTodo(todoId: String): Flow<ArchivedTodoItem?> = combine(
        todoDao.observeArchivedById(todoId),
        notificationDao.observeForTodo(todoId),
    ) { row, notifications ->
        row?.toDomain(notifications.map { notification -> notification.toDomain() })
    }

    override fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary> =
        executionDao.observeArchiveHistoryCount(todoId).map { count -> count.toDomain() }

    override fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>> =
        Pager(PAGING_CONFIG) { executionDao.pageArchiveHistory(todoId) }
            .flow
            .map { pagingData ->
                pagingData
                    .filter { row -> row.hasValidDomainValues() }
                    .map { row -> requireNotNull(row.toDomain()) }
            }

    override suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview> = runCatching {
        val entity = todoDao.findById(todoId)
            ?.takeIf { it.archivedAt != null }
            ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
        val notifications = notificationDao.findForTodo(todoId).map { notification ->
            notification.toDomain()
        }
        val uncategorizedEndHour = settingsRepository.uncategorizedEndHour.first()
        val row = todoDao.observeArchivedById(todoId).first()
            ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
        val endHour = row.categoryEndHour ?: uncategorizedEndHour
        val now = ZonedDateTime.now(clock)
        val restoredTodo = entity.toDomain(notifications).copy(archivedAt = null)
        val currentLogicalDate = logicalDate(now, endHour)
        val hasFutureOccurrence = restoredTodo.nextOccurrenceOnOrAfter(currentLogicalDate) != null
        val systemState = notificationScheduler.systemState()
        val invalidSettings = validateNotifications(notifications, restoredTodo.dueMinutes, endHour)
        val unavailableCount = when {
            notifications.isEmpty() -> 0
            !systemState.canPostNotifications -> notifications.size
            invalidSettings.isNotEmpty() -> notifications.size
            else -> 0
        }
        ArchiveActionPreview(
            todoId = todoId,
            title = entity.title,
            hasFutureOccurrence = hasFutureOccurrence,
            notificationSettingCount = notifications.size,
            unavailableNotificationCount = unavailableCount,
            historySummary = executionDao.observeArchiveHistoryCount(todoId).first().toDomain(),
        )
    }

    override suspend fun restore(todoId: String): Result<Unit> = operationMutex.withLock {
        runCatching {
            val entity = todoDao.findById(todoId)
                ?.takeIf { it.archivedAt != null }
                ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            todoRepository.restoreTodo(entity.id).getOrThrow()
        }
    }

    override suspend fun deletePermanently(todoId: String): Result<Unit> = operationMutex.withLock {
        runCatching {
            val entity = todoDao.findById(todoId)
                ?.takeIf { it.archivedAt != null }
                ?: throw ValidationException(ValidationError.TODO_NOT_FOUND)
            todoRepository.deleteTodo(entity.id).getOrThrow()
        }
    }

    private fun ArchivedTodoRow.toDomain(
        notifications: List<TodoNotification> = emptyList(),
    ): ArchivedTodoItem {
        val category = todo.categoryId?.let { categoryId ->
            categoryName?.let { name ->
                Category(
                    id = categoryId,
                    name = name,
                    colorIndex = categoryColorIndex ?: 15,
                    iconName = categoryIconName ?: DEFAULT_CATEGORY_ICON,
                    endHour = categoryEndHour ?: 0,
                    sortOrder = categorySortOrder ?: -1,
                )
            }
        }
        return ArchivedTodoItem(todo.toDomain(notifications), category)
    }

    private fun ArchiveHistoryCountRow.toDomain() = ArchiveHistorySummary(
        completedCount = completedCount,
        missedCount = missedCount,
        skippedCount = skippedCount,
        periodResultCount = periodResultCount,
    )

    private fun ArchiveHistoryRow.toDomain(): ArchivedHistoryItem? {
        val snapshot = HistorySnapshotJson.decodeDomain(snapshotJson) ?: currentDefinitionSnapshot()
        return if (rowType == ROW_EXECUTION) {
            ArchivedHistoryItem.Execution(
                HistoryEntry(
                    id = id,
                    todoId = todoId,
                    logicalDate = logicalDate?.let(LocalDate::parse) ?: return null,
                    state = status?.let(TodoState::fromStoredValue) ?: return null,
                    actedAt = actedAt,
                    finalizedAt = finalizedAt,
                    snapshot = snapshot,
                    canUndoAction = false,
                ),
            )
        } else {
            ArchivedHistoryItem.Period(
                PeriodHistoryEntry(
                    id = id,
                    todoId = todoId,
                    periodType = periodType?.let(RecurrenceType::fromStoredValue) ?: return null,
                    periodStart = periodStart?.let(LocalDate::parse) ?: return null,
                    periodEnd = periodEnd?.let(LocalDate::parse) ?: return null,
                    requiredCount = requiredCount ?: return null,
                    completedCount = completedCount ?: return null,
                    achieved = achieved ?: return null,
                    displayDate = displayDate?.let(LocalDate::parse) ?: return null,
                    finalizedAt = finalizedAt,
                    snapshot = snapshot,
                ),
            )
        }
    }

    private fun ArchiveHistoryRow.hasValidDomainValues(): Boolean = when (rowType) {
        ROW_EXECUTION -> logicalDate != null && status != null
        else -> periodType != null && periodStart != null && periodEnd != null &&
            requiredCount != null && completedCount != null && achieved != null && displayDate != null
    }

    private fun ArchiveHistoryRow.currentDefinitionSnapshot(): HistoryTodoSnapshot {
        val recurrence = RecurrenceRuleJson.decode(
            currentRecurrenceType,
            currentRepeatParamsVersion,
            currentRepeatParamsJson,
        )
        return HistoryTodoSnapshot(
            todoId = todoId,
            definitionRevision = currentDefinitionRevision,
            title = currentTitle,
            description = currentDescription,
            startDate = LocalDate.parse(currentStartDate),
            endDate = currentEndDate?.let(LocalDate::parse),
            recurrenceRule = recurrence,
            dueMinutes = currentDueMinutes,
            notifications = emptyList(),
            categoryId = currentCategoryId,
            categoryName = currentCategoryName,
            categoryColorIndex = currentCategoryColorIndex,
            categoryIconName = currentCategoryIconName,
            categorySortOrder = currentCategorySortOrder,
            endHour = currentCategoryEndHour ?: 0,
            weekStart = DayOfWeek.MONDAY,
            createdAt = currentCreatedAt,
        )
    }

    private fun TodoNotificationEntity.toDomain() = TodoNotification(
        id = id,
        relation = NotificationRelation.fromStoredValue(relation),
        amount = amount,
        unit = NotificationUnit.fromStoredValue(unit),
    )

    private companion object {
        const val ROW_EXECUTION = "execution"
        const val DEFAULT_CATEGORY_ICON = "Category"
        val PAGING_CONFIG = PagingConfig(
            pageSize = 50,
            initialLoadSize = 50,
            prefetchDistance = 10,
            enablePlaceholders = false,
        )
    }
}
