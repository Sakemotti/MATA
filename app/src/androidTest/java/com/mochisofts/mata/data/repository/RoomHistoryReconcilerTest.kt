package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHistoryReconcilerTest {
    private lateinit var database: MataDatabase
    private lateinit var reconciler: RoomHistoryReconciler
    private lateinit var holidayRepository: TestHolidayRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        holidayRepository = TestHolidayRepository()
        reconciler = RoomHistoryReconciler(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            periodResultDao = database.periodResultDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            notificationDao = database.todoNotificationDao(),
            settingsRepository = FakeSettingsRepository(),
            holidayRepository = holidayRepository,
            clock = Clock.fixed(
                Instant.parse("2026-08-11T03:00:00Z"),
                ZoneId.of("Asia/Tokyo"),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailyTodo_createsMissedRecordsOnlyForFinishedLogicalDays() = runBlocking {
        database.todoDao().upsert(todo("daily", RecurrenceRule.daily(), "2026-08-08"))

        val result = reconciler.reconcile()

        val executions = database.todoExecutionDao().findForTodo("daily")
        assertEquals(
            listOf("2026-08-08", "2026-08-09", "2026-08-10"),
            executions.map { it.logicalDate }.sorted(),
        )
        assertEquals(setOf("missed"), executions.map { it.status }.toSet())
        assertEquals(3, result.generatedRecords)
        assertFalse(result.hasMore)
    }

    @Test
    fun weeklyCountTodo_finalizesOnePeriodUsingCompletedExecutions() = runBlocking {
        database.todoDao().upsert(
            todo(
                id = "weekly",
                rule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 3),
                startDate = "2026-08-03",
            ),
        )
        listOf("2026-08-03", "2026-08-05").forEachIndexed { index, date ->
            database.todoExecutionDao().insert(
                TodoExecutionEntity(
                    id = "execution-$index",
                    operationId = "operation-$index",
                    todoId = "weekly",
                    logicalDate = date,
                    status = "completed",
                    actedAt = index.toLong(),
                    finalizedAt = index.toLong(),
                    definitionRevision = 1,
                    snapshotVersion = 1,
                    snapshotJson = "{}",
                ),
            )
        }

        reconciler.reconcile()

        val result = database.periodResultDao().findForTodo("weekly").single()
        assertEquals("2026-08-03", result.periodStart)
        assertEquals("2026-08-09", result.periodEnd)
        assertEquals(3, result.requiredCount)
        assertEquals(2, result.completedCount)
        assertFalse(result.achieved)
    }

    @Test
    fun weekdayTodo_doesNotCreateMissedRecordForHoliday() = runBlocking {
        holidayRepository.snapshot.value = HolidaySnapshot(
            namesByDate = mapOf(LocalDate.of(2026, 8, 10) to "祝日"),
        )
        database.todoDao().upsert(
            todo("weekdays", RecurrenceRule(RecurrenceType.WEEKDAYS), "2026-08-07"),
        )

        reconciler.reconcile()

        assertEquals(
            listOf("2026-08-07"),
            database.todoExecutionDao().findForTodo("weekdays").map { it.logicalDate },
        )
    }

    private fun todo(id: String, rule: RecurrenceRule, startDate: String): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(rule)
        return TodoEntity(
            id = id,
            title = id,
            description = "",
            categoryId = null,
            startDate = startDate,
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = null,
            definitionRevision = 1,
            createdAt = 1,
            updatedAt = 1,
            archivedAt = null,
        )
    }
}

private class FakeSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val uncategorizedEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)

    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setUncategorizedEndHour(value: Int) { uncategorizedEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
}
