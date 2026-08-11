package com.mochisofts.mata.data.repository

import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class HistorySnapshotV1(
    val version: Int = VERSION,
    val todoId: String,
    val definitionRevision: Int,
    val title: String,
    val description: String,
    val startDate: String,
    val endDate: String?,
    val recurrenceType: String,
    val repeatParamsVersion: Int,
    val repeatParamsJson: String,
    val dueMinutes: Int?,
    val notifications: List<HistoryNotificationSnapshot>,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColorIndex: Int?,
    val categoryIconName: String?,
    val endHour: Int,
    val weekStart: Int,
    val logicalDate: String?,
    val periodStart: String?,
    val periodEnd: String?,
) {
    companion object {
        const val VERSION = 1
    }
}

@Serializable
internal data class HistoryNotificationSnapshot(
    val relation: String,
    val amount: Int,
    val unit: String,
)

internal object HistorySnapshotJson {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(
        todo: TodoEntity,
        category: CategoryEntity?,
        notifications: List<TodoNotificationEntity>,
        endHour: Int,
        weekStart: DayOfWeek,
        logicalDate: LocalDate? = null,
        periodStart: LocalDate? = null,
        periodEnd: LocalDate? = null,
    ): String = json.encodeToString(
        HistorySnapshotV1(
            todoId = todo.id,
            definitionRevision = todo.definitionRevision,
            title = todo.title,
            description = todo.description,
            startDate = todo.startDate,
            endDate = todo.endDate,
            recurrenceType = todo.recurrenceType,
            repeatParamsVersion = todo.repeatParamsVersion,
            repeatParamsJson = todo.repeatParamsJson,
            dueMinutes = todo.dueMinutes,
            notifications = notifications.map { notification ->
                HistoryNotificationSnapshot(
                    relation = notification.relation,
                    amount = notification.amount,
                    unit = notification.unit,
                )
            },
            categoryId = category?.id,
            categoryName = category?.name,
            categoryColorIndex = category?.colorIndex,
            categoryIconName = category?.iconName,
            endHour = endHour,
            weekStart = weekStart.value,
            logicalDate = logicalDate?.toString(),
            periodStart = periodStart?.toString(),
            periodEnd = periodEnd?.toString(),
        ),
    )
}
