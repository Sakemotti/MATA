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
