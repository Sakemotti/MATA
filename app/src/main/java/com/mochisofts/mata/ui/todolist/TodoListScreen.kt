package com.mochisofts.mata.ui.todolist

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochisofts.mata.ui.ads.MataBannerAd
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MataAdaptiveNavigation
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationType
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.HolidayYearStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.rememberCoroutineScope

private data class TodoActionTarget(val id: String, val title: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onAddTodo: () -> Unit,
    onEditTodo: (String) -> Unit,
    onDestination: (MataDestination) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adsRuntimeState by viewModel.adsRuntimeState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val completedMessage = stringResource(R.string.message_todo_completed)
    val skippedMessage = stringResource(R.string.message_todo_skipped)
    val undoLabel = stringResource(R.string.action_undo)
    var showDatePicker by remember { mutableStateOf(false) }
    var readOnlyOccurrence by remember { mutableStateOf<TodoOccurrence?>(null) }
    var archiveTarget by remember { mutableStateOf<TodoActionTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<TodoActionTarget?>(null) }

    LaunchedEffect(viewModel, resources, completedMessage, undoLabel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodoListEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                is TodoListEffect.Completed -> {
                    val result = withTimeoutOrNull(5_000) {
                        snackbarHostState.showSnackbar(
                            message = completedMessage,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoCompletion(effect.todoId, effect.logicalDate)
                    }
                }
                is TodoListEffect.Skipped -> {
                    val result = withTimeoutOrNull(5_000) {
                        snackbarHostState.showSnackbar(
                            message = skippedMessage,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoSkip(effect.todoId, effect.logicalDate)
                    }
                }
                TodoListEffect.Archived -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.message_todo_archived))
                }
                TodoListEffect.Deleted -> {
                    snackbarHostState.showSnackbar(resources.getString(R.string.message_todo_deleted))
                }
            }
        }
    }

    MataAdaptiveNavigation(
        selected = MataDestination.TODOS,
        drawerState = drawerState,
        onSelect = onDestination,
    ) { navigationType ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.todo_list_title)) },
                    navigationIcon = {
                        if (navigationType == MataNavigationType.MODAL_DRAWER) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    Icons.Outlined.Menu,
                                    contentDescription = stringResource(R.string.content_description_open_menu),
                                )
                            }
                        }
                    },
                    actions = {
                        if (state.mode == TodoListMode.DATE && state.isToday) {
                            IconButton(onClick = { viewModel.setShowCompleted(!state.showCompleted) }) {
                                Icon(
                                    if (state.showCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.Check,
                                    contentDescription = if (state.showCompleted) {
                                        stringResource(R.string.content_description_hide_completed_todos)
                                    } else {
                                        stringResource(R.string.content_description_show_completed_todos)
                                    },
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    hasOverlay = (navigationType == MataNavigationType.MODAL_DRAWER &&
                        (drawerState.currentValue != DrawerValue.Closed ||
                            drawerState.targetValue != DrawerValue.Closed)) ||
                        showDatePicker ||
                        readOnlyOccurrence != null ||
                        archiveTarget != null ||
                        deleteTarget != null,
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = state.mode.ordinal) {
                    TodoListMode.entries.forEach { mode ->
                        Tab(
                            selected = state.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            text = {
                                Text(
                                    stringResource(
                                        if (mode == TodoListMode.DATE) {
                                            R.string.todo_list_tab_date
                                        } else {
                                            R.string.todo_list_tab_category
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                HolidayDataStatus(state)
                when (state.mode) {
                    TodoListMode.DATE -> DateMode(
                        state = state,
                        onPrevious = viewModel::selectPreviousDate,
                        onNext = viewModel::selectNextDate,
                        onToday = viewModel::selectToday,
                        onPickDate = { showDatePicker = true },
                        onComplete = viewModel::complete,
                        onSkip = viewModel::skip,
                        onArchive = { occurrence ->
                            archiveTarget = TodoActionTarget(occurrence.todo.id, occurrence.todo.title)
                        },
                        onDelete = { occurrence ->
                            deleteTarget = TodoActionTarget(occurrence.todo.id, occurrence.todo.title)
                        },
                        onOpen = { occurrence ->
                            if (state.selectedDate.isBefore(LocalDate.now())) {
                                readOnlyOccurrence = occurrence
                            } else {
                                onEditTodo(occurrence.todo.id)
                            }
                        },
                    )
                    TodoListMode.CATEGORY -> CategoryMode(
                        state = state,
                        onSelectCategory = viewModel::selectCategory,
                        onComplete = viewModel::complete,
                        onEdit = onEditTodo,
                        onSkip = viewModel::skip,
                        onArchive = { todo -> archiveTarget = TodoActionTarget(todo.id, todo.title) },
                        onDelete = { todo -> deleteTarget = TodoActionTarget(todo.id, todo.title) },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.selectDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(pickerState) }
    }

    readOnlyOccurrence?.let { occurrence ->
        AlertDialog(
            onDismissRequest = { readOnlyOccurrence = null },
            title = { Text(occurrence.todo.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        occurrence.todo.description.ifBlank {
                            stringResource(R.string.todo_description_empty)
                        },
                    )
                    Text(occurrence.category?.name ?: stringResource(R.string.label_uncategorized))
                    Text(stringResource(occurrence.state.labelRes()))
                }
            },
            confirmButton = {
                TextButton(onClick = { readOnlyOccurrence = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    archiveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text(stringResource(R.string.dialog_archive_todo_title)) },
            text = { Text(stringResource(R.string.dialog_archive_todo_message, target.title)) },
            confirmButton = {
                TextButton(onClick = {
                    archiveTarget = null
                    viewModel.archive(target.id)
                }) { Text(stringResource(R.string.action_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { archiveTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_todo_title)) },
            text = { Text(stringResource(R.string.dialog_delete_todo_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.delete(target.id)
                }) { Text(stringResource(R.string.action_delete_permanently)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun HolidayDataStatus(state: TodoListUiState) {
    state.holidayName?.let { name ->
        Text(
            text = stringResource(R.string.holiday_name_format, name),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    val message = when (state.holidayStatus) {
        HolidayYearStatus.AVAILABLE_STALE -> R.string.holiday_data_stale
        HolidayYearStatus.FAILED_WITH_CACHE -> R.string.holiday_data_failed_with_cache
        HolidayYearStatus.FETCHING -> if (state.holidayDataAvailable) {
            R.string.holiday_data_refreshing
        } else {
            R.string.holiday_data_loading
        }
        HolidayYearStatus.UNAVAILABLE -> R.string.holiday_data_unavailable
        HolidayYearStatus.FAILED_WITHOUT_CACHE -> R.string.holiday_data_provisional
        HolidayYearStatus.OUT_OF_RANGE -> if (state.holidayDataAvailable) {
            null
        } else {
            R.string.holiday_data_out_of_range
        }
        HolidayYearStatus.AVAILABLE_CURRENT,
        null,
        -> null
    }
    message?.let { messageRes ->
        Text(
            text = stringResource(messageRes),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (
                state.holidayStatus == HolidayYearStatus.FAILED_WITHOUT_CACHE ||
                state.holidayStatus == HolidayYearStatus.FAILED_WITH_CACHE
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun DateMode(
    state: TodoListUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onPickDate: () -> Unit,
    onComplete: (TodoOccurrence) -> Unit,
    onSkip: (TodoOccurrence) -> Unit,
    onArchive: (TodoOccurrence) -> Unit,
    onDelete: (TodoOccurrence) -> Unit,
    onOpen: (TodoOccurrence) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Outlined.ChevronLeft,
                contentDescription = stringResource(R.string.content_description_previous_day),
            )
        }
        TextButton(onClick = onPickDate) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.isToday) {
                    stringResource(R.string.label_today)
                } else {
                    state.selectedDate.toJapaneseDate(stringResource(R.string.date_pattern_short))
                },
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = stringResource(R.string.content_description_next_day),
            )
        }
    }
    if (!state.isToday) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onToday) { Text(stringResource(R.string.action_return_to_today)) }
        }
    }
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.message_loading))
        }
    } else if (state.occurrences.isEmpty()) {
        EmptyTodos(
            stringResource(
                if (state.isToday) {
                    R.string.empty_today_todos
                } else {
                    R.string.empty_selected_date_todos
                },
            ),
        )
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.occurrences, key = { "${it.todo.id}:${it.logicalDate}" }) { occurrence ->
                TodoOccurrenceRow(
                    occurrence = occurrence,
                    canComplete = state.isToday,
                    showActions = !state.selectedDate.isBefore(LocalDate.now()),
                    onComplete = onComplete,
                    onSkip = onSkip,
                    onArchive = onArchive,
                    onDelete = onDelete,
                    onOpen = onOpen,
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun CategoryMode(
    state: TodoListUiState,
    onSelectCategory: (String?) -> Unit,
    onComplete: (TodoOccurrence) -> Unit,
    onEdit: (String) -> Unit,
    onSkip: (TodoOccurrence) -> Unit,
    onArchive: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.selectedCategoryId == null,
            onClick = { onSelectCategory(null) },
            label = { Text(stringResource(R.string.label_uncategorized)) },
        )
        state.categories.forEach { category ->
            FilterChip(
                selected = state.selectedCategoryId == category.id,
                onClick = { onSelectCategory(category.id) },
                label = { Text(category.name) },
            )
        }
    }
    if (state.categoryItems.isEmpty()) {
        EmptyTodos(stringResource(R.string.empty_category_todos))
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.categoryItems, key = { it.todo.id }) { item ->
                val occurrence = item.occurrence
                ListItem(
                    headlineContent = { Text(item.todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            recurrenceSummary(item.todo),
                        )
                    },
                    leadingContent = {
                        if (occurrence != null) {
                            Checkbox(
                                checked = occurrence.state == TodoState.COMPLETED,
                                onCheckedChange = if (occurrence.state == TodoState.PENDING) {
                                    { checked -> if (checked) onComplete(occurrence) }
                                } else null,
                            )
                        }
                    },
                    trailingContent = {
                        TodoActionMenu(
                            canSkip = occurrence?.state == TodoState.PENDING,
                            onSkip = occurrence?.let { { onSkip(it) } },
                            onArchive = { onArchive(item.todo) },
                            onDelete = { onDelete(item.todo) },
                        )
                    },
                    modifier = Modifier.clickable { onEdit(item.todo.id) },
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun TodoOccurrenceRow(
    occurrence: TodoOccurrence,
    canComplete: Boolean,
    showActions: Boolean,
    onComplete: (TodoOccurrence) -> Unit,
    onSkip: (TodoOccurrence) -> Unit,
    onArchive: (TodoOccurrence) -> Unit,
    onDelete: (TodoOccurrence) -> Unit,
    onOpen: (TodoOccurrence) -> Unit,
) {
    val completed = occurrence.state == TodoState.COMPLETED
    ListItem(
        headlineContent = {
            Text(
                text = occurrence.todo.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (completed) TextDecoration.LineThrough else null,
            )
        },
        supportingContent = {
            Column {
                if (occurrence.todo.description.isNotBlank()) {
                    Text(occurrence.todo.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                occurrence.progress?.let { progress ->
                    Text(
                        stringResource(
                            R.string.todo_recurrence_progress_format,
                            progress.completedCount,
                            progress.period.requiredCount,
                        ),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                occurrence.category?.name
                                    ?: stringResource(R.string.label_uncategorized),
                            )
                        },
                    )
                    occurrence.todo.dueMinutes?.let { minutes ->
                        Text(
                            stringResource(
                                R.string.todo_due_time_format,
                                minutes / 60,
                                minutes % 60,
                            ),
                        )
                    }
                }
            }
        },
        leadingContent = {
            Checkbox(
                checked = completed,
                onCheckedChange = if (canComplete && !completed) {
                    { checked -> if (checked) onComplete(occurrence) }
                } else null,
            )
        },
        trailingContent = if (showActions) {
            {
                TodoActionMenu(
                    canSkip = canComplete && occurrence.state == TodoState.PENDING,
                    onSkip = { onSkip(occurrence) },
                    onArchive = { onArchive(occurrence) },
                    onDelete = { onDelete(occurrence) },
                )
            }
        } else {
            null
        },
        modifier = Modifier.clickable { onOpen(occurrence) },
    )
}

@Composable
private fun TodoActionMenu(
    canSkip: Boolean,
    onSkip: (() -> Unit)?,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.content_description_todo_actions),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canSkip && onSkip != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_skip)) },
                    onClick = { expanded = false; onSkip() },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_archive)) },
                onClick = { expanded = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

@Composable
private fun EmptyTodos(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun LocalDate.toJapaneseDate(pattern: String): String =
    format(DateTimeFormatter.ofPattern(pattern, Locale.JAPANESE))

@Composable
private fun recurrenceSummary(todo: Todo): String = when (todo.recurrenceType) {
    RecurrenceType.ONCE -> todo.startDate.toJapaneseDate(stringResource(R.string.date_pattern_short))
    RecurrenceType.DAILY -> stringResource(R.string.label_daily)
    RecurrenceType.WEEKDAYS -> stringResource(R.string.label_weekdays)
    RecurrenceType.SELECTED_WEEKDAYS -> stringResource(R.string.label_selected_weekdays)
    RecurrenceType.MONTHLY_DAY -> stringResource(
        R.string.todo_recurrence_monthly_day_format,
        todo.recurrenceRule.monthlyDay ?: 1,
    )
    RecurrenceType.MONTHLY_NTH_WEEKDAYS -> stringResource(R.string.label_monthly_nth_weekdays)
    RecurrenceType.MONTH_END -> stringResource(R.string.label_month_end)
    RecurrenceType.EVERY_N_DAYS -> stringResource(
        R.string.todo_recurrence_every_n_days_format,
        todo.recurrenceRule.intervalDays ?: 1,
    )
    RecurrenceType.WEEKLY_COUNT -> stringResource(
        R.string.todo_recurrence_weekly_count_format,
        todo.recurrenceRule.requiredCount ?: 1,
    )
    RecurrenceType.MONTHLY_COUNT -> stringResource(
        R.string.todo_recurrence_monthly_count_format,
        todo.recurrenceRule.requiredCount ?: 1,
    )
}

@StringRes
private fun TodoState.labelRes(): Int = when (this) {
    TodoState.PENDING -> R.string.label_pending
    TodoState.COMPLETED -> R.string.label_completed
    TodoState.SKIPPED -> R.string.label_skipped
    TodoState.MISSED -> R.string.label_missed
}
