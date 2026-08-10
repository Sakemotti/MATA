package com.mochisofts.mata.ui.todolist

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationDrawer
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onAddTodo: () -> Unit,
    onEditTodo: (String) -> Unit,
    onDestination: (MataDestination) -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var readOnlyOccurrence by remember { mutableStateOf<TodoOccurrence?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodoListEffect.Message -> snackbarHostState.showSnackbar(effect.text)
                is TodoListEffect.Completed -> {
                    val result = snackbarHostState.showSnackbar(
                        message = "TODOを完了しました",
                        actionLabel = "元に戻す",
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoCompletion(effect.todoId, effect.logicalDate)
                    }
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MataNavigationDrawer(MataDestination.TODOS) { destination ->
                scope.launch { drawerState.close() }
                if (destination != MataDestination.TODOS) onDestination(destination)
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TODO") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "メニューを開く")
                        }
                    },
                    actions = {
                        if (state.mode == TodoListMode.DATE && state.isToday) {
                            IconButton(onClick = { viewModel.setShowCompleted(!state.showCompleted) }) {
                                Icon(
                                    if (state.showCompleted) Icons.Outlined.CheckCircle else Icons.Outlined.Check,
                                    contentDescription = if (state.showCompleted) {
                                        "完了済みTODOを非表示"
                                    } else {
                                        "完了済みTODOを表示"
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
                    text = { Text("TODOを追加") },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = state.mode.ordinal) {
                    TodoListMode.entries.forEach { mode ->
                        Tab(
                            selected = state.mode == mode,
                            onClick = { viewModel.setMode(mode) },
                            text = { Text(if (mode == TodoListMode.DATE) "日付" else "カテゴリ") },
                        )
                    }
                }
                when (state.mode) {
                    TodoListMode.DATE -> DateMode(
                        state = state,
                        onPrevious = viewModel::selectPreviousDate,
                        onNext = viewModel::selectNextDate,
                        onToday = viewModel::selectToday,
                        onPickDate = { showDatePicker = true },
                        onComplete = viewModel::complete,
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
                }) { Text("決定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") }
            },
        ) { DatePicker(pickerState) }
    }

    readOnlyOccurrence?.let { occurrence ->
        AlertDialog(
            onDismissRequest = { readOnlyOccurrence = null },
            title = { Text(occurrence.todo.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(occurrence.todo.description.ifBlank { "説明なし" })
                    Text(occurrence.category?.name ?: "カテゴリ未設定")
                    Text(if (occurrence.state == TodoState.COMPLETED) "完了" else "未完了")
                }
            },
            confirmButton = {
                TextButton(onClick = { readOnlyOccurrence = null }) { Text("閉じる") }
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
    onOpen: (TodoOccurrence) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "前日")
        }
        TextButton(onClick = onPickDate) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.isToday) "今日" else state.selectedDate.toJapaneseDate())
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "翌日")
        }
    }
    if (!state.isToday) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onToday) { Text("今日へ戻る") }
        }
    }
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("読み込み中…") }
    } else if (state.occurrences.isEmpty()) {
        EmptyTodos(if (state.isToday) "今日のTODOはありません" else "この日のTODOはありません")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.occurrences, key = { "${it.todo.id}:${it.logicalDate}" }) { occurrence ->
                TodoOccurrenceRow(occurrence, state.isToday, onComplete, onOpen)
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
            label = { Text("カテゴリ未設定") },
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
        EmptyTodos("このカテゴリにTODOはありません")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.categoryItems, key = { it.todo.id }) { item ->
                val occurrence = item.occurrence
                ListItem(
                    headlineContent = { Text(item.todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (item.todo.recurrenceType.name == "DAILY") "毎日" else item.todo.startDate.toJapaneseDate(),
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
    onComplete: (TodoOccurrence) -> Unit,
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(occurrence.category?.name ?: "カテゴリ未設定") },
                    )
                    occurrence.todo.dueMinutes?.let { minutes ->
                        Text("期限 ${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}")
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
        modifier = Modifier.clickable { onOpen(occurrence) },
    )
}

@Composable
private fun EmptyTodos(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun LocalDate.toJapaneseDate(): String =
    format(DateTimeFormatter.ofPattern("M月d日（E）", Locale.JAPANESE))
