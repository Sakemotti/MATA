package com.mochisofts.mata.app.notification

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIdentityTest {
    private val date = LocalDate.of(2026, 8, 25)

    @Test
    fun individualTags_areRecognizedForReminderAndCompletion() {
        assertTrue(isMataIndividualNotificationTag(reminderNotificationTag("todo-1", date)))
        assertTrue(isMataIndividualNotificationTag(completedNotificationTag("todo-1", date)))
        assertTrue(isMataIndividualNotificationTag("mata_todo"))
        assertFalse(isMataIndividualNotificationTag("mata_summary"))
        assertFalse(isMataIndividualNotificationTag(null))
    }

    @Test
    fun todoMatching_doesNotMatchAnIdWithTheSamePrefix() {
        val reminder = reminderNotificationTag("todo-10", date)
        val completed = completedNotificationTag("todo-10", date)

        assertTrue(notificationTagBelongsToTodo(reminder, "todo-10"))
        assertTrue(notificationTagBelongsToTodo(completed, "todo-10"))
        assertFalse(notificationTagBelongsToTodo(reminder, "todo-1"))
        assertFalse(notificationTagBelongsToTodo(completed, "todo-1"))
    }
}
