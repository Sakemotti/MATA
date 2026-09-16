package com.mochisofts.mata.ui.category

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import com.mochisofts.mata.core.designsystem.CategoryColorOptions
import com.mochisofts.mata.core.designsystem.CategoryIconOptions
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.CategoryTodoCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun cm003_userCategoryRowIdentifiesIconColorNameReorderHandleAndDeleteMenu() {
        setScreen(categories())

        composeRule.onNodeWithTag(CATEGORY_ROW_TEST_TAG_PREFIX + "daily").assertIsDisplayed()
        composeRule.onNodeWithText("日常").assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.expectValue(CategoryColorIdKey, "green"))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(CategoryIconIdKey, "Home"))
        composeRule.onNodeWithTag(
            CATEGORY_REORDER_HANDLE_TEST_TAG_PREFIX + "daily",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            CATEGORY_ACTIONS_TEST_TAG_PREFIX + "daily",
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithText(text(R.string.action_delete)).assertCountEquals(1)
    }

    @Test
    fun cm022_deleteDialogShowsCategoryTodoCountsMoveAndIrreversibleWarning() {
        val repository = setScreen(
            initialCategories = categories(),
            todoCounts = mapOf("daily" to CategoryTodoCounts(active = 2, archived = 1)),
        )

        composeRule.onNodeWithTag(
            CATEGORY_ACTIONS_TEST_TAG_PREFIX + "daily",
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(
                text(R.string.category_delete_active_count, 2),
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(CATEGORY_DELETE_DIALOG_TEST_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("日常").assertCountEquals(2)
        composeRule.onNodeWithText(text(R.string.category_delete_active_count, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_delete_archived_count, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_delete_move_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.category_delete_irreversible_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.snapshot.map(Category::id) == listOf("game")
        }
        composeRule.onNodeWithText(text(R.string.category_deleted_message)).assertIsDisplayed()
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
        composeRule.onNode(hasSetTextAction() and hasText("ゲーム")).assertIsDisplayed()
        composeRule.onNode(
            hasContentDescription(text(R.string.category_color_purple)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(CategoryIconIdKey, "SportsEsports"),
            )
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
    fun cm014_fixedPaletteShowsAndSelectsExactlySixteenNamedColors() {
        setScreen(emptyList())
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()

        composeRule.onAllNodes(defined(CategoryColorIdKey)).assertCountEquals(16)
        CategoryColorOptions.forEach { option ->
            val node = composeRule.onNode(
                hasContentDescription(text(option.labelRes)) and
                    SemanticsMatcher.expectValue(CategoryColorIdKey, option.id),
            )
            node.performScrollTo().performClick()
            node.assertIsSelected()
        }
    }

    @Test
    fun cm015_savedColorIdSurvivesLightDarkAndDynamicPrimaryThemes() {
        val repository = CategoryScreenTestRepository(categories())
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
        val theme = mutableStateOf(AppTheme.LIGHT)
        val dynamicColor = mutableStateOf(false)
        composeRule.setContent {
            MataTheme(appTheme = theme.value, useDynamicColor = dynamicColor.value) {
                CategoryListScreen(onDestination = {}, viewModel = viewModel)
            }
        }
        composeRule.onNodeWithText("ゲーム").performClick()
        val purple = composeRule.onNode(
            SemanticsMatcher.expectValue(CategoryColorIdKey, "purple"),
        )
        purple.performScrollTo().assertIsSelected()
        val lightArgb = purple.fetchSemanticsNode().config[CategoryColorArgbKey]

        composeRule.runOnIdle { theme.value = AppTheme.DARK }
        composeRule.waitForIdle()
        val darkArgb = purple.fetchSemanticsNode().config[CategoryColorArgbKey]
        assertNotEquals(lightArgb, darkArgb)
        purple.assertIsSelected()

        composeRule.runOnIdle {
            theme.value = AppTheme.LIGHT
            dynamicColor.value = true
        }
        composeRule.waitForIdle()
        assertEquals(lightArgb, purple.fetchSemanticsNode().config[CategoryColorArgbKey])
        composeRule.runOnIdle {
            assertEquals(2, repository.snapshot.single { it.id == "game" }.colorIndex)
        }
    }

    @Test
    fun cm016_iconPickerGroupsAndNormalizesNameGroupAndKeywordSearch() {
        setScreen(emptyList())
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG).performScrollTo().performClick()

        composeRule.onNodeWithTag(CATEGORY_ICON_PICKER_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(
            CATEGORY_ICON_GROUP_TEST_TAG_PREFIX + R.string.category_icon_group_life,
        ).assertIsDisplayed()
        val search = composeRule.onNodeWithTag(CATEGORY_ICON_SEARCH_TEST_TAG)
        search.performTextInput("ジョギング")
        composeRule.onNodeWithTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + "DirectionsRun")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + "FitnessCenter")
            .assertDoesNotExist()

        composeRule.onNodeWithContentDescription(text(R.string.category_icon_clear_search))
            .performClick()
        composeRule.onNodeWithTag(
            CATEGORY_ICON_GROUP_TEST_TAG_PREFIX + R.string.category_icon_group_life,
        ).assertIsDisplayed()
        search.performTextInput("ＰＣ")
        composeRule.onNodeWithTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + "Laptop")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG)
            .assert(SemanticsMatcher.expectValue(CategoryIconIdKey, "Laptop"))
    }

    @Test
    fun cmd04_colorCatalogMatchesStableIdsJapaneseNamesAndBaseColors() {
        assertEquals(
            listOf(
                "red", "pink", "purple", "indigo", "blue", "light_blue", "cyan", "teal",
                "green", "light_green", "lime", "yellow", "orange", "deep_orange", "brown", "gray",
            ),
            CategoryColorOptions.map { it.id },
        )
        assertEquals(
            listOf(
                0xFFC62828, 0xFFAD1457, 0xFF6A1B9A, 0xFF283593,
                0xFF1565C0, 0xFF0277BD, 0xFF00838F, 0xFF00796B,
                0xFF2E7D32, 0xFF558B2F, 0xFF827717, 0xFFF9A825,
                0xFFEF6C00, 0xFFD84315, 0xFF5D4037, 0xFF546E7A,
            ),
            CategoryColorOptions.map { it.baseArgb },
        )
        assertEquals(
            listOf(
                "赤", "ピンク", "紫", "藍", "青", "水色", "シアン", "青緑",
                "緑", "黄緑", "ライム", "黄", "オレンジ", "濃いオレンジ", "茶", "グレー",
            ),
            CategoryColorOptions.map { text(it.labelRes) },
        )

        setScreen(emptyList(), appTheme = AppTheme.LIGHT)
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()
        composeRule.onAllNodes(defined(CategoryColorIdKey)).assertCountEquals(16)
        CategoryColorOptions.forEach { option ->
            val renderedColor = composeRule.onNode(
                SemanticsMatcher.expectValue(CategoryColorIdKey, option.id),
            ).fetchSemanticsNode().config[CategoryColorArgbKey]
            assertEquals(option.baseArgb, renderedColor)
        }
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test
    fun cmd05_iconCatalogMatchesAllMaterialIdsAndJapaneseNamesWithoutCustomInput() {
        val expected = listOf(
            "Home" to "家", "Bed" to "ベッド", "WbSunny" to "朝",
            "CleaningServices" to "掃除", "LocalLaundryService" to "洗濯", "DeleteSweep" to "片付け",
            "ShoppingCart" to "カート", "ShoppingBag" to "買い物袋", "Storefront" to "店舗",
            "Restaurant" to "食事", "Kitchen" to "キッチン", "LocalDining" to "料理",
            "Favorite" to "健康", "HealthAndSafety" to "健康管理", "SelfImprovement" to "リラックス",
            "FitnessCenter" to "筋力トレーニング", "DirectionsRun" to "ランニング", "SportsSoccer" to "球技",
            "Medication" to "薬", "MedicalServices" to "病院", "Vaccines" to "予防接種",
            "School" to "学校", "MenuBook" to "読書", "EditNote" to "ノート",
            "Work" to "仕事", "BusinessCenter" to "業務", "Laptop" to "パソコン",
            "SportsEsports" to "ゲーム", "Casino" to "ボードゲーム", "EmojiEvents" to "実績",
            "Event" to "イベント", "Celebration" to "お祝い", "Flag" to "目標",
            "Payments" to "支払い", "Savings" to "貯金", "AccountBalanceWallet" to "財布",
            "DirectionsCar" to "車", "Train" to "電車", "Flight" to "飛行機",
            "Person" to "個人", "Groups" to "グループ", "FamilyRestroom" to "家族",
            "Pets" to "ペット",
            "Category" to "カテゴリ", "Star" to "星", "CheckCircle" to "チェック", "MoreHoriz" to "その他",
        )
        assertEquals(expected, CategoryIconOptions.map { it.id to text(it.labelRes) })
        assertEquals(CategoryIconOptions.size, CategoryIconOptions.map { it.id }.distinct().size)

        setScreen(emptyList())
        composeRule.onNodeWithTag(CATEGORY_EMPTY_ADD_TEST_TAG).performClick()
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG).performScrollTo().performClick()
        val results = composeRule.onNodeWithTag(CATEGORY_ICON_RESULTS_TEST_TAG)
        expected.forEach { (id, label) ->
            results.performScrollToNode(hasTestTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + id))
            composeRule.onNodeWithTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + id)
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(CategoryIconIdKey, id))
                .assert(SemanticsMatcher.expectValue(CategoryIconLabelKey, label))
        }
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        composeRule.onNodeWithText("画像アップロード").assertDoesNotExist()
        composeRule.onNodeWithText("絵文字").assertDoesNotExist()
        composeRule.onNodeWithText("自由入力").assertDoesNotExist()
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
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG)
            .performScrollTo()
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(CategoryIconIdKey, "Category"))
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
    fun cm019_addScrollsToHighlightedTailAndEditKeepsTheListPosition() {
        val initial = (1..30).map { index ->
            Category(
                id = "category-$index",
                name = "Category $index",
                colorIndex = index % CategoryColorOptions.size,
                iconName = "Category",
                sortOrder = index - 1,
            )
        }
        val repository = setScreen(initial)
        composeRule.onNodeWithTag(CATEGORY_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(CATEGORY_ROW_TEST_TAG_PREFIX + "category-20"))
        composeRule.onNodeWithText("Category 20").assertIsDisplayed().performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Updated category 20")
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.snapshot.any { it.id == "category-20" && it.name == "Updated category 20" }
        }
        composeRule.onNodeWithText("Updated category 20").assertIsDisplayed()
        composeRule.onNodeWithText("Category 1").assertIsNotDisplayed()
        composeRule.onNodeWithText(text(R.string.category_updated_message)).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(5_000)
        composeRule.onNodeWithTag(CATEGORY_ADD_FAB_TEST_TAG).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("New tail category")
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()

        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText("New tail category").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("New tail category").assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_ROW_TEST_TAG_PREFIX + "new")
            .assert(SemanticsMatcher.expectValue(CategoryNewlyAddedKey, true))
        composeRule.onNodeWithText(text(R.string.category_added_message)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("new", repository.snapshot.last().id)
        }
    }

    @Test
    fun cm028_recreationRestoresListDraftPickerSearchAndClosesNormalDialog() {
        val initial = (1..30).map { index ->
            Category(
                id = "restore-$index",
                name = "Restore $index",
                colorIndex = index % CategoryColorOptions.size,
                iconName = "Category",
                sortOrder = index - 1,
            )
        }
        val repository = CategoryScreenTestRepository(initial)
        val savedStateHandle = SavedStateHandle()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            val viewModel = remember {
                CategoryListViewModel(savedStateHandle, repository)
            }
            MataTheme(useDynamicColor = false) {
                CategoryListScreen(onDestination = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Restore 1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(CATEGORY_LIST_TEST_TAG)
            .performScrollToNode(hasTestTag(CATEGORY_ROW_TEST_TAG_PREFIX + "restore-25"))
        composeRule.onNodeWithText("Restore 25").assertIsDisplayed().performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("Restored draft")
        composeRule.onNode(SemanticsMatcher.expectValue(CategoryColorIdKey, "gray"))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(CATEGORY_ICON_LIST_TEST_TAG).performScrollTo().performClick()
        composeRule.onNodeWithTag(CATEGORY_ICON_SEARCH_TEST_TAG).performTextInput("Laptop")
        composeRule.onNodeWithTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + "Laptop").assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(CATEGORY_ICON_PICKER_TEST_TAG).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction() and hasText("Laptop")).assertIsDisplayed()
        composeRule.onNodeWithTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + "Laptop").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("Restored draft")).assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.expectValue(CategoryColorIdKey, "gray"))
            .performScrollTo()
            .assertIsSelected()

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.dialog_discard_changes_title)).assertIsDisplayed()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText(text(R.string.dialog_discard_changes_title)).assertDoesNotExist()
        composeRule.onNode(hasSetTextAction() and hasText("Restored draft")).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.action_discard)).performClick()
        composeRule.onNodeWithText("Restore 25").assertIsDisplayed()
        composeRule.onNodeWithText("Restore 1").assertIsNotDisplayed()
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
        appTheme: AppTheme = AppTheme.SYSTEM,
        todoCounts: Map<String, CategoryTodoCounts> = emptyMap(),
    ): CategoryScreenTestRepository {
        val repository = CategoryScreenTestRepository(initialCategories, todoCounts)
        val viewModel = CategoryListViewModel(SavedStateHandle(), repository)
        composeRule.setContent {
            MataTheme(appTheme = appTheme, useDynamicColor = false) {
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

    private fun <T> defined(key: SemanticsPropertyKey<T>) =
        SemanticsMatcher("${key.name} is defined") { it.config.contains(key) }
}

private class CategoryScreenTestRepository(
    initialCategories: List<Category>,
    private val todoCounts: Map<String, CategoryTodoCounts> = emptyMap(),
) : CategoryRepository {
    private val categories = MutableStateFlow(initialCategories)
    val snapshot: List<Category>
        get() = categories.value

    override fun observeCategories(): Flow<List<Category>> = categories

    override suspend fun getCategory(id: String): Category? =
        categories.value.firstOrNull { it.id == id }

    override suspend fun getTodoCounts(id: String): CategoryTodoCounts =
        todoCounts[id] ?: CategoryTodoCounts()

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
        categories.value = if (existing == null) {
            categories.value + saved
        } else {
            categories.value.map { category -> if (category.id == savedId) saved else category }
        }
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
