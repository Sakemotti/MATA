package com.mochisofts.mata.domain.model

import java.time.LocalDate

enum class ArchiveSortOrder(val storedValue: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    TITLE("title"),
    ;

    companion object {
        fun fromStoredValue(value: String?): ArchiveSortOrder =
            entries.firstOrNull { it.storedValue == value } ?: NEWEST
    }
}

data class ArchivedTodoItem(
    val todo: Todo,
    val category: Category?,
) {
    val archivedAt: Long
        get() = requireNotNull(todo.archivedAt)
}

data class ArchiveHistorySummary(
    val completedCount: Int,
    val missedCount: Int,
    val skippedCount: Int,
    val periodResultCount: Int,
) {
    val executionCount: Int
        get() = completedCount + missedCount + skippedCount

    val totalCount: Int
        get() = executionCount + periodResultCount
}

data class ArchiveActionPreview(
    val todoId: String,
    val title: String,
    val hasFutureOccurrence: Boolean,
    val notificationSettingCount: Int,
    val unavailableNotificationCount: Int,
    val historySummary: ArchiveHistorySummary,
)

sealed interface ArchivedHistoryItem {
    val stableId: String
    val historyDate: LocalDate
    val comparisonTime: Long

    data class Execution(val entry: HistoryEntry) : ArchivedHistoryItem {
        override val stableId: String = "execution:${entry.id}"
        override val historyDate: LocalDate = entry.logicalDate
        override val comparisonTime: Long = entry.actedAt ?: entry.finalizedAt ?: 0L
    }

    data class Period(val entry: PeriodHistoryEntry) : ArchivedHistoryItem {
        override val stableId: String = "period:${entry.id}"
        override val historyDate: LocalDate = entry.displayDate
        override val comparisonTime: Long = entry.finalizedAt
    }
}

val archivedHistoryComparator: Comparator<ArchivedHistoryItem> =
    compareByDescending<ArchivedHistoryItem> { it.historyDate }
        .thenByDescending { it.comparisonTime }
        .thenBy { if (it is ArchivedHistoryItem.Execution) 0 else 1 }
        .thenBy { it.stableId }
