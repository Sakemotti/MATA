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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.mataClickablePointer

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
        MataNavigationDrawerContent(selected = selected, onSelect = onSelect)
    }
}

@Composable
fun MataNavigationDrawerContent(
    selected: MataDestination,
    onSelect: (MataDestination) -> Unit,
) {
    Column(Modifier.fillMaxWidth().focusGroup()) {
        Text(
            text = stringResource(R.string.app_name),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
        HorizontalDivider()
        MataDestination.entries.forEach { destination ->
            NavigationDrawerItem(
                label = { Text(stringResource(destination.labelRes)) },
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .mataClickablePointer(),
            )
        }
    }
}

@Composable
fun MataNavigationRail(
    selected: MataDestination,
    onSelect: (MataDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        modifier = modifier.focusGroup(),
    ) {
        Spacer(Modifier.height(12.dp))
        MataDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) },
                alwaysShowLabel = true,
                modifier = Modifier.mataClickablePointer(),
            )
        }
    }
}

private val MataDestination.icon
    get() = when (this) {
        MataDestination.TODOS -> Icons.Outlined.Checklist
        MataDestination.CALENDAR -> Icons.Outlined.CalendarMonth
        MataDestination.CATEGORIES -> Icons.Outlined.Category
        MataDestination.ARCHIVE -> Icons.Outlined.Archive
        MataDestination.SETTINGS -> Icons.Outlined.Settings
    }
