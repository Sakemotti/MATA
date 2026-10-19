package com.mochisofts.mata.ui.incomplete

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataCardLayout
import com.mochisofts.mata.core.designsystem.MataCategoryLabel
import com.mochisofts.mata.core.designsystem.MataSectionCard
import com.mochisofts.mata.core.designsystem.MataSnackbarHost
import com.mochisofts.mata.core.designsystem.mataPageColor
import com.mochisofts.mata.core.designsystem.mataPageKeyScroll
import com.mochisofts.mata.core.designsystem.navigation.MataAdaptiveNavigation
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.core.designsystem.navigation.MataNavigationType
import com.mochisofts.mata.domain.model.IncompleteTodoGroup
import com.mochisofts.mata.domain.model.IncompleteTodoHistory
import com.mochisofts.mata.domain.model.IncompleteTodoSource
import com.mochisofts.mata.ui.ads.MataBannerAd
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncompleteTodoListScreen(
    onDestination: (MataDestination) -> Unit,
    onRegister: (IncompleteTodoSource) -> Unit,
    viewModel: IncompleteTodoListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ads by viewModel.adsRuntimeState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var hideTarget by remember { mutableStateOf<IncompleteTodoGroup?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { delay(60_000); viewModel.refresh(reconcile = false) }
        }
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(resources.getString(it)) } }
    MataAdaptiveNavigation(selected = MataDestination.INCOMPLETE, drawerState = drawer, onSelect = onDestination) { layout ->
        val pageColor = MaterialTheme.mataPageColor
        Scaffold(
            containerColor = pageColor,
            snackbarHost = { MataSnackbarHost(snackbar) },
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.incomplete_title)) },
                        navigationIcon = {
                            if (layout.navigationType == MataNavigationType.MODAL_DRAWER) {
                                IconButton(onClick = { scope.launch { drawer.open() } }) {
                                    Icon(Icons.Outlined.Menu, stringResource(R.string.content_description_open_menu))
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = pageColor, scrolledContainerColor = pageColor),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            bottomBar = {
                MataBannerAd(
                    runtimeState = ads, isForeground = lifecycle.isAtLeast(Lifecycle.State.RESUMED),
                    isScreenVisible = true, isImeVisible = false,
                    hasOverlay = hideTarget != null || drawer.currentValue != DrawerValue.Closed ||
                        drawer.targetValue != DrawerValue.Closed,
                )
            },
        ) { padding ->
            IncompleteTodoListContent(
                state, onRegister, { hideTarget = it }, { viewModel.refresh() },
                history = { group -> IncompleteHistory(group, viewModel) },
                modifier = Modifier.padding(padding),
            )
        }
    }
    hideTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { hideTarget = null },
            title = { Text(stringResource(R.string.incomplete_hide_title)) },
            text = { Text(stringResource(R.string.incomplete_hide_message, target.todo.title)) },
            confirmButton = {
                TextButton(onClick = { hideTarget = null; viewModel.dismiss(target.source) }) {
                    Text(stringResource(R.string.incomplete_hide))
                }
            },
            dismissButton = { TextButton(onClick = { hideTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
internal fun IncompleteTodoListContent(
    state: IncompleteTodoListUiState,
    onRegister: (IncompleteTodoSource) -> Unit,
    onHide: (IncompleteTodoGroup) -> Unit,
    onRetry: () -> Unit,
    history: @Composable (IncompleteTodoGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    var categoryKey by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = state.groups.filter { categoryKey == null || (it.category?.id ?: "__uncategorized__") == categoryKey }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier.fillMaxSize().testTag("incomplete_list").mataPageKeyScroll(listState),
        state = listState, contentPadding = MataCardLayout.PageContentPadding,
        verticalArrangement = Arrangement.spacedBy(MataCardLayout.CardSpacing),
    ) {
        item(key = "summary") {
            MataSectionCard(title = stringResource(R.string.incomplete_count, state.groups.size)) {
                Text(stringResource(R.string.incomplete_description))
                if (state.isReconciling) Text(stringResource(R.string.incomplete_reconciling))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MataCardLayout.CardSpacing)) {
                    FilterChip(selected = categoryKey == null, onClick = { categoryKey = null }, label = { Text(stringResource(R.string.incomplete_all_categories)) })
                    state.groups.distinctBy { it.category?.id }.forEach { group ->
                        val key = group.category?.id ?: "__uncategorized__"
                        FilterChip(selected = categoryKey == key, onClick = { categoryKey = key }, label = {
                            Text(group.category?.name ?: stringResource(R.string.label_uncategorized))
                        })
                    }
                }
            }
        }
        if (state.hasLoadError || state.hasHistoryError) item(key = "error") {
            MataSectionCard {
                Text(stringResource(R.string.incomplete_load_error))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
        if (state.isLoading) item(key = "loading") {
            MataSectionCard { Text(stringResource(R.string.message_loading)) }
        } else if (groups.isEmpty() && !state.hasLoadError && !state.hasHistoryError && !state.isReconciling) item(key = "empty") {
            MataSectionCard { Text(stringResource(if (categoryKey == null) R.string.incomplete_empty else R.string.incomplete_filter_empty)) }
        }
        items(groups, key = { it.todo.id }) { group ->
            var expanded by rememberSaveable(group.todo.id) { mutableStateOf(false) }
            MataSectionCard(modifier = Modifier.fillMaxWidth().testTag("incomplete_card:${group.todo.id}")) {
                Text(group.todo.title, style = MaterialTheme.typography.titleLarge)
                MataCategoryLabel(group.category?.name ?: stringResource(R.string.label_uncategorized),
                    group.category?.iconName ?: "CategoryOff", group.category?.colorIndex)
                Text(stringResource(R.string.incomplete_group_count, group.count), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.incomplete_range, plainDate(group.oldestDate), plainDate(group.latestDate)))
                group.current?.let { current ->
                    Text(if (current.progress != null) stringResource(R.string.incomplete_current_period, current.progress.remainingCount)
                        else stringResource(R.string.incomplete_current))
                }
                if (group.pastCount > 0) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.incomplete_collapse else R.string.incomplete_expand))
                    }
                    if (expanded) history(group)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val enabled = !state.isReconciling && !state.hasLoadError && !state.hasHistoryError && state.busyTodoId == null
                Button(onClick = { onRegister(group.source) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.incomplete_register))
                }
                OutlinedButton(onClick = { onHide(group) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.incomplete_hide))
                }
            }
        }
    }
}

@Composable
private fun IncompleteHistory(group: IncompleteTodoGroup, viewModel: IncompleteTodoListViewModel) {
    var page by rememberSaveable(group.todo.id, group.source.throughDate.toString()) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    key(group.todo.id, page, retry) {
        var failed by remember { mutableStateOf(false) }
        val flow = remember(viewModel, group.todo.id, page, retry) {
            viewModel.history(group.todo.id, page).catch { failed = true; emit(emptyList()) }
        }
        val entries by flow.collectAsStateWithLifecycle(initialValue = null)
        when {
            failed -> {
                Text(stringResource(R.string.incomplete_load_error))
                TextButton(onClick = { retry++ }) { Text(stringResource(R.string.action_retry)) }
            }
            entries == null -> Text(stringResource(R.string.message_loading))
            else -> {
                Text(stringResource(R.string.incomplete_history_page, page * 20 + 1, page * 20 + entries!!.size, group.pastCount))
                entries!!.forEach { item ->
                    Text(if (item.periodStart != null) stringResource(R.string.incomplete_period, plainDate(item.periodStart), plainDate(item.date))
                        else plainDate(item.date))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(MataCardLayout.CardSpacing)) {
                    TextButton(onClick = { page-- }, enabled = page > 0) { Text(stringResource(R.string.incomplete_previous_page)) }
                    TextButton(onClick = { page++ }, enabled = (page + 1) * 20 < group.pastCount) { Text(stringResource(R.string.incomplete_next_page)) }
                }
            }
        }
    }
}

@Composable
private fun plainDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_plain)))
