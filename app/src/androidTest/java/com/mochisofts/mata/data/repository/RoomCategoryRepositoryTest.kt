package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.core.common.ValidationError
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.core.observability.DiagnosticLogger
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCategoryRepositoryTest {
    private lateinit var database: MataDatabase
    private lateinit var repository: RoomCategoryRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCategoryRepository(
            database = database,
            categoryDao = database.categoryDao(),
            clock = Clock.fixed(
                Instant.parse("2026-08-17T00:00:00Z"),
                ZoneId.of("Asia/Tokyo"),
            ),
            notificationScheduler = CategoryTestNotificationScheduler(),
            widgetUpdater = WidgetUpdater(context, DiagnosticLogger()),
        )
        listOf("a", "b", "c").forEachIndexed { index, id ->
            database.categoryDao().upsert(category(id, index))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun reorderCategories_reassignsUniqueSortOrdersAtomically() = runBlocking {
        val result = repository.reorderCategories(listOf("c", "a", "b"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("c", "a", "b"), database.categoryDao().findAll().map(CategoryEntity::id))
        assertEquals(listOf(0, 1, 2), database.categoryDao().findAll().map(CategoryEntity::sortOrder))
    }

    @Test
    fun reorderCategories_withStaleIdsPreservesExistingOrder() = runBlocking {
        val result = repository.reorderCategories(listOf("c", "a"))

        assertTrue(result.isFailure)
        assertEquals(listOf("a", "b", "c"), database.categoryDao().findAll().map(CategoryEntity::id))
        assertEquals(listOf(0, 1, 2), database.categoryDao().findAll().map(CategoryEntity::sortOrder))
    }

    @Test
    fun cm012_nameValidationRejectsBlankTooLongAndNormalizedDuplicate() = runBlocking {
        assertValidationError(ValidationError.CATEGORY_NAME_REQUIRED) {
            repository.saveCategory(null, "   ", 0, "Category")
        }

        val thirtyCharacters = "あ".repeat(30)
        val savedId = repository.saveCategory(null, thirtyCharacters, 0, "Category").getOrThrow()
        assertEquals(thirtyCharacters, database.categoryDao().findById(savedId)?.name)

        assertValidationError(ValidationError.CATEGORY_NAME_TOO_LONG) {
            repository.saveCategory(null, "あ".repeat(31), 0, "Category")
        }
        assertValidationError(ValidationError.CATEGORY_NAME_DUPLICATE) {
            repository.saveCategory(null, "Ａ", 0, "Category")
        }
    }

    @Test
    fun cm013_saveTrimsOnlyOuterWhitespaceAndPreservesUserNotation() = runBlocking {
        val id = repository.saveCategory(
            id = null,
            name = "  ＡＢ  C  ",
            colorIndex = 3,
            iconName = "Home",
        ).getOrThrow()

        val saved = requireNotNull(database.categoryDao().findById(id))
        assertEquals("ＡＢ  C", saved.name)
        assertEquals("ab  c", saved.normalizedName)
        assertEquals(3, saved.colorIndex)
        assertEquals("Home", saved.iconName)
    }

    @Test
    fun cm021_editUpdatesCurrentReferencesButPreservesFinalizedHistorySnapshot() = runBlocking {
        val originalCategory = requireNotNull(database.categoryDao().findById("a"))
        val currentTodo = todo("current", originalCategory.id, startDate = "2026-08-01")
        val futureTodo = todo("future", originalCategory.id, startDate = "2026-09-01")
        database.todoDao().upsert(currentTodo)
        database.todoDao().upsert(futureTodo)
        insertExecution("current-history", currentTodo, originalCategory)

        repository.saveCategory(
            id = originalCategory.id,
            name = "Updated category",
            colorIndex = 7,
            iconName = "SportsEsports",
        ).getOrThrow()

        val updated = requireNotNull(database.categoryDao().findById(originalCategory.id))
        assertEquals("Updated category", updated.name)
        assertEquals(7, updated.colorIndex)
        assertEquals("SportsEsports", updated.iconName)
        assertEquals(originalCategory.id, database.todoDao().findById(currentTodo.id)?.categoryId)
        assertEquals(originalCategory.id, database.todoDao().findById(futureTodo.id)?.categoryId)

        val snapshot = HistorySnapshotJson.decode(
            requireNotNull(database.todoExecutionDao().findById("current-history")).snapshotJson,
        )
        assertEquals(originalCategory.name, snapshot?.categoryName)
        assertEquals(originalCategory.colorIndex, snapshot?.categoryColorIndex)
        assertEquals(originalCategory.iconName, snapshot?.categoryIconName)
    }

    @Test
    fun cm023_deleteMovesActiveAndArchivedTodosButPreservesHistorySnapshot() = runBlocking {
        val originalCategory = requireNotNull(database.categoryDao().findById("b"))
        val activeTodo = todo("active", originalCategory.id, startDate = "2026-08-01")
        val archivedTodo = todo(
            id = "archived",
            categoryId = originalCategory.id,
            startDate = "2026-07-01",
            archivedAt = 50,
        )
        database.todoDao().upsert(activeTodo)
        database.todoDao().upsert(archivedTodo)
        insertExecution("active-history", activeTodo, originalCategory)

        repository.deleteCategory(originalCategory.id).getOrThrow()

        assertEquals(null, database.todoDao().findById(activeTodo.id)?.categoryId)
        assertEquals(null, database.todoDao().findById(archivedTodo.id)?.categoryId)
        val snapshot = HistorySnapshotJson.decode(
            requireNotNull(database.todoExecutionDao().findById("active-history")).snapshotJson,
        )
        assertEquals(originalCategory.id, snapshot?.categoryId)
        assertEquals(originalCategory.name, snapshot?.categoryName)
        assertEquals(originalCategory.colorIndex, snapshot?.categoryColorIndex)
        assertEquals(originalCategory.iconName, snapshot?.categoryIconName)
    }

    private suspend fun assertValidationError(
        expected: ValidationError,
        operation: suspend () -> Result<*>,
    ) {
        val failure = operation().exceptionOrNull()
        assertTrue(failure is ValidationException)
        assertEquals(expected, (failure as ValidationException).error)
    }

    private fun todo(
        id: String,
        categoryId: String?,
        startDate: String,
        archivedAt: Long? = null,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(RecurrenceRule.daily())
        return TodoEntity(
            id = id,
            title = id,
            description = "",
            categoryId = categoryId,
            startDate = startDate,
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = null,
            definitionRevision = 1,
            createdAt = 10,
            updatedAt = 10,
            archivedAt = archivedAt,
        )
    }

    private suspend fun insertExecution(
        id: String,
        todo: TodoEntity,
        category: CategoryEntity,
    ) {
        val logicalDate = LocalDate.of(2026, 8, 16)
        database.todoExecutionDao().insert(
            TodoExecutionEntity(
                id = id,
                operationId = "operation-$id",
                todoId = todo.id,
                logicalDate = logicalDate.toString(),
                status = "completed",
                actedAt = 20,
                finalizedAt = 20,
                definitionRevision = todo.definitionRevision,
                snapshotVersion = 1,
                snapshotJson = HistorySnapshotJson.encode(
                    todo = todo,
                    category = category,
                    notifications = emptyList(),
                    endHour = 0,
                    weekStart = DayOfWeek.MONDAY,
                    logicalDate = logicalDate,
                ),
            ),
        )
    }

    private fun category(id: String, sortOrder: Int) = CategoryEntity(
        id = id,
        name = id.uppercase(),
        normalizedName = id,
        colorIndex = 0,
        iconName = "Category",
        legacyEndHour = 0,
        sortOrder = sortOrder,
        createdAt = 1L,
        updatedAt = 1L,
    )
}

private class CategoryTestNotificationScheduler : NotificationScheduler {
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
