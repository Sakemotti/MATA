package com.mochisofts.mata.ui.categorytodolist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.core.navigation.CategoryTodoListRoute
import com.mochisofts.mata.core.navigation.UNCATEGORIZED_CATEGORY_KEY
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class CategoryTodoListItem(
    val todo: Todo,
    val todayState: TodoState?,
)

data class CategoryTodoListUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val items: List<CategoryTodoListItem> = emptyList(),
)

private sealed interface CategoryTodoLoadState {
    data object Loading : CategoryTodoLoadState
    data class Data(val value: CategoryTodoListUiState) : CategoryTodoLoadState
    data object Error : CategoryTodoLoadState
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryTodoListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    clock: Clock,
    adsConsentRepository: AdsConsentRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<CategoryTodoListRoute>()
    private val selectedCategoryId =
        savedStateHandle.getStateFlow(
            SELECTED_CATEGORY_ID_KEY,
            route.selectedCategoryKey?.takeUnless {
                it == UNCATEGORIZED_CATEGORY_KEY
            },
        )

    val adsRuntimeState = adsConsentRepository.state
    private val loadGeneration = MutableStateFlow(0)

    val uiState: StateFlow<CategoryTodoListUiState> = loadGeneration.flatMapLatest {
        combine(
            categoryRepository.observeCategories(),
            todoRepository.observeTodos(),
            todoRepository.observeOccurrences(LocalDate.now(clock)),
            selectedCategoryId,
            ::buildCategoryTodoListUiState,
        ).map<CategoryTodoListUiState, CategoryTodoLoadState>(CategoryTodoLoadState::Data)
            .onStart { emit(CategoryTodoLoadState.Loading) }
            .catch { emit(CategoryTodoLoadState.Error) }
    }.map { state ->
        when (state) {
            CategoryTodoLoadState.Loading -> CategoryTodoListUiState(isLoading = true)
            CategoryTodoLoadState.Error -> CategoryTodoListUiState(
                isLoading = false,
                hasLoadError = true,
            )
            is CategoryTodoLoadState.Data -> state.value
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryTodoListUiState(),
    )

    fun selectCategory(categoryId: String?) {
        savedStateHandle[SELECTED_CATEGORY_ID_KEY] = categoryId
    }

    fun retryLoad() {
        loadGeneration.update(Int::inc)
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
