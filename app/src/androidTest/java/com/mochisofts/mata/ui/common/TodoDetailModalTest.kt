package com.mochisofts.mata.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.core.designsystem.DETAIL_DELETE_ACTION_TAG
import com.mochisofts.mata.core.designsystem.DETAIL_PRIMARY_ACTION_TAG
import com.mochisofts.mata.core.designsystem.MataDetailBottomActions
import com.mochisofts.mata.core.designsystem.MataTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodoDetailModalTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modalShowsSharedReadOnlyContentAndOnlyCloseAction() {
        var dismissed = false
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoDetailFullScreen(
                    data = TodoDetailModalData(
                        title = "朝のストレッチ",
                        description = "肩と腰を伸ばす",
                        category = TodoDetailCategory(
                            name = "健康",
                            iconName = "Home",
                            colorIndex = 2,
                        ),
                        fields = listOf(
                            TodoDetailField("対象論理日", "2026年8月26日"),
                            TodoDetailField("状態", "完了"),
                        ),
                    ),
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("朝のストレッチ").assertIsDisplayed()
        composeRule.onNodeWithText("肩と腰を伸ばす").assertIsDisplayed()
        composeRule.onNodeWithText("健康").assertIsDisplayed()
        composeRule.onNodeWithText("対象論理日").assertIsDisplayed()
        composeRule.onNodeWithText("2026年8月26日").assertIsDisplayed()
        composeRule.onNodeWithText("状態").assertIsDisplayed()
        composeRule.onNodeWithText("完了").assertIsDisplayed()
        composeRule.onAllNodesWithText("編集").assertCountEquals(0)
        composeRule.onAllNodesWithText("スキップ").assertCountEquals(0)
        composeRule.onAllNodesWithText("削除").assertCountEquals(0)
        composeRule.onNodeWithTag(TODO_DETAIL_CARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun emptyDescriptionAndLongFieldsKeepCloseActionAvailable() {
        val fields = buildList {
            repeat(20) { index ->
                add(TodoDetailField("項目$index", "値$index"))
            }
            add(TodoDetailField("最終項目", "最終値"))
        }
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoDetailFullScreen(
                    data = TodoDetailModalData(
                        title = "長い詳細",
                        description = "",
                        category = TodoDetailCategory(
                            name = "カテゴリ未設定",
                            iconName = null,
                            colorIndex = null,
                        ),
                        fields = fields,
                    ),
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("説明なし").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()
        composeRule.onNodeWithText("最終値").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()
    }

    @Test
    fun fullScreenDetailKeepsCommonHeaderVisibleWhileWholeCardScrolls() {
        var dismissed = false
        val fields = buildList {
            repeat(20) { index -> add(TodoDetailField("項目$index", "値$index")) }
            add(TodoDetailField("最終項目", "最終値"))
        }
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoDetailFullScreen(
                    data = TodoDetailModalData(
                        title = "読み取り専用TODO",
                        description = "説明",
                        category = TodoDetailCategory(
                            name = "カテゴリ",
                            iconName = "Home",
                            colorIndex = 2,
                        ),
                        fields = fields,
                    ),
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("TODO詳細").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("最終値"))
        composeRule.onNodeWithText("最終値").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun pastDetailKeepsDefinitionActionsVisibleWithoutAddingHistoryMutations() {
        var edited = false
        var deleted = false
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoDetailFullScreen(
                    data = TodoDetailModalData(
                        title = "過去TODO",
                        description = "長い説明".repeat(250),
                        category = TodoDetailCategory("カテゴリ未設定", null, null),
                        fields = listOf(TodoDetailField("状態", "未完了")),
                    ),
                    onDismiss = {},
                    onEdit = { edited = true },
                    onDelete = { deleted = true },
                )
            }
        }

        composeRule.onNodeWithText("編集").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("完全削除").assertIsDisplayed().performClick()
        val primaryBounds = composeRule.onNodeWithTag(DETAIL_PRIMARY_ACTION_TAG)
            .fetchSemanticsNode().boundsInRoot
        val deleteBounds = composeRule.onNodeWithTag(DETAIL_DELETE_ACTION_TAG)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(primaryBounds.width, deleteBounds.width, 1f)
        composeRule.onNodeWithTag(TODO_DETAIL_SCROLL_TAG).performScrollToNode(hasText("未完了"))
        composeRule.onNodeWithText("未完了").assertIsDisplayed()
        composeRule.onNodeWithText("編集").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()
        composeRule.onAllNodesWithText("スキップ").assertCountEquals(0)
        composeRule.onAllNodesWithText("アーカイブ").assertCountEquals(0)
        composeRule.runOnIdle {
            assertTrue(edited)
            assertTrue(deleted)
        }
    }

    @Test
    fun definitionAndEachSectionHaveSeparateCardsInTheSameScrollArea() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoDetailFullScreen(
                    data = TodoDetailModalData(
                        title = "区切り確認",
                        description = "長文の説明。".repeat(100),
                        category = TodoDetailCategory("日常", "Home", 8),
                        sections = listOf(
                            TodoDetailSection("スケジュール", listOf(TodoDetailField("期限時刻", "21:00"))),
                            TodoDetailSection("通知", listOf(TodoDetailField("通知設定", "1時間前"))),
                            TodoDetailSection("履歴", listOf(TodoDetailField("状態", "未完了"))),
                        ),
                    ),
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText("基本情報").assertIsDisplayed()
        composeRule.onNodeWithTag(TODO_DETAIL_CARD_TAG).assertIsDisplayed()
        repeat(3) { index ->
            composeRule.onNodeWithTag(TODO_DETAIL_SECTION_TAG_PREFIX + index)
                .performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("未完了").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()
    }

    @Test
    fun sharedBottomActionsStayEqualWidthAndClickableAtMaximumFontOnCompactWidth() {
        var edited = false
        var deleted = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MataTheme(useDynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        MataDetailBottomActions(
                            primaryLabel = "編集",
                            primaryIcon = Icons.Outlined.Edit,
                            onPrimary = { edited = true },
                            onDelete = { deleted = true },
                        )
                    }
                }
            }
        }
        val primary = composeRule.onNodeWithTag(DETAIL_PRIMARY_ACTION_TAG)
        val delete = composeRule.onNodeWithTag(DETAIL_DELETE_ACTION_TAG)
        primary.assertIsDisplayed().performClick()
        delete.assertIsDisplayed().performClick()
        assertEquals(primary.fetchSemanticsNode().boundsInRoot.width, delete.fetchSemanticsNode().boundsInRoot.width, 1f)
        composeRule.runOnIdle {
            assertTrue(edited)
            assertTrue(deleted)
        }
    }
}
