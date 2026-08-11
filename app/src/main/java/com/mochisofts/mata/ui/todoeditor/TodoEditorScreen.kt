package com.mochisofts.mata.ui.todoeditor

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.nextOccurrences
import com.mochisofts.mata.domain.model.recurrencePeriod
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DatePickerTarget {
    START,
    END,
}

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
    var dateField by remember { mutableStateOf<DatePickerTarget?>(null) }
    var showRecurrenceSheet by remember { mutableStateOf(false) }
    var showNotificationPermissionRationale by remember { mutableStateOf(false) }
    var showPastNotificationWarning by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.notificationPermissionRequestFinished()
    }
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshNotificationStatus()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationStatus()
    }

    fun requestBack() {
        if (state.isDirty) showDiscardDialog = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodoEditorEffect.Saved -> onSaved(effect.isNew)
                TodoEditorEffect.Deleted -> onSaved(false)
                TodoEditorEffect.ExplainNotificationPermission -> {
                    showNotificationPermissionRationale = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.todo_editor_add_title
                            else R.string.todo_editor_edit_title,
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
                    TextButton(
                        onClick = {
                            if (state.hasPastNotificationForCurrentOccurrence) {
                                showPastNotificationWarning = true
                            } else {
                                viewModel.save()
                            }
                        },
                        enabled = state.canSave && state.isDirty,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.padding(32.dp))
            }
        } else {
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
                DateField(
                    value = state.startDate,
                    labelRes = if (state.recurrenceType == RecurrenceType.ONCE) {
                        R.string.todo_editor_execution_date_label
                    } else {
                        R.string.todo_editor_start_date_label
                    },
                    onClick = { dateField = DatePickerTarget.START },
                )
                RecurrenceSelector(state.recurrenceType) { showRecurrenceSheet = true }
                RecurrenceParameters(state, viewModel)

                if (state.recurrenceType != RecurrenceType.ONCE) {
                    val selectedEndDate = state.endDate
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.todo_editor_specify_end_date))
                            Text(
                                if (selectedEndDate == null) {
                                    stringResource(R.string.todo_editor_indefinite)
                                } else {
                                    selectedEndDate.toJapaneseDate()
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = selectedEndDate != null,
                            onCheckedChange = { enabled ->
                                viewModel.setEndDate(state.startDate.takeIf { enabled })
                            },
                        )
                    }
                    selectedEndDate?.let { endDate ->
                        DateField(
                            value = endDate,
                            labelRes = R.string.todo_editor_end_date_label,
                            isError = endDate.isBefore(state.startDate),
                            onClick = { dateField = DatePickerTarget.END },
                        )
                        if (endDate.isBefore(state.startDate)) {
                            Text(
                                stringResource(R.string.error_todo_end_date_before_start),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                SchedulePreview(state)
                DueTimeSelector(state.dueMinutes, viewModel::setDueMinutes)

                NotificationEditorSection(
                    state = state,
                    viewModel = viewModel,
                    onOpenNotificationSettings = {
                        systemSettingsLauncher.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            systemSettingsLauncher.launch(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                )

                state.errorMessageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
                if (state.isSaving) CircularProgressIndicator()
            }
        }
    }

    dateField?.let { target ->
        val selectedDate = if (target == DatePickerTarget.START) state.startDate else state.endDate ?: state.startDate
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == DatePickerTarget.START) viewModel.setStartDate(date)
                        else viewModel.setEndDate(date)
                    }
                    dateField = null
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { dateField = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(pickerState) }
    }

    if (showRecurrenceSheet) {
        RecurrenceBottomSheet(
            selected = state.recurrenceType,
            onSelect = { type ->
                viewModel.setRecurrence(type)
                showRecurrenceSheet = false
            },
            onDismiss = { showRecurrenceSheet = false },
        )
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
    if (showNotificationPermissionRationale) {
        AlertDialog(
            onDismissRequest = {
                showNotificationPermissionRationale = false
                viewModel.notificationPermissionRequestFinished()
            },
            title = { Text(stringResource(R.string.notification_permission_dialog_title)) },
            text = { Text(stringResource(R.string.notification_permission_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationPermissionRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.notificationPermissionRequestFinished()
                    }
                }) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNotificationPermissionRationale = false
                    viewModel.notificationPermissionRequestFinished()
                }) {
                    Text(stringResource(R.string.action_not_now))
                }
            },
        )
    }
    if (showPastNotificationWarning) {
        AlertDialog(
            onDismissRequest = { showPastNotificationWarning = false },
            title = { Text(stringResource(R.string.todo_editor_past_notification_title)) },
            text = { Text(stringResource(R.string.todo_editor_past_notification_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showPastNotificationWarning = false
                    viewModel.save()
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPastNotificationWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DateField(
    value: LocalDate,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
    isError: Boolean = false,
) {
    val label = stringResource(labelRes)
    val displayValue = value.toJapaneseDate()
    val fieldContentDescription = stringResource(
        R.string.content_description_field_value,
        label,
        displayValue,
    )
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            label = { Text(label) },
            readOnly = true,
            isError = isError,
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = fieldContentDescription
                }
                .clickable(role = Role.Button, onClick = onClick),
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
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_uncategorized)) },
                onClick = { onSelect(null); expanded = false },
            )
            state.categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { onSelect(category.id); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun RecurrenceSelector(value: RecurrenceType, onClick: () -> Unit) {
    val label = stringResource(R.string.todo_editor_recurrence_label)
    val displayValue = recurrenceLabel(value)
    val fieldContentDescription = stringResource(
        R.string.content_description_field_value,
        label,
        displayValue,
    )
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
        Box(
            Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = fieldContentDescription
                }
                .clickable(role = Role.Button, onClick = onClick),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrenceBottomSheet(
    selected: RecurrenceType,
    onSelect: (RecurrenceType) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.todo_editor_recurrence_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        RecurrenceType.entries.forEach { type ->
            ListItem(
                headlineContent = { Text(recurrenceLabel(type)) },
                trailingContent = {
                    if (type == selected) Text(stringResource(R.string.label_completed))
                },
                modifier = Modifier.clickable { onSelect(type) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceParameters(state: TodoEditorUiState, viewModel: TodoEditorViewModel) {
    when (state.recurrenceType) {
        RecurrenceType.SELECTED_WEEKDAYS -> {
            Text(stringResource(R.string.todo_editor_selected_weekdays_label))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in state.selectedWeekdays,
                        onClick = { viewModel.toggleWeekday(day) },
                        label = { Text(weekdayLabel(day)) },
                    )
                }
            }
            if (state.selectedWeekdays.isEmpty()) {
                Text(
                    stringResource(R.string.error_todo_recurrence_rule_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        RecurrenceType.MONTHLY_DAY -> NumberSelector(
            value = state.monthlyDay,
            range = 1..31,
            label = stringResource(R.string.todo_editor_monthly_day_label),
            formatRes = R.string.todo_editor_day_count_format,
            onSelect = viewModel::setMonthlyDay,
        )
        RecurrenceType.EVERY_N_DAYS -> OutlinedTextField(
            value = state.intervalDaysInput,
            onValueChange = viewModel::setIntervalDays,
            label = { Text(stringResource(R.string.todo_editor_interval_days_label)) },
            suffix = { Text(stringResource(R.string.unit_day)) },
            singleLine = true,
            isError = state.intervalDaysInput.toIntOrNull() !in 1..999,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        RecurrenceType.WEEKLY_COUNT -> NumberSelector(
            value = state.weeklyCount,
            range = 1..7,
            label = stringResource(R.string.todo_editor_weekly_count_label),
            formatRes = R.string.todo_editor_count_format,
            onSelect = viewModel::setWeeklyCount,
        )
        RecurrenceType.MONTHLY_COUNT -> NumberSelector(
            value = state.monthlyCount,
            range = 1..31,
            label = stringResource(R.string.todo_editor_monthly_count_label),
            formatRes = R.string.todo_editor_count_format,
            onSelect = viewModel::setMonthlyCount,
        )
        else -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberSelector(
    value: Int,
    range: IntRange,
    label: String,
    @StringRes formatRes: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(formatRes, value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            range.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(formatRes, option)) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SchedulePreview(state: TodoEditorUiState) {
    Text(
        stringResource(R.string.todo_editor_schedule_preview_title),
        style = MaterialTheme.typography.titleMedium,
    )
    val todo = Todo(
        id = "",
        title = state.title,
        description = state.description,
        categoryId = state.categoryId,
        startDate = state.startDate,
        endDate = state.endDate.takeUnless { state.recurrenceType == RecurrenceType.ONCE },
        recurrenceRule = state.recurrenceRule,
        dueMinutes = state.dueMinutes,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = 0,
    )
    val previewFrom = maxOf(state.today, state.startDate)
    if (state.recurrenceType.isCountBased) {
        val period = todo.recurrencePeriod(previewFrom, state.weekStart)
        if (period == null) {
            Text(stringResource(R.string.todo_editor_no_upcoming_dates))
        } else {
            Text(
                stringResource(
                    R.string.todo_editor_count_preview_format,
                    period.startDate.toJapaneseDate(),
                    period.endDate.toJapaneseDate(),
                    period.requiredCount,
                ),
            )
        }
    } else {
        val dates = if (state.recurrenceRule.isValid()) {
            todo.nextOccurrences(previewFrom, 3)
        } else {
            emptyList()
        }
        if (dates.isEmpty()) {
            Text(stringResource(R.string.todo_editor_no_upcoming_dates))
        } else {
            dates.forEach { date -> Text(date.toJapaneseDate()) }
        }
        if (state.recurrenceType == RecurrenceType.WEEKDAYS) {
            Text(
                stringResource(R.string.todo_editor_holiday_provisional),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LocalDate.toJapaneseDate(): String = format(
    DateTimeFormatter.ofPattern(stringResource(R.string.date_pattern_full), Locale.JAPANESE),
)
