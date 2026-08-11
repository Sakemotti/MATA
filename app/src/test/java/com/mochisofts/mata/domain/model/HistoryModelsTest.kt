package com.mochisofts.mata.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryModelsTest {
    private val date = LocalDate.of(2026, 8, 10)

    @Test
    fun unfinishedOrSkipped_hasPriorityOverPendingAndCompleted() {
        val summary = summarizeHistoryDay(
            date,
            listOf(TodoState.COMPLETED, TodoState.PENDING, TodoState.SKIPPED),
            emptyList(),
        )

        assertEquals(1, summary.completedCount)
        assertEquals(3, summary.plannedCount)
        assertEquals(HistoryDayState.UNACHIEVED, summary.state)
    }

    @Test
    fun pendingWithoutFailure_isInProgress() {
        val summary = summarizeHistoryDay(
            date,
            listOf(TodoState.COMPLETED, TodoState.PENDING),
            emptyList(),
        )

        assertEquals(HistoryDayState.IN_PROGRESS, summary.state)
    }

    @Test
    fun allCompleted_isCompletedAndPeriodMarkersStayIndependent() {
        val summary = summarizeHistoryDay(
            date,
            listOf(TodoState.COMPLETED, TodoState.COMPLETED),
            listOf(true, false),
        )

        assertEquals(HistoryDayState.COMPLETED, summary.state)
        assertTrue(summary.hasAchievedPeriod)
        assertTrue(summary.hasUnachievedPeriod)
    }

    @Test
    fun onlyPeriodResult_hasNoDailyStateOrCounts() {
        val summary = summarizeHistoryDay(date, emptyList(), listOf(true))

        assertEquals(0, summary.completedCount)
        assertEquals(0, summary.plannedCount)
        assertNull(summary.state)
        assertTrue(summary.hasAchievedPeriod)
        assertFalse(summary.hasUnachievedPeriod)
    }
}
