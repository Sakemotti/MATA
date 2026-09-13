package com.mochisofts.mata.ui.category

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryManagementScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cm001_drawerOpensCategoryManagementAsSelectedFullScreenDestination() {
        val destinations = mutableListOf<MataDestination>()
        setScreen(categories(), destinations::add)

        composeRule.onNodeWithContentDescription(text(R.string.content_description_open_menu))
            .performClick()
        composeRule.onNode(
            hasText(text(R.string.nav_category_management)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.nav_todo_list)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { destinations.isNotEmpty() }
        composeRule.runOnIdle { assertEquals(listOf(MataDestination.TODOS), destinations) }
    }

    @Test
    fun cm002_listContainsOnlyUserCategoriesAndNeverShowsUncategorized() {
        setScreen(categories())

        composeRule.onNodeWithText("日常").assertIsDisplayed()
        composeRule.onNodeWithText("ゲーム").assertIsDisplayed()
        composeRule.onNodeWithText("カテゴリ未設定").assertDoesNotExist()
    }

    @Test
    fun cm005_emptyActionAndFabOpenTheSameNewCategoryForm() {
        setScreen(emptyList())

        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).assertIsDisplayed().performClick()
        assertNewEditorDisplayed()

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithTag(CATEGORY_ADD_FAB_TEST_TAG).assertIsDisplayed().performClick()
        assertNewEditorDisplayed()
    }

    @Test
    fun cm006_categoryRowLoadsSelectedValuesIntoEditForm() {
        setScreen(categories())

        composeRule.onNodeWithText("ゲーム").performClick()
        composeRule.onNodeWithText(text(R.string.category_editor_edit_title)).assertIsDisplayed()
        composeRule.onAllNodesWithText("ゲーム").assertCountEquals(2)
        composeRule.onNode(
            hasContentDescription(text(R.string.category_color_purple)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG)
            .performScrollToNode(hasText(text(R.string.category_icon_game)))
        composeRule.onNode(
            hasText(text(R.string.category_icon_game)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cm011_editorShowsPreviewNameColorAndIconWithoutEndHour() {
        setScreen(emptyList())
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()

        composeRule.onNodeWithText(text(R.string.category_name_preview)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_name_required_label)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_color_label)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_icon_label))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("一日の終了時刻").assertDoesNotExist()
        composeRule.onNodeWithText("終了時刻").assertDoesNotExist()
    }

    @Test
    fun cmd01_newEditorUsesSpecifiedDefaultsAndHasNoEndHourInput() {
        setScreen(emptyList())
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()

        composeRule.onNodeWithText(text(R.string.category_name_preview)).assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription(text(R.string.category_color_green)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNode(
            hasText(text(R.string.category_icon_category)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("終了時刻").assertDoesNotExist()
    }

    @Test
    fun cm009_accessibilityActionsMoveOneStepAndOmitUnavailableDirections() {
        val repository = setScreen(categories())
        val moveUp = text(R.string.category_move_up)
        val moveDown = text(R.string.category_move_down)
        val dailyHandle = text(R.string.category_reorder_handle, "日常")
        val gameHandle = text(R.string.category_reorder_handle, "ゲーム")

        val dailyNode = composeRule.onNodeWithContentDescription(dailyHandle)
        val initialActions = dailyNode.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(moveDown), initialActions.map { it.label })
        composeRule.runOnIdle {
            assertTrue(initialActions.single().action())
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.snapshot.map(Category::id) == listOf("game", "daily")
        }

        val movedDailyActions = composeRule.onNodeWithContentDescription(dailyHandle)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(moveUp), movedDailyActions.map { it.label })
        val movedGameActions = composeRule.onNodeWithContentDescription(gameHandle)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf(moveDown), movedGameActions.map { it.label })
    }

    @Test
    fun cm026_deleteSuccessShowsMessageWithoutUndo() {
        val repository = setScreen(categories())

        composeRule.onNodeWithText("日常").performClick()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_delete_category))
            .performClick()
        composeRule.onNodeWithText(text(R.string.dialog_delete_category_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.snapshot.map(Category::id) == listOf("game")
        }
        composeRule.onNodeWithText(text(R.string.category_deleted_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertDoesNotExist()
    }

    @Test
    fun cm027_backOnlyConfirmsDirtyDraftAndDiscardClearsIt() {
        setScreen(categories())

        composeRule.onNodeWithText("日常").performClick()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText("ゲーム").assertIsDisplayed()

        composeRule.onNodeWithText("日常").performClick()
        val nameInput = composeRule.onNode(hasSetTextAction())
        nameInput.performTextClearance()
        nameInput.performTextInput("変更中")
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.dialog_discard_changes_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_continue_editing)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("変更中")).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.action_discard)).performClick()
        composeRule.onNodeWithText("ゲーム").assertIsDisplayed()
        composeRule.onNodeWithText("変更中").assertDoesNotExist()
        composeRule.onNodeWithText("日常").assertIsDisplayed()
    }

    @Test
    fun cmd03_nameChangesPreviewImmediatelyAndInvalidDraftRemainsEditable() {
        setScreen(emptyList())
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()
        val nameInput = composeRule.onNode(hasSetTextAction())

        nameInput.performTextInput("即時プレビュー")
        composeRule.onAllNodesWithText("即時プレビュー").assertCountEquals(2)
        val preview = composeRule.onNode(hasText("即時プレビュー") and !hasSetTextAction())
        assertFalse(preview.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))

        nameInput.performTextClearance()
        composeRule.onNodeWithText(text(R.string.category_name_preview)).assertIsDisplayed()
        val invalidName = "長".repeat(31)
        nameInput.performTextInput(invalidName)
        composeRule.onNode(hasText(invalidName) and !hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_save)).assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("修正可能")
    }

    private fun setScreen(
        initialCategories: List<Category>,
        onDestination: (MataDestination) -> Unit = {},
    ): CategoryScreenTestRepository {
        val repository = CategoryScreenTestRepository(initialCategories)
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                CategoryListScreen(onDestination = onDestination, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            if (initialCategories.isEmpty()) {
                composeRule.onAllNodesWithText(text(R.string.category_empty_message))
                    .fetchSemanticsNodes().isNotEmpty()
            } else {
                composeRule.onAllNodesWithText(initialCategories.first().name)
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        return repository
    }

    private fun assertNewEditorDisplayed() {
        composeRule.onNodeWithText(text(R.string.category_editor_add_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_name_preview)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_save)).assertIsDisplayed()
    }

    private fun categories() = listOf(
        Category("daily", "日常", colorIndex = 8, iconName = "Home", sortOrder = 0),
        Category("game", "ゲーム", colorIndex = 2, iconName = "SportsEsports", sortOrder = 1),
    )

    private fun text(resId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *formatArgs)
}

private class CategoryScreenTestRepository(initialCategories: List<Category>) : CategoryRepository {
    private val categories = MutableStateFlow(initialCategories)
    val snapshot: List<Category>
        get() = categories.value

    override fun observeCategories(): Flow<List<Category>> = categories

    override suspend fun getCategory(id: String): Category? =
        categories.value.firstOrNull { it.id == id }

    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
    ): Result<String> {
        val savedId = id ?: "new"
        val existing = categories.value.firstOrNull { it.id == savedId }
        val saved = Category(
            id = savedId,
            name = name,
            colorIndex = colorIndex,
            iconName = iconName,
            sortOrder = existing?.sortOrder ?: categories.value.size,
        )
        categories.value = categories.value.filterNot { it.id == savedId } + saved
        return Result.success(savedId)
    }

    override suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> {
        val byId = categories.value.associateBy(Category::id)
        categories.value = orderedIds.mapIndexed { index, id ->
            requireNotNull(byId[id]).copy(sortOrder = index)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteCategory(id: String): Result<Unit> {
        val before = categories.value
        categories.value = before.filterNot { it.id == id }
        return if (categories.value.size < before.size) Result.success(Unit)
        else Result.failure(IllegalArgumentException("missing category"))
    }
}
