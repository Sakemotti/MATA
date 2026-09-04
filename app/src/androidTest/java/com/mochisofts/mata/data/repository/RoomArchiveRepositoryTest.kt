package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.ScheduledNotificationEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.nextOccurrences
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomArchiveRepositoryTest {
    private lateinit var database: MataDatabase
    private lateinit var repository: RoomArchiveRepository
    private lateinit var todoRepository: RoomTodoRepository
    private lateinit var scheduler: ArchiveTestNotificationScheduler
    private val settings = ArchiveTestSettingsRepository()
    private val clock = Clock.fixed(
        Instant.parse("2026-08-11T03:00:00Z"),
        ZoneId.of("Asia/Tokyo"),
    )
    private val date = LocalDate.of(2026, 8, 11)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = ArchiveTestNotificationScheduler(database)
        todoRepository = RoomTodoRepository(
            database = database,
            todoDao = database.todoDao(),
            categoryDao = database.categoryDao(),
            executionDao = database.todoExecutionDao(),
            notificationDao = database.todoNotificationDao(),
            runtimeStateDao = database.todoRuntimeStateDao(),
            settingsRepository = settings,
            notificationScheduler = scheduler,
            widgetUpdater = WidgetUpdater(context, DiagnosticLogger()),
            holidayRepository = TestHolidayRepository(),
            clock = clock,
        )
        repository = RoomArchiveRepository(
            todoDao = database.todoDao(),
            executionDao = database.todoExecutionDao(),
            notificationDao = database.todoNotificationDao(),
            todoRepository = todoRepository,
            settingsRepository = settings,
            notificationScheduler = scheduler,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pagingAndPreview_useCurrentDefinitionAndHistoryCounts() = runBlocking {
        val values = insertArchivedTodo()

        val page = database.todoDao().pageArchivedNewest("検索語").load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        assertEquals(listOf(values.todo.id), page.data.map { it.todo.id })
        assertEquals(values.category.name, page.data.single().categoryName)

        val preview = repository.getActionPreview(values.todo.id).getOrThrow()
        assertTrue(preview.hasFutureOccurrence)
        assertEquals(1, preview.notificationSettingCount)
        assertEquals(1, preview.historySummary.completedCount)
        assertEquals(1, preview.historySummary.periodResultCount)
    }

    @Test
    fun sta010_restoreReconcilesCurrentAndFutureFromOriginalDefinition() = runBlocking {
        val values = insertArchivedTodo()

        repository.restore(values.todo.id).getOrThrow()

        assertNull(database.todoDao().findById(values.todo.id)?.archivedAt)
        val restored = requireNotNull(todoRepository.getTodo(values.todo.id))
        assertEquals(LocalDate.parse(values.todo.startDate), restored.startDate)
        assertEquals(listOf(date, date.plusDays(1)), restored.nextOccurrences(date, limit = 2))
        val occurrence = todoRepository.observeOccurrences(date).first().single()
        assertEquals(date, occurrence.logicalDate)
        assertEquals(TodoState.PENDING, occurrence.state)
        assertTrue(scheduler.reconciledTodoIds.contains(values.todo.id))
    }

    @Test
    fun at016_restoreKeepsEveryNDaysAnchoredToOriginalStartDate() = runBlocking {
        val start = date.minusDays(20)
        val values = insertArchivedTodo(
            rule = RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 3),
            startDate = start,
        )

        repository.restore(values.todo.id).getOrThrow()

        val restored = requireNotNull(todoRepository.getTodo(values.todo.id))
        assertEquals(start, restored.startDate)
        assertEquals(
            listOf(date.plusDays(1), date.plusDays(4)),
            restored.nextOccurrences(date, limit = 2),
        )
    }

    @Test
    fun at017_restoreDoesNotBackfillArchivedExecutionsOrPeriodResults() = runBlocking {
        val values = insertArchivedTodo()
        val executionIds = database.todoExecutionDao().findForTodo(values.todo.id).map { it.id }
        val periodIds = database.periodResultDao().findForTodo(values.todo.id).map { it.id }

        repository.restore(values.todo.id).getOrThrow()

        assertEquals(
            executionIds,
            database.todoExecutionDao().findForTodo(values.todo.id).map { it.id },
        )
        assertEquals(
            periodIds,
            database.periodResultDao().findForTodo(values.todo.id).map { it.id },
        )
        assertEquals(1, database.todoExecutionDao().findForTodo(values.todo.id).size)
        assertEquals(1, database.periodResultDao().findForTodo(values.todo.id).size)
        assertEquals(
            date.minusDays(1).toString(),
            database.todoRuntimeStateDao().find(values.todo.id)?.lastFinalizedLogicalDate,
        )
    }

    @Test
    fun sta011_todoRepositoryDeleteRemovesDefinitionHistoryStateAndNotifications() = runBlocking {
        val values = insertArchivedTodo()

        todoRepository.deleteTodo(values.todo.id).getOrThrow()

        assertAllRelatedRowsDeleted(values.todo.id)
        assertTrue(scheduler.cancelledTodoIds.contains(values.todo.id))
    }

    @Test
    fun at028_archivePermanentDeleteRemovesAllRelatedRowsAndScheduledNotifications() = runBlocking {
        val values = insertArchivedTodo()

        repository.deletePermanently(values.todo.id).getOrThrow()

        assertAllRelatedRowsDeleted(values.todo.id)
        assertTrue(scheduler.cancelledTodoIds.contains(values.todo.id))
    }

    @Test
    fun migratedHistoryWithoutFullSnapshot_fallsBackToCurrentDefinition() = runBlocking {
        val values = insertArchivedTodo()
        database.todoExecutionDao().deleteById("execution")
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = "migrated-execution",
                operationId = "migrated-operation",
                todoId = values.todo.id,
                logicalDate = date.minusDays(15).toString(),
                status = "completed",
                actedAt = 100,
                finalizedAt = 100,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = "{\"version\":1,\"migratedFromSchema\":3}",
            ),
        )

        val history = repository.pagedHistory(values.todo.id).asSnapshot()
        val migrated = history.filterIsInstance<ArchivedHistoryItem.Execution>().single()

        assertEquals(values.todo.title, migrated.entry.snapshot.title)
        assertEquals(values.category.name, migrated.entry.snapshot.categoryName)
    }

    private suspend fun insertArchivedTodo(
        rule: RecurrenceRule = RecurrenceRule.daily(),
        startDate: LocalDate = date.minusDays(20),
    ): TestValues {
        val category = CategoryEntity(
            id = "category",
            name = "生活",
            normalizedName = "生活",
            colorIndex = 4,
            iconName = "Home",
            legacyEndHour = 0,
            sortOrder = 0,
            createdAt = 1,
        )
        database.categoryDao().upsert(category)
        val encoded = RecurrenceRuleJson.encode(rule)
        val todo = TodoEntity(
            id = "todo",
            title = "検索語を含むTODO",
            description = "説明",
            categoryId = category.id,
            startDate = startDate.toString(),
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = 720,
            definitionRevision = 1,
            createdAt = 10,
            updatedAt = 10,
            archivedAt = 20,
        )
        database.todoDao().upsert(todo)
        database.todoNotificationDao().upsertAll(
            listOf(
                TodoNotificationEntity(
                    id = "notification",
                    todoId = todo.id,
                    relation = "at",
                    amount = 0,
                    unit = "minute",
                    sortOrder = 0,
                    createdAt = 10,
                    updatedAt = 10,
                ),
            ),
        )
        database.todoRuntimeStateDao().upsert(
            TodoRuntimeStateEntity(
                todoId = todo.id,
                lastFinalizedLogicalDate = date.minusDays(10).toString(),
                lastFinalizedWeeklyPeriodEnd = null,
                lastFinalizedMonthlyPeriodEnd = null,
                appliedDefinitionRevision = 1,
                reconciliationCursorDate = null,
                updatedAt = 10,
            ),
        )
        val snapshot = HistorySnapshotJson.encode(
            todo = todo,
            category = category,
            notifications = database.todoNotificationDao().findForTodo(todo.id),
            endHour = 0,
            weekStart = DayOfWeek.MONDAY,
            logicalDate = date.minusDays(15),
        )
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = "execution",
                operationId = "operation",
                todoId = todo.id,
                logicalDate = date.minusDays(15).toString(),
                status = "completed",
                actedAt = 100,
                finalizedAt = 100,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = snapshot,
            ),
        )
        database.periodResultDao().insert(
            PeriodResultEntity(
                id = "period",
                todoId = todo.id,
                periodType = "weekly_count",
                periodStart = date.minusDays(14).toString(),
                periodEnd = date.minusDays(8).toString(),
                requiredCount = 1,
                completedCount = 1,
                achieved = true,
                displayDate = date.minusDays(8).toString(),
                finalizedAt = 200,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = snapshot,
            ),
        )
        database.scheduledNotificationDao().upsert(
            ScheduledNotificationEntity(
                candidateKey = "candidate",
                todoId = todo.id,
                notificationSettingId = "notification",
                logicalDate = date.toString(),
                definitionRevision = 1,
                triggerAt = 999,
                requestCode = 10000,
                schedulingMode = "exact",
                state = "scheduled",
                failureCode = null,
                createdAt = 10,
                updatedAt = 10,
            ),
        )
        return TestValues(todo, category)
    }

    private suspend fun assertAllRelatedRowsDeleted(todoId: String) {
        assertNull(database.todoDao().findById(todoId))
        assertTrue(database.todoExecutionDao().findForTodo(todoId).isEmpty())
        assertTrue(database.periodResultDao().findForTodo(todoId).isEmpty())
        assertNull(database.todoRuntimeStateDao().find(todoId))
        assertTrue(database.todoNotificationDao().findForTodo(todoId).isEmpty())
        assertTrue(database.scheduledNotificationDao().findForTodo(todoId).isEmpty())
    }

    private data class TestValues(val todo: TodoEntity, val category: CategoryEntity)
}

private class ArchiveTestSettingsRepository : SettingsRepository {
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

private class ArchiveTestNotificationScheduler(
    private val database: MataDatabase,
) : NotificationScheduler {
    override val notificationCount = MutableStateFlow(0)
    val reconciledTodoIds = mutableListOf<String>()
    val cancelledTodoIds = mutableListOf<String>()
    override fun systemState() = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    )
    override suspend fun reconcileTodo(todoId: String) { reconciledTodoIds += todoId }
    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) {
        cancelledTodoIds += todoId
        database.scheduledNotificationDao().deleteForTodo(todoId)
    }
}
