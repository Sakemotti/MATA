package com.mochisofts.mata.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.repository.ArchiveRepository
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ArchiveDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = ArchiveDetailMainDispatcherRule()

    @Test
    fun loadingTransitionsToArchivedTodoAndEmptyHistorySummary() = runTest {
        val item = archivedTodo("todo")
        val viewModel = ArchiveDetailViewModel(
            SavedStateHandle(mapOf("todoId" to item.todo.id)),
            DetailArchiveRepository(item),
        )

        assertTrue(viewModel.uiState.value.isLoading)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(item, viewModel.uiState.value.item)
        assertEquals(0, requireNotNull(viewModel.uiState.value.summary).totalCount)
        assertNull(viewModel.uiState.value.loadErrorRes)
    }

    @Test
    fun contentLoadFailureFinishesLoadingWithNotFoundState() = runTest {
        val viewModel = ArchiveDetailViewModel(
            SavedStateHandle(mapOf("todoId" to "todo")),
            FailingDetailArchiveRepository(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.item)
        assertEquals(R.string.error_todo_not_found, viewModel.uiState.value.loadErrorRes)
    }

    @Test
    fun previewFailureAllowsRetryAndBlocksDuplicateRequest() = runTest {
        val repository = DetailArchiveRepository(archivedTodo("todo")).apply {
            previewGate = CompletableDeferred()
            previewResult = Result.failure(IllegalStateException("preview failed"))
        }
        val viewModel = ArchiveDetailViewModel(
            SavedStateHandle(mapOf("todoId" to "todo")),
            repository,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val effect = async { viewModel.effects.first() }

        viewModel.requestAction(ArchiveAction.RESTORE)
        runCurrent()
        viewModel.requestAction(ArchiveAction.DELETE)
        assertEquals(1, repository.previewCalls)
        assertTrue(viewModel.uiState.value.isLoadingPreview)

        repository.previewGate?.complete(Unit)
        runCurrent()
        assertEquals(ArchiveDetailEffect.Message(R.string.archive_preview_load_error), effect.await())
        assertFalse(viewModel.uiState.value.isLoadingPreview)

        repository.previewResult = null
        viewModel.requestAction(ArchiveAction.RESTORE)
        runCurrent()

        assertEquals(2, repository.previewCalls)
        assertEquals("todo", viewModel.uiState.value.preview?.todoId)
    }

    @Test
    fun confirmBlocksDuplicateRestoreUntilRepositoryCompletes() = runTest {
        val repository = DetailArchiveRepository(archivedTodo("todo")).apply {
            restoreGate = CompletableDeferred()
        }
        val viewModel = ArchiveDetailViewModel(
            SavedStateHandle(mapOf("todoId" to "todo")),
            repository,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.requestAction(ArchiveAction.RESTORE)
        runCurrent()
        val effect = async { viewModel.effects.first() }

        viewModel.confirmAction()
        runCurrent()
        viewModel.confirmAction()
        assertEquals(1, repository.restoreCalls)
        assertTrue(viewModel.uiState.value.isRunningAction)

        repository.restoreGate?.complete(Unit)
        runCurrent()

        assertEquals(
            ArchiveDetailEffect.Finished(R.string.archive_restore_success),
            effect.await(),
        )
    }

    private fun archivedTodo(id: String) = ArchivedTodoItem(
        todo = Todo(
            id = id,
            title = "アーカイブ済み",
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

private class DetailArchiveRepository(item: ArchivedTodoItem) : ArchiveRepository {
    private val selectedItem = MutableStateFlow<ArchivedTodoItem?>(item)
    var previewGate: CompletableDeferred<Unit>? = null
    var previewResult: Result<ArchiveActionPreview>? = null
    var restoreGate: CompletableDeferred<Unit>? = null
    var previewCalls = 0
    var restoreCalls = 0

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
        previewCalls += 1
        previewGate?.await()
        previewResult?.let { return it }
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
        restoreCalls += 1
        restoreGate?.await()
        return Result.success(Unit)
    }

    override suspend fun deletePermanently(todoId: String): Result<Unit> = Result.success(Unit)
}

private class FailingDetailArchiveRepository : ArchiveRepository {
    override fun pagedTodos(
        query: String,
        sortOrder: ArchiveSortOrder,
    ): Flow<PagingData<ArchivedTodoItem>> = flowOf(PagingData.empty())
    override fun observeTodo(todoId: String): Flow<ArchivedTodoItem?> = flow { error("load failed") }
    override fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary> = flowOf(
        ArchiveHistorySummary(0, 0, 0, 0),
    )
    override fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>> =
        flowOf(PagingData.empty())
    override suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview> =
        Result.failure(IllegalStateException())
    override suspend fun restore(todoId: String): Result<Unit> = Result.success(Unit)
    override suspend fun deletePermanently(todoId: String): Result<Unit> = Result.success(Unit)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveDetailMainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
