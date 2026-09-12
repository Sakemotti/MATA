package com.mochisofts.mata.ui.categorytodolist

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.ui.ads.BannerLoadState
import com.mochisofts.mata.ui.ads.reservesSpace
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.ui.todolist.buildTodoOccurrenceGroups
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
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
class CategoryTodoListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ctl008_loadingEmptyConsentPendingAndAdFailureLeaveNoBlankAdArea() = runTest {
        val categoryRepository = CategoryTodoListTestCategoryRepository()
        val todoRepository = CategoryTodoListTestTodoRepository().apply { failLoad = true }
        val adsRepository = CategoryTodoListTestAdsRepository()
        val viewModel = CategoryTodoListViewModel(
            savedStateHandle = SavedStateHandle(),
            todoRepository = todoRepository,
            categoryRepository = categoryRepository,
            clock = Clock.fixed(
                LocalDate.of(2026, 9, 6).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
                ZoneId.of("Asia/Tokyo"),
            ),
            adsConsentRepository = adsRepository,
        )
        assertTrue(viewModel.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        assertTrue(viewModel.uiState.value.hasLoadError)

        todoRepository.failLoad = false
        viewModel.retryLoad()
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasLoadError)
        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertFalse(adsRepository.state.value.canLoadBanner)
        assertFalse(BannerLoadState.FAILED.reservesSpace)
    }

    @Test
    fun viewModelTransitionsFromLoadingToEmptyThenSelectedContent() = runTest {
        val categoryRepository = CategoryTodoListTestCategoryRepository()
        val todoRepository = CategoryTodoListTestTodoRepository()
        val viewModel = CategoryTodoListViewModel(
            savedStateHandle = SavedStateHandle(),
            todoRepository = todoRepository,
            categoryRepository = categoryRepository,
            clock = Clock.fixed(
                LocalDate.of(2026, 9, 6).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
                ZoneId.of("Asia/Tokyo"),
            ),
            adsConsentRepository = CategoryTodoListTestAdsRepository(),
        )

        assertTrue(viewModel.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.items.isEmpty())

        val category = category("category", 0)
        val todo = todo("todo", category.id, 1)
        categoryRepository.categories.value = listOf(category)
        todoRepository.todos.value = listOf(todo)
        todoRepository.occurrences.value = listOf(occurrence(todo, TodoState.COMPLETED, LocalDate.of(2026, 9, 6)))
        viewModel.selectCategory(category.id)
        runCurrent()

        assertEquals(category.id, viewModel.uiState.value.selectedCategoryId)
        assertEquals(listOf("todo"), viewModel.uiState.value.items.map { it.todo.id })
        assertEquals(TodoState.COMPLETED, viewModel.uiState.value.items.single().todayState)
    }

    @Test
    fun selectedCategoryShowsEveryTodayStateWithoutFiltering() {
        val category = category(id = "category", sortOrder = 1)
        val pending = todo(id = "pending", categoryId = category.id, createdAt = 3)
        val completed = todo(id = "completed", categoryId = category.id, createdAt = 1)
        val skipped = todo(id = "skipped", categoryId = category.id, createdAt = 2)
        val other = todo(id = "other", categoryId = "other-category", createdAt = 0)
        val date = LocalDate.of(2026, 8, 25)

        val state = buildCategoryTodoListUiState(
            categories = listOf(category(id = "other-category", sortOrder = 0), category),
            todos = listOf(pending, completed, skipped, other),
            todayOccurrences = listOf(
                occurrence(pending, TodoState.PENDING, date),
                occurrence(completed, TodoState.COMPLETED, date),
                occurrence(skipped, TodoState.SKIPPED, date),
            ),
            requestedCategoryId = category.id,
        )

        assertFalse(state.isLoading)
        assertEquals(category.id, state.selectedCategoryId)
        assertEquals(listOf("completed", "skipped", "pending"), state.items.map { it.todo.id })
        assertEquals(
            listOf(TodoState.COMPLETED, TodoState.SKIPPED, TodoState.PENDING),
            state.items.map { it.todayState },
        )
    }

    @Test
    fun cm010_reorderedCategoriesImmediatelyDriveTabsGroupsAndSameCategoryTodos() = runTest {
        val first = category(id = "first", sortOrder = 0)
        val second = category(id = "second", sortOrder = 1)
        val older = todo(id = "older", categoryId = first.id, createdAt = 1)
        val newer = todo(id = "newer", categoryId = first.id, createdAt = 2)
        val secondTodo = todo(id = "second-todo", categoryId = second.id, createdAt = 3)
        val categoryRepository = CategoryTodoListTestCategoryRepository().apply {
            categories.value = listOf(first, second)
        }
        val todoRepository = CategoryTodoListTestTodoRepository().apply {
            todos.value = listOf(newer, secondTodo, older)
        }
        val viewModel = CategoryTodoListViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("category_todo_list_selected_category_id" to first.id),
            ),
            todoRepository = todoRepository,
            categoryRepository = categoryRepository,
            clock = Clock.fixed(
                LocalDate.of(2026, 9, 6).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
                ZoneId.of("Asia/Tokyo"),
            ),
            adsConsentRepository = CategoryTodoListTestAdsRepository(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        val reorderedFirst = first.copy(sortOrder = 1)
        val reorderedSecond = second.copy(sortOrder = 0)
        categoryRepository.categories.value = listOf(reorderedFirst, reorderedSecond)
        runCurrent()

        assertEquals(
            listOf(second.id, first.id),
            viewModel.uiState.value.categories.map(Category::id),
        )
        assertEquals(listOf("older", "newer"), viewModel.uiState.value.items.map { it.todo.id })

        val groups = buildTodoOccurrenceGroups(
            occurrences = listOf(
                occurrence(older, TodoState.PENDING, LocalDate.of(2026, 9, 6)).copy(
                    category = reorderedFirst,
                ),
                occurrence(secondTodo, TodoState.PENDING, LocalDate.of(2026, 9, 6)).copy(
                    category = reorderedSecond,
                ),
                occurrence(newer, TodoState.PENDING, LocalDate.of(2026, 9, 6)).copy(
                    category = reorderedFirst,
                ),
            ),
            dayEndHour = 0,
        )
        assertEquals(listOf(second.id, first.id), groups.map { it.category?.id })
        assertEquals(listOf("older", "newer"), groups.last().occurrences.map { it.todo.id })
    }

    @Test
    fun deletedSelectionFallsBackToUncategorized() {
        val uncategorized = todo(id = "uncategorized", categoryId = null, createdAt = 1)
        val categorized = todo(id = "categorized", categoryId = "available", createdAt = 2)

        val state = buildCategoryTodoListUiState(
            categories = listOf(category(id = "available", sortOrder = 0)),
            todos = listOf(uncategorized, categorized),
            todayOccurrences = emptyList(),
            requestedCategoryId = "deleted",
        )

        assertNull(state.selectedCategoryId)
        assertEquals(listOf("uncategorized"), state.items.map { it.todo.id })
        assertNull(state.items.single().todayState)
    }

    private fun category(id: String, sortOrder: Int) = Category(
        id = id,
        name = id,
        colorIndex = 0,
        iconName = "Category",
        sortOrder = sortOrder,
    )

    private fun todo(id: String, categoryId: String?, createdAt: Long) = Todo(
        id = id,
        title = id,
        description = "",
        categoryId = categoryId,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        recurrenceRule = RecurrenceRule.daily(),
        dueMinutes = null,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = createdAt,
    )

    private fun occurrence(todo: Todo, state: TodoState, date: LocalDate) = TodoOccurrence(
        todo = todo,
        category = null,
        logicalDate = date,
        state = state,
    )
}

private class CategoryTodoListTestTodoRepository : TodoRepository {
    val todos = MutableStateFlow<List<Todo>>(emptyList())
    val occurrences = MutableStateFlow<List<TodoOccurrence>>(emptyList())
    var failLoad = false
    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> = if (failLoad) {
        flow { error("load failed") }
    } else {
        occurrences
    }
    override fun observeTodos(): Flow<List<Todo>> = todos
    override suspend fun getTodo(id: String): Todo? = todos.value.firstOrNull { it.id == id }
    override suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        notifications: List<TodoNotification>,
        dueDate: LocalDate?,
        carryOverEnabled: Boolean,
    ): Result<String> = Result.success(id ?: "todo")
    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> = Result.success(Unit)
    override suspend fun archiveTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun restoreTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTodo(id: String): Result<Unit> = Result.success(Unit)
}

private class CategoryTodoListTestCategoryRepository : CategoryRepository {
    val categories = MutableStateFlow<List<Category>>(emptyList())
    override fun observeCategories(): Flow<List<Category>> = categories
    override suspend fun getCategory(id: String): Category? = categories.value.firstOrNull { it.id == id }
    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
    ): Result<String> = Result.success(id ?: "category")
    override suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteCategory(id: String): Result<Unit> = Result.success(Unit)
}

private class CategoryTodoListTestAdsRepository : AdsConsentRepository {
    override val state: StateFlow<AdsRuntimeState> = MutableStateFlow(AdsRuntimeState())
    override val events: Flow<AdsConsentEvent> = MutableSharedFlow()
    override fun gatherConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
