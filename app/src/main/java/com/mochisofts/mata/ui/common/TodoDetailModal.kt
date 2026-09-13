package com.mochisofts.mata.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val scrollState = rememberScrollState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title) },
        text = {
            TodoDetailContent(
                data = data,
                modifier = Modifier
                    .fillMaxWidth()
                    .mataPageKeyScroll(scrollState)
                    .verticalScroll(scrollState),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            if (onEdit != null || onDelete != null) {
                androidx.compose.foundation.layout.Row {
                    onEdit?.let { action ->
                        TextButton(onClick = action) {
                            Text(stringResource(R.string.action_edit))
                        }
                    }
                    onDelete?.let { action ->
                        TextButton(onClick = action) {
                            Text(stringResource(R.string.action_delete_permanently))
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoDetailFullScreen(
    data: TodoDetailModalData,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                ReadOnlyDetailTopAppBar(
                    title = stringResource(R.string.todo_detail_title),
                    onClose = onDismiss,
                )
            },
        ) { padding ->
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .mataPageKeyScroll(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                TodoDefinitionCard(
                    data = data,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun TodoDefinitionCard(
    data: TodoDetailModalData,
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            sectionTitle?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TodoDetailContent(
                data = data,
                showTitle = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadOnlyDetailTopAppBar(
    title: String,
    onClose: () -> Unit,
) {
    Column {
        TopAppBar(
            title = { Text(title) },
            actions = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            },
        )
        HorizontalDivider()
    }
}

@Composable
private fun TodoDetailContent(
    data: TodoDetailModalData,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showTitle) {
            Text(data.title, style = MaterialTheme.typography.headlineSmall)
        }
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
