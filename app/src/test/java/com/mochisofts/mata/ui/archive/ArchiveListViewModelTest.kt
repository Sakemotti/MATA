package com.mochisofts.mata.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.repository.ArchiveRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ArchiveListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun navigationResultArrivingAfterCreation_isEmittedAndConsumed() = runTest {
        val handle = SavedStateHandle()
        val viewModel = ArchiveListViewModel(
            savedStateHandle = handle,
            repository = EmptyArchiveRepository(),
            settingsRepository = ArchiveViewModelSettingsRepository(),
        )
        val effect = async { viewModel.effects.first() }
        runCurrent()

        handle[ARCHIVE_RESULT_KEY] = 123
        runCurrent()

        assertEquals(ArchiveListEffect.Message(123), effect.await())
        assertEquals(null, handle.get<Int>(ARCHIVE_RESULT_KEY))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun selectedTodo_isLoadedAndRemovedWhenSearchExcludesIt() = runTest {
        val item = archivedTodo("todo", "毎日の確認")
        val repository = SelectionArchiveRepository(item)
        val handle = SavedStateHandle()
        val viewModel = ArchiveListViewModel(
            savedStateHandle = handle,
            repository = repository,
            settingsRepository = ArchiveViewModelSettingsRepository(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        viewModel.openDetail(item.todo.id)
        runCurrent()

        assertEquals(item.todo.id, viewModel.uiState.value.selectedTodoId)
        assertEquals(item, viewModel.uiState.value.selectedItem)
        assertEquals(item.todo.id, handle.get<String>("archive_selected_todo_id"))

        viewModel.updateSearchQuery("一致しない文字")
        advanceTimeBy(301)
        runCurrent()

        assertNull(viewModel.uiState.value.selectedTodoId)
        assertNull(handle.get<String>("archive_selected_todo_id"))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun successfulRestore_clearsSelectedTodo() = runTest {
        val item = archivedTodo("todo", "復元対象")
        val repository = SelectionArchiveRepository(item)
        val viewModel = ArchiveListViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            settingsRepository = ArchiveViewModelSettingsRepository(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.openDetail(item.todo.id)
        runCurrent()

        viewModel.requestAction(item.todo.id, ArchiveAction.RESTORE)
        runCurrent()
        viewModel.confirmAction()
        runCurrent()

        assertEquals(item.todo.id, repository.restoredTodoId)
        assertNull(viewModel.uiState.value.selectedTodoId)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun selectedTodoRemovedExternally_clearsSelectionAndReportsNotFound() = runTest {
        val item = archivedTodo("todo", "外部削除対象")
        val repository = SelectionArchiveRepository(item)
        val viewModel = ArchiveListViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            settingsRepository = ArchiveViewModelSettingsRepository(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.openDetail(item.todo.id)
        runCurrent()

        repository.removeExternally()
        runCurrent()

        assertNull(viewModel.uiState.value.selectedTodoId)
        assertEquals(
            ArchiveListEffect.Message(R.string.error_todo_not_found),
            viewModel.effects.first(),
        )
    }

    private fun archivedTodo(id: String, title: String) = ArchivedTodoItem(
        todo = Todo(
            id = id,
            title = title,
            description = "説明",
            categoryId = null,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = null,
            definitionRevision = 1,
            archivedAt = 1_000L,
            createdAt = 100L,
        ),
        category = null,
    )
}

private class SelectionArchiveRepository(item: ArchivedTodoItem) : ArchiveRepository {
    private val selectedItem = MutableStateFlow<ArchivedTodoItem?>(item)
    var restoredTodoId: String? = null

    fun removeExternally() {
        selectedItem.value = null
    }

    override fun pagedTodos(
        query: String,
        sortOrder: ArchiveSortOrder,
    ): Flow<PagingData<ArchivedTodoItem>> = flowOf(PagingData.from(listOfNotNull(selectedItem.value)))

    override fun observeTodo(todoId: String): Flow<ArchivedTodoItem?> = selectedItem

    override fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary> = flowOf(
        ArchiveHistorySummary(0, 0, 0, 0),
    )

    override fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>> =
        flowOf(PagingData.empty())

    override suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview> {
        val item = requireNotNull(selectedItem.value)
        return Result.success(
            ArchiveActionPreview(
                todoId = todoId,
                title = item.todo.title,
                hasFutureOccurrence = true,
                notificationSettingCount = 0,
                unavailableNotificationCount = 0,
                historySummary = ArchiveHistorySummary(0, 0, 0, 0),
            ),
        )
    }

    override suspend fun restore(todoId: String): Result<Unit> {
        restoredTodoId = todoId
        return Result.success(Unit)
    }

    override suspend fun deletePermanently(todoId: String): Result<Unit> = Result.success(Unit)
}

private class EmptyArchiveRepository : ArchiveRepository {
    override fun pagedTodos(
        query: String,
        sortOrder: ArchiveSortOrder,
    ): Flow<PagingData<ArchivedTodoItem>> = flowOf(PagingData.empty())

    override fun observeTodo(todoId: String): Flow<ArchivedTodoItem?> = flowOf(null)
    override fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary> = emptyFlow()
    override fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>> =
        flowOf(PagingData.empty())
    override suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview> =
        Result.failure(IllegalStateException())
    override suspend fun restore(todoId: String) = Result.success(Unit)
    override suspend fun deletePermanently(todoId: String) = Result.success(Unit)
}

private class ArchiveViewModelSettingsRepository : SettingsRepository {
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
