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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
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
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.todo_editor_add_title
                            } else {
                                R.string.todo_editor_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !state.isSaving) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.content_description_delete_todo),
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = state.canSave && state.isDirty) {
                        Text(stringResource(R.string.action_save))
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
            Text(
                stringResource(R.string.todo_editor_section_basic_information),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.todo_editor_title_label)) },
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.character_counter_format, state.title.length, 100))
                },
                isError = state.title.trim().isEmpty() || state.title.length > 100,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::setDescription,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.todo_editor_description_label)) },
                minLines = 3,
                supportingText = {
                    Text(stringResource(R.string.character_counter_format, state.description.length, 1000))
                },
                isError = state.description.length > 1000,
            )
            CategorySelector(state, viewModel::setCategory)

            Text(
                stringResource(R.string.todo_editor_section_schedule),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = state.startDate.format(
                    DateTimeFormatter.ofPattern(
                        stringResource(R.string.date_pattern_full),
                        Locale.JAPANESE,
                    ),
                ),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                label = {
                    Text(
                        stringResource(
                            if (state.recurrenceType == RecurrenceType.ONCE) {
                                R.string.todo_editor_execution_date_label
                            } else {
                                R.string.todo_editor_start_date_label
                            },
                        ),
                    )
                },
                readOnly = true,
            )
            RecurrenceSelector(state.recurrenceType, viewModel::setRecurrence)
            DueTimeSelector(state.dueMinutes, viewModel::setDueMinutes)

            Text(
                stringResource(R.string.todo_editor_section_notification),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.todo_editor_no_notification),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.todo_editor_notification_placeholder),
                style = MaterialTheme.typography.bodySmall,
            )

            state.errorMessageRes?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error)
            }
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
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(pickerState) }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.dialog_discard_changes_title)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
            confirmButton = {
                TextButton(onClick = onBack) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_continue_editing))
                }
            },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_todo_title)) },
            text = { Text(stringResource(R.string.dialog_delete_todo_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(state: TodoEditorUiState, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = state.categories.firstOrNull { it.id == state.categoryId }?.name
        ?: stringResource(R.string.label_uncategorized)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.label_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_uncategorized)) },
                onClick = { onSelect(null); expanded = false },
            )
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
            value = stringResource(
                if (value == RecurrenceType.ONCE) R.string.label_no_recurrence else R.string.label_daily,
            ),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.todo_editor_recurrence_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_no_recurrence)) },
                onClick = { onSelect(RecurrenceType.ONCE); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_daily)) },
                onClick = { onSelect(RecurrenceType.DAILY); expanded = false },
            )
        }
    }
}

@Composable
private fun DueTimeSelector(value: Int?, onChange: (Int?) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.todo_editor_due_time_label))
            Text(
                value?.let { stringResource(R.string.time_format, it / 60, it % 60) }
                    ?: stringResource(R.string.label_not_set),
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
            }) { Text(stringResource(R.string.action_change)) }
        }
    }
}
