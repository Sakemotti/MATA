package com.mochisofts.mata.domain.repository

import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.CompletionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryMonth
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategory(id: String): Category?
    suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
        endHour: Int,
    ): Result<String>
    suspend fun deleteCategory(id: String): Result<Unit>
}

interface TodoRepository {
    fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>>
    fun observeTodos(): Flow<List<Todo>>
    suspend fun getTodo(id: String): Todo?
    suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        notifications: List<TodoNotification> = emptyList(),
    ): Result<String>
    suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String = UUID.randomUUID().toString(),
    ): Result<Unit>
    suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String = UUID.randomUUID().toString(),
    ): Result<Unit>
    suspend fun undoCompletion(operationId: String): Result<Unit>
    suspend fun archiveTodo(id: String): Result<Unit>
    suspend fun restoreTodo(id: String): Result<Unit>
    suspend fun deleteTodo(id: String): Result<Unit>
}

interface SettingsRepository {
    val showCompleted: Flow<Boolean>
    val todoListMode: Flow<String>
    val uncategorizedEndHour: Flow<Int>
    val weekStart: Flow<DayOfWeek>
    val theme: Flow<AppTheme>
    val notificationPermissionRequested: Flow<Boolean>
    val archiveSortOrder: Flow<ArchiveSortOrder>
        get() = flowOf(ArchiveSortOrder.NEWEST)
    suspend fun setShowCompleted(value: Boolean)
    suspend fun setTodoListMode(value: String)
    suspend fun setUncategorizedEndHour(value: Int)
    suspend fun setWeekStart(value: DayOfWeek)
    suspend fun setTheme(value: AppTheme)
    suspend fun setNotificationPermissionRequested(value: Boolean)
    suspend fun setArchiveSortOrder(value: ArchiveSortOrder) = Unit
}

interface NotificationScheduler {
    val notificationCount: Flow<Int>
    fun systemState(): NotificationSystemState
    suspend fun reconcileTodo(todoId: String)
    suspend fun reconcileAll()
    suspend fun cancelTodo(todoId: String)
}

data class HistoryReconciliationResult(
    val generatedRecords: Int,
    val hasMore: Boolean,
)

interface HistoryReconciler {
    suspend fun reconcile(maxRecords: Int = 500): HistoryReconciliationResult
}

interface HistoryRepository {
    fun observeMonth(startDate: LocalDate, endDate: LocalDate): Flow<HistoryMonth>
    fun observeDay(date: LocalDate): Flow<HistoryDay>
    suspend fun undoCompletion(executionId: String): Result<CompletionUndoToken>
    suspend fun restoreCompletion(token: CompletionUndoToken): Result<Unit>
}

interface ArchiveRepository {
    fun pagedTodos(query: String, sortOrder: ArchiveSortOrder): Flow<PagingData<ArchivedTodoItem>>
    fun observeTodo(todoId: String): Flow<ArchivedTodoItem?>
    fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary>
    fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>>
    suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview>
    suspend fun restore(todoId: String): Result<Unit>
    suspend fun deletePermanently(todoId: String): Result<Unit>
}

interface HolidayRepository {
    val snapshot: Flow<HolidaySnapshot>
    suspend fun currentSnapshot(): HolidaySnapshot
    suspend fun needsRefresh(): Boolean
    suspend fun refresh(): HolidayRefreshResult
    suspend fun pendingNotificationGeneration(): Long?
    suspend fun markNotificationGenerationProcessed(generation: Long)
    suspend fun pendingWidgetGeneration(): Long?
    suspend fun markWidgetGenerationProcessed(generation: Long)
}
