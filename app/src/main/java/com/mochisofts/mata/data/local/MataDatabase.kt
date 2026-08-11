package com.mochisofts.mata.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TodoEntity::class,
        TodoExecutionEntity::class,
        PeriodResultEntity::class,
        TodoRuntimeStateEntity::class,
        TodoNotificationEntity::class,
        ScheduledNotificationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MataDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun todoDao(): TodoDao
    abstract fun todoExecutionDao(): TodoExecutionDao
    abstract fun periodResultDao(): PeriodResultDao
    abstract fun todoRuntimeStateDao(): TodoRuntimeStateDao
    abstract fun todoNotificationDao(): TodoNotificationDao
    abstract fun scheduledNotificationDao(): ScheduledNotificationDao
}
