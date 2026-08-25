package com.mochisofts.mata.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataCategoryLabel
import com.mochisofts.mata.core.designsystem.mataPageKeyScroll
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.TodoNotification

data class TodoDetailCategory(
    val name: String,
    val iconName: String?,
    val colorIndex: Int?,
)

data class TodoDetailField(
    val label: String,
    val value: String,
)

data class TodoDetailModalData(
    val title: String,
    val description: String,
    val category: TodoDetailCategory,
    val fields: List<TodoDetailField>,
)

@Composable
fun TodoDetailModal(
    data: TodoDetailModalData,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .mataPageKeyScroll(scrollState)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodoDetailLine(
                    label = stringResource(R.string.todo_editor_description_label),
                    value = data.description.ifBlank {
                        stringResource(R.string.todo_description_empty)
                    },
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.label_category),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    MataCategoryLabel(
                        name = data.category.name,
                        iconName = data.category.iconName ?: "CategoryOff",
                        colorIndex = data.category.colorIndex,
                    )
                }
                data.fields.forEach { field ->
                    TodoDetailLine(field.label, field.value)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
fun todoNotificationSettingsText(notifications: List<TodoNotification>): String {
    if (notifications.isEmpty()) return stringResource(R.string.label_not_set)
    val labels = mutableListOf<String>()
    for (notification in notifications) {
        labels += when (notification.relation) {
            NotificationRelation.AT -> stringResource(R.string.notification_relation_at)
            NotificationRelation.BEFORE,
            NotificationRelation.AFTER,
            -> {
                val unit = stringResource(
                    when (notification.unit) {
                        NotificationUnit.MINUTE -> R.string.unit_minute
                        NotificationUnit.HOUR -> R.string.unit_hour
                        NotificationUnit.DAY -> R.string.unit_day
                    },
                )
                stringResource(
                    if (notification.relation == NotificationRelation.BEFORE) {
                        R.string.notification_relation_before_format
                    } else {
                        R.string.notification_relation_after_format
                    },
                    notification.amount,
                    unit,
                )
            }
        }
    }
    return labels.joinToString()
}

@Composable
private fun TodoDetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value)
    }
}
