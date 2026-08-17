package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

fun logicalDate(now: ZonedDateTime, endHour: Int): LocalDate {
    require(endHour in 0..23)
    return if (now.hour < endHour) now.toLocalDate().minusDays(1) else now.toLocalDate()
}

fun logicalDayStart(date: LocalDate, endHour: Int, zoneId: ZoneId): ZonedDateTime {
    require(endHour in 0..23)
    return date.atTime(endHour, 0).atZone(zoneId)
}

fun logicalDayEnd(date: LocalDate, endHour: Int, zoneId: ZoneId): ZonedDateTime =
    logicalDayStart(date.plusDays(1), endHour, zoneId)

fun deadlineAt(
    logicalDate: LocalDate,
    endHour: Int,
    dueMinutes: Int?,
    zoneId: ZoneId,
): ZonedDateTime {
    require(endHour in 0..23)
    require(dueMinutes == null || dueMinutes in 0..1439)
    if (dueMinutes == null) return logicalDayEnd(logicalDate, endHour, zoneId)

    val dueTime = LocalTime.of(dueMinutes / 60, dueMinutes % 60)
    val dueDate = if (endHour != 0 && dueMinutes < endHour * 60) {
        logicalDate.plusDays(1)
    } else {
        logicalDate
    }
    return dueDate.atTime(dueTime).atZone(zoneId)
}

fun Todo.occursOn(date: LocalDate, holidays: Set<LocalDate> = emptySet()): Boolean {
    if (archivedAt != null || date.isBefore(startDate) || endDate?.let(date::isAfter) == true) {
        return false
    }
    return when (recurrenceRule.type) {
        RecurrenceType.ONCE -> date == startDate
        RecurrenceType.DAILY -> true
        RecurrenceType.WEEKDAYS -> date.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY && date !in holidays
        RecurrenceType.SELECTED_WEEKDAYS -> date.dayOfWeek in recurrenceRule.selectedWeekdays
        RecurrenceType.MONTHLY_DAY -> {
            val targetDay = recurrenceRule.monthlyDay?.coerceAtMost(date.lengthOfMonth()) ?: return false
            date.dayOfMonth == targetDay
        }
        RecurrenceType.MONTHLY_NTH_WEEKDAYS -> MonthlyNthWeekday(
            ordinal = (date.dayOfMonth - 1) / 7 + 1,
            dayOfWeek = date.dayOfWeek,
        ) in recurrenceRule.monthlyNthWeekdays
        RecurrenceType.MONTH_END -> date.dayOfMonth == date.lengthOfMonth()
        RecurrenceType.EVERY_N_DAYS -> {
            val interval = recurrenceRule.intervalDays ?: return false
            ChronoUnit.DAYS.between(startDate, date) % interval == 0L
        }
        RecurrenceType.WEEKLY_COUNT,
        RecurrenceType.MONTHLY_COUNT,
        -> true
    }
}

fun Todo.occurrencesIn(
    startInclusive: LocalDate,
    endInclusive: LocalDate,
    holidays: Set<LocalDate> = emptySet(),
): List<LocalDate> {
    if (endInclusive.isBefore(startInclusive)) return emptyList()
    return generateSequence(startInclusive) { current ->
        current.plusDays(1).takeUnless { it.isAfter(endInclusive) }
    }.filter { occursOn(it, holidays) }.toList()
}

fun Todo.nextOccurrences(
    fromInclusive: LocalDate,
    limit: Int,
    holidays: Set<LocalDate> = emptySet(),
): List<LocalDate> {
    require(limit >= 0)
    if (limit == 0) return emptyList()
    val searchStart = maxOf(fromInclusive, startDate)
    val searchEnd = endDate ?: searchStart.plusYears(10)
    return generateSequence(searchStart) { current ->
        current.plusDays(1).takeUnless { it.isAfter(searchEnd) }
    }.filter { occursOn(it, holidays) }.take(limit).toList()
}

fun Todo.recurrencePeriod(date: LocalDate, weekStart: DayOfWeek): RecurrencePeriod? {
    if (!recurrenceRule.type.isCountBased || date.isBefore(startDate) || endDate?.let(date::isAfter) == true) {
        return null
    }
    val naturalStart: LocalDate
    val naturalEnd: LocalDate
    when (recurrenceRule.type) {
        RecurrenceType.WEEKLY_COUNT -> {
            val daysFromStart = (date.dayOfWeek.value - weekStart.value + 7) % 7
            naturalStart = date.minusDays(daysFromStart.toLong())
            naturalEnd = naturalStart.plusDays(6)
        }
        RecurrenceType.MONTHLY_COUNT -> {
            naturalStart = date.with(TemporalAdjusters.firstDayOfMonth())
            naturalEnd = date.with(TemporalAdjusters.lastDayOfMonth())
        }
        else -> return null
    }
    val effectiveStart = maxOf(naturalStart, startDate)
    val effectiveEnd = minOf(naturalEnd, endDate ?: naturalEnd)
    if (effectiveEnd.isBefore(effectiveStart)) return null
    val activeDays = ChronoUnit.DAYS.between(effectiveStart, effectiveEnd).toInt() + 1
    val requiredCount = minOf(recurrenceRule.requiredCount ?: return null, activeDays)
    return RecurrencePeriod(effectiveStart, effectiveEnd, requiredCount)
}

private operator fun ClosedRange<DayOfWeek>.contains(value: DayOfWeek): Boolean =
    value.value in start.value..endInclusive.value
