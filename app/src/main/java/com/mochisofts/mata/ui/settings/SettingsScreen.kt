package com.mochisofts.mata.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationDrawer
import com.mochisofts.mata.domain.model.AppTheme
import java.time.DayOfWeek
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDestination: (MataDestination) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showEndHourSheet by remember { mutableStateOf(false) }
    var showWeekStartSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MataNavigationDrawer(MataDestination.SETTINGS) { destination ->
                scope.launch {
                    drawerState.close()
                    if (destination != MataDestination.SETTINGS) onDestination(destination)
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.content_description_open_menu),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            when {
                state.isLoading -> {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.hasLoadError -> {
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.settings_loading_error))
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                else -> {
                    val settingsEnabled = state.savingSetting == null
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SettingsSectionHeader(R.string.settings_section_general)
                        SettingsValueRow(
                            title = stringResource(R.string.settings_end_hour_title),
                            value = stringResource(R.string.hour_format, state.endHour),
                            description = stringResource(R.string.settings_end_hour_description),
                            isSaving = state.savingSetting == SavingSetting.END_HOUR,
                            enabled = settingsEnabled,
                            onClick = { showEndHourSheet = true },
                        )
                        Text(
                            text = if (state.endHour == 0) {
                                stringResource(R.string.category_day_boundary_midnight)
                            } else {
                                stringResource(
                                    R.string.category_day_boundary_format,
                                    state.endHour,
                                    state.endHour - 1,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_week_start_title),
                            value = weekdayName(state.weekStart),
                            description = stringResource(R.string.settings_week_start_description),
                            isSaving = state.savingSetting == SavingSetting.WEEK_START,
                            enabled = settingsEnabled,
                            onClick = { showWeekStartSheet = true },
                        )

                        SettingsSectionHeader(R.string.settings_section_display)
                        ShowCompletedRow(
                            checked = state.showCompleted,
                            isSaving = state.savingSetting == SavingSetting.SHOW_COMPLETED,
                            enabled = settingsEnabled,
                            onCheckedChange = viewModel::setShowCompleted,
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_theme_title),
                            value = themeName(state.theme),
                            description = stringResource(R.string.settings_theme_description),
                            isSaving = state.savingSetting == SavingSetting.THEME,
                            enabled = settingsEnabled,
                            onClick = { showThemeSheet = true },
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showEndHourSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.settings_end_hour_title),
            options = (0..23).toList(),
            selected = state.endHour,
            label = { stringResource(R.string.hour_format, it) },
            onSelect = {
                showEndHourSheet = false
                viewModel.setEndHour(it)
            },
            onDismiss = { showEndHourSheet = false },
        )
    }
    if (showWeekStartSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.settings_week_start_title),
            options = DayOfWeek.entries,
            selected = state.weekStart,
            label = { weekdayName(it) },
            onSelect = {
                showWeekStartSheet = false
                viewModel.setWeekStart(it)
            },
            onDismiss = { showWeekStartSheet = false },
        )
    }
    if (showThemeSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.settings_theme_title),
            options = AppTheme.entries,
            selected = state.theme,
            label = { themeName(it) },
            onSelect = {
                showThemeSheet = false
                viewModel.setTheme(it)
            },
            onDismiss = { showThemeSheet = false },
        )
    }
}

@Composable
private fun SettingsSectionHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    description: String,
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(value)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun ShowCompletedRow(
    checked: Boolean,
    isSaving: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_completed_title)) },
        supportingContent = { Text(stringResource(R.string.settings_show_completed_description)) },
        trailingContent = {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Switch(checked = checked, onCheckedChange = null)
            }
        },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionBottomSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                ListItem(
                    headlineContent = { Text(label(option)) },
                    trailingContent = {
                        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                    modifier = Modifier.selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) },
                    ),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun weekdayName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.weekday_monday_full
        DayOfWeek.TUESDAY -> R.string.weekday_tuesday_full
        DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday_full
        DayOfWeek.THURSDAY -> R.string.weekday_thursday_full
        DayOfWeek.FRIDAY -> R.string.weekday_friday_full
        DayOfWeek.SATURDAY -> R.string.weekday_saturday_full
        DayOfWeek.SUNDAY -> R.string.weekday_sunday_full
    },
)

@Composable
private fun themeName(theme: AppTheme): String = stringResource(
    when (theme) {
        AppTheme.SYSTEM -> R.string.settings_theme_system
        AppTheme.LIGHT -> R.string.settings_theme_light
        AppTheme.DARK -> R.string.settings_theme_dark
    },
)
