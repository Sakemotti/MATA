package com.mochisofts.mata.domain.repository

import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

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
    suspend fun setCompleted(todoId: String, logicalDate: LocalDate, completed: Boolean): Result<Unit>
    suspend fun deleteTodo(id: String): Result<Unit>
}

interface SettingsRepository {
    val showCompleted: Flow<Boolean>
    val todoListMode: Flow<String>
    val uncategorizedEndHour: Flow<Int>
    val weekStart: Flow<DayOfWeek>
    val theme: Flow<AppTheme>
    val notificationPermissionRequested: Flow<Boolean>
    suspend fun setShowCompleted(value: Boolean)
    suspend fun setTodoListMode(value: String)
    suspend fun setUncategorizedEndHour(value: Int)
    suspend fun setWeekStart(value: DayOfWeek)
    suspend fun setTheme(value: AppTheme)
    suspend fun setNotificationPermissionRequested(value: Boolean)
}

interface NotificationScheduler {
    val notificationCount: Flow<Int>
    fun systemState(): NotificationSystemState
    suspend fun reconcileTodo(todoId: String)
    suspend fun reconcileAll()
    suspend fun cancelTodo(todoId: String)
}
