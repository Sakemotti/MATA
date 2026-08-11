package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

enum class NotificationRelation(val code: String) {
    BEFORE("before"),
    AT("at"),
    AFTER("after");

    companion object {
        fun fromStoredValue(value: String): NotificationRelation =
            entries.firstOrNull { it.code == value } ?: AT
    }
}

enum class NotificationUnit(val code: String, val nominalMinutes: Int) {
    MINUTE("minute", 1),
    HOUR("hour", 60),
    DAY("day", 1_440);

    companion object {
        fun fromStoredValue(value: String): NotificationUnit =
            entries.firstOrNull { it.code == value } ?: MINUTE
    }
}

data class TodoNotification(
    val id: String,
    val relation: NotificationRelation,
    val amount: Int,
    val unit: NotificationUnit,
) {
    val normalizedMinutes: Int
        get() = amount * unit.nominalMinutes
}

data class NotificationCandidate(
    val notification: TodoNotification,
    val logicalDate: LocalDate,
    val deadlineAt: ZonedDateTime,
    val triggerAt: ZonedDateTime,
    val isDayBoundary: Boolean,
)

data class NotificationSystemState(
    val canPostNotifications: Boolean,
    val runtimePermissionRelevant: Boolean,
    val runtimePermissionGranted: Boolean,
    val exactAlarmRelevant: Boolean,
    val canScheduleExactAlarms: Boolean,
)

enum class NotificationValidationError {
    TOO_MANY,
    INVALID_AMOUNT,
    DUPLICATE,
    AFTER_REQUIRES_DEADLINE,
    AFTER_DAY_END,
}

fun validateNotifications(
    notifications: List<TodoNotification>,
    dueMinutes: Int?,
    endHour: Int,
): Set<NotificationValidationError> {
    require(endHour in 0..23)
    val errors = mutableSetOf<NotificationValidationError>()
    if (notifications.size > MAX_NOTIFICATIONS_PER_TODO) {
        errors += NotificationValidationError.TOO_MANY
    }
    if (notifications.any { notification ->
            when (notification.relation) {
                NotificationRelation.AT -> notification.amount != 0
                NotificationRelation.BEFORE,
                NotificationRelation.AFTER,
                -> notification.amount !in 1..MAX_NOTIFICATION_AMOUNT
            }
        }
    ) {
        errors += NotificationValidationError.INVALID_AMOUNT
    }
    if (notifications.groupBy { it.relation to it.normalizedMinutes }.any { it.value.size > 1 }) {
        errors += NotificationValidationError.DUPLICATE
    }

    val afterNotifications = notifications.filter { it.relation == NotificationRelation.AFTER }
    if (afterNotifications.isNotEmpty() && dueMinutes == null) {
        errors += NotificationValidationError.AFTER_REQUIRES_DEADLINE
    }
    if (dueMinutes != null) {
        val logicalStartMinutes = endHour * 60
        val deadlineOffset = if (dueMinutes >= logicalStartMinutes || endHour == 0) {
            dueMinutes - logicalStartMinutes
        } else {
            1_440 - logicalStartMinutes + dueMinutes
        }
        val remainingMinutes = 1_440 - deadlineOffset
        if (afterNotifications.any { it.normalizedMinutes >= remainingMinutes }) {
            errors += NotificationValidationError.AFTER_DAY_END
        }
    }
    return errors
}

fun notificationTriggerAt(
    deadline: ZonedDateTime,
    notification: TodoNotification,
): ZonedDateTime = when (notification.relation) {
    NotificationRelation.AT -> deadline
    NotificationRelation.BEFORE -> when (notification.unit) {
        NotificationUnit.DAY -> deadline.minusDays(notification.amount.toLong())
        NotificationUnit.HOUR -> deadline.minusHours(notification.amount.toLong())
        NotificationUnit.MINUTE -> deadline.minusMinutes(notification.amount.toLong())
    }
    NotificationRelation.AFTER -> when (notification.unit) {
        NotificationUnit.DAY -> deadline.plusDays(notification.amount.toLong())
        NotificationUnit.HOUR -> deadline.plusHours(notification.amount.toLong())
        NotificationUnit.MINUTE -> deadline.plusMinutes(notification.amount.toLong())
    }
}

fun nextNotificationCandidate(
    todo: Todo,
    notification: TodoNotification,
    endHour: Int,
    now: ZonedDateTime,
    weekStart: DayOfWeek,
    completedDates: Set<LocalDate> = emptySet(),
    actedDates: Set<LocalDate> = completedDates,
): NotificationCandidate? {
    if (todo.archivedAt != null || validateNotifications(listOf(notification), todo.dueMinutes, endHour).isNotEmpty()) {
        return null
    }

    var searchDate = maxOf(todo.startDate, logicalDate(now, endHour))
    repeat(MAX_CANDIDATE_SEARCH_STEPS) {
        val occurrenceDate = todo.nextOccurrenceOnOrAfter(searchDate) ?: return null
        if (todo.endDate?.let(occurrenceDate::isAfter) == true) return null
        searchDate = occurrenceDate.plusDays(1)
        if (occurrenceDate in actedDates) return@repeat

        val period = todo.recurrencePeriod(occurrenceDate, weekStart)
        if (period != null) {
            val completedCount = completedDates.count { it in period.startDate..period.endDate }
            if (completedCount >= period.requiredCount) {
                searchDate = period.endDate.plusDays(1)
                return@repeat
            }
        }

        val deadline = deadlineAt(occurrenceDate, endHour, todo.dueMinutes, now.zone)
        val trigger = notificationTriggerAt(deadline, notification)
        val dayEnd = logicalDayEnd(occurrenceDate, endHour, now.zone)
        val isBoundary = todo.dueMinutes == null && notification.relation == NotificationRelation.AT
        if (trigger.isAfter(now) && (trigger.isBefore(dayEnd) || isBoundary)) {
            return NotificationCandidate(
                notification = notification,
                logicalDate = occurrenceDate,
                deadlineAt = deadline,
                triggerAt = trigger,
                isDayBoundary = isBoundary,
            )
        }
    }
    return null
}

fun Todo.nextOccurrenceOnOrAfter(
    fromInclusive: LocalDate,
    holidays: Set<LocalDate> = emptySet(),
): LocalDate? {
    val from = maxOf(fromInclusive, startDate)
    val candidate = when (recurrenceRule.type) {
        RecurrenceType.ONCE -> startDate.takeUnless { it.isBefore(from) }
        RecurrenceType.DAILY,
        RecurrenceType.WEEKLY_COUNT,
        RecurrenceType.MONTHLY_COUNT,
        -> from
        RecurrenceType.WEEKDAYS -> generateSequence(from) { it.plusDays(1) }
            .take(370)
            .firstOrNull { it.dayOfWeek.value <= DayOfWeek.FRIDAY.value && it !in holidays }
        RecurrenceType.SELECTED_WEEKDAYS -> generateSequence(from) { it.plusDays(1) }
            .take(7)
            .firstOrNull { it.dayOfWeek in recurrenceRule.selectedWeekdays }
        RecurrenceType.MONTHLY_DAY -> {
            val requestedDay = recurrenceRule.monthlyDay ?: return null
            var month = YearMonth.from(from)
            var date = month.atDay(requestedDay.coerceAtMost(month.lengthOfMonth()))
            if (date.isBefore(from)) {
                month = month.plusMonths(1)
                date = month.atDay(requestedDay.coerceAtMost(month.lengthOfMonth()))
            }
            date
        }
        RecurrenceType.MONTH_END -> {
            var date = YearMonth.from(from).atEndOfMonth()
            if (date.isBefore(from)) date = YearMonth.from(from).plusMonths(1).atEndOfMonth()
            date
        }
        RecurrenceType.EVERY_N_DAYS -> {
            val interval = recurrenceRule.intervalDays ?: return null
            val days = ChronoUnit.DAYS.between(startDate, from).coerceAtLeast(0)
            val intervals = ceil(days.toDouble() / interval).toLong()
            startDate.plusDays(intervals * interval)
        }
    }
    return candidate?.takeIf { date -> endDate == null || !date.isAfter(endDate) }
}

const val MAX_NOTIFICATIONS_PER_TODO = 10
const val MAX_NOTIFICATION_AMOUNT = 999
private const val MAX_CANDIDATE_SEARCH_STEPS = 2_048
