package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.deadlineAt
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.ui.todolist.TodoListDateSelection
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScheduleSpecCoverageTest {
    private lateinit var database: MataDatabase
    private lateinit var settings: MutableTestSettingsRepository
    private lateinit var todoRepository: RoomTodoRepository
    private lateinit var historyReconciler: RoomHistoryReconciler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = Clock.fixed(
            Instant.parse("2026-08-11T03:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = MutableTestSettingsRepository()
        todoRepository = RoomTodoRepository(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            notificationDao = database.todoNotificationDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            settingsRepository = settings,
            notificationScheduler = NoOpSettingsNotificationScheduler(),
            widgetUpdater = WidgetUpdater(context, DiagnosticLogger()),
            holidayRepository = TestHolidayRepository(),
            clock = clock,
        )
        historyReconciler = RoomHistoryReconciler(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            periodResultDao = database.periodResultDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            notificationDao = database.todoNotificationDao(),
            settingsRepository = settings,
            holidayRepository = TestHolidayRepository(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun day004_commonBoundaryProvidesOneLogicalDateAcrossAllCategories() = runBlocking {
        settings.setDayEndHour(13)
        val categories = listOf(category("work", 0), category("home", 1))
        categories.forEach { database.categoryDao().upsert(it) }
        listOf(
            dailyTodo("uncategorized", null),
            dailyTodo("work-todo", categories[0].id),
            dailyTodo("home-todo", categories[1].id),
        ).forEach { database.todoDao().upsert(it) }

        val occurrences = todoRepository.observeOccurrences(LocalDate.of(2026, 8, 11)).first()

        assertEquals(
            setOf("uncategorized", "work-todo", "home-todo"),
            occurrences.map { it.todo.id }.toSet(),
        )
        assertEquals(setOf(LocalDate.of(2026, 8, 10)), occurrences.map { it.logicalDate }.toSet())
    }

    @Test
    fun day005_boundaryChangeImmediatelyAppliesRegardlessOfCategoryId() = runBlocking {
        val category = category("category", 0)
        database.categoryDao().upsert(category)
        listOf(
            dailyTodo("uncategorized", null),
            dailyTodo("categorized", category.id),
        ).forEach { database.todoDao().upsert(it) }
        settings.setDayEndHour(13)
        val selectedDate = LocalDate.of(2026, 8, 11)

        assertEquals(
            setOf(LocalDate.of(2026, 8, 10)),
            todoRepository.observeOccurrences(selectedDate).first().map { it.logicalDate }.toSet(),
        )

        settings.setDayEndHour(11)

        assertEquals(
            setOf(LocalDate.of(2026, 8, 11)),
            todoRepository.observeOccurrences(selectedDate).first().map { it.logicalDate }.toSet(),
        )
    }

    @Test
    fun day009_overduePendingTodoRemainsUntilLogicalDayEnd() = runBlocking {
        settings.setDayEndHour(13)
        database.todoDao().upsert(
            dailyTodo(
                id = "overdue",
                categoryId = null,
                dueMinutes = 11 * 60,
            ),
        )

        val occurrence = todoRepository.observeOccurrences(LocalDate.of(2026, 8, 11))
            .first()
            .single()

        assertEquals(LocalDate.of(2026, 8, 10), occurrence.logicalDate)
        assertEquals("pending", occurrence.state.code)
        assertEquals(true, occurrence.isOverdue)
    }

    @Test
    fun day012_boundaryChangeMovesLiveScheduleButKeepsFinalizedSnapshots() = runBlocking {
        settings.setDayEndHour(13)
        val category = category("category", 0)
        database.categoryDao().upsert(category)
        val todos = listOf(
            dailyTodo("uncategorized", null),
            dailyTodo("categorized", category.id),
        )
        todos.forEach { todo ->
            database.todoDao().upsert(todo)
            database.todoExecutionDao().insert(
                completedExecution(
                    id = "history-${todo.id}",
                    todoId = todo.id,
                    logicalDate = "2026-08-09",
                    snapshotJson = HistorySnapshotJson.encode(
                        todo = todo,
                        category = todo.categoryId?.let { category },
                        notifications = emptyList(),
                        endHour = 13,
                        weekStart = DayOfWeek.MONDAY,
                        logicalDate = LocalDate.of(2026, 8, 9),
                    ),
                ),
            )
        }
        val selectedDate = LocalDate.of(2026, 8, 11)
        assertEquals(
            setOf(LocalDate.of(2026, 8, 10)),
            todoRepository.observeOccurrences(selectedDate).first().map { it.logicalDate }.toSet(),
        )
        val historyBefore = todos.associate { todo ->
            todo.id to database.todoExecutionDao().findForTodo(todo.id).single()
        }

        settings.setDayEndHour(11)

        assertEquals(
            setOf(LocalDate.of(2026, 8, 11)),
            todoRepository.observeOccurrences(selectedDate).first().map { it.logicalDate }.toSet(),
        )
        todos.forEach { todo ->
            val historyAfter = database.todoExecutionDao().findForTodo(todo.id).single()
            assertEquals(historyBefore.getValue(todo.id), historyAfter)
            val snapshot = requireNotNull(HistorySnapshotJson.decode(historyAfter.snapshotJson))
            assertEquals(13, snapshot.endHour)
            assertEquals("2026-08-09", snapshot.logicalDate)
            assertEquals(
                "2026-08-10T13:00+09:00[Asia/Tokyo]",
                deadlineAt(
                    LocalDate.parse(historyAfter.logicalDate),
                    snapshot.endHour,
                    snapshot.dueMinutes,
                    ZoneId.of("Asia/Tokyo"),
                ).toString(),
            )
        }
    }

    @Test
    fun day014_timePassageWaitsForRefreshOrDateSelectionEvent() = runBlocking {
        val firstDate = LocalDate.of(2026, 8, 11)
        val selection = TodoListDateSelection(firstDate, followsTodayInitially = true)
        val emissions = Channel<LocalDate>(Channel.UNLIMITED)
        val collection = launch {
            selection.requests.collect(emissions::send)
        }

        assertEquals(firstDate, emissions.receive())

        // Advancing the clock does not mutate this state: callers explicitly signal a refresh.
        yield()
        assertTrue(emissions.tryReceive().isFailure)

        selection.refresh(firstDate)
        assertEquals(firstDate, emissions.receive())

        val nextDate = firstDate.plusDays(1)
        selection.refresh(nextDate)
        assertEquals(nextDate, emissions.receive())

        selection.select(nextDate.plusDays(1))
        assertEquals(nextDate.plusDays(1), emissions.receive())
        collection.cancelAndJoin()
        emissions.close()
        Unit
    }

    @Test
    fun st010_weekStartChangeImmediatelyRecalculatesCurrentCountPeriod() = runBlocking {
        database.todoDao().upsert(
            todo(
                id = "current-weekly-count",
                startDate = "2026-08-12",
                requiredCount = 7,
            ),
        )
        database.todoExecutionDao().insert(
            completedExecution(
                id = "current-completion",
                todoId = "current-weekly-count",
                logicalDate = "2026-08-12",
                snapshotJson = "{\"definition\":\"current\"}",
            ),
        )

        val selectedDate = LocalDate.of(2026, 8, 13)
        val mondayProgress = todoRepository.observeOccurrences(selectedDate)
            .first()
            .single()
            .progress!!
        assertEquals(LocalDate.of(2026, 8, 12), mondayProgress.period.startDate)
        assertEquals(LocalDate.of(2026, 8, 16), mondayProgress.period.endDate)
        assertEquals(5, mondayProgress.period.requiredCount)
        assertEquals(1, mondayProgress.completedCount)
        assertEquals(4, mondayProgress.remainingCount)

        settings.setWeekStart(DayOfWeek.SUNDAY)

        val sundayProgress = todoRepository.observeOccurrences(selectedDate)
            .first()
            .single()
            .progress!!
        assertEquals(LocalDate.of(2026, 8, 12), sundayProgress.period.startDate)
        assertEquals(LocalDate.of(2026, 8, 15), sundayProgress.period.endDate)
        assertEquals(4, sundayProgress.period.requiredCount)
        assertEquals(1, sundayProgress.completedCount)
        assertEquals(3, sundayProgress.remainingCount)
    }

    @Test
    fun st011_settingChangesNeverRewriteFinalizedHistoryOrPeriodSnapshot() = runBlocking {
        database.todoDao().upsert(
            todo(
                id = "finalized-weekly-count",
                startDate = "2026-08-03",
                requiredCount = 3,
            ),
        )
        database.todoExecutionDao().insert(
            completedExecution(
                id = "finalized-completion",
                todoId = "finalized-weekly-count",
                logicalDate = "2026-08-04",
                snapshotJson = "{\"definition\":\"before-settings-change\"}",
            ),
        )
        historyReconciler.reconcile()
        val executionBefore = database.todoExecutionDao()
            .findForTodo("finalized-weekly-count")
            .single()
        val periodBefore = database.periodResultDao()
            .findForTodo("finalized-weekly-count")
            .single()
        assertEquals("2026-08-03", periodBefore.periodStart)
        assertEquals("2026-08-09", periodBefore.periodEnd)
        assertEquals(3, periodBefore.requiredCount)
        assertEquals(1, periodBefore.completedCount)

        settings.setDayEndHour(5)
        settings.setWeekStart(DayOfWeek.SUNDAY)
        historyReconciler.reconcile()

        assertEquals(
            listOf(executionBefore),
            database.todoExecutionDao().findForTodo("finalized-weekly-count"),
        )
        assertEquals(
            listOf(periodBefore),
            database.periodResultDao().findForTodo("finalized-weekly-count"),
        )
    }

    private fun todo(id: String, startDate: String, requiredCount: Int): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(
            RecurrenceRule(
                type = RecurrenceType.WEEKLY_COUNT,
                requiredCount = requiredCount,
            ),
        )
        return TodoEntity(
            id = id,
            title = id,
            description = "",
            categoryId = null,
            startDate = startDate,
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
    }

    private fun dailyTodo(
        id: String,
        categoryId: String?,
        dueMinutes: Int? = null,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(RecurrenceRule.daily())
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
            createdAt = 1,
            updatedAt = 1,
            archivedAt = null,
        )
    }

    private fun category(id: String, sortOrder: Int) = CategoryEntity(
        id = id,
        name = id,
        normalizedName = id,
        colorIndex = sortOrder,
        iconName = "Category",
        legacyEndHour = 0,
        sortOrder = sortOrder,
        createdAt = 1,
    )

    private fun completedExecution(
        id: String,
        todoId: String,
        logicalDate: String,
        snapshotJson: String,
    ) = TodoExecutionEntity(
        id = id,
        operationId = "operation-$id",
        todoId = todoId,
        logicalDate = logicalDate,
        status = "completed",
        actedAt = 1,
        finalizedAt = 1,
        definitionRevision = 1,
        snapshotVersion = 1,
        snapshotJson = snapshotJson,
    )
}

private class MutableTestSettingsRepository : SettingsRepository {
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

private class NoOpSettingsNotificationScheduler : NotificationScheduler {
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
