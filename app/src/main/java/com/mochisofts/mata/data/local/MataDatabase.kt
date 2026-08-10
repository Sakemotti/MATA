package com.mochisofts.mata.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CategoryEntity::class, TodoEntity::class, TodoExecutionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MataDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun todoDao(): TodoDao
    abstract fun todoExecutionDao(): TodoExecutionDao
}

