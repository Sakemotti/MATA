package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationModelsTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test
    fun triggerCalculationSupportsBeforeAtAndAfter() {
        val deadline = ZonedDateTime.of(2026, 8, 12, 12, 0, 0, 0, zone)

        assertEquals(
            deadline.minusMinutes(30),
            notificationTriggerAt(deadline, notification(NotificationRelation.BEFORE, 30)),
        )
        assertEquals(
            deadline,
            notificationTriggerAt(deadline, notification(NotificationRelation.AT, 0)),
        )
        assertEquals(
            deadline.plusHours(2),
            notificationTriggerAt(
                deadline,
                notification(NotificationRelation.AFTER, 2, NotificationUnit.HOUR),
            ),
        )
    }

    @Test
    fun calendarDayKeepsLocalTimeAcrossDst() {
        val newYork = ZoneId.of("America/New_York")
        val deadline = ZonedDateTime.of(2026, 3, 9, 9, 0, 0, 0, newYork)

        val trigger = notificationTriggerAt(
            deadline,
            notification(NotificationRelation.BEFORE, 1, NotificationUnit.DAY),
        )

        assertEquals(9, trigger.hour)
        assertEquals(LocalDate.of(2026, 3, 8), trigger.toLocalDate())
    }

    @Test
    fun validationRejectsEquivalentDuplicatesAndInvalidAfterDeadline() {
        val errors = validateNotifications(
            notifications = listOf(
                notification(NotificationRelation.BEFORE, 60),
                notification(NotificationRelation.BEFORE, 1, NotificationUnit.HOUR, "second"),
                notification(NotificationRelation.AFTER, 1, NotificationUnit.HOUR, "after"),
            ),
            dueMinutes = 23 * 60 + 30,
            endHour = 0,
        )

        assertTrue(NotificationValidationError.DUPLICATE in errors)
        assertTrue(NotificationValidationError.AFTER_DAY_END in errors)
    }

    @Test
    fun nextCandidateSkipsPastCandidateAndCompletedOccurrence() {
        val todo = todo(
            startDate = LocalDate.of(2026, 8, 10),
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = 12 * 60,
        )
        val now = ZonedDateTime.of(2026, 8, 11, 13, 0, 0, 0, zone)

        val candidate = nextNotificationCandidate(
            todo = todo,
            notification = notification(NotificationRelation.BEFORE, 30),
            endHour = 0,
            now = now,
            weekStart = DayOfWeek.MONDAY,
            completedDates = setOf(LocalDate.of(2026, 8, 12)),
        )

        assertEquals(LocalDate.of(2026, 8, 13), candidate?.logicalDate)
        assertEquals(11, candidate?.triggerAt?.hour)
        assertEquals(30, candidate?.triggerAt?.minute)
    }

    @Test
    fun nextCandidate_skipsJapaneseHolidayForWeekdayTodo() {
        val todo = todo(
            startDate = LocalDate.of(2026, 8, 10),
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKDAYS),
            dueMinutes = 12 * 60,
        )

        val candidate = nextNotificationCandidate(
            todo = todo,
            notification = notification(NotificationRelation.AT, 0),
            endHour = 0,
            now = ZonedDateTime.of(2026, 8, 10, 9, 0, 0, 0, zone),
            weekStart = DayOfWeek.MONDAY,
            holidays = setOf(LocalDate.of(2026, 8, 10)),
        )

        assertEquals(LocalDate.of(2026, 8, 11), candidate?.logicalDate)
    }

    @Test
    fun invalidBoundaryAfterNotificationHasNoCandidate() {
        val todo = todo(
            startDate = LocalDate.of(2026, 8, 10),
            recurrenceRule = RecurrenceRule.once(),
            dueMinutes = null,
        )

        val candidate = nextNotificationCandidate(
            todo = todo,
            notification = notification(NotificationRelation.AFTER, 1),
            endHour = 4,
            now = ZonedDateTime.of(2026, 8, 10, 0, 0, 0, 0, zone),
            weekStart = DayOfWeek.MONDAY,
        )

        assertNull(candidate)
    }

    private fun notification(
        relation: NotificationRelation,
        amount: Int,
        unit: NotificationUnit = NotificationUnit.MINUTE,
        id: String = "notification-id",
    ) = TodoNotification(id, relation, amount, unit)

    private fun todo(
        startDate: LocalDate,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
    ) = Todo(
        id = "todo-id",
        title = "title",
        description = "",
        categoryId = null,
        startDate = startDate,
        endDate = null,
        recurrenceRule = recurrenceRule,
        dueMinutes = dueMinutes,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = 1,
    )
}
