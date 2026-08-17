package com.mochisofts.mata.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetDisplayModel(
    val snapshotVersion: Int = CURRENT_VERSION,
    val generatedAt: Long,
    val calendarDate: String,
    val totalCount: Int,
    val groups: List<WidgetCategoryGroup>,
    val holidayDataProvisional: Boolean,
    val nextRefreshAt: Long,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class WidgetCategoryGroup(
    val categoryId: String?,
    val categoryName: String,
    val colorIndex: Int,
    val iconName: String,
    val sortOrder: Int,
    val logicalDate: String,
    val logicalDateLabel: String?,
    val items: List<WidgetTodoItem>,
)

@Serializable
data class WidgetTodoItem(
    val todoId: String,
    val definitionRevision: Int,
    val title: String,
    val logicalDate: String,
    val deadlineAt: Long,
    val deadlineLabel: String,
    val overdue: Boolean,
    val completedCount: Int? = null,
    val requiredCount: Int? = null,
    val canComplete: Boolean = true,
)
