package com.mochisofts.mata.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationDrawer
import com.mochisofts.mata.core.designsystem.CategoryColorNameResIds
import com.mochisofts.mata.core.designsystem.CategoryIconOptions
import com.mochisofts.mata.core.designsystem.CategoryLightColors
import com.mochisofts.mata.core.designsystem.categoryIcon
import com.mochisofts.mata.domain.model.Category
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDestination: (MataDestination) -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MataNavigationDrawer(MataDestination.CATEGORIES) { destination ->
                scope.launch { drawerState.close() }
                if (destination != MataDestination.CATEGORIES) onDestination(destination)
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.category_management_title)) },
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
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_category)) },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Category, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.label_uncategorized)) },
                        supportingContent = {
                            Column {
                                Text(stringResource(R.string.category_default_end_time))
                                Text(stringResource(R.string.category_use_common_setting))
                            }
                        },
                    )
                    HorizontalDivider()
                }
                if (categories.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(stringResource(R.string.category_empty_message))
                            TextButton(onClick = onAdd) {
                                Text(stringResource(R.string.action_add_category))
                            }
                        }
                    }
                } else {
                    items(categories, key = Category::id) { category ->
                        CategoryListRow(category = category, onClick = { onEdit(category.id) })
                        HorizontalDivider()
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }
}

@Composable
private fun CategoryListRow(category: Category, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CategoryLightColors[category.colorIndex].copy(alpha = 0.14f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    categoryIcon(category.iconName),
                    contentDescription = null,
                    tint = CategoryLightColors[category.colorIndex],
                )
            }
        },
        headlineContent = { Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(stringResource(R.string.category_end_time_format, category.endHour))
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    onBack: () -> Unit,
    onSaved: (Boolean) -> Unit,
    viewModel: CategoryEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    fun requestBack() {
        if (state.isDirty) showDiscardDialog = true else onBack()
    }
    BackHandler(onBack = ::requestBack)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryEditorEffect.Saved -> onSaved(effect.isNew)
                CategoryEditorEffect.Deleted -> onSaved(false)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.category_editor_add_title
                            } else {
                                R.string.category_editor_edit_title
                            },
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
                                contentDescription = stringResource(R.string.content_description_delete_category),
                            )
                        }
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.canSave && state.isDirty,
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                CategoryPreview(state)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.category_name_required_label)) },
                    supportingText = {
                        Text(stringResource(R.string.character_counter_format, state.name.length, 30))
                    },
                    isError = state.name.length > 30,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryLightColors.chunked(4).forEach { rowColors ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            rowColors.forEach { color ->
                                val index = CategoryLightColors.indexOf(color)
                                ColorOption(
                                    color = color,
                                    label = stringResource(CategoryColorNameResIds[index]),
                                    selected = state.colorIndex == index,
                                    onClick = { viewModel.setColor(index) },
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.category_icon_label), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CategoryIconOptions, key = { it.id }) { option ->
                        val label = stringResource(option.labelRes)
                        OutlinedCard(
                            onClick = { viewModel.setIcon(option.id) },
                            border = if (state.iconName == option.id) {
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                CardDefaults.outlinedCardBorder()
                            },
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(option.imageVector, contentDescription = label)
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                EndHourSelector(state.endHour, viewModel::setEndHour)
                Text(
                    if (state.endHour == 0) {
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
                )
                state.errorMessageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
                if (state.isSaving) CircularProgressIndicator()
            }
        }
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
            title = { Text(stringResource(R.string.dialog_delete_category_title)) },
            text = { Text(stringResource(R.string.dialog_delete_category_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CategoryPreview(state: CategoryEditorUiState) {
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    categoryIcon(state.iconName),
                    contentDescription = null,
                    tint = CategoryLightColors[state.colorIndex],
                )
            },
            headlineContent = {
                Text(state.name.ifBlank { stringResource(R.string.category_name_preview) })
            },
            supportingContent = {
                Text(stringResource(R.string.category_end_time_format, state.endHour))
            },
        )
    }
}

@Composable
private fun ColorOption(
    color: Color,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else color),
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.content_description_selected_option, label),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndHourSelector(value: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(R.string.hour_format, value),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category_end_hour_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            (0..23).forEach { hour ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.hour_format, hour)) },
                    onClick = { onSelect(hour); expanded = false },
                )
            }
        }
    }
}
