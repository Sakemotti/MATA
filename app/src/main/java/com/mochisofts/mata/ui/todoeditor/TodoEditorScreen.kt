package com.mochisofts.mata.ui.todoeditor

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.domain.model.RecurrenceType
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorScreen(
    onBack: () -> Unit,
    onSaved: (Boolean) -> Unit,
    viewModel: TodoEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    fun requestBack() {
        if (state.isDirty) showDiscardDialog = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodoEditorEffect.Saved -> onSaved(effect.isNew)
                TodoEditorEffect.Deleted -> onSaved(false)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "TODOを追加" else "TODOを編集") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !state.isSaving) {
                            Icon(Icons.Outlined.Delete, contentDescription = "TODOを削除")
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave && state.isDirty) {
                        Text("保存")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("基本情報", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("タイトル（必須）") },
                singleLine = true,
                supportingText = { Text("${state.title.length} / 100") },
                isError = state.title.trim().isEmpty() || state.title.length > 100,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("説明") },
                minLines = 3,
                supportingText = { Text("${state.description.length} / 1000") },
                isError = state.description.length > 1000,
            )
            CategorySelector(state, viewModel::setCategory)

            Text("スケジュール", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.startDate.format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", Locale.JAPANESE)),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                label = { Text(if (state.recurrenceType == RecurrenceType.ONCE) "実行日" else "開始日") },
                readOnly = true,
            )
            RecurrenceSelector(state.recurrenceType, viewModel::setRecurrence)
            DueTimeSelector(state.dueMinutes, viewModel::setDueMinutes)

            Text("通知", style = MaterialTheme.typography.titleMedium)
            Text("通知なし", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("通知設定は次の実装単位で追加します", style = MaterialTheme.typography.bodySmall)

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        viewModel.setStartDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("決定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("キャンセル") } },
        ) { DatePicker(pickerState) }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("変更を破棄しますか？") },
            text = { Text("入力した内容は保存されません。") },
            confirmButton = { TextButton(onClick = onBack) { Text("破棄") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("編集を続ける") } },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("TODOを完全に削除しますか？") },
            text = { Text("TODOとすべての履歴が削除され、元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) { Text("完全削除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(state: TodoEditorUiState, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.categories.firstOrNull { it.id == state.categoryId }?.name ?: "カテゴリ未設定"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("カテゴリ") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("カテゴリ未設定") }, onClick = { onSelect(null); expanded = false })
            state.categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = { onSelect(category.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceSelector(value: RecurrenceType, onSelect: (RecurrenceType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = if (value == RecurrenceType.ONCE) "繰り返しなし" else "毎日",
            onValueChange = {},
            readOnly = true,
            label = { Text("繰り返し") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("繰り返しなし") }, onClick = { onSelect(RecurrenceType.ONCE); expanded = false })
            DropdownMenuItem(text = { Text("毎日") }, onClick = { onSelect(RecurrenceType.DAILY); expanded = false })
        }
    }
}

@Composable
private fun DueTimeSelector(value: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("期限時刻")
            Text(
                value?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "設定なし",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = value != null,
            onCheckedChange = { checked ->
                if (!checked) {
                    onChange(null)
                } else {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onChange(hour * 60 + minute) },
                        12,
                        0,
                        true,
                    ).show()
                }
            },
        )
        if (value != null) {
            TextButton(onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onChange(hour * 60 + minute) },
                    value / 60,
                    value % 60,
                    true,
                ).show()
            }) { Text("変更") }
        }
    }
}
