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
}
