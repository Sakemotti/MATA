package com.mochisofts.mata.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme

@Preview(name = "Fixed Light", widthDp = 360, heightDp = 760, showBackground = true)
@Preview(
    name = "Fixed Dark",
    widthDp = 360,
    heightDp = 760,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Font 200%",
    widthDp = 360,
    heightDp = 900,
    showBackground = true,
    fontScale = 2f,
)
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MataComponentGalleryPreview() {
    val appTheme = if (isSystemInDarkTheme()) AppTheme.DARK else AppTheme.LIGHT
    MataTheme(appTheme = appTheme, useDynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                MataCategoryLabel(
                    name = stringResource(R.string.category_name_preview),
                    iconName = "Home",
                    colorIndex = 8,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MataStatusLabel(
                        text = stringResource(R.string.label_completed),
                        icon = Icons.Outlined.CheckCircle,
                        type = MataStatusType.SUCCESS,
                    )
                    MataStatusLabel(
                        text = stringResource(R.string.label_skipped),
                        icon = Icons.Outlined.SkipNext,
                        type = MataStatusType.NEUTRAL,
                    )
                    MataStatusLabel(
                        text = stringResource(R.string.label_missed),
                        icon = Icons.Outlined.ErrorOutline,
                        type = MataStatusType.ERROR,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MataCompletionCheckbox(checked = true, onCheckedChange = {})
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.label_completed)) },
                    )
                }
                OutlinedTextField(
                    value = stringResource(R.string.category_name_preview),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.category_name_required_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
