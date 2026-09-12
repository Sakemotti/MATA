package com.mochisofts.mata.ui.archive

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.ArchiveRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ArchiveListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun at006_emptyAndSearchEmptyStatesAreDistinctAndSearchCanBeCleared() {
        val repository = TestArchiveRepository()
        setScreen(repository)
        waitForText(text(R.string.archive_empty))

        composeRule.onNodeWithContentDescription(text(R.string.content_description_archive_search))
            .performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("該当しない検索語")

        waitForText(text(R.string.archive_search_empty))
        composeRule.onNodeWithText(text(R.string.archive_clear_search)).performClick()

        waitForText(text(R.string.archive_empty))
        composeRule.onNodeWithText(text(R.string.archive_search_empty)).assertDoesNotExist()
    }

    @Test
    fun at008_rowOpensReadOnlyFullScreenDetailWithBackNavigation() {
        val item = archivedTodo(title = "詳細を開くTODO")
        setScreen(TestArchiveRepository(items = listOf(item)))
        waitForText(item.todo.title)

        composeRule.onNodeWithText(item.todo.title).performClick()

        waitForText(text(R.string.archive_detail_title))
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()

        waitForText(item.todo.title)
        composeRule.onNodeWithText(text(R.string.archive_detail_title)).assertDoesNotExist()
    }

    @Test
    fun at009_listMenuAndDetailExposeRestoreAndPermanentDeleteWithoutGestures() {
        val item = archivedTodo(title = "操作対象TODO")
        setScreen(TestArchiveRepository(items = listOf(item)))
        waitForText(item.todo.title)

        openRowMenu()
        composeRule.onNodeWithText(text(R.string.action_restore)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_restore)).performClick()
        waitForText(text(R.string.archive_restore_dialog_title))
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        composeRule.onNodeWithText(item.todo.title).performClick()
        waitForText(text(R.string.archive_detail_title))
        composeRule.onNodeWithText(text(R.string.action_restore)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsDisplayed()
        composeRule.onAllNodes(
            SemanticsMatcher("has long click action") {
                it.config.contains(SemanticsActions.OnLongClick)
            },
        ).assertCountEquals(0)
        composeRule.onAllNodes(
            SemanticsMatcher("has dismiss gesture action") {
                it.config.contains(SemanticsActions.Dismiss)
            },
        ).assertCountEquals(0)
    }

    @Test
    fun at013_historyModalIsReadOnlyEvenWhenSnapshotActionCanBeUndone() {
        val item = archivedTodo(title = "現在のTODO")
        val historyTitle = "当時の履歴スナップショット"
        val history = ArchivedHistoryItem.Execution(
            HistoryEntry(
                id = "history",
                todoId = item.todo.id,
                logicalDate = LocalDate.of(2026, 9, 1),
                state = TodoState.COMPLETED,
                actedAt = Instant.parse("2026-09-01T03:00:00Z").toEpochMilli(),
                finalizedAt = null,
                snapshot = historySnapshot(item.todo.id, historyTitle),
                canUndoAction = true,
            ),
        )
        setScreen(TestArchiveRepository(items = listOf(item), history = listOf(history)))
        waitForText(item.todo.title)
        composeRule.onNodeWithText(item.todo.title).performClick()
        waitForText(text(R.string.archive_detail_title))

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(historyTitle))
        composeRule.onNodeWithText(historyTitle).performClick()

        composeRule.onNodeWithText(text(R.string.action_close)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_skip)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_archive)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_delete)).assertDoesNotExist()
        composeRule.onNodeWithText("編集").assertDoesNotExist()
    }

    @Test
    fun at015_restorePreviewWarnsWhenNoFutureOccurrenceExists() {
        val item = archivedTodo(title = "終了済みTODO", endDate = LocalDate.of(2026, 8, 31))
        val repository = TestArchiveRepository(items = listOf(item)).apply {
            preview = preview(item, hasFutureOccurrence = false)
        }
        setScreen(repository)
        waitForText(item.todo.title)

        openRowAction(R.string.action_restore)

        waitForText(text(R.string.archive_restore_dialog_title))
        composeRule.onNodeWithText(text(R.string.archive_restore_no_future)).assertIsDisplayed()
    }

    @Test
    fun at022_restorePreviewExplainsUnavailableNotificationsWithoutDiscardingThem() {
        val item = archivedTodo(title = "通知停止中TODO")
        val repository = TestArchiveRepository(items = listOf(item)).apply {
            preview = preview(
                item = item,
                notificationSettingCount = 3,
                unavailableNotificationCount = 2,
            )
        }
        setScreen(repository)
        waitForText(item.todo.title)

        openRowAction(R.string.action_restore)

        composeRule.onNodeWithText(
            text(R.string.archive_restore_notifications, 3),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            text(R.string.archive_restore_notifications_unavailable, 2),
        ).assertIsDisplayed()
        assertEquals(3, repository.preview.notificationSettingCount)
    }

    @Test
    fun at025_successfulRestoreStaysOnListAndOffersNoUndoOrAutomaticEdit() {
        val item = archivedTodo(title = "復元成功TODO")
        val repository = TestArchiveRepository(items = listOf(item))
        setScreen(repository)
        waitForText(item.todo.title)

        openRowAction(R.string.action_restore)
        waitForText(text(R.string.archive_restore_dialog_title))
        composeRule.onNodeWithText(text(R.string.action_restore)).performClick()

        waitForText(text(R.string.archive_restore_success))
        waitForText(text(R.string.archive_empty))
        composeRule.onNodeWithText(text(R.string.action_undo)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.todo_editor_edit_title)).assertDoesNotExist()
        assertEquals(listOf(item.todo.id), repository.restoredTodoIds)
    }

    @Test
    fun at027_permanentDeleteDialogUsesExplicitButtonsWithoutTitleInput() {
        val item = archivedTodo(title = "完全削除対象TODO")
        setScreen(TestArchiveRepository(items = listOf(item)))
        waitForText(item.todo.title)

        openRowAction(R.string.action_delete_permanently)

        waitForText(text(R.string.archive_delete_dialog_title))
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    private fun setScreen(repository: TestArchiveRepository): ArchiveListViewModel {
        val viewModel = ArchiveListViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            settingsRepository = TestArchiveSettingsRepository(),
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                ArchiveListScreen(
                    onDestination = { _: MataDestination -> },
                    viewModel = viewModel,
                )
            }
        }
        return viewModel
    }

    private fun openRowMenu() {
        composeRule.onAllNodesWithContentDescription(text(R.string.archive_row_actions))[0]
            .performClick()
    }

    private fun openRowAction(actionRes: Int) {
        openRowMenu()
        composeRule.onNodeWithText(text(actionRes)).performClick()
    }

    private fun waitForText(value: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(resourceId: Int, vararg args: Any): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId, *args)
}

private class TestArchiveRepository(
    items: List<ArchivedTodoItem> = emptyList(),
    private val history: List<ArchivedHistoryItem> = emptyList(),
) : ArchiveRepository {
    private val items = MutableStateFlow(items)
    private val todoSources = mutableListOf<PagingSource<Int, ArchivedTodoItem>>()
    var preview: ArchiveActionPreview = items.firstOrNull()?.let(::preview)
        ?: ArchiveActionPreview(
            todoId = "missing",
            title = "missing",
            hasFutureOccurrence = true,
            notificationSettingCount = 0,
            unavailableNotificationCount = 0,
            historySummary = ArchiveHistorySummary(0, 0, 0, 0),
        )
    val restoredTodoIds = mutableListOf<String>()

    override fun pagedTodos(
        query: String,
        sortOrder: ArchiveSortOrder,
    ): Flow<PagingData<ArchivedTodoItem>> = Pager(PagingConfig(pageSize = 50)) {
        TestListPagingSource {
            val normalized = query.trim()
            val filtered = items.value.filter { item ->
                normalized.isEmpty() ||
                    item.todo.title.contains(normalized, ignoreCase = true) ||
                    item.todo.description.contains(normalized, ignoreCase = true) ||
                    item.category?.name?.contains(normalized, ignoreCase = true) == true
            }
            when (sortOrder) {
                ArchiveSortOrder.NEWEST -> filtered.sortedByDescending { it.archivedAt }
                ArchiveSortOrder.OLDEST -> filtered.sortedBy { it.archivedAt }
                ArchiveSortOrder.TITLE -> filtered.sortedBy { it.todo.title }
            }
        }.also(todoSources::add)
    }.flow

    override fun observeTodo(todoId: String): Flow<ArchivedTodoItem?> =
        items.map { current -> current.firstOrNull { it.todo.id == todoId } }

    override fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary> = flowOf(
        ArchiveHistorySummary(
            completedCount = history.count {
                it is ArchivedHistoryItem.Execution && it.entry.state == TodoState.COMPLETED
            },
            missedCount = history.count {
                it is ArchivedHistoryItem.Execution && it.entry.state == TodoState.MISSED
            },
            skippedCount = history.count {
                it is ArchivedHistoryItem.Execution && it.entry.state == TodoState.SKIPPED
            },
            periodResultCount = history.count { it is ArchivedHistoryItem.Period },
        ),
    )

    override fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>> =
        Pager(PagingConfig(pageSize = 50)) { TestListPagingSource { history } }.flow

    override suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview> =
        Result.success(preview.copy(todoId = todoId))

    override suspend fun restore(todoId: String): Result<Unit> {
        restoredTodoIds += todoId
        items.value = items.value.filterNot { it.todo.id == todoId }
        todoSources.toList().forEach(PagingSource<Int, ArchivedTodoItem>::invalidate)
        return Result.success(Unit)
    }

    override suspend fun deletePermanently(todoId: String): Result<Unit> {
        items.value = items.value.filterNot { it.todo.id == todoId }
        todoSources.toList().forEach(PagingSource<Int, ArchivedTodoItem>::invalidate)
        return Result.success(Unit)
    }
}

private class TestListPagingSource<T : Any>(
    private val values: () -> List<T>,
) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val allValues = values()
        val start = params.key ?: 0
        val end = (start + params.loadSize).coerceAtMost(allValues.size)
        return LoadResult.Page(
            data = if (start < allValues.size) allValues.subList(start, end) else emptyList(),
            prevKey = if (start == 0) null else (start - params.loadSize).coerceAtLeast(0),
            nextKey = if (end < allValues.size) end else null,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(state.config.pageSize)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(state.config.pageSize)
        }
}

private class TestArchiveSettingsRepository : SettingsRepository {
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

private fun archivedTodo(
    title: String,
    endDate: LocalDate? = null,
): ArchivedTodoItem {
    val id = title.hashCode().toString()
    val category = Category(
        id = "category",
        name = "ルーチン",
        colorIndex = 2,
        iconName = "TaskAlt",
        sortOrder = 0,
    )
    return ArchivedTodoItem(
        todo = Todo(
            id = id,
            title = title,
            description = "アーカイブ済みTODOの説明",
            categoryId = category.id,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = endDate,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = 18 * 60,
            definitionRevision = 1,
            archivedAt = Instant.parse("2026-09-01T09:00:00Z").toEpochMilli(),
            createdAt = 1L,
            notifications = listOf(
                TodoNotification(
                    id = "notification",
                    relation = NotificationRelation.BEFORE,
                    amount = 1,
                    unit = NotificationUnit.HOUR,
                ),
            ),
            carryOverEnabled = true,
        ),
        category = category,
    )
}

private fun preview(
    item: ArchivedTodoItem,
    hasFutureOccurrence: Boolean = true,
    notificationSettingCount: Int = item.todo.notifications.size,
    unavailableNotificationCount: Int = 0,
) = ArchiveActionPreview(
    todoId = item.todo.id,
    title = item.todo.title,
    hasFutureOccurrence = hasFutureOccurrence,
    notificationSettingCount = notificationSettingCount,
    unavailableNotificationCount = unavailableNotificationCount,
    historySummary = ArchiveHistorySummary(2, 1, 1, 1),
)

private fun historySnapshot(todoId: String, title: String) = HistoryTodoSnapshot(
    todoId = todoId,
    definitionRevision = 1,
    title = title,
    description = "当時の説明",
    startDate = LocalDate.of(2026, 8, 1),
    endDate = null,
    recurrenceRule = RecurrenceRule.daily(),
    dueMinutes = 18 * 60,
    notifications = emptyList(),
    categoryId = "history-category",
    categoryName = "当時のカテゴリ",
    categoryColorIndex = 1,
    categoryIconName = "History",
    categorySortOrder = 0,
    endHour = 0,
    weekStart = DayOfWeek.MONDAY,
    createdAt = 1L,
)
