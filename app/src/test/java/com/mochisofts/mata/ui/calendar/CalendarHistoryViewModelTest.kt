package com.mochisofts.mata.ui.calendar

import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.HistoryActionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryMonth
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.summarizeHistoryDay
import com.mochisofts.mata.domain.repository.HistoryRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarHistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ch002_initialSelectionUsesTodayAndCurrentMonth() = runTest {
        val viewModel = createViewModel(CalendarTestHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertEquals(TODAY, viewModel.uiState.value.today)
        assertEquals(TODAY, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.from(TODAY), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun ch004_selectTodayReturnsFromPastMonthToToday() = runTest {
        val viewModel = createViewModel(CalendarTestHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.showPreviousMonth()
        runCurrent()
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.displayedMonth)
        assertEquals(LocalDate.of(2026, 8, 6), viewModel.uiState.value.selectedDate)

        viewModel.selectToday()
        runCurrent()
        assertEquals(YearMonth.from(TODAY), viewModel.uiState.value.displayedMonth)
        assertEquals(TODAY, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun ch005_futureDatesAndMonthsCannotBeSelected() = runTest {
        val viewModel = createViewModel(CalendarTestHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.selectDate(TODAY.plusDays(1))
        viewModel.selectMonth(YearMonth.from(TODAY).plusMonths(1))
        viewModel.showNextMonth()
        runCurrent()

        assertEquals(TODAY, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.from(TODAY), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun ch027_regionLoadingEmptyErrorsAndRetriesRemainIndependent() = runTest {
        val repository = CalendarTestHistoryRepository().apply {
            failMonth = true
            failDay = true
        }
        val viewModel = createViewModel(repository)

        assertTrue(viewModel.uiState.value.isMonthLoading)
        assertTrue(viewModel.uiState.value.isDayLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertEquals(R.string.calendar_history_month_load_error, viewModel.uiState.value.monthErrorRes)
        assertEquals(R.string.calendar_history_day_load_error, viewModel.uiState.value.dayErrorRes)
        assertFalse(viewModel.uiState.value.isMonthLoading)
        assertFalse(viewModel.uiState.value.isDayLoading)

        repository.failMonth = false
        repository.failDay = false
        viewModel.refresh()
        runCurrent()

        assertNull(viewModel.uiState.value.monthErrorRes)
        assertNull(viewModel.uiState.value.dayErrorRes)
        assertTrue(viewModel.uiState.value.month.summaries.isEmpty())
        assertTrue(requireNotNull(viewModel.uiState.value.day).entries.isEmpty())
        assertEquals(2, repository.monthLoads)
        assertEquals(2, repository.dayLoads)
    }

    @Test
    fun ch030_staleMonthResponseCannotOverwriteLatestSelection() = runTest {
        val repository = CalendarTestHistoryRepository().apply { blockFirstMonth = true }
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        assertTrue(viewModel.uiState.value.isMonthLoading)

        viewModel.selectMonth(YearMonth.of(2026, 8))
        runCurrent()

        assertTrue(repository.firstMonthCancelled)
        assertTrue(repository.monthLoads >= 2)
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.displayedMonth)
        assertFalse(viewModel.uiState.value.isMonthLoading)

        repository.firstMonthGate.complete(Unit)
        runCurrent()
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.displayedMonth)
    }

    @Test
    fun ch028_undoAndRestoreFailuresReloadConfirmedHistoryState() = runTest {
        val repository = CalendarTestHistoryRepository().apply {
            undoGate = CompletableDeferred()
            undoResult = Result.failure(IllegalStateException("write failed"))
            restoreResult = Result.failure(IllegalStateException("restore failed"))
        }
        val viewModel = createViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        val effect = async { viewModel.effects.first() }

        viewModel.undoAction("first")
        runCurrent()
        viewModel.undoAction("second")
        runCurrent()

        assertEquals(1, repository.undoCalls)
        assertEquals("first", viewModel.uiState.value.busyExecutionId)

        repository.undoGate?.complete(Unit)
        runCurrent()

        assertEquals(
            CalendarHistoryEffect.Message(R.string.calendar_history_undo_error),
            effect.await(),
        )
        assertNull(viewModel.uiState.value.busyExecutionId)
        assertEquals(2, repository.monthLoads)
        assertEquals(2, repository.dayLoads)
        assertTrue(viewModel.uiState.value.month.summaries.isEmpty())
        assertTrue(requireNotNull(viewModel.uiState.value.day).entries.isEmpty())

        val token = HistoryActionUndoToken(
            id = "first",
            operationId = "operation",
            todoId = "todo",
            logicalDate = LocalDate.of(2026, 9, 6),
            state = TodoState.COMPLETED,
            actedAt = 1,
            finalizedAt = 1,
            definitionRevision = 1,
            snapshotVersion = 1,
            snapshotJson = "{}",
        )
        viewModel.restoreAction(token)
        runCurrent()

        assertEquals(1, repository.restoreCalls)
        assertEquals(
            CalendarHistoryEffect.Message(R.string.calendar_history_restore_error),
            viewModel.effects.first(),
        )
        assertNull(viewModel.uiState.value.busyExecutionId)
        assertEquals(3, repository.monthLoads)
        assertEquals(3, repository.dayLoads)
        assertTrue(viewModel.uiState.value.month.summaries.isEmpty())
        assertTrue(requireNotNull(viewModel.uiState.value.day).entries.isEmpty())
    }

    @Test
    fun ch031_repositoryChangesSettingsAndExplicitRefreshReloadVisibleHistory() = runTest {
        val repository = CalendarTestHistoryRepository()
        val settings = CalendarTestSettingsRepository()
        val viewModel = createViewModel(repository, settingsRepository = settings)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        val updatedSummary = summarizeHistoryDay(
            TODAY,
            listOf(TodoState.COMPLETED, TodoState.PENDING),
            emptyList(),
        )
        repository.monthUpdates.value = HistoryMonth(mapOf(TODAY to updatedSummary))
        repository.dayUpdates.value = HistoryDay(
            date = TODAY,
            summary = updatedSummary,
            entries = emptyList(),
            periodResults = emptyList(),
        )
        runCurrent()

        assertEquals(2, viewModel.uiState.value.month.summaries[TODAY]?.plannedCount)
        assertEquals(1, viewModel.uiState.value.day?.summary?.completedCount)

        val beforeSettingMonthLoads = repository.monthLoads
        settings.weekStart.value = DayOfWeek.SUNDAY
        runCurrent()
        assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.weekStart)
        assertTrue(repository.monthLoads > beforeSettingMonthLoads)

        val beforeRefreshMonthLoads = repository.monthLoads
        val beforeRefreshDayLoads = repository.dayLoads
        viewModel.refresh()
        runCurrent()
        assertTrue(repository.monthLoads > beforeRefreshMonthLoads)
        assertTrue(repository.dayLoads > beforeRefreshDayLoads)
    }

    @Test
    fun chd06_freshScreenDoesNotPersistMonthDateOrPositionState() = runTest {
        val first = createViewModel(CalendarTestHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            first.uiState.collect()
        }
        runCurrent()
        first.selectDate(LocalDate.of(2026, 7, 12))
        runCurrent()
        assertEquals(YearMonth.of(2026, 7), first.uiState.value.displayedMonth)

        val reopened = createViewModel(CalendarTestHistoryRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reopened.uiState.collect()
        }
        runCurrent()

        assertEquals(YearMonth.from(TODAY), reopened.uiState.value.displayedMonth)
        assertEquals(TODAY, reopened.uiState.value.selectedDate)
    }

    @Test
    fun holidayChangesUpdateVisibleDatesWithoutReloadingHistory() = runTest {
        val history = CalendarTestHistoryRepository()
        val holidays = CalendarTestHolidayRepository()
        val viewModel = createViewModel(history, holidayRepository = holidays)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertTrue(viewModel.uiState.value.holidayNamesByDate.isEmpty())
        val monthLoads = history.monthLoads
        val dayLoads = history.dayLoads
        val septemberHoliday = LocalDate.of(2026, 9, 22)
        val augustHoliday = LocalDate.of(2026, 8, 11)
        holidays.updates.value = HolidaySnapshot(
            namesByDate = mapOf(septemberHoliday to "国民の休日", augustHoliday to "山の日"),
        )
        runCurrent()

        assertEquals(mapOf(septemberHoliday to "国民の休日"), viewModel.uiState.value.holidayNamesByDate)
        assertEquals(monthLoads, history.monthLoads)
        assertEquals(dayLoads, history.dayLoads)

        viewModel.showPreviousMonth()
        runCurrent()
        assertEquals(mapOf(augustHoliday to "山の日"), viewModel.uiState.value.holidayNamesByDate)

        holidays.updates.value = HolidaySnapshot()
        runCurrent()
        assertTrue(viewModel.uiState.value.holidayNamesByDate.isEmpty())
    }

    @Test
    fun holidayReadFailureDoesNotBlockMonthOrDayHistory() = runTest {
        val viewModel = createViewModel(
            CalendarTestHistoryRepository(),
            holidayRepository = CalendarTestHolidayRepository(failRead = true),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertFalse(viewModel.uiState.value.isMonthLoading)
        assertFalse(viewModel.uiState.value.isDayLoading)
        assertNull(viewModel.uiState.value.monthErrorRes)
        assertNull(viewModel.uiState.value.dayErrorRes)
        assertTrue(viewModel.uiState.value.holidayNamesByDate.isEmpty())
        assertEquals(TODAY, requireNotNull(viewModel.uiState.value.day).date)
    }

    private fun createViewModel(
        repository: CalendarTestHistoryRepository,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        settingsRepository: CalendarTestSettingsRepository = CalendarTestSettingsRepository(),
        holidayRepository: HolidayRepository = CalendarTestHolidayRepository(),
    ) = CalendarHistoryViewModel(
        savedStateHandle = savedStateHandle,
        historyRepository = repository,
        settingsRepository = settingsRepository,
        clock = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
            ZoneId.of("Asia/Tokyo"),
        ),
        holidayRepository = holidayRepository,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 6)
    }
}

private class CalendarTestHolidayRepository(failRead: Boolean = false) : HolidayRepository {
    val updates = MutableStateFlow(HolidaySnapshot())
    override val snapshot: Flow<HolidaySnapshot> = if (failRead) {
        flow { error("holiday read failed") }
    } else {
        updates
    }
    override suspend fun currentSnapshot(): HolidaySnapshot = updates.value
    override suspend fun needsRefresh(): Boolean = false
    override suspend fun refresh(): HolidayRefreshResult = HolidayRefreshResult(successful = true)
    override suspend fun pendingNotificationGeneration(): Long? = null
    override suspend fun markNotificationGenerationProcessed(generation: Long) = Unit
    override suspend fun pendingWidgetGeneration(): Long? = null
    override suspend fun markWidgetGenerationProcessed(generation: Long) = Unit
}

private class CalendarTestHistoryRepository : HistoryRepository {
    var failMonth = false
    var failDay = false
    var monthLoads = 0
    var dayLoads = 0
    var undoCalls = 0
    var restoreCalls = 0
    var undoGate: CompletableDeferred<Unit>? = null
    var undoResult: Result<HistoryActionUndoToken> = Result.failure(IllegalStateException())
    var restoreResult: Result<Unit> = Result.success(Unit)
    var blockFirstMonth = false
    val firstMonthGate = CompletableDeferred<Unit>()
    var firstMonthCancelled = false
    val monthUpdates = MutableStateFlow(HistoryMonth(emptyMap()))
    val dayUpdates = MutableStateFlow(emptyHistoryDay(LocalDate.of(2026, 9, 6)))

    override fun observeMonth(startDate: LocalDate, endDate: LocalDate): Flow<HistoryMonth> = flow {
        monthLoads += 1
        if (blockFirstMonth && monthLoads == 1) {
            try {
                firstMonthGate.await()
            } finally {
                firstMonthCancelled = true
            }
        }
        if (failMonth) error("month load failed")
        emitAll(monthUpdates)
    }

    override fun observeDay(date: LocalDate): Flow<HistoryDay> = flow {
        dayLoads += 1
        if (failDay) error("day load failed")
        emitAll(dayUpdates.map { value ->
            if (value.date == date) value else emptyHistoryDay(date)
        })
    }

    override suspend fun undoAction(executionId: String): Result<HistoryActionUndoToken> {
        undoCalls += 1
        undoGate?.await()
        return undoResult
    }

    override suspend fun restoreAction(token: HistoryActionUndoToken): Result<Unit> {
        restoreCalls += 1
        return restoreResult
    }
}

private fun emptyHistoryDay(date: LocalDate) = HistoryDay(
    date = date,
    summary = summarizeHistoryDay(date, emptyList<TodoState>(), emptyList()),
    entries = emptyList(),
    periodResults = emptyList(),
)

private class CalendarTestSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val dayEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override val archiveSortOrder = MutableStateFlow(ArchiveSortOrder.NEWEST)
    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
    override suspend fun setArchiveSortOrder(value: ArchiveSortOrder) { archiveSortOrder.value = value }
}
