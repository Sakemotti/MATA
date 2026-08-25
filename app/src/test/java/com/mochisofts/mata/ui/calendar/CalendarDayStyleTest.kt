package com.mochisofts.mata.ui.calendar

import androidx.compose.ui.graphics.Color
import com.mochisofts.mata.core.designsystem.MataDarkColors
import com.mochisofts.mata.core.designsystem.MataLightColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDayStyleTest {
    @Test
    fun selectedDay_usesOnPrimaryContainerInEveryFixedTheme() {
        listOf(MataLightColors, MataDarkColors).forEach { colors ->
            assertEquals(colors.onPrimaryContainer, calendarDayTextColor(true, colors))
        }
    }

    @Test
    fun unselectedDay_inheritsTheSurroundingContentColor() {
        assertEquals(Color.Unspecified, calendarDayTextColor(false, MataLightColors))
    }
}
