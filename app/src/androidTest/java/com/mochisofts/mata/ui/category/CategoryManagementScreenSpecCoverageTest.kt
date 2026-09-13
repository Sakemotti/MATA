package com.mochisofts.mata.ui.category

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

    private fun setScreen(
        initialCategories: List<Category>,
        onDestination: (MataDestination) -> Unit = {},
    ) {
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
    }

    private fun assertNewEditorDisplayed() {
        composeRule.onNodeWithText(text(R.string.category_editor_add_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_name_preview)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_save)).assertIsDisplayed()
    }

    private fun categories() = listOf(
        Category("daily", "日常", colorIndex = 8, iconName = "Home", sortOrder = 0),
        Category("game", "ゲーム", colorIndex = 2, iconName = "Game", sortOrder = 1),
    )

    private fun text(resId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
}

private class CategoryScreenTestRepository(initialCategories: List<Category>) : CategoryRepository {
    private val categories = MutableStateFlow(initialCategories)

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
