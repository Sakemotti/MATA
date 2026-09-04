package com.mochisofts.mata.domain.model

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** One test per release test-spec ID for notification calculations. */
class NotificationTestSpecCoverageTest {
    @Test
    fun ntf001_beforeAtAndAfterUseMinuteHourAndCalendarDayOffsets() {
        val zone = ZoneId.of("Asia/Tokyo")
        val logicalDate = LocalDate.of(2026, 9, 4)
        val deadline = deadlineAt(logicalDate, endHour = 4, dueMinutes = 3 * 60, zoneId = zone)

        assertEquals(ZonedDateTime.of(2026, 9, 5, 3, 0, 0, 0, zone), deadline)
        assertEquals(
            deadline.minusMinutes(15),
            notificationTriggerAt(deadline, notification(NotificationRelation.BEFORE, 15, NotificationUnit.MINUTE)),
        )
        assertEquals(
            deadline.minusHours(2),
            notificationTriggerAt(deadline, notification(NotificationRelation.BEFORE, 2, NotificationUnit.HOUR)),
        )
        assertEquals(
            deadline.minusDays(1),
            notificationTriggerAt(deadline, notification(NotificationRelation.BEFORE, 1, NotificationUnit.DAY)),
        )
        assertEquals(
            deadline,
            notificationTriggerAt(deadline, notification(NotificationRelation.AT, 0, NotificationUnit.MINUTE)),
        )
        assertEquals(
            deadline.plusMinutes(20),
            notificationTriggerAt(deadline, notification(NotificationRelation.AFTER, 20, NotificationUnit.MINUTE)),
        )
        assertEquals(
            deadline.plusHours(1),
            notificationTriggerAt(deadline, notification(NotificationRelation.AFTER, 1, NotificationUnit.HOUR)),
        )
    }

    private fun notification(
        relation: NotificationRelation,
        amount: Int,
        unit: NotificationUnit,
    ) = TodoNotification("notification", relation, amount, unit)
}
