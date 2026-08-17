package com.mochisofts.mata.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject

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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todo_executions_new (
                id TEXT NOT NULL PRIMARY KEY,
                operationId TEXT NOT NULL,
                todoId TEXT NOT NULL,
                logicalDate TEXT NOT NULL,
                status TEXT NOT NULL,
                actedAt INTEGER,
                finalizedAt INTEGER NOT NULL,
                definitionRevision INTEGER NOT NULL,
                snapshotVersion INTEGER NOT NULL,
                snapshotJson TEXT NOT NULL,
                FOREIGN KEY(todoId) REFERENCES todos(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO todo_executions_new (
                id, operationId, todoId, logicalDate, status, actedAt, finalizedAt,
                definitionRevision, snapshotVersion, snapshotJson
            )
            SELECT
                lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' ||
                    lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' ||
                    lower(hex(randomblob(6))),
                lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-' ||
                    lower(hex(randomblob(2))) || '-' || lower(hex(randomblob(2))) || '-' ||
                    lower(hex(randomblob(6))),
                executions.todoId,
                executions.logicalDate,
                executions.state,
                executions.performedAt,
                executions.performedAt,
                todos.definitionRevision,
                1,
                '{"version":1,"migratedFromSchema":3}'
            FROM todo_executions AS executions
            INNER JOIN todos ON todos.id = executions.todoId
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE todo_executions")
        db.execSQL("ALTER TABLE todo_executions_new RENAME TO todo_executions")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_todo_executions_operationId " +
                "ON todo_executions(operationId)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_executions_todoId ON todo_executions(todoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_executions_logicalDate ON todo_executions(logicalDate)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_executions_status ON todo_executions(status)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_todo_executions_todoId_logicalDate " +
                "ON todo_executions(todoId, logicalDate)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS period_results (
                id TEXT NOT NULL PRIMARY KEY,
                todoId TEXT NOT NULL,
                periodType TEXT NOT NULL,
                periodStart TEXT NOT NULL,
                periodEnd TEXT NOT NULL,
                requiredCount INTEGER NOT NULL,
                completedCount INTEGER NOT NULL,
                achieved INTEGER NOT NULL,
                displayDate TEXT NOT NULL,
                finalizedAt INTEGER NOT NULL,
                definitionRevision INTEGER NOT NULL,
                snapshotVersion INTEGER NOT NULL,
                snapshotJson TEXT NOT NULL,
                FOREIGN KEY(todoId) REFERENCES todos(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_period_results_todoId ON period_results(todoId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_period_results_periodStart ON period_results(periodStart)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_period_results_periodEnd ON period_results(periodEnd)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_period_results_displayDate ON period_results(displayDate)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_period_results_todoId_periodStart_periodEnd " +
                "ON period_results(todoId, periodStart, periodEnd)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS todo_runtime_states (
                todoId TEXT NOT NULL PRIMARY KEY,
                lastFinalizedLogicalDate TEXT,
                lastFinalizedWeeklyPeriodEnd TEXT,
                lastFinalizedMonthlyPeriodEnd TEXT,
                appliedDefinitionRevision INTEGER NOT NULL,
                reconciliationCursorDate TEXT,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(todoId) REFERENCES todos(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS holidays (
                date TEXT NOT NULL PRIMARY KEY,
                year INTEGER NOT NULL,
                name TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                sourceDataHash TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_holidays_year ON holidays(year)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS holiday_fetch_states (
                year INTEGER NOT NULL PRIMARY KEY,
                sourceId TEXT NOT NULL,
                availability TEXT NOT NULL,
                dataHash TEXT,
                fetchedAt INTEGER,
                lastCheckedAt INTEGER,
                lastAttemptedAt INTEGER,
                lastAttemptResult TEXT NOT NULL,
                etag TEXT,
                lastModified TEXT,
                lastErrorCode TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS holiday_update_states (
                id INTEGER NOT NULL PRIMARY KEY,
                generation INTEGER NOT NULL,
                changedYears TEXT NOT NULL,
                changedDates TEXT NOT NULL,
                renamedDates TEXT NOT NULL,
                domainProcessed INTEGER NOT NULL,
                notificationProcessed INTEGER NOT NULL,
                widgetProcessed INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS widget_instance_states (
                appWidgetId INTEGER NOT NULL PRIMARY KEY,
                snapshotVersion INTEGER NOT NULL,
                snapshotJson TEXT,
                lastSuccessAt INTEGER,
                loadState TEXT NOT NULL,
                errorCode TEXT,
                lastFailureAt INTEGER,
                undoOperationId TEXT,
                undoTodoTitle TEXT,
                undoExpiresAt INTEGER,
                nextRefreshAt INTEGER,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE categories SET updatedAt = createdAt")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_categories_normalizedName " +
                "ON categories(normalizedName)",
        )
        repairLegacyExecutionSnapshots(db)
    }
}

private fun repairLegacyExecutionSnapshots(db: SupportSQLiteDatabase) {
    db.query(
        """
        SELECT executions.id, executions.todoId, executions.logicalDate,
            executions.definitionRevision,
            todos.title, todos.description, todos.startDate, todos.endDate,
            todos.recurrenceType, todos.repeatParamsVersion, todos.repeatParamsJson,
            todos.dueMinutes, todos.categoryId, todos.createdAt,
            categories.name, categories.colorIndex, categories.iconName,
            categories.sortOrder, COALESCE(categories.endHour, 0)
        FROM todo_executions AS executions
        INNER JOIN todos ON todos.id = executions.todoId
        LEFT JOIN categories ON categories.id = todos.categoryId
        WHERE executions.snapshotJson = '{"version":1,"migratedFromSchema":3}'
        """.trimIndent(),
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val todoId = cursor.getString(1)
            val notifications = JSONArray()
            db.query(
                "SELECT relation, amount, unit FROM todo_notifications " +
                    "WHERE todoId = ? ORDER BY sortOrder ASC",
                arrayOf(todoId),
            ).use { notificationCursor ->
                while (notificationCursor.moveToNext()) {
                    notifications.put(
                        JSONObject()
                            .put("relation", notificationCursor.getString(0))
                            .put("amount", notificationCursor.getInt(1))
                            .put("unit", notificationCursor.getString(2)),
                    )
                }
            }
            val snapshot = JSONObject()
                .put("version", 1)
                .put("todoId", todoId)
                .put("definitionRevision", cursor.getInt(3))
                .put("title", cursor.getString(4))
                .put("description", cursor.getString(5))
                .put("startDate", cursor.getString(6))
                .put("endDate", cursor.stringOrJsonNull(7))
                .put("recurrenceType", cursor.getString(8))
                .put("repeatParamsVersion", cursor.getInt(9))
                .put("repeatParamsJson", cursor.getString(10))
                .put("dueMinutes", cursor.intOrJsonNull(11))
                .put("notifications", notifications)
                .put("categoryId", cursor.stringOrJsonNull(12))
                .put("categoryName", cursor.stringOrJsonNull(14))
                .put("categoryColorIndex", cursor.intOrJsonNull(15))
                .put("categoryIconName", cursor.stringOrJsonNull(16))
                .put("categorySortOrder", cursor.intOrJsonNull(17))
                .put("endHour", cursor.getInt(18))
                .put("weekStart", 1)
                .put("createdAt", cursor.getLong(13))
                .put("logicalDate", cursor.getString(2))
                .put("periodStart", JSONObject.NULL)
                .put("periodEnd", JSONObject.NULL)
            db.execSQL(
                "UPDATE todo_executions SET snapshotJson = ? WHERE id = ?",
                arrayOf(snapshot.toString(), cursor.getString(0)),
            )
        }
    }
}

private fun android.database.Cursor.stringOrJsonNull(index: Int): Any =
    if (isNull(index)) JSONObject.NULL else getString(index)

private fun android.database.Cursor.intOrJsonNull(index: Int): Any =
    if (isNull(index)) JSONObject.NULL else getInt(index)
