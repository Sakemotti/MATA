package com.mochisofts.mata.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.TodoState
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalendarHistoryRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bodyStartsAtSamePositionForStateIconCheckboxAndUndoButton() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(Modifier.width(360.dp)) {
                    HistoryEntryRow(
                        entry = historyEntry("未完了項目", TodoState.PENDING, canUndoAction = false),
                        busyExecutionId = null,
                        onClick = {},
                        onUndoAction = {},
                    )
                    HistoryEntryRow(
                        entry = historyEntry("完了項目", TodoState.COMPLETED, canUndoAction = true),
                        busyExecutionId = null,
                        onClick = {},
                        onUndoAction = {},
                    )
                    HistoryEntryRow(
                        entry = historyEntry("スキップ項目", TodoState.SKIPPED, canUndoAction = true),
                        busyExecutionId = null,
                        onClick = {},
                        onUndoAction = {},
                    )
                }
            }
        }

        val pendingLeft = composeRule.onNodeWithText("未完了項目")
            .fetchSemanticsNode()
            .boundsInRoot.left
        val completedLeft = composeRule.onNodeWithText("完了項目")
            .fetchSemanticsNode()
            .boundsInRoot.left
        val skippedLeft = composeRule.onNodeWithText("スキップ項目")
            .fetchSemanticsNode()
            .boundsInRoot.left

        assertEquals(pendingLeft, completedLeft, POSITION_TOLERANCE_PX)
        assertEquals(pendingLeft, skippedLeft, POSITION_TOLERANCE_PX)
    }
}

private fun historyEntry(
    title: String,
    state: TodoState,
    canUndoAction: Boolean,
): HistoryEntry {
    val date = LocalDate.of(2026, 9, 1)
    return HistoryEntry(
        id = "$title-id",
        todoId = "$title-todo",
        logicalDate = date,
        state = state,
        actedAt = if (state == TodoState.PENDING) null else 1L,
        finalizedAt = null,
        snapshot = HistoryTodoSnapshot(
            todoId = "$title-todo",
            definitionRevision = 1,
            title = title,
            description = "",
            startDate = date,
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = null,
            notifications = emptyList(),
            categoryId = null,
            categoryName = null,
            categoryColorIndex = null,
            categoryIconName = null,
            categorySortOrder = null,
            endHour = 0,
            weekStart = DayOfWeek.MONDAY,
            createdAt = 0L,
        ),
        canUndoAction = canUndoAction,
    )
}

private const val POSITION_TOLERANCE_PX = 0.5f
