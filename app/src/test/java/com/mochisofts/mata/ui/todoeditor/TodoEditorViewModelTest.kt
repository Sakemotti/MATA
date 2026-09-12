package com.mochisofts.mata.ui.todoeditor

import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.HolidayYearState
import com.mochisofts.mata.domain.model.HolidayYearStatus
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.NotificationValidationError
import com.mochisofts.mata.domain.model.RecurrenceDayFilter
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.occurrencesIn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ted06_missingTargetNeverShowsEmptyFormAndReturnsNotFound() = runTest {
        val existing = Todo(
            id = "target",
            title = "target",
            description = "",
            categoryId = null,
            startDate = LocalDate.of(2026, 9, 3),
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = null,
            definitionRevision = 1,
            archivedAt = null,
            createdAt = 1,
        )
        val gate = CompletableDeferred<Unit>()
        val repository = FakeTodoRepository(mapOf(existing.id to existing)).apply {
            getGate = gate
        }
        val viewModel = createViewModel(todoRepository = repository, todoId = existing.id)
        val effect = async { viewModel.effects.first() }
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals("", viewModel.uiState.value.title)

        repository.remove(existing.id)
        gate.complete(Unit)
        runCurrent()

        assertEquals(TodoEditorEffect.NotFound, effect.await())
        assertTrue(viewModel.uiState.value.isLoading)
        assertEquals("", viewModel.uiState.value.title)
    }

    @Test
    fun te007_newTodoUsesCurrentLogicalDateAndRejectsEarlierDate() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(
            todoRepository = repository,
            settingsRepository = FakeSettingsRepository(dayEndHour = 4),
            clock = fixedClock("2026-09-03T02:00:00+09:00"),
        )
        runCurrent()

        assertEquals(LocalDate.of(2026, 9, 2), viewModel.uiState.value.today)
        assertEquals(LocalDate.of(2026, 9, 2), viewModel.uiState.value.startDate)

        viewModel.setTitle("朝のルーチン")
        viewModel.setStartDate(LocalDate.of(2026, 9, 1))
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.save()
        runCurrent()
        assertEquals(0, repository.saveCount)

        viewModel.setStartDate(LocalDate.of(2026, 9, 2))
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()

        assertEquals(LocalDate.of(2026, 9, 2), repository.lastSave?.startDate)
        assertEquals(TodoEditorEffect.Saved(isNew = true), viewModel.effects.first())
    }

    @Test
    fun existingTodoWithPastStartDateCanStillBeSaved() {
        val state = TodoEditorUiState(
            isLoading = false,
            isNew = false,
            title = "以前からのルーチン",
            today = LocalDate.of(2026, 9, 3),
            startDate = LocalDate.of(2025, 1, 1),
        )

        assertTrue(state.canSave)
    }

    @Test
    fun te031_monthlyNthWeekdaysSaveReloadPreviewAndSkipMissingFifthWeekday() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(
            todoRepository = repository,
            clock = fixedClock("2026-01-01T12:00:00+09:00"),
        )
        runCurrent()

        val firstMonday = MonthlyNthWeekday(1, DayOfWeek.MONDAY)
        val thirdFriday = MonthlyNthWeekday(3, DayOfWeek.FRIDAY)
        val fifthMonday = MonthlyNthWeekday(5, DayOfWeek.MONDAY)
        viewModel.setTitle("月次レビュー")
        viewModel.setStartDate(LocalDate.of(2026, 1, 1))
        viewModel.setRecurrence(RecurrenceType.MONTHLY_NTH_WEEKDAYS)

        val initialSelection = viewModel.uiState.value.monthlyNthWeekdays.single()
        viewModel.toggleMonthlyNthWeekday(
            initialSelection.ordinal,
            initialSelection.dayOfWeek,
        )
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()
        assertEquals(0, repository.saveCount)

        viewModel.toggleMonthlyNthWeekday(firstMonday.ordinal, firstMonday.dayOfWeek)
        viewModel.toggleMonthlyNthWeekday(thirdFriday.ordinal, thirdFriday.dayOfWeek)
        viewModel.toggleMonthlyNthWeekday(fifthMonday.ordinal, fifthMonday.dayOfWeek)
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()

        val savedRule = requireNotNull(repository.lastSave).recurrenceRule
        assertEquals(
            setOf(firstMonday, thirdFriday, fifthMonday),
            savedRule.monthlyNthWeekdays,
        )
        val savedTodo = todoFromSave(requireNotNull(repository.lastSave))
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 30),
            ),
            savedTodo.occurrencesIn(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
            ),
        )

        val reopened = createViewModel(
            todoRepository = FakeTodoRepository(mapOf(savedTodo.id to savedTodo)),
            clock = fixedClock("2026-01-01T12:00:00+09:00"),
            todoId = savedTodo.id,
        )
        runCurrent()
        assertEquals(savedRule.monthlyNthWeekdays, reopened.uiState.value.monthlyNthWeekdays)
    }

    @Test
    fun te032_multiWeekCountFilterSaveReloadAndPreviewStayTogether() = runTest {
        val repository = FakeTodoRepository()
        val holiday = LocalDate.of(2026, 9, 4)
        val viewModel = createViewModel(
            todoRepository = repository,
            holidayRepository = FakeHolidayRepository(setOf(holiday)),
        )
        runCurrent()

        viewModel.setTitle("週末チャレンジ")
        viewModel.setRecurrence(RecurrenceType.WEEKLY_COUNT)
        viewModel.setPeriodWeeks(2)
        viewModel.setWeeklyCount(1)
        viewModel.setDayFilter(RecurrenceDayFilter.WEEKENDS_HOLIDAYS)
        viewModel.save()
        runCurrent()

        val rule = requireNotNull(repository.lastSave).recurrenceRule
        assertEquals(RecurrenceType.WEEKLY_COUNT, rule.type)
        assertEquals(2, rule.periodWeeks)
        assertEquals(1, rule.requiredCount)
        assertEquals(RecurrenceDayFilter.WEEKENDS_HOLIDAYS, rule.dayFilter)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), rule.selectedWeekdays)

        val savedTodo = todoFromSave(requireNotNull(repository.lastSave))
        assertEquals(
            LocalDate.of(2026, 9, 3),
            savedTodo.recurrencePeriod(LocalDate.of(2026, 9, 3), DayOfWeek.MONDAY)?.startDate,
        )
        assertTrue(savedTodo.occurrencesIn(holiday, holiday, setOf(holiday)).contains(holiday))

        val reopened = createViewModel(
            todoRepository = FakeTodoRepository(mapOf(savedTodo.id to savedTodo)),
            holidayRepository = FakeHolidayRepository(setOf(holiday)),
            todoId = savedTodo.id,
        )
        runCurrent()
        assertEquals(2, reopened.uiState.value.periodWeeks)
        assertEquals(1, reopened.uiState.value.weeklyCount)
        assertEquals(RecurrenceDayFilter.WEEKENDS_HOLIDAYS, reopened.uiState.value.dayFilter)
    }

    @Test
    fun dueDateAndCarryOver_areValidatedAndSavedForSupportedTypes() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(todoRepository = repository)
        runCurrent()
        viewModel.setTitle("期限付き")
        val startDate = viewModel.uiState.value.startDate

        viewModel.setDueDate(startDate.minusDays(1))
        assertFalse(viewModel.uiState.value.canSave)

        val dueDate = startDate.plusDays(3)
        viewModel.setDueDate(dueDate)
        viewModel.setCarryOverEnabled(true)
        viewModel.save()
        runCurrent()

        assertEquals(dueDate, repository.lastSave?.dueDate)
        assertTrue(repository.lastSave?.carryOverEnabled == true)

        viewModel.setRecurrence(RecurrenceType.WEEKLY_COUNT)
        assertFalse(viewModel.uiState.value.carryOverEnabled)
    }

    @Test
    fun te003_titleAndDescriptionBoundariesControlSaveAvailability() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setTitle("")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.setTitle("a")
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.setTitle("a".repeat(100))
        viewModel.setDescription("b".repeat(1000))
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.setTitle("a".repeat(101))
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.setTitle("有効なタイトル")
        viewModel.setDescription("b".repeat(1001))
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.setDescription("")
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun te009_allRecurrenceTypesAcceptOnlyTheirValidBoundaries() = runTest {
        val viewModel = createViewModel()
        runCurrent()
        viewModel.setTitle("全方式の検証")

        listOf(
            RecurrenceType.ONCE,
            RecurrenceType.DAILY,
            RecurrenceType.WEEKDAYS,
            RecurrenceType.MONTH_END,
        ).forEach { type ->
            viewModel.setRecurrence(type)
            assertEquals(type, viewModel.uiState.value.recurrenceType)
            assertTrue("$type must be valid", viewModel.uiState.value.canSave)
        }

        viewModel.setRecurrence(RecurrenceType.SELECTED_WEEKDAYS)
        assertTrue(viewModel.uiState.value.selectedWeekdays.isNotEmpty())
        viewModel.uiState.value.selectedWeekdays.toList().forEach(viewModel::toggleWeekday)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.toggleWeekday(DayOfWeek.MONDAY)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.setRecurrence(RecurrenceType.MONTHLY_DAY)
        viewModel.setMonthlyDay(31)
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.setMonthlyDay(32)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.setRecurrence(RecurrenceType.MONTHLY_NTH_WEEKDAYS)
        assertTrue(viewModel.uiState.value.monthlyNthWeekdays.isNotEmpty())
        val nth = viewModel.uiState.value.monthlyNthWeekdays.single()
        viewModel.toggleMonthlyNthWeekday(nth.ordinal, nth.dayOfWeek)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.toggleMonthlyNthWeekday(5, DayOfWeek.FRIDAY)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.setRecurrence(RecurrenceType.EVERY_N_DAYS)
        viewModel.setIntervalDays("0")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.setIntervalDays("999")
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.setIntervalDays("12a34")
        assertEquals("123", viewModel.uiState.value.intervalDaysInput)

        viewModel.setRecurrence(RecurrenceType.WEEKLY_COUNT)
        viewModel.setPeriodWeeks(0)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.setPeriodWeeks(52)
        viewModel.setWeeklyCount(364)
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.setWeeklyCount(365)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.setRecurrence(RecurrenceType.MONTHLY_COUNT)
        viewModel.setMonthlyCount(31)
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.setMonthlyCount(32)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun te033_dayPresetsApplyImmediatelyAndIndividualChangesBecomeCustom() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        listOf(RecurrenceType.SELECTED_WEEKDAYS, RecurrenceType.WEEKLY_COUNT).forEach { type ->
            viewModel.setRecurrence(type)
            viewModel.setDayFilter(RecurrenceDayFilter.WEEKDAYS)
            assertEquals(
                DayOfWeek.entries.filterTo(mutableSetOf()) { it.value <= 5 },
                viewModel.uiState.value.selectedWeekdays,
            )
            assertEquals(RecurrenceDayFilter.WEEKDAYS, viewModel.uiState.value.dayFilter)

            viewModel.setDayFilter(RecurrenceDayFilter.WEEKENDS_HOLIDAYS)
            assertEquals(
                setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
                viewModel.uiState.value.selectedWeekdays,
            )
            assertEquals(
                RecurrenceDayFilter.WEEKENDS_HOLIDAYS,
                viewModel.uiState.value.dayFilter,
            )

            viewModel.toggleWeekday(DayOfWeek.MONDAY)
            assertEquals(RecurrenceDayFilter.CUSTOM, viewModel.uiState.value.dayFilter)
            assertTrue(DayOfWeek.MONDAY in viewModel.uiState.value.selectedWeekdays)
        }
    }

    @Test
    fun te006_categorySelectionNeverChangesGlobalLogicalDayCalculations() = runTest {
        val categories = listOf(
            category("CAT-000", "日常", 0),
            category("CAT-004", "ゲーム", 4),
        )
        val viewModel = createViewModel(
            settingsRepository = FakeSettingsRepository(dayEndHour = 4),
            categoryRepository = FakeCategoryRepository(categories),
            clock = fixedClock("2026-09-03T02:00:00+09:00"),
        )
        runCurrent()
        viewModel.setTitle("カテゴリに依存しない期限")
        viewModel.setRecurrence(RecurrenceType.DAILY)
        viewModel.setDueMinutes(3 * 60)
        viewModel.upsertNotification(
            id = null,
            relation = NotificationRelation.AT,
            amount = 0,
            unit = NotificationUnit.MINUTE,
        )

        val notificationId = viewModel.uiState.value.notifications.single().id
        val expectedPreview = tokyoDateTime("2026-09-03T03:00:00+09:00")
        listOf("CAT-000", "CAT-004", null).forEach { categoryId ->
            viewModel.setCategory(categoryId)
            val state = viewModel.uiState.value
            assertEquals(categoryId, state.categoryId)
            assertEquals(4, state.effectiveEndHour)
            assertEquals(LocalDate.of(2026, 9, 2), state.today)
            assertEquals(expectedPreview, state.notificationPreviews[notificationId])
            assertTrue(state.notificationErrors.isEmpty())
        }
    }

    @Test
    fun te012_missingOrFailedHolidayDataKeepsWeekdayPreviewProvisionalAndSaveable() = runTest {
        listOf(
            HolidayYearStatus.UNAVAILABLE,
            HolidayYearStatus.FAILED_WITHOUT_CACHE,
        ).forEach { status ->
            val repository = FakeTodoRepository()
            val holidayRepository = FakeHolidayRepository(status = status)
            val viewModel = createViewModel(
                todoRepository = repository,
                holidayRepository = holidayRepository,
                clock = fixedClock("2026-09-07T09:00:00+09:00"),
            )
            runCurrent()
            viewModel.setTitle("平日の暫定予定")
            viewModel.setRecurrence(RecurrenceType.WEEKDAYS)

            assertEquals(status, viewModel.uiState.value.holidaySnapshot.statusFor(2026))
            assertTrue(viewModel.uiState.value.holidaySnapshot.isProvisional(2026))
            assertTrue(viewModel.uiState.value.canSave)
            viewModel.save()
            runCurrent()

            val saved = todoFromSave(requireNotNull(repository.lastSave))
            assertEquals(
                listOf(
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2026, 9, 9),
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 11),
                ),
                saved.occurrencesIn(
                    LocalDate.of(2026, 9, 7),
                    LocalDate.of(2026, 9, 11),
                    viewModel.uiState.value.holidaySnapshot.dates,
                ),
            )
        }
    }

    @Test
    fun te016_notificationLimitDuplicateAndDayEndValidationTrackEditsAndDeletes() = runTest {
        val viewModel = createViewModel()
        runCurrent()
        viewModel.setTitle("通知検証")
        viewModel.setDueMinutes(12 * 60)

        repeat(10) { index ->
            viewModel.upsertNotification(
                id = null,
                relation = NotificationRelation.BEFORE,
                amount = index + 1,
                unit = NotificationUnit.MINUTE,
            )
        }
        assertEquals(10, viewModel.uiState.value.notifications.size)
        assertTrue(viewModel.uiState.value.notificationErrors.isEmpty())
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.upsertNotification(
            id = null,
            relation = NotificationRelation.BEFORE,
            amount = 11,
            unit = NotificationUnit.MINUTE,
        )
        assertTrue(NotificationValidationError.TOO_MANY in viewModel.uiState.value.notificationErrors)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.deleteNotification(viewModel.uiState.value.notifications.last().id)

        val duplicateTarget = viewModel.uiState.value.notifications.last()
        viewModel.upsertNotification(
            id = duplicateTarget.id,
            relation = NotificationRelation.BEFORE,
            amount = 1,
            unit = NotificationUnit.MINUTE,
        )
        assertTrue(NotificationValidationError.DUPLICATE in viewModel.uiState.value.notificationErrors)

        viewModel.upsertNotification(
            id = duplicateTarget.id,
            relation = NotificationRelation.AFTER,
            amount = 12,
            unit = NotificationUnit.HOUR,
        )
        assertTrue(NotificationValidationError.AFTER_DAY_END in viewModel.uiState.value.notificationErrors)
        viewModel.deleteNotification(duplicateTarget.id)
        assertTrue(viewModel.uiState.value.notificationErrors.isEmpty())
    }

    @Test
    fun te017_notificationsSortByComputedTriggerWhilePreservingDifferenceUnits() = runTest {
        val viewModel = createViewModel(clock = fixedClock("2026-09-03T09:00:00+09:00"))
        runCurrent()
        viewModel.setTitle("通知順序")
        viewModel.setDueMinutes(12 * 60)

        viewModel.upsertNotification(null, NotificationRelation.AFTER, 1, NotificationUnit.HOUR)
        viewModel.upsertNotification(null, NotificationRelation.AT, 0, NotificationUnit.MINUTE)
        viewModel.upsertNotification(null, NotificationRelation.BEFORE, 30, NotificationUnit.MINUTE)

        val state = viewModel.uiState.value
        assertEquals(
            listOf(
                NotificationRelation.BEFORE,
                NotificationRelation.AT,
                NotificationRelation.AFTER,
            ),
            state.notifications.map { it.relation },
        )
        assertEquals(
            listOf(
                tokyoDateTime("2026-09-03T11:30:00+09:00"),
                tokyoDateTime("2026-09-03T12:00:00+09:00"),
                tokyoDateTime("2026-09-03T13:00:00+09:00"),
            ),
            state.notifications.map { state.notificationPreviews[it.id] },
        )
        assertEquals(30, state.notifications[0].amount)
        assertEquals(NotificationUnit.MINUTE, state.notifications[0].unit)
        assertEquals(1, state.notifications[2].amount)
        assertEquals(NotificationUnit.HOUR, state.notifications[2].unit)
    }

    @Test
    fun te018_pastCurrentNotificationWarnsWhileFutureCandidatesRemainPreviewed() = runTest {
        val viewModel = createViewModel(clock = fixedClock("2026-09-03T12:00:00+09:00"))
        runCurrent()
        viewModel.setTitle("過去通知を含むTODO")
        viewModel.setDueMinutes(10 * 60)
        viewModel.upsertNotification(null, NotificationRelation.AT, 0, NotificationUnit.MINUTE)
        viewModel.upsertNotification(null, NotificationRelation.AFTER, 3, NotificationUnit.HOUR)

        val state = viewModel.uiState.value
        val at = state.notifications.single { it.relation == NotificationRelation.AT }
        val after = state.notifications.single { it.relation == NotificationRelation.AFTER }
        assertTrue(state.hasPastNotificationForCurrentOccurrence)
        assertEquals(null, state.notificationPreviews[at.id])
        assertEquals(
            tokyoDateTime("2026-09-03T13:00:00+09:00"),
            state.notificationPreviews[after.id],
        )
        assertTrue(state.canSave)
    }

    @Test
    fun ted03_latestCategoryRecurrenceAndDeadlineDriveOneConsistentPreview() = runTest {
        val viewModel = createViewModel(
            settingsRepository = FakeSettingsRepository(dayEndHour = 4),
            categoryRepository = FakeCategoryRepository(
                listOf(category("CAT-004", "朝", 4)),
            ),
            clock = fixedClock("2026-09-03T02:00:00+09:00"),
        )
        runCurrent()
        viewModel.setTitle("最新入力のプレビュー")
        viewModel.setCategory("CAT-004")
        viewModel.setRecurrence(RecurrenceType.DAILY)
        viewModel.setDueMinutes(5 * 60)
        viewModel.upsertNotification(null, NotificationRelation.BEFORE, 1, NotificationUnit.HOUR)
        val notificationId = viewModel.uiState.value.notifications.single().id
        assertEquals(
            tokyoDateTime("2026-09-03T04:00:00+09:00"),
            viewModel.uiState.value.notificationPreviews[notificationId],
        )

        viewModel.setRecurrence(RecurrenceType.EVERY_N_DAYS)
        viewModel.setIntervalDays("2")
        assertEquals(
            tokyoDateTime("2026-09-04T04:00:00+09:00"),
            viewModel.uiState.value.notificationPreviews[notificationId],
        )

        viewModel.setDueMinutes(3 * 60)
        val state = viewModel.uiState.value
        assertEquals("CAT-004", state.categoryId)
        assertEquals(RecurrenceType.EVERY_N_DAYS, state.recurrenceType)
        assertEquals(3 * 60, state.dueMinutes)
        assertEquals(
            tokyoDateTime("2026-09-05T02:00:00+09:00"),
            state.notificationPreviews[notificationId],
        )
        assertTrue(state.notificationErrors.isEmpty())
    }

    @Test
    fun te019_permissionDenialKeepsSettingsAndPermissionGrantReconcilesFutureCandidates() = runTest {
        val repository = FakeTodoRepository()
        val settings = FakeSettingsRepository()
        val scheduler = FakeNotificationScheduler(
            state = NotificationSystemState(
                canPostNotifications = false,
                runtimePermissionRelevant = true,
                runtimePermissionGranted = false,
                exactAlarmRelevant = false,
                canScheduleExactAlarms = true,
            ),
        )
        val viewModel = createViewModel(
            todoRepository = repository,
            settingsRepository = settings,
            notificationScheduler = scheduler,
        )
        runCurrent()

        viewModel.setTitle("通知付きTODO")
        viewModel.setDueMinutes(13 * 60)
        viewModel.upsertNotification(null, NotificationRelation.BEFORE, 4, NotificationUnit.HOUR)
        viewModel.upsertNotification(null, NotificationRelation.AT, 0, NotificationUnit.MINUTE)
        viewModel.save()
        runCurrent()

        assertEquals(true, settings.notificationPermissionRequestedState.value)
        assertEquals(TodoEditorEffect.ExplainNotificationPermission, viewModel.effects.first())
        assertEquals(2, repository.lastSave?.notifications?.size)

        viewModel.notificationPermissionRequestFinished()
        runCurrent()

        assertEquals(listOf("saved-todo"), scheduler.reconciledTodoIds)
        assertEquals(TodoEditorEffect.Saved(isNew = true), viewModel.effects.first())

        val saved = todoFromSave(requireNotNull(repository.lastSave))
        val now = ZonedDateTime.parse("2026-09-03T12:00:00+09:00")
        assertEquals(
            null,
            com.mochisofts.mata.domain.model.nextNotificationCandidate(
                saved,
                saved.notifications.single { it.relation == NotificationRelation.BEFORE },
                0,
                now,
                DayOfWeek.MONDAY,
            ),
        )
        val future = com.mochisofts.mata.domain.model.nextNotificationCandidate(
            saved,
            saved.notifications.single { it.relation == NotificationRelation.AT },
            0,
            now,
            DayOfWeek.MONDAY,
        )
        assertTrue(requireNotNull(future).triggerAt.isAfter(now))
    }

    @Test
    fun te025_rapidSaveFailureKeepsDraftAndAllowsSingleRetry() = runTest {
        val repository = FakeTodoRepository().apply {
            saveResult = Result.failure(IllegalStateException("save failed"))
            saveGate = CompletableDeferred()
        }
        val viewModel = createViewModel(todoRepository = repository)
        runCurrent()

        viewModel.setTitle("保存に失敗するTODO")
        viewModel.save()
        runCurrent()
        viewModel.save()

        assertEquals(1, repository.saveCount)
        assertTrue(viewModel.uiState.value.isSaving)

        repository.saveGate?.complete(Unit)
        runCurrent()

        assertEquals("保存に失敗するTODO", viewModel.uiState.value.title)
        assertTrue(viewModel.uiState.value.isDirty)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.errorMessageRes)
        assertTrue(viewModel.uiState.value.canSave)

        repository.saveResult = Result.success("saved-todo")
        viewModel.save()
        runCurrent()

        assertEquals(2, repository.saveCount)
        assertEquals(TodoEditorEffect.Saved(isNew = true), viewModel.effects.first())
    }

    @Test
    fun te028_archiveAndDeleteOnlyLeaveEditorAfterSuccessfulOperation() = runTest {
        val target = Todo(
            id = "target",
            title = "保存済みTODO",
            description = "保存済みの説明",
            categoryId = null,
            startDate = LocalDate.of(2026, 9, 3),
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = null,
            definitionRevision = 1,
            archivedAt = null,
            createdAt = 1,
        )
        val repository = FakeTodoRepository(mapOf(target.id to target)).apply {
            archiveResult = Result.failure(IllegalStateException("archive failed"))
            deleteResult = Result.failure(IllegalStateException("delete failed"))
        }
        val viewModel = createViewModel(todoRepository = repository, todoId = target.id)
        runCurrent()
        viewModel.setTitle("編集中のTODO")

        viewModel.archive()
        runCurrent()
        assertEquals(1, repository.archiveCount)
        assertEquals("編集中のTODO", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.errorMessageRes)

        viewModel.delete()
        runCurrent()
        assertEquals(1, repository.deleteCount)
        assertEquals("編集中のTODO", viewModel.uiState.value.title)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.errorMessageRes)

        val successfulArchive = createViewModel(
            todoRepository = FakeTodoRepository(mapOf(target.id to target)),
            todoId = target.id,
        )
        runCurrent()
        successfulArchive.archive()
        runCurrent()
        assertEquals(TodoEditorEffect.Archived, successfulArchive.effects.first())

        val successfulDelete = createViewModel(
            todoRepository = FakeTodoRepository(mapOf(target.id to target)),
            todoId = target.id,
        )
        runCurrent()
        successfulDelete.delete()
        runCurrent()
        assertEquals(TodoEditorEffect.Deleted, successfulDelete.effects.first())
    }

    private fun createViewModel(
        todoRepository: FakeTodoRepository = FakeTodoRepository(),
        categoryRepository: FakeCategoryRepository = FakeCategoryRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        notificationScheduler: FakeNotificationScheduler = FakeNotificationScheduler(),
        holidayRepository: FakeHolidayRepository = FakeHolidayRepository(),
        clock: Clock = fixedClock("2026-09-03T12:00:00+09:00"),
        todoId: String? = null,
    ) = TodoEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("todoId" to todoId)),
        todoRepository = todoRepository,
        categoryRepository = categoryRepository,
        settingsRepository = settingsRepository,
        notificationScheduler = notificationScheduler,
        holidayRepository = holidayRepository,
        clock = clock,
    )

    private fun fixedClock(value: String): Clock {
        val zone = ZoneId.of("Asia/Tokyo")
        return Clock.fixed(ZonedDateTime.parse(value).toInstant(), zone)
    }

    private fun tokyoDateTime(value: String): ZonedDateTime =
        ZonedDateTime.parse(value).withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
}

private fun todoFromSave(call: SaveTodoCall) = Todo(
    id = "saved-todo",
    title = call.title,
    description = call.description,
    categoryId = call.categoryId,
    startDate = call.startDate,
    endDate = call.endDate,
    recurrenceRule = call.recurrenceRule,
    dueMinutes = call.dueMinutes,
    definitionRevision = 1,
    archivedAt = null,
    createdAt = 1,
    notifications = call.notifications,
    dueDate = call.dueDate,
    carryOverEnabled = call.carryOverEnabled,
)

private data class SaveTodoCall(
    val id: String?,
    val title: String,
    val description: String,
    val categoryId: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val recurrenceRule: RecurrenceRule,
    val dueMinutes: Int?,
    val notifications: List<TodoNotification>,
    val dueDate: LocalDate?,
    val carryOverEnabled: Boolean,
)

private class FakeTodoRepository(
    initialTodos: Map<String, Todo> = emptyMap(),
) : TodoRepository {
    private val todos = initialTodos.toMutableMap()
    var getGate: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var saveResult: Result<String> = Result.success("saved-todo")
    var saveCount: Int = 0
    var lastSave: SaveTodoCall? = null
    var archiveResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    var archiveCount: Int = 0
    var deleteCount: Int = 0

    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> =
        flowOf(emptyList())

    override fun observeTodos(): Flow<List<Todo>> = flowOf(todos.values.toList())

    override suspend fun getTodo(id: String): Todo? {
        getGate?.await()
        return todos[id]
    }

    fun remove(id: String) {
        todos.remove(id)
    }

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
    ): Result<String> {
        saveCount += 1
        saveGate?.await()
        lastSave = SaveTodoCall(
            id = id,
            title = title,
            description = description,
            categoryId = categoryId,
            startDate = startDate,
            endDate = endDate,
            recurrenceRule = recurrenceRule,
            dueMinutes = dueMinutes,
            notifications = notifications,
            dueDate = dueDate,
            carryOverEnabled = carryOverEnabled,
        )
        return saveResult
    }

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun archiveTodo(id: String): Result<Unit> {
        archiveCount += 1
        return archiveResult
    }
    override suspend fun restoreTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTodo(id: String): Result<Unit> {
        deleteCount += 1
        return deleteResult
    }
}

private class FakeCategoryRepository(
    private val categories: List<Category> = emptyList(),
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> = flowOf(categories)
    override suspend fun getCategory(id: String): Category? = categories.firstOrNull { it.id == id }

    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
    ): Result<String> = Result.success(id ?: "category")

    override suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteCategory(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeSettingsRepository(
    dayEndHour: Int = 0,
) : SettingsRepository {
    private val showCompletedState = MutableStateFlow(false)
    private val todoListModeState = MutableStateFlow("DATE")
    private val dayEndHourState = MutableStateFlow(dayEndHour)
    private val weekStartState = MutableStateFlow(DayOfWeek.MONDAY)
    private val themeState = MutableStateFlow(AppTheme.SYSTEM)
    val notificationPermissionRequestedState = MutableStateFlow(false)

    override val showCompleted: Flow<Boolean> = showCompletedState
    override val todoListMode: Flow<String> = todoListModeState
    override val dayEndHour: Flow<Int> = dayEndHourState
    override val weekStart: Flow<DayOfWeek> = weekStartState
    override val theme: Flow<AppTheme> = themeState
    override val notificationPermissionRequested: Flow<Boolean> =
        notificationPermissionRequestedState

    override suspend fun setShowCompleted(value: Boolean) {
        showCompletedState.value = value
    }

    override suspend fun setTodoListMode(value: String) {
        todoListModeState.value = value
    }

    override suspend fun setDayEndHour(value: Int) {
        dayEndHourState.value = value
    }

    override suspend fun setWeekStart(value: DayOfWeek) {
        weekStartState.value = value
    }

    override suspend fun setTheme(value: AppTheme) {
        themeState.value = value
    }

    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequestedState.value = value
    }
}

private class FakeNotificationScheduler(
    var state: NotificationSystemState = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    ),
) : NotificationScheduler {
    override val notificationCount: Flow<Int> = flowOf(0)
    val reconciledTodoIds = mutableListOf<String>()

    override fun systemState(): NotificationSystemState = state

    override suspend fun reconcileTodo(todoId: String) {
        reconciledTodoIds += todoId
    }

    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) = Unit
}

private class FakeHolidayRepository(
    holidays: Set<LocalDate> = emptySet(),
    status: HolidayYearStatus? = null,
) : HolidayRepository {
    private val state = HolidaySnapshot(
        namesByDate = holidays.associateWith { "テスト祝日" },
        yearStates = status?.let {
            mapOf(2026 to HolidayYearState(year = 2026, status = it))
        }.orEmpty(),
        supportedYears = if (status == null) emptySet() else setOf(2026),
    )
    override val snapshot: Flow<HolidaySnapshot> = flowOf(state)

    override suspend fun currentSnapshot(): HolidaySnapshot = state
    override suspend fun needsRefresh(): Boolean = false
    override suspend fun refresh(): HolidayRefreshResult = HolidayRefreshResult(successful = true)
    override suspend fun pendingNotificationGeneration(): Long? = null
    override suspend fun markNotificationGenerationProcessed(generation: Long) = Unit
    override suspend fun pendingWidgetGeneration(): Long? = null
    override suspend fun markWidgetGenerationProcessed(generation: Long) = Unit
}

private fun category(id: String, name: String, colorIndex: Int) = Category(
    id = id,
    name = name,
    colorIndex = colorIndex,
    iconName = "Category",
    sortOrder = colorIndex,
)
