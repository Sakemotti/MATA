package com.mochisofts.mata.ui.archive

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MataAdaptiveNavigation
import com.mochisofts.mata.app.MataAdaptiveLayoutInfo
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationType
import com.mochisofts.mata.core.designsystem.categoryIcon
import com.mochisofts.mata.core.designsystem.mataCategoryColor
import com.mochisofts.mata.core.designsystem.mataClickablePointer
import com.mochisofts.mata.core.designsystem.mataColors
import com.mochisofts.mata.core.designsystem.mataPageKeyScroll
import com.mochisofts.mata.core.designsystem.MataSnackbarHost
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.PeriodHistoryEntry
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.RecurrenceDayFilter
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.ui.common.TodoDetailCategory
import com.mochisofts.mata.ui.common.TodoDetailField
import com.mochisofts.mata.ui.common.TodoDetailModal
import com.mochisofts.mata.ui.common.TodoDetailModalData
import com.mochisofts.mata.ui.common.todoNotificationSettingsText
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveListScreen(
    onDestination: (MataDestination) -> Unit,
    viewModel: ArchiveListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todos = viewModel.todos.collectAsLazyPagingItems()
    val history = viewModel.selectedHistory.collectAsLazyPagingItems()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val detailListState = rememberLazyListState()
    val resources = LocalResources.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var selectedHistory by remember { mutableStateOf<ArchivedHistoryItem?>(null) }

    BackHandler(state.selectedTodoId != null || state.searchActive) {
        if (state.selectedTodoId != null) viewModel.closeDetail() else viewModel.closeSearch()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        todos.refresh()
        if (state.selectedTodoId != null) history.refresh()
    }
    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ArchiveListEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
            }
        }
    }

    MataAdaptiveNavigation(
        selected = MataDestination.ARCHIVE,
        drawerState = drawerState,
        onSelect = onDestination,
    ) { layoutInfo ->
        if (!layoutInfo.useTwoPane && state.selectedTodoId != null) {
            ArchiveSelectedDetail(
                state = state,
                history = history,
                listState = detailListState,
                showTopBar = true,
                onBack = viewModel::closeDetail,
                onAction = { action ->
                    state.selectedTodoId?.let { viewModel.requestAction(it, action) }
                },
                onHistoryClick = { selectedHistory = it },
                snackbarHostState = snackbarHostState,
                modifier = Modifier.fillMaxSize(),
            )
            return@MataAdaptiveNavigation
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (state.searchActive) {
                            val focusManager = LocalFocusManager.current
                            TextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::updateSearchQuery,
                                placeholder = { Text(stringResource(R.string.archive_search_hint)) },
                                singleLine = true,
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                            )
                        } else {
                            Text(stringResource(R.string.archive_title))
                        }
                    },
                    navigationIcon = {
                        if (state.searchActive) {
                            IconButton(onClick = viewModel::closeSearch) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.content_description_close_search),
                                )
                            }
                        } else if (layoutInfo.navigationType == MataNavigationType.MODAL_DRAWER) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.content_description_open_menu),
                                )
                            }
                        }
                    },
                    actions = {
                        if (!state.searchActive) {
                            IconButton(onClick = viewModel::openSearch) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = stringResource(
                                        R.string.content_description_archive_search,
                                    ),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Sort,
                                    contentDescription = stringResource(
                                        R.string.content_description_archive_sort,
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                ArchiveSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(archiveSortLabel(order)) },
                                        onClick = {
                                            sortMenuExpanded = false
                                            viewModel.setSortOrder(order)
                                        },
                                        leadingIcon = if (state.sortOrder == order) {
                                            { Icon(Icons.Outlined.CheckCircle, null) }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            snackbarHost = { MataSnackbarHost(snackbarHostState) },
        ) { padding ->
            if (layoutInfo.useTwoPane) {
                ArchiveTwoPaneContent(
                    layoutInfo = layoutInfo,
                    state = state,
                    todos = todos,
                    history = history,
                    listState = listState,
                    detailListState = detailListState,
                    onOpenDetail = viewModel::openDetail,
                    onAction = viewModel::requestAction,
                    onClearSearch = viewModel::closeSearch,
                    onCloseDetail = viewModel::closeDetail,
                    onHistoryClick = { selectedHistory = it },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                ArchiveTodoList(
                    items = todos,
                    query = state.searchQuery.trim(),
                    selectedTodoId = null,
                    loadingPreviewTodoId = state.loadingPreviewTodoId,
                    runningTodoId = state.runningTodoId,
                    listState = listState,
                    onOpenDetail = viewModel::openDetail,
                    onAction = viewModel::requestAction,
                    onClearSearch = viewModel::closeSearch,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }

    state.preview?.let { preview ->
        ArchiveActionDialog(
            preview = preview,
            action = requireNotNull(state.previewAction),
            running = state.runningTodoId != null,
            onConfirm = viewModel::confirmAction,
            onDismiss = viewModel::dismissAction,
        )
    }
    selectedHistory?.let { item ->
        ArchiveHistoryDialog(item = item, onDismiss = { selectedHistory = null })
    }
}

internal data class ArchivePaneWidths(
    val listWidthDp: Float,
    val detailWidthDp: Float,
)

internal fun archivePaneWidths(availableWidthDp: Float): ArchivePaneWidths {
    val gap = 24f
    val contentWidth = (availableWidthDp - gap).coerceAtLeast(0f)
    val listWidth = (contentWidth * 0.42f).coerceIn(320f, 400f)
    return ArchivePaneWidths(
        listWidthDp = listWidth,
        detailWidthDp = (contentWidth - listWidth).coerceAtLeast(392f),
    )
}

@Composable
private fun ArchiveTwoPaneContent(
    layoutInfo: MataAdaptiveLayoutInfo,
    state: ArchiveListUiState,
    todos: LazyPagingItems<ArchivedTodoItem>,
    history: LazyPagingItems<ArchivedHistoryItem>,
    listState: LazyListState,
    detailListState: LazyListState,
    onOpenDetail: (String) -> Unit,
    onAction: (String, ArchiveAction) -> Unit,
    onClearSearch: () -> Unit,
    onCloseDetail: () -> Unit,
    onHistoryClick: (ArchivedHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val widths = layoutInfo.twoPaneHinge?.let { hinge ->
        ArchivePaneWidths(
            listWidthDp = hinge.startPaneWidthDp,
            detailWidthDp = hinge.endPaneWidthDp,
        )
    } ?: archivePaneWidths(layoutInfo.availableContentWidthDp)
    val paneSpacing = (layoutInfo.twoPaneHinge?.gapDp ?: 24f).dp
    Row(
        modifier = modifier.padding(horizontal = layoutInfo.outerMarginDp.dp),
        horizontalArrangement = Arrangement.spacedBy(paneSpacing),
    ) {
        ArchiveTodoList(
            items = todos,
            query = state.searchQuery.trim(),
            selectedTodoId = state.selectedTodoId,
            loadingPreviewTodoId = state.loadingPreviewTodoId,
            runningTodoId = state.runningTodoId,
            listState = listState,
            onOpenDetail = onOpenDetail,
            onAction = onAction,
            onClearSearch = onClearSearch,
            modifier = Modifier.width(widths.listWidthDp.dp).fillMaxSize(),
        )
        Surface(
            modifier = Modifier.width(widths.detailWidthDp.dp).fillMaxSize(),
            tonalElevation = 1.dp,
        ) {
            if (state.selectedTodoId == null) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.archive_two_pane_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ArchiveSelectedDetail(
                    state = state,
                    history = history,
                    listState = detailListState,
                    showTopBar = false,
                    onBack = onCloseDetail,
                    onAction = { action -> onAction(state.selectedTodoId, action) },
                    onHistoryClick = onHistoryClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveSelectedDetail(
    state: ArchiveListUiState,
    history: LazyPagingItems<ArchivedHistoryItem>,
    listState: LazyListState,
    showTopBar: Boolean,
    onBack: () -> Unit,
    onAction: (ArchiveAction) -> Unit,
    onHistoryClick: (ArchivedHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(R.string.archive_detail_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            state.selectedItem?.let {
                ArchiveBottomActions(
                    enabled = state.loadingPreviewTodoId == null && state.runningTodoId == null,
                    loading = state.loadingPreviewTodoId != null || state.runningTodoId != null,
                    onRestore = { onAction(ArchiveAction.RESTORE) },
                    onDelete = { onAction(ArchiveAction.DELETE) },
                )
            }
        },
        snackbarHost = {
            snackbarHostState?.let { MataSnackbarHost(it) }
        },
    ) { padding ->
        when {
            state.isDetailLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.selectedItem != null -> ArchiveDetailContent(
                item = state.selectedItem,
                summary = state.selectedSummary,
                history = history,
                listState = listState,
                onHistoryClick = onHistoryClick,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(state.detailLoadErrorRes ?: R.string.error_todo_not_found),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ArchiveTodoList(
    items: LazyPagingItems<ArchivedTodoItem>,
    query: String,
    selectedTodoId: String?,
    loadingPreviewTodoId: String?,
    runningTodoId: String?,
    listState: LazyListState,
    onOpenDetail: (String) -> Unit,
    onAction: (String, ArchiveAction) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val refresh = items.loadState.refresh) {
        LoadState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LoadState.Error -> ArchiveErrorArea(
            messageRes = R.string.archive_list_load_error,
            onRetry = items::retry,
            modifier = modifier,
        )
        is LoadState.NotLoading -> {
            if (items.itemCount == 0) {
                ArchiveEmptyArea(query, onClearSearch, modifier)
            } else {
                LazyColumn(
                    modifier = modifier.mataPageKeyScroll(listState),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(
                        count = items.itemCount,
                        key = { index -> items[index]?.todo?.id ?: "placeholder-$index" },
                    ) { index ->
                        val item = items[index] ?: return@items
                        val busy = item.todo.id == loadingPreviewTodoId || item.todo.id == runningTodoId
                        ArchivedTodoRow(
                            item = item,
                            busy = busy,
                            selected = item.todo.id == selectedTodoId,
                            onClick = { onOpenDetail(item.todo.id) },
                            onAction = { action -> onAction(item.todo.id, action) },
                        )
                        HorizontalDivider()
                    }
                    when (items.loadState.append) {
                        LoadState.Loading -> item {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(28.dp)) }
                        }
                        is LoadState.Error -> item {
                            ArchiveInlineRetry(R.string.archive_more_load_error, items::retry)
                        }
                        is LoadState.NotLoading -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedTodoRow(
    item: ArchivedTodoItem,
    busy: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onAction: (ArchiveAction) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val categoryName = item.category?.name ?: stringResource(R.string.label_uncategorized)
    val recurrence = recurrenceDescription(item.todo.recurrenceRule)
    val period = activePeriodDescription(item)
    val archivedAt = formatEpochMillis(item.archivedAt)
    val semantics = listOf(item.todo.title, categoryName, recurrence, period, archivedAt).joinToString()
    ListItem(
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
        ),
        headlineContent = {
            Text(item.todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                if (item.todo.description.isNotBlank()) {
                    Text(item.todo.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(stringResource(R.string.archive_category_recurrence, categoryName, recurrence))
                Text(period, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.archive_archived_at, archivedAt),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        leadingContent = { ArchiveCategoryIcon(item) },
        trailingContent = {
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = stringResource(R.string.archive_row_actions),
                        )
                    }
                    ArchiveRowMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        onAction = {
                            menuExpanded = false
                            onAction(it)
                        },
                    )
                }
            }
        },
        modifier = Modifier
            .alpha(if (busy) 0.6f else 1f)
            .semantics {
                contentDescription = semantics
                this.selected = selected
            }
            .mataClickablePointer(enabled = !busy)
            .clickable(enabled = !busy, onClick = onClick),
    )
}

@Composable
private fun ArchiveRowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ArchiveAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_restore)) },
            leadingIcon = { Icon(Icons.Outlined.Restore, null) },
            onClick = { onAction(ArchiveAction.RESTORE) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete_permanently)) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.DeleteForever,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = { onAction(ArchiveAction.DELETE) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveDetailScreen(
    onBack: () -> Unit,
    onFinished: (Int) -> Unit,
    onNotFound: () -> Unit,
    viewModel: ArchiveDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history = viewModel.history.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var selectedHistory by remember { mutableStateOf<ArchivedHistoryItem?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { history.refresh() }
    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ArchiveDetailEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                is ArchiveDetailEffect.Finished -> onFinished(effect.messageRes)
            }
        }
    }
    LaunchedEffect(state.isLoading, state.item, state.isRunningAction) {
        if (!state.isLoading && state.item == null && !state.isRunningAction) onNotFound()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.archive_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            state.item?.let {
                ArchiveBottomActions(
                    enabled = !state.isLoadingPreview && !state.isRunningAction,
                    loading = state.isLoadingPreview || state.isRunningAction,
                    onRestore = { viewModel.requestAction(ArchiveAction.RESTORE) },
                    onDelete = { viewModel.requestAction(ArchiveAction.DELETE) },
                )
            }
        },
        snackbarHost = { MataSnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            state.item != null -> ArchiveDetailContent(
                item = state.item,
                summary = state.summary,
                history = history,
                onHistoryClick = { selectedHistory = it },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    state.preview?.let { preview ->
        ArchiveActionDialog(
            preview = preview,
            action = requireNotNull(state.previewAction),
            running = state.isRunningAction,
            onConfirm = viewModel::confirmAction,
            onDismiss = viewModel::dismissAction,
        )
    }
    selectedHistory?.let { item ->
        ArchiveHistoryDialog(item = item, onDismiss = { selectedHistory = null })
    }
}

@Composable
private fun ArchiveDetailContent(
    item: ArchivedTodoItem?,
    summary: ArchiveHistorySummary?,
    history: LazyPagingItems<ArchivedHistoryItem>,
    listState: LazyListState = rememberLazyListState(),
    onHistoryClick: (ArchivedHistoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val archivedItem = item ?: return
    LazyColumn(
        modifier = modifier.mataPageKeyScroll(listState),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { ArchiveSectionHeader(R.string.archive_section_todo) }
        item { ArchiveTodoDefinition(archivedItem) }
        item { ArchiveSectionHeader(R.string.archive_section_summary) }
        item { ArchiveSummary(summary) }
        item { ArchiveSectionHeader(R.string.archive_section_history) }
        when (val refresh = history.loadState.refresh) {
            LoadState.Loading -> item {
                Box(
                    Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            is LoadState.Error -> item {
                ArchiveInlineRetry(R.string.archive_history_load_error, history::retry)
            }
            is LoadState.NotLoading -> {
                if (history.itemCount == 0) {
                    item {
                        Text(
                            stringResource(R.string.archive_history_empty),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        )
                    }
                } else {
                    items(
                        count = history.itemCount,
                        key = { index -> history[index]?.stableId ?: "history-placeholder-$index" },
                    ) { index ->
                        val historyItem = history[index] ?: return@items
                        ArchiveHistoryRow(historyItem) { onHistoryClick(historyItem) }
                        HorizontalDivider()
                    }
                }
            }
        }
        when (history.loadState.append) {
            LoadState.Loading -> item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(28.dp)) }
            }
            is LoadState.Error -> item {
                ArchiveInlineRetry(R.string.archive_more_load_error, history::retry)
            }
            is LoadState.NotLoading -> Unit
        }
    }
}

@Composable
private fun ArchiveTodoDefinition(item: ArchivedTodoItem) {
    val todo = item.todo
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(todo.title, style = MaterialTheme.typography.titleLarge)
        if (todo.description.isNotBlank()) Text(todo.description)
        ArchiveDetailLine(
            stringResource(R.string.label_category),
            item.category?.name ?: stringResource(R.string.label_uncategorized),
        )
        ArchiveDetailLine(
            stringResource(R.string.archive_label_schedule),
            activePeriodDescription(item),
        )
        ArchiveDetailLine(
            stringResource(R.string.archive_label_recurrence),
            recurrenceDescription(todo.recurrenceRule),
        )
        ArchiveDetailLine(
            stringResource(R.string.archive_label_holiday),
            stringResource(
                if (todo.recurrenceType == RecurrenceType.WEEKDAYS) {
                    R.string.archive_holiday_excluded
                } else if (todo.recurrenceRule.dayFilter == RecurrenceDayFilter.WEEKDAYS) {
                    R.string.archive_holiday_excluded
                } else if (
                    todo.recurrenceRule.dayFilter == RecurrenceDayFilter.WEEKENDS_HOLIDAYS
                ) {
                    R.string.archive_holiday_included
                } else {
                    R.string.archive_holiday_not_excluded
                },
            ),
        )
        ArchiveDetailLine(
            stringResource(R.string.archive_label_due),
            todo.dueMinutes?.let { minutes ->
                stringResource(R.string.time_format, minutes / 60, minutes % 60)
            } ?: stringResource(R.string.archive_due_none),
        )
        ArchiveDetailLine(
            stringResource(R.string.archive_label_notifications),
            notificationDescription(todo.notifications),
        )
        ArchiveDetailLine(
            stringResource(R.string.archive_label_archived_at),
            formatEpochMillis(item.archivedAt),
        )
    }
}

@Composable
private fun ArchiveSummary(summary: ArchiveHistorySummary?) {
    if (summary == null) {
        Box(
            Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(Modifier.size(28.dp)) }
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.archive_summary_completed, summary.completedCount))
        Text(stringResource(R.string.archive_summary_missed, summary.missedCount))
        Text(stringResource(R.string.archive_summary_skipped, summary.skippedCount))
        Text(stringResource(R.string.archive_summary_period, summary.periodResultCount))
        Text(
            stringResource(R.string.archive_summary_total, summary.totalCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ArchiveHistoryRow(item: ArchivedHistoryItem, onClick: () -> Unit) {
    when (item) {
        is ArchivedHistoryItem.Execution -> {
            val entry = item.entry
            ListItem(
                headlineContent = { Text(entry.snapshot.title, maxLines = 2) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.archive_execution_support,
                            entry.logicalDate.toPlainDate(),
                            todoStateLabel(entry.state),
                            entry.snapshot.dueMinutes?.let { minutes ->
                                stringResource(R.string.time_format, minutes / 60, minutes % 60)
                            } ?: stringResource(R.string.archive_due_none),
                        ),
                    )
                    entry.actedAt?.let { Text(formatEpochMillis(it)) }
                },
                leadingContent = { ExecutionStateIcon(entry.state) },
                trailingContent = { SnapshotCategoryIcon(entry.snapshot) },
                modifier = Modifier.mataClickablePointer().clickable(onClick = onClick),
            )
        }
        is ArchivedHistoryItem.Period -> {
            val entry = item.entry
            ListItem(
                headlineContent = { Text(entry.snapshot.title, maxLines = 2) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.archive_period_support,
                            entry.periodStart.toPlainDate(),
                            entry.periodEnd.toPlainDate(),
                            entry.completedCount,
                            entry.requiredCount,
                            stringResource(
                                if (entry.achieved) R.string.label_achieved else R.string.label_unachieved,
                            ),
                        ),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.History, null) },
                trailingContent = { SnapshotCategoryIcon(entry.snapshot) },
                modifier = Modifier.mataClickablePointer().clickable(onClick = onClick),
            )
        }
    }
}

@Composable
private fun ArchiveBottomActions(
    enabled: Boolean,
    loading: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onRestore, enabled = enabled, modifier = Modifier.weight(1f)) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Outlined.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_restore))
            }
            Button(
                onClick = onDelete,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_delete_permanently))
            }
        }
    }
}

@Composable
private fun ArchiveActionDialog(
    preview: ArchiveActionPreview,
    action: ArchiveAction,
    running: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        icon = {
            Icon(
                if (action == ArchiveAction.RESTORE) Icons.Outlined.Restore else Icons.Outlined.DeleteForever,
                contentDescription = null,
            )
        },
        title = {
            Text(
                stringResource(
                    if (action == ArchiveAction.RESTORE) {
                        R.string.archive_restore_dialog_title
                    } else {
                        R.string.archive_delete_dialog_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (action == ArchiveAction.RESTORE) {
                    Text(stringResource(R.string.archive_restore_dialog_message, preview.title))
                    if (!preview.hasFutureOccurrence) {
                        Text(
                            stringResource(R.string.archive_restore_no_future),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (preview.notificationSettingCount > 0) {
                        Text(
                            stringResource(
                                R.string.archive_restore_notifications,
                                preview.notificationSettingCount,
                            ),
                        )
                    }
                    if (preview.unavailableNotificationCount > 0) {
                        Text(
                            stringResource(
                                R.string.archive_restore_notifications_unavailable,
                                preview.unavailableNotificationCount,
                            ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    Text(
                        stringResource(
                            R.string.archive_delete_dialog_message,
                            preview.title,
                            preview.historySummary.executionCount,
                            preview.historySummary.periodResultCount,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !running,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (action == ArchiveAction.DELETE) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    stringResource(
                        if (action == ArchiveAction.RESTORE) {
                            R.string.action_restore
                        } else {
                            R.string.action_delete_permanently
                        },
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !running) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ArchiveHistoryDialog(item: ArchivedHistoryItem, onDismiss: () -> Unit) {
    TodoDetailModal(data = item.detailModalData(), onDismiss = onDismiss)
}

@Composable
private fun ArchivedHistoryItem.detailModalData(): TodoDetailModalData {
    val snapshot = when (this) {
        is ArchivedHistoryItem.Execution -> entry.snapshot
        is ArchivedHistoryItem.Period -> entry.snapshot
    }
    val fields = mutableListOf(
        TodoDetailField(
            label = stringResource(R.string.archive_label_recurrence),
            value = recurrenceDescription(snapshot.recurrenceRule),
        ),
        TodoDetailField(
            label = stringResource(R.string.archive_label_due),
            value = snapshot.dueMinutes?.let {
                stringResource(R.string.time_format, it / 60, it % 60)
            } ?: stringResource(R.string.label_not_set),
        ),
        TodoDetailField(
            label = stringResource(R.string.archive_label_notifications),
            value = todoNotificationSettingsText(snapshot.notifications),
        ),
    )
    when (this) {
        is ArchivedHistoryItem.Execution -> {
            fields += TodoDetailField(
                label = stringResource(R.string.calendar_history_logical_date),
                value = entry.logicalDate.toPlainDate(),
            )
            fields += TodoDetailField(
                label = stringResource(R.string.calendar_history_state),
                value = todoStateLabel(entry.state),
            )
            entry.actedAt?.let {
                fields += TodoDetailField(
                    label = stringResource(R.string.calendar_history_operation_time),
                    value = formatEpochMillis(it),
                )
            }
        }
        is ArchivedHistoryItem.Period -> {
            fields += TodoDetailField(
                label = stringResource(R.string.calendar_history_period),
                value = stringResource(
                    R.string.calendar_history_period_range,
                    entry.periodStart.toPlainDate(),
                    entry.periodEnd.toPlainDate(),
                ),
            )
            fields += TodoDetailField(
                label = stringResource(R.string.calendar_history_result),
                value = stringResource(
                    R.string.calendar_history_result_count,
                    entry.completedCount,
                    entry.requiredCount,
                    stringResource(if (entry.achieved) R.string.label_achieved else R.string.label_unachieved),
                ),
            )
        }
    }
    return TodoDetailModalData(
        title = snapshot.title,
        description = snapshot.description,
        category = TodoDetailCategory(
            name = snapshot.categoryName ?: stringResource(R.string.label_uncategorized),
            iconName = snapshot.categoryIconName,
            colorIndex = snapshot.categoryColorIndex,
        ),
        fields = fields,
    )
}

@Composable
private fun ArchiveCategoryIcon(item: ArchivedTodoItem) {
    val color = mataCategoryColor(item.category?.colorIndex)
    Box(
        Modifier.size(40.dp).background(color.copy(alpha = 0.16f), MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            categoryIcon(item.category?.iconName ?: DEFAULT_CATEGORY_ICON),
            contentDescription = item.category?.name ?: stringResource(R.string.label_uncategorized),
            tint = color,
        )
    }
}

@Composable
private fun SnapshotCategoryIcon(snapshot: HistoryTodoSnapshot) {
    val color = mataCategoryColor(snapshot.categoryColorIndex)
    Icon(
        categoryIcon(snapshot.categoryIconName ?: DEFAULT_CATEGORY_ICON),
        contentDescription = snapshot.categoryName ?: stringResource(R.string.label_uncategorized),
        tint = color,
    )
}

@Composable
private fun ExecutionStateIcon(state: TodoState) {
    val icon = when (state) {
        TodoState.COMPLETED -> Icons.Outlined.CheckCircle
        TodoState.SKIPPED -> Icons.Outlined.SkipNext
        TodoState.MISSED, TodoState.PENDING -> Icons.Outlined.ErrorOutline
    }
    val tint = when (state) {
        TodoState.COMPLETED -> MaterialTheme.mataColors.statusSuccess
        TodoState.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
        TodoState.MISSED, TodoState.PENDING -> MaterialTheme.colorScheme.error
    }
    Icon(icon, contentDescription = todoStateLabel(state), tint = tint)
}

@Composable
private fun ArchiveSectionHeader(@StringRes labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun ArchiveDetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value)
    }
}

@Composable
private fun ArchiveEmptyArea(query: String, onClearSearch: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Archive, null, Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(if (query.isBlank()) R.string.archive_empty else R.string.archive_search_empty),
        )
        if (query.isNotBlank()) {
            TextButton(onClick = onClearSearch) {
                Text(stringResource(R.string.archive_clear_search))
            }
        }
    }
}

@Composable
private fun ArchiveErrorArea(
    @StringRes messageRes: Int,
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(messageRes))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun ArchiveInlineRetry(@StringRes messageRes: Int, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(messageRes))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun activePeriodDescription(item: ArchivedTodoItem): String {
    val todo = item.todo
    val start = todo.startDate.toPlainDate()
    return when {
        todo.recurrenceType == RecurrenceType.ONCE ->
            stringResource(R.string.archive_period_single, start)
        todo.endDate == null ->
            stringResource(R.string.archive_period_indefinite, start)
        else ->
            stringResource(R.string.archive_period_range, start, todo.endDate.toPlainDate())
    }
}

@Composable
private fun recurrenceDescription(rule: RecurrenceRule): String = when (rule.type) {
    RecurrenceType.ONCE -> stringResource(R.string.label_no_recurrence)
    RecurrenceType.DAILY -> stringResource(R.string.label_daily)
    RecurrenceType.WEEKDAYS -> stringResource(R.string.label_weekdays)
    RecurrenceType.SELECTED_WEEKDAYS -> when (rule.dayFilter) {
        RecurrenceDayFilter.WEEKDAYS -> stringResource(R.string.label_weekdays)
        RecurrenceDayFilter.WEEKENDS_HOLIDAYS ->
            stringResource(R.string.todo_editor_day_filter_weekends_holidays)
        else -> {
            val labels = mutableListOf<String>()
            DayOfWeek.entries.filter { it in rule.selectedWeekdays }.forEach { day ->
                labels += weekdayLabel(day)
            }
            stringResource(
                R.string.archive_selected_weekdays_format,
                labels.joinToString(stringResource(R.string.list_separator_middle_dot)),
            )
        }
    }
    RecurrenceType.MONTHLY_DAY -> stringResource(
        R.string.todo_recurrence_monthly_day_format,
        rule.monthlyDay ?: 1,
    )
    RecurrenceType.MONTHLY_NTH_WEEKDAYS -> {
        val labels = rule.monthlyNthWeekdays
            .sortedWith(compareBy({ it.ordinal }, { it.dayOfWeek.value }))
            .map { value ->
                stringResource(
                    R.string.todo_recurrence_nth_weekday_item_format,
                    value.ordinal,
                    weekdayLabel(value.dayOfWeek),
                )
            }
        stringResource(
            R.string.todo_recurrence_monthly_nth_weekdays_format,
            labels.joinToString(stringResource(R.string.list_separator_middle_dot)),
        )
    }
    RecurrenceType.MONTH_END -> stringResource(R.string.label_month_end)
    RecurrenceType.EVERY_N_DAYS -> stringResource(
        R.string.todo_recurrence_every_n_days_format,
        rule.intervalDays ?: 1,
    )
    RecurrenceType.WEEKLY_COUNT -> {
        val period = stringResource(
            R.string.todo_recurrence_weekly_interval_count_format,
            rule.periodWeeks,
            rule.requiredCount ?: 1,
        )
        val filter = when (rule.dayFilter) {
            RecurrenceDayFilter.ALL -> null
            RecurrenceDayFilter.WEEKDAYS -> stringResource(R.string.label_weekdays)
            RecurrenceDayFilter.WEEKENDS_HOLIDAYS ->
                stringResource(R.string.todo_editor_day_filter_weekends_holidays)
            RecurrenceDayFilter.CUSTOM -> {
                val labels = mutableListOf<String>()
                DayOfWeek.entries.filter { it in rule.selectedWeekdays }.forEach { day ->
                    labels += weekdayLabel(day)
                }
                stringResource(
                    R.string.archive_selected_weekdays_format,
                    labels.joinToString(stringResource(R.string.list_separator_middle_dot)),
                )
            }
        }
        filter?.let {
            stringResource(R.string.todo_recurrence_with_day_filter_format, period, it)
        } ?: period
    }
    RecurrenceType.MONTHLY_COUNT -> stringResource(
        R.string.todo_recurrence_monthly_count_format,
        rule.requiredCount ?: 1,
    )
}

@Composable
private fun notificationDescription(notifications: List<TodoNotification>): String {
    if (notifications.isEmpty()) return stringResource(R.string.archive_notifications_none)
    val labels = mutableListOf<String>()
    notifications.forEach { notification ->
        labels += when (notification.relation) {
            NotificationRelation.AT -> stringResource(R.string.notification_relation_at)
            NotificationRelation.BEFORE -> stringResource(
                R.string.notification_relation_before_format,
                notification.amount,
                notificationUnitLabel(notification.unit),
            )
            NotificationRelation.AFTER -> stringResource(
                R.string.notification_relation_after_format,
                notification.amount,
                notificationUnitLabel(notification.unit),
            )
        }
    }
    return labels.joinToString()
}

@Composable
private fun notificationUnitLabel(unit: NotificationUnit): String = stringResource(
    when (unit) {
        NotificationUnit.MINUTE -> R.string.unit_minute
        NotificationUnit.HOUR -> R.string.unit_hour
        NotificationUnit.DAY -> R.string.unit_day
    },
)

@Composable
private fun weekdayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.weekday_monday
        DayOfWeek.TUESDAY -> R.string.weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.weekday_thursday
        DayOfWeek.FRIDAY -> R.string.weekday_friday
        DayOfWeek.SATURDAY -> R.string.weekday_saturday
        DayOfWeek.SUNDAY -> R.string.weekday_sunday
    },
)

@Composable
private fun todoStateLabel(state: TodoState): String = stringResource(
    when (state) {
        TodoState.PENDING -> R.string.label_pending
        TodoState.COMPLETED -> R.string.label_completed
        TodoState.SKIPPED -> R.string.label_skipped
        TodoState.MISSED -> R.string.label_missed
    },
)

@Composable
private fun archiveSortLabel(order: ArchiveSortOrder): String = stringResource(
    when (order) {
        ArchiveSortOrder.NEWEST -> R.string.archive_sort_newest
        ArchiveSortOrder.OLDEST -> R.string.archive_sort_oldest
        ArchiveSortOrder.TITLE -> R.string.archive_sort_title
    },
)

@Composable
private fun LocalDate.toPlainDate(): String = format(
    DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_plain), Locale.JAPANESE),
)

@Composable
private fun formatEpochMillis(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(stringResource(R.string.date_time_pattern), Locale.JAPANESE))

private const val DEFAULT_CATEGORY_ICON = "CategoryOff"
