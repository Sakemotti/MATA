package com.mochisofts.mata.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "categories",
    indices = [Index(value = ["sortOrder"], unique = true)],
)
data class CategoryEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val normalizedName: String,
    val colorIndex: Int,
    val iconName: String,
    val endHour: Int,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "todos",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("startDate"),
        Index("endDate"),
        Index("archivedAt"),
    ],
)
data class TodoEntity(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val description: String,
    val categoryId: String?,
    val startDate: String,
    val endDate: String?,
    val recurrenceType: String,
    val repeatParamsVersion: Int,
    val repeatParamsJson: String,
    val dueMinutes: Int?,
    val definitionRevision: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val archivedAt: Long?,
)

@Entity(
    tableName = "todo_executions",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["operationId"], unique = true),
        Index("todoId"),
        Index("logicalDate"),
        Index("status"),
        Index(value = ["todoId", "logicalDate"], unique = true),
    ],
)
data class TodoExecutionEntity(
    @androidx.room.PrimaryKey val id: String,
    val operationId: String,
    val todoId: String,
    val logicalDate: String,
    val status: String,
    val actedAt: Long?,
    val finalizedAt: Long,
    val definitionRevision: Int,
    val snapshotVersion: Int,
    val snapshotJson: String,
)

@Entity(
    tableName = "period_results",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("todoId"),
        Index("periodStart"),
        Index("periodEnd"),
        Index("displayDate"),
        Index(value = ["todoId", "periodStart", "periodEnd"], unique = true),
    ],
)
data class PeriodResultEntity(
    @androidx.room.PrimaryKey val id: String,
    val todoId: String,
    val periodType: String,
    val periodStart: String,
    val periodEnd: String,
    val requiredCount: Int,
    val completedCount: Int,
    val achieved: Boolean,
    val displayDate: String,
    val finalizedAt: Long,
    val definitionRevision: Int,
    val snapshotVersion: Int,
    val snapshotJson: String,
)

@Entity(
    tableName = "todo_runtime_states",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TodoRuntimeStateEntity(
    @androidx.room.PrimaryKey val todoId: String,
    val lastFinalizedLogicalDate: String?,
    val lastFinalizedWeeklyPeriodEnd: String?,
    val lastFinalizedMonthlyPeriodEnd: String?,
    val appliedDefinitionRevision: Int,
    val reconciliationCursorDate: String?,
    val updatedAt: Long,
)

@Entity(
    tableName = "todo_notifications",
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("todoId"),
        Index(value = ["todoId", "sortOrder"], unique = true),
    ],
)
data class TodoNotificationEntity(
    @androidx.room.PrimaryKey val id: String,
    val todoId: String,
    val relation: String,
    val amount: Int,
    val unit: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "scheduled_notifications",
    indices = [
        Index("todoId"),
        Index("notificationSettingId"),
        Index("triggerAt"),
        Index("state"),
        Index(value = ["requestCode"], unique = true),
    ],
)
data class ScheduledNotificationEntity(
    @androidx.room.PrimaryKey val candidateKey: String,
    val todoId: String,
    val notificationSettingId: String,
    val logicalDate: String,
    val definitionRevision: Int,
    val triggerAt: Long,
    val requestCode: Int,
    val schedulingMode: String,
    val state: String,
    val failureCode: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "holidays",
    indices = [Index("year")],
)
data class HolidayEntity(
    @androidx.room.PrimaryKey val date: String,
    val year: Int,
    val name: String,
    val sourceId: String,
    val sourceDataHash: String,
    val fetchedAt: Long,
)

@Entity(tableName = "holiday_fetch_states")
data class HolidayFetchStateEntity(
    @androidx.room.PrimaryKey val year: Int,
    val sourceId: String,
    val availability: String,
    val dataHash: String?,
    val fetchedAt: Long?,
    val lastCheckedAt: Long?,
    val lastAttemptedAt: Long?,
    val lastAttemptResult: String,
    val etag: String?,
    val lastModified: String?,
    val lastErrorCode: String?,
)

@Entity(tableName = "holiday_update_states")
data class HolidayUpdateStateEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val generation: Long,
    val changedYears: String,
    val changedDates: String,
    val renamedDates: String,
    val domainProcessed: Boolean,
    val notificationProcessed: Boolean,
    val widgetProcessed: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "widget_instance_states")
data class WidgetInstanceStateEntity(
    @androidx.room.PrimaryKey val appWidgetId: Int,
    val snapshotVersion: Int,
    val snapshotJson: String?,
    val lastSuccessAt: Long?,
    val loadState: String,
    val errorCode: String?,
    val lastFailureAt: Long?,
    val undoOperationId: String?,
    val undoTodoTitle: String?,
    val undoExpiresAt: Long?,
    val nextRefreshAt: Long?,
    val updatedAt: Long,
)
