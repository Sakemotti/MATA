package com.mochisofts.mata.ui.todoeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
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
    val startDate: LocalDate = LocalDate.MIN,
    val recurrenceType: RecurrenceType = RecurrenceType.ONCE,
    val dueMinutes: Int? = null,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving && title.trim().isNotEmpty() && title.trim().length <= 100 &&
            description.length <= 1000
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
    clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TodoEditorRoute>()
    private val _uiState = MutableStateFlow(
        TodoEditorUiState(isNew = route.todoId == null, startDate = LocalDate.now(clock)),
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
            val todo = route.todoId?.let { todoRepository.getTodo(it) }
            _uiState.update { state ->
                if (route.todoId != null && todo == null) {
                    state.copy(isLoading = false, error = "TODOが見つかりません")
                } else if (todo == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        isLoading = false,
                        title = todo.title,
                        description = todo.description,
                        categoryId = todo.categoryId,
                        startDate = todo.startDate,
                        recurrenceType = todo.recurrenceType,
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
    fun setRecurrence(value: RecurrenceType) = edit { copy(recurrenceType = value) }
    fun setDueMinutes(value: Int?) = edit { copy(dueMinutes = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            todoRepository.saveTodo(
                id = route.todoId,
                title = state.title,
                description = state.description,
                categoryId = state.categoryId,
                startDate = state.startDate,
                recurrenceType = state.recurrenceType,
                dueMinutes = state.dueMinutes,
            ).onSuccess {
                effectsChannel.send(TodoEditorEffect.Saved(route.todoId == null))
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(isSaving = false, error = throwable.message ?: "TODOを保存できませんでした")
                }
            }
        }
    }

    fun delete() {
        val todoId = route.todoId ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
                .onSuccess { effectsChannel.send(TodoEditorEffect.Deleted) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isSaving = false, error = "TODOを削除できませんでした")
                    }
                }
        }
    }

    private fun edit(transform: TodoEditorUiState.() -> TodoEditorUiState) {
        _uiState.update { state -> state.transform().copy(isDirty = true, error = null) }
    }
}

