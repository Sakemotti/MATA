package com.mochisofts.mata.widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.domain.model.AdsRuntimeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WidgetTodoActionDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyDialogShowsAllActionsAndDispatchesSkip() {
        var skipped = false
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                WidgetTodoActionDialog(
                    state = WidgetTodoActionUiState(
                        isLoading = false,
                        title = "朝のストレッチ",
                        actionsEnabled = true,
                    ),
                    adsRuntime = AdsRuntimeState(),
                    isForeground = true,
                    onComplete = {},
                    onSkip = { skipped = true },
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithText("朝のストレッチに対する操作を選択してください。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("完了").assertIsDisplayed()
        composeRule.onNodeWithText("スキップ").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("キャンセル").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(skipped) }
    }
}
