package com.mochisofts.mata.ui.todolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
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
    data class Message(val text: String) : TodoListEffect
    data class Completed(val todoId: String, val logicalDate: LocalDate) : TodoListEffect
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now(clock))
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val effectsChannel = Channel<TodoListEffect>(Channel.BUFFERED)
    val effects: Flow<TodoListEffect> = effectsChannel.receiveAsFlow()

    private val occurrenceFlow = selectedDate.flatMapLatest(todoRepository::observeOccurrences)

    private val content = combine(
        occurrenceFlow,
        todoRepository.observeTodos(),
        categoryRepository.observeCategories(),
        selectedDate,
        selectedCategoryId,
    ) { occurrences, todos, categories, date, categoryId ->
        Content(occurrences, todos, categories, date, categoryId)
    }

    val uiState: StateFlow<TodoListUiState> = combine(
        content,
        settingsRepository.showCompleted,
        settingsRepository.todoListMode,
    ) { content, showCompleted, storedMode ->
        val today = LocalDate.now(clock)
        val currentOccurrences = content.occurrences.associateBy { it.todo.id }
        val visibleOccurrences = if (content.date == today && !showCompleted) {
            content.occurrences.filter { it.state != TodoState.COMPLETED }
        } else {
            content.occurrences
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
                .map { todo -> CategoryTodoItem(todo, currentOccurrences[todo.id]) },
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
                .onFailure { effectsChannel.send(TodoListEffect.Message("完了にできませんでした")) }
        }
    }

    fun undoCompletion(todoId: String, logicalDate: LocalDate) {
        viewModelScope.launch {
            todoRepository.setCompleted(todoId, logicalDate, false)
                .onFailure { effectsChannel.send(TodoListEffect.Message("完了を取り消せませんでした")) }
        }
    }

    private data class Content(
        val occurrences: List<TodoOccurrence>,
        val todos: List<Todo>,
        val categories: List<Category>,
        val date: LocalDate,
        val categoryId: String?,
    )
}

private fun String.toTodoListMode(): TodoListMode =
    runCatching { TodoListMode.valueOf(this) }.getOrDefault(TodoListMode.DATE)

