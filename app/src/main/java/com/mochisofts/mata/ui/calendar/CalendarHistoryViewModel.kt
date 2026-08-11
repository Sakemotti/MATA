package com.mochisofts.mata.ui.calendar

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.CompletionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryMonth
import com.mochisofts.mata.domain.repository.HistoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarHistoryUiState(
    val displayedMonth: YearMonth,
    val selectedDate: LocalDate,
    val today: LocalDate,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val gridDates: List<LocalDate> = emptyList(),
    val month: HistoryMonth = HistoryMonth(emptyMap()),
    val day: HistoryDay? = null,
    val isMonthLoading: Boolean = true,
    val isDayLoading: Boolean = true,
    @StringRes val monthErrorRes: Int? = null,
    @StringRes val dayErrorRes: Int? = null,
    val busyExecutionId: String? = null,
)

sealed interface CalendarHistoryEffect {
    data class CompletionUndone(val token: CompletionUndoToken) : CalendarHistoryEffect
    data class Message(@StringRes val messageRes: Int) : CalendarHistoryEffect
}

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Data<T>(val value: T) : LoadState<T>
    data class Error(@StringRes val messageRes: Int) : LoadState<Nothing>
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarHistoryViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val historyRepository: HistoryRepository,
    settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val initialToday = LocalDate.now(clock)
    private val today = MutableStateFlow(initialToday)
    private val displayedMonth = savedStateHandle.getStateFlow(
        KEY_DISPLAYED_MONTH,
        YearMonth.from(initialToday).toString(),
    ).map(YearMonth::parse)
    private val selectedDate = savedStateHandle.getStateFlow(
        KEY_SELECTED_DATE,
        initialToday.toString(),
    ).map(LocalDate::parse)
    private val refreshGeneration = MutableStateFlow(0)
    private val busyExecutionId = MutableStateFlow<String?>(null)
    private val effectsChannel = Channel<CalendarHistoryEffect>(Channel.BUFFERED)
    val effects: Flow<CalendarHistoryEffect> = effectsChannel.receiveAsFlow()

    private val selection = combine(
        displayedMonth,
        selectedDate,
        settingsRepository.weekStart,
        today,
    ) { month, date, weekStart, currentDate ->
        CalendarSelection(month, date, currentDate, weekStart, calendarGridDates(month, weekStart))
    }

    private val monthLoad = combine(selection, refreshGeneration) { value, _ -> value }
        .flatMapLatest { value ->
            val dates = value.gridDates
            if (dates.isEmpty()) {
                flowOf(LoadState.Data(HistoryMonth(emptyMap())))
            } else {
                historyRepository.observeMonth(dates.first(), dates.last())
                    .map<HistoryMonth, LoadState<HistoryMonth>> { value -> LoadState.Data(value) }
                    .onStart { emit(LoadState.Loading) }
                    .catch { emit(LoadState.Error(R.string.calendar_history_month_load_error)) }
            }
        }

    private val dayLoad = combine(selectedDate, refreshGeneration) { date, _ -> date }
        .flatMapLatest { date ->
            historyRepository.observeDay(date)
                .map<HistoryDay, LoadState<HistoryDay>> { value -> LoadState.Data(value) }
                .onStart { emit(LoadState.Loading) }
                .catch { emit(LoadState.Error(R.string.calendar_history_day_load_error)) }
        }

    val uiState: StateFlow<CalendarHistoryUiState> = combine(
        selection,
        monthLoad,
        dayLoad,
        busyExecutionId,
    ) { selection, monthState, dayState, busyId ->
        CalendarHistoryUiState(
            displayedMonth = selection.month,
            selectedDate = selection.selectedDate,
            today = selection.today,
            weekStart = selection.weekStart,
            gridDates = selection.gridDates,
            month = (monthState as? LoadState.Data)?.value ?: HistoryMonth(emptyMap()),
            day = (dayState as? LoadState.Data)?.value,
            isMonthLoading = monthState is LoadState.Loading,
            isDayLoading = dayState is LoadState.Loading,
            monthErrorRes = (monthState as? LoadState.Error)?.messageRes,
            dayErrorRes = (dayState as? LoadState.Error)?.messageRes,
            busyExecutionId = busyId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarHistoryUiState(
            displayedMonth = YearMonth.from(initialToday),
            selectedDate = initialToday,
            today = initialToday,
        ),
    )

    fun showPreviousMonth() {
        selectMonth(uiState.value.displayedMonth.minusMonths(1))
    }

    fun showNextMonth() {
        val next = uiState.value.displayedMonth.plusMonths(1)
        if (!next.isAfter(YearMonth.from(uiState.value.today))) selectMonth(next)
    }

    fun selectMonth(month: YearMonth) {
        val currentMonth = YearMonth.from(uiState.value.today)
        if (month.isAfter(currentMonth)) return
        savedStateHandle[KEY_DISPLAYED_MONTH] = month.toString()
        val preferredDay = uiState.value.selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        val date = month.atDay(preferredDay).coerceAtMost(uiState.value.today)
        savedStateHandle[KEY_SELECTED_DATE] = date.toString()
    }

    fun selectDate(date: LocalDate) {
        if (date.isAfter(uiState.value.today)) return
        savedStateHandle[KEY_SELECTED_DATE] = date.toString()
        savedStateHandle[KEY_DISPLAYED_MONTH] = YearMonth.from(date).toString()
    }

    fun selectToday() {
        savedStateHandle[KEY_SELECTED_DATE] = uiState.value.today.toString()
        savedStateHandle[KEY_DISPLAYED_MONTH] = YearMonth.from(uiState.value.today).toString()
    }

    fun refresh() {
        val currentDate = LocalDate.now(clock)
        today.value = currentDate
        if (uiState.value.selectedDate.isAfter(currentDate)) {
            savedStateHandle[KEY_SELECTED_DATE] = currentDate.toString()
            savedStateHandle[KEY_DISPLAYED_MONTH] = YearMonth.from(currentDate).toString()
        }
        refreshGeneration.update(Int::inc)
    }

    fun undoCompletion(executionId: String) {
        if (busyExecutionId.value != null) return
        busyExecutionId.value = executionId
        viewModelScope.launch {
            historyRepository.undoCompletion(executionId)
                .onSuccess { token ->
                    effectsChannel.send(CalendarHistoryEffect.CompletionUndone(token))
                }
                .onFailure { throwable ->
                    effectsChannel.send(
                        CalendarHistoryEffect.Message(
                            throwable.toUserMessageRes(R.string.calendar_history_undo_error),
                        ),
                    )
                }
            busyExecutionId.value = null
        }
    }

    fun restoreCompletion(token: CompletionUndoToken) {
        if (busyExecutionId.value != null) return
        busyExecutionId.value = token.id
        viewModelScope.launch {
            historyRepository.restoreCompletion(token)
                .onFailure { throwable ->
                    effectsChannel.send(
                        CalendarHistoryEffect.Message(
                            throwable.toUserMessageRes(R.string.calendar_history_restore_error),
                        ),
                    )
                }
            busyExecutionId.value = null
        }
    }

    private data class CalendarSelection(
        val month: YearMonth,
        val selectedDate: LocalDate,
        val today: LocalDate,
        val weekStart: DayOfWeek,
        val gridDates: List<LocalDate>,
    )

    private companion object {
        const val KEY_DISPLAYED_MONTH = "calendar_displayed_month"
        const val KEY_SELECTED_DATE = "calendar_selected_date"
    }
}

internal fun calendarGridDates(month: YearMonth, weekStart: DayOfWeek): List<LocalDate> {
    val firstOfMonth = month.atDay(1)
    val leadingDays = (firstOfMonth.dayOfWeek.value - weekStart.value + 7) % 7
    val gridStart = firstOfMonth.minusDays(leadingDays.toLong())
    return List(42) { index -> gridStart.plusDays(index.toLong()) }
}

private fun LocalDate.coerceAtMost(maximum: LocalDate): LocalDate = minOf(this, maximum)
