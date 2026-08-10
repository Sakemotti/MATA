package com.mochisofts.mata.domain.model

import java.time.LocalDate
import java.time.ZonedDateTime

enum class RecurrenceType {
    ONCE,
    DAILY,
}

enum class TodoState {
    PENDING,
    COMPLETED,
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
    val recurrenceType: RecurrenceType,
    val dueMinutes: Int?,
    val createdAt: Long,
)

data class TodoOccurrence(
    val todo: Todo,
    val category: Category?,
    val logicalDate: LocalDate,
    val state: TodoState,
)

fun logicalDate(now: ZonedDateTime, endHour: Int): LocalDate =
    if (now.hour < endHour) now.toLocalDate().minusDays(1) else now.toLocalDate()

fun Todo.occursOn(date: LocalDate): Boolean = when (recurrenceType) {
    RecurrenceType.ONCE -> startDate == date
    RecurrenceType.DAILY -> !date.isBefore(startDate)
}

