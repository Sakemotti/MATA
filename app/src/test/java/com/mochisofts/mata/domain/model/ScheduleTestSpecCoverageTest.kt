package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One test per release test-spec ID for the pure schedule calculation rules. */
class ScheduleTestSpecCoverageTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")

    @Test
    fun day001_midnightBoundaryMatchesCalendarDate() {
        val date = LocalDate.of(2026, 8, 11)

        listOf(0 to 0, 12 to 34, 23 to 59).forEach { (hour, minute) ->
            assertEquals(date, logicalDate(date.atTime(hour, minute).atZone(tokyo), 0))
        }
    }

    @Test
    fun day002_fourOClockBoundaryChangesAtExactlyFour() {
        val date = LocalDate.of(2026, 8, 11)

        assertEquals(date.minusDays(1), logicalDate(date.atTime(3, 59).atZone(tokyo), 4))
        assertEquals(date, logicalDate(date.atTime(4, 0).atZone(tokyo), 4))
        assertEquals(date, logicalDate(date.atTime(4, 1).atZone(tokyo), 4))
    }

    @Test
    fun day003_twentyThreeOClockBoundaryChangesAtExactlyTwentyThree() {
        val date = LocalDate.of(2026, 8, 11)

        assertEquals(date.minusDays(1), logicalDate(date.atTime(22, 59).atZone(tokyo), 23))
        assertEquals(date, logicalDate(date.atTime(23, 0).atZone(tokyo), 23))
        assertEquals(date, logicalDate(date.atTime(23, 1).atZone(tokyo), 23))
    }

    @Test
    fun day006_dueTimeBeforeBoundaryFallsOnNextCalendarDay() {
        val date = LocalDate.of(2026, 8, 11)

        assertEquals(
            ZonedDateTime.of(2026, 8, 12, 3, 0, 0, 0, tokyo),
            deadlineAt(date, endHour = 4, dueMinutes = 3 * 60, zoneId = tokyo),
        )
    }

    @Test
    fun day007_dueTimeAtOrAfterBoundaryStaysOnLogicalDate() {
        val date = LocalDate.of(2026, 8, 11)

        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 4, 0, 0, 0, tokyo),
            deadlineAt(date, endHour = 4, dueMinutes = 4 * 60, zoneId = tokyo),
        )
        assertEquals(
            ZonedDateTime.of(2026, 8, 11, 5, 0, 0, 0, tokyo),
            deadlineAt(date, endHour = 4, dueMinutes = 5 * 60, zoneId = tokyo),
        )
    }

    @Test
    fun day008_missingDueTimeUsesLogicalDayEnd() {
        val date = LocalDate.of(2026, 8, 11)
        val scheduled = todo(date, date, RecurrenceRule.once())

        assertEquals(null, scheduled.dueMinutes)
        assertEquals(
            ZonedDateTime.of(2026, 8, 12, 4, 0, 0, 0, tokyo),
            deadlineAt(date, endHour = 4, dueMinutes = scheduled.dueMinutes, zoneId = tokyo),
        )
    }

    @Test
    fun day011_clockZoneAndDstChangesRecalculateLogicalDateAndDeadline() {
        val instant = Instant.parse("2026-08-11T03:00:00Z")
        val losAngeles = ZoneId.of("America/Los_Angeles")

        assertEquals(
            LocalDate.of(2026, 8, 11),
            logicalDate(instant.atZone(tokyo), endHour = 4),
        )
        assertEquals(
            LocalDate.of(2026, 8, 10),
            logicalDate(instant.atZone(losAngeles), endHour = 4),
        )
        assertEquals(
            LocalDate.of(2026, 8, 10),
            logicalDate(ZonedDateTime.of(2026, 8, 11, 3, 59, 0, 0, tokyo), endHour = 4),
        )
        assertEquals(
            LocalDate.of(2026, 8, 11),
            logicalDate(ZonedDateTime.of(2026, 8, 11, 4, 0, 0, 0, tokyo), endHour = 4),
        )

        val newYork = ZoneId.of("America/New_York")
        val missingBoundary = logicalDayStart(LocalDate.of(2026, 3, 8), endHour = 2, newYork)
        assertEquals(3, missingBoundary.hour)
        assertEquals(ZoneOffset.of("-04:00"), missingBoundary.offset)
        val repeatedBoundary = logicalDayStart(LocalDate.of(2026, 11, 1), endHour = 1, newYork)
        assertEquals(ZoneOffset.of("-04:00"), repeatedBoundary.offset)
        assertEquals(
            ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, newYork),
            deadlineAt(LocalDate.of(2026, 3, 7), endHour = 2, dueMinutes = null, newYork),
        )
    }

    @Test
    fun day013_monthYearAndLeapDayBoundariesRemainContinuous() {
        assertEquals(
            LocalDate.of(2025, 12, 31),
            logicalDate(ZonedDateTime.of(2026, 1, 1, 3, 59, 0, 0, tokyo), 4),
        )
        assertEquals(
            ZonedDateTime.of(2024, 3, 1, 3, 0, 0, 0, tokyo),
            deadlineAt(LocalDate.of(2024, 2, 29), 4, 3 * 60, tokyo),
        )
        assertEquals(
            ZonedDateTime.of(2026, 2, 1, 3, 0, 0, 0, tokyo),
            deadlineAt(LocalDate.of(2026, 1, 31), 4, 3 * 60, tokyo),
        )
    }

    @Test
    fun rpt001_onceOccursOnlyOnItsExecutionDate() {
        val date = LocalDate.of(2026, 8, 11)
        val scheduled = todo(date, null, RecurrenceRule.once())

        assertEquals(listOf(date), scheduled.occurrencesIn(date.minusDays(2), date.plusDays(2)))
    }

    @Test
    fun rpt002_dailyOccursOnEveryActiveDate() {
        val start = LocalDate.of(2026, 8, 10)
        val end = start.plusDays(4)

        assertEquals(
            (0L..4L).map(start::plusDays),
            todo(start, end, RecurrenceRule.daily()).occurrencesIn(start.minusDays(1), end.plusDays(1)),
        )
    }

    @Test
    fun rpt003_weekdaysExcludeSaturdayAndSunday() {
        val monday = LocalDate.of(2026, 8, 10)
        val sunday = monday.plusDays(6)

        assertEquals(
            (0L..4L).map(monday::plusDays),
            todo(monday, sunday, RecurrenceRule(RecurrenceType.WEEKDAYS))
                .occurrencesIn(monday, sunday),
        )
    }

    @Test
    fun rpt004_weekdaysExcludeEveryHolidayKindProvidedByHolidayData() {
        val nationalHoliday = LocalDate.of(2026, 2, 11)
        val substituteHoliday = LocalDate.of(2026, 5, 6)
        val citizensHoliday = LocalDate.of(2015, 9, 22)

        listOf(nationalHoliday, substituteHoliday, citizensHoliday).forEach { holiday ->
            val scheduled = todo(
                holiday.minusDays(3),
                holiday.plusDays(3),
                RecurrenceRule(RecurrenceType.WEEKDAYS),
            )
            assertFalse(scheduled.occursOn(holiday, holidays = setOf(holiday)))
        }
    }

    @Test
    fun rpt007_selectedWeekdayIsNotExcludedOnlyBecauseItIsAHoliday() {
        val monday = LocalDate.of(2026, 8, 10)
        val scheduled = todo(
            monday,
            monday,
            RecurrenceRule(
                type = RecurrenceType.SELECTED_WEEKDAYS,
                selectedWeekdays = setOf(DayOfWeek.MONDAY),
            ),
        )

        assertTrue(scheduled.occursOn(monday, holidays = setOf(monday)))
    }

    @Test
    fun rpt008_selectedWeekdaysOccurAcrossMultipleWeeks() {
        val start = LocalDate.of(2026, 8, 10)
        val end = start.plusDays(13)
        val scheduled = todo(
            start,
            end,
            RecurrenceRule(
                type = RecurrenceType.SELECTED_WEEKDAYS,
                selectedWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            ),
        )

        assertEquals(
            listOf(start, start.plusDays(2), start.plusDays(7), start.plusDays(9)),
            scheduled.occurrencesIn(start, end),
        )
    }

    @Test
    fun rpt009_monthlyDayFifteenOccursInEveryMonth() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 3, 31)

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 15),
            ),
            todo(start, end, RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = 15))
                .occurrencesIn(start, end),
        )
    }

    @Test
    fun rpt010_missingMonthlyDaysClampToMonthEnd() {
        val start = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2025, 4, 30)
        val expected = mapOf(
            29 to listOf(31 to 29, 28 to 28, 31 to 29, 30 to 29),
            30 to listOf(31 to 30, 28 to 28, 31 to 30, 30 to 30),
            31 to listOf(31 to 31, 28 to 28, 31 to 31, 30 to 30),
        )

        expected.forEach { (monthlyDay, monthLengthsAndDays) ->
            val actual = todo(
                start,
                end,
                RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = monthlyDay),
            ).occurrencesIn(start, end)
            assertEquals(
                monthLengthsAndDays.mapIndexed { index, (_, day) -> LocalDate.of(2025, index + 1, day) },
                actual,
            )
        }
    }

    @Test
    fun rpt011_monthEndUsesActualLastDayIncludingLeapFebruary() {
        val rule = RecurrenceRule(RecurrenceType.MONTH_END)

        assertEquals(
            listOf(
                LocalDate.of(2023, 2, 28),
                LocalDate.of(2023, 3, 31),
                LocalDate.of(2023, 4, 30),
            ),
            todo(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 4, 30), rule)
                .occurrencesIn(LocalDate.of(2023, 2, 1), LocalDate.of(2023, 4, 30)),
        )
        assertTrue(
            todo(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29), rule)
                .occursOn(LocalDate.of(2024, 2, 29)),
        )
    }

    @Test
    fun rpt012_everyNDaysIsAlwaysAnchoredToStartDate() {
        val start = LocalDate.of(2026, 8, 10)
        val scheduled = todo(
            start,
            start.plusDays(9),
            RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3),
        )

        assertEquals(
            listOf(start, start.plusDays(3), start.plusDays(6), start.plusDays(9)),
            scheduled.occurrencesIn(start.minusDays(10), start.plusDays(20)),
        )
    }

    @Test
    fun rpt013_fixedScheduleDoesNotAcceptCompletionTimeAsAnAnchor() {
        val start = LocalDate.of(2026, 8, 10)
        val scheduled = todo(
            start,
            start.plusDays(12),
            RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3),
        )

        val expected = listOf(start, start.plusDays(3), start.plusDays(6), start.plusDays(9), start.plusDays(12))
        assertEquals(expected, scheduled.occurrencesIn(start, start.plusDays(12)))
        assertEquals(expected.drop(1), scheduled.nextOccurrences(start.plusDays(1), limit = 4))
    }

    @Test
    fun rpt014_allRulesStayInsideInclusiveActivePeriod() {
        val start = LocalDate.of(2026, 1, 5)
        val end = LocalDate.of(2026, 1, 11)
        val rules = listOf(
            RecurrenceRule.once(),
            RecurrenceRule.daily(),
            RecurrenceRule(RecurrenceType.WEEKDAYS),
            RecurrenceRule(RecurrenceType.SELECTED_WEEKDAYS, selectedWeekdays = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = 5),
            RecurrenceRule(
                RecurrenceType.MONTHLY_NTH_WEEKDAYS,
                monthlyNthWeekdays = setOf(MonthlyNthWeekday(1, DayOfWeek.MONDAY)),
            ),
            RecurrenceRule(RecurrenceType.MONTH_END),
            RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 2),
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 1),
            RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 1),
        )

        rules.forEach { rule ->
            val occurrences = todo(start, end, rule).occurrencesIn(start.minusDays(7), end.plusDays(7))
            assertTrue(occurrences.all { it in start..end })
        }
        assertEquals(start, todo(start, end, RecurrenceRule.once()).occurrencesIn(start, end).single())
        assertEquals(end, todo(start, end, RecurrenceRule.daily()).occurrencesIn(start, end).last())
    }

    @Test
    fun rpt015_unboundedRulesReturnOnlyTheRequestedFiniteCount() {
        val start = LocalDate.of(2026, 1, 5)
        val rules = listOf(
            RecurrenceRule.daily(),
            RecurrenceRule(RecurrenceType.WEEKDAYS),
            RecurrenceRule(RecurrenceType.SELECTED_WEEKDAYS, selectedWeekdays = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = 15),
            RecurrenceRule(
                RecurrenceType.MONTHLY_NTH_WEEKDAYS,
                monthlyNthWeekdays = setOf(MonthlyNthWeekday(1, DayOfWeek.MONDAY)),
            ),
            RecurrenceRule(RecurrenceType.MONTH_END),
            RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3),
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 1),
            RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 1),
        )

        rules.forEach { rule ->
            val occurrences = todo(start, null, rule).nextOccurrences(start, limit = 3)
            assertEquals(3, occurrences.size)
            assertTrue(occurrences.zipWithNext().all { (first, second) -> first < second })
        }
    }

    @Test
    fun rpt016_weeklyCountUsesConfiguredWeekStart() {
        val start = LocalDate.of(2026, 8, 12)
        val scheduled = todo(
            start,
            null,
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 7),
        )

        assertEquals(
            RecurrencePeriod(start, LocalDate.of(2026, 8, 16), requiredCount = 5),
            scheduled.recurrencePeriod(LocalDate.of(2026, 8, 13), DayOfWeek.MONDAY),
        )
        assertEquals(
            RecurrencePeriod(start, LocalDate.of(2026, 8, 15), requiredCount = 4),
            scheduled.recurrencePeriod(LocalDate.of(2026, 8, 13), DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun rpt017_weeklyCountClampsFirstAndLastPartialPeriods() {
        val start = LocalDate.of(2026, 8, 12)
        val end = LocalDate.of(2026, 8, 20)
        val scheduled = todo(
            start,
            end,
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 7),
        )

        assertEquals(
            RecurrencePeriod(start, LocalDate.of(2026, 8, 16), requiredCount = 5),
            scheduled.recurrencePeriod(LocalDate.of(2026, 8, 13), DayOfWeek.MONDAY),
        )
        assertEquals(
            RecurrencePeriod(LocalDate.of(2026, 8, 17), end, requiredCount = 4),
            scheduled.recurrencePeriod(LocalDate.of(2026, 8, 18), DayOfWeek.MONDAY),
        )
    }

    @Test
    fun rpt018_monthlyCountClampsFirstAndLastPartialMonths() {
        val start = LocalDate.of(2026, 1, 10)
        val end = LocalDate.of(2026, 2, 15)
        val scheduled = todo(
            start,
            end,
            RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 31),
        )

        assertEquals(
            RecurrencePeriod(start, LocalDate.of(2026, 1, 31), requiredCount = 22),
            scheduled.recurrencePeriod(LocalDate.of(2026, 1, 20), DayOfWeek.MONDAY),
        )
        assertEquals(
            RecurrencePeriod(LocalDate.of(2026, 2, 1), end, requiredCount = 15),
            scheduled.recurrencePeriod(LocalDate.of(2026, 2, 10), DayOfWeek.MONDAY),
        )
    }

    @Test
    fun rpt027_invalidCountAndIntervalRangesAreRejected() {
        assertFalse(RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 0).isValid())
        assertFalse(RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 1000).isValid())
        assertFalse(RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 0).isValid())
        assertFalse(RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 8).isValid())
        assertFalse(RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 1, periodWeeks = 0).isValid())
        assertFalse(RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 0).isValid())
        assertFalse(RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 32).isValid())
        assertTrue(RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 999).isValid())
        assertTrue(RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 7).isValid())
        assertTrue(RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 31).isValid())
    }

    @Test
    fun rpt028_multipleNthWeekdaysSkipMissingFifthWithoutDuplicates() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 4, 30)
        val scheduled = todo(
            start,
            end,
            RecurrenceRule(
                RecurrenceType.MONTHLY_NTH_WEEKDAYS,
                monthlyNthWeekdays = setOf(
                    MonthlyNthWeekday(1, DayOfWeek.MONDAY),
                    MonthlyNthWeekday(3, DayOfWeek.FRIDAY),
                    MonthlyNthWeekday(5, DayOfWeek.MONDAY),
                ),
            ),
        )

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
    }

    @Test
    fun rpt029_multiWeekPeriodsRemainAdjacentWhenWeekStartOrStartDateChanges() {
        val start = LocalDate.of(2026, 8, 12)
        val scheduled = todo(
            start,
            null,
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 1, periodWeeks = 2),
        )

        val mondayFirst = requireNotNull(scheduled.recurrencePeriod(start, DayOfWeek.MONDAY))
        val mondaySecond = requireNotNull(
            scheduled.recurrencePeriod(mondayFirst.endDate.plusDays(1), DayOfWeek.MONDAY),
        )
        assertEquals(mondayFirst.endDate.plusDays(1), mondaySecond.startDate)
        assertEquals(LocalDate.of(2026, 8, 23), mondayFirst.endDate)

        val sundayFirst = requireNotNull(scheduled.recurrencePeriod(start, DayOfWeek.SUNDAY))
        val sundaySecond = requireNotNull(
            scheduled.recurrencePeriod(sundayFirst.endDate.plusDays(1), DayOfWeek.SUNDAY),
        )
        assertEquals(sundayFirst.endDate.plusDays(1), sundaySecond.startDate)
        assertEquals(LocalDate.of(2026, 8, 22), sundayFirst.endDate)

        val mondayStart = scheduled.copy(startDate = LocalDate.of(2026, 8, 17))
        assertEquals(
            RecurrencePeriod(
                LocalDate.of(2026, 8, 17),
                LocalDate.of(2026, 8, 30),
                requiredCount = 1,
            ),
            mondayStart.recurrencePeriod(LocalDate.of(2026, 8, 24), DayOfWeek.MONDAY),
        )
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
