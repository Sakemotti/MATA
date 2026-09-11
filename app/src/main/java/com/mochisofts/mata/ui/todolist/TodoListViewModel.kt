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
import com.mochisofts.mata.domain.model.effectiveDueSortMinutes
import com.mochisofts.mata.domain.model.usesHolidayData
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.core.ads.AdsConsentRepository
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    val hasLoadError: Boolean = false,
    val selectedDate: LocalDate = LocalDate.MIN,
    val isToday: Boolean = true,
    val showCompleted: Boolean = false,
    val completedCount: Int = 0,
    val plannedCount: Int = 0,
    val groups: List<TodoOccurrenceGroup> = emptyList(),
    val holidayName: String? = null,
    val holidayStatus: HolidayYearStatus? = null,
    val holidayDataAvailable: Boolean = false,
)

private sealed interface TodoListLoadState {
    data object Loading : TodoListLoadState
    data class Data(val content: TodoListContent) : TodoListLoadState
    data object Error : TodoListLoadState
}

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
    private val activeTodoOperations = mutableSetOf<String>()
    private val loadGeneration = MutableStateFlow(0)
    val effects: Flow<TodoListEffect> = effectsChannel.receiveAsFlow()
    val adsRuntimeState = adsConsentRepository.state

    init {
        if (route.showTodoNotFound) {
            effectsChannel.trySend(TodoListEffect.Message(R.string.error_todo_not_found))
        }
    }

    private val content = loadGeneration.flatMapLatest {
        observeTodoListContent(
            selectedDate = dateSelection.requests,
            occurrencesForDate = todoRepository::observeOccurrences,
            todos = todoRepository.observeTodos(),
            holidaySnapshot = holidayRepository.snapshot,
        ).map<TodoListContent, TodoListLoadState>(TodoListLoadState::Data)
            .onStart { emit(TodoListLoadState.Loading) }
            .catch { emit(TodoListLoadState.Error) }
    }

    val uiState: StateFlow<TodoListUiState> = combine(
        content,
        settingsRepository.showCompleted,
        settingsRepository.dayEndHour,
    ) { loadState, showCompleted, dayEndHour ->
        val loaded = (loadState as? TodoListLoadState.Data)?.content
        if (loaded == null) {
            return@combine TodoListUiState(
                isLoading = loadState is TodoListLoadState.Loading,
                hasLoadError = loadState is TodoListLoadState.Error,
                selectedDate = dateSelection.value,
                isToday = dateSelection.value == LocalDate.now(clock),
                showCompleted = showCompleted,
            )
        }
        val today = LocalDate.now(clock)
        val plannedOccurrences = loaded.occurrences.filter { occurrence ->
            occurrence.state != TodoState.SKIPPED
        }
        val visibleOccurrences = loaded.occurrences.filter { occurrence ->
            occurrence.state != TodoState.SKIPPED &&
                (loaded.date != today || showCompleted || occurrence.state != TodoState.COMPLETED)
        }
        TodoListUiState(
            isLoading = false,
            selectedDate = loaded.date,
            isToday = loaded.date == today,
            showCompleted = showCompleted,
            completedCount = plannedOccurrences.count { occurrence ->
                occurrence.state == TodoState.COMPLETED
            },
            plannedCount = plannedOccurrences.size,
            groups = buildTodoOccurrenceGroups(visibleOccurrences, dayEndHour),
            holidayName = loaded.holidaySnapshot.holidayName(loaded.date),
            holidayStatus = loaded.holidaySnapshot.statusFor(loaded.date.year)
                .takeIf {
                    loaded.todos.any { todo -> todo.recurrenceRule.usesHolidayData() }
                },
            holidayDataAvailable = loaded.holidaySnapshot.isDefinitive(loaded.date.year),
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

    fun retryLoad() {
        loadGeneration.update(Int::inc)
    }

    fun setShowCompleted(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowCompleted(value) }
    }

    fun complete(occurrence: TodoOccurrence) {
        runTodoOperation(
            todoId = occurrence.todo.id,
            successEffect = TodoListEffect.Completed,
            fallbackErrorRes = R.string.error_todo_complete_failed,
        ) {
            todoRepository.setCompleted(
                occurrence.todo.id,
                occurrence.logicalDate,
                true,
                scheduledLogicalDate = occurrence.scheduledLogicalDate,
            )
        }
    }

    fun skip(occurrence: TodoOccurrence) {
        runTodoOperation(
            todoId = occurrence.todo.id,
            successEffect = TodoListEffect.Skipped,
            fallbackErrorRes = R.string.error_todo_skip_failed,
        ) {
            todoRepository.setSkipped(
                occurrence.todo.id,
                occurrence.logicalDate,
                true,
                scheduledLogicalDate = occurrence.scheduledLogicalDate,
            )
        }
    }

    fun archive(todoId: String) {
        runTodoOperation(
            todoId = todoId,
            successEffect = TodoListEffect.Archived,
            fallbackErrorRes = R.string.error_todo_archive_failed,
        ) {
            todoRepository.archiveTodo(todoId)
        }
    }

    fun delete(todoId: String) {
        runTodoOperation(
            todoId = todoId,
            successEffect = TodoListEffect.Deleted,
            fallbackErrorRes = R.string.error_todo_delete_failed,
        ) {
            todoRepository.deleteTodo(todoId)
        }
    }

    private fun runTodoOperation(
        todoId: String,
        successEffect: TodoListEffect,
        @StringRes fallbackErrorRes: Int,
        operation: suspend () -> Result<Unit>,
    ) {
        if (!activeTodoOperations.add(todoId)) return
        viewModelScope.launch {
            try {
                operation()
                    .onSuccess { effectsChannel.send(successEffect) }
                    .onFailure { throwable ->
                        effectsChannel.send(
                            TodoListEffect.Message(
                                throwable.toUserMessageRes(fallbackErrorRes),
                            ),
                        )
                    }
            } finally {
                activeTodoOperations.remove(todoId)
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
                    it.todo.effectiveDueSortMinutes(dayEndHour)
                }.thenBy { it.todo.createdAt }
                    .thenBy { it.todo.id },
            ),
        )
    }
    .sortedWith(
        compareBy<TodoOccurrenceGroup> { it.category?.sortOrder ?: -1 }
            .thenBy { it.category?.id.orEmpty() },
    )
