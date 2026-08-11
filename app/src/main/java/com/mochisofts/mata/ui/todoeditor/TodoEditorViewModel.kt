package com.mochisofts.mata.ui.todoeditor

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.R
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val description: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    val today: LocalDate = LocalDate.MIN,
    val startDate: LocalDate = LocalDate.MIN,
    val endDate: LocalDate? = null,
    val recurrenceType: RecurrenceType = RecurrenceType.ONCE,
    val selectedWeekdays: Set<DayOfWeek> = emptySet(),
    val monthlyDay: Int = 1,
    val intervalDaysInput: String = "1",
    val weeklyCount: Int = 1,
    val monthlyCount: Int = 1,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val dueMinutes: Int? = null,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
) {
    val recurrenceRule: RecurrenceRule
        get() = RecurrenceRule(
            type = recurrenceType,
            selectedWeekdays = selectedWeekdays,
            monthlyDay = monthlyDay.takeIf { recurrenceType == RecurrenceType.MONTHLY_DAY },
            intervalDays = intervalDaysInput.toIntOrNull()
                ?.takeIf { recurrenceType == RecurrenceType.EVERY_N_DAYS },
            requiredCount = when (recurrenceType) {
                RecurrenceType.WEEKLY_COUNT -> weeklyCount
                RecurrenceType.MONTHLY_COUNT -> monthlyCount
                else -> null
            },
        )

    val canSave: Boolean
        get() = !isLoading && !isSaving && title.trim().isNotEmpty() && title.trim().length <= 100 &&
            description.length <= 1000 && recurrenceRule.isValid() &&
            (recurrenceType == RecurrenceType.ONCE || endDate == null || !endDate.isBefore(startDate))
}

sealed interface TodoEditorEffect {
    data class Saved(val isNew: Boolean) : TodoEditorEffect
    data object Deleted : TodoEditorEffect
}

@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    settingsRepository: SettingsRepository,
    clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TodoEditorRoute>()
    private val today = LocalDate.now(clock)
    private val _uiState = MutableStateFlow(
        TodoEditorUiState(isNew = route.todoId == null, today = today, startDate = today),
    )
    val uiState: StateFlow<TodoEditorUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<TodoEditorEffect>(Channel.BUFFERED)
    val effects: Flow<TodoEditorEffect> = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { categories ->
                _uiState.update { state -> state.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            settingsRepository.weekStart.collect { weekStart ->
                _uiState.update { state -> state.copy(weekStart = weekStart) }
            }
        }
        viewModelScope.launch {
            val todo = route.todoId?.let { todoRepository.getTodo(it) }
            _uiState.update { state ->
                if (route.todoId != null && todo == null) {
                    state.copy(isLoading = false, errorMessageRes = R.string.error_todo_not_found)
                } else if (todo == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        isLoading = false,
                        title = todo.title,
                        description = todo.description,
                        categoryId = todo.categoryId,
                        startDate = todo.startDate,
                        endDate = todo.endDate.takeUnless { todo.recurrenceType == RecurrenceType.ONCE },
                        recurrenceType = todo.recurrenceType,
                        selectedWeekdays = todo.recurrenceRule.selectedWeekdays,
                        monthlyDay = todo.recurrenceRule.monthlyDay ?: todo.startDate.dayOfMonth,
                        intervalDaysInput = (todo.recurrenceRule.intervalDays ?: 1).toString(),
                        weeklyCount = todo.recurrenceRule.requiredCount ?: 1,
                        monthlyCount = todo.recurrenceRule.requiredCount ?: 1,
                        dueMinutes = todo.dueMinutes,
                    )
                }
            }
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setDescription(value: String) = edit { copy(description = value) }
    fun setCategory(value: String?) = edit { copy(categoryId = value) }
    fun setStartDate(value: LocalDate) = edit { copy(startDate = value) }
    fun setEndDate(value: LocalDate?) = edit { copy(endDate = value) }
    fun setRecurrence(value: RecurrenceType) = edit {
        copy(
            recurrenceType = value,
            selectedWeekdays = if (
                value == RecurrenceType.SELECTED_WEEKDAYS && selectedWeekdays.isEmpty()
            ) {
                setOf(startDate.dayOfWeek)
            } else {
                selectedWeekdays
            },
            monthlyDay = if (value == RecurrenceType.MONTHLY_DAY && monthlyDay == 1) {
                startDate.dayOfMonth
            } else {
                monthlyDay
            },
        )
    }
    fun toggleWeekday(value: DayOfWeek) = edit {
        copy(
            selectedWeekdays = if (value in selectedWeekdays) {
                selectedWeekdays - value
            } else {
                selectedWeekdays + value
            },
        )
    }
    fun setMonthlyDay(value: Int) = edit { copy(monthlyDay = value) }
    fun setIntervalDays(value: String) = edit {
        copy(intervalDaysInput = value.filter(Char::isDigit).take(3))
    }
    fun setWeeklyCount(value: Int) = edit { copy(weeklyCount = value) }
    fun setMonthlyCount(value: Int) = edit { copy(monthlyCount = value) }
    fun setDueMinutes(value: Int?) = edit { copy(dueMinutes = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            todoRepository.saveTodo(
                id = route.todoId,
                title = state.title,
                description = state.description,
                categoryId = state.categoryId,
                startDate = state.startDate,
                endDate = state.endDate.takeUnless { state.recurrenceType == RecurrenceType.ONCE },
                recurrenceRule = state.recurrenceRule,
                dueMinutes = state.dueMinutes,
            ).onSuccess {
                effectsChannel.send(TodoEditorEffect.Saved(route.todoId == null))
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = throwable.toUserMessageRes(R.string.error_todo_save_failed),
                    )
                }
            }
        }
    }

    fun delete() {
        val todoId = route.todoId ?: return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
                .onSuccess { effectsChannel.send(TodoEditorEffect.Deleted) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            errorMessageRes = R.string.error_todo_delete_failed,
                        )
                    }
                }
        }
    }

    private fun edit(transform: TodoEditorUiState.() -> TodoEditorUiState) {
        _uiState.update {
            state -> state.transform().copy(isDirty = true, errorMessageRes = null)
        }
    }
}
