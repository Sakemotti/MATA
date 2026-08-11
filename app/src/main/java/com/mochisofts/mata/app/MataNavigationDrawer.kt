package com.mochisofts.mata.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.R

enum class MataDestination(@StringRes val labelRes: Int) {
    TODOS(R.string.nav_todo_list),
    CALENDAR(R.string.nav_calendar_history),
    CATEGORIES(R.string.nav_category_management),
    ARCHIVE(R.string.nav_archived_todos),
    SETTINGS(R.string.nav_settings),
}

@Composable
fun MataNavigationDrawer(
    selected: MataDestination,
    onSelect: (MataDestination) -> Unit,
) {
    ModalDrawerSheet {
        Text(
            text = stringResource(R.string.app_name),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
        HorizontalDivider()
        MataDestination.entries.forEach { destination ->
            val icon = when (destination) {
                MataDestination.TODOS -> Icons.Outlined.Checklist
                MataDestination.CALENDAR -> Icons.Outlined.CalendarMonth
                MataDestination.CATEGORIES -> Icons.Outlined.Category
                MataDestination.ARCHIVE -> Icons.Outlined.Archive
                MataDestination.SETTINGS -> Icons.Outlined.Settings
            }
            NavigationDrawerItem(
                label = { Text(stringResource(destination.labelRes)) },
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(icon, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
