package com.mochisofts.mata.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class HistoryMonthExecutionRow(
    val logicalDate: String,
    val status: String,
)

data class HistoryMonthPeriodRow(
    val displayDate: String,
    val achieved: Boolean,
)

data class ArchivedTodoRow(
    @Embedded val todo: TodoEntity,
    val categoryName: String?,
    val categoryColorIndex: Int?,
    val categoryIconName: String?,
    val categoryEndHour: Int?,
    val categorySortOrder: Int?,
)

data class ArchiveHistoryCountRow(
    val completedCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val periodResultCount: Int,
)

data class ArchiveHistoryRow(
    val rowType: String,
    val id: String,
    val todoId: String,
    val historyDate: String,
    val comparisonTime: Long,
    val logicalDate: String?,
    val status: String?,
    val actedAt: Long?,
    val finalizedAt: Long,
    val periodType: String?,
    val periodStart: String?,
    val periodEnd: String?,
    val requiredCount: Int?,
    val completedCount: Int?,
    val achieved: Boolean?,
    val displayDate: String?,
    val definitionRevision: Int,
    val snapshotVersion: Int,
    val snapshotJson: String,
    val currentTitle: String,
    val currentDescription: String,
    val currentCategoryId: String?,
    val currentStartDate: String,
    val currentEndDate: String?,
    val currentRecurrenceType: String,
    val currentRepeatParamsVersion: Int,
    val currentRepeatParamsJson: String,
    val currentDueMinutes: Int?,
    val currentDefinitionRevision: Int,
    val currentCreatedAt: Long,
    val currentCategoryName: String?,
    val currentCategoryColorIndex: Int?,
    val currentCategoryIconName: String?,
    val currentCategorySortOrder: Int?,
    val currentCategoryEndHour: Int?,
)

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun findAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun backupCount(): Int

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun backupPage(limit: Int, offset: Int): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories WHERE normalizedName = :normalizedName AND id != :excludedId LIMIT 1")
    suspend fun findDuplicate(normalizedName: String, excludedId: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
    suspend fun nextSortOrder(): Int

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("UPDATE categories SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackup(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAllForRestore()

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE archivedAt IS NULL ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun findById(id: String): TodoEntity?

    @Query(
        """
        SELECT todos.*,
            categories.name AS categoryName,
            categories.colorIndex AS categoryColorIndex,
            categories.iconName AS categoryIconName,
            categories.endHour AS categoryEndHour,
            categories.sortOrder AS categorySortOrder
        FROM todos
        LEFT JOIN categories ON categories.id = todos.categoryId
        WHERE todos.archivedAt IS NOT NULL AND todos.id = :id
        LIMIT 1
        """,
    )
    fun observeArchivedById(id: String): Flow<ArchivedTodoRow?>

    @Query(
        """
        SELECT todos.*,
            categories.name AS categoryName,
            categories.colorIndex AS categoryColorIndex,
            categories.iconName AS categoryIconName,
            categories.endHour AS categoryEndHour,
            categories.sortOrder AS categorySortOrder
        FROM todos
        LEFT JOIN categories ON categories.id = todos.categoryId
        WHERE todos.archivedAt IS NOT NULL
          AND (:query = '' OR instr(lower(todos.title), lower(:query)) > 0
            OR instr(lower(todos.description), lower(:query)) > 0
            OR instr(lower(COALESCE(categories.name, '')), lower(:query)) > 0)
        ORDER BY todos.archivedAt DESC, todos.title COLLATE LOCALIZED ASC, todos.id ASC
        """,
    )
    fun pageArchivedNewest(query: String): PagingSource<Int, ArchivedTodoRow>

    @Query(
        """
        SELECT todos.*,
            categories.name AS categoryName,
            categories.colorIndex AS categoryColorIndex,
            categories.iconName AS categoryIconName,
            categories.endHour AS categoryEndHour,
            categories.sortOrder AS categorySortOrder
        FROM todos
        LEFT JOIN categories ON categories.id = todos.categoryId
        WHERE todos.archivedAt IS NOT NULL
          AND (:query = '' OR instr(lower(todos.title), lower(:query)) > 0
            OR instr(lower(todos.description), lower(:query)) > 0
            OR instr(lower(COALESCE(categories.name, '')), lower(:query)) > 0)
        ORDER BY todos.archivedAt ASC, todos.title COLLATE LOCALIZED ASC, todos.id ASC
        """,
    )
    fun pageArchivedOldest(query: String): PagingSource<Int, ArchivedTodoRow>

    @Query(
        """
        SELECT todos.*,
            categories.name AS categoryName,
            categories.colorIndex AS categoryColorIndex,
            categories.iconName AS categoryIconName,
            categories.endHour AS categoryEndHour,
            categories.sortOrder AS categorySortOrder
        FROM todos
        LEFT JOIN categories ON categories.id = todos.categoryId
        WHERE todos.archivedAt IS NOT NULL
          AND (:query = '' OR instr(lower(todos.title), lower(:query)) > 0
            OR instr(lower(todos.description), lower(:query)) > 0
            OR instr(lower(COALESCE(categories.name, '')), lower(:query)) > 0)
        ORDER BY todos.title COLLATE LOCALIZED ASC, todos.archivedAt DESC, todos.id ASC
        """,
    )
    fun pageArchivedTitle(query: String): PagingSource<Int, ArchivedTodoRow>

    @Query("SELECT * FROM todos WHERE archivedAt IS NULL ORDER BY id ASC")
    suspend fun findAllActive(): List<TodoEntity>

    @Query("SELECT COUNT(*) FROM todos")
    suspend fun backupCount(): Int

    @Query("SELECT * FROM todos ORDER BY createdAt ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun backupPage(limit: Int, offset: Int): List<TodoEntity>

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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackup(todo: TodoEntity)

    @Query("DELETE FROM todos")
    suspend fun deleteAllForRestore()

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TodoExecutionDao {
    @Query("SELECT * FROM todo_executions")
    fun observeAll(): Flow<List<TodoExecutionEntity>>

    @Query("SELECT * FROM todo_executions")
    suspend fun findAll(): List<TodoExecutionEntity>

    @Query("SELECT COUNT(*) FROM todo_executions")
    suspend fun backupCount(): Int

    @Query(
        "SELECT * FROM todo_executions " +
            "ORDER BY logicalDate ASC, todoId ASC, id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun backupPage(limit: Int, offset: Int): List<TodoExecutionEntity>

    @Query(
        """
        SELECT * FROM todo_executions
        WHERE logicalDate BETWEEN :startDate AND :endDate
        ORDER BY logicalDate ASC, finalizedAt ASC, id ASC
        """,
    )
    suspend fun findBetween(startDate: String, endDate: String): List<TodoExecutionEntity>

    @Query(
        "SELECT logicalDate, status FROM todo_executions " +
            "WHERE logicalDate BETWEEN :startDate AND :endDate ORDER BY logicalDate ASC",
    )
    fun observeMonthRows(startDate: String, endDate: String): Flow<List<HistoryMonthExecutionRow>>

    @Query(
        "SELECT * FROM todo_executions WHERE logicalDate = :logicalDate " +
            "ORDER BY finalizedAt ASC, id ASC",
    )
    fun observeForDate(logicalDate: String): Flow<List<TodoExecutionEntity>>

    @Query("SELECT * FROM todo_executions WHERE todoId = :todoId")
    suspend fun findForTodo(todoId: String): List<TodoExecutionEntity>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM todo_executions WHERE todoId = :todoId AND status = 'completed')
                AS completedCount,
            (SELECT COUNT(*) FROM todo_executions WHERE todoId = :todoId AND status = 'missed')
                AS missedCount,
            (SELECT COUNT(*) FROM todo_executions WHERE todoId = :todoId AND status = 'skipped')
                AS skippedCount,
            (SELECT COUNT(*) FROM period_results WHERE todoId = :todoId)
                AS periodResultCount
        """,
    )
    fun observeArchiveHistoryCount(todoId: String): Flow<ArchiveHistoryCountRow>

    @Query(
        """
        WITH archive_history AS (
            SELECT 'execution' AS rowType,
                id, todoId, logicalDate AS historyDate,
                COALESCE(actedAt, finalizedAt) AS comparisonTime,
                logicalDate, status, actedAt, finalizedAt,
                NULL AS periodType, NULL AS periodStart, NULL AS periodEnd,
                NULL AS requiredCount, NULL AS completedCount, NULL AS achieved,
                NULL AS displayDate, definitionRevision, snapshotVersion, snapshotJson
            FROM todo_executions
            WHERE todoId = :todoId
            UNION ALL
            SELECT 'period' AS rowType,
                id, todoId, displayDate AS historyDate,
                finalizedAt AS comparisonTime,
                NULL AS logicalDate, NULL AS status, NULL AS actedAt, finalizedAt,
                periodType, periodStart, periodEnd,
                requiredCount, completedCount, achieved,
                displayDate, definitionRevision, snapshotVersion, snapshotJson
            FROM period_results
            WHERE todoId = :todoId
        )
        SELECT archive_history.*,
            todos.title AS currentTitle,
            todos.description AS currentDescription,
            todos.categoryId AS currentCategoryId,
            todos.startDate AS currentStartDate,
            todos.endDate AS currentEndDate,
            todos.recurrenceType AS currentRecurrenceType,
            todos.repeatParamsVersion AS currentRepeatParamsVersion,
            todos.repeatParamsJson AS currentRepeatParamsJson,
            todos.dueMinutes AS currentDueMinutes,
            todos.definitionRevision AS currentDefinitionRevision,
            todos.createdAt AS currentCreatedAt,
            categories.name AS currentCategoryName,
            categories.colorIndex AS currentCategoryColorIndex,
            categories.iconName AS currentCategoryIconName,
            categories.sortOrder AS currentCategorySortOrder,
            categories.endHour AS currentCategoryEndHour
        FROM archive_history
        INNER JOIN todos ON todos.id = archive_history.todoId
        LEFT JOIN categories ON categories.id = todos.categoryId
        ORDER BY historyDate DESC, comparisonTime DESC, rowType ASC, archive_history.id ASC
        """,
    )
    fun pageArchiveHistory(todoId: String): PagingSource<Int, ArchiveHistoryRow>

    @Query("SELECT * FROM todo_executions WHERE todoId = :todoId AND logicalDate = :logicalDate LIMIT 1")
    suspend fun find(todoId: String, logicalDate: String): TodoExecutionEntity?

    @Query("SELECT * FROM todo_executions WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): TodoExecutionEntity?

    @Query("SELECT * FROM todo_executions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TodoExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(execution: TodoExecutionEntity)

    @Query("DELETE FROM todo_executions")
    suspend fun deleteAllForRestore()

    @Query("DELETE FROM todo_executions WHERE todoId = :todoId AND logicalDate = :logicalDate")
    suspend fun delete(todoId: String, logicalDate: String)

    @Query("DELETE FROM todo_executions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PeriodResultDao {
    @Query("SELECT COUNT(*) FROM period_results")
    suspend fun backupCount(): Int

    @Query(
        "SELECT * FROM period_results " +
            "ORDER BY periodStart ASC, todoId ASC, id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun backupPage(limit: Int, offset: Int): List<PeriodResultEntity>

    @Query(
        "SELECT displayDate, achieved FROM period_results " +
            "WHERE displayDate BETWEEN :startDate AND :endDate ORDER BY displayDate ASC",
    )
    fun observeMonthRows(startDate: String, endDate: String): Flow<List<HistoryMonthPeriodRow>>

    @Query(
        "SELECT * FROM period_results WHERE displayDate = :displayDate " +
            "ORDER BY finalizedAt ASC, id ASC",
    )
    fun observeForDate(displayDate: String): Flow<List<PeriodResultEntity>>

    @Query("SELECT * FROM period_results WHERE todoId = :todoId ORDER BY periodStart ASC")
    suspend fun findForTodo(todoId: String): List<PeriodResultEntity>

    @Query(
        "SELECT * FROM period_results " +
            "WHERE todoId = :todoId AND periodStart = :periodStart AND periodEnd = :periodEnd LIMIT 1",
    )
    suspend fun find(todoId: String, periodStart: String, periodEnd: String): PeriodResultEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(result: PeriodResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackup(result: PeriodResultEntity)

    @Query("DELETE FROM period_results")
    suspend fun deleteAllForRestore()
}

@Dao
interface TodoRuntimeStateDao {
    @Query("SELECT COUNT(*) FROM todo_runtime_states")
    suspend fun backupCount(): Int

    @Query("SELECT * FROM todo_runtime_states ORDER BY todoId ASC LIMIT :limit OFFSET :offset")
    suspend fun backupPage(limit: Int, offset: Int): List<TodoRuntimeStateEntity>

    @Query("SELECT * FROM todo_runtime_states WHERE todoId = :todoId LIMIT 1")
    suspend fun find(todoId: String): TodoRuntimeStateEntity?

    @Upsert
    suspend fun upsert(state: TodoRuntimeStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackup(state: TodoRuntimeStateEntity)

    @Query("DELETE FROM todo_runtime_states")
    suspend fun deleteAllForRestore()
}

@Dao
interface TodoNotificationDao {
    @Query("SELECT * FROM todo_notifications WHERE todoId = :todoId ORDER BY sortOrder ASC")
    suspend fun findForTodo(todoId: String): List<TodoNotificationEntity>

    @Query("SELECT * FROM todo_notifications WHERE todoId = :todoId ORDER BY sortOrder ASC")
    fun observeForTodo(todoId: String): Flow<List<TodoNotificationEntity>>

    @Query("SELECT * FROM todo_notifications WHERE id = :id AND todoId = :todoId LIMIT 1")
    suspend fun find(todoId: String, id: String): TodoNotificationEntity?

    @Query("SELECT COUNT(*) FROM todo_notifications")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM todo_notifications")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM todo_notifications " +
            "ORDER BY todoId ASC, sortOrder ASC, id ASC LIMIT :limit OFFSET :offset",
    )
    suspend fun backupPage(limit: Int, offset: Int): List<TodoNotificationEntity>

    @Upsert
    suspend fun upsertAll(notifications: List<TodoNotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBackup(notification: TodoNotificationEntity)

    @Query("DELETE FROM todo_notifications")
    suspend fun deleteAllForRestore()

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

    @Query("DELETE FROM scheduled_notifications")
    suspend fun deleteAllForRestore()
}

@Dao
interface HolidayDao {
    @Query("SELECT * FROM holidays ORDER BY date ASC")
    fun observeAll(): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holidays ORDER BY date ASC")
    suspend fun findAll(): List<HolidayEntity>

    @Query("SELECT * FROM holidays WHERE year IN (:years) ORDER BY date ASC")
    suspend fun findForYears(years: List<Int>): List<HolidayEntity>

    @Query("DELETE FROM holidays WHERE year = :year")
    suspend fun deleteYear(year: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayEntity>)
}

@Dao
interface HolidayFetchStateDao {
    @Query("SELECT * FROM holiday_fetch_states ORDER BY year ASC")
    fun observeAll(): Flow<List<HolidayFetchStateEntity>>

    @Query("SELECT * FROM holiday_fetch_states ORDER BY year ASC")
    suspend fun findAll(): List<HolidayFetchStateEntity>

    @Query("SELECT * FROM holiday_fetch_states WHERE year IN (:years) ORDER BY year ASC")
    suspend fun findForYears(years: List<Int>): List<HolidayFetchStateEntity>

    @Upsert
    suspend fun upsertAll(states: List<HolidayFetchStateEntity>)
}

@Dao
interface HolidayUpdateStateDao {
    @Query("SELECT * FROM holiday_update_states WHERE id = 1 LIMIT 1")
    fun observeCurrent(): Flow<HolidayUpdateStateEntity?>

    @Query("SELECT * FROM holiday_update_states WHERE id = 1 LIMIT 1")
    suspend fun findCurrent(): HolidayUpdateStateEntity?

    @Upsert
    suspend fun upsert(state: HolidayUpdateStateEntity)
}

@Dao
interface WidgetInstanceStateDao {
    @Query("SELECT * FROM widget_instance_states ORDER BY appWidgetId ASC")
    suspend fun findAll(): List<WidgetInstanceStateEntity>

    @Query("SELECT * FROM widget_instance_states WHERE appWidgetId = :appWidgetId LIMIT 1")
    fun observe(appWidgetId: Int): Flow<WidgetInstanceStateEntity?>

    @Query("SELECT * FROM widget_instance_states WHERE appWidgetId = :appWidgetId LIMIT 1")
    suspend fun find(appWidgetId: Int): WidgetInstanceStateEntity?

    @Upsert
    suspend fun upsert(state: WidgetInstanceStateEntity)

    @Query("DELETE FROM widget_instance_states WHERE appWidgetId = :appWidgetId")
    suspend fun delete(appWidgetId: Int)

    @Query("DELETE FROM widget_instance_states")
    suspend fun deleteAll()
}
