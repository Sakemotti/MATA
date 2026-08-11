package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoScheduleCalculatorTest {
    @Test
    fun logicalDate_usesConfiguredBoundary() {
        val zone = ZoneId.of("Asia/Tokyo")

        assertEquals(
            LocalDate.of(2026, 8, 10),
            logicalDate(ZonedDateTime.of(2026, 8, 11, 3, 59, 0, 0, zone), 4),
        )
        assertEquals(
            LocalDate.of(2026, 8, 11),
            logicalDate(ZonedDateTime.of(2026, 8, 11, 4, 0, 0, 0, zone), 4),
        )
        assertEquals(
            LocalDate.of(2026, 8, 11),
            logicalDate(ZonedDateTime.of(2026, 8, 11, 0, 0, 0, 0, zone), 0),
        )
    }

    @Test
    fun fixedRecurrenceTypes_matchExpectedDates() {
        val start = LocalDate.of(2026, 8, 10)
        val end = LocalDate.of(2026, 8, 23)

        assertEquals(
            listOf(start),
            todo(start, end, RecurrenceRule.once()).occurrencesIn(start, end),
        )
        assertEquals(
            14,
            todo(start, end, RecurrenceRule.daily()).occurrencesIn(start, end).size,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 8, 12),
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 19),
            ),
            todo(
                start,
                end,
                RecurrenceRule(
                    RecurrenceType.SELECTED_WEEKDAYS,
                    selectedWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                ),
            ).occurrencesIn(start, end),
        )
    }

    @Test
    fun weekdays_excludeProvidedHolidaysOnlyForWeekdayRule() {
        val monday = LocalDate.of(2026, 8, 10)
        val holiday = LocalDate.of(2026, 8, 11)
        val weekdays = todo(monday, monday.plusDays(6), RecurrenceRule(RecurrenceType.WEEKDAYS))
        val selected = todo(
            monday,
            monday.plusDays(6),
            RecurrenceRule(
                RecurrenceType.SELECTED_WEEKDAYS,
                selectedWeekdays = setOf(DayOfWeek.TUESDAY),
            ),
        )

        assertFalse(weekdays.occursOn(holiday, setOf(holiday)))
        assertTrue(selected.occursOn(holiday, setOf(holiday)))
        assertEquals(5, weekdays.occurrencesIn(monday, monday.plusDays(6)).size)
    }

    @Test
    fun monthlyRules_handleMissingDaysAndLeapYear() {
        val monthly31 = todo(
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 4, 30),
            RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = 31),
        )
        assertEquals(
            listOf(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29),
                LocalDate.of(2024, 3, 31),
                LocalDate.of(2024, 4, 30),
            ),
            monthly31.occurrencesIn(monthly31.startDate, monthly31.endDate!!),
        )

        val monthEnd = monthly31.copy(recurrenceRule = RecurrenceRule(RecurrenceType.MONTH_END))
        assertEquals(
            LocalDate.of(2024, 2, 29),
            monthEnd.occurrencesIn(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)).single(),
        )
    }

    @Test
    fun everyNDays_isAnchoredToStartDateAndIncludesEndDate() {
        val start = LocalDate.of(2026, 8, 10)
        val scheduled = todo(
            start,
            start.plusDays(9),
            RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3),
        )

        assertEquals(
            listOf(start, start.plusDays(3), start.plusDays(6), start.plusDays(9)),
            scheduled.occurrencesIn(start.minusDays(5), start.plusDays(20)),
        )
    }

    @Test
    fun countPeriods_followWeekStartAndClampToActivePartialPeriod() {
        val todo = todo(
            start = LocalDate.of(2026, 8, 12),
            end = LocalDate.of(2026, 8, 14),
            rule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 7),
        )

        assertEquals(
            RecurrencePeriod(
                startDate = LocalDate.of(2026, 8, 12),
                endDate = LocalDate.of(2026, 8, 14),
                requiredCount = 3,
            ),
            todo.recurrencePeriod(LocalDate.of(2026, 8, 13), DayOfWeek.MONDAY),
        )
    }

    @Test
    fun deadline_resolvesAgainstLogicalDateBoundary() {
        val zone = ZoneId.of("Asia/Tokyo")
        val date = LocalDate.of(2026, 8, 10)

        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 3, 0, 0, 0, zone),
            deadlineAt(date, 4, 3 * 60, zone),
        )
        assertEquals(
            ZonedDateTime.of(2026, 8, 10, 4, 0, 0, 0, zone),
            deadlineAt(date, 4, 4 * 60, zone),
        )
        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 4, 0, 0, 0, zone),
            deadlineAt(date, 4, null, zone),
        )
    }

    @Test
    fun logicalBoundary_usesZoneRulesForMissingDstTime() {
        val zone = ZoneId.of("America/New_York")
        val start = logicalDayStart(LocalDate.of(2026, 3, 8), 2, zone)

        assertEquals(3, start.hour)
        assertEquals(LocalDate.of(2026, 3, 8), start.toLocalDate())
    }

    private fun todo(
        start: LocalDate,
        end: LocalDate?,
        rule: RecurrenceRule,
    ) = Todo(
        id = "todo",
        title = "title",
        description = "",
        categoryId = null,
        startDate = start,
        endDate = end,
        recurrenceRule = rule,
        dueMinutes = null,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = 0,
    )
}
