package com.mochisofts.mata.ui.todolist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.R
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.HolidayYearStatus
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.usesHolidayData
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoOccurrenceGroup(
    val category: Category?,
    val occurrences: List<TodoOccurrence>,
)

internal data class TodoListContent(
    val occurrences: List<TodoOccurrence>,
    val todos: List<Todo>,
    val holidaySnapshot: HolidaySnapshot,
    val date: LocalDate,
)

data class TodoListUiState(
    val isLoading: Boolean = true,
    val selectedDate: LocalDate = LocalDate.MIN,
    val isToday: Boolean = true,
    val showCompleted: Boolean = false,
    val groups: List<TodoOccurrenceGroup> = emptyList(),
    val holidayName: String? = null,
    val holidayStatus: HolidayYearStatus? = null,
    val holidayDataAvailable: Boolean = false,
)

sealed interface TodoListEffect {
    data class Message(@StringRes val messageRes: Int) : TodoListEffect
    data object Completed : TodoListEffect
    data object Skipped : TodoListEffect
    data object Archived : TodoListEffect
    data object Deleted : TodoListEffect
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todoRepository: TodoRepository,
    holidayRepository: HolidayRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    adsConsentRepository: AdsConsentRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TodoListRoute>()
    private val dateSelection = TodoListDateSelection(
        initialDate = route.selectedDate
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?: LocalDate.now(clock),
        followsTodayInitially = route.selectedDate == null,
    )
    private val effectsChannel = Channel<TodoListEffect>(Channel.BUFFERED)
    val effects: Flow<TodoListEffect> = effectsChannel.receiveAsFlow()
    val adsRuntimeState = adsConsentRepository.state

    init {
        if (route.showTodoNotFound) {
            effectsChannel.trySend(TodoListEffect.Message(R.string.error_todo_not_found))
        }
    }

    private val content = observeTodoListContent(
        selectedDate = dateSelection.requests,
        occurrencesForDate = todoRepository::observeOccurrences,
        todos = todoRepository.observeTodos(),
        holidaySnapshot = holidayRepository.snapshot,
    )

    val uiState: StateFlow<TodoListUiState> = combine(
        content,
        settingsRepository.showCompleted,
        settingsRepository.dayEndHour,
    ) { content, showCompleted, dayEndHour ->
        val today = LocalDate.now(clock)
        val visibleOccurrences = content.occurrences.filter { occurrence ->
            occurrence.state != TodoState.SKIPPED &&
                (content.date != today || showCompleted || occurrence.state != TodoState.COMPLETED)
        }
        TodoListUiState(
            isLoading = false,
            selectedDate = content.date,
            isToday = content.date == today,
            showCompleted = showCompleted,
            groups = buildTodoOccurrenceGroups(visibleOccurrences, dayEndHour),
            holidayName = content.holidaySnapshot.holidayName(content.date),
            holidayStatus = content.holidaySnapshot.statusFor(content.date.year)
                .takeIf {
                    content.todos.any { todo -> todo.recurrenceRule.usesHolidayData() }
                },
            holidayDataAvailable = content.holidaySnapshot.isDefinitive(content.date.year),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodoListUiState(selectedDate = dateSelection.value),
    )

    fun selectPreviousDate() {
        dateSelection.select(dateSelection.value.minusDays(1))
    }

    fun selectNextDate() {
        dateSelection.select(dateSelection.value.plusDays(1))
    }

    fun selectToday() {
        dateSelection.selectToday(LocalDate.now(clock))
    }

    fun selectDate(date: LocalDate) {
        dateSelection.select(date)
    }

    fun refresh() {
        dateSelection.refresh(LocalDate.now(clock))
    }

    fun setShowCompleted(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowCompleted(value) }
    }

    fun complete(occurrence: TodoOccurrence) {
        viewModelScope.launch {
            todoRepository.setCompleted(occurrence.todo.id, occurrence.logicalDate, true)
                .onSuccess {
                    effectsChannel.send(TodoListEffect.Completed)
                }
                .onFailure { throwable ->
                    effectsChannel.send(
                        TodoListEffect.Message(
                            throwable.toUserMessageRes(R.string.error_todo_complete_failed),
                        ),
                    )
                }
        }
    }

    fun skip(occurrence: TodoOccurrence) {
        viewModelScope.launch {
            todoRepository.setSkipped(occurrence.todo.id, occurrence.logicalDate, true)
                .onSuccess {
                    effectsChannel.send(TodoListEffect.Skipped)
                }
                .onFailure { throwable ->
                    effectsChannel.send(
                        TodoListEffect.Message(
                            throwable.toUserMessageRes(R.string.error_todo_skip_failed),
                        ),
                    )
                }
        }
    }

    fun archive(todoId: String) {
        viewModelScope.launch {
            todoRepository.archiveTodo(todoId)
                .onSuccess { effectsChannel.send(TodoListEffect.Archived) }
                .onFailure { throwable ->
                    effectsChannel.send(
                        TodoListEffect.Message(
                            throwable.toUserMessageRes(R.string.error_todo_archive_failed),
                        ),
                    )
                }
        }
    }

    fun delete(todoId: String) {
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
                .onSuccess { effectsChannel.send(TodoListEffect.Deleted) }
                .onFailure { throwable ->
                    effectsChannel.send(
                        TodoListEffect.Message(
                            throwable.toUserMessageRes(R.string.error_todo_delete_failed),
                        ),
                    )
                }
        }
    }
}

internal class TodoListDateSelection(
    initialDate: LocalDate,
    followsTodayInitially: Boolean,
) {
    private val selectedDate = MutableStateFlow(initialDate)
    private val refreshGeneration = MutableStateFlow(0)
    private var followsToday = followsTodayInitially

    val value: LocalDate
        get() = selectedDate.value

    val requests: Flow<LocalDate> = combine(
        selectedDate,
        refreshGeneration,
    ) { date, _ -> date }

    fun select(date: LocalDate) {
        followsToday = false
        selectedDate.value = date
    }

    fun selectToday(today: LocalDate) {
        followsToday = true
        if (selectedDate.value == today) {
            refreshGeneration.update(Int::inc)
        } else {
            selectedDate.value = today
        }
    }

    fun refresh(today: LocalDate) {
        if (followsToday && selectedDate.value != today) {
            selectedDate.value = today
        } else {
            refreshGeneration.update(Int::inc)
        }
    }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal fun observeTodoListContent(
    selectedDate: Flow<LocalDate>,
    occurrencesForDate: (LocalDate) -> Flow<List<TodoOccurrence>>,
    todos: Flow<List<Todo>>,
    holidaySnapshot: Flow<HolidaySnapshot>,
): Flow<TodoListContent> {
    val datedOccurrences = selectedDate.flatMapLatest { date ->
        occurrencesForDate(date).map { occurrences -> date to occurrences }
    }
    return combine(datedOccurrences, todos, holidaySnapshot) { (date, occurrences), todoList, holidays ->
        TodoListContent(
            occurrences = occurrences,
            todos = todoList,
            holidaySnapshot = holidays,
            date = date,
        )
    }
}

internal fun buildTodoOccurrenceGroups(
    occurrences: List<TodoOccurrence>,
    dayEndHour: Int,
): List<TodoOccurrenceGroup> = occurrences
    .groupBy { occurrence -> occurrence.category?.id }
    .map { (_, items) ->
        val category = items.firstOrNull()?.category
        TodoOccurrenceGroup(
            category = category,
            occurrences = items.sortedWith(
                compareBy<TodoOccurrence> {
                    it.effectiveDueMinutes(dayEndHour)
                }.thenBy { it.todo.createdAt }
                    .thenBy { it.todo.id },
            ),
        )
    }
    .sortedWith(
        compareBy<TodoOccurrenceGroup> { it.category?.sortOrder ?: -1 }
            .thenBy { it.category?.id.orEmpty() },
    )

private fun TodoOccurrence.effectiveDueMinutes(dayEndHour: Int): Int {
    val due = todo.dueMinutes ?: dayEndHour * MINUTES_PER_HOUR
    return due + if (todo.dueMinutes == null || due < dayEndHour * MINUTES_PER_HOUR) {
        MINUTES_PER_DAY
    } else {
        0
    }
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 1_440
