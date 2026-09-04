package com.mochisofts.mata.data.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.app.notification.NotificationPresenter
import com.mochisofts.mata.core.notification.AlarmGateway
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.data.repository.TestHolidayRepository
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** One Room-to-alarm-gateway integration test per notification release test-spec ID. */
@RunWith(AndroidJUnit4::class)
class NotificationSchedulerTestSpecCoverageTest {
    private lateinit var database: MataDatabase
    private lateinit var scheduler: AndroidNotificationScheduler
    private lateinit var gateway: RecordingAlarmGateway
    private lateinit var settings: NotificationTestSettingsRepository
    private lateinit var holidays: TestHolidayRepository
    private lateinit var stateProvider: MutableNotificationSystemStateProvider
    private lateinit var clock: MutableNotificationClock
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gateway = RecordingAlarmGateway()
        settings = NotificationTestSettingsRepository()
        holidays = TestHolidayRepository()
        stateProvider = MutableNotificationSystemStateProvider()
        clock = MutableNotificationClock(
            Instant.parse("2026-08-10T15:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        scheduler = newScheduler()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun ntf002_multipleNotificationsAreRegisteredOnceInChronologicalOrder() = runBlocking {
        insertTodo()
        insertNotification("before", NotificationRelation.BEFORE, 30, NotificationUnit.MINUTE, 0)
        insertNotification("at", NotificationRelation.AT, 0, NotificationUnit.MINUTE, 1)
        insertNotification("after", NotificationRelation.AFTER, 1, NotificationUnit.HOUR, 2)

        scheduler.reconcileTodo(TODO_ID)

        val records = database.scheduledNotificationDao().findForTodo(TODO_ID).sortedBy { it.triggerAt }
        assertEquals(listOf("before", "at", "after"), records.map { it.notificationSettingId })
        assertEquals(3, records.map { it.candidateKey }.distinct().size)
        assertEquals(records.map { it.triggerAt }, gateway.scheduled.map { it.triggerAt })
    }

    @Test
    fun ntf006_permissionDenialKeepsSettingsAndSuppressesAlarmRegistration() = runBlocking {
        insertTodo()
        insertNotification()
        stateProvider.canPostNotifications = false

        scheduler.reconcileTodo(TODO_ID)

        assertEquals(1, database.todoNotificationDao().findForTodo(TODO_ID).size)
        val record = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        assertEquals(AndroidNotificationScheduler.STATE_SUPPRESSED, record.state)
        assertEquals(AndroidNotificationScheduler.FAILURE_PERMISSION, record.failureCode)
        assertTrue(gateway.scheduled.isEmpty())
    }

    @Test
    fun ntf007_permissionGrantRegistersOnlyFutureCandidates() = runBlocking {
        insertTodo(rule = RecurrenceRule.daily())
        insertNotification("past-today", NotificationRelation.BEFORE, 13, NotificationUnit.HOUR, 0)
        insertNotification("future-today", NotificationRelation.AT, 0, NotificationUnit.MINUTE, 1)
        stateProvider.canPostNotifications = false
        scheduler.reconcileTodo(TODO_ID)
        stateProvider.canPostNotifications = true

        scheduler.reconcileTodo(TODO_ID)

        val now = clock.millis()
        assertEquals(2, database.todoNotificationDao().findForTodo(TODO_ID).size)
        assertTrue(database.scheduledNotificationDao().findForTodo(TODO_ID).all { it.triggerAt > now })
        assertTrue(gateway.scheduled.all { it.triggerAt > now })
    }

    @Test
    fun ntf009_exactPermissionReplacesInexactWithoutDuplicates() = runBlocking {
        insertTodo()
        insertNotification()
        stateProvider.canScheduleExactAlarms = false
        scheduler.reconcileTodo(TODO_ID)
        val inexact = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        gateway.clearEvents()
        stateProvider.canScheduleExactAlarms = true

        scheduler.reconcileTodo(TODO_ID)

        val exact = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        assertEquals(AndroidNotificationScheduler.MODE_INEXACT, inexact.schedulingMode)
        assertEquals(AndroidNotificationScheduler.MODE_EXACT, exact.schedulingMode)
        assertEquals(inexact.candidateKey, exact.candidateKey)
        assertEquals(inexact.requestCode, exact.requestCode)
        assertEquals(1, gateway.cancelled.size)
        assertEquals(listOf(true), gateway.scheduled.map { it.exact })
    }

    @Test
    fun ntf010_platformRebuildRestoresFutureAndDropsPastRegistrations() = runBlocking {
        insertTodo(rule = RecurrenceRule.daily())
        insertNotification()
        scheduler.reconcileTodo(TODO_ID)
        val future = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        database.scheduledNotificationDao().upsert(
            scheduledRecord(
                key = "$TODO_ID|obsolete|2026-08-10|1",
                notificationId = "obsolete",
                triggerAt = clock.millis() - Duration.ofHours(1).toMillis(),
                requestCode = future.requestCode + 1,
            ),
        )
        gateway.clearEvents()

        scheduler.rebuildAll()

        val records = database.scheduledNotificationDao().findForTodo(TODO_ID)
        assertEquals(listOf(future.candidateKey), records.map { it.candidateKey })
        assertEquals(1, gateway.scheduled.size)
        assertTrue(gateway.scheduled.single().triggerAt > clock.millis())
        assertTrue(gateway.cancelled.any { it.key == future.candidateKey })
        assertTrue(gateway.cancelled.any { it.key.contains("obsolete") })
    }

    @Test
    fun ntf011_clockAndZoneChangeMoveFutureAlarmWithoutPastOrDuplicateRegistration() = runBlocking {
        insertTodo(rule = RecurrenceRule.daily())
        insertNotification()
        scheduler.reconcileTodo(TODO_ID)
        val tokyoRecord = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        gateway.clearEvents()
        clock.zoneId = ZoneId.of("UTC")

        scheduler.rebuildAll()

        val utcRecord = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        assertNotEquals(tokyoRecord.triggerAt, utcRecord.triggerAt)
        assertTrue(utcRecord.triggerAt > clock.millis())
        assertEquals(1, gateway.scheduled.size)
        assertEquals(1, database.scheduledNotificationDao().findForTodo(TODO_ID).size)
    }

    @Test
    fun ntf012_scheduleInputsRecomputeFutureAlarmsWithoutChangingHistory() = runBlocking {
        insertTodo(dueMinutes = 3 * 60)
        insertNotification()
        insertExecution()
        scheduler.reconcileTodo(TODO_ID)
        val originalTrigger = database.scheduledNotificationDao().findForTodo(TODO_ID).single().triggerAt
        settings.dayEndHour.value = 4

        scheduler.reconcileAll()

        val afterDayEndChange = database.scheduledNotificationDao().findForTodo(TODO_ID).single().triggerAt
        assertEquals(Duration.ofDays(1).toMillis(), afterDayEndChange - originalTrigger)
        val originalTodo = requireNotNull(database.todoDao().findById(TODO_ID))
        database.todoDao().upsert(originalTodo.copy(dueMinutes = 5 * 60, definitionRevision = 2, updatedAt = 2))
        scheduler.reconcileTodo(TODO_ID)
        val afterTodoChange = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        assertEquals(2, afterTodoChange.definitionRevision)
        assertNotEquals(afterDayEndChange, afterTodoChange.triggerAt)

        insertTodo(
            id = WEEKLY_TODO_ID,
            startDate = LocalDate.of(2026, 8, 9),
            rule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 1),
        )
        insertNotification(todoId = WEEKLY_TODO_ID, id = "weekly-notification")
        insertExecution(todoId = WEEKLY_TODO_ID, date = LocalDate.of(2026, 8, 10), id = "weekly-history")
        settings.weekStart.value = DayOfWeek.MONDAY
        scheduler.reconcileTodo(WEEKLY_TODO_ID)
        val mondayFirst = database.scheduledNotificationDao().findForTodo(WEEKLY_TODO_ID).single().logicalDate
        settings.weekStart.value = DayOfWeek.SUNDAY
        scheduler.reconcileAll()
        val sundayFirst = database.scheduledNotificationDao().findForTodo(WEEKLY_TODO_ID).single().logicalDate
        assertEquals("2026-08-17", mondayFirst)
        assertEquals("2026-08-16", sundayFirst)

        insertTodo(
            id = HOLIDAY_TODO_ID,
            rule = RecurrenceRule(RecurrenceType.WEEKDAYS),
        )
        insertNotification(todoId = HOLIDAY_TODO_ID, id = "holiday-notification")
        scheduler.reconcileTodo(HOLIDAY_TODO_ID)
        val beforeHoliday = database.scheduledNotificationDao().findForTodo(HOLIDAY_TODO_ID).single().logicalDate
        holidays.snapshot.value = HolidaySnapshot(
            namesByDate = mapOf(LocalDate.of(2026, 8, 11) to "test holiday"),
        )
        scheduler.reconcileAll()
        val afterHoliday = database.scheduledNotificationDao().findForTodo(HOLIDAY_TODO_ID).single().logicalDate
        assertEquals("2026-08-11", beforeHoliday)
        assertEquals("2026-08-12", afterHoliday)
        assertEquals("history", database.todoExecutionDao().findById("history")?.id)
    }

    @Test
    fun ntf013_invalidRelationshipKeepsSettingWithSuppressionReasonAndCount() = runBlocking {
        insertTodo(dueMinutes = 23 * 60 + 30)
        insertNotification("invalid", NotificationRelation.AFTER, 1, NotificationUnit.HOUR, 0)

        scheduler.reconcileTodo(TODO_ID)

        assertEquals(1, database.todoNotificationDao().findForTodo(TODO_ID).size)
        val record = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        assertEquals(AndroidNotificationScheduler.STATE_SUPPRESSED, record.state)
        assertEquals(
            "${AndroidNotificationScheduler.FAILURE_INVALID_PREFIX}after_day_end",
            record.failureCode,
        )
        assertEquals(1, database.scheduledNotificationDao().countByState(AndroidNotificationScheduler.STATE_SUPPRESSED))
        assertTrue(gateway.scheduled.isEmpty())
    }

    @Test
    fun ntf014_archiveAndDeleteCancelEveryRemainingAlarm() = runBlocking {
        insertTodo()
        insertNotification()
        insertTodo(id = DELETED_TODO_ID)
        insertNotification(todoId = DELETED_TODO_ID, id = "delete-notification")
        scheduler.reconcileAll()
        val archived = requireNotNull(database.todoDao().findById(TODO_ID))
        database.todoDao().upsert(archived.copy(archivedAt = clock.millis(), updatedAt = 2))
        database.todoDao().deleteById(DELETED_TODO_ID)
        gateway.clearEvents()

        scheduler.rebuildAll()

        assertTrue(database.scheduledNotificationDao().findForTodo(TODO_ID).isEmpty())
        assertTrue(database.scheduledNotificationDao().findForTodo(DELETED_TODO_ID).isEmpty())
        assertEquals(2, gateway.cancelled.size)
    }

    @Test
    fun ntf015_repeatedReconciliationIsIdempotentByCandidateKey() = runBlocking {
        insertTodo()
        insertNotification()
        scheduler.reconcileTodo(TODO_ID)
        val first = database.scheduledNotificationDao().findForTodo(TODO_ID).single()
        gateway.clearEvents()

        repeat(3) { scheduler.reconcileTodo(TODO_ID) }

        assertEquals(listOf(first), database.scheduledNotificationDao().findForTodo(TODO_ID))
        assertTrue(gateway.scheduled.isEmpty())
        assertTrue(gateway.cancelled.isEmpty())
    }

    private fun newScheduler() = AndroidNotificationScheduler(
        context = context,
        todoDao = database.todoDao(),
        executionDao = database.todoExecutionDao(),
        notificationDao = database.todoNotificationDao(),
        scheduledDao = database.scheduledNotificationDao(),
        settingsRepository = settings,
        holidayRepository = holidays,
        alarmGateway = gateway,
        presenter = NotificationPresenter(context),
        systemStateProvider = stateProvider,
        clock = clock,
    )

    private suspend fun insertTodo(
        id: String = TODO_ID,
        startDate: LocalDate = LocalDate.of(2026, 8, 11),
        rule: RecurrenceRule = RecurrenceRule.once(),
        dueMinutes: Int? = 12 * 60,
    ) {
        val encoded = RecurrenceRuleJson.encode(rule)
        database.todoDao().upsert(
            TodoEntity(
                id = id,
                title = "title",
                description = "",
                categoryId = null,
                startDate = startDate.toString(),
                endDate = null,
                recurrenceType = encoded.typeCode,
                repeatParamsVersion = encoded.paramsVersion,
                repeatParamsJson = encoded.paramsJson,
                dueMinutes = dueMinutes,
                definitionRevision = 1,
                createdAt = 1,
                updatedAt = 1,
                archivedAt = null,
            ),
        )
    }

    private suspend fun insertNotification(
        id: String = "notification",
        relation: NotificationRelation = NotificationRelation.AT,
        amount: Int = 0,
        unit: NotificationUnit = NotificationUnit.MINUTE,
        sortOrder: Int = 0,
        todoId: String = TODO_ID,
    ) {
        database.todoNotificationDao().upsertAll(
            listOf(
                TodoNotificationEntity(
                    id = id,
                    todoId = todoId,
                    relation = relation.code,
                    amount = amount,
                    unit = unit.code,
                    sortOrder = sortOrder,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
    }

    private suspend fun insertExecution(
        todoId: String = TODO_ID,
        date: LocalDate = LocalDate.of(2026, 8, 10),
        id: String = "history",
    ) {
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = id,
                operationId = "$id-operation",
                todoId = todoId,
                logicalDate = date.toString(),
                status = "completed",
                actedAt = 1,
                finalizedAt = 1,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = "{}",
            ),
        )
    }

    private fun scheduledRecord(
        key: String,
        notificationId: String,
        triggerAt: Long,
        requestCode: Int,
    ) = ScheduledNotificationEntity(
        candidateKey = key,
        todoId = TODO_ID,
        notificationSettingId = notificationId,
        logicalDate = "2026-08-10",
        definitionRevision = 1,
        triggerAt = triggerAt,
        requestCode = requestCode,
        schedulingMode = AndroidNotificationScheduler.MODE_EXACT,
        state = AndroidNotificationScheduler.STATE_SCHEDULED,
        failureCode = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private companion object {
        const val TODO_ID = "todo"
        const val WEEKLY_TODO_ID = "weekly-todo"
        const val HOLIDAY_TODO_ID = "holiday-todo"
        const val DELETED_TODO_ID = "deleted-todo"
    }
}

private data class ScheduledAlarmCall(
    val key: String,
    val requestCode: Int,
    val triggerAt: Long,
    val exact: Boolean,
)

private data class CancelledAlarmCall(val key: String, val requestCode: Int)

private class RecordingAlarmGateway : AlarmGateway {
    val scheduled = mutableListOf<ScheduledAlarmCall>()
    val cancelled = mutableListOf<CancelledAlarmCall>()

    override fun schedule(candidateKey: String, requestCode: Int, triggerAtMillis: Long, exact: Boolean) {
        scheduled += ScheduledAlarmCall(candidateKey, requestCode, triggerAtMillis, exact)
    }

    override fun cancel(candidateKey: String, requestCode: Int) {
        cancelled += CancelledAlarmCall(candidateKey, requestCode)
    }

    fun clearEvents() {
        scheduled.clear()
        cancelled.clear()
    }
}

private class MutableNotificationSystemStateProvider : NotificationSystemStateProvider {
    var canPostNotifications = true
    var canScheduleExactAlarms = true

    override fun current() = NotificationSystemState(
        canPostNotifications = canPostNotifications,
        runtimePermissionRelevant = true,
        runtimePermissionGranted = canPostNotifications,
        exactAlarmRelevant = true,
        canScheduleExactAlarms = canScheduleExactAlarms,
    )
}

private class MutableNotificationClock(
    private var currentInstant: Instant,
    var zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableNotificationClock(currentInstant, zone)
    override fun instant(): Instant = currentInstant
}

private class NotificationTestSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val dayEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override val archiveSortOrder = MutableStateFlow(ArchiveSortOrder.NEWEST)

    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
    override suspend fun setArchiveSortOrder(value: ArchiveSortOrder) { archiveSortOrder.value = value }
}
