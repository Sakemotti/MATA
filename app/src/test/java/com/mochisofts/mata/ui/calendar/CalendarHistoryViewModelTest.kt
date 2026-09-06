package com.mochisofts.mata.ui.calendar

import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.HistoryActionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryMonth
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.summarizeHistoryDay
import com.mochisofts.mata.domain.repository.HistoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    fun loadErrorsRemainIndependentAndRefreshRetriesBothRegions() = runTest {
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
    fun undoFailureReportsErrorAndBlocksConcurrentHistoryMutation() = runTest {
        val repository = CalendarTestHistoryRepository().apply {
            undoGate = CompletableDeferred()
            undoResult = Result.failure(IllegalStateException("write failed"))
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
    }

    private fun createViewModel(repository: CalendarTestHistoryRepository) = CalendarHistoryViewModel(
        savedStateHandle = SavedStateHandle(),
        historyRepository = repository,
        settingsRepository = CalendarTestSettingsRepository(),
        clock = Clock.fixed(
            LocalDate.of(2026, 9, 6).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
            ZoneId.of("Asia/Tokyo"),
        ),
    )
}

private class CalendarTestHistoryRepository : HistoryRepository {
    var failMonth = false
    var failDay = false
    var monthLoads = 0
    var dayLoads = 0
    var undoCalls = 0
    var undoGate: CompletableDeferred<Unit>? = null
    var undoResult: Result<HistoryActionUndoToken> = Result.failure(IllegalStateException())

    override fun observeMonth(startDate: LocalDate, endDate: LocalDate): Flow<HistoryMonth> = flow {
        monthLoads += 1
        if (failMonth) error("month load failed")
        emit(HistoryMonth(emptyMap()))
    }

    override fun observeDay(date: LocalDate): Flow<HistoryDay> = flow {
        dayLoads += 1
        if (failDay) error("day load failed")
        emit(
            HistoryDay(
                date = date,
                summary = summarizeHistoryDay(date, emptyList<TodoState>(), emptyList()),
                entries = emptyList(),
                periodResults = emptyList(),
            ),
        )
    }

    override suspend fun undoAction(executionId: String): Result<HistoryActionUndoToken> {
        undoCalls += 1
        undoGate?.await()
        return undoResult
    }

    override suspend fun restoreAction(token: HistoryActionUndoToken): Result<Unit> = Result.success(Unit)
}

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
