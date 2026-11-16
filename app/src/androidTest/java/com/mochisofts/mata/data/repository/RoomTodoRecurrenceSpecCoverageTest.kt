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
import com.mochisofts.mata.domain.model.nextOccurrences
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
    fun tl003_todayUsesOneGlobalLogicalDateAcrossUncategorizedAndUserCategories() = runBlocking {
        val calendarDate = LocalDate.of(2026, 8, 11)
        val logicalDate = calendarDate.minusDays(1)
        settings.dayEndHour.value = 4
        clock.setDateTime(calendarDate, hour = 2)
        database.categoryDao().upsert(
            CategoryEntity(
                id = "game",
                name = "ゲーム",
                normalizedName = "ゲーム",
                colorIndex = 4,
                iconName = "SportsEsports",
                legacyEndHour = 22,
                sortOrder = 0,
                createdAt = 1,
            ),
        )
        database.todoDao().upsert(
            todoEntity("uncategorized", RecurrenceRule.daily(), startDate = logicalDate),
        )
        database.todoDao().upsert(
            todoEntity(
                "categorized",
                RecurrenceRule.daily(),
                startDate = logicalDate,
                categoryId = "game",
            ),
        )

        val occurrences = todoRepository.observeOccurrences(calendarDate).first()

        assertEquals(setOf("uncategorized", "categorized"), occurrences.map { it.todo.id }.toSet())
        assertEquals(setOf(logicalDate), occurrences.map { it.logicalDate }.toSet())
        assertEquals(setOf(null, "game"), occurrences.map { it.category?.id }.toSet())
        assertEquals(setOf(logicalDate), occurrences.map { it.effectiveDueDate }.toSet())
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
    fun te011_schedulePreviewAppliesHolidaysOnlyToHolidayAwareDayFilters() = runBlocking {
        val previewStart = LocalDate.of(2026, 11, 2)
        val weekdayHoliday = LocalDate.of(2026, 11, 3)
        val holidayDates = setOf(weekdayHoliday)
        val weekdays = todoEntity(
            id = "preview-weekdays",
            rule = RecurrenceRule(RecurrenceType.WEEKDAYS),
            startDate = previewStart,
        )
        val weekendsAndHolidays = todoEntity(
            id = "preview-weekends-holidays",
            rule = RecurrenceRule(
                type = RecurrenceType.SELECTED_WEEKDAYS,
                dayFilter = RecurrenceDayFilter.WEEKENDS_HOLIDAYS,
            ),
            startDate = previewStart,
        )
        val arbitraryTuesday = todoEntity(
            id = "preview-arbitrary-tuesday",
            rule = RecurrenceRule(
                type = RecurrenceType.SELECTED_WEEKDAYS,
                selectedWeekdays = setOf(DayOfWeek.TUESDAY),
                dayFilter = RecurrenceDayFilter.CUSTOM,
            ),
            startDate = previewStart,
        )
        listOf(weekdays, weekendsAndHolidays, arbitraryTuesday).forEach { todo ->
            database.todoDao().upsert(todo)
        }

        val weekdayPreview = requireNotNull(todoRepository.getTodo(weekdays.id))
            .nextOccurrences(previewStart, limit = 3, holidays = holidayDates)
        val weekendHolidayPreview = requireNotNull(todoRepository.getTodo(weekendsAndHolidays.id))
            .nextOccurrences(previewStart, limit = 3, holidays = holidayDates)
        val arbitraryPreview = requireNotNull(todoRepository.getTodo(arbitraryTuesday.id))
            .nextOccurrences(previewStart, limit = 3, holidays = holidayDates)

        assertEquals(
            listOf(previewStart, previewStart.plusDays(2), previewStart.plusDays(3)),
            weekdayPreview,
        )
        assertEquals(
            listOf(weekdayHoliday, previewStart.plusDays(5), previewStart.plusDays(6)),
            weekendHolidayPreview,
        )
        assertEquals(
            listOf(weekdayHoliday, weekdayHoliday.plusWeeks(1), weekdayHoliday.plusWeeks(2)),
            arbitraryPreview,
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
    fun tl016_weeklyAndMonthlyCountTodosRejectSecondCompletionOnSameLogicalDate() = runBlocking {
        val rules = listOf(
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 2),
            RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 2),
        )

        rules.forEachIndexed { index, rule ->
            val todo = todoEntity("count-$index", rule)
            database.todoDao().upsert(todo)
            todoRepository.setCompleted(
                todo.id,
                clock.date,
                true,
                operationId = "first-$index",
            ).getOrThrow()

            val duplicate = todoRepository.setCompleted(
                todo.id,
                clock.date,
                true,
                operationId = "duplicate-$index",
            )

            assertTrue(duplicate.isFailure)
            assertEquals(1, database.todoExecutionDao().findForTodo(todo.id).size)
            assertEquals(
                1,
                todoRepository.observeOccurrences(clock.date).first()
                    .single { it.todo.id == todo.id }
                    .progress
                    ?.completedCount,
            )
        }
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

    @Test
    fun completionCycleDelayedCompletionSchedulesFromResolutionAndRejectsStaleAction() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle()
        assertTrue(database.todoDao().findById(id)!!.carryOverEnabled)
        clock.setDate(start.plusDays(4))
        val current = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(start, current.scheduledLogicalDate)
        assertTrue(current.isCarryOver)
        todoRepository.setCompleted(id, clock.date, true, "complete-cycle", start).getOrThrow()
        assertTrue(todoRepository.observeOccurrences(clock.date).first().all { it.state == TodoState.COMPLETED })
        val saved = database.todoExecutionDao().findForTodo(id).single()
        assertEquals(clock.date.toString(), saved.resolvedLogicalDate)
        assertEquals(start.toString(), saved.scheduledLogicalDate)
        assertEquals(listOf(start.plusDays(7)), todoRepository.getTodo(id)!!.nextOccurrences(clock.date, 3))
        assertTrue(todoRepository.setCompleted(id, clock.date, true, "stale-cycle", start).isFailure)
        clock.setDate(start.plusDays(6))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        clock.setDate(start.plusDays(7))
        assertEquals(clock.date, todoRepository.observeOccurrences(clock.date).first().single().scheduledLogicalDate)
    }

    @Test
    fun completionCycleSkipAdvancesAndHistoryUndoReopensTheOriginalOccurrence() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle()
        clock.setDate(start.plusDays(2))
        todoRepository.setSkipped(id, clock.date, true, "skip-cycle", start).getOrThrow()
        assertEquals(listOf(start.plusDays(5)), todoRepository.getTodo(id)!!.nextOccurrences(clock.date, 3))
        val skipped = database.todoExecutionDao().findForTodo(id).single()
        val token = historyRepository().undoAction(skipped.id).getOrThrow()
        assertEquals(start, todoRepository.observeOccurrences(clock.date).first().single().scheduledLogicalDate)
        historyRepository().restoreAction(token).getOrThrow()
        assertEquals(listOf(start.plusDays(5)), todoRepository.getTodo(id)!!.nextOccurrences(clock.date, 3))
    }

    @Test
    fun completionCycleCurrentCompletionCanBeUndoneWithoutChangingOlderHistory() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle()
        todoRepository.setCompleted(id, start, true, "first-cycle", start).getOrThrow()
        val first = database.todoExecutionDao().findForTodo(id).single()
        clock.setDate(start.plusDays(3))
        todoRepository.setCompleted(id, clock.date, true, "second-cycle", clock.date).getOrThrow()
        val second = database.todoExecutionDao().findForTodo(id).first { it.id != first.id }
        assertTrue(historyRepository().observeDay(clock.date).first().entries.single().canUndoAction)
        historyRepository().undoAction(second.id).getOrThrow()
        assertEquals(listOf(first), database.todoExecutionDao().findForTodo(id))
        assertEquals(clock.date, todoRepository.observeOccurrences(clock.date).first().single().scheduledLogicalDate)
        assertTrue(historyRepository().undoAction(first.id).isFailure)
    }

    @Test
    fun completionCycleLongAbsenceNeverCreatesMissedHistoryOrDuplicateRows() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle()
        clock.setDate(start.plusYears(5))
        repeat(3) {
            val result = historyReconciler().reconcile(500)
            assertEquals(0, result.generatedRecords)
            assertFalse(result.hasMore)
        }
        assertTrue(database.todoExecutionDao().findForTodo(id).isEmpty())
        assertEquals(start, todoRepository.observeOccurrences(clock.date).first().single().scheduledLogicalDate)
        assertEquals(start.toString(), database.todoRuntimeStateDao().find(id)?.pendingScheduledLogicalDate)
    }

    @Test
    fun completionCycleUsesLogicalDayAndRecomputesNotificationsAndWidgetFromSameAnchor() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle()
        settings.dayEndHour.value = 4
        clock.setDateTime(start.plusDays(1), 2)
        todoRepository.setCompleted(id, start, true, "logical-cycle", start).getOrThrow()
        val todo = todoRepository.getTodo(id)!!
        val notification = TodoNotification("n", NotificationRelation.AT, 0, NotificationUnit.MINUTE)
        assertEquals(start.plusDays(3), nextNotificationCandidate(todo, notification, 4,
            clock.instant().atZone(zone), DayOfWeek.MONDAY)?.logicalDate)
        val entity = database.todoDao().findById(id)!!
        val executions = database.todoExecutionDao().findForTodo(id)
        assertEquals(0, widgetModel(entity, start.plusDays(2), executions, emptySet()).totalCount)
        assertEquals(1, widgetModel(entity, start.plusDays(3), executions, emptySet()).totalCount)
    }

    @Test
    fun completionCycleWidgetQueryIncludesTheAnchorOlderThanItsNormalWindow() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle(interval = 90)
        todoRepository.setCompleted(id, start, true, "long-cycle", start).getOrThrow()
        val date = start.plusDays(80)
        val rows = database.todoExecutionDao().findForWidget(date.minusDays(10).toString(), date.toString())
        assertEquals(1, rows.size)
        assertEquals(0, widgetModel(database.todoDao().findById(id)!!, date, rows, emptySet()).totalCount)
    }

    @Test
    fun completionCycleEndDateStopsNewCyclesButKeepsTheExistingUnfinishedCycle() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle(endDate = start)
        clock.setDate(start.plusDays(8))
        assertEquals(start, todoRepository.observeOccurrences(clock.date).first().single().scheduledLogicalDate)
        todoRepository.setCompleted(id, clock.date, true, "ended-cycle", start).getOrThrow()
        clock.setDate(start.plusDays(9))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        assertTrue(todoRepository.getTodo(id)!!.nextOccurrences(clock.date, 3).isEmpty())
    }

    @Test
    fun completionCycleIntervalEditUsesExistingHistoryAndArchiveRestoreDoesNotBackfill() = runBlocking {
        val start = clock.date
        val id = saveCompletionCycle()
        todoRepository.setCompleted(id, start, true, "edit-cycle", start).getOrThrow()
        val history = database.todoExecutionDao().findForTodo(id)
        todoRepository.saveTodo(id, "edited", "", null, start, null,
            RecurrenceRule(RecurrenceType.AFTER_COMPLETION_DAYS, intervalDays = 5), null,
            emptyList(), null, false).getOrThrow()
        assertEquals(listOf(start.plusDays(5)), todoRepository.getTodo(id)!!.nextOccurrences(start, 3))
        assertEquals(history, database.todoExecutionDao().findForTodo(id))
        todoRepository.archiveTodo(id).getOrThrow()
        clock.setDate(start.plusDays(10))
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        todoRepository.restoreTodo(id).getOrThrow()
        historyReconciler().reconcile(500)
        assertEquals(history, database.todoExecutionDao().findForTodo(id))
        assertEquals(start.plusDays(5), todoRepository.observeOccurrences(clock.date).first().single().scheduledLogicalDate)
    }

    private suspend fun saveCompletionCycle(interval: Int = 3, endDate: LocalDate? = null): String =
        todoRepository.saveTodo(null, "completion cycle", "", null, clock.date, endDate,
            RecurrenceRule(RecurrenceType.AFTER_COMPLETION_DAYS, intervalDays = interval),
            null, emptyList(), null, false).getOrThrow()

    private fun historyRepository() = RoomHistoryRepository(
        database = database,
        todoDao = database.todoDao(),
        categoryDao = database.categoryDao(),
        executionDao = database.todoExecutionDao(),
            periodResultDao = database.periodResultDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
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
        categoryId: String? = null,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(rule)
        return TodoEntity(
            id = id,
            title = id,
            description = "",
            categoryId = categoryId,
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

    fun setDateTime(value: LocalDate, hour: Int) {
        currentInstant = value.atTime(hour, 0).atZone(clockZone).toInstant()
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
