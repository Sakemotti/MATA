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
    fun weekdayPresets_distinguishWeekdaysFromWeekendsAndHolidays() {
        val mondayHoliday = LocalDate.of(2026, 8, 10)
        val saturday = LocalDate.of(2026, 8, 15)
        val weekdays = todo(
            mondayHoliday,
            saturday,
            RecurrenceRule(
                RecurrenceType.SELECTED_WEEKDAYS,
                dayFilter = RecurrenceDayFilter.WEEKDAYS,
            ),
        )
        val weekendsAndHolidays = weekdays.copy(
            recurrenceRule = RecurrenceRule(
                RecurrenceType.SELECTED_WEEKDAYS,
                dayFilter = RecurrenceDayFilter.WEEKENDS_HOLIDAYS,
            ),
        )

        assertFalse(weekdays.occursOn(mondayHoliday, setOf(mondayHoliday)))
        assertTrue(weekendsAndHolidays.occursOn(mondayHoliday, setOf(mondayHoliday)))
        assertTrue(weekendsAndHolidays.occursOn(saturday, emptySet()))
        assertFalse(weekendsAndHolidays.occursOn(mondayHoliday.plusDays(1), emptySet()))
    }

    @Test
    fun ted04_monthlyDaysAndMonthEndUseActualLastDayInCommonAndLeapYears() {
        listOf(29, 30, 31).forEach { monthlyDay ->
            val commonYear = todo(
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2023, 3, 31),
                RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = monthlyDay),
            )
            val leapYear = todo(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31),
                RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = monthlyDay),
            )

            assertEquals(
                listOf(
                    LocalDate.of(2023, 1, monthlyDay),
                    LocalDate.of(2023, 2, 28),
                    LocalDate.of(2023, 3, monthlyDay),
                ),
                commonYear.occurrencesIn(commonYear.startDate, commonYear.endDate!!),
            )
            assertEquals(
                listOf(
                    LocalDate.of(2024, 1, monthlyDay),
                    LocalDate.of(2024, 2, 29),
                    LocalDate.of(2024, 3, monthlyDay),
                ),
                leapYear.occurrencesIn(leapYear.startDate, leapYear.endDate!!),
            )
        }

        listOf(2023 to 28, 2024 to 29).forEach { (year, februaryEnd) ->
            val monthEnd = todo(
                LocalDate.of(year, 2, 1),
                LocalDate.of(year, 2, februaryEnd),
                RecurrenceRule(RecurrenceType.MONTH_END),
            )
            assertEquals(
                LocalDate.of(year, 2, februaryEnd),
                monthEnd.occurrencesIn(monthEnd.startDate, monthEnd.endDate!!).single(),
            )
        }
    }

    @Test
    fun monthlyNthWeekdays_supportMultipleSelectionsAndSkipMissingFifthWeekday() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 4, 30)
        val rule = RecurrenceRule(
            RecurrenceType.MONTHLY_NTH_WEEKDAYS,
            monthlyNthWeekdays = setOf(
                MonthlyNthWeekday(1, DayOfWeek.MONDAY),
                MonthlyNthWeekday(3, DayOfWeek.FRIDAY),
                MonthlyNthWeekday(5, DayOfWeek.MONDAY),
            ),
        )
        val scheduled = todo(start, end, rule)

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 20),
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 20),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 17),
            ),
            scheduled.occurrencesIn(start, end),
        )
        assertTrue(scheduled.occursOn(LocalDate.of(2026, 1, 5), setOf(LocalDate.of(2026, 1, 5))))
        assertTrue(rule.isValid())
        assertFalse(RecurrenceRule(RecurrenceType.MONTHLY_NTH_WEEKDAYS).isValid())
        assertFalse(
            RecurrenceRule(
                RecurrenceType.MONTHLY_NTH_WEEKDAYS,
                monthlyNthWeekdays = setOf(MonthlyNthWeekday(6, DayOfWeek.MONDAY)),
            ).isValid(),
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
    fun weeklyCount_supportsMultiWeekPeriodsAndEligibleDayFilter() {
        val start = LocalDate.of(2026, 8, 12)
        val holiday = LocalDate.of(2026, 8, 14)
        val scheduled = todo(
            start = start,
            end = null,
            rule = RecurrenceRule(
                RecurrenceType.WEEKLY_COUNT,
                requiredCount = 1,
                periodWeeks = 2,
                dayFilter = RecurrenceDayFilter.WEEKENDS_HOLIDAYS,
            ),
        )

        assertEquals(
            RecurrencePeriod(
                startDate = start,
                endDate = LocalDate.of(2026, 8, 23),
                requiredCount = 1,
            ),
            scheduled.recurrencePeriod(start, DayOfWeek.MONDAY),
        )
        assertEquals(
            LocalDate.of(2026, 8, 24),
            scheduled.recurrencePeriod(LocalDate.of(2026, 8, 30), DayOfWeek.MONDAY)?.startDate,
        )
        assertTrue(scheduled.occursOn(holiday, setOf(holiday)))
        assertTrue(scheduled.occursOn(LocalDate.of(2026, 8, 15), emptySet()))
        assertFalse(scheduled.occursOn(LocalDate.of(2026, 8, 13), emptySet()))
    }

    @Test
    fun te015_deadlineResolvesBeforeAtAndAfterLogicalDayBoundary() {
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
            ZonedDateTime.of(2026, 8, 10, 5, 0, 0, 0, zone),
            deadlineAt(date, 4, 5 * 60, zone),
        )
        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 4, 0, 0, 0, zone),
            deadlineAt(date, 4, null, zone),
        )
    }

    @Test
    fun onceTodo_usesExecutionWindowAndEffectiveDueDate() {
        val start = LocalDate.of(2026, 9, 9)
        val due = LocalDate.of(2026, 9, 12)
        val once = todo(start, null, RecurrenceRule.once()).copy(
            dueDate = due,
            dueMinutes = 2 * 60,
        )

        assertFalse(once.isInExecutionWindow(start.minusDays(1)))
        assertTrue(once.isInExecutionWindow(start))
        assertTrue(once.isInExecutionWindow(due.minusDays(1)))
        assertTrue(once.isInExecutionWindow(due))
        assertFalse(once.isInExecutionWindow(due.plusDays(1)))
        assertEquals(due, once.effectiveDueDate(start))
        assertEquals(
            ZonedDateTime.of(2026, 9, 13, 2, 0, 0, 0, ZoneId.of("Asia/Tokyo")),
            once.deadlineAt(start, 4, ZoneId.of("Asia/Tokyo")),
        )
    }

    @Test
    fun effectiveDueSortMinutes_ordersDeadlinesWithinLogicalDay() {
        val base = todo(
            LocalDate.of(2026, 8, 10),
            null,
            RecurrenceRule.daily(),
        )

        assertEquals(4 * 60, base.copy(dueMinutes = 4 * 60).effectiveDueSortMinutes(4))
        assertEquals(23 * 60, base.copy(dueMinutes = 23 * 60).effectiveDueSortMinutes(4))
        assertEquals(27 * 60, base.copy(dueMinutes = 3 * 60).effectiveDueSortMinutes(4))
        assertEquals(28 * 60, base.effectiveDueSortMinutes(4))
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
