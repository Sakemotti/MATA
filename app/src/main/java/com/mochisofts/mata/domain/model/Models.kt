package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceType(val code: String) {
    ONCE("none"),
    DAILY("daily"),
    WEEKDAYS("weekdays"),
    SELECTED_WEEKDAYS("selected_weekdays"),
    MONTHLY_DAY("monthly_day"),
    MONTH_END("month_end"),
    EVERY_N_DAYS("every_n_days"),
    WEEKLY_COUNT("weekly_count"),
    MONTHLY_COUNT("monthly_count");

    val isCountBased: Boolean
        get() = this == WEEKLY_COUNT || this == MONTHLY_COUNT

    companion object {
        fun fromStoredValue(value: String): RecurrenceType =
            entries.firstOrNull { it.code == value }
                ?: runCatching { valueOf(value) }.getOrDefault(ONCE)
    }
}

enum class TodoState(val code: String) {
    PENDING("pending"),
    COMPLETED("completed"),
    SKIPPED("skipped"),
    MISSED("missed");

    companion object {
        fun fromStoredValue(value: String): TodoState =
            entries.firstOrNull { it.code == value }
                ?: runCatching { valueOf(value) }.getOrDefault(PENDING)
    }
}

data class RecurrenceRule(
    val type: RecurrenceType,
    val selectedWeekdays: Set<DayOfWeek> = emptySet(),
    val monthlyDay: Int? = null,
    val intervalDays: Int? = null,
    val requiredCount: Int? = null,
) {
    fun isValid(): Boolean = when (type) {
        RecurrenceType.SELECTED_WEEKDAYS -> selectedWeekdays.isNotEmpty()
        RecurrenceType.MONTHLY_DAY -> monthlyDay in 1..31
        RecurrenceType.EVERY_N_DAYS -> intervalDays in 1..999
        RecurrenceType.WEEKLY_COUNT -> requiredCount in 1..7
        RecurrenceType.MONTHLY_COUNT -> requiredCount in 1..31
        else -> true
    }

    companion object {
        fun once() = RecurrenceRule(RecurrenceType.ONCE)
        fun daily() = RecurrenceRule(RecurrenceType.DAILY)
    }
}

data class Category(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val iconName: String,
    val endHour: Int,
    val sortOrder: Int,
)

data class Todo(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val recurrenceRule: RecurrenceRule,
    val dueMinutes: Int?,
    val definitionRevision: Int,
    val archivedAt: Long?,
    val createdAt: Long,
    val notifications: List<TodoNotification> = emptyList(),
) {
    val recurrenceType: RecurrenceType
        get() = recurrenceRule.type
}

data class RecurrencePeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val requiredCount: Int,
)

data class RecurrenceProgress(
    val period: RecurrencePeriod,
    val completedCount: Int,
) {
    val remainingCount: Int
        get() = (period.requiredCount - completedCount).coerceAtLeast(0)

    val isAchieved: Boolean
        get() = completedCount >= period.requiredCount
}

data class TodoOccurrence(
    val todo: Todo,
    val category: Category?,
    val logicalDate: LocalDate,
    val state: TodoState,
    val progress: RecurrenceProgress? = null,
)
