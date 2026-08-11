package com.mochisofts.mata

import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.occursOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceUnitTest {
    @Test
    fun logicalDate_beforeCategoryBoundary_isPreviousDate() {
        val now = ZonedDateTime.of(2026, 8, 10, 3, 59, 0, 0, ZoneId.of("Asia/Tokyo"))

        assertEquals(LocalDate.of(2026, 8, 9), logicalDate(now, endHour = 4))
    }

    @Test
    fun logicalDate_atCategoryBoundary_isCalendarDate() {
        val now = ZonedDateTime.of(2026, 8, 10, 4, 0, 0, 0, ZoneId.of("Asia/Tokyo"))

        assertEquals(LocalDate.of(2026, 8, 10), logicalDate(now, endHour = 4))
    }

    @Test
    fun oneTimeTodo_occursOnlyOnStartDate() {
        val todo = todo(RecurrenceType.ONCE)

        assertTrue(todo.occursOn(LocalDate.of(2026, 8, 10)))
        assertFalse(todo.occursOn(LocalDate.of(2026, 8, 11)))
    }

    @Test
    fun dailyTodo_occursFromStartDate() {
        val todo = todo(RecurrenceType.DAILY)

        assertFalse(todo.occursOn(LocalDate.of(2026, 8, 9)))
        assertTrue(todo.occursOn(LocalDate.of(2026, 8, 10)))
        assertTrue(todo.occursOn(LocalDate.of(2026, 8, 11)))
    }

    private fun todo(recurrenceType: RecurrenceType) = Todo(
        id = "todo-id",
        title = "テスト",
        description = "",
        categoryId = null,
        startDate = LocalDate.of(2026, 8, 10),
        endDate = null,
        recurrenceRule = RecurrenceRule(recurrenceType),
        dueMinutes = null,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = 0,
    )
}
