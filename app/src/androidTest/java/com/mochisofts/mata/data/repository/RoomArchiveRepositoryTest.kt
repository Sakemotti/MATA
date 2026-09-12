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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var clock: MutableArchiveClock
    private val date = LocalDate.of(2026, 8, 11)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clock = MutableArchiveClock(
            Instant.parse("2026-08-11T03:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
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
    fun pastEndedTodoRestoreKeepsDefinitionEndedAfterPreviewWarning() = runBlocking {
        val endDate = date.minusDays(1)
        val values = insertArchivedTodo(todoId = "ended-restore", endDate = endDate)

        val preview = repository.getActionPreview(values.todo.id).getOrThrow()
        assertFalse(preview.hasFutureOccurrence)

        repository.restore(values.todo.id).getOrThrow()

        val restored = requireNotNull(todoRepository.getTodo(values.todo.id))
        assertNull(restored.archivedAt)
        assertEquals(endDate, restored.endDate)
        assertTrue(
            todoRepository.observeOccurrences(date).first()
                .none { occurrence -> occurrence.todo.id == values.todo.id },
        )
    }

    @Test
    fun unavailableNotificationsRemainConfiguredAndStoppedAfterRestore() = runBlocking {
        val values = insertArchivedTodo(todoId = "unavailable-notifications")
        database.scheduledNotificationDao().deleteForTodo(values.todo.id)
        scheduler.systemStateValue = scheduler.systemStateValue.copy(canPostNotifications = false)

        val preview = repository.getActionPreview(values.todo.id).getOrThrow()
        assertEquals(1, preview.notificationSettingCount)
        assertEquals(1, preview.unavailableNotificationCount)

        repository.restore(values.todo.id).getOrThrow()

        assertNull(database.todoDao().findById(values.todo.id)?.archivedAt)
        assertEquals(1, database.todoNotificationDao().findForTodo(values.todo.id).size)
        assertTrue(database.scheduledNotificationDao().findForTodo(values.todo.id).isEmpty())
        assertTrue(scheduler.reconciledTodoIds.contains(values.todo.id))
    }

    @Test
    fun at002_archivedDefinitionsAppearOnceWithoutCategoryGroupingOrOccurrenceExpansion() = runBlocking {
        val dailyCategory = categoryEntity("daily-category", "日常", 0)
        val countCategory = categoryEntity("count-category", "ゲーム", 1)
        insertArchivedDefinition(
            id = "daily",
            title = "毎日のTODO",
            category = dailyCategory,
            rule = RecurrenceRule.daily(),
            archivedAt = 300,
        )
        insertArchivedDefinition(
            id = "weekly-count",
            title = "週3回のTODO",
            category = countCategory,
            rule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 3),
            archivedAt = 200,
        )
        insertArchivedDefinition(
            id = "monthly",
            title = "毎月のTODO",
            category = null,
            rule = RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = 11),
            archivedAt = 100,
        )

        val items = repository.pagedTodos("", ArchiveSortOrder.NEWEST).asSnapshot()

        assertEquals(listOf("daily", "weekly-count", "monthly"), items.map { it.todo.id })
        assertEquals(3, items.map { it.todo.id }.distinct().size)
        assertEquals(
            setOf(RecurrenceType.DAILY, RecurrenceType.WEEKLY_COUNT, RecurrenceType.MONTHLY_DAY),
            items.map { it.todo.recurrenceType }.toSet(),
        )
        assertEquals(listOf("日常", "ゲーム", null), items.map { it.category?.name })
    }

    @Test
    fun at004_searchMatchesCurrentTitleDescriptionAndCategoryIgnoringAsciiCase() = runBlocking {
        val ordinary = categoryEntity("ordinary", "通常カテゴリ", 0)
        val matching = categoryEntity("matching", "MixedCategory", 1)
        insertArchivedDefinition(
            id = "title-match",
            title = "MixedTitle",
            description = "通常説明",
            category = ordinary,
            archivedAt = 300,
        )
        insertArchivedDefinition(
            id = "description-match",
            title = "通常タイトル1",
            description = "MixedDescription",
            category = ordinary,
            archivedAt = 200,
        )
        insertArchivedDefinition(
            id = "category-match",
            title = "通常タイトル2",
            description = "別の説明",
            category = matching,
            archivedAt = 100,
        )

        assertEquals(listOf("title-match"), archivedTodoIds("mixedtitle"))
        assertEquals(listOf("description-match"), archivedTodoIds("MIXEDDESCRIPTION"))
        assertEquals(listOf("category-match"), archivedTodoIds("mixedcategory"))
        assertEquals(emptyList<String>(), archivedTodoIds("一致しない語"))
    }

    @Test
    fun at011_historySummaryCountsCompletedMissedSkippedAndPeriodRows() = runBlocking {
        val todo = insertArchivedDefinition(id = "summary", title = "集計対象")
        insertHistoryExecution(
            todo,
            "completed",
            TodoState.COMPLETED,
            logicalDate = date.minusDays(2),
            actedAt = 100,
        )
        insertHistoryExecution(
            todo,
            "missed",
            TodoState.MISSED,
            logicalDate = date.minusDays(1),
            actedAt = 200,
        )
        insertHistoryExecution(todo, "skipped", TodoState.SKIPPED, actedAt = 300)
        insertPeriodResult(todo, "period", finalizedAt = 400)

        val summary = repository.observeHistorySummary(todo.id).first()

        assertEquals(1, summary.completedCount)
        assertEquals(1, summary.missedCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(1, summary.periodResultCount)
        assertEquals(3, summary.executionCount)
        assertEquals(4, summary.totalCount)
    }

    @Test
    fun atd01_archiveSortModesUseDocumentedStableTieBreakers() = runBlocking {
        insertArchivedDefinition("same-b", "Same", archivedAt = 300)
        insertArchivedDefinition("same-a", "Same", archivedAt = 300)
        insertArchivedDefinition("alpha", "Alpha", archivedAt = 200)
        insertArchivedDefinition("zulu", "Zulu", archivedAt = 200)
        insertArchivedDefinition("same-old", "Same", archivedAt = 100)

        assertEquals(
            listOf("same-a", "same-b", "alpha", "zulu", "same-old"),
            archivedTodoIds("", ArchiveSortOrder.NEWEST),
        )
        assertEquals(
            listOf("same-old", "alpha", "zulu", "same-a", "same-b"),
            archivedTodoIds("", ArchiveSortOrder.OLDEST),
        )
        assertEquals(
            listOf("alpha", "same-a", "same-b", "same-old", "zulu"),
            archivedTodoIds("", ArchiveSortOrder.TITLE),
        )
    }

    @Test
    fun atd02_searchTrimsWhitespaceResetsOnBlankAndIgnoresHistorySnapshots() = runBlocking {
        val todo = insertArchivedDefinition(
            id = "current",
            title = "Current Needle",
            archivedAt = 100,
        )
        val oldDefinition = todo.copy(title = "History Needle")
        val snapshot = HistorySnapshotJson.encode(
            todo = oldDefinition,
            category = null,
            notifications = emptyList(),
            endHour = 0,
            weekStart = DayOfWeek.MONDAY,
            logicalDate = date.minusDays(1),
        )
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = "historical-title",
                operationId = "historical-title-operation",
                todoId = todo.id,
                logicalDate = date.minusDays(1).toString(),
                status = TodoState.COMPLETED.code,
                actedAt = 100,
                finalizedAt = 100,
                definitionRevision = todo.definitionRevision,
                snapshotVersion = 1,
                snapshotJson = snapshot,
            ),
        )

        assertEquals(listOf(todo.id), archivedTodoIds("  current needle  "))
        assertEquals(listOf(todo.id), archivedTodoIds("   "))
        assertEquals(emptyList<String>(), archivedTodoIds("history needle"))
    }

    @Test
    fun atd04_historyUsesTimeTypeAndStableIdTieBreakersWithinTheSameDate() = runBlocking {
        val todo = insertArchivedDefinition(id = "history-order", title = "履歴順序")
        insertHistoryExecution(todo, "execution", TodoState.COMPLETED, actedAt = 500)
        insertHistoryExecution(
            todo,
            "previous-day",
            TodoState.SKIPPED,
            logicalDate = date.minusDays(1),
            actedAt = 900,
        )
        insertPeriodResult(todo, "period-b", finalizedAt = 500)
        insertPeriodResult(
            todo,
            "period-a",
            finalizedAt = 500,
            periodStart = date.minusDays(13),
        )

        val history = repository.pagedHistory(todo.id).asSnapshot()

        assertEquals(
            listOf(
                "execution:execution",
                "period:period-a",
                "period:period-b",
                "execution:previous-day",
            ),
            history.map { it.stableId },
        )
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
    fun at018_restoreKeepsCurrentCompletedAndSkippedOccurrencesWithoutDuplicates() = runBlocking {
        val states = listOf(TodoState.COMPLETED, TodoState.SKIPPED)
        states.forEachIndexed { index, state ->
            val values = insertArchivedTodo(
                todoId = "current-$index",
                includeBaseHistory = false,
            )
            insertExecution(
                values = values,
                id = "current-execution-$index",
                logicalDate = date,
                state = state,
            )

            repository.restore(values.todo.id).getOrThrow()

            val matching = todoRepository.observeOccurrences(date).first()
                .filter { occurrence -> occurrence.todo.id == values.todo.id }
            assertEquals(1, matching.size)
            assertEquals(state, matching.single().state)
            assertEquals(
                1,
                database.todoExecutionDao().findForTodo(values.todo.id)
                    .count { execution -> execution.logicalDate == date.toString() },
            )
        }
    }

    @Test
    fun at019_restoreRecalculatesWeeklyAndMonthlyCountProgressFromPreservedHistory() = runBlocking {
        val weekly = insertArchivedTodo(
            todoId = "weekly-progress",
            rule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 3),
            includeBaseHistory = false,
        )
        insertExecution(weekly, "weekly-completed", date.minusDays(1), TodoState.COMPLETED)
        insertExecution(weekly, "weekly-skipped", date, TodoState.SKIPPED)

        val monthly = insertArchivedTodo(
            todoId = "monthly-achieved",
            rule = RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 2),
            includeBaseHistory = false,
        )
        insertExecution(monthly, "monthly-completed-1", date.withDayOfMonth(1), TodoState.COMPLETED)
        insertExecution(monthly, "monthly-completed-2", date.withDayOfMonth(2), TodoState.COMPLETED)
        insertExecution(monthly, "monthly-skipped", date.withDayOfMonth(3), TodoState.SKIPPED)

        repository.restore(weekly.todo.id).getOrThrow()
        repository.restore(monthly.todo.id).getOrThrow()

        val occurrences = todoRepository.observeOccurrences(date).first()
        val weeklyOccurrence = occurrences.single { occurrence -> occurrence.todo.id == weekly.todo.id }
        assertEquals(TodoState.SKIPPED, weeklyOccurrence.state)
        assertEquals(3, weeklyOccurrence.progress?.period?.requiredCount)
        assertEquals(1, weeklyOccurrence.progress?.completedCount)
        assertEquals(2, weeklyOccurrence.progress?.remainingCount)
        assertTrue(occurrences.none { occurrence -> occurrence.todo.id == monthly.todo.id })
        assertEquals(3, database.todoExecutionDao().findForTodo(monthly.todo.id).size)
    }

    @Test
    fun at031_archiveListAndHistoryUseStableBoundedPages() = runBlocking {
        val encoded = RecurrenceRuleJson.encode(RecurrenceRule.daily())
        repeat(120) { index ->
            val suffix = index.toString().padStart(3, '0')
            database.todoDao().upsert(
                TodoEntity(
                    id = "paged-todo-$suffix",
                    title = "Archive $suffix",
                    description = "",
                    categoryId = null,
                    startDate = date.minusDays(150).toString(),
                    endDate = null,
                    recurrenceType = encoded.typeCode,
                    repeatParamsVersion = encoded.paramsVersion,
                    repeatParamsJson = encoded.paramsJson,
                    dueMinutes = null,
                    definitionRevision = 1,
                    createdAt = index.toLong(),
                    updatedAt = index.toLong(),
                    archivedAt = 1_000L + index,
                ),
            )
        }
        repeat(120) { index ->
            database.todoExecutionDao().insert(
                TodoExecutionEntity(
                    id = "paged-execution-${index.toString().padStart(3, '0')}",
                    operationId = "paged-operation-${index.toString().padStart(3, '0')}",
                    todoId = "paged-todo-000",
                    logicalDate = date.minusDays(index.toLong()).toString(),
                    status = TodoState.COMPLETED.code,
                    actedAt = index.toLong(),
                    finalizedAt = index.toLong(),
                    definitionRevision = 1,
                    snapshotVersion = 1,
                    snapshotJson = "{}",
                ),
            )
        }

        val todoPages = loadThreePages(database.todoDao().pageArchivedNewest(""))
        val historyPages = loadThreePages(
            database.todoExecutionDao().pageArchiveHistory("paged-todo-000"),
        )

        assertEquals(listOf(50, 50, 20), todoPages.map { page -> page.size })
        assertEquals(listOf(50, 50, 20), historyPages.map { page -> page.size })
        assertEquals(120, todoPages.flatten().map { row -> row.todo.id }.distinct().size)
        assertEquals(120, historyPages.flatten().map { row -> row.id }.distinct().size)
        assertEquals("paged-todo-119", todoPages.first().first().todo.id)
        assertEquals("paged-execution-000", historyPages.first().first().id)
    }

    @Test
    fun atd05_restoreClearsOldArchiveTimeAndRearchiveRecordsNewTime() = runBlocking {
        val values = insertArchivedTodo()
        val oldArchiveTime = requireNotNull(values.todo.archivedAt)

        repository.restore(values.todo.id).getOrThrow()
        assertNull(database.todoDao().findById(values.todo.id)?.archivedAt)
        clock.advance(Duration.ofHours(2))
        todoRepository.archiveTodo(values.todo.id).getOrThrow()

        val newArchiveTime = requireNotNull(database.todoDao().findById(values.todo.id)?.archivedAt)
        assertEquals(clock.millis(), newArchiveTime)
        assertTrue(newArchiveTime != oldArchiveTime)
    }

    @Test
    fun atd06_permanentDeletePersistsInvalidationBeforeCancellationFailure() = runBlocking {
        val values = insertArchivedTodo()
        scheduler.failCancellation = true

        repository.deletePermanently(values.todo.id).getOrThrow()

        assertNull(database.todoDao().findById(values.todo.id))
        assertTrue(scheduler.todoWasAbsentWhenCancellationAttempted)
        assertTrue(!scheduler.isDeliveryEligible("candidate"))
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
    fun at023_restoreFailuresKeepArchivedStateAndNotificationsStopped() = runBlocking {
        val sqlite = database.openHelper.writableDatabase

        val databaseFailure = insertArchivedTodo(todoId = "restore-database-failure")
        database.scheduledNotificationDao().deleteForTodo(databaseFailure.todo.id)
        sqlite.execSQL(
            "CREATE TRIGGER at023_fail_restore BEFORE UPDATE OF archivedAt ON todos " +
                "WHEN OLD.id = '${databaseFailure.todo.id}' " +
                "BEGIN SELECT RAISE(ABORT, 'injected restore database failure'); END",
        )
        assertTrue(repository.restore(databaseFailure.todo.id).isFailure)
        assertEquals(
            databaseFailure.todo.archivedAt,
            database.todoDao().findById(databaseFailure.todo.id)?.archivedAt,
        )
        assertTrue(database.scheduledNotificationDao().findForTodo(databaseFailure.todo.id).isEmpty())
        sqlite.execSQL("DROP TRIGGER at023_fail_restore")

        val calculationFailure = insertArchivedTodo(todoId = "restore-calculation-failure")
        database.scheduledNotificationDao().deleteForTodo(calculationFailure.todo.id)
        settings.failDayEndHourRead = true
        assertTrue(repository.restore(calculationFailure.todo.id).isFailure)
        settings.failDayEndHourRead = false
        assertEquals(
            calculationFailure.todo.archivedAt,
            database.todoDao().findById(calculationFailure.todo.id)?.archivedAt,
        )
        assertTrue(database.scheduledNotificationDao().findForTodo(calculationFailure.todo.id).isEmpty())

        val notificationFailure = insertArchivedTodo(todoId = "restore-notification-failure")
        database.scheduledNotificationDao().deleteForTodo(notificationFailure.todo.id)
        val originalRuntime = database.todoRuntimeStateDao().find(notificationFailure.todo.id)
        scheduler.failReconciliation = true
        assertTrue(repository.restore(notificationFailure.todo.id).isFailure)
        scheduler.failReconciliation = false

        assertEquals(notificationFailure.todo, database.todoDao().findById(notificationFailure.todo.id))
        assertEquals(originalRuntime, database.todoRuntimeStateDao().find(notificationFailure.todo.id))
        assertTrue(database.scheduledNotificationDao().findForTodo(notificationFailure.todo.id).isEmpty())
        assertTrue(scheduler.cancelledTodoIds.contains(notificationFailure.todo.id))
    }

    @Test
    fun at029_permanentDeleteFailureRollsBackAllArchivedTodoRows() = runBlocking {
        val values = insertArchivedTodo(todoId = "delete-rollback")
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "CREATE TRIGGER at029_fail_delete BEFORE DELETE ON todo_executions " +
                "WHEN OLD.todoId = '${values.todo.id}' " +
                "BEGIN SELECT RAISE(ABORT, 'injected permanent delete failure'); END",
        )

        assertTrue(repository.deletePermanently(values.todo.id).isFailure)

        assertEquals(values.todo, database.todoDao().findById(values.todo.id))
        assertEquals(1, database.todoExecutionDao().findForTodo(values.todo.id).size)
        assertEquals(1, database.periodResultDao().findForTodo(values.todo.id).size)
        assertEquals(1, database.todoNotificationDao().findForTodo(values.todo.id).size)
        assertNotNull(database.todoRuntimeStateDao().find(values.todo.id))
        assertEquals(1, database.scheduledNotificationDao().findForTodo(values.todo.id).size)
        sqlite.execSQL("DROP TRIGGER at029_fail_delete")
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
        todoId: String = "todo",
        rule: RecurrenceRule = RecurrenceRule.daily(),
        startDate: LocalDate = date.minusDays(20),
        endDate: LocalDate? = null,
        includeBaseHistory: Boolean = true,
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
            id = todoId,
            title = "検索語を含むTODO",
            description = "説明",
            categoryId = category.id,
            startDate = startDate.toString(),
            endDate = endDate?.toString(),
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
                    id = if (todoId == "todo") "notification" else "notification-$todoId",
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
        if (includeBaseHistory) {
            val suffix = if (todoId == "todo") "" else "-$todoId"
            database.todoExecutionDao().insert(
                TodoExecutionEntity(
                    id = "execution$suffix",
                    operationId = "operation$suffix",
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
                    id = "period$suffix",
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
        }
        database.scheduledNotificationDao().upsert(
            ScheduledNotificationEntity(
                candidateKey = if (todoId == "todo") "candidate" else "candidate-$todoId",
                todoId = todo.id,
                notificationSettingId = if (todoId == "todo") "notification" else "notification-$todoId",
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

    private suspend fun insertArchivedDefinition(
        id: String,
        title: String,
        description: String = "",
        category: CategoryEntity? = null,
        rule: RecurrenceRule = RecurrenceRule.daily(),
        archivedAt: Long = 20,
    ): TodoEntity {
        category?.let { database.categoryDao().upsert(it) }
        val encoded = RecurrenceRuleJson.encode(rule)
        return TodoEntity(
            id = id,
            title = title,
            description = description,
            categoryId = category?.id,
            startDate = date.minusDays(20).toString(),
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = null,
            definitionRevision = 1,
            createdAt = 10,
            updatedAt = 10,
            archivedAt = archivedAt,
        ).also { database.todoDao().upsert(it) }
    }

    private fun categoryEntity(id: String, name: String, sortOrder: Int) = CategoryEntity(
        id = id,
        name = name,
        normalizedName = name.lowercase(),
        colorIndex = sortOrder,
        iconName = "Category",
        legacyEndHour = 0,
        sortOrder = sortOrder,
        createdAt = sortOrder.toLong(),
    )

    private suspend fun archivedTodoIds(
        query: String,
        sortOrder: ArchiveSortOrder = ArchiveSortOrder.NEWEST,
    ): List<String> = repository.pagedTodos(query, sortOrder).asSnapshot().map { it.todo.id }

    private suspend fun insertHistoryExecution(
        todo: TodoEntity,
        id: String,
        state: TodoState,
        logicalDate: LocalDate = date,
        actedAt: Long,
    ) {
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = id,
                operationId = "operation-$id",
                todoId = todo.id,
                logicalDate = logicalDate.toString(),
                status = state.code,
                actedAt = actedAt,
                finalizedAt = actedAt,
                definitionRevision = todo.definitionRevision,
                snapshotVersion = 1,
                snapshotJson = "{}",
            ),
        )
    }

    private suspend fun insertPeriodResult(
        todo: TodoEntity,
        id: String,
        finalizedAt: Long,
        periodStart: LocalDate = date.minusDays(6),
    ) {
        database.periodResultDao().insert(
            PeriodResultEntity(
                id = id,
                todoId = todo.id,
                periodType = RecurrenceType.WEEKLY_COUNT.code,
                periodStart = periodStart.toString(),
                periodEnd = date.toString(),
                requiredCount = 1,
                completedCount = 1,
                achieved = true,
                displayDate = date.toString(),
                finalizedAt = finalizedAt,
                definitionRevision = todo.definitionRevision,
                snapshotVersion = 1,
                snapshotJson = "{}",
            ),
        )
    }

    private suspend fun insertExecution(
        values: TestValues,
        id: String,
        logicalDate: LocalDate,
        state: TodoState,
    ) {
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = id,
                operationId = "operation-$id",
                todoId = values.todo.id,
                logicalDate = logicalDate.toString(),
                status = state.code,
                actedAt = 300,
                finalizedAt = 300,
                definitionRevision = values.todo.definitionRevision,
                snapshotVersion = 1,
                snapshotJson = "{}",
            ),
        )
    }

    private suspend fun <T : Any> loadThreePages(source: PagingSource<Int, T>): List<List<T>> {
        val first = source.load(refreshParams()) as PagingSource.LoadResult.Page
        val second = source.load(appendParams(requireNotNull(first.nextKey))) as PagingSource.LoadResult.Page
        val third = source.load(appendParams(requireNotNull(second.nextKey))) as PagingSource.LoadResult.Page
        assertNull(third.nextKey)
        return listOf(first.data, second.data, third.data)
    }

    private fun refreshParams() = PagingSource.LoadParams.Refresh<Int>(
        key = null,
        loadSize = PAGE_SIZE,
        placeholdersEnabled = false,
    )

    private fun appendParams(key: Int) = PagingSource.LoadParams.Append(
        key = key,
        loadSize = PAGE_SIZE,
        placeholdersEnabled = false,
    )

    private suspend fun assertAllRelatedRowsDeleted(todoId: String) {
        assertNull(database.todoDao().findById(todoId))
        assertTrue(database.todoExecutionDao().findForTodo(todoId).isEmpty())
        assertTrue(database.periodResultDao().findForTodo(todoId).isEmpty())
        assertNull(database.todoRuntimeStateDao().find(todoId))
        assertTrue(database.todoNotificationDao().findForTodo(todoId).isEmpty())
        assertTrue(database.scheduledNotificationDao().findForTodo(todoId).isEmpty())
    }

    private data class TestValues(val todo: TodoEntity, val category: CategoryEntity)

    private companion object {
        const val PAGE_SIZE = 50
    }
}

private class ArchiveTestSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    private val dayEndHourState = MutableStateFlow(0)
    var failDayEndHourRead = false
    override val dayEndHour: Flow<Int>
        get() = if (failDayEndHourRead) flow { error("injected schedule calculation failure") } else dayEndHourState
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override val archiveSortOrder = MutableStateFlow(ArchiveSortOrder.NEWEST)
    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHourState.value = value }
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
    var failCancellation = false
    var failReconciliation = false
    var todoWasAbsentWhenCancellationAttempted = false
    var systemStateValue = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    )
    override fun systemState() = systemStateValue
    override suspend fun reconcileTodo(todoId: String) {
        reconciledTodoIds += todoId
        if (failReconciliation) {
            database.scheduledNotificationDao().upsert(
                ScheduledNotificationEntity(
                    candidateKey = "partial-$todoId",
                    todoId = todoId,
                    notificationSettingId = "notification-$todoId",
                    logicalDate = "2026-08-11",
                    definitionRevision = 1,
                    triggerAt = 1_000,
                    requestCode = 20_000,
                    schedulingMode = "exact",
                    state = "scheduled",
                    failureCode = null,
                    createdAt = 10,
                    updatedAt = 10,
                ),
            )
            error("injected notification registration failure")
        }
    }
    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) {
        cancelledTodoIds += todoId
        todoWasAbsentWhenCancellationAttempted = database.todoDao().findById(todoId) == null
        if (failCancellation) throw IllegalStateException("injected cancellation failure")
        database.scheduledNotificationDao().deleteForTodo(todoId)
    }

    suspend fun isDeliveryEligible(candidateKey: String): Boolean {
        val scheduled = database.scheduledNotificationDao().find(candidateKey) ?: return false
        val todo = database.todoDao().findById(scheduled.todoId) ?: return false
        return todo.archivedAt == null &&
            database.todoNotificationDao().find(todo.id, scheduled.notificationSettingId) != null
    }
}

private class MutableArchiveClock(
    private var currentInstant: Instant,
    private val zoneId: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zoneId
    override fun withZone(zone: ZoneId): Clock = MutableArchiveClock(currentInstant, zone)
    override fun instant(): Instant = currentInstant
    fun advance(duration: Duration) { currentInstant = currentInstant.plus(duration) }
}
