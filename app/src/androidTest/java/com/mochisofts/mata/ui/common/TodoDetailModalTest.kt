package com.mochisofts.mata.ui.common

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mochisofts.mata.core.designsystem.MataTheme
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
                TodoDetailModal(
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
        composeRule.onNodeWithText("閉じる").assertIsDisplayed().performClick()
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
                TodoDetailModal(
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
        composeRule.onNodeWithText("閉じる").assertIsDisplayed()
        composeRule.onNodeWithText("最終値").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("閉じる").assertIsDisplayed()
    }
}
