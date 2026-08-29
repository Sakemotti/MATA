package com.mochisofts.mata.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
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

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED_BENCHMARK_DATA) return
        runBlocking(Dispatchers.IO) {
            settingsRepository.setDayEndHour(0)
            seedCategories()
            seedTodos(LocalDate.now(clock))
            settingsRepository.setShowCompleted(false)
            settingsRepository.setTodoListMode(TODO_LIST_MODE_DATE)
        }
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

    private fun categoryId(index: Int): String =
        "10000000-0000-0000-0000-${String.format(Locale.ROOT, "%012d", index + 1)}"

    private fun todoId(index: Int): String =
        "00000000-0000-0000-0000-${String.format(Locale.ROOT, "%012d", index + 1)}"

    companion object {
        private const val ACTION_SEED_BENCHMARK_DATA =
            "com.mochisofts.mata.action.SEED_BENCHMARK_DATA"
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
    }
}
