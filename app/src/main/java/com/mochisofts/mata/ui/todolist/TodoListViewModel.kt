package com.mochisofts.mata.ui.todolist

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.R
import com.mochisofts.mata.core.navigation.TodoListRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TodoListMode {
    DATE,
    CATEGORY,
}

data class CategoryTodoItem(
    val todo: Todo,
    val occurrence: TodoOccurrence?,
)

data class TodoListUiState(
    val isLoading: Boolean = true,
    val mode: TodoListMode = TodoListMode.DATE,
    val selectedDate: LocalDate = LocalDate.MIN,
    val isToday: Boolean = true,
    val showCompleted: Boolean = false,
    val occurrences: List<TodoOccurrence> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val categoryItems: List<CategoryTodoItem> = emptyList(),
)

sealed interface TodoListEffect {
    data class Message(@StringRes val messageRes: Int) : TodoListEffect
    data class Completed(val todoId: String, val logicalDate: LocalDate) : TodoListEffect
    data class Skipped(val todoId: String, val logicalDate: LocalDate) : TodoListEffect
    data object Archived : TodoListEffect
    data object Deleted : TodoListEffect
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TodoListRoute>()
    private val selectedDate = MutableStateFlow(
        route.selectedDate?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?: LocalDate.now(clock),
    )
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val effectsChannel = Channel<TodoListEffect>(Channel.BUFFERED)
    val effects: Flow<TodoListEffect> = effectsChannel.receiveAsFlow()

    private val occurrenceFlow = selectedDate.flatMapLatest(todoRepository::observeOccurrences)
    private val todayOccurrenceFlow = todoRepository.observeOccurrences(LocalDate.now(clock))

    private val baseContent = combine(
        occurrenceFlow,
        todayOccurrenceFlow,
        todoRepository.observeTodos(),
        categoryRepository.observeCategories(),
    ) { occurrences, todayOccurrences, todos, categories ->
        BaseContent(occurrences, todayOccurrences, todos, categories)
    }

    private val content = combine(baseContent, selectedDate, selectedCategoryId) { base, date, categoryId ->
        Content(base.occurrences, base.todayOccurrences, base.todos, base.categories, date, categoryId)
    }

    val uiState: StateFlow<TodoListUiState> = combine(
        content,
        settingsRepository.showCompleted,
        settingsRepository.todoListMode,
    ) { content, showCompleted, storedMode ->
        val today = LocalDate.now(clock)
        val currentOccurrences = content.todayOccurrences.associateBy { it.todo.id }
        val visibleOccurrences = content.occurrences.filter { occurrence ->
            occurrence.state != TodoState.SKIPPED &&
                (content.date != today || showCompleted || occurrence.state != TodoState.COMPLETED)
        }
        TodoListUiState(
            isLoading = false,
            mode = storedMode.toTodoListMode(),
            selectedDate = content.date,
            isToday = content.date == today,
            showCompleted = showCompleted,
            occurrences = visibleOccurrences,
            categories = content.categories,
            selectedCategoryId = content.categoryId,
            categoryItems = content.todos
                .filter { it.categoryId == content.categoryId }
                .map { todo -> CategoryTodoItem(todo, currentOccurrences[todo.id]) }
                .filter { item -> item.occurrence?.state != TodoState.SKIPPED },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodoListUiState(selectedDate = selectedDate.value),
    )

    fun selectPreviousDate() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun selectNextDate() {
        selectedDate.value = selectedDate.value.plusDays(1)
    }

    fun selectToday() {
        selectedDate.value = LocalDate.now(clock)
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun selectCategory(id: String?) {
        selectedCategoryId.value = id
    }

    fun setMode(mode: TodoListMode) {
        viewModelScope.launch { settingsRepository.setTodoListMode(mode.name) }
    }

    fun setShowCompleted(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowCompleted(value) }
    }

    fun complete(occurrence: TodoOccurrence) {
        viewModelScope.launch {
            todoRepository.setCompleted(occurrence.todo.id, occurrence.logicalDate, true)
                .onSuccess {
                    effectsChannel.send(
                        TodoListEffect.Completed(occurrence.todo.id, occurrence.logicalDate),
                    )
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

    fun undoCompletion(todoId: String, logicalDate: LocalDate) {
        viewModelScope.launch {
            todoRepository.setCompleted(todoId, logicalDate, false)
                .onFailure { throwable ->
                    effectsChannel.send(
                        TodoListEffect.Message(
                            throwable.toUserMessageRes(R.string.error_todo_undo_completion_failed),
                        ),
                    )
                }
        }
    }

    fun skip(occurrence: TodoOccurrence) {
        viewModelScope.launch {
            todoRepository.setSkipped(occurrence.todo.id, occurrence.logicalDate, true)
                .onSuccess {
                    effectsChannel.send(
                        TodoListEffect.Skipped(occurrence.todo.id, occurrence.logicalDate),
                    )
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

    fun undoSkip(todoId: String, logicalDate: LocalDate) {
        viewModelScope.launch {
            todoRepository.setSkipped(todoId, logicalDate, false)
                .onFailure { throwable ->
                    effectsChannel.send(
                        TodoListEffect.Message(
                            throwable.toUserMessageRes(R.string.error_todo_undo_skip_failed),
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

    private data class Content(
        val occurrences: List<TodoOccurrence>,
        val todayOccurrences: List<TodoOccurrence>,
        val todos: List<Todo>,
        val categories: List<Category>,
        val date: LocalDate,
        val categoryId: String?,
    )

    private data class BaseContent(
        val occurrences: List<TodoOccurrence>,
        val todayOccurrences: List<TodoOccurrence>,
        val todos: List<Todo>,
        val categories: List<Category>,
    )
}

private fun String.toTodoListMode(): TodoListMode =
    runCatching { TodoListMode.valueOf(this) }.getOrDefault(TodoListMode.DATE)
