package com.mochisofts.mata.data.notification

import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNotificationSchedulerTest {
    @Test
    fun incrementalReconciliation_reusesMatchingScheduledAlarm() {
        assertTrue(
            canReuseScheduledAlarm(
                previous = scheduledNotification(),
                desiredTriggerAt = TRIGGER_AT,
                desiredMode = AndroidNotificationScheduler.MODE_EXACT,
                canPostNotifications = true,
                reconciliationMode = AlarmReconciliationMode.INCREMENTAL,
            ),
        )
    }

    @Test
    fun platformEventRebuild_doesNotTrustPersistedScheduledState() {
        assertFalse(
            canReuseScheduledAlarm(
                previous = scheduledNotification(),
                desiredTriggerAt = TRIGGER_AT,
                desiredMode = AndroidNotificationScheduler.MODE_EXACT,
                canPostNotifications = true,
                reconciliationMode = AlarmReconciliationMode.REBUILD_OS_REGISTRATIONS,
            ),
        )
    }

    @Test
    fun incrementalReconciliation_doesNotReuseMismatchedOrUnavailableAlarm() {
        val scheduled = scheduledNotification()
        assertFalse(reusable(previous = scheduled.copy(state = AndroidNotificationScheduler.STATE_FAILED)))
        assertFalse(reusable(previous = scheduled.copy(triggerAt = TRIGGER_AT + 1)))
        assertFalse(reusable(previous = scheduled.copy(schedulingMode = AndroidNotificationScheduler.MODE_INEXACT)))
        assertFalse(reusable(previous = scheduled, canPostNotifications = false))
        assertFalse(reusable(previous = null))
    }

    private fun reusable(
        previous: ScheduledNotificationEntity?,
        canPostNotifications: Boolean = true,
    ): Boolean = canReuseScheduledAlarm(
        previous = previous,
        desiredTriggerAt = TRIGGER_AT,
        desiredMode = AndroidNotificationScheduler.MODE_EXACT,
        canPostNotifications = canPostNotifications,
        reconciliationMode = AlarmReconciliationMode.INCREMENTAL,
    )

    private fun scheduledNotification() = ScheduledNotificationEntity(
        candidateKey = "todo|notification|2026-08-26|1",
        todoId = "todo",
        notificationSettingId = "notification",
        logicalDate = "2026-08-26",
        definitionRevision = 1,
        triggerAt = TRIGGER_AT,
        requestCode = 10_000,
        schedulingMode = AndroidNotificationScheduler.MODE_EXACT,
        state = AndroidNotificationScheduler.STATE_SCHEDULED,
        failureCode = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private companion object {
        const val TRIGGER_AT = 1_777_777_777_000L
    }
}
