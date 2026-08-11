package com.mochisofts.mata.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE todos ADD COLUMN endDate TEXT")
        db.execSQL("ALTER TABLE todos ADD COLUMN repeatParamsVersion INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE todos ADD COLUMN repeatParamsJson TEXT NOT NULL DEFAULT '{}'")
        db.execSQL("ALTER TABLE todos ADD COLUMN definitionRevision INTEGER NOT NULL DEFAULT 1")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todos_endDate ON todos(endDate)")
        db.execSQL("UPDATE todos SET recurrenceType = 'none' WHERE recurrenceType = 'ONCE'")
        db.execSQL("UPDATE todos SET recurrenceType = 'daily' WHERE recurrenceType = 'DAILY'")
    }
}
