package com.mochisofts.mata.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MataDatabaseMigrationTest {
    private val databaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MataDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesTodoAndAddsRecurrenceFields() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO todos (
                    id, title, description, categoryId, startDate, recurrenceType,
                    dueMinutes, createdAt, updatedAt, archivedAt
                ) VALUES (
                    'todo-id', 'title', '', NULL, '2026-08-10', 'DAILY',
                    NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            MIGRATION_1_2,
        ).use { database ->
            database.query(
                """
                SELECT recurrenceType, endDate, repeatParamsVersion,
                       repeatParamsJson, definitionRevision
                FROM todos WHERE id = 'todo-id'
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("daily", cursor.getString(0))
                assertEquals(null, cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals("{}", cursor.getString(3))
                assertEquals(1, cursor.getInt(4))
            }
        }
    }

    @Test
    fun migrate2To3_addsNotificationTablesWithoutChangingTodos() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO todos (
                    id, title, description, categoryId, startDate, endDate, recurrenceType,
                    repeatParamsVersion, repeatParamsJson, dueMinutes, definitionRevision,
                    createdAt, updatedAt, archivedAt
                ) VALUES (
                    'todo-id', 'title', '', NULL, '2026-08-10', '2026-08-10', 'none',
                    1, '{}', 720, 1, 1, 1, NULL
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            MIGRATION_2_3,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO todo_notifications (
                    id, todoId, relation, amount, unit, sortOrder, createdAt, updatedAt
                ) VALUES ('notification-id', 'todo-id', 'before', 30, 'minute', 0, 1, 1)
                """.trimIndent(),
            )
            database.query("SELECT COUNT(*) FROM todo_notifications").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query("SELECT title FROM todos WHERE id = 'todo-id'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("title", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrate3To4_preservesExecutionsAndAddsHistoryTables() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO todos (
                    id, title, description, categoryId, startDate, endDate, recurrenceType,
                    repeatParamsVersion, repeatParamsJson, dueMinutes, definitionRevision,
                    createdAt, updatedAt, archivedAt
                ) VALUES (
                    'todo-id', 'title', '', NULL, '2026-08-10', NULL, 'daily',
                    1, '{}', NULL, 3, 1, 1, NULL
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO todo_executions (todoId, logicalDate, state, performedAt)
                VALUES ('todo-id', '2026-08-10', 'completed', 1234)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            MIGRATION_3_4,
        ).use { database ->
            database.query(
                """
                SELECT status, actedAt, finalizedAt, definitionRevision, snapshotVersion
                FROM todo_executions WHERE todoId = 'todo-id'
                """.trimIndent(),
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("completed", cursor.getString(0))
                assertEquals(1234, cursor.getLong(1))
                assertEquals(1234, cursor.getLong(2))
                assertEquals(3, cursor.getInt(3))
                assertEquals(1, cursor.getInt(4))
            }
            database.query("SELECT COUNT(*) FROM period_results").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM todo_runtime_states").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate4To5_addsHolidayCacheTables() {
        helper.createDatabase(databaseName, 4).close()

        helper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            MIGRATION_4_5,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO holidays (
                    date, year, name, sourceId, sourceDataHash, fetchedAt
                ) VALUES ('2026-01-01', 2026, '元日', 'holidays_jp_v1', 'hash', 1)
                """.trimIndent(),
            )
            database.query("SELECT name FROM holidays WHERE date = '2026-01-01'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("元日", cursor.getString(0))
            }
            database.query("SELECT COUNT(*) FROM holiday_fetch_states").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM holiday_update_states").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun migrate5To6_addsPersistedWidgetInstanceState() {
        helper.createDatabase(databaseName, 5).close()

        helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            MIGRATION_5_6,
        ).use { database ->
            database.execSQL(
                """
                INSERT INTO widget_instance_states (
                    appWidgetId, snapshotVersion, snapshotJson, lastSuccessAt, loadState,
                    errorCode, lastFailureAt, undoOperationId, undoTodoTitle, undoExpiresAt,
                    nextRefreshAt, updatedAt
                ) VALUES (42, 1, '{}', 100, 'ready', NULL, NULL, NULL, NULL, NULL, 200, 100)
                """.trimIndent(),
            )
            database.query(
                "SELECT snapshotVersion, loadState, nextRefreshAt FROM widget_instance_states WHERE appWidgetId = 42",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
                assertEquals("ready", cursor.getString(1))
                assertEquals(200, cursor.getLong(2))
            }
        }
    }
}
