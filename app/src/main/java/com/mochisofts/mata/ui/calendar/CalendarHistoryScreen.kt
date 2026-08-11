package com.mochisofts.mata.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationDrawer
import com.mochisofts.mata.core.designsystem.CategoryLightColors
import com.mochisofts.mata.core.designsystem.categoryIcon
import com.mochisofts.mata.domain.model.HistoryDayState
import com.mochisofts.mata.domain.model.HistoryDaySummary
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.PeriodHistoryEntry
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface HistoryDialogItem {
    data class Execution(val value: HistoryEntry) : HistoryDialogItem
    data class Period(val value: PeriodHistoryEntry) : HistoryDialogItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarHistoryScreen(
    onDestination: (MataDestination) -> Unit,
    viewModel: CalendarHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showMonthPicker by remember { mutableStateOf(false) }
    var dialogItem by remember { mutableStateOf<HistoryDialogItem?>(null) }
    val undoMessage = stringResource(R.string.calendar_history_completion_undone)
    val undoLabel = stringResource(R.string.action_undo)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    LaunchedEffect(viewModel, resources, undoMessage, undoLabel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalendarHistoryEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                is CalendarHistoryEffect.CompletionUndone -> {
                    val result = withTimeoutOrNull(5_000) {
                        snackbarHostState.showSnackbar(
                            message = undoMessage,
                            actionLabel = undoLabel,
                            withDismissAction = true,
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.restoreCompletion(effect.token)
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MataNavigationDrawer(MataDestination.CALENDAR) { destination ->
                scope.launch {
                    drawerState.close()
                    if (destination != MataDestination.CALENDAR) onDestination(destination)
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.calendar_history_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.content_description_open_menu),
                            )
                        }
                    },
                    actions = {
                        if (state.displayedMonth != YearMonth.from(state.today)) {
                            TextButton(onClick = viewModel::selectToday) {
                                Text(stringResource(R.string.label_today))
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                MonthControls(
                    state = state,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onSelectMonth = { showMonthPicker = true },
                )
                WeekdayHeader(state.weekStart)
                when {
                    state.isMonthLoading -> Box(
                        Modifier.fillMaxWidth().height(288.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    state.monthErrorRes != null -> ErrorArea(
                        messageRes = state.monthErrorRes!!,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.fillMaxWidth().height(288.dp),
                    )
                    else -> MonthGrid(state, viewModel::selectDate, viewModel::showPreviousMonth, viewModel::showNextMonth)
                }
                HorizontalDivider()
                DayHistoryArea(
                    state = state,
                    onRetry = viewModel::refresh,
                    onEntryClick = { dialogItem = HistoryDialogItem.Execution(it) },
                    onPeriodClick = { dialogItem = HistoryDialogItem.Period(it) },
                    onUndoCompletion = viewModel::undoCompletion,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            displayedMonth = state.displayedMonth,
            today = state.today,
            onSelect = { viewModel.selectMonth(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false },
        )
    }
    dialogItem?.let { item ->
        HistoryDetailDialog(item = item, onDismiss = { dialogItem = null })
    }
}

@Composable
private fun MonthControls(
    state: CalendarHistoryUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectMonth: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.calendar_history_previous_month))
        }
        TextButton(onClick = onSelectMonth) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            Text(
                state.displayedMonth.format(
                    DateTimeFormatter.ofPattern(stringResource(R.string.year_month_pattern)),
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = state.displayedMonth < YearMonth.from(state.today),
        ) {
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.calendar_history_next_month))
        }
    }
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek) {
    Row(Modifier.fillMaxWidth()) {
        (0..6).forEach { offset ->
            val day = DayOfWeek.of((weekStart.value - 1 + offset) % 7 + 1)
            Text(
                text = weekdayShortLabel(day),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    state: CalendarHistoryUiState,
    onSelectDate: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var dragAmount by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(state.displayedMonth) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { _, amount -> dragAmount += amount },
                    onDragEnd = {
                        if (dragAmount > 80f) onPrevious()
                        if (dragAmount < -80f && state.displayedMonth < YearMonth.from(state.today)) {
                            onNext()
                        }
                        dragAmount = 0f
                    },
                )
            },
    ) {
        state.gridDates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    CalendarDayCell(
                        date = date,
                        displayedMonth = state.displayedMonth,
                        selectedDate = state.selectedDate,
                        today = state.today,
                        summary = state.month.summaries[date],
                        onClick = { onSelectDate(date) },
                        modifier = Modifier.weight(1f).aspectRatio(1.18f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    summary: HistoryDaySummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !date.isAfter(today)
    val selected = date == selectedDate
    val current = date == today
    val inMonth = YearMonth.from(date) == displayedMonth
    val stateLabel = summary?.state?.label()
    val semanticsLabel = buildString {
        append(date.format(DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_full))))
        if (selected) append(stringResource(R.string.calendar_history_selected_suffix))
        if (current) append(stringResource(R.string.calendar_history_today_suffix))
        summary?.let {
            if (it.plannedCount > 0) append(
                stringResource(
                    R.string.calendar_history_cell_count_semantics,
                    it.completedCount,
                    it.plannedCount,
                ),
            )
            stateLabel?.let(::append)
            if (it.hasAchievedPeriod) append(stringResource(R.string.calendar_history_period_achieved))
            if (it.hasUnachievedPeriod) append(stringResource(R.string.calendar_history_period_unachieved))
        }
    }
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val borderColor = if (current) MaterialTheme.colorScheme.primary else Color.Transparent
    Column(
        modifier
            .padding(2.dp)
            .border(if (current) 1.5.dp else 0.dp, borderColor, CircleShape)
            .background(background, CircleShape)
            .alpha(if (inMonth && enabled) 1f else 0.48f)
            .semantics {
                contentDescription = semanticsLabel
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected || current) FontWeight.Bold else FontWeight.Normal,
        )
        if (summary?.plannedCount != null && summary.plannedCount > 0) {
            Text(
                stringResource(
                    R.string.calendar_history_cell_count,
                    summary.completedCount,
                    summary.plannedCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            summary?.state?.let { HistoryStateIcon(it, Modifier.size(13.dp)) }
            if (summary?.hasAchievedPeriod == true) {
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (summary?.hasUnachievedPeriod == true) {
                Icon(Icons.Filled.Error, null, Modifier.size(10.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DayHistoryArea(
    state: CalendarHistoryUiState,
    onRetry: () -> Unit,
    onEntryClick: (HistoryEntry) -> Unit,
    onPeriodClick: (PeriodHistoryEntry) -> Unit,
    onUndoCompletion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isDayLoading -> Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.dayErrorRes != null -> ErrorArea(state.dayErrorRes, onRetry, modifier)
        state.day == null -> Unit
        else -> {
            val day = requireNotNull(state.day)
            LazyColumn(modifier.fillMaxWidth()) {
                item {
                    DaySummaryHeader(day)
                }
                val unfinished = day.entries.filter {
                    it.state == TodoState.MISSED || it.state == TodoState.PENDING
                }
                historySection(
                    titleRes = R.string.label_unfinished,
                    entries = unfinished,
                    onEntryClick = onEntryClick,
                    onUndoCompletion = onUndoCompletion,
                    busyExecutionId = state.busyExecutionId,
                )
                historySection(
                    titleRes = R.string.label_skipped,
                    entries = day.entries.filter { it.state == TodoState.SKIPPED },
                    onEntryClick = onEntryClick,
                    onUndoCompletion = onUndoCompletion,
                    busyExecutionId = state.busyExecutionId,
                )
                historySection(
                    titleRes = R.string.label_completed,
                    entries = day.entries.filter { it.state == TodoState.COMPLETED },
                    onEntryClick = onEntryClick,
                    onUndoCompletion = onUndoCompletion,
                    busyExecutionId = state.busyExecutionId,
                )
                if (day.periodResults.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.calendar_history_period_results)) }
                    items(day.periodResults, key = { "period:${it.id}" }) { period ->
                        PeriodResultRow(period, onPeriodClick)
                        HorizontalDivider()
                    }
                }
                if (day.entries.isEmpty() && day.periodResults.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillParentMaxHeight().fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(R.string.calendar_history_empty_day)) }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historySection(
    @androidx.annotation.StringRes titleRes: Int,
    entries: List<HistoryEntry>,
    onEntryClick: (HistoryEntry) -> Unit,
    onUndoCompletion: (String) -> Unit,
    busyExecutionId: String?,
) {
    if (entries.isEmpty()) return
    item { SectionTitle(stringResource(titleRes)) }
    items(entries, key = { "entry:${it.id ?: "pending:${it.todoId}:${it.logicalDate}"}" }) { entry ->
        HistoryEntryRow(entry, busyExecutionId, onEntryClick, onUndoCompletion)
        HorizontalDivider()
    }
}

@Composable
private fun DaySummaryHeader(day: com.mochisofts.mata.domain.model.HistoryDay) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            day.date.format(
                DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_full), Locale.JAPANESE),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        if (day.summary.plannedCount > 0) {
            Text(
                stringResource(
                    R.string.calendar_history_day_count,
                    day.summary.completedCount,
                    day.summary.plannedCount,
                ),
            )
            day.summary.state?.let { Text(it.label(), fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun HistoryEntryRow(
    entry: HistoryEntry,
    busyExecutionId: String?,
    onClick: (HistoryEntry) -> Unit,
    onUndoCompletion: (String) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(entry.snapshot.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                if (entry.snapshot.description.isNotBlank()) {
                    Text(entry.snapshot.description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(historyEntrySupportingText(entry))
                entry.actedAt?.let {
                    Text(
                        stringResource(
                            R.string.calendar_history_action_time,
                            formatEpochMillis(it),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        leadingContent = {
            if (entry.canUndoCompletion && entry.id != null) {
                Checkbox(
                    checked = true,
                    enabled = busyExecutionId == null,
                    onCheckedChange = { checked -> if (!checked) onUndoCompletion(entry.id) },
                )
            } else {
                HistoryEntryIcon(entry.state)
            }
        },
        trailingContent = { CategorySnapshotIcon(entry.snapshot) },
        modifier = Modifier.clickable { onClick(entry) },
    )
}

@Composable
private fun PeriodResultRow(entry: PeriodHistoryEntry, onClick: (PeriodHistoryEntry) -> Unit) {
    ListItem(
        headlineContent = {
            Text(entry.snapshot.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                stringResource(
                    if (entry.periodType == RecurrenceType.WEEKLY_COUNT) {
                        R.string.calendar_history_weekly_period_format
                    } else {
                        R.string.calendar_history_monthly_period_format
                    },
                    entry.completedCount,
                    entry.requiredCount,
                    stringResource(
                        if (entry.achieved) R.string.label_achieved else R.string.label_unachieved,
                    ),
                ),
            )
            Text(
                stringResource(
                    R.string.calendar_history_period_range,
                    entry.periodStart.toShortDate(),
                    entry.periodEnd.toShortDate(),
                ),
            )
        },
        leadingContent = {
            Icon(
                if (entry.achieved) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = stringResource(
                    if (entry.achieved) R.string.label_achieved else R.string.label_unachieved,
                ),
                tint = if (entry.achieved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = { CategorySnapshotIcon(entry.snapshot) },
        modifier = Modifier.clickable { onClick(entry) },
    )
}

@Composable
private fun CategorySnapshotIcon(snapshot: HistoryTodoSnapshot) {
    val color = CategoryLightColors.getOrElse(snapshot.categoryColorIndex ?: 15) {
        CategoryLightColors.last()
    }
    Box(
        Modifier.size(32.dp).background(color.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            categoryIcon(snapshot.categoryIconName ?: "Category"),
            contentDescription = snapshot.categoryName ?: stringResource(R.string.label_uncategorized),
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HistoryEntryIcon(state: TodoState) {
    val icon = when (state) {
        TodoState.PENDING -> Icons.Outlined.ErrorOutline
        TodoState.MISSED -> Icons.Outlined.Close
        TodoState.SKIPPED -> Icons.Outlined.SkipNext
        TodoState.COMPLETED -> Icons.Outlined.CheckCircle
    }
    Icon(icon, contentDescription = state.label())
}

@Composable
private fun HistoryStateIcon(state: HistoryDayState, modifier: Modifier = Modifier) {
    val icon = when (state) {
        HistoryDayState.IN_PROGRESS -> Icons.Filled.Schedule
        HistoryDayState.COMPLETED -> Icons.Filled.CheckCircle
        HistoryDayState.UNACHIEVED -> Icons.Filled.Error
    }
    val tint = when (state) {
        HistoryDayState.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        HistoryDayState.COMPLETED -> MaterialTheme.colorScheme.primary
        HistoryDayState.UNACHIEVED -> MaterialTheme.colorScheme.error
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier)
}

@Composable
private fun HistoryDetailDialog(item: HistoryDialogItem, onDismiss: () -> Unit) {
    val snapshot = when (item) {
        is HistoryDialogItem.Execution -> item.value.snapshot
        is HistoryDialogItem.Period -> item.value.snapshot
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(snapshot.title) },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (snapshot.description.isNotBlank()) Text(snapshot.description)
                DetailLine(
                    R.string.label_category,
                    snapshot.categoryName ?: stringResource(R.string.label_uncategorized),
                )
                DetailLine(R.string.calendar_history_recurrence, recurrenceLabel(snapshot.recurrenceRule.type))
                DetailLine(
                    R.string.calendar_history_due,
                    snapshot.dueMinutes?.let {
                        stringResource(R.string.time_format, it / 60, it % 60)
                    } ?: stringResource(R.string.label_not_set),
                )
                when (item) {
                    is HistoryDialogItem.Execution -> {
                        DetailLine(R.string.calendar_history_logical_date, item.value.logicalDate.toShortDate())
                        DetailLine(R.string.calendar_history_state, item.value.state.label())
                        item.value.actedAt?.let {
                            DetailLine(R.string.calendar_history_operation_time, formatEpochMillis(it))
                        }
                    }
                    is HistoryDialogItem.Period -> {
                        DetailLine(
                            R.string.calendar_history_period,
                            stringResource(
                                R.string.calendar_history_period_range,
                                item.value.periodStart.toShortDate(),
                                item.value.periodEnd.toShortDate(),
                            ),
                        )
                        DetailLine(
                            R.string.calendar_history_result,
                            stringResource(
                                R.string.calendar_history_result_count,
                                item.value.completedCount,
                                item.value.requiredCount,
                                stringResource(
                                    if (item.value.achieved) R.string.label_achieved else R.string.label_unachieved,
                                ),
                            ),
                        )
                    }
                }
                if (snapshot.notifications.isNotEmpty()) {
                    val notificationTexts = mutableListOf<String>()
                    for (notification in snapshot.notifications) {
                        notificationTexts += notificationDisplayText(
                            notification.relation,
                            notification.amount,
                            notification.unit,
                        )
                    }
                    DetailLine(
                        R.string.calendar_history_notifications,
                        notificationTexts.joinToString(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun DetailLine(@androidx.annotation.StringRes labelRes: Int, value: String) {
    Column {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
        Text(value)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthPickerDialog(
    displayedMonth: YearMonth,
    today: LocalDate,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = displayedMonth.atDay(1)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onSelect(YearMonth.from(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)))
                }
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) { DatePicker(state = state, title = { Text(stringResource(R.string.calendar_history_select_month)) }) }
}

@Composable
private fun ErrorArea(
    @androidx.annotation.StringRes messageRes: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(messageRes))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun historyEntrySupportingText(entry: HistoryEntry): String {
    val category = entry.snapshot.categoryName ?: stringResource(R.string.label_uncategorized)
    val due = entry.snapshot.dueMinutes?.let {
        stringResource(R.string.time_format, it / 60, it % 60)
    } ?: stringResource(R.string.label_not_set)
    return stringResource(R.string.calendar_history_entry_support, category, due, entry.state.label())
}

@Composable
private fun HistoryDayState.label(): String = stringResource(
    when (this) {
        HistoryDayState.IN_PROGRESS -> R.string.label_in_progress
        HistoryDayState.COMPLETED -> R.string.label_completed
        HistoryDayState.UNACHIEVED -> R.string.label_unachieved
    },
)

@Composable
private fun TodoState.label(): String = stringResource(
    when (this) {
        TodoState.PENDING -> R.string.label_unfinished
        TodoState.COMPLETED -> R.string.label_completed
        TodoState.SKIPPED -> R.string.label_skipped
        TodoState.MISSED -> R.string.label_unfinished
    },
)

@Composable
private fun recurrenceLabel(type: RecurrenceType): String = stringResource(
    when (type) {
        RecurrenceType.ONCE -> R.string.label_no_recurrence
        RecurrenceType.DAILY -> R.string.label_daily
        RecurrenceType.WEEKDAYS -> R.string.label_weekdays
        RecurrenceType.SELECTED_WEEKDAYS -> R.string.label_selected_weekdays
        RecurrenceType.MONTHLY_DAY -> R.string.label_monthly_day
        RecurrenceType.MONTH_END -> R.string.label_month_end
        RecurrenceType.EVERY_N_DAYS -> R.string.label_every_n_days
        RecurrenceType.WEEKLY_COUNT -> R.string.label_weekly_count
        RecurrenceType.MONTHLY_COUNT -> R.string.label_monthly_count
    },
)

@Composable
private fun weekdayShortLabel(day: DayOfWeek): String = stringResource(
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
private fun notificationDisplayText(
    relation: NotificationRelation,
    amount: Int,
    unit: NotificationUnit,
): String {
    if (relation == NotificationRelation.AT) return stringResource(R.string.notification_relation_at)
    val unitLabel = stringResource(
        when (unit) {
            NotificationUnit.MINUTE -> R.string.unit_minute
            NotificationUnit.HOUR -> R.string.unit_hour
            NotificationUnit.DAY -> R.string.unit_day
        },
    )
    return stringResource(
        if (relation == NotificationRelation.BEFORE) {
            R.string.notification_relation_before_format
        } else {
            R.string.notification_relation_after_format
        },
        amount,
        unitLabel,
    )
}

@Composable
private fun formatEpochMillis(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(stringResource(R.string.date_time_pattern), Locale.JAPANESE))

@Composable
private fun LocalDate.toShortDate(): String = format(
    DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_plain), Locale.JAPANESE),
)
