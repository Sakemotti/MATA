package com.mochisofts.mata.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.repository.HistorySnapshotJson
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.RecurrenceDayFilter
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class BenchmarkDataReceiver : BroadcastReceiver() {
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clock: Clock
    @Inject lateinit var categoryDao: CategoryDao
    @Inject lateinit var todoDao: TodoDao
    @Inject lateinit var executionDao: TodoExecutionDao
    @Inject lateinit var notificationDao: TodoNotificationDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        runBlocking(Dispatchers.IO) {
            when (intent.action) {
                ACTION_SEED_BENCHMARK_DATA -> seedBenchmarkData()
                ACTION_SEED_STORE_SCREENSHOT_DATA -> seedStoreScreenshotData(LocalDate.now(clock))
            }
        }
    }

    private suspend fun seedBenchmarkData() {
        seedCategories()
        seedTodos(LocalDate.now(clock))
        settingsRepository.setDayEndHour(0)
        settingsRepository.setShowCompleted(false)
        settingsRepository.setTodoListMode(TODO_LIST_MODE_DATE)
    }

    private suspend fun seedCategories() {
        CATEGORY_ICONS.forEachIndexed { index, icon ->
            categoryRepository.saveCategory(
                id = categoryId(index),
                name = "Benchmark Category ${index + 1}",
                colorIndex = index,
                iconName = icon,
            ).getOrThrow()
        }
    }

    private suspend fun seedTodos(today: LocalDate) {
        repeat(TODO_COUNT) { index ->
            todoRepository.saveTodo(
                id = todoId(index),
                title = "Benchmark TODO ${String.format(Locale.ROOT, "%03d", index + 1)}",
                description = "Fixed performance-test data",
                categoryId = categoryId(index % CATEGORY_ICONS.size),
                startDate = today,
                endDate = null,
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = index * MINUTES_PER_DAY / TODO_COUNT,
            ).getOrThrow()
        }
    }

    private suspend fun seedStoreScreenshotData(today: LocalDate) {
        STORE_CATEGORIES.forEach { category ->
            categoryRepository.saveCategory(
                id = category.id,
                name = category.name,
                colorIndex = category.colorIndex,
                iconName = category.iconName,
            ).getOrThrow()
        }

        val activeTodos = listOf(
            StoreTodo(
                id = storeTodoId(1),
                title = "部屋の換気",
                description = "朝の空気を入れ替える",
                categoryId = storeCategoryId(1),
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = 8 * 60,
            ),
            StoreTodo(
                id = storeTodoId(2),
                title = "週次レビュー",
                description = "今月の予定と達成状況を振り返る",
                categoryId = storeCategoryId(1),
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.MONTHLY_NTH_WEEKDAYS,
                    monthlyNthWeekdays = setOf(
                        MonthlyNthWeekday(1, DayOfWeek.MONDAY),
                        MonthlyNthWeekday(3, DayOfWeek.FRIDAY),
                    ),
                ),
                dueMinutes = 20 * 60,
            ),
            StoreTodo(
                id = storeTodoId(3),
                title = "ゴミ出し",
                description = "指定曜日の朝に忘れず出す",
                categoryId = storeCategoryId(2),
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.SELECTED_WEEKDAYS,
                    selectedWeekdays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
                    dayFilter = RecurrenceDayFilter.CUSTOM,
                ),
                dueMinutes = 7 * 60 + 30,
            ),
            StoreTodo(
                id = storeTodoId(4),
                title = "キッチンの整理",
                description = "調理台とシンクを片付ける",
                categoryId = storeCategoryId(2),
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.SELECTED_WEEKDAYS,
                    selectedWeekdays = setOf(DayOfWeek.SATURDAY),
                    dayFilter = RecurrenceDayFilter.CUSTOM,
                ),
                dueMinutes = 10 * 60,
            ),
            StoreTodo(
                id = storeTodoId(5),
                title = "デイリーミッション",
                description = "毎日の報酬を受け取る",
                categoryId = storeCategoryId(3),
                recurrenceRule = RecurrenceRule.daily(),
                dueMinutes = 21 * 60,
            ),
            StoreTodo(
                id = storeTodoId(6),
                title = "週次チャレンジ",
                description = "週に3回チャレンジを進める",
                categoryId = storeCategoryId(3),
                recurrenceRule = RecurrenceRule(
                    type = RecurrenceType.WEEKLY_COUNT,
                    requiredCount = 3,
                ),
                dueMinutes = 23 * 60,
            ),
        )
        activeTodos.forEach { todo -> saveStoreTodo(todo, today.minusMonths(2)) }

        seedPastAction(storeTodoId(1), today.minusDays(1), TodoState.COMPLETED)
        seedPastAction(storeTodoId(5), today.minusDays(1), TodoState.SKIPPED)
        seedPastAction(storeTodoId(1), today.minusDays(2), TodoState.COMPLETED)

        val archivedTodo = StoreTodo(
            id = storeTodoId(7),
            title = "以前の朝ルーチン",
            description = "過去に使っていたルーチン",
            categoryId = storeCategoryId(1),
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = 6 * 60 + 30,
        )
        saveStoreTodo(archivedTodo, today.minusMonths(3))
        seedPastAction(archivedTodo.id, today.minusDays(3), TodoState.COMPLETED)
        todoRepository.archiveTodo(archivedTodo.id).getOrThrow()

        settingsRepository.setDayEndHour(0)
        settingsRepository.setShowCompleted(false)
        settingsRepository.setTodoListMode(TODO_LIST_MODE_DATE)
        settingsRepository.setWeekStart(DayOfWeek.MONDAY)
        settingsRepository.setTheme(AppTheme.LIGHT)
    }

    private suspend fun saveStoreTodo(todo: StoreTodo, startDate: LocalDate) {
        todoRepository.saveTodo(
            id = todo.id,
            title = todo.title,
            description = todo.description,
            categoryId = todo.categoryId,
            startDate = startDate,
            endDate = null,
            recurrenceRule = todo.recurrenceRule,
            dueMinutes = todo.dueMinutes,
            notifications = listOf(
                TodoNotification(
                    id = "${todo.id}-before",
                    relation = NotificationRelation.BEFORE,
                    amount = 15,
                    unit = NotificationUnit.MINUTE,
                ),
            ),
        ).getOrThrow()
    }

    private suspend fun seedPastAction(todoId: String, logicalDate: LocalDate, state: TodoState) {
        if (executionDao.find(todoId, logicalDate.toString()) != null) return
        val todo = todoDao.findById(todoId) ?: error("Store screenshot TODO not found: $todoId")
        val category = todo.categoryId?.let { categoryDao.findById(it) }
        val timestamp = clock.millis()
        executionDao.insert(
            TodoExecutionEntity(
                id = storeExecutionId(todoId, logicalDate),
                operationId = storeOperationId(todoId, logicalDate),
                todoId = todoId,
                logicalDate = logicalDate.toString(),
                status = state.code,
                actedAt = timestamp,
                finalizedAt = timestamp,
                definitionRevision = todo.definitionRevision,
                snapshotVersion = 1,
                snapshotJson = HistorySnapshotJson.encode(
                    todo = todo,
                    category = category,
                    notifications = notificationDao.findForTodo(todoId),
                    endHour = 0,
                    weekStart = DayOfWeek.MONDAY,
                    logicalDate = logicalDate,
                ),
            ),
        )
    }

    private fun categoryId(index: Int): String =
        "10000000-0000-0000-0000-${String.format(Locale.ROOT, "%012d", index + 1)}"

    private fun todoId(index: Int): String =
        "00000000-0000-0000-0000-${String.format(Locale.ROOT, "%012d", index + 1)}"

    companion object {
        private const val ACTION_SEED_BENCHMARK_DATA =
            "com.mochisofts.mata.action.SEED_BENCHMARK_DATA"
        private const val ACTION_SEED_STORE_SCREENSHOT_DATA =
            "com.mochisofts.mata.action.SEED_STORE_SCREENSHOT_DATA"
        private const val TODO_LIST_MODE_DATE = "DATE"
        private const val TODO_COUNT = 100
        private const val MINUTES_PER_DAY = 1_440
        private val CATEGORY_ICONS = listOf(
            "Home",
            "Work",
            "FitnessCenter",
            "MenuBook",
            "SportsEsports",
        )

        private val SUPPORTED_ACTIONS = setOf(
            ACTION_SEED_BENCHMARK_DATA,
            ACTION_SEED_STORE_SCREENSHOT_DATA,
        )

        private val STORE_CATEGORIES = listOf(
            StoreCategory(storeCategoryId(1), "日常", 8, "Home"),
            StoreCategory(storeCategoryId(2), "家事", 12, "Restaurant"),
            StoreCategory(storeCategoryId(3), "ゲーム", 4, "SportsEsports"),
        )

        private fun storeCategoryId(index: Int): String =
            "20000000-0000-0000-0000-${String.format(Locale.ROOT, "%012d", index)}"

        private fun storeTodoId(index: Int): String =
            "30000000-0000-0000-0000-${String.format(Locale.ROOT, "%012d", index)}"

        private fun storeExecutionId(todoId: String, date: LocalDate): String =
            "store-execution-$todoId-$date"

        private fun storeOperationId(todoId: String, date: LocalDate): String =
            "store-operation-$todoId-$date"
    }
}

private data class StoreCategory(
    val id: String,
    val name: String,
    val colorIndex: Int,
    val iconName: String,
)

private data class StoreTodo(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val recurrenceRule: RecurrenceRule,
    val dueMinutes: Int,
)
