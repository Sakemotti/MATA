package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.widget.WidgetSourceData
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.data.widget.buildWidgetDisplayModel
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.HolidayYearState
import com.mochisofts.mata.domain.model.HolidayYearStatus
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.RecurrenceDayFilter
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.nextNotificationCandidate
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTodoRecurrenceSpecCoverageTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private lateinit var database: MataDatabase
    private lateinit var clock: MutableRecurrenceClock
    private lateinit var settings: RecurrenceTestSettingsRepository
    private lateinit var holidays: TestHolidayRepository
    private lateinit var todoRepository: RoomTodoRepository
    private lateinit var widgetUpdater: WidgetUpdater

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableRecurrenceClock(LocalDate.of(2026, 8, 10), zone)
        settings = RecurrenceTestSettingsRepository()
        holidays = TestHolidayRepository()
        widgetUpdater = WidgetUpdater(context, DiagnosticLogger())
        todoRepository = RoomTodoRepository(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            notificationDao = database.todoNotificationDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            settingsRepository = settings,
            notificationScheduler = NoOpRecurrenceNotificationScheduler(),
            widgetUpdater = widgetUpdater,
            holidayRepository = holidays,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rpt005_failedHolidayFetchUsesProvisionalWeekdaysAndDoesNotBlockSave() = runBlocking {
        holidays.snapshot.value = holidaySnapshot(HolidayYearStatus.FAILED_WITHOUT_CACHE)

        val result = todoRepository.saveTodo(
            id = null,
            title = "provisional-weekday",
            description = "",
            categoryId = null,
            startDate = clock.date,
            endDate = null,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKDAYS),
            dueMinutes = null,
            notifications = emptyList(),
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "provisional-weekday",
            todoRepository.observeOccurrences(clock.date).first().single().todo.title,
        )
        assertEquals(HolidayYearStatus.FAILED_WITHOUT_CACHE, holidays.snapshot.value.statusFor(2026))
    }

    @Test
    fun rpt006_holidayRecoveryRecalculatesOpenDatesButPreservesPastExecution() = runBlocking {
        val todo = todoEntity("holiday-recovery", RecurrenceRule(RecurrenceType.WEEKDAYS))
        database.todoDao().upsert(todo)
        val pastDate = LocalDate.of(2026, 8, 7)
        val pastExecution = execution("past", todo.id, pastDate, TodoState.COMPLETED)
        database.todoExecutionDao().insert(pastExecution)
        holidays.snapshot.value = holidaySnapshot(HolidayYearStatus.FAILED_WITHOUT_CACHE)
        assertEquals(todo.id, todoRepository.observeOccurrences(clock.date).first().single().todo.id)

        val newlyKnownHolidays = setOf(pastDate, clock.date, clock.date.plusDays(1))
        holidays.snapshot.value = holidaySnapshot(
            status = HolidayYearStatus.AVAILABLE_CURRENT,
            dates = newlyKnownHolidays,
            generation = 2,
        )

        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        assertTrue(todoRepository.observeOccurrences(clock.date.plusDays(1)).first().isEmpty())
        assertEquals(
            pastExecution,
            database.todoExecutionDao().find(todo.id, pastDate.toString()),
        )
        assertEquals(
            TodoState.COMPLETED,
            todoRepository.observeOccurrences(pastDate).first().single().state,
        )
    }

    @Test
    fun rpt019_differentOperationsCannotCompleteCountTodoTwiceOnSameLogicalDate() = runBlocking {
        val todo = todoEntity("single-completion", weeklyCount(requiredCount = 2))
        database.todoDao().upsert(todo)

        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "operation-1").getOrThrow()
        val duplicate = todoRepository.setCompleted(
            todo.id,
            clock.date,
            true,
            operationId = "operation-2",
        )

        assertTrue(duplicate.isFailure)
        assertEquals(1, database.todoExecutionDao().findForTodo(todo.id).size)
        assertEquals(
            1,
            todoRepository.observeOccurrences(clock.date).first().single().progress?.completedCount,
        )
    }

    @Test
    fun rpt020_unachievedCountTodoAppearsDailyWithoutPendingHistoryRows() = runBlocking {
        val todo = todoEntity("daily-until-achieved", weeklyCount(requiredCount = 3))
        database.todoDao().upsert(todo)

        repeat(4) { offset ->
            clock.setDate(LocalDate.of(2026, 8, 10).plusDays(offset.toLong()))
            val occurrence = todoRepository.observeOccurrences(clock.date).first().single()
            assertEquals(TodoState.PENDING, occurrence.state)
            assertEquals(0, occurrence.progress?.completedCount)
            assertEquals(3, occurrence.progress?.remainingCount)
        }
        assertTrue(database.todoExecutionDao().findForTodo(todo.id).isEmpty())
    }

    @Test
    fun rpt021_achievedCountTodoStaysHiddenUntilNextPeriod() = runBlocking {
        val todo = todoEntity("achieved", weeklyCount(requiredCount = 2))
        database.todoDao().upsert(todo)
        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "first").getOrThrow()
        clock.setDate(clock.date.plusDays(1))
        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "second").getOrThrow()

        val immediatelyVisible = todoRepository.observeOccurrences(clock.date).first()
            .filter { it.state != TodoState.COMPLETED && it.state != TodoState.SKIPPED }
        assertTrue(immediatelyVisible.isEmpty())
        clock.setDate(LocalDate.of(2026, 8, 12))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())

        clock.setDate(LocalDate.of(2026, 8, 17))
        val nextPeriod = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(0, nextPeriod.progress?.completedCount)
        assertEquals(2, nextPeriod.progress?.remainingCount)
    }

    @Test
    fun rpt022_skipHidesOnlyTodayAndDoesNotReduceRequiredCount() = runBlocking {
        val todo = todoEntity("skipped", weeklyCount(requiredCount = 2))
        database.todoDao().upsert(todo)
        todoRepository.setSkipped(todo.id, clock.date, true, operationId = "skip").getOrThrow()

        val skipped = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(TodoState.SKIPPED, skipped.state)
        assertTrue(listOf(skipped).filter { it.state != TodoState.SKIPPED }.isEmpty())
        assertEquals(0, skipped.progress?.completedCount)
        assertEquals(2, skipped.progress?.remainingCount)

        clock.setDate(clock.date.plusDays(1))
        val tomorrow = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(TodoState.PENDING, tomorrow.state)
        assertEquals(2, tomorrow.progress?.remainingCount)
    }

    @Test
    fun rpt023_completionsOnDifferentDaysCreateSeparateHistoryRows() = runBlocking {
        val todo = todoEntity("separate-history", weeklyCount(requiredCount = 3))
        database.todoDao().upsert(todo)
        val firstDate = clock.date
        todoRepository.setCompleted(todo.id, firstDate, true, operationId = "day-one").getOrThrow()
        clock.setDate(firstDate.plusDays(1))
        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "day-two").getOrThrow()

        val rows = database.todoExecutionDao().findForTodo(todo.id)
        assertEquals(2, rows.size)
        assertEquals(setOf(firstDate.toString(), clock.date.toString()), rows.map { it.logicalDate }.toSet())
        assertTrue(rows.all { TodoState.fromStoredValue(it.status) == TodoState.COMPLETED })
    }

    @Test
    fun rpt025_undoInCurrentPeriodRecalculatesProgressAndTodayVisibility() = runBlocking {
        val todo = todoEntity("undo-current-period", weeklyCount(requiredCount = 1))
        database.todoDao().upsert(todo)
        val completionDate = clock.date
        todoRepository.setCompleted(todo.id, completionDate, true, operationId = "complete").getOrThrow()
        val execution = database.todoExecutionDao().findForTodo(todo.id).single()
        clock.setDate(completionDate.plusDays(1))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())

        historyRepository().undoAction(execution.id).getOrThrow()

        assertTrue(database.todoExecutionDao().findForTodo(todo.id).isEmpty())
        val occurrence = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(TodoState.PENDING, occurrence.state)
        assertEquals(0, occurrence.progress?.completedCount)
        assertEquals(1, occurrence.progress?.remainingCount)
    }

    @Test
    fun rpt026_weekStartChangeRecalculatesCurrentPeriodAndKeepsFinalizedPeriod() = runBlocking {
        clock.setDate(LocalDate.of(2026, 8, 13))
        val todo = todoEntity(
            id = "week-start-change",
            rule = weeklyCount(requiredCount = 3),
            startDate = LocalDate.of(2026, 8, 3),
        )
        database.todoDao().upsert(todo)
        database.todoExecutionDao().insert(
            execution("past", todo.id, LocalDate.of(2026, 8, 4), TodoState.COMPLETED),
        )
        historyReconciler().reconcile()
        val pastPeriod = database.periodResultDao().findForTodo(todo.id).single()
        todoRepository.setCompleted(
            todo.id,
            LocalDate.of(2026, 8, 13),
            true,
            operationId = "current-period",
        ).getOrThrow()
        val mondayPeriod = todoRepository.observeOccurrences(clock.date).first().single().progress!!
        assertEquals(LocalDate.of(2026, 8, 10), mondayPeriod.period.startDate)
        assertEquals(LocalDate.of(2026, 8, 16), mondayPeriod.period.endDate)

        settings.setWeekStart(DayOfWeek.SUNDAY)
        historyReconciler().reconcile()

        val sundayPeriod = todoRepository.observeOccurrences(clock.date).first().single().progress!!
        assertEquals(LocalDate.of(2026, 8, 9), sundayPeriod.period.startDate)
        assertEquals(LocalDate.of(2026, 8, 15), sundayPeriod.period.endDate)
        assertEquals(1, sundayPeriod.completedCount)
        assertEquals(listOf(pastPeriod), database.periodResultDao().findForTodo(todo.id))
    }

    @Test
    fun rpt030_twoWeekWeekendHolidayRuleIsSharedByListNotificationAndWidget() = runBlocking {
        val holiday = LocalDate.of(2026, 8, 14)
        val rule = weeklyCount(
            requiredCount = 1,
            periodWeeks = 2,
            dayFilter = RecurrenceDayFilter.WEEKENDS_HOLIDAYS,
        )
        val todo = todoEntity(
            id = "weekend-holiday",
            rule = rule,
            dueMinutes = 18 * 60,
        )
        database.todoDao().upsert(todo)
        holidays.snapshot.value = holidaySnapshot(
            HolidayYearStatus.AVAILABLE_CURRENT,
            dates = setOf(holiday),
        )

        clock.setDate(LocalDate.of(2026, 8, 13))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        clock.setDate(holiday)
        assertEquals(todo.id, todoRepository.observeOccurrences(clock.date).first().single().todo.id)
        val domainTodo = requireNotNull(todoRepository.getTodo(todo.id))
        val notification = TodoNotification(
            id = "at-deadline",
            relation = NotificationRelation.AT,
            amount = 0,
            unit = NotificationUnit.MINUTE,
        )
        val candidate = nextNotificationCandidate(
            todo = domainTodo,
            notification = notification,
            endHour = 0,
            now = LocalDate.of(2026, 8, 13).atTime(12, 0).atZone(zone),
            weekStart = DayOfWeek.MONDAY,
            holidays = setOf(holiday),
        )
        assertEquals(holiday, candidate?.logicalDate)
        assertEquals(1, widgetModel(todo, holiday, emptyList(), setOf(holiday)).totalCount)

        todoRepository.setCompleted(todo.id, holiday, true, operationId = "holiday-completion").getOrThrow()
        val completion = database.todoExecutionDao().findForTodo(todo.id).single()
        assertEquals(0, widgetModel(todo, holiday, listOf(completion), setOf(holiday)).totalCount)
        clock.setDate(LocalDate.of(2026, 8, 16))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        val nextCandidate = nextNotificationCandidate(
            todo = domainTodo,
            notification = notification,
            endHour = 0,
            now = clock.instant().atZone(zone),
            weekStart = DayOfWeek.MONDAY,
            completedDates = setOf(holiday),
            holidays = setOf(holiday),
        )
        assertEquals(LocalDate.of(2026, 8, 29), nextCandidate?.logicalDate)
        clock.setDate(LocalDate.of(2026, 8, 29))
        assertFalse(todoRepository.observeOccurrences(clock.date).first().isEmpty())
    }

    private fun historyRepository() = RoomHistoryRepository(
        database = database,
        todoDao = database.todoDao(),
        categoryDao = database.categoryDao(),
        executionDao = database.todoExecutionDao(),
        periodResultDao = database.periodResultDao(),
        todoRepository = todoRepository,
        settingsRepository = settings,
        notificationScheduler = NoOpRecurrenceNotificationScheduler(),
        widgetUpdater = widgetUpdater,
        clock = clock,
    )

    private fun historyReconciler() = RoomHistoryReconciler(
        database = database,
        todoDao = database.todoDao(),
        categoryDao = database.categoryDao(),
        executionDao = database.todoExecutionDao(),
        periodResultDao = database.periodResultDao(),
        runtimeStateDao = database.todoRuntimeStateDao(),
        notificationDao = database.todoNotificationDao(),
        settingsRepository = settings,
        holidayRepository = holidays,
        clock = clock,
    )

    private fun widgetModel(
        todo: TodoEntity,
        date: LocalDate,
        executions: List<TodoExecutionEntity>,
        holidayDates: Set<LocalDate>,
    ) = buildWidgetDisplayModel(
        now = date.atTime(12, 0).atZone(zone),
        source = WidgetSourceData(listOf(todo), emptyList(), executions, holidayDates),
        dayEndHour = 0,
        weekStart = DayOfWeek.MONDAY,
        holidayState = holidaySnapshot(HolidayYearStatus.AVAILABLE_CURRENT, holidayDates),
        uncategorizedName = "uncategorized",
        logicalDateLabel = LocalDate::toString,
        deadlineLabel = { nextDay, hour, minute -> "$nextDay-$hour-$minute" },
    )

    private fun weeklyCount(
        requiredCount: Int,
        periodWeeks: Int = 1,
        dayFilter: RecurrenceDayFilter = RecurrenceDayFilter.ALL,
    ) = RecurrenceRule(
        type = RecurrenceType.WEEKLY_COUNT,
        requiredCount = requiredCount,
        periodWeeks = periodWeeks,
        dayFilter = dayFilter,
    )

    private fun todoEntity(
        id: String,
        rule: RecurrenceRule,
        startDate: LocalDate = LocalDate.of(2026, 8, 10),
        dueMinutes: Int? = null,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(rule)
        return TodoEntity(
            id = id,
            title = id,
            description = "",
            categoryId = null,
            startDate = startDate.toString(),
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

    private fun execution(
        id: String,
        todoId: String,
        date: LocalDate,
        state: TodoState,
    ) = TodoExecutionEntity(
        id = id,
        operationId = "operation-$id",
        todoId = todoId,
        logicalDate = date.toString(),
        status = state.code,
        actedAt = 1,
        finalizedAt = 1,
        definitionRevision = 1,
        snapshotVersion = 1,
        snapshotJson = "{}",
    )

    private fun holidaySnapshot(
        status: HolidayYearStatus,
        dates: Set<LocalDate> = emptySet(),
        generation: Long = 1,
    ) = HolidaySnapshot(
        namesByDate = dates.associateWith { "holiday-$it" },
        yearStates = mapOf(
            2026 to HolidayYearState(
                year = 2026,
                status = status,
                available = status == HolidayYearStatus.AVAILABLE_CURRENT,
            ),
        ),
        generation = generation,
        changedYears = setOf(2026),
        supportedYears = setOf(2026),
    )
}

private class MutableRecurrenceClock(
    initialDate: LocalDate,
    private val clockZone: ZoneId,
) : Clock() {
    private var currentInstant = initialDate.atTime(12, 0).atZone(clockZone).toInstant()
    val date: LocalDate
        get() = currentInstant.atZone(clockZone).toLocalDate()

    fun setDate(value: LocalDate) {
        currentInstant = value.atTime(12, 0).atZone(clockZone).toInstant()
    }

    override fun getZone(): ZoneId = clockZone
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(currentInstant, zone)
    override fun instant(): Instant = currentInstant
}

private class RecurrenceTestSettingsRepository : SettingsRepository {
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

private class NoOpRecurrenceNotificationScheduler : NotificationScheduler {
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
