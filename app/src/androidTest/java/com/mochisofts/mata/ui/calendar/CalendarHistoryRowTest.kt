package com.mochisofts.mata.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
    fun ch018_longRowsKeepBodyAlignedAcrossStateControlsAndShowDistinctScheduleDates() {
        val pendingTitle = "未完了の長いタイトルが二行を超えても本文領域からはみ出さず末尾が省略されるTODO"
        val completedTitle = "完了済みの長いタイトルが二行を超えても本文領域からはみ出さず末尾が省略されるTODO"
        val skippedTitle = "スキップ済みの長いタイトルが二行を超えても本文領域からはみ出さず末尾が省略されるTODO"
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(Modifier.width(360.dp)) {
                    HistoryEntryRow(
                        entry = historyEntry(pendingTitle, TodoState.PENDING, canUndoAction = false),
                        busyExecutionId = null,
                        onClick = {},
                        onUndoAction = {},
                    )
                    HistoryEntryRow(
                        entry = historyEntry(
                            completedTitle,
                            TodoState.COMPLETED,
                            canUndoAction = true,
                            scheduledLogicalDate = LocalDate.of(2026, 8, 30),
                            dueDate = LocalDate.of(2026, 9, 2),
                        ),
                        busyExecutionId = null,
                        onClick = {},
                        onUndoAction = {},
                    )
                    HistoryEntryRow(
                        entry = historyEntry(skippedTitle, TodoState.SKIPPED, canUndoAction = true),
                        busyExecutionId = null,
                        onClick = {},
                        onUndoAction = {},
                    )
                }
            }
        }

        val pendingLeft = composeRule.onNodeWithText(pendingTitle)
            .fetchSemanticsNode()
            .boundsInRoot.left
        val completedLeft = composeRule.onNodeWithText(completedTitle)
            .fetchSemanticsNode()
            .boundsInRoot.left
        val skippedLeft = composeRule.onNodeWithText(skippedTitle)
            .fetchSemanticsNode()
            .boundsInRoot.left

        assertEquals(pendingLeft, completedLeft, POSITION_TOLERANCE_PX)
        assertEquals(pendingLeft, skippedLeft, POSITION_TOLERANCE_PX)
        composeRule.onNodeWithText("$completedTitle の履歴に保存された長い説明が二行を超えても全文を読み上げられる説明文")
            .assertExists()
        composeRule.onNodeWithText("実行日 2026年8月30日・期限日 2026年9月2日").assertExists()
    }
}

private fun historyEntry(
    title: String,
    state: TodoState,
    canUndoAction: Boolean,
    scheduledLogicalDate: LocalDate? = null,
    dueDate: LocalDate? = null,
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
            description = "$title の履歴に保存された長い説明が二行を超えても全文を読み上げられる説明文",
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
            dueDate = dueDate,
            scheduledLogicalDate = scheduledLogicalDate,
            resolvedLogicalDate = date,
        ),
        canUndoAction = canUndoAction,
    )
}

private const val POSITION_TOLERANCE_PX = 0.5f
