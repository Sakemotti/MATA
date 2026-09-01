package com.mochisofts.mata.ui.categorytodolist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MataAdaptiveNavigation
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationType
import com.mochisofts.mata.core.designsystem.MataStatusLabel
import com.mochisofts.mata.core.designsystem.MataStatusType
import com.mochisofts.mata.core.designsystem.MataTodoListItem
import com.mochisofts.mata.core.designsystem.MataTodoListItemDefaults
import com.mochisofts.mata.core.designsystem.mataClickablePointer
import com.mochisofts.mata.core.designsystem.mataPageKeyScroll
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.ui.ads.MataBannerAd
import com.mochisofts.mata.ui.todolist.labelRes
import com.mochisofts.mata.ui.todolist.recurrenceSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTodoListScreen(
    onAddTodo: () -> Unit,
    onEditTodo: (String) -> Unit,
    onDestination: (MataDestination) -> Unit,
    viewModel: CategoryTodoListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adsRuntimeState by viewModel.adsRuntimeState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    MataAdaptiveNavigation(
        selected = MataDestination.CATEGORY_TODOS,
        drawerState = drawerState,
        onSelect = onDestination,
    ) { layoutInfo ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.category_todo_list_title)) },
                    navigationIcon = {
                        if (layoutInfo.navigationType == MataNavigationType.MODAL_DRAWER) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(
                                        R.string.content_description_open_menu,
                                    ),
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAddTodo,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_todo)) },
                )
            },
            bottomBar = {
                MataBannerAd(
                    runtimeState = adsRuntimeState,
                    isForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                    isScreenVisible = true,
                    isImeVisible = imeVisible,
                    hasOverlay = layoutInfo.navigationType == MataNavigationType.MODAL_DRAWER &&
                        (drawerState.currentValue != DrawerValue.Closed ||
                            drawerState.targetValue != DrawerValue.Closed),
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                CategoryTabs(
                    state = state,
                    onSelectCategory = viewModel::selectCategory,
                )
                CategoryTodos(
                    state = state,
                    onEditTodo = onEditTodo,
                )
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    state: CategoryTodoListUiState,
    onSelectCategory: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .horizontalScroll(rememberScrollState())
            .focusGroup()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.selectedCategoryId == null,
            onClick = { onSelectCategory(null) },
            label = { Text(stringResource(R.string.label_uncategorized)) },
            modifier = Modifier.mataClickablePointer(),
        )
        state.categories.forEach { category ->
            FilterChip(
                selected = state.selectedCategoryId == category.id,
                onClick = { onSelectCategory(category.id) },
                label = { Text(category.name) },
                modifier = Modifier.mataClickablePointer(),
            )
        }
    }
}

@Composable
private fun CategoryTodos(
    state: CategoryTodoListUiState,
    onEditTodo: (String) -> Unit,
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator()
        }
    } else if (state.items.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.empty_category_todos),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.fillMaxSize().mataPageKeyScroll(listState),
            state = listState,
        ) {
            items(state.items, key = { it.todo.id }) { item ->
                val stateLabel = item.todayState?.let { stringResource(it.labelRes()) }
                MataTodoListItem(
                    headlineContent = {
                        Text(
                            item.todo.title,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            recurrenceSummary(item.todo),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        item.todayState?.let { status ->
                            CategoryTodoStatus(status)
                        }
                    },
                    reserveTrailingSpace = true,
                    trailingSlotWidth = MataTodoListItemDefaults.StatusSlotWidth,
                    modifier = Modifier
                        .semantics {
                            stateLabel?.let { stateDescription = it }
                        }
                        .mataClickablePointer()
                        .clickable { onEditTodo(item.todo.id) },
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun CategoryTodoStatus(state: TodoState) {
    val (icon, type) = when (state) {
        TodoState.PENDING -> Icons.Outlined.RadioButtonUnchecked to MataStatusType.IN_PROGRESS
        TodoState.COMPLETED -> Icons.Outlined.CheckCircle to MataStatusType.SUCCESS
        TodoState.SKIPPED -> Icons.Outlined.SkipNext to MataStatusType.NEUTRAL
        TodoState.MISSED -> Icons.Outlined.ErrorOutline to MataStatusType.ERROR
    }
    MataStatusLabel(
        text = stringResource(state.labelRes()),
        icon = icon,
        type = type,
    )
}
