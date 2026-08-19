package com.mochisofts.mata.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarGridDatesTest {
    @Test
    fun mondayStart_alwaysProducesSixWeeksFromExpectedColumn() {
        val dates = calendarGridDates(YearMonth.of(2026, 8), DayOfWeek.MONDAY)

        assertEquals(42, dates.size)
        assertEquals(LocalDate.of(2026, 7, 27), dates.first())
        assertEquals(LocalDate.of(2026, 9, 6), dates.last())
        assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
    }

    @Test
    fun sundayStart_reordersCalendarColumns() {
        val dates = calendarGridDates(YearMonth.of(2026, 8), DayOfWeek.SUNDAY)

        assertEquals(LocalDate.of(2026, 7, 26), dates.first())
        assertEquals(DayOfWeek.SUNDAY, dates.first().dayOfWeek)
        assertEquals(42, dates.size)
    }

    @Test
    fun minimumTwoPaneWidthKeepsBothPanesAtTheirMinimum() {
        val widths = calendarPaneWidths(736f)

        assertEquals(360f, widths.leftDp)
        assertEquals(352f, widths.rightDp)
    }

    @Test
    fun largerTwoPaneWidthDistributesRemainingSpaceToHistory() {
        val widths = calendarPaneWidths(1_104f)

        assertEquals(432f, widths.leftDp)
        assertEquals(648f, widths.rightDp)
    }
}
