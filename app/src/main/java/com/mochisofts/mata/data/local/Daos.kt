package com.mochisofts.mata.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE normalizedName = :normalizedName AND id != :excludedId LIMIT 1")
    suspend fun findDuplicate(normalizedName: String, excludedId: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE archivedAt IS NULL ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun findById(id: String): TodoEntity?

    @Query(
        """
        SELECT DISTINCT todos.* FROM todos
        INNER JOIN todo_notifications ON todo_notifications.todoId = todos.id
        WHERE todos.archivedAt IS NULL
        ORDER BY todos.id ASC
        """,
    )
    suspend fun findActiveWithNotifications(): List<TodoEntity>

    @Upsert
    suspend fun upsert(todo: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TodoExecutionDao {
    @Query("SELECT * FROM todo_executions")
    fun observeAll(): Flow<List<TodoExecutionEntity>>

    @Query("SELECT * FROM todo_executions WHERE todoId = :todoId")
    suspend fun findForTodo(todoId: String): List<TodoExecutionEntity>

    @Query("SELECT * FROM todo_executions WHERE todoId = :todoId AND logicalDate = :logicalDate LIMIT 1")
    suspend fun find(todoId: String, logicalDate: String): TodoExecutionEntity?

    @Upsert
    suspend fun upsert(execution: TodoExecutionEntity)

    @Query("DELETE FROM todo_executions WHERE todoId = :todoId AND logicalDate = :logicalDate")
    suspend fun delete(todoId: String, logicalDate: String)
}

@Dao
interface TodoNotificationDao {
    @Query("SELECT * FROM todo_notifications WHERE todoId = :todoId ORDER BY sortOrder ASC")
    suspend fun findForTodo(todoId: String): List<TodoNotificationEntity>

    @Query("SELECT * FROM todo_notifications WHERE id = :id AND todoId = :todoId LIMIT 1")
    suspend fun find(todoId: String, id: String): TodoNotificationEntity?

    @Query("SELECT COUNT(*) FROM todo_notifications")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM todo_notifications")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(notifications: List<TodoNotificationEntity>)

    @Query("DELETE FROM todo_notifications WHERE todoId = :todoId")
    suspend fun deleteForTodo(todoId: String)
}

@Dao
interface ScheduledNotificationDao {
    @Query("SELECT * FROM scheduled_notifications WHERE candidateKey = :candidateKey LIMIT 1")
    suspend fun find(candidateKey: String): ScheduledNotificationEntity?

    @Query("SELECT * FROM scheduled_notifications WHERE todoId = :todoId")
    suspend fun findForTodo(todoId: String): List<ScheduledNotificationEntity>

    @Query("SELECT DISTINCT todoId FROM scheduled_notifications")
    suspend fun findTodoIds(): List<String>

    @Query("SELECT COALESCE(MAX(requestCode), 9999) FROM scheduled_notifications")
    suspend fun maxRequestCode(): Int

    @Upsert
    suspend fun upsert(notification: ScheduledNotificationEntity)

    @Query("DELETE FROM scheduled_notifications WHERE candidateKey = :candidateKey")
    suspend fun delete(candidateKey: String)

    @Query("DELETE FROM scheduled_notifications WHERE todoId = :todoId")
    suspend fun deleteForTodo(todoId: String)
}
