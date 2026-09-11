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
    fun ch009_dailyCountsIncludeEveryPlanAndOnlyCompletedInCompletedCount() {
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
    fun ch012_dayStateUsesUnachievedThenInProgressThenCompletedPriority() {
        val unachieved = summarizeHistoryDay(
            date = date,
            states = listOf(TodoState.COMPLETED, TodoState.PENDING, TodoState.SKIPPED),
            periodAchievements = emptyList(),
        )
        val inProgress = summarizeHistoryDay(
            date = date,
            states = listOf(TodoState.COMPLETED, TodoState.PENDING),
            periodAchievements = emptyList(),
        )
        val completed = summarizeHistoryDay(
            date = date,
            states = listOf(TodoState.COMPLETED, TodoState.COMPLETED),
            periodAchievements = emptyList(),
        )

        assertEquals(HistoryDayState.UNACHIEVED, unachieved.state)
        assertEquals(HistoryDayState.IN_PROGRESS, inProgress.state)
        assertEquals(HistoryDayState.COMPLETED, completed.state)
    }

    @Test
    fun chd04_dailyStateAndPeriodResultMarkersRemainIndependent() {
        val dailyUnachievedPeriodAchieved = summarizeHistoryDay(
            date = date,
            states = listOf(TodoState.SKIPPED),
            periodAchievements = listOf(true),
        )
        val dailyCompletedPeriodUnachieved = summarizeHistoryDay(
            date = date,
            states = listOf(TodoState.COMPLETED),
            periodAchievements = listOf(false),
        )

        assertEquals(HistoryDayState.UNACHIEVED, dailyUnachievedPeriodAchieved.state)
        assertTrue(dailyUnachievedPeriodAchieved.hasAchievedPeriod)
        assertFalse(dailyUnachievedPeriodAchieved.hasUnachievedPeriod)
        assertEquals(HistoryDayState.COMPLETED, dailyCompletedPeriodUnachieved.state)
        assertFalse(dailyCompletedPeriodUnachieved.hasAchievedPeriod)
        assertTrue(dailyCompletedPeriodUnachieved.hasUnachievedPeriod)
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
