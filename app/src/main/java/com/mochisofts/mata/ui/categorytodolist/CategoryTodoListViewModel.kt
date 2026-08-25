package com.mochisofts.mata.ui.categorytodolist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CategoryTodoListItem(
    val todo: Todo,
    val todayState: TodoState?,
)

data class CategoryTodoListUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val items: List<CategoryTodoListItem> = emptyList(),
)

@HiltViewModel
class CategoryTodoListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    clock: Clock,
    adsConsentRepository: AdsConsentRepository,
) : ViewModel() {
    private val selectedCategoryId =
        savedStateHandle.getStateFlow<String?>(SELECTED_CATEGORY_ID_KEY, null)

    val adsRuntimeState = adsConsentRepository.state

    val uiState: StateFlow<CategoryTodoListUiState> = combine(
        categoryRepository.observeCategories(),
        todoRepository.observeTodos(),
        todoRepository.observeOccurrences(LocalDate.now(clock)),
        selectedCategoryId,
        ::buildCategoryTodoListUiState,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryTodoListUiState(),
    )

    fun selectCategory(categoryId: String?) {
        savedStateHandle[SELECTED_CATEGORY_ID_KEY] = categoryId
    }

    private companion object {
        const val SELECTED_CATEGORY_ID_KEY = "category_todo_list_selected_category_id"
    }
}

internal fun buildCategoryTodoListUiState(
    categories: List<Category>,
    todos: List<Todo>,
    todayOccurrences: List<TodoOccurrence>,
    requestedCategoryId: String?,
): CategoryTodoListUiState {
    val selectedCategoryId = requestedCategoryId?.takeIf { requested ->
        categories.any { it.id == requested }
    }
    val statesByTodoId = todayOccurrences.associate { it.todo.id to it.state }
    return CategoryTodoListUiState(
        isLoading = false,
        categories = categories.sortedBy(Category::sortOrder),
        selectedCategoryId = selectedCategoryId,
        items = todos.asSequence()
            .filter { it.categoryId == selectedCategoryId }
            .sortedBy(Todo::createdAt)
            .map { todo ->
                CategoryTodoListItem(
                    todo = todo,
                    todayState = statesByTodoId[todo.id],
                )
            }
            .toList(),
    )
}
