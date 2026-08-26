package com.mochisofts.mata.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

enum class HistoryDayState {
    IN_PROGRESS,
    COMPLETED,
    UNACHIEVED,
}

data class HistoryTodoSnapshot(
    val todoId: String,
    val definitionRevision: Int,
    val title: String,
    val description: String,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val recurrenceRule: RecurrenceRule,
    val dueMinutes: Int?,
    val notifications: List<TodoNotification>,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColorIndex: Int?,
    val categoryIconName: String?,
    val categorySortOrder: Int?,
    val endHour: Int,
    val weekStart: DayOfWeek,
    val createdAt: Long,
)

data class HistoryEntry(
    val id: String?,
    val todoId: String,
    val logicalDate: LocalDate,
    val state: TodoState,
    val actedAt: Long?,
    val finalizedAt: Long?,
    val snapshot: HistoryTodoSnapshot,
    val canUndoAction: Boolean,
)

data class PeriodHistoryEntry(
    val id: String,
    val todoId: String,
    val periodType: RecurrenceType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val requiredCount: Int,
    val completedCount: Int,
    val achieved: Boolean,
    val displayDate: LocalDate,
    val finalizedAt: Long,
    val snapshot: HistoryTodoSnapshot,
)

data class HistoryDaySummary(
    val date: LocalDate,
    val completedCount: Int,
    val plannedCount: Int,
    val state: HistoryDayState?,
    val hasAchievedPeriod: Boolean,
    val hasUnachievedPeriod: Boolean,
)

data class HistoryMonth(
    val summaries: Map<LocalDate, HistoryDaySummary>,
)

data class HistoryDay(
    val date: LocalDate,
    val summary: HistoryDaySummary,
    val entries: List<HistoryEntry>,
    val periodResults: List<PeriodHistoryEntry>,
)

data class HistoryActionUndoToken(
    val id: String,
    val operationId: String,
    val todoId: String,
    val logicalDate: LocalDate,
    val state: TodoState,
    val actedAt: Long,
    val finalizedAt: Long,
    val definitionRevision: Int,
    val snapshotVersion: Int,
    val snapshotJson: String,
)

fun summarizeHistoryDay(
    date: LocalDate,
    states: List<TodoState>,
    periodAchievements: List<Boolean>,
): HistoryDaySummary {
    val completed = states.count { it == TodoState.COMPLETED }
    val planned = states.size
    val state = when {
        states.any { it == TodoState.MISSED || it == TodoState.SKIPPED } ->
            HistoryDayState.UNACHIEVED
        states.any { it == TodoState.PENDING } -> HistoryDayState.IN_PROGRESS
        planned > 0 && completed == planned -> HistoryDayState.COMPLETED
        else -> null
    }
    return HistoryDaySummary(
        date = date,
        completedCount = completed,
        plannedCount = planned,
        state = state,
        hasAchievedPeriod = periodAchievements.any { it },
        hasUnachievedPeriod = periodAchievements.any { !it },
    )
}
