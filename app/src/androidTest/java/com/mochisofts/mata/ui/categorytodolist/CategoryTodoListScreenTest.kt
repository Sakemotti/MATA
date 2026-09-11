package com.mochisofts.mata.ui.categorytodolist

import android.app.Activity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryTodoListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ctl002_tabsFollowCategoryOrderAndSelectionSwitchesContent() {
        val earlier = category("earlier", "早い", 0)
        val later = category("later", "遅い", 10)
        val repositories = repositories(
            categories = listOf(later, earlier),
            todos = listOf(
                todo("uncategorized", "カテゴリ未設定TODO", null),
                todo("earlier-todo", "早いTODO", earlier.id),
                todo("later-todo", "遅いTODO", later.id),
            ),
        )
        setScreen(repositories)
        waitForText("カテゴリ未設定TODO")

        val uncategorizedLeft = composeRule.onNodeWithText(uncategorizedLabel())
            .fetchSemanticsNode().boundsInRoot.left
        val earlierLeft = composeRule.onNodeWithText(earlier.name)
            .fetchSemanticsNode().boundsInRoot.left
        val laterLeft = composeRule.onNodeWithText(later.name)
            .fetchSemanticsNode().boundsInRoot.left
        assertTrue(uncategorizedLeft < earlierLeft)
        assertTrue(earlierLeft < laterLeft)

        composeRule.onNodeWithText(later.name).performClick()
        waitForText("遅いTODO")
        composeRule.onNodeWithText(later.name).assertIsSelected()
        composeRule.onNodeWithText("カテゴリ未設定TODO").assertDoesNotExist()

        composeRule.onNodeWithText(uncategorizedLabel()).performClick()
        waitForText("カテゴリ未設定TODO")
        composeRule.onNodeWithText(uncategorizedLabel()).assertIsSelected()
        composeRule.onNodeWithText("遅いTODO").assertDoesNotExist()
    }

    @Test
    fun ctl003_activeDefinitionsAppearOnceWithoutOccurrenceExpansion() {
        val category = category("routine", "ルーチン", 0)
        val repositories = repositories(
            categories = listOf(category),
            todos = listOf(
                todo("before", "開始日前", category.id, startDate = TODAY.plusDays(1)),
                todo("active", "実施期間中", category.id, endDate = TODAY.plusDays(1)),
                todo("ended", "終了済み", category.id, endDate = TODAY.minusDays(1)),
                todo("unlimited", "無期限", category.id),
            ),
        )
        setScreen(repositories)
        selectCategory(category)

        listOf("開始日前", "実施期間中", "終了済み", "無期限").forEach { title ->
            composeRule.onAllNodesWithText(title).assertCountEquals(1)
        }
    }

    @Test
    fun ctl004_pendingCompletedAndSkippedRowsShowEveryStateLabel() {
        val category = category("states", "状態", 0)
        val pending = todo("pending", "保留の行", category.id)
        val completed = todo("completed", "完了の行", category.id)
        val skipped = todo("skipped", "スキップの行", category.id)
        val repositories = repositories(
            categories = listOf(category),
            todos = listOf(pending, completed, skipped),
            occurrences = listOf(
                occurrence(pending, TodoState.PENDING),
                occurrence(completed, TodoState.COMPLETED),
                occurrence(skipped, TodoState.SKIPPED),
            ),
        )
        setScreen(repositories)
        selectCategory(category)

        composeRule.onNodeWithText(stateLabel(R.string.label_pending)).assertIsDisplayed()
        composeRule.onNodeWithText(stateLabel(R.string.label_completed)).assertIsDisplayed()
        composeRule.onNodeWithText(stateLabel(R.string.label_skipped)).assertIsDisplayed()
    }

    @Test
    fun ctl005_nonScheduledAndNotStartedRowsOmitTodayStateLabel() {
        val category = category("future", "予定外", 0)
        val repositories = repositories(
            categories = listOf(category),
            todos = listOf(
                todo("not-today", "今日は対象外", category.id),
                todo("not-started", "開始日前の定義", category.id, startDate = TODAY.plusDays(1)),
            ),
            occurrences = emptyList(),
        )
        setScreen(repositories)
        selectCategory(category)

        composeRule.onNodeWithText("今日は対象外").assertIsDisplayed()
        composeRule.onNodeWithText("開始日前の定義").assertIsDisplayed()
        composeRule.onAllNodesWithText(stateLabel(R.string.label_pending)).assertCountEquals(0)
        composeRule.onAllNodesWithText(stateLabel(R.string.label_completed)).assertCountEquals(0)
        composeRule.onAllNodesWithText(stateLabel(R.string.label_skipped)).assertCountEquals(0)
    }

    @Test
    fun ctl006_rowHasNoCompletionOrActionControlsAndOpensEditor() {
        val category = category("edit", "編集", 0)
        val item = todo("editable", "編集するTODO", category.id)
        val repositories = repositories(
            categories = listOf(category),
            todos = listOf(item),
            occurrences = listOf(occurrence(item, TodoState.PENDING)),
        )
        val editedIds = mutableListOf<String>()
        setScreen(repositories, onEditTodo = editedIds::add)
        selectCategory(category)

        composeRule.onAllNodesWithContentDescription(todoActionsLabel()).assertCountEquals(0)
        composeRule.onAllNodes(
            SemanticsMatcher("has toggleable state") {
                it.config.contains(SemanticsProperties.ToggleableState)
            },
        ).assertCountEquals(0)
        composeRule.onNodeWithText("編集するTODO").performClick()
        composeRule.runOnIdle { assertEquals(listOf(item.id), editedIds) }
    }

    @Test
    fun ctl007_rowClickOnlyRequestsNavigationAndDoesNotMutateTodoState() {
        val category = category("safe", "安全", 0)
        val item = todo("safe-item", "状態を変えないTODO", category.id)
        val occurrence = occurrence(item, TodoState.COMPLETED)
        val repositories = repositories(
            categories = listOf(category),
            todos = listOf(item),
            occurrences = listOf(occurrence),
        )
        var editRequest: String? = null
        setScreen(repositories, onEditTodo = { editRequest = it })
        selectCategory(category)

        composeRule.onNodeWithText("状態を変えないTODO").performClick()
        composeRule.runOnIdle {
            assertEquals(item.id, editRequest)
            assertEquals(listOf(item), repositories.todoRepository.todos.value)
            assertEquals(listOf(occurrence), repositories.todoRepository.occurrences.value)
            assertEquals(0, repositories.todoRepository.mutationCount)
        }
    }

    @Test
    fun ctl009_deletedSelectedCategoryFallsBackToUncategorizedContent() {
        val deleted = category("deleted", "削除対象", 0)
        val repositories = repositories(
            categories = listOf(deleted),
            todos = listOf(
                todo("uncategorized", "移動後TODO", null),
                todo("old", "削除カテゴリの古いTODO", deleted.id),
            ),
        )
        setScreen(repositories)
        selectCategory(deleted)
        composeRule.onNodeWithText("削除カテゴリの古いTODO").assertIsDisplayed()

        repositories.categoryRepository.categories.value = emptyList()
        waitForText("移動後TODO")

        composeRule.onNodeWithText(uncategorizedLabel()).assertIsSelected()
        composeRule.onNodeWithText("削除カテゴリの古いTODO").assertDoesNotExist()
    }

    private fun setScreen(
        repositories: TestRepositories,
        onEditTodo: (String) -> Unit = {},
    ) {
        val viewModel = CategoryTodoListViewModel(
            savedStateHandle = SavedStateHandle(),
            todoRepository = repositories.todoRepository,
            categoryRepository = repositories.categoryRepository,
            clock = CLOCK,
            adsConsentRepository = TestAdsConsentRepository(),
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                CategoryTodoListScreen(
                    onAddTodo = {},
                    onEditTodo = onEditTodo,
                    onDestination = { _: MataDestination -> },
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun selectCategory(category: Category) {
        waitForText(category.name)
        composeRule.onNodeWithText(category.name).performClick()
        composeRule.onNodeWithText(category.name).assertIsSelected()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun stateLabel(resourceId: Int): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId)

    private fun uncategorizedLabel(): String = stateLabel(R.string.label_uncategorized)

    private fun todoActionsLabel(): String = stateLabel(R.string.content_description_todo_actions)

    private fun repositories(
        categories: List<Category>,
        todos: List<Todo>,
        occurrences: List<TodoOccurrence> = emptyList(),
    ) = TestRepositories(
        categoryRepository = TestCategoryRepository(categories),
        todoRepository = TestTodoRepository(todos, occurrences),
    )

    private fun category(id: String, name: String, sortOrder: Int) = Category(
        id = id,
        name = name,
        colorIndex = 0,
        iconName = "Category",
        sortOrder = sortOrder,
    )

    private fun todo(
        id: String,
        title: String,
        categoryId: String?,
        startDate: LocalDate = TODAY.minusDays(1),
        endDate: LocalDate? = null,
    ) = Todo(
        id = id,
        title = title,
        description = "",
        categoryId = categoryId,
        startDate = startDate,
        endDate = endDate,
        recurrenceRule = RecurrenceRule.daily(),
        dueMinutes = null,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = id.hashCode().toLong(),
    )

    private fun occurrence(todo: Todo, state: TodoState) = TodoOccurrence(
        todo = todo,
        category = null,
        logicalDate = TODAY,
        state = state,
    )

    private data class TestRepositories(
        val categoryRepository: TestCategoryRepository,
        val todoRepository: TestTodoRepository,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 11)
        val CLOCK: Clock = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
            ZoneId.of("Asia/Tokyo"),
        )
    }
}

private class TestCategoryRepository(categories: List<Category>) : CategoryRepository {
    val categories = MutableStateFlow(categories)

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

private class TestTodoRepository(
    todos: List<Todo>,
    occurrences: List<TodoOccurrence>,
) : TodoRepository {
    val todos = MutableStateFlow(todos)
    val occurrences = MutableStateFlow(occurrences)
    var mutationCount: Int = 0
        private set

    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> = occurrences
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
    ): Result<String> {
        mutationCount++
        return Result.success(id ?: "todo")
    }

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> {
        mutationCount++
        return Result.success(Unit)
    }

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> {
        mutationCount++
        return Result.success(Unit)
    }

    override suspend fun archiveTodo(id: String): Result<Unit> {
        mutationCount++
        return Result.success(Unit)
    }

    override suspend fun restoreTodo(id: String): Result<Unit> {
        mutationCount++
        return Result.success(Unit)
    }

    override suspend fun deleteTodo(id: String): Result<Unit> {
        mutationCount++
        return Result.success(Unit)
    }
}

private class TestAdsConsentRepository : AdsConsentRepository {
    override val state: StateFlow<AdsRuntimeState> = MutableStateFlow(AdsRuntimeState())
    override val events: Flow<AdsConsentEvent> = MutableSharedFlow()
    override fun gatherConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
