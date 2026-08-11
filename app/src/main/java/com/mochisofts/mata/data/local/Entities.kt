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
    primaryKeys = ["todoId", "logicalDate"],
    foreignKeys = [
        ForeignKey(
            entity = TodoEntity::class,
            parentColumns = ["id"],
            childColumns = ["todoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("todoId"), Index("logicalDate")],
)
data class TodoExecutionEntity(
    val todoId: String,
    val logicalDate: String,
    val state: String,
    val performedAt: Long,
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
