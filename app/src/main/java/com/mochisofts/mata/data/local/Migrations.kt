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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todo_notifications (
                id TEXT NOT NULL PRIMARY KEY,
                todoId TEXT NOT NULL,
                relation TEXT NOT NULL,
                amount INTEGER NOT NULL,
                unit TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(todoId) REFERENCES todos(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_notifications_todoId ON todo_notifications(todoId)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_todo_notifications_todoId_sortOrder
            ON todo_notifications(todoId, sortOrder)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scheduled_notifications (
                candidateKey TEXT NOT NULL PRIMARY KEY,
                todoId TEXT NOT NULL,
                notificationSettingId TEXT NOT NULL,
                logicalDate TEXT NOT NULL,
                definitionRevision INTEGER NOT NULL,
                triggerAt INTEGER NOT NULL,
                requestCode INTEGER NOT NULL,
                schedulingMode TEXT NOT NULL,
                state TEXT NOT NULL,
                failureCode TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_notifications_todoId ON scheduled_notifications(todoId)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_scheduled_notifications_notificationSettingId " +
                "ON scheduled_notifications(notificationSettingId)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_notifications_triggerAt ON scheduled_notifications(triggerAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scheduled_notifications_state ON scheduled_notifications(state)")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_scheduled_notifications_requestCode
            ON scheduled_notifications(requestCode)
            """.trimIndent(),
        )
    }
}
