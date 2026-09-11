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
import org.junit.Assert.assertFalse
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
    fun ch010_countBasedActionsAppearOnlyOnCompletedAndSkippedLogicalDates() = runBlocking {
        val completedDate = LocalDate.of(2026, 8, 4)
        val skippedDate = LocalDate.of(2026, 8, 5)
        val untouchedDate = LocalDate.of(2026, 8, 6)
        val weekly = insertTodo(
            id = "weekly-count",
            rule = RecurrenceRule(
                type = com.mochisofts.mata.domain.model.RecurrenceType.WEEKLY_COUNT,
                requiredCount = 2,
            ),
        )
        val monthly = insertTodo(
            id = "monthly-count",
            rule = RecurrenceRule(
                type = com.mochisofts.mata.domain.model.RecurrenceType.MONTHLY_COUNT,
                requiredCount = 3,
            ),
        )
        insertExecution(weekly, completedDate, TodoState.COMPLETED)
        insertExecution(monthly, skippedDate, TodoState.SKIPPED)

        val month = repository.observeMonth(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
        ).first()

        assertEquals(1, month.summaries[completedDate]?.plannedCount)
        assertEquals(1, month.summaries[completedDate]?.completedCount)
        assertEquals(1, month.summaries[skippedDate]?.plannedCount)
        assertEquals(0, month.summaries[skippedDate]?.completedCount)
        assertNull(month.summaries[untouchedDate])
    }

    @Test
    fun ch011_historyBelongsToTargetLogicalDateRegardlessOfActionTimeOrLegacyCategoryBoundary() =
        runBlocking {
            val logicalDate = LocalDate.of(2026, 8, 10)
            val actionCalendarDate = LocalDate.of(2026, 8, 11)
            val midnightCategory = CategoryEntity(
                id = "category-midnight",
                name = "0時区切り",
                normalizedName = "0時区切り",
                colorIndex = 0,
                iconName = "Home",
                legacyEndHour = 0,
                sortOrder = 0,
                createdAt = 1,
            )
            val fourOClockCategory = CategoryEntity(
                id = "category-four",
                name = "4時区切り",
                normalizedName = "4時区切り",
                colorIndex = 1,
                iconName = "Gamepad",
                legacyEndHour = 4,
                sortOrder = 1,
                createdAt = 2,
            )
            database.categoryDao().upsert(midnightCategory)
            database.categoryDao().upsert(fourOClockCategory)
            val midnightTodo = insertTodo(
                id = "todo-midnight",
                rule = RecurrenceRule.daily(),
                categoryId = midnightCategory.id,
            )
            val fourOClockTodo = insertTodo(
                id = "todo-four",
                rule = RecurrenceRule.daily(),
                categoryId = fourOClockCategory.id,
            )
            val actedAt = Instant.parse("2026-08-11T03:00:00Z").toEpochMilli()
            insertExecution(
                todo = midnightTodo,
                logicalDate = logicalDate,
                state = TodoState.COMPLETED,
                actedAt = actedAt,
                category = midnightCategory,
            )
            insertExecution(
                todo = fourOClockTodo,
                logicalDate = logicalDate,
                state = TodoState.COMPLETED,
                actedAt = actedAt,
                category = fourOClockCategory,
            )

            val month = repository.observeMonth(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
            ).first()
            val day = repository.observeDay(logicalDate).first()

            assertEquals(2, month.summaries[logicalDate]?.completedCount)
            assertNull(month.summaries[actionCalendarDate])
            assertEquals(2, day.entries.size)
            assertTrue(day.entries.all { it.logicalDate == logicalDate })
        }

    @Test
    fun ch013_finalizedCountPeriodsExposeMarkerCountsAndAchievementOnPeriodEnd() = runBlocking {
        val weeklyEnd = LocalDate.of(2026, 8, 9)
        val monthlyEnd = LocalDate.of(2026, 8, 31)
        val weekly = insertTodo(
            id = "weekly-period",
            rule = RecurrenceRule(
                type = com.mochisofts.mata.domain.model.RecurrenceType.WEEKLY_COUNT,
                requiredCount = 2,
            ),
        )
        val monthly = insertTodo(
            id = "monthly-period",
            rule = RecurrenceRule(
                type = com.mochisofts.mata.domain.model.RecurrenceType.MONTHLY_COUNT,
                requiredCount = 3,
            ),
        )
        database.periodResultDao().insert(
            periodResult(
                id = "weekly-achieved",
                todo = weekly,
                periodStart = LocalDate.of(2026, 8, 3),
                periodEnd = weeklyEnd,
                requiredCount = 2,
                completedCount = 2,
                achieved = true,
            ),
        )
        database.periodResultDao().insert(
            periodResult(
                id = "monthly-unachieved",
                todo = monthly,
                periodStart = LocalDate.of(2026, 8, 1),
                periodEnd = monthlyEnd,
                requiredCount = 3,
                completedCount = 2,
                achieved = false,
            ),
        )

        val month = repository.observeMonth(
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
        ).first()
        val weeklyDay = repository.observeDay(weeklyEnd).first()
        val monthlyDay = repository.observeDay(monthlyEnd).first()

        assertTrue(requireNotNull(month.summaries[weeklyEnd]).hasAchievedPeriod)
        assertFalse(requireNotNull(month.summaries[weeklyEnd]).hasUnachievedPeriod)
        assertFalse(requireNotNull(month.summaries[monthlyEnd]).hasAchievedPeriod)
        assertTrue(requireNotNull(month.summaries[monthlyEnd]).hasUnachievedPeriod)
        with(weeklyDay.periodResults.single()) {
            assertEquals(weeklyEnd, displayDate)
            assertEquals(weeklyEnd, periodEnd)
            assertEquals(2, requiredCount)
            assertEquals(2, completedCount)
            assertTrue(achieved)
        }
        with(monthlyDay.periodResults.single()) {
            assertEquals(monthlyEnd, displayDate)
            assertEquals(monthlyEnd, periodEnd)
            assertEquals(3, requiredCount)
            assertEquals(2, completedCount)
            assertFalse(achieved)
        }
    }

    @Test
    fun ch017_historySectionsSortByDeadlineHistoricalCategoryAndCreationTime() = runBlocking {
        val logicalDate = LocalDate.of(2026, 8, 10)
        val firstCategory = CategoryEntity(
            id = "category-first",
            name = "先のカテゴリ",
            normalizedName = "先のカテゴリ",
            colorIndex = 0,
            iconName = "Home",
            sortOrder = 0,
            createdAt = 1,
        )
        val secondCategory = CategoryEntity(
            id = "category-second",
            name = "後のカテゴリ",
            normalizedName = "後のカテゴリ",
            colorIndex = 1,
            iconName = "Gamepad",
            sortOrder = 1,
            createdAt = 2,
        )
        database.categoryDao().upsert(firstCategory)
        database.categoryDao().upsert(secondCategory)

        val pending = insertTodo("pending", RecurrenceRule.daily(), dueMinutes = 60)
        val missed = insertTodo("missed", RecurrenceRule.daily(), dueMinutes = 120)
        val skipped = insertTodo("skipped", RecurrenceRule.daily(), dueMinutes = 1)
        val completedEarly = insertTodo(
            "completed-early",
            RecurrenceRule.daily(),
            categoryId = secondCategory.id,
            dueMinutes = 300,
            createdAt = 50,
        )
        val completedCreatedFirst = insertTodo(
            "completed-created-first",
            RecurrenceRule.daily(),
            categoryId = firstCategory.id,
            dueMinutes = 600,
            createdAt = 10,
        )
        val completedCreatedSecond = insertTodo(
            "completed-created-second",
            RecurrenceRule.daily(),
            categoryId = firstCategory.id,
            dueMinutes = 600,
            createdAt = 20,
        )
        val completedCategorySecond = insertTodo(
            "completed-category-second",
            RecurrenceRule.daily(),
            categoryId = secondCategory.id,
            dueMinutes = 600,
            createdAt = 1,
        )

        insertExecution(pending, logicalDate, TodoState.PENDING)
        insertExecution(missed, logicalDate, TodoState.MISSED)
        insertExecution(skipped, logicalDate, TodoState.SKIPPED)
        insertExecution(completedEarly, logicalDate, TodoState.COMPLETED, category = secondCategory)
        insertExecution(
            completedCreatedFirst,
            logicalDate,
            TodoState.COMPLETED,
            category = firstCategory,
        )
        insertExecution(
            completedCreatedSecond,
            logicalDate,
            TodoState.COMPLETED,
            category = firstCategory,
        )
        insertExecution(
            completedCategorySecond,
            logicalDate,
            TodoState.COMPLETED,
            category = secondCategory,
        )

        assertEquals(
            listOf(
                "pending",
                "missed",
                "skipped",
                "completed-early",
                "completed-created-first",
                "completed-created-second",
                "completed-category-second",
            ),
            repository.observeDay(logicalDate).first().entries.map { it.todoId },
        )
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

    private suspend fun insertTodo(
        id: String,
        rule: RecurrenceRule,
        categoryId: String? = null,
        dueMinutes: Int? = null,
        createdAt: Long = 1,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(rule)
        return TodoEntity(
            id = id,
            title = id,
            description = "",
            categoryId = categoryId,
            startDate = "2026-08-01",
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = dueMinutes,
            definitionRevision = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = null,
        ).also { database.todoDao().upsert(it) }
    }

    private suspend fun insertExecution(
        todo: TodoEntity,
        logicalDate: LocalDate,
        state: TodoState,
        actedAt: Long = 1,
        category: CategoryEntity? = null,
    ) {
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = "execution-${todo.id}",
                operationId = "operation-${todo.id}",
                todoId = todo.id,
                logicalDate = logicalDate.toString(),
                status = state.code,
                actedAt = actedAt,
                finalizedAt = actedAt,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = HistorySnapshotJson.encode(
                    todo = todo,
                    category = category,
                    notifications = emptyList(),
                    endHour = 0,
                    weekStart = DayOfWeek.MONDAY,
                    logicalDate = logicalDate,
                ),
            ),
        )
    }

    private fun periodResult(
        id: String,
        todo: TodoEntity,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        requiredCount: Int,
        completedCount: Int,
        achieved: Boolean,
    ) = PeriodResultEntity(
        id = id,
        todoId = todo.id,
        periodType = todo.recurrenceType,
        periodStart = periodStart.toString(),
        periodEnd = periodEnd.toString(),
        requiredCount = requiredCount,
        completedCount = completedCount,
        achieved = achieved,
        displayDate = periodEnd.toString(),
        finalizedAt = 1,
        definitionRevision = todo.definitionRevision,
        snapshotVersion = 1,
        snapshotJson = HistorySnapshotJson.encode(
            todo = todo,
            category = null,
            notifications = emptyList(),
            endHour = 0,
            weekStart = DayOfWeek.MONDAY,
            periodStart = periodStart,
            periodEnd = periodEnd,
        ),
    )
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
