package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveModelsTest {
    @Test
    fun sortOrder_unknownValueFallsBackToNewest() {
        assertEquals(ArchiveSortOrder.NEWEST, ArchiveSortOrder.fromStoredValue("unknown"))
        assertEquals(ArchiveSortOrder.TITLE, ArchiveSortOrder.fromStoredValue("title"))
    }

    @Test
    fun historyComparator_ordersDateThenTimeThenExecutionBeforePeriod() {
        val date = LocalDate.of(2026, 8, 10)
        val snapshot = HistoryTodoSnapshot(
            todoId = "todo",
            definitionRevision = 1,
            title = "title",
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
            createdAt = 1,
        )
        val older = ArchivedHistoryItem.Execution(
            HistoryEntry(
                id = "older",
                todoId = "todo",
                logicalDate = date.minusDays(1),
                state = TodoState.COMPLETED,
                actedAt = 30,
                finalizedAt = 30,
                snapshot = snapshot,
                canUndoAction = false,
            ),
        )
        val period = ArchivedHistoryItem.Period(
            PeriodHistoryEntry(
                id = "period",
                todoId = "todo",
                periodType = RecurrenceType.WEEKLY_COUNT,
                periodStart = date.minusDays(6),
                periodEnd = date,
                requiredCount = 1,
                completedCount = 1,
                achieved = true,
                displayDate = date,
                finalizedAt = 40,
                snapshot = snapshot,
            ),
        )
        val execution = ArchivedHistoryItem.Execution(
            HistoryEntry(
                id = "execution",
                todoId = "todo",
                logicalDate = date,
                state = TodoState.COMPLETED,
                actedAt = 40,
                finalizedAt = 40,
                snapshot = snapshot,
                canUndoAction = false,
            ),
        )

        assertEquals(
            listOf(execution, period, older),
            listOf(older, period, execution).sortedWith(archivedHistoryComparator),
        )
    }
}
