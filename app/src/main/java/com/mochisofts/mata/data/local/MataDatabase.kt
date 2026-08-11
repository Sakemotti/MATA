package com.mochisofts.mata.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        TodoEntity::class,
        TodoExecutionEntity::class,
        TodoNotificationEntity::class,
        ScheduledNotificationEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class MataDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun todoDao(): TodoDao
    abstract fun todoExecutionDao(): TodoExecutionDao
    abstract fun todoNotificationDao(): TodoNotificationDao
    abstract fun scheduledNotificationDao(): ScheduledNotificationDao
}
