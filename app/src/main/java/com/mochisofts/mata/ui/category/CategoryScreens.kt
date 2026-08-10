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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationDrawer
import com.mochisofts.mata.core.designsystem.CategoryColorNames
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
                    title = { Text("カテゴリ管理") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "メニューを開く")
                        }
                    },
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("カテゴリを追加") },
                )
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        leadingContent = { Icon(Icons.Outlined.Category, contentDescription = null) },
                        headlineContent = { Text("カテゴリ未設定") },
                        supportingContent = {
                            Column {
                                Text("一日の終了 0:00")
                                Text("設定画面の共通値を使用")
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
                            Text("カテゴリを追加してTODOを整理しましょう")
                            TextButton(onClick = onAdd) { Text("カテゴリを追加") }
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
        supportingContent = { Text("一日の終了 ${category.endHour}:00") },
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
                title = { Text(if (state.isNew) "カテゴリを追加" else "カテゴリを編集") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }, enabled = !state.isSaving) {
                            Icon(Icons.Outlined.Delete, contentDescription = "カテゴリを削除")
                        }
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.canSave && state.isDirty,
                    ) { Text("保存") }
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
                    label = { Text("カテゴリ名（必須）") },
                    supportingText = { Text("${state.name.length} / 30") },
                    isError = state.name.length > 30,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("色", style = MaterialTheme.typography.titleMedium)
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
                                    label = CategoryColorNames[index],
                                    selected = state.colorIndex == index,
                                    onClick = { viewModel.setColor(index) },
                                )
                            }
                        }
                    }
                }
                Text("アイコン", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CategoryIconOptions, key = { it.id }) { option ->
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
                                Icon(option.imageVector, contentDescription = option.label)
                                Text(option.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                EndHourSelector(state.endHour, viewModel::setEndHour)
                Text(
                    if (state.endHour == 0) {
                        "カレンダー日と同じ0:00から23:59までを1日として扱います"
                    } else {
                        "${state.endHour}:00の場合、翌日の${state.endHour - 1}:59までを前日分として扱います"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.isSaving) CircularProgressIndicator()
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("変更を破棄しますか？") },
            text = { Text("入力した内容は保存されません。") },
            confirmButton = { TextButton(onClick = onBack) { Text("破棄") } },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("編集を続ける") }
            },
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("カテゴリを削除しますか？") },
            text = { Text("所属するTODOはカテゴリ未設定へ移動します。この操作は元に戻せません。") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.delete() }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("キャンセル") }
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
            headlineContent = { Text(state.name.ifBlank { "カテゴリ名" }) },
            supportingContent = { Text("一日の終了 ${state.endHour}:00") },
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
                if (selected) Icon(Icons.Outlined.Check, contentDescription = "$label、選択中", tint = Color.White)
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
            value = "$value:00",
            onValueChange = {},
            readOnly = true,
            label = { Text("一日の終了時刻") },
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
                    text = { Text("$hour:00") },
                    onClick = { onSelect(hour); expanded = false },
                )
            }
        }
    }
}
