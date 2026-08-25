package com.mochisofts.mata.ui.todoeditor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.mataClickablePointer
import com.mochisofts.mata.domain.model.MAX_NOTIFICATIONS_PER_TODO
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.NotificationValidationError
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.validateNotifications
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class NotificationDraft(
    val id: String?,
    val relation: NotificationRelation,
    val amountInput: String,
    val unit: NotificationUnit,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NotificationEditorSection(
    state: TodoEditorUiState,
    viewModel: TodoEditorViewModel,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
) {
    var draft by remember { mutableStateOf<NotificationDraft?>(null) }

    Text(
        stringResource(R.string.todo_editor_section_notification),
        style = MaterialTheme.typography.titleMedium,
    )

    if (state.notifications.isNotEmpty() && !state.notificationSystemState.canPostNotifications) {
        NotificationWarning(
            text = stringResource(R.string.todo_editor_notification_permission_warning),
            action = stringResource(R.string.action_open_settings),
            onAction = onOpenNotificationSettings,
        )
    }
    if (
        state.notifications.isNotEmpty() &&
        state.notificationSystemState.exactAlarmRelevant &&
        !state.notificationSystemState.canScheduleExactAlarms
    ) {
        NotificationWarning(
            text = stringResource(R.string.todo_editor_inexact_alarm_warning),
            action = stringResource(R.string.action_alarm_settings),
            onAction = onOpenExactAlarmSettings,
        )
    }

    if (state.notifications.isEmpty()) {
        Text(
            stringResource(R.string.todo_editor_no_notification),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        state.notifications.forEachIndexed { index, notification ->
            if (index > 0) HorizontalDivider()
            ListItem(
                headlineContent = { Text(notificationLabel(notification)) },
                supportingContent = {
                    val preview = state.notificationPreviews[notification.id]
                    Text(
                        if (preview == null) {
                            stringResource(R.string.todo_editor_notification_invalid_preview)
                        } else {
                            stringResource(
                                R.string.todo_editor_notification_next_format,
                                preview.format(NOTIFICATION_DATE_TIME_FORMATTER),
                            )
                        },
                    )
                },
                trailingContent = {
                    IconButton(onClick = { viewModel.deleteNotification(notification.id) }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete_notification),
                        )
                    }
                },
                modifier = Modifier
                    .mataClickablePointer()
                    .clickable {
                        draft = NotificationDraft(
                            id = notification.id,
                            relation = notification.relation,
                            amountInput = notification.amount.toString(),
                            unit = notification.unit,
                        )
                    },
            )
        }
    }

    state.notificationErrors.firstOrNull()?.let { error ->
        Text(
            text = notificationErrorText(error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    TextButton(
        onClick = {
            draft = NotificationDraft(
                id = null,
                relation = NotificationRelation.BEFORE,
                amountInput = "30",
                unit = NotificationUnit.MINUTE,
            )
        },
        enabled = state.notifications.size < MAX_NOTIFICATIONS_PER_TODO,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text(stringResource(R.string.action_add_notification))
    }

    draft?.let { currentDraft ->
        val amount = if (currentDraft.relation == NotificationRelation.AT) {
            0
        } else {
            currentDraft.amountInput.toIntOrNull() ?: 0
        }
        val candidate = TodoNotification(
            id = currentDraft.id ?: "draft",
            relation = currentDraft.relation,
            amount = amount,
            unit = if (currentDraft.relation == NotificationRelation.AT) {
                NotificationUnit.MINUTE
            } else {
                currentDraft.unit
            },
        )
        val candidateList = state.notifications.filterNot { it.id == currentDraft.id } + candidate
        val errors = validateNotifications(candidateList, state.dueMinutes, state.effectiveEndHour)

        ModalBottomSheet(onDismissRequest = { draft = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(
                        if (currentDraft.id == null) R.string.todo_editor_add_notification_title
                        else R.string.todo_editor_edit_notification_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                FlowRow(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NotificationRelation.entries.forEach { relation ->
                        FilterChip(
                            selected = currentDraft.relation == relation,
                            onClick = { draft = currentDraft.copy(relation = relation) },
                            enabled = relation != NotificationRelation.AFTER || state.dueMinutes != null,
                            label = { Text(relationName(relation)) },
                            modifier = Modifier.mataClickablePointer(
                                relation != NotificationRelation.AFTER || state.dueMinutes != null,
                            ),
                        )
                    }
                }
                if (currentDraft.relation != NotificationRelation.AT) {
                    OutlinedTextField(
                        value = currentDraft.amountInput,
                        onValueChange = { value ->
                            draft = currentDraft.copy(
                                amountInput = value.filter(Char::isDigit).take(3),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.todo_editor_notification_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = NotificationValidationError.INVALID_AMOUNT in errors,
                    )
                    FlowRow(
                        modifier = Modifier.focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NotificationUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = currentDraft.unit == unit,
                                onClick = { draft = currentDraft.copy(unit = unit) },
                                label = { Text(unitName(unit)) },
                                modifier = Modifier.mataClickablePointer(),
                            )
                        }
                    }
                }
                errors.firstOrNull()?.let { error ->
                    Text(
                        notificationErrorText(error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.upsertNotification(
                            id = currentDraft.id,
                            relation = currentDraft.relation,
                            amount = amount,
                            unit = currentDraft.unit,
                        )
                        draft = null
                    },
                    enabled = errors.isEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

@Composable
private fun NotificationWarning(text: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun notificationLabel(notification: TodoNotification): String = when (notification.relation) {
    NotificationRelation.AT -> stringResource(R.string.notification_relation_at)
    NotificationRelation.BEFORE -> stringResource(
        R.string.notification_relation_before_format,
        notification.amount,
        unitName(notification.unit),
    )
    NotificationRelation.AFTER -> stringResource(
        R.string.notification_relation_after_format,
        notification.amount,
        unitName(notification.unit),
    )
}

@Composable
private fun relationName(relation: NotificationRelation): String = stringResource(
    when (relation) {
        NotificationRelation.BEFORE -> R.string.notification_relation_before
        NotificationRelation.AT -> R.string.notification_relation_at
        NotificationRelation.AFTER -> R.string.notification_relation_after
    },
)

@Composable
private fun unitName(unit: NotificationUnit): String = stringResource(
    when (unit) {
        NotificationUnit.MINUTE -> R.string.unit_minute
        NotificationUnit.HOUR -> R.string.unit_hour
        NotificationUnit.DAY -> R.string.unit_day
    },
)

@Composable
private fun notificationErrorText(error: NotificationValidationError): String = stringResource(
    when (error) {
        NotificationValidationError.TOO_MANY -> R.string.error_todo_notification_too_many
        NotificationValidationError.INVALID_AMOUNT -> R.string.error_todo_notification_amount_invalid
        NotificationValidationError.DUPLICATE -> R.string.error_todo_notification_duplicate
        NotificationValidationError.AFTER_REQUIRES_DEADLINE ->
            R.string.error_todo_notification_after_requires_deadline
        NotificationValidationError.AFTER_DAY_END -> R.string.error_todo_notification_after_day_end
    },
)

private val NOTIFICATION_DATE_TIME_FORMATTER =
    DateTimeFormatter.ofPattern("yyyy年M月d日（E）H:mm", Locale.JAPANESE)
