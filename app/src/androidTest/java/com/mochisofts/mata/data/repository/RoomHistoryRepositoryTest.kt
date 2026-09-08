package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHistoryRepositoryTest {
    private lateinit var database: MataDatabase
    private lateinit var repository: RoomHistoryRepository
    private lateinit var workManager: WorkManager
    private val observedQueries = CopyOnWriteArrayList<ObservedQuery>()
    private val date = LocalDate.of(2026, 8, 11)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                RoomDatabase.QueryCallback { sqlQuery, bindArgs ->
                    observedQueries += ObservedQuery(sqlQuery, bindArgs.toList())
                },
                Executor { command -> command.run() },
            )
            .build()
        workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(WidgetUpdater.IMMEDIATE_UPDATE_WORK_NAME).result.get()
        repository = RoomHistoryRepository(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            periodResultDao = database.periodResultDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            todoRepository = EmptyTodoRepository(),
            settingsRepository = CalendarSettingsRepository(),
            notificationScheduler = NoOpNotificationScheduler(),
            widgetUpdater = WidgetUpdater(context, DiagnosticLogger()),
            clock = Clock.fixed(
                Instant.parse("2026-08-11T03:00:00Z"),
                ZoneId.of("Asia/Tokyo"),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sta003_currentActionsCanBeUndoneButPastActionsCannot() = runBlocking {
        val category = CategoryEntity(
            id = "category",
            name = "当時のカテゴリ",
            normalizedName = "当時のカテゴリ",
            colorIndex = 4,
            iconName = "Home",
            legacyEndHour = 0,
            sortOrder = 2,
            createdAt = 1,
        )
        database.categoryDao().upsert(category)
        val encoded = RecurrenceRuleJson.encode(RecurrenceRule.daily())
        val todo = TodoEntity(
            id = "todo",
            title = "当時のタイトル",
            description = "説明",
            categoryId = category.id,
            startDate = date.toString(),
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = null,
            definitionRevision = 1,
            createdAt = 10,
            updatedAt = 10,
            archivedAt = null,
        )
        database.todoDao().upsert(todo)
        val execution = TodoExecutionEntity(
            id = "execution",
            operationId = "operation",
            todoId = todo.id,
            logicalDate = date.toString(),
            status = "completed",
            actedAt = 20,
            finalizedAt = 20,
            definitionRevision = 1,
            snapshotVersion = 1,
            snapshotJson = HistorySnapshotJson.encode(
                todo = todo,
                category = category,
                notifications = emptyList(),
                endHour = 0,
                weekStart = DayOfWeek.MONDAY,
                logicalDate = date,
            ),
        )
        database.todoExecutionDao().insert(execution)
        database.categoryDao().delete(category)
        val uncategorizedTodo = database.todoDao().findById(todo.id)!!
        database.todoDao().upsert(uncategorizedTodo.copy(archivedAt = 30))

        val history = repository.observeDay(date).first()

        assertEquals("当時のカテゴリ", history.entries.single().snapshot.categoryName)
        assertEquals(4, history.entries.single().snapshot.categoryColorIndex)
        assertTrue(history.entries.single().canUndoAction)

        val token = repository.undoAction(execution.id).getOrThrow()
        assertEquals(TodoState.COMPLETED, token.state)
        assertNull(database.todoExecutionDao().findById(execution.id))
        val undoUpdateWorkIds = immediateUpdateWorkIds()
        assertTrue(undoUpdateWorkIds.isNotEmpty())

        repository.restoreAction(token).getOrThrow()
        assertNotNull(database.todoExecutionDao().findById(execution.id))
        assertTrue(immediateUpdateWorkIds().any { it !in undoUpdateWorkIds })

        database.todoExecutionDao().deleteById(execution.id)
        val skippedExecution = execution.copy(
            id = "skipped-execution",
            operationId = "skipped-operation",
            status = TodoState.SKIPPED.code,
        )
        database.todoExecutionDao().insert(skippedExecution)

        val skippedHistory = repository.observeDay(date).first()
        assertEquals(TodoState.SKIPPED, skippedHistory.entries.single().state)
        assertTrue(skippedHistory.entries.single().canUndoAction)

        val skippedToken = repository.undoAction(skippedExecution.id).getOrThrow()
        assertEquals(TodoState.SKIPPED, skippedToken.state)
        assertNull(database.todoExecutionDao().findById(skippedExecution.id))

        repository.restoreAction(skippedToken).getOrThrow()
        assertEquals(
            TodoState.SKIPPED.code,
            database.todoExecutionDao().findById(skippedExecution.id)?.status,
        )

        database.todoExecutionDao().deleteById(skippedExecution.id)
        val pastExecution = execution.copy(
            id = "past-execution",
            operationId = "past-operation",
            logicalDate = date.minusDays(1).toString(),
        )
        database.todoExecutionDao().insert(pastExecution)

        assertTrue(repository.undoAction(pastExecution.id).isFailure)
        assertNotNull(database.todoExecutionDao().findById(pastExecution.id))
    }

    @Test
    fun ch029_monthAndDayQueriesStayBoundedWithLargeHistory() = runBlocking {
        val encoded = RecurrenceRuleJson.encode(RecurrenceRule.daily())
        val todo = TodoEntity(
            id = "large-history",
            title = "large history",
            description = "",
            categoryId = null,
            startDate = "2023-01-01",
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = null,
            definitionRevision = 1,
            createdAt = 1,
            updatedAt = 1,
            archivedAt = null,
        )
        database.withTransaction {
            database.todoDao().upsert(todo)
            repeat(1_500) { index ->
                val logicalDate = LocalDate.of(2023, 1, 1).plusDays(index.toLong())
                database.todoExecutionDao().insert(
                    TodoExecutionEntity(
                        id = "bulk-execution-$index",
                        operationId = "bulk-operation-$index",
                        todoId = todo.id,
                        logicalDate = logicalDate.toString(),
                        status = TodoState.COMPLETED.code,
                        actedAt = index.toLong(),
                        finalizedAt = index.toLong(),
                        definitionRevision = 1,
                        snapshotVersion = 1,
                        snapshotJson = "{}",
                    ),
                )
            }
            repeat(200) { index ->
                val periodStart = LocalDate.of(2023, 1, 2).plusWeeks(index.toLong())
                database.periodResultDao().insert(
                    PeriodResultEntity(
                        id = "bulk-period-$index",
                        todoId = todo.id,
                        periodType = "weekly_count",
                        periodStart = periodStart.toString(),
                        periodEnd = periodStart.plusDays(6).toString(),
                        requiredCount = 3,
                        completedCount = 2,
                        achieved = false,
                        displayDate = periodStart.plusDays(6).toString(),
                        finalizedAt = index.toLong(),
                        definitionRevision = 1,
                        snapshotVersion = 1,
                        snapshotJson = "{}",
                    ),
                )
            }
        }

        val monthStart = LocalDate.of(2026, 8, 1)
        val monthEnd = LocalDate.of(2026, 8, 31)
        observedQueries.clear()
        val month = repository.observeMonth(monthStart, monthEnd).first()
        val monthHistoryQueries = observedHistoryQueries()

        assertEquals(31, month.summaries.size)
        assertTrue(month.summaries.keys.all { it in monthStart..monthEnd })
        assertTrue(monthHistoryQueries.any { query ->
            query.normalizedSql.contains("from todo_executions") &&
                query.normalizedSql.contains("logicaldate between ? and ?") &&
                query.arguments.map(Any?::toString) == listOf(monthStart.toString(), monthEnd.toString())
        })
        assertTrue(monthHistoryQueries.any { query ->
            query.normalizedSql.contains("from period_results") &&
                query.normalizedSql.contains("displaydate between ? and ?") &&
                query.arguments.map(Any?::toString) == listOf(monthStart.toString(), monthEnd.toString())
        })
        assertTrue(monthHistoryQueries.all { it.normalizedSql.contains("between ? and ?") })

        val selectedDate = LocalDate.of(2026, 8, 9)
        observedQueries.clear()
        val day = repository.observeDay(selectedDate).first()
        val dayHistoryQueries = observedHistoryQueries()

        assertEquals(1, day.entries.size)
        assertEquals(1, day.periodResults.size)
        assertTrue(dayHistoryQueries.any { query ->
            query.normalizedSql.contains("from todo_executions") &&
                query.normalizedSql.contains("logicaldate = ?") &&
                query.arguments.map(Any?::toString) == listOf(selectedDate.toString())
        })
        assertTrue(dayHistoryQueries.any { query ->
            query.normalizedSql.contains("from period_results") &&
                query.normalizedSql.contains("displaydate = ?") &&
                query.arguments.map(Any?::toString) == listOf(selectedDate.toString())
        })
        assertTrue(dayHistoryQueries.all { query ->
            query.normalizedSql.contains("logicaldate = ?") ||
                query.normalizedSql.contains("displaydate = ?")
        })
    }

    private fun observedHistoryQueries(): List<ObservedQuery> = observedQueries.filter { query ->
        query.normalizedSql.contains("from todo_executions") ||
            query.normalizedSql.contains("from period_results")
    }

    private fun immediateUpdateWorkIds() =
        workManager.getWorkInfosForUniqueWork(WidgetUpdater.IMMEDIATE_UPDATE_WORK_NAME)
            .get()
            .map { it.id }
}

private data class ObservedQuery(
    val sql: String,
    val arguments: List<Any?>,
) {
    val normalizedSql: String = sql.lowercase().replace(Regex("\\s+"), " ").trim()
}

private class CalendarSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val dayEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
}

private class EmptyTodoRepository : TodoRepository {
    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> =
        MutableStateFlow(emptyList())
    override fun observeTodos(): Flow<List<Todo>> = MutableStateFlow(emptyList())
    override suspend fun getTodo(id: String): Todo? = null
    override suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        notifications: List<TodoNotification>,
        dueDate: LocalDate?,
        carryOverEnabled: Boolean,
    ) = Result.success(id ?: "todo")
    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ) = Result.success(Unit)
    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ) = Result.success(Unit)
    override suspend fun archiveTodo(id: String) = Result.success(Unit)
    override suspend fun restoreTodo(id: String) = Result.success(Unit)
    override suspend fun deleteTodo(id: String) = Result.success(Unit)
}

private class NoOpNotificationScheduler : NotificationScheduler {
    override val notificationCount = MutableStateFlow(0)
    override fun systemState() = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    )
    override suspend fun reconcileTodo(todoId: String) = Unit
    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) = Unit
}
