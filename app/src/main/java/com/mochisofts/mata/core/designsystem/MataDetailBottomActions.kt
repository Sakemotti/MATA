package com.mochisofts.mata.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.R

internal const val DETAIL_PRIMARY_ACTION_TAG = "detail_primary_action"
internal const val DETAIL_DELETE_ACTION_TAG = "detail_delete_action"

/** Shared fixed action area for current-definition and past-occurrence details. */
@Composable
fun MataDetailBottomActions(
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimary: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(MataCardLayout.CardSpacing),
            horizontalArrangement = Arrangement.spacedBy(MataCardLayout.CardSpacing),
        ) {
            onPrimary?.let { action ->
                Button(
                    onClick = action,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).testTag(DETAIL_PRIMARY_ACTION_TAG),
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(primaryIcon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(primaryLabel)
                }
            }
            onDelete?.let { action ->
                Button(
                    onClick = action,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).testTag(DETAIL_DELETE_ACTION_TAG),
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_delete_permanently))
                }
            }
        }
    }
}
