package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionRecurrenceTest {
    private val start = LocalDate.of(2026, 11, 13)
    private fun todo(interval: Int = 3, resolution: LocalDate? = null) = Todo(
        "cycle", "cycle", "", null, start, null,
        RecurrenceRule(RecurrenceType.AFTER_COMPLETION_DAYS, intervalDays = interval),
        600, 1, null, 0, carryOverEnabled = true, lastResolvedLogicalDate = resolution,
    )

    @Test fun firstOccurrenceIsStartAndLaterUnknownDatesAreNeverInvented() {
        val todo = todo()
        assertEquals(listOf(start), todo.nextOccurrences(start, 100))
        assertEquals(listOf(start), todo.occurrencesIn(start, start.plusYears(100)))
        assertTrue(todo.occursOn(start))
        assertFalse(todo.occursOn(start.plusDays(3)))
        assertNull(todo.nextOccurrenceOnOrAfter(start.plusDays(1)))
    }

    @Test fun delayedCompletionUsesResolvedDateRatherThanOriginalStartOrSchedule() {
        val resolved = start.plusDays(4)
        val todo = todo(resolution = resolved)
        assertEquals(resolved.plusDays(3), todo.nextCompletionScheduledDate())
        assertEquals(listOf(resolved.plusDays(3)), todo.nextOccurrences(start, 3))
        assertNull(todo.completionOccurrenceOn(resolved.plusDays(2)))
    }

    @Test fun unfinishedOccurrenceRemainsTheSameSingleOccurrenceForYears() {
        val todo = todo()
        assertEquals(start, todo.completionOccurrenceOn(start.plusYears(20)))
        assertNull(todo.completionOccurrenceOn(start.minusDays(1)))
    }

    @Test fun endDateLimitsNewOccurrencesButDoesNotDiscardAlreadyDueOccurrence() {
        val todo = todo().copy(endDate = start)
        assertEquals(start, todo.completionOccurrenceOn(start.plusDays(20)))
        assertNull(todo.copy(lastResolvedLogicalDate = start).nextCompletionScheduledDate())
        assertNull(todo.copy(archivedAt = 1).nextCompletionScheduledDate())
    }

    @Test fun intervalsAreValidatedAtOneAnd999AndRejectZeroOr1000() {
        assertTrue(todo(1).recurrenceRule.isValid())
        assertTrue(todo(999).recurrenceRule.isValid())
        assertFalse(todo(0).recurrenceRule.isValid())
        assertFalse(todo(1000).recurrenceRule.isValid())
        assertNull(todo(0).nextCompletionScheduledDate())
    }

    @Test fun startDateAndLeapMonthBoundariesRemainWellDefined() {
        val leap = LocalDate.of(2028, 2, 28)
        assertEquals(LocalDate.of(2028, 3, 1), todo(2, leap).nextCompletionScheduledDate())
        assertEquals(start, todo(3, start.minusDays(10)).nextCompletionScheduledDate())
    }

    @Test fun undoRecomputesFutureWithoutChangingTheEarlierResolution() {
        val earlier = start.plusDays(1)
        val previous = todo(resolution = earlier)
        val completed = previous.copy(lastResolvedLogicalDate = start.plusDays(5))
        assertEquals(start.plusDays(8), completed.nextCompletionScheduledDate())
        assertEquals(start.plusDays(4), previous.nextCompletionScheduledDate())
    }

    @Test fun notificationSchedulesOnlyTheKnownNextOccurrenceAndNeverRepeatsOverdue() {
        val todo = todo(resolution = start)
        val notification = TodoNotification("n", NotificationRelation.AT, 0, NotificationUnit.MINUTE)
        val zone = ZoneId.of("Asia/Tokyo")
        val candidate = nextNotificationCandidate(todo, notification, 0,
            start.atTime(12, 0).atZone(zone), DayOfWeek.MONDAY)
        assertEquals(start.plusDays(3), candidate?.logicalDate)
        assertNull(nextNotificationCandidate(todo, notification, 0,
            start.plusDays(4).atTime(12, 0).atZone(zone), DayOfWeek.MONDAY))
    }

    @Test fun logicalDayBoundaryIsUsedAsTheAnchorRatherThanCalendarDay() {
        val now = start.plusDays(1).atTime(2, 0).atZone(ZoneId.of("Asia/Tokyo"))
        assertEquals(start.plusDays(3), todo(resolution = logicalDate(now, 4)).nextCompletionScheduledDate())
    }

    @Test fun existingFixedIntervalStillIgnoresResolutionHistory() {
        val todo = todo(resolution = start.plusDays(5)).copy(
            recurrenceRule = RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3),
        )
        assertTrue(todo.occursOn(start.plusDays(3)))
        assertEquals(start.plusDays(6), todo.nextOccurrenceOnOrAfter(start.plusDays(4)))
    }
}
