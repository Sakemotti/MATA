package com.mochisofts.mata.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.time.DayOfWeek

/** A holiday takes precedence over Saturday; weekdays retain the caller's normal text color. */
@Composable
fun mataDateTextColor(
    dayOfWeek: DayOfWeek,
    isHoliday: Boolean = false,
    defaultColor: Color = MaterialTheme.colorScheme.onSurface,
    backgroundColor: Color? = null,
): Color {
    val dateColor = when {
        isHoliday || dayOfWeek == DayOfWeek.SUNDAY -> MaterialTheme.mataColors.calendarSundayHoliday
        dayOfWeek == DayOfWeek.SATURDAY -> MaterialTheme.mataColors.calendarSaturday
        else -> return defaultColor
    }
    return backgroundColor?.let { mataDateColorOnBackground(dateColor, it) } ?: dateColor
}

/** Dynamic palettes can use stronger selection fills; preserve the date hue and its legibility. */
internal fun mataDateColorOnBackground(color: Color, background: Color): Color {
    fun contrast(candidate: Color): Float {
        val foregroundLuminance = candidate.luminance()
        val backgroundLuminance = background.luminance()
        return (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
    }
    if (contrast(color) >= 4.5f) return color
    val target = if (contrast(Color.Black) > contrast(Color.White)) Color.Black else Color.White
    var lower = 0f
    var upper = 1f
    repeat(12) {
        val fraction = (lower + upper) / 2f
        if (contrast(lerp(color, target, fraction)) >= 4.5f) upper = fraction else lower = fraction
    }
    return lerp(color, target, upper)
}
