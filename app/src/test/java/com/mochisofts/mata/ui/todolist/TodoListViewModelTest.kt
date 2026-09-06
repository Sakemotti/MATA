package com.mochisofts.mata.ui.todolist

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadingTransitionsToEmptyThenRepositoryContent() = runTest {
        val repository = TodoListTestRepository()
        val viewModel = createViewModel(repository)

        assertTrue(viewModel.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.groups.isEmpty())

        val occurrence = occurrence("todo")
        repository.todos.value = listOf(occurrence.todo)
        repository.occurrences.value = listOf(occurrence)
        runCurrent()

        assertEquals(listOf("todo"), viewModel.uiState.value.groups.single().occurrences.map { it.todo.id })
    }

    @Test
    fun failedActionReportsErrorWithoutPublishingSuccess() = runTest {
        val repository = TodoListTestRepository().apply {
            completeResult = Result.failure(IllegalStateException("write failed"))
        }
        val viewModel = createViewModel(repository)
        val effect = async { viewModel.effects.first() }

        viewModel.complete(occurrence("todo"))
        runCurrent()

        assertEquals(
            TodoListEffect.Message(R.string.error_todo_complete_failed),
            effect.await(),
        )
        assertEquals(1, repository.completeCalls)
    }

    @Test
    fun concurrentActionsForSameTodoAreIgnoredUntilFirstFinishes() = runTest {
        val repository = TodoListTestRepository().apply {
            completeGate = CompletableDeferred()
        }
        val viewModel = createViewModel(repository)
        val target = occurrence("todo")

        viewModel.complete(target)
        runCurrent()
        viewModel.skip(target)
        viewModel.delete(target.todo.id)
        runCurrent()

        assertEquals(1, repository.completeCalls)
        assertEquals(0, repository.skipCalls)
        assertEquals(0, repository.deleteCalls)

        repository.completeGate?.complete(Unit)
        runCurrent()
        viewModel.skip(target)
        runCurrent()

        assertEquals(1, repository.skipCalls)
    }

    private fun createViewModel(repository: TodoListTestRepository) = TodoListViewModel(
        savedStateHandle = SavedStateHandle(),
        todoRepository = repository,
        holidayRepository = TodoListTestHolidayRepository(),
        settingsRepository = TodoListTestSettingsRepository(),
        clock = Clock.fixed(
            LocalDate.of(2026, 9, 6).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
            ZoneId.of("Asia/Tokyo"),
        ),
        adsConsentRepository = TodoListTestAdsRepository(),
    )

    private fun occurrence(id: String): TodoOccurrence {
        val date = LocalDate.of(2026, 9, 6)
        val todo = Todo(
            id = id,
            title = id,
            description = "",
            categoryId = null,
            startDate = date,
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = null,
            definitionRevision = 1,
            archivedAt = null,
            createdAt = 1,
        )
        return TodoOccurrence(todo, null, date, TodoState.PENDING)
    }
}

private class TodoListTestRepository : TodoRepository {
    val occurrences = MutableStateFlow<List<TodoOccurrence>>(emptyList())
    val todos = MutableStateFlow<List<Todo>>(emptyList())
    var completeResult: Result<Unit> = Result.success(Unit)
    var completeGate: CompletableDeferred<Unit>? = null
    var completeCalls = 0
    var skipCalls = 0
    var deleteCalls = 0

    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> = occurrences
    override fun observeTodos(): Flow<List<Todo>> = todos
    override suspend fun getTodo(id: String): Todo? = todos.value.firstOrNull { it.id == id }
    override suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        notifications: List<TodoNotification>,
    ): Result<String> = Result.success(id ?: "todo")

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
    ): Result<Unit> {
        completeCalls += 1
        completeGate?.await()
        return completeResult
    }

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
    ): Result<Unit> {
        skipCalls += 1
        return Result.success(Unit)
    }

    override suspend fun archiveTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun restoreTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTodo(id: String): Result<Unit> {
        deleteCalls += 1
        return Result.success(Unit)
    }
}

private class TodoListTestSettingsRepository : SettingsRepository {
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

private class TodoListTestHolidayRepository : HolidayRepository {
    override val snapshot = MutableStateFlow(HolidaySnapshot())
    override suspend fun currentSnapshot(): HolidaySnapshot = snapshot.value
    override suspend fun needsRefresh(): Boolean = false
    override suspend fun refresh() = HolidayRefreshResult(successful = true)
    override suspend fun pendingNotificationGeneration(): Long? = null
    override suspend fun markNotificationGenerationProcessed(generation: Long) = Unit
    override suspend fun pendingWidgetGeneration(): Long? = null
    override suspend fun markWidgetGenerationProcessed(generation: Long) = Unit
}

private class TodoListTestAdsRepository : AdsConsentRepository {
    override val state: StateFlow<AdsRuntimeState> = MutableStateFlow(AdsRuntimeState())
    override val events: Flow<AdsConsentEvent> = MutableSharedFlow()
    override fun gatherConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
