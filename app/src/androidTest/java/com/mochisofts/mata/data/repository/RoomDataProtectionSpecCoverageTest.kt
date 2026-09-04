package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.backup.DataMutationGate
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.repository.NotificationScheduler
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class RoomDataProtectionSpecCoverageTest {
    private lateinit var context: Context
    private lateinit var database: MataDatabase
    private lateinit var categoryRepository: RoomCategoryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        categoryRepository = RoomCategoryRepository(
            database = database,
            categoryDao = database.categoryDao(),
            clock = TEST_CLOCK,
            notificationScheduler = DataProtectionNotificationScheduler(),
            widgetUpdater = WidgetUpdater(context, DiagnosticLogger()),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dat001_roomAndDataStoreSurviveCompleteStorageReopen() = runBlocking {
        val databaseName = "data-protection-${System.nanoTime()}.db"
        val dataStoreFile = File(context.cacheDir, "$databaseName.preferences_pb")
        val scopes = mutableListOf<CoroutineScope>()
        var writingDatabase: MataDatabase? = null
        var reopenedDatabase: MataDatabase? = null
        context.deleteDatabase(databaseName)
        dataStoreFile.delete()

        try {
            writingDatabase = persistentDatabase(databaseName)
            val firstDataStore = newDataStore(dataStoreFile, scopes)
            val firstSettings = DataStoreSettingsRepository(firstDataStore, DataMutationGate())
            val category = categoryEntity("persistent-category", 0)
            val todo = todoEntity(
                id = "persistent-todo",
                categoryId = category.id,
                archivedAt = null,
            )
            writingDatabase.categoryDao().upsert(category)
            writingDatabase.todoDao().upsert(todo)
            writingDatabase.todoExecutionDao().insert(executionEntity(todo.id, 1))
            firstSettings.setDayEndHour(4)
            firstSettings.setWeekStart(DayOfWeek.SUNDAY)
            firstSettings.setShowCompleted(true)
            firstSettings.setTodoListMode("CATEGORY")
            firstSettings.setTheme(AppTheme.DARK)
            firstSettings.setNotificationPermissionRequested(true)
            firstSettings.setArchiveSortOrder(ArchiveSortOrder.OLDEST)

            writingDatabase.close()
            writingDatabase = null
            scopes.single().coroutineContext[Job]?.cancelAndJoin()

            reopenedDatabase = persistentDatabase(databaseName)
            val secondDataStore = newDataStore(dataStoreFile, scopes)
            val reopenedSettings = DataStoreSettingsRepository(secondDataStore, DataMutationGate())

            assertEquals(category, reopenedDatabase.categoryDao().findById(category.id))
            assertEquals(todo, reopenedDatabase.todoDao().findById(todo.id))
            assertEquals(1, reopenedDatabase.todoExecutionDao().findForTodo(todo.id).size)
            assertEquals(4, reopenedSettings.dayEndHour.first())
            assertEquals(DayOfWeek.SUNDAY, reopenedSettings.weekStart.first())
            assertTrue(reopenedSettings.showCompleted.first())
            assertEquals("CATEGORY", reopenedSettings.todoListMode.first())
            assertEquals(AppTheme.DARK, reopenedSettings.theme.first())
            assertTrue(reopenedSettings.notificationPermissionRequested.first())
            assertEquals(ArchiveSortOrder.OLDEST, reopenedSettings.archiveSortOrder.first())
        } finally {
            writingDatabase?.close()
            reopenedDatabase?.close()
            scopes.forEach { scope -> scope.coroutineContext[Job]?.cancelAndJoin() }
            assertTrue(context.deleteDatabase(databaseName) || !context.getDatabasePath(databaseName).exists())
            assertTrue(dataStoreFile.delete() || !dataStoreFile.exists())
        }
    }

    @Test
    fun dat006_failedCategoryReferenceUpdateRollsBackDefinitionAndHistory() = runBlocking {
        val category = categoryEntity("transaction-category", 0)
        val todo = todoEntity("transaction-todo", category.id, archivedAt = null)
        val execution = executionEntity(todo.id, 1)
        database.categoryDao().upsert(category)
        database.todoDao().upsert(todo)
        database.todoExecutionDao().insert(execution)
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_category_unlink BEFORE UPDATE OF categoryId ON todos " +
                "WHEN OLD.categoryId = '${category.id}' " +
                "BEGIN SELECT RAISE(ABORT, 'injected category reference failure'); END",
        )

        val result = categoryRepository.deleteCategory(category.id)

        assertTrue(result.isFailure)
        assertEquals(category, database.categoryDao().findById(category.id))
        assertEquals(category.id, database.todoDao().findById(todo.id)?.categoryId)
        assertEquals(listOf(execution), database.todoExecutionDao().findForTodo(todo.id))
    }

    @Test
    fun dat007_largeJoinedSearchAndAggregationLoadStableBoundedPages() = runBlocking {
        val recordCount = 120
        database.withTransaction {
            repeat(recordCount) { index ->
                val category = categoryEntity("category-${index.padded()}", index)
                val todo = todoEntity(
                    id = "todo-${index.padded()}",
                    categoryId = category.id,
                    archivedAt = 1_000L + index,
                    title = "検索対象 ${index.padded()}",
                    createdAt = index.toLong(),
                )
                database.categoryDao().upsert(category)
                database.todoDao().upsert(todo)
                database.todoExecutionDao().insert(executionEntity(todo.id, index))
            }
        }

        val source = database.todoDao().pageArchivedNewest("検索対象")
        val first = source.load(refreshParams()) as PagingSource.LoadResult.Page
        val second = source.load(appendParams(requireNotNull(first.nextKey))) as PagingSource.LoadResult.Page
        val third = source.load(appendParams(requireNotNull(second.nextKey))) as PagingSource.LoadResult.Page
        val rows = first.data + second.data + third.data

        assertEquals(listOf(50, 50, 20), listOf(first.data.size, second.data.size, third.data.size))
        assertNull(third.nextKey)
        assertEquals(recordCount, rows.size)
        assertEquals(recordCount, rows.map { it.todo.id }.distinct().size)
        assertEquals(
            (recordCount - 1 downTo 0).map { "todo-${it.padded()}" },
            rows.map { it.todo.id },
        )
        assertTrue(rows.all { it.categoryName?.startsWith("Category") == true })

        val summary = database.todoExecutionDao()
            .observeArchiveHistoryCount("todo-060")
            .first()
        assertEquals(1, summary.completedCount)
        assertEquals(0, summary.missedCount)
        assertEquals(0, summary.skippedCount)
        assertEquals(0, summary.periodResultCount)
    }

    private fun persistentDatabase(name: String): MataDatabase =
        Room.databaseBuilder(context, MataDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()

    private fun newDataStore(
        file: File,
        scopes: MutableList<CoroutineScope>,
    ): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun categoryEntity(id: String, sortOrder: Int) = CategoryEntity(
        id = id,
        name = "Category $sortOrder",
        normalizedName = "category-$sortOrder",
        colorIndex = sortOrder % 16,
        iconName = "Category",
        legacyEndHour = 0,
        sortOrder = sortOrder,
        createdAt = sortOrder.toLong(),
        updatedAt = sortOrder.toLong(),
    )

    private fun todoEntity(
        id: String,
        categoryId: String?,
        archivedAt: Long?,
        title: String = "Persistent TODO",
        createdAt: Long = 10,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(RecurrenceRule.daily())
        return TodoEntity(
            id = id,
            title = title,
            description = "private local description",
            categoryId = categoryId,
            startDate = "2026-09-05",
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = 720,
            definitionRevision = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = archivedAt,
        )
    }

    private fun executionEntity(todoId: String, index: Int) = TodoExecutionEntity(
        id = "execution-${index.padded()}",
        operationId = "operation-${index.padded()}",
        todoId = todoId,
        logicalDate = "2026-09-04",
        status = "completed",
        actedAt = index.toLong(),
        finalizedAt = index.toLong(),
        definitionRevision = 1,
        snapshotVersion = 1,
        snapshotJson = "{}",
    )

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

    private fun Int.padded(): String = toString().padStart(3, '0')

    private companion object {
        const val PAGE_SIZE = 50
        val TEST_CLOCK: Clock = Clock.fixed(
            Instant.parse("2026-09-05T03:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
    }
}

private class DataProtectionNotificationScheduler : NotificationScheduler {
    override val notificationCount = MutableStateFlow(0)

    override fun systemState() = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    )

    override suspend fun reconcileTodo(todoId: String) = Unit
    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) = Unit
}
