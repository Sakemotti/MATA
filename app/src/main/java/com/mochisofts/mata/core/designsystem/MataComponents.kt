package com.mochisofts.mata.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class MataStatusType {
    SUCCESS,
    NEUTRAL,
    ERROR,
    IN_PROGRESS,
    FUTURE,
}

object MataTodoListItemDefaults {
    val LeadingSlotWidth = 48.dp
    val ActionSlotWidth = 48.dp
    val StatusSlotWidth = 112.dp
}

@Composable
fun MataTodoListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    reserveLeadingSpace: Boolean = leadingContent != null,
    reserveTrailingSpace: Boolean = trailingContent != null,
    trailingSlotWidth: Dp = MataTodoListItemDefaults.ActionSlotWidth,
) {
    ListItem(
        headlineContent = headlineContent,
        modifier = modifier,
        supportingContent = supportingContent,
        leadingContent = if (reserveLeadingSpace) {
            {
                Box(
                    modifier = Modifier.width(MataTodoListItemDefaults.LeadingSlotWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    leadingContent?.invoke()
                }
            }
        } else {
            null
        },
        trailingContent = if (reserveTrailingSpace) {
            {
                Box(
                    modifier = Modifier.width(trailingSlotWidth),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    trailingContent?.invoke()
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun MataStatusLabel(
    text: String,
    icon: ImageVector,
    type: MataStatusType,
    modifier: Modifier = Modifier,
) {
    val colors = statusLabelColors(type)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.small,
        color = colors.container,
        contentColor = colors.content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun MataCategoryLabel(
    name: String,
    iconName: String,
    colorIndex: Int?,
    modifier: Modifier = Modifier,
) {
    val categoryColor = mataCategoryColor(colorIndex)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = categoryIcon(iconName),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = categoryColor,
            )
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun MataCompletionCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = onCheckedChange != null,
    modifier: Modifier = Modifier,
) {
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.mataColors.statusSuccess,
            checkmarkColor = MaterialTheme.mataColors.onStatusSuccess,
        ),
    )
}

@Composable
fun MataSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun statusLabelColors(type: MataStatusType): StatusLabelColors = when (type) {
    MataStatusType.SUCCESS -> StatusLabelColors(
        MaterialTheme.mataColors.statusSuccessContainer,
        MaterialTheme.mataColors.onStatusSuccessContainer,
    )
    MataStatusType.NEUTRAL -> StatusLabelColors(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MataStatusType.ERROR -> StatusLabelColors(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer,
    )
    MataStatusType.IN_PROGRESS -> StatusLabelColors(
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
    )
    MataStatusType.FUTURE -> StatusLabelColors(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class StatusLabelColors(val container: Color, val content: Color)
