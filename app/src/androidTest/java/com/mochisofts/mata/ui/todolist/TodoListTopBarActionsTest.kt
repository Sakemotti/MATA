package com.mochisofts.mata.ui.todolist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mochisofts.mata.core.designsystem.MataTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodoListTopBarActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayShowsOnlyCompletedTextAction() {
        var toggled = false
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoListTopBarActions(
                    isToday = true,
                    showCompleted = false,
                    onToggleCompleted = { toggled = true },
                    onToday = {},
                )
            }
        }

        composeRule.onNodeWithText("完了済みTODOを表示")
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithText("今日へ戻る").assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(toggled) }
    }

    @Test
    fun anotherDateShowsOnlyReturnToTodayAction() {
        var returnedToToday = false
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoListTopBarActions(
                    isToday = false,
                    showCompleted = false,
                    onToggleCompleted = {},
                    onToday = { returnedToToday = true },
                )
            }
        }

        composeRule.onNodeWithText("今日へ戻る")
            .assertIsDisplayed()
            .performClick()
        composeRule.onAllNodesWithText("完了済みTODOを表示").assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(returnedToToday) }
    }
}
