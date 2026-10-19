package com.mochisofts.mata.ui.incomplete

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.IncompleteTodoGroup
import com.mochisofts.mata.domain.model.IncompleteTodoSource
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IncompleteTodoListScreenTest {
    @get:Rule val rule = createComposeRule()
    private val date = LocalDate.of(2026, 10, 18)
    private fun group(id: String = "one", category: Category? = null) = IncompleteTodoGroup(
        Todo(id, "確認対象$id", "", category?.id, date.minusDays(3), null, RecurrenceRule.daily(), null, 1, null, 0),
        category, 3, date.minusDays(3), date.minusDays(1), null, IncompleteTodoSource(id, date.minusDays(1)),
    )

    @Test fun expandsDatesAndProvidesRegistrationAndHideActionsInsideTheCard() {
        val group = group()
        var registered: IncompleteTodoSource? = null
        var hidden = false
        rule.setContent {
            MataTheme(useDynamicColor = false) {
                IncompleteTodoListContent(IncompleteTodoListUiState(false, groups = listOf(group), isReconciling = false),
                    { registered = it }, { hidden = true }, {}, history = { Text("展開した対象日") })
            }
        }
        rule.onNodeWithTag("incomplete_card:one").assertIsDisplayed()
        rule.onNodeWithTag("incomplete_list").performScrollToNode(hasText("対象日を表示"))
        rule.onNodeWithText("対象日を表示").performClick()
        rule.onNodeWithTag("incomplete_list").performScrollToNode(hasText("展開した対象日"))
        rule.onNodeWithText("展開した対象日").assertIsDisplayed()
        rule.onNodeWithTag("incomplete_list").performScrollToNode(hasText("今日への再登録"))
        rule.onNodeWithText("今日への再登録").performClick()
        rule.onNodeWithTag("incomplete_list").performScrollToNode(hasText("未完了TODOに表示しない"))
        rule.onNodeWithText("未完了TODOに表示しない").performClick()
        rule.runOnIdle { assertEquals(group.source, registered); assertTrue(hidden) }
    }

    @Test fun categoryFilterDoesNotMergeDifferentTodosWithTheSameTitle() {
        val first = group()
        val second = group("two", Category("work", "仕事", 1, "Work", 0))
        rule.setContent {
            MataTheme(useDynamicColor = false) {
                IncompleteTodoListContent(IncompleteTodoListUiState(false, groups = listOf(first, second), isReconciling = false),
                    {}, {}, {}, history = {})
            }
        }
        rule.onNode(hasText("仕事") and hasClickAction()).performClick()
        rule.onNodeWithTag("incomplete_card:one").assertDoesNotExist()
        rule.onNodeWithTag("incomplete_card:two").assertIsDisplayed()
    }

    @Test fun darkThemeAtTwoHundredPercentFontKeepsBothActionsReachable() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MataTheme(appTheme = AppTheme.DARK, useDynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        IncompleteTodoListContent(IncompleteTodoListUiState(false, groups = listOf(group()), isReconciling = false),
                            {}, {}, {}, history = {})
                    }
                }
            }
        }
        rule.onNodeWithTag("incomplete_list").performScrollToNode(hasText("今日への再登録"))
        rule.onNodeWithText("今日への再登録").assertIsDisplayed().performClick()
        rule.onNodeWithTag("incomplete_list").performScrollToNode(hasText("未完了TODOに表示しない"))
        rule.onNodeWithText("未完了TODOに表示しない").assertIsDisplayed().performClick()
    }
}
