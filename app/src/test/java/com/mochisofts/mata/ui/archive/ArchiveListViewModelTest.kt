package com.mochisofts.mata.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.repository.ArchiveRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    override val uncategorizedEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override val archiveSortOrder = MutableStateFlow(ArchiveSortOrder.NEWEST)
    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setUncategorizedEndHour(value: Int) { uncategorizedEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
    override suspend fun setArchiveSortOrder(value: ArchiveSortOrder) { archiveSortOrder.value = value }
}
