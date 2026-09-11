package com.mochisofts.mata.ui.todolist

import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import java.time.LocalDate
import org.junit.Assert.assertEquals
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

    @Test
    fun tl011_missingDueTimeUsesLogicalDayEndAndDisplaysNoDeadline() {
        val date = LocalDate.of(2026, 9, 9)
        val timed = occurrence(id = "timed", date = date, dueMinutes = 8 * 60)
        val missing = occurrence(id = "missing", date = date, dueMinutes = null)

        val groups = buildTodoOccurrenceGroups(
            occurrences = listOf(missing, timed),
            dayEndHour = 4,
        )
        assertEquals(listOf("timed", "missing"), groups.single().occurrences.map { it.todo.id })

        val noDeadline = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.todo_due_none)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoOccurrenceRow(
                    occurrence = missing,
                    canComplete = true,
                    showActions = false,
                    onComplete = {},
                    onSkip = {},
                    onArchive = {},
                    onDelete = {},
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText(noDeadline).assertIsDisplayed()
    }

    private fun occurrence(
        id: String,
        date: LocalDate,
        dueMinutes: Int?,
    ): TodoOccurrence = TodoOccurrence(
        todo = Todo(
            id = id,
            title = id,
            description = "",
            categoryId = null,
            startDate = date,
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = dueMinutes,
            definitionRevision = 1,
            archivedAt = null,
            createdAt = 1,
        ),
        category = null,
        logicalDate = date,
        state = TodoState.PENDING,
    )
}
