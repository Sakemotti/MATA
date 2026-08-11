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
