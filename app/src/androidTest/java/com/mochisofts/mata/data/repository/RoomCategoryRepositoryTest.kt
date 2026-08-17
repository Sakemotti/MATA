package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import java.time.Clock
import java.time.Instant
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
            widgetUpdater = WidgetUpdater(context),
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

    private fun category(id: String, sortOrder: Int) = CategoryEntity(
        id = id,
        name = id.uppercase(),
        normalizedName = id,
        colorIndex = 0,
        iconName = "Category",
        endHour = 0,
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
