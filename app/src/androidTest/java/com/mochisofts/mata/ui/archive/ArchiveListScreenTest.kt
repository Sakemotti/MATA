package com.mochisofts.mata.ui.archive

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.backup.BackupGateway
import com.mochisofts.mata.core.backup.BackupOperationPhase
import com.mochisofts.mata.core.backup.BackupOperationState
import com.mochisofts.mata.core.backup.BackupOperationStatus
import com.mochisofts.mata.core.backup.BackupOperationType
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
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArchiveListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var activeViewModelStore: ViewModelStore? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            activeViewModelStore?.clear()
            activeViewModelStore = null
        }
    }

    @Test
    fun at001_archiveIsAnIndependentDrawerDestinationWithHamburgerNavigation() {
        setScreen(TestArchiveRepository())
        waitForText(text(R.string.archive_title))

        composeRule.onNodeWithContentDescription(text(R.string.content_description_open_menu))
            .assertIsDisplayed()
            .performClick()
        composeRule.onNode(
            hasText(text(R.string.nav_archived_todos)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertIsDisplayed().assertIsSelected()
    }

    @Test
    fun at003_sortModesReorderRowsAndOnlyTheLastSelectionIsPersisted() {
        val items = listOf(
            archivedTodo(title = "Charlie", archivedAt = 1_000L),
            archivedTodo(title = "Bravo", archivedAt = 2_000L),
            archivedTodo(title = "Alpha", archivedAt = 3_000L),
        )
        val repository = TestArchiveRepository(items = items)
        val settings = TestArchiveSettingsRepository()
        setScreen(repository, settings)
        waitForTitleOrder("Alpha", "Bravo", "Charlie")

        selectSortOrder(R.string.archive_sort_oldest)
        waitForTitleOrder("Charlie", "Bravo", "Alpha")

        selectSortOrder(R.string.archive_sort_title)
        waitForTitleOrder("Alpha", "Bravo", "Charlie")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            settings.archiveSortOrder.value == ArchiveSortOrder.TITLE
        }

        val reopened = ArchiveListViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            settingsRepository = settings,
        )
        val restoredOrder = runBlocking {
            withTimeout(5_000) {
                reopened.uiState.first { it.sortOrder == ArchiveSortOrder.TITLE }.sortOrder
            }
        }
        assertEquals(ArchiveSortOrder.TITLE, restoredOrder)
    }

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
    fun at007_rowShowsDefinitionSummaryAndLimitsLongTitleAndDescriptionToTwoLines() {
        val longTitle = "長いタイトル".repeat(30)
        val longDescription = "長い説明文".repeat(50)
        val item = archivedTodo(title = longTitle, description = longDescription)
        setScreen(TestArchiveRepository(items = listOf(item)))
        waitForText(longTitle)

        composeRule.onNodeWithText(longTitle, useUnmergedTree = true).assertIsDisplayed()
        assertTextHeightAtMost(longTitle, 56)
        composeRule.onNodeWithText(longDescription, useUnmergedTree = true).assertIsDisplayed()
        assertTextHeightAtMost(longDescription, 48)
        composeRule.onNodeWithText("ルーチン・毎日").assertIsDisplayed()
        composeRule.onNodeWithText("2026年8月1日から無期限").assertIsDisplayed()
        composeRule.onNodeWithText("アーカイブ日時", substring = true).assertIsDisplayed()
    }

    @Test
    fun at008_rowOpensReadOnlyThreeCardFullScreenDetailWithCloseAction() {
        val item = archivedTodo(title = "詳細を開くTODO")
        setScreen(TestArchiveRepository(items = listOf(item)))
        waitForText(item.todo.title)

        composeRule.onNodeWithText(item.todo.title).performClick()

        waitForText(text(R.string.archive_detail_title))
        composeRule.onNodeWithText(text(R.string.archive_section_todo)).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(text(R.string.archive_section_summary)),
        )
        composeRule.onNodeWithText(text(R.string.archive_section_summary)).assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText(text(R.string.archive_section_history)),
        )
        composeRule.onNodeWithText(text(R.string.archive_section_history)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_close)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.action_close)).performClick()

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
    fun at010_detailShowsAllReadOnlyDefinitionFieldsInThreeCards() {
        val item = archivedTodo(title = "全項目TODO", description = "すべての任意項目を含む説明")
        setScreen(TestArchiveRepository(items = listOf(item)))
        waitForText(item.todo.title)
        composeRule.onNodeWithText(item.todo.title).performClick()
        waitForText(text(R.string.archive_detail_title))

        listOf(
            text(R.string.archive_section_todo),
            item.todo.title,
            text(R.string.todo_editor_description_label),
            item.todo.description,
            text(R.string.label_category),
            item.category!!.name,
            text(R.string.archive_label_schedule),
            text(R.string.archive_label_recurrence),
            text(R.string.archive_label_holiday),
            text(R.string.archive_label_due),
            text(R.string.todo_editor_carry_over_label),
            text(R.string.todo_editor_section_notification),
            text(R.string.archive_label_notifications),
            text(R.string.archive_section_archive_info),
            text(R.string.archive_label_archived_at),
            text(R.string.archive_section_summary),
            text(R.string.archive_section_history),
        ).forEach(::scrollToDetailText)
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
    fun at014_restoreDialogExplainsFutureResumeNoBackfillAndIrreversibility() {
        val item = archivedTodo(title = "実施期間中の復元対象")
        val repository = TestArchiveRepository(items = listOf(item))
        setScreen(repository)
        waitForText(item.todo.title)

        openRowAction(R.string.action_restore)

        waitForText(text(R.string.archive_restore_dialog_title))
        composeRule.onNodeWithText(
            text(R.string.archive_restore_dialog_message, item.todo.title),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.archive_restore_no_future)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_restore)).performClick()

        waitForText(text(R.string.archive_restore_success))
        waitForText(text(R.string.archive_empty))
        assertEquals(listOf(item.todo.id), repository.restoredTodoIds)
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
    fun at026_permanentDeleteDialogShowsHistoryScopeCalendarImpactAndNoUndo() {
        val item = archivedTodo(title = "履歴を持つ完全削除対象")
        val repository = TestArchiveRepository(items = listOf(item))
        setScreen(repository)
        waitForText(item.todo.title)

        openRowAction(R.string.action_delete_permanently)

        waitForText(text(R.string.archive_delete_dialog_title))
        composeRule.onNodeWithText(
            text(
                R.string.archive_delete_dialog_message,
                item.todo.title,
                repository.preview.historySummary.executionCount,
                repository.preview.historySummary.periodResultCount,
            ),
        ).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
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

    @Test
    fun at032_archiveActionsRejectBackupAndDuplicateOperationConflicts() {
        val item = archivedTodo(title = "排他制御対象TODO")
        val repository = TestArchiveRepository(items = listOf(item))
        val backup = TestArchiveBackupGateway().apply { setBlocked(true) }
        val viewModel = setScreen(repository, backupGateway = backup)
        waitForText(item.todo.title)

        composeRule.onAllNodesWithContentDescription(text(R.string.archive_row_actions))[0]
            .assertIsNotEnabled()
        composeRule.onNodeWithText(item.todo.title).performClick()
        waitForText(text(R.string.archive_detail_title))
        composeRule.onNodeWithText(text(R.string.action_restore)).assertIsNotEnabled()
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsNotEnabled()

        backup.setBlocked(false)
        composeRule.onNodeWithText(text(R.string.action_restore)).assertIsEnabled().performClick()
        waitForText(text(R.string.archive_restore_dialog_title))
        backup.setBlocked(true)
        composeRule.onAllNodesWithText(text(R.string.action_restore))[1].assertIsNotEnabled()
        assertEquals(0, repository.restoreCalls)

        backup.setBlocked(false)
        repository.restoreGate = CompletableDeferred()
        composeRule.onAllNodesWithText(text(R.string.action_restore))[1]
            .assertIsEnabled()
            .performClick()
        viewModel.confirmAction()
        viewModel.requestAction(item.todo.id, ArchiveAction.DELETE)
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.restoreCalls == 1 }
        assertEquals(0, repository.deleteCalls)
        repository.restoreGate?.complete(Unit)
        waitForText(text(R.string.archive_restore_success))
        assertEquals(1, repository.restoreCalls)
    }

    @Test
    fun at033_externalChangesRefreshListSearchDetailSummaryAndHistory() {
        val item = archivedTodo(title = "更新前TODO")
        val repository = TestArchiveRepository(items = listOf(item))
        setScreen(repository)
        waitForText(item.todo.title)
        composeRule.onNodeWithText(item.todo.title).performClick()
        waitForText(text(R.string.archive_detail_title))

        val updatedCategory = requireNotNull(item.category).copy(name = "更新後カテゴリ")
        val updated = item.copy(
            todo = item.todo.copy(title = "更新後TODO", categoryId = updatedCategory.id),
            category = updatedCategory,
        )
        val externalHistory = ArchivedHistoryItem.Execution(
            HistoryEntry(
                id = "external-history",
                todoId = item.todo.id,
                logicalDate = LocalDate.of(2026, 9, 2),
                state = TodoState.COMPLETED,
                actedAt = Instant.parse("2026-09-02T03:00:00Z").toEpochMilli(),
                finalizedAt = null,
                snapshot = historySnapshot(item.todo.id, "画面外で追加された履歴"),
                canUndoAction = false,
            ),
        )
        repository.updateItem(updated)
        repository.replaceHistory(listOf(externalHistory))

        waitForText(updated.todo.title)
        scrollToDetailText(updatedCategory.name)
        scrollToDetailText(text(R.string.archive_summary_completed, 1))
        scrollToDetailText("画面外で追加された履歴")

        composeRule.onNodeWithContentDescription(text(R.string.action_close)).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_archive_search))
            .performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("画面外新規")
        waitForText(text(R.string.archive_search_empty))
        val externallyArchived = archivedTodo(title = "画面外新規アーカイブ")
        repository.addItem(externallyArchived)
        waitForText(externallyArchived.todo.title)

        composeRule.onNodeWithContentDescription(text(R.string.content_description_close_search))
            .performClick()
        waitForText(updated.todo.title)
        composeRule.onNodeWithText(updated.todo.title).performClick()
        waitForText(text(R.string.archive_detail_title))
        repository.removeItem(updated.todo.id)
        waitForText(text(R.string.error_todo_not_found))
        composeRule.onNodeWithText(text(R.string.archive_detail_title)).assertDoesNotExist()
        composeRule.onNodeWithText(externallyArchived.todo.title).assertIsDisplayed()
    }

    private fun setScreen(
        repository: TestArchiveRepository,
        settingsRepository: TestArchiveSettingsRepository = TestArchiveSettingsRepository(),
        backupGateway: BackupGateway? = null,
    ): ArchiveListViewModel {
        val createdViewModel = ArchiveListViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            settingsRepository = settingsRepository,
            backupGateway = backupGateway,
        )
        val viewModelStore = ViewModelStore()
        val viewModel = ViewModelProvider.create(
            store = viewModelStore,
            factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = createdViewModel as T
            },
        )[ArchiveListViewModel::class]
        activeViewModelStore = viewModelStore
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

    private fun selectSortOrder(labelRes: Int) {
        composeRule.onNodeWithContentDescription(text(R.string.content_description_archive_sort))
            .performClick()
        composeRule.onNodeWithText(text(labelRes)).performClick()
    }

    private fun waitForTitleOrder(vararg titles: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                val tops = titles.map { title ->
                    composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
                }
                tops == tops.sorted()
            }.getOrDefault(false)
        }
    }

    private fun scrollToDetailText(value: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(value))
        composeRule.onNodeWithText(value).assertIsDisplayed()
    }

    private fun assertTextHeightAtMost(value: String, maxHeightDp: Int) {
        val heightPx = composeRule.onNodeWithText(value, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.height
        val maxHeightPx = with(composeRule.density) { maxHeightDp.dp.toPx() }
        assertTrue("$value exceeded $maxHeightDp dp: $heightPx px", heightPx <= maxHeightPx)
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
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(resourceId: Int, vararg args: Any): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId, *args)
}

private class TestArchiveRepository(
    items: List<ArchivedTodoItem> = emptyList(),
    history: List<ArchivedHistoryItem> = emptyList(),
) : ArchiveRepository {
    private val items = MutableStateFlow(items)
    private val history = MutableStateFlow(history)
    private val todoSources = mutableListOf<PagingSource<Int, ArchivedTodoItem>>()
    private val historySources = mutableListOf<PagingSource<Int, ArchivedHistoryItem>>()
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
    var restoreGate: CompletableDeferred<Unit>? = null
    var restoreCalls = 0
    var deleteCalls = 0

    fun updateItem(item: ArchivedTodoItem) {
        items.value = items.value.map { current -> if (current.todo.id == item.todo.id) item else current }
        todoSources.toList().forEach(PagingSource<Int, ArchivedTodoItem>::invalidate)
    }

    fun addItem(item: ArchivedTodoItem) {
        items.value = items.value + item
        todoSources.toList().forEach(PagingSource<Int, ArchivedTodoItem>::invalidate)
    }

    fun removeItem(todoId: String) {
        items.value = items.value.filterNot { it.todo.id == todoId }
        todoSources.toList().forEach(PagingSource<Int, ArchivedTodoItem>::invalidate)
    }

    fun replaceHistory(items: List<ArchivedHistoryItem>) {
        history.value = items
        historySources.toList().forEach(PagingSource<Int, ArchivedHistoryItem>::invalidate)
    }

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

    override fun observeHistorySummary(todoId: String): Flow<ArchiveHistorySummary> = history.map { current ->
        ArchiveHistorySummary(
            completedCount = current.count {
                it is ArchivedHistoryItem.Execution && it.entry.state == TodoState.COMPLETED
            },
            missedCount = current.count {
                it is ArchivedHistoryItem.Execution && it.entry.state == TodoState.MISSED
            },
            skippedCount = current.count {
                it is ArchivedHistoryItem.Execution && it.entry.state == TodoState.SKIPPED
            },
            periodResultCount = current.count { it is ArchivedHistoryItem.Period },
        )
    }

    override fun pagedHistory(todoId: String): Flow<PagingData<ArchivedHistoryItem>> =
        Pager(PagingConfig(pageSize = 50)) {
            TestListPagingSource { history.value }.also(historySources::add)
        }.flow

    override suspend fun getActionPreview(todoId: String): Result<ArchiveActionPreview> =
        Result.success(preview.copy(todoId = todoId))

    override suspend fun restore(todoId: String): Result<Unit> {
        restoreCalls += 1
        restoreGate?.await()
        restoredTodoIds += todoId
        removeItem(todoId)
        return Result.success(Unit)
    }

    override suspend fun deletePermanently(todoId: String): Result<Unit> {
        deleteCalls += 1
        removeItem(todoId)
        return Result.success(Unit)
    }
}

private class TestArchiveBackupGateway : BackupGateway {
    private val mutableState = MutableStateFlow(BackupOperationState())
    override val state: StateFlow<BackupOperationState> = mutableState

    fun setBlocked(blocked: Boolean) {
        mutableState.value = if (blocked) {
            BackupOperationState(
                operationId = "restore",
                type = BackupOperationType.RESTORE,
                status = BackupOperationStatus.RUNNING,
                phase = BackupOperationPhase.RESTORING,
            )
        } else {
            BackupOperationState()
        }
    }

    override fun suggestedFileName() = "backup.zip"
    override fun startCreate(uri: Uri) = false
    override fun startRestoreValidation(uri: Uri) = false
    override fun confirmRestore() = false
    override fun cancelRestoreConfirmation() = Unit
    override fun acknowledgeResult() = Unit
    override suspend fun recoverInterruptedOperation() = Unit
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
    description: String = "アーカイブ済みTODOの説明",
    endDate: LocalDate? = null,
    archivedAt: Long = Instant.parse("2026-09-01T09:00:00Z").toEpochMilli(),
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
            description = description,
            categoryId = category.id,
            startDate = LocalDate.of(2026, 8, 1),
            endDate = endDate,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = 18 * 60,
            definitionRevision = 1,
            archivedAt = archivedAt,
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
