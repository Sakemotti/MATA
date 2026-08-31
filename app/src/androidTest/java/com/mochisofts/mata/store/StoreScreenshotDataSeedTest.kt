package com.mochisofts.mata.store

import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.repository.HistorySnapshotJson
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import dagger.hilt.android.EntryPointAccessors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreScreenshotDataSeedTest {
    @Test
    fun replaceDebugDataWithStoreScreenshotFixture() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Store screenshot data must never target a release application.", BuildConfig.DEBUG)
        assertTrue(context.packageName.endsWith(".debug"))

        val dependencies = EntryPointAccessors.fromApplication(
            context.applicationContext,
            StoreScreenshotSeedEntryPoint::class.java,
        )
        val database = dependencies.database()
        val scheduler = dependencies.notificationScheduler()

        database.scheduledNotificationDao().findTodoIds().forEach { todoId ->
            scheduler.cancelTodo(todoId)
        }
        database.clearAllTables()

        val today = LocalDate.now()
        val zoneId = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val categories = screenshotCategories(now)
        val todos = screenshotTodos(today, now)
        val notifications = screenshotNotifications(now)

        database.withTransaction {
            categories.forEach { database.categoryDao().insertBackup(it) }
            todos.forEach { database.todoDao().insertBackup(it) }
            notifications.forEach { database.todoNotificationDao().insertBackup(it) }
            screenshotExecutions(today, zoneId, todos, categories).forEach { execution ->
                database.todoExecutionDao().insert(execution)
            }
        }

        dependencies.settingsRepository().apply {
            setShowCompleted(false)
            setTodoListMode("DATE")
            setDayEndHour(0)
            setWeekStart(DayOfWeek.MONDAY)
            setTheme(AppTheme.LIGHT)
            setNotificationPermissionRequested(true)
            setArchiveSortOrder(ArchiveSortOrder.NEWEST)
        }
        scheduler.reconcileAll()
        dependencies.widgetUpdater().requestImmediateUpdate()

        assertEquals(3, database.categoryDao().backupCount())
        assertEquals(6, database.todoDao().backupCount())
        assertEquals(2, database.todoNotificationDao().count())
        assertEquals(3, database.todoExecutionDao().backupCount())
    }

    private fun screenshotCategories(now: Long) = listOf(
        CategoryEntity(
            id = CATEGORY_DAILY,
            name = "日常",
            normalizedName = "日常",
            colorIndex = 6,
            iconName = "Home",
            sortOrder = 0,
            createdAt = now - 60_000,
        ),
        CategoryEntity(
            id = CATEGORY_HOUSEWORK,
            name = "家事",
            normalizedName = "家事",
            colorIndex = 8,
            iconName = "ShoppingCart",
            sortOrder = 1,
            createdAt = now - 59_000,
        ),
        CategoryEntity(
            id = CATEGORY_GAME,
            name = "ゲーム",
            normalizedName = "ゲーム",
            colorIndex = 2,
            iconName = "SportsEsports",
            sortOrder = 2,
            createdAt = now - 58_000,
        ),
    )

    private fun screenshotTodos(today: LocalDate, now: Long): List<TodoEntity> {
        val nthWeekdayRule = RecurrenceRule(
            type = RecurrenceType.MONTHLY_NTH_WEEKDAYS,
            monthlyNthWeekdays = setOf(
                MonthlyNthWeekday(
                    ordinal = (today.dayOfMonth - 1) / 7 + 1,
                    dayOfWeek = today.dayOfWeek,
                ),
            ),
        )
        return listOf(
            todo(
                id = TODO_AIR,
                title = "部屋の換気",
                description = "朝の空気を入れ替える",
                categoryId = CATEGORY_DAILY,
                startDate = today,
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = 8 * 60,
                createdAt = now - 50_000,
            ),
            todo(
                id = TODO_TRASH,
                title = "ゴミ出し",
                description = "収集時間までに出す",
                categoryId = CATEGORY_HOUSEWORK,
                startDate = today,
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = 8 * 60 + 30,
                createdAt = now - 49_000,
            ),
            todo(
                id = TODO_MONTHLY_REVIEW,
                title = "月次振り返り",
                description = "今月のルーチンを見直す",
                categoryId = CATEGORY_DAILY,
                startDate = today,
                recurrenceRule = nthWeekdayRule,
                dueMinutes = 20 * 60,
                createdAt = now - 48_000,
            ),
            todo(
                id = TODO_DAILY_MISSION,
                title = "デイリーミッション",
                description = "今日のミッションを確認する",
                categoryId = CATEGORY_GAME,
                startDate = today,
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = 22 * 60,
                createdAt = now - 47_000,
            ),
            todo(
                id = TODO_WEEKLY_CHALLENGE,
                title = "週次チャレンジ",
                description = "達成状況をカレンダーで確認する",
                categoryId = CATEGORY_GAME,
                startDate = today.minusDays(14),
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = 21 * 60,
                createdAt = now - 46_000,
                archivedAt = now - 3_600_000,
            ),
            todo(
                id = TODO_KITCHEN,
                title = "キッチンの整理",
                description = "作業台と収納を整える",
                categoryId = CATEGORY_HOUSEWORK,
                startDate = today.minusDays(10),
                recurrenceRule = RecurrenceRule.once(),
                dueMinutes = null,
                createdAt = now - 45_000,
                archivedAt = now - 7_200_000,
            ),
        )
    }

    private fun todo(
        id: String,
        title: String,
        description: String,
        categoryId: String,
        startDate: LocalDate,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        createdAt: Long,
        archivedAt: Long? = null,
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(recurrenceRule)
        return TodoEntity(
            id = id,
            title = title,
            description = description,
            categoryId = categoryId,
            startDate = startDate.toString(),
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = dueMinutes,
            definitionRevision = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = archivedAt,
        )
    }

    private fun screenshotNotifications(now: Long) = listOf(
        TodoNotificationEntity(
            id = NOTIFICATION_BEFORE,
            todoId = TODO_MONTHLY_REVIEW,
            relation = "before",
            amount = 1,
            unit = "hour",
            sortOrder = 0,
            createdAt = now - 40_000,
            updatedAt = now - 40_000,
        ),
        TodoNotificationEntity(
            id = NOTIFICATION_AT,
            todoId = TODO_MONTHLY_REVIEW,
            relation = "at",
            amount = 0,
            unit = "minute",
            sortOrder = 1,
            createdAt = now - 39_000,
            updatedAt = now - 39_000,
        ),
    )

    private fun screenshotExecutions(
        today: LocalDate,
        zoneId: ZoneId,
        todos: List<TodoEntity>,
        categories: List<CategoryEntity>,
    ): List<TodoExecutionEntity> {
        val todo = todos.single { it.id == TODO_WEEKLY_CHALLENGE }
        val category = categories.single { it.id == CATEGORY_GAME }
        return listOf("completed", "skipped", "missed").mapIndexed { index, status ->
            val logicalDate = today.minusDays((3 - index).toLong())
            val actionTime = logicalDate.atTime(LocalTime.of(20, index * 10))
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            TodoExecutionEntity(
                id = EXECUTION_IDS[index],
                operationId = OPERATION_IDS[index],
                todoId = todo.id,
                logicalDate = logicalDate.toString(),
                status = status,
                actedAt = actionTime.takeUnless { status == "missed" },
                finalizedAt = actionTime,
                definitionRevision = 1,
                snapshotVersion = 1,
                snapshotJson = HistorySnapshotJson.encode(
                    todo = todo,
                    category = category,
                    notifications = emptyList(),
                    endHour = 0,
                    weekStart = DayOfWeek.MONDAY,
                    logicalDate = logicalDate,
                ),
            )
        }
    }

    private companion object {
        const val CATEGORY_DAILY = "10000000-0000-0000-0000-000000000001"
        const val CATEGORY_HOUSEWORK = "10000000-0000-0000-0000-000000000002"
        const val CATEGORY_GAME = "10000000-0000-0000-0000-000000000003"
        const val TODO_AIR = "20000000-0000-0000-0000-000000000001"
        const val TODO_TRASH = "20000000-0000-0000-0000-000000000002"
        const val TODO_MONTHLY_REVIEW = "20000000-0000-0000-0000-000000000003"
        const val TODO_DAILY_MISSION = "20000000-0000-0000-0000-000000000004"
        const val TODO_WEEKLY_CHALLENGE = "20000000-0000-0000-0000-000000000005"
        const val TODO_KITCHEN = "20000000-0000-0000-0000-000000000006"
        const val NOTIFICATION_BEFORE = "30000000-0000-0000-0000-000000000001"
        const val NOTIFICATION_AT = "30000000-0000-0000-0000-000000000002"
        val EXECUTION_IDS = listOf(
            "40000000-0000-0000-0000-000000000001",
            "40000000-0000-0000-0000-000000000002",
            "40000000-0000-0000-0000-000000000003",
        )
        val OPERATION_IDS = listOf(
            "50000000-0000-0000-0000-000000000001",
            "50000000-0000-0000-0000-000000000002",
            "50000000-0000-0000-0000-000000000003",
        )
    }
}
