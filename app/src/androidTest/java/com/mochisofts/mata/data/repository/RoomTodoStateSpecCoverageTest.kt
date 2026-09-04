package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.widget.WidgetSourceData
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.data.widget.buildWidgetDisplayModel
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.nextOccurrenceOnOrAfter
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTodoStateSpecCoverageTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private lateinit var database: MataDatabase
    private lateinit var clock: MutableStateTestClock
    private lateinit var settings: StateTestSettingsRepository
    private lateinit var scheduler: StateTestNotificationScheduler
    private lateinit var widgetUpdater: WidgetUpdater
    private lateinit var todoRepository: RoomTodoRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableStateTestClock(LocalDate.of(2026, 8, 10), zone)
        settings = StateTestSettingsRepository()
        scheduler = StateTestNotificationScheduler(database)
        widgetUpdater = WidgetUpdater(context, DiagnosticLogger())
        todoRepository = RoomTodoRepository(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            notificationDao = database.todoNotificationDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            settingsRepository = settings,
            notificationScheduler = scheduler,
            widgetUpdater = widgetUpdater,
            holidayRepository = TestHolidayRepository(),
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sta001_completionCreatesOneSharedListHistoryAndWidgetState() = runBlocking {
        val todo = todoEntity("complete-shared-state")
        database.todoDao().upsert(todo)
        assertEquals(TodoState.PENDING, todoRepository.observeOccurrences(clock.date).first().single().state)
        assertEquals(1, widgetModel(todo, emptyList()).totalCount)

        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "complete-once").getOrThrow()

        val execution = database.todoExecutionDao().findForTodo(todo.id).single()
        assertEquals(TodoState.COMPLETED, TodoState.fromStoredValue(execution.status))
        val listOccurrence = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(TodoState.COMPLETED, listOccurrence.state)
        assertTrue(listOf(listOccurrence).filter { it.state == TodoState.PENDING }.isEmpty())
        val historyEntry = historyRepository().observeDay(clock.date).first().entries.single()
        assertEquals(TodoState.COMPLETED, historyEntry.state)
        assertEquals(0, widgetModel(todo, listOf(execution)).totalCount)
    }

    @Test
    fun sta004_skipHidesTodayCreatesHistoryAndLeavesNextOccurrenceUnchanged() = runBlocking {
        val todo = todoEntity("skip-shared-state")
        database.todoDao().upsert(todo)
        val domainBefore = requireNotNull(todoRepository.getTodo(todo.id))
        val expectedNext = domainBefore.nextOccurrenceOnOrAfter(clock.date.plusDays(1))

        todoRepository.setSkipped(todo.id, clock.date, true, operationId = "skip-once").getOrThrow()

        val occurrence = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals(TodoState.SKIPPED, occurrence.state)
        assertTrue(listOf(occurrence).filter { it.state != TodoState.SKIPPED }.isEmpty())
        assertEquals(
            TodoState.SKIPPED,
            historyRepository().observeDay(clock.date).first().entries.single().state,
        )
        val domainAfter = requireNotNull(todoRepository.getTodo(todo.id))
        assertEquals(expectedNext, domainAfter.nextOccurrenceOnOrAfter(clock.date.plusDays(1)))
        clock.setDate(clock.date.plusDays(1))
        assertEquals(TodoState.PENDING, todoRepository.observeOccurrences(clock.date).first().single().state)
    }

    @Test
    fun sta006_editUpdatesCurrentDefinitionWithoutRewritingPastSnapshot() = runBlocking {
        val todo = todoEntity("edited-state", title = "before edit", dueMinutes = 9 * 60)
        database.todoDao().upsert(todo)
        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "before-edit").getOrThrow()
        val historicalDate = clock.date
        val executionBefore = database.todoExecutionDao().findForTodo(todo.id).single()
        clock.setDate(historicalDate.plusDays(1))

        todoRepository.saveTodo(
            id = todo.id,
            title = "after edit",
            description = "updated description",
            categoryId = null,
            startDate = LocalDate.of(2026, 8, 10),
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = 18 * 60,
            notifications = emptyList(),
        ).getOrThrow()

        val current = todoRepository.observeOccurrences(clock.date).first().single()
        assertEquals("after edit", current.todo.title)
        assertEquals("updated description", current.todo.description)
        assertEquals(18 * 60, current.todo.dueMinutes)
        assertEquals(TodoState.PENDING, current.state)
        assertEquals(executionBefore, database.todoExecutionDao().findForTodo(todo.id).single())
        val historical = historyRepository().observeDay(historicalDate).first().entries.single()
        assertEquals("before edit", historical.snapshot.title)
        assertEquals(9 * 60, historical.snapshot.dueMinutes)
        assertEquals(TodoState.COMPLETED, historical.state)
    }

    @Test
    fun sta007_archivePendingTodoDropsCurrentOccurrenceAndStopsNotificationsWithoutHistory() = runBlocking {
        val todo = todoEntity("archive-pending")
        database.todoDao().upsert(todo)
        insertNotificationState(todo.id)
        assertEquals(1, todoRepository.observeOccurrences(clock.date).first().size)

        todoRepository.archiveTodo(todo.id).getOrThrow()

        val archived = requireNotNull(database.todoDao().findById(todo.id))
        assertEquals(clock.millis(), archived.archivedAt)
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        assertTrue(todoRepository.observeTodos().first().isEmpty())
        assertTrue(database.todoExecutionDao().findForTodo(todo.id).isEmpty())
        assertTrue(database.periodResultDao().findForTodo(todo.id).isEmpty())
        assertTrue(database.scheduledNotificationDao().findForTodo(todo.id).isEmpty())
        assertEquals(listOf(todo.id), scheduler.cancelledTodoIds)
    }

    @Test
    fun sta008_archiveKeepsCompletedAndSkippedHistoryAndGeneratesNoFutureRecords() = runBlocking {
        val todo = todoEntity("archive-with-history")
        database.todoDao().upsert(todo)
        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "completed").getOrThrow()
        clock.setDate(clock.date.plusDays(1))
        todoRepository.setSkipped(todo.id, clock.date, true, operationId = "skipped").getOrThrow()
        val historyBefore = database.todoExecutionDao().findForTodo(todo.id).sortedBy { it.logicalDate }

        todoRepository.archiveTodo(todo.id).getOrThrow()
        clock.setDate(LocalDate.of(2026, 8, 20))
        historyReconciler().reconcile()

        assertEquals(historyBefore, database.todoExecutionDao().findForTodo(todo.id).sortedBy { it.logicalDate })
        assertEquals(
            listOf(TodoState.COMPLETED.code, TodoState.SKIPPED.code),
            historyBefore.map { it.status },
        )
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
        assertTrue(database.periodResultDao().findForTodo(todo.id).isEmpty())
    }

    @Test
    fun sta009_archiveActiveCountPeriodKeepsActionsWithoutFinalizingPeriod() = runBlocking {
        val todo = todoEntity(
            id = "archive-active-period",
            rule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 3),
        )
        database.todoDao().upsert(todo)
        todoRepository.setCompleted(todo.id, clock.date, true, operationId = "period-completed").getOrThrow()
        clock.setDate(clock.date.plusDays(1))
        todoRepository.setSkipped(todo.id, clock.date, true, operationId = "period-skipped").getOrThrow()

        todoRepository.archiveTodo(todo.id).getOrThrow()
        clock.setDate(LocalDate.of(2026, 8, 20))
        historyReconciler().reconcile()

        assertEquals(
            setOf(TodoState.COMPLETED.code, TodoState.SKIPPED.code),
            database.todoExecutionDao().findForTodo(todo.id).map { it.status }.toSet(),
        )
        assertTrue(database.periodResultDao().findForTodo(todo.id).isEmpty())
        assertTrue(todoRepository.observeOccurrences(clock.date).first().isEmpty())
    }

    @Test
    fun sta012_archiveRestoreAndDeleteFailuresRollbackEveryDatabaseMutation() = runBlocking {
        val sqlite = database.openHelper.writableDatabase

        val archiveTodo = todoEntity("fault-archive")
        database.todoDao().upsert(archiveTodo)
        insertNotificationState(archiveTodo.id)
        sqlite.execSQL(
            "CREATE TRIGGER fail_archive BEFORE UPDATE OF archivedAt ON todos " +
                "BEGIN SELECT RAISE(ABORT, 'injected archive failure'); END",
        )
        assertTrue(todoRepository.archiveTodo(archiveTodo.id).isFailure)
        assertNull(database.todoDao().findById(archiveTodo.id)?.archivedAt)
        assertEquals(1, database.todoNotificationDao().findForTodo(archiveTodo.id).size)
        assertEquals(1, database.scheduledNotificationDao().findForTodo(archiveTodo.id).size)
        sqlite.execSQL("DROP TRIGGER fail_archive")

        val restoreTodo = todoEntity("fault-restore").copy(archivedAt = 100)
        database.todoDao().upsert(restoreTodo)
        sqlite.execSQL(
            "CREATE TRIGGER fail_restore BEFORE INSERT ON todo_runtime_states " +
                "BEGIN SELECT RAISE(ABORT, 'injected restore failure'); END",
        )
        assertTrue(todoRepository.restoreTodo(restoreTodo.id).isFailure)
        assertEquals(100L, database.todoDao().findById(restoreTodo.id)?.archivedAt)
        assertNull(database.todoRuntimeStateDao().find(restoreTodo.id))
        sqlite.execSQL("DROP TRIGGER fail_restore")

        val deleteTodo = todoEntity("fault-delete").copy(archivedAt = 200)
        database.todoDao().upsert(deleteTodo)
        val deleteExecution = execution("fault-delete-execution", deleteTodo.id, clock.date)
        database.todoExecutionDao().insert(deleteExecution)
        insertNotificationState(deleteTodo.id)
        database.todoRuntimeStateDao().upsert(runtimeState(deleteTodo.id))
        sqlite.execSQL(
            "CREATE TRIGGER fail_delete BEFORE DELETE ON todo_executions " +
                "BEGIN SELECT RAISE(ABORT, 'injected delete failure'); END",
        )
        assertTrue(todoRepository.deleteTodo(deleteTodo.id).isFailure)
        assertNotNull(database.todoDao().findById(deleteTodo.id))
        assertEquals(deleteExecution, database.todoExecutionDao().findById(deleteExecution.id))
        assertEquals(1, database.todoNotificationDao().findForTodo(deleteTodo.id).size)
        assertNotNull(database.todoRuntimeStateDao().find(deleteTodo.id))
        assertEquals(1, database.scheduledNotificationDao().findForTodo(deleteTodo.id).size)
        sqlite.execSQL("DROP TRIGGER fail_delete")
    }

    private fun historyRepository() = RoomHistoryRepository(
        database = database,
        todoDao = database.todoDao(),
        categoryDao = database.categoryDao(),
        executionDao = database.todoExecutionDao(),
        periodResultDao = database.periodResultDao(),
        todoRepository = todoRepository,
        settingsRepository = settings,
        notificationScheduler = scheduler,
        widgetUpdater = widgetUpdater,
        clock = clock,
    )

    private fun historyReconciler() = RoomHistoryReconciler(
        database = database,
        todoDao = database.todoDao(),
        categoryDao = database.categoryDao(),
        executionDao = database.todoExecutionDao(),
        periodResultDao = database.periodResultDao(),
        runtimeStateDao = database.todoRuntimeStateDao(),
        notificationDao = database.todoNotificationDao(),
        settingsRepository = settings,
        holidayRepository = TestHolidayRepository(),
        clock = clock,
    )

    private fun widgetModel(todo: TodoEntity, executions: List<TodoExecutionEntity>) =
        buildWidgetDisplayModel(
            now = clock.instant().atZone(zone),
            source = WidgetSourceData(listOf(todo), emptyList(), executions, emptySet()),
            dayEndHour = 0,
            weekStart = DayOfWeek.MONDAY,
            holidayState = TestHolidayRepository().snapshot.value,
            uncategorizedName = "uncategorized",
            logicalDateLabel = LocalDate::toString,
            deadlineLabel = { nextDay, hour, minute -> "$nextDay-$hour-$minute" },
        )

    private suspend fun insertNotificationState(todoId: String) {
        database.todoNotificationDao().upsertAll(
            listOf(
                TodoNotificationEntity(
                    id = "notification-$todoId",
                    todoId = todoId,
                    relation = "at",
                    amount = 0,
                    unit = "minute",
                    sortOrder = 0,
                    createdAt = 1,
                    updatedAt = 1,
                ),
            ),
        )
        database.scheduledNotificationDao().upsert(
            ScheduledNotificationEntity(
                candidateKey = "candidate-$todoId",
                todoId = todoId,
                notificationSettingId = "notification-$todoId",
                logicalDate = clock.date.toString(),
                definitionRevision = 1,
                triggerAt = clock.millis() + 1_000,
                requestCode = todoId.hashCode().and(Int.MAX_VALUE).coerceAtLeast(10_000),
                schedulingMode = "exact",
                state = "scheduled",
                failureCode = null,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    private fun todoEntity(
        id: String,
        title: String = id,
        dueMinutes: Int? = null,
        rule: RecurrenceRule = RecurrenceRule.daily(),
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(rule)
        return TodoEntity(
            id = id,
            title = title,
            description = "",
            categoryId = null,
            startDate = LocalDate.of(2026, 8, 10).toString(),
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = dueMinutes,
            definitionRevision = 1,
            createdAt = 1,
            updatedAt = 1,
            archivedAt = null,
        )
    }

    private fun execution(id: String, todoId: String, date: LocalDate) = TodoExecutionEntity(
        id = id,
        operationId = "operation-$id",
        todoId = todoId,
        logicalDate = date.toString(),
        status = TodoState.COMPLETED.code,
        actedAt = 1,
        finalizedAt = 1,
        definitionRevision = 1,
        snapshotVersion = 1,
        snapshotJson = "{}",
    )

    private fun runtimeState(todoId: String) = TodoRuntimeStateEntity(
        todoId = todoId,
        lastFinalizedLogicalDate = clock.date.minusDays(1).toString(),
        lastFinalizedWeeklyPeriodEnd = null,
        lastFinalizedMonthlyPeriodEnd = null,
        appliedDefinitionRevision = 1,
        reconciliationCursorDate = null,
        updatedAt = 1,
    )
}

private class MutableStateTestClock(
    initialDate: LocalDate,
    private val clockZone: ZoneId,
) : Clock() {
    private var currentInstant = initialDate.atTime(12, 0).atZone(clockZone).toInstant()
    val date: LocalDate
        get() = currentInstant.atZone(clockZone).toLocalDate()

    fun setDate(value: LocalDate) {
        currentInstant = value.atTime(12, 0).atZone(clockZone).toInstant()
    }

    override fun getZone(): ZoneId = clockZone
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(currentInstant, zone)
    override fun instant(): Instant = currentInstant
}

private class StateTestSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val dayEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)

    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
}

private class StateTestNotificationScheduler(
    private val database: MataDatabase,
) : NotificationScheduler {
    override val notificationCount = MutableStateFlow(0)
    val cancelledTodoIds = mutableListOf<String>()

    override fun systemState() = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    )

    override suspend fun reconcileTodo(todoId: String) = Unit
    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) {
        cancelledTodoIds += todoId
        database.scheduledNotificationDao().deleteForTodo(todoId)
    }
}
