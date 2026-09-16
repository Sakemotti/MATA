package com.mochisofts.mata.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.navigation.MataAdaptiveNavigation
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.core.designsystem.navigation.MataAdaptiveLayoutInfo
import com.mochisofts.mata.core.designsystem.navigation.MataNavigationType
import com.mochisofts.mata.core.designsystem.CategoryColorNameResIds
import com.mochisofts.mata.core.designsystem.CategoryColorOptions
import com.mochisofts.mata.core.designsystem.CategoryIconOption
import com.mochisofts.mata.core.designsystem.CategoryIconOptions
import com.mochisofts.mata.core.designsystem.categoryIcon
import com.mochisofts.mata.core.designsystem.mataCategoryColor
import com.mochisofts.mata.core.designsystem.mataClickablePointer
import com.mochisofts.mata.core.designsystem.mataColors
import com.mochisofts.mata.core.designsystem.mataPageKeyScroll
import com.mochisofts.mata.core.designsystem.MataSnackbarHost
import com.mochisofts.mata.core.designsystem.MataCardLayout
import com.mochisofts.mata.core.designsystem.mataCardSegmentShape
import com.mochisofts.mata.core.designsystem.mataCardColor
import com.mochisofts.mata.core.designsystem.mataPageColor
import com.mochisofts.mata.domain.model.Category
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryListScreen(
    onDestination: (MataDestination) -> Unit,
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgeThreshold = with(density) { 64.dp.toPx() }
    val autoScrollStep = with(density) { 12.dp.toPx() }
    var draggedCategoryId by remember { mutableStateOf<String?>(null) }
    var draggedOffset by remember { mutableStateOf(0f) }
    var autoScrollDelta by remember { mutableStateOf(0f) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showWideDeleteDialog by remember { mutableStateOf(false) }
    var pendingEditorAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var useTwoPaneLayout by remember { mutableStateOf(false) }
    var highlightedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var highlightUntilEpochMillis by rememberSaveable { mutableStateOf(0L) }

    fun runAfterDiscardCheck(action: () -> Unit) {
        if (state.editor?.isSaving == true) return
        if (state.editor?.isDirty == true) {
            pendingEditorAction = action
            showDiscardDialog = true
        } else {
            action()
        }
    }

    fun requestCloseEditor() = runAfterDiscardCheck(viewModel::closeEditor)

    BackHandler(enabled = state.editor != null, onBack = ::requestCloseEditor)

    fun updateAutoScroll(draggedCenter: Float) {
        val layoutInfo = listState.layoutInfo
        autoScrollDelta = categoryAutoScrollDelta(
            draggedCenter = draggedCenter,
            viewportStart = layoutInfo.viewportStartOffset.toFloat(),
            viewportEnd = layoutInfo.viewportEndOffset.toFloat(),
            edgeThreshold = edgeThreshold,
            autoScrollStep = autoScrollStep,
        )
    }

    fun moveDraggedCategory(categoryId: String) {
        val layoutInfo = listState.layoutInfo
        val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.key == categoryId } ?: return
        val draggedTop = draggedItem.offset + draggedOffset
        val draggedCenter = draggedTop + draggedItem.size / 2f
        val visibleCategoryItems = layoutInfo.visibleItemsInfo.filter { item ->
            item.key is String &&
                item.key != categoryId &&
                state.categories.any { it.id == item.key }
        }
        val targetItem = visibleCategoryItems.firstOrNull { item ->
            draggedCenter >= item.offset && draggedCenter <= item.offset + item.size
        } ?: when {
            draggedCenter < (visibleCategoryItems.firstOrNull()?.offset ?: Int.MIN_VALUE) -> {
                visibleCategoryItems.firstOrNull()
            }
            draggedCenter > (
                visibleCategoryItems.lastOrNull()?.let { it.offset + it.size } ?: Int.MAX_VALUE
            ) -> visibleCategoryItems.lastOrNull()
            else -> null
        }
        if (targetItem != null &&
            viewModel.moveReorderingCategory(categoryId, targetItem.key as String)
        ) {
            draggedOffset = draggedTop - targetItem.offset
        }
        updateAutoScroll(draggedCenter)
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryListEffect.OrderSaved -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            R.string.category_reorder_saved,
                            effect.total,
                            effect.position,
                        ),
                    )
                }
                is CategoryListEffect.CategorySaved -> {
                    if (!useTwoPaneLayout) viewModel.closeEditor()
                    if (effect.isNew) {
                        highlightedCategoryId = effect.id
                        highlightUntilEpochMillis = System.currentTimeMillis() + CATEGORY_HIGHLIGHT_DURATION_MILLIS
                        val updated = viewModel.uiState.first { current ->
                            current.categories.any { it.id == effect.id }
                        }
                        val index = updated.categories.indexOfFirst { it.id == effect.id }
                        if (index >= 0) listState.animateScrollToItem(index)
                    }
                    snackbarHostState.showSnackbar(
                        resources.getString(
                            if (effect.isNew) {
                                R.string.category_added_message
                            } else {
                                R.string.category_updated_message
                            },
                        ),
                    )
                }
                CategoryListEffect.CategoryDeleted -> {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.category_deleted_message),
                    )
                }
                is CategoryListEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
            }
        }
    }

    LaunchedEffect(highlightedCategoryId, highlightUntilEpochMillis) {
        if (highlightedCategoryId == null) return@LaunchedEffect
        val remaining = highlightUntilEpochMillis - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        highlightedCategoryId = null
        highlightUntilEpochMillis = 0L
    }

    LaunchedEffect(draggedCategoryId, autoScrollDelta) {
        val categoryId = draggedCategoryId ?: return@LaunchedEffect
        while (autoScrollDelta != 0f) {
            val consumed = listState.scrollBy(autoScrollDelta)
            if (consumed == 0f) {
                autoScrollDelta = 0f
                break
            }
            draggedOffset += consumed
            moveDraggedCategory(categoryId)
            delay(16)
        }
    }

    MataAdaptiveNavigation(
        selected = MataDestination.CATEGORIES,
        drawerState = drawerState,
        onSelect = { destination ->
            runAfterDiscardCheck { onDestination(destination) }
        },
    ) { layoutInfo ->
        SideEffect { useTwoPaneLayout = layoutInfo.useTwoPane }
        LaunchedEffect(layoutInfo.useTwoPane) {
            if (draggedCategoryId != null) {
                autoScrollDelta = 0f
                draggedCategoryId = null
                draggedOffset = 0f
                viewModel.cancelReordering()
            }
        }

        if (!layoutInfo.useTwoPane && state.editor != null) {
            Box(Modifier.fillMaxSize()) {
                CategoryEditorScaffold(
                    state = requireNotNull(state.editor),
                    onBack = ::requestCloseEditor,
                    onNameChange = viewModel::setEditorName,
                    onColorChange = viewModel::setEditorColor,
                    onIconChange = viewModel::setEditorIcon,
                    onSave = viewModel::saveEditor,
                    onDelete = viewModel::deleteEditor,
                )
                MataSnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            return@MataAdaptiveNavigation
        }

        val pageColor = MaterialTheme.mataPageColor
        Scaffold(
            containerColor = pageColor,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.category_management_title)) },
                        navigationIcon = {
                            if (layoutInfo.navigationType == MataNavigationType.MODAL_DRAWER) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Outlined.Menu,
                                        contentDescription = stringResource(R.string.content_description_open_menu),
                                    )
                                }
                            }
                        },
                        actions = {
                            val editor = state.editor
                            if (layoutInfo.useTwoPane && editor != null) {
                                if (!editor.isNew) {
                                    IconButton(
                                        onClick = { showWideDeleteDialog = true },
                                        enabled = !editor.isSaving,
                                    ) {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = stringResource(
                                                R.string.content_description_delete_category,
                                            ),
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = viewModel::saveEditor,
                                    enabled = editor.canSave && editor.isDirty,
                                ) { Text(stringResource(R.string.action_save)) }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = pageColor,
                            scrolledContainerColor = pageColor,
                        ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            floatingActionButton = {
                if (!layoutInfo.useTwoPane) {
                    ExtendedFloatingActionButton(
                        modifier = Modifier.testTag(CATEGORY_ADD_FAB_TEST_TAG),
                        onClick = viewModel::openNewEditor,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_add_category)) },
                    )
                }
            },
            snackbarHost = { MataSnackbarHost(snackbarHostState) },
        ) { padding ->
            if (layoutInfo.useTwoPane) {
                CategoryTwoPaneContent(
                    layoutInfo = layoutInfo,
                    state = state,
                    listState = listState,
                    draggedCategoryId = draggedCategoryId,
                    draggedOffset = draggedOffset,
                    highlightedCategoryId = highlightedCategoryId,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onAdd = { runAfterDiscardCheck(viewModel::openNewEditor) },
                    onEdit = { id -> runAfterDiscardCheck { viewModel.openEditor(id) } },
                    onMoveUp = { id -> viewModel.moveCategoryOneStep(id, -1) },
                    onMoveDown = { id -> viewModel.moveCategoryOneStep(id, 1) },
                    onDragStart = { category ->
                        if (viewModel.startReordering()) {
                            draggedCategoryId = category.id
                            draggedOffset = 0f
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { category, delta ->
                        draggedOffset += delta
                        moveDraggedCategory(category.id)
                    },
                    onDragEnd = { category ->
                        autoScrollDelta = 0f
                        draggedCategoryId = null
                        draggedOffset = 0f
                        viewModel.finishReordering(category.id)
                    },
                    onDragCancel = {
                        autoScrollDelta = 0f
                        draggedCategoryId = null
                        draggedOffset = 0f
                        viewModel.cancelReordering()
                    },
                    onCloseEditor = ::requestCloseEditor,
                    viewModel = viewModel,
                )
            } else {
                CategoryListContent(
                    state = state,
                    listState = listState,
                    draggedCategoryId = draggedCategoryId,
                    draggedOffset = draggedOffset,
                    highlightedCategoryId = highlightedCategoryId,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    onAdd = viewModel::openNewEditor,
                    onEdit = viewModel::openEditor,
                    onMoveUp = { id -> viewModel.moveCategoryOneStep(id, -1) },
                    onMoveDown = { id -> viewModel.moveCategoryOneStep(id, 1) },
                    onDragStart = { category ->
                        if (viewModel.startReordering()) {
                            draggedCategoryId = category.id
                            draggedOffset = 0f
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { category, delta ->
                        draggedOffset += delta
                        moveDraggedCategory(category.id)
                    },
                    onDragEnd = { category ->
                        autoScrollDelta = 0f
                        draggedCategoryId = null
                        draggedOffset = 0f
                        viewModel.finishReordering(category.id)
                    },
                    onDragCancel = {
                        autoScrollDelta = 0f
                        draggedCategoryId = null
                        draggedOffset = 0f
                        viewModel.cancelReordering()
                    },
                    onRetry = viewModel::retryLoad,
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                pendingEditorAction = null
            },
            title = { Text(stringResource(R.string.dialog_discard_changes_title)) },
            text = { Text(stringResource(R.string.dialog_discard_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        val action = pendingEditorAction
                        pendingEditorAction = null
                        action?.invoke()
                    },
                ) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        pendingEditorAction = null
                    },
                ) { Text(stringResource(R.string.action_continue_editing)) }
            },
        )
    }
    if (showWideDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showWideDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_category_title)) },
            text = { Text(stringResource(R.string.dialog_delete_category_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWideDeleteDialog = false
                        viewModel.deleteEditor()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showWideDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

internal data class CategoryPaneWidths(
    val listWidthDp: Float,
    val editorWidthDp: Float,
)

internal fun categoryAutoScrollDelta(
    draggedCenter: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edgeThreshold: Float,
    autoScrollStep: Float,
): Float = when {
    draggedCenter < viewportStart + edgeThreshold -> -autoScrollStep
    draggedCenter > viewportEnd - edgeThreshold -> autoScrollStep
    else -> 0f
}

internal fun categoryPaneWidths(availableWidthDp: Float): CategoryPaneWidths {
    val gap = 24f
    val contentWidth = (availableWidthDp - gap).coerceAtLeast(0f)
    val listWidth = (contentWidth * 0.42f).coerceIn(320f, 400f)
    return CategoryPaneWidths(
        listWidthDp = listWidth,
        editorWidthDp = (contentWidth - listWidth).coerceAtLeast(392f),
    )
}

@Composable
private fun CategoryTwoPaneContent(
    layoutInfo: MataAdaptiveLayoutInfo,
    state: CategoryListUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    draggedCategoryId: String?,
    draggedOffset: Float,
    highlightedCategoryId: String?,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMoveUp: (String) -> Boolean,
    onMoveDown: (String) -> Boolean,
    onDragStart: (Category) -> Unit,
    onDrag: (Category, Float) -> Unit,
    onDragEnd: (Category) -> Unit,
    onDragCancel: () -> Unit,
    onCloseEditor: () -> Unit,
    viewModel: CategoryListViewModel,
) {
    val widths = layoutInfo.twoPaneHinge?.let { hinge ->
        CategoryPaneWidths(
            listWidthDp = hinge.startPaneWidthDp,
            editorWidthDp = hinge.endPaneWidthDp,
        )
    } ?: categoryPaneWidths(layoutInfo.availableContentWidthDp)
    val paneSpacing = (layoutInfo.twoPaneHinge?.gapDp ?: 24f).dp
    Row(
        modifier = modifier.padding(horizontal = layoutInfo.outerMarginDp.dp),
        horizontalArrangement = Arrangement.spacedBy(paneSpacing),
    ) {
        Box(Modifier.width(widths.listWidthDp.dp).fillMaxSize()) {
            CategoryListContent(
                state = state,
                listState = listState,
                draggedCategoryId = draggedCategoryId,
                draggedOffset = draggedOffset,
                highlightedCategoryId = highlightedCategoryId,
                modifier = Modifier.fillMaxSize(),
                onAdd = onAdd,
                onEdit = onEdit,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                onRetry = viewModel::retryLoad,
            )
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add_category)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
        Surface(
            modifier = Modifier.width(widths.editorWidthDp.dp).fillMaxSize(),
            tonalElevation = 1.dp,
        ) {
            val editor = state.editor
            if (editor == null) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.category_two_pane_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CategoryEditorScaffold(
                    state = editor,
                    onBack = onCloseEditor,
                    onNameChange = viewModel::setEditorName,
                    onColorChange = viewModel::setEditorColor,
                    onIconChange = viewModel::setEditorIcon,
                    onSave = viewModel::saveEditor,
                    onDelete = viewModel::deleteEditor,
                    showTopBar = false,
                )
            }
        }
    }
}

@Composable
private fun CategoryListContent(
    state: CategoryListUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    draggedCategoryId: String?,
    draggedOffset: Float,
    highlightedCategoryId: String?,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onMoveUp: (String) -> Boolean,
    onMoveDown: (String) -> Boolean,
    onDragStart: (Category) -> Unit,
    onDrag: (Category, Float) -> Unit,
    onDragEnd: (Category) -> Unit,
    onDragCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = modifier
            .testTag(CATEGORY_LIST_TEST_TAG)
            .mataPageKeyScroll(listState),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = MataCardLayout.PageHorizontalPadding,
            top = MataCardLayout.PageVerticalPadding,
            end = MataCardLayout.PageHorizontalPadding,
        ),
    ) {
        if (state.isLoading) {
            item(key = "loading") {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.hasLoadError) {
            item(key = "load_error") {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.category_list_load_error))
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        } else if (state.categories.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.category_empty_message))
                    TextButton(
                        onClick = onAdd,
                        modifier = Modifier.testTag(CATEGORY_EMPTY_ADD_TEST_TAG),
                    ) {
                        Text(stringResource(R.string.action_add_category))
                    }
                }
            }
        } else {
            items(state.categories, key = Category::id) { category ->
                val index = state.categories.indexOfFirst { it.id == category.id }
                val isDragging = draggedCategoryId == category.id
                Column(Modifier.animateItem(placementSpec = tween(durationMillis = 200))) {
                    CategoryListRow(
                        category = category,
                        index = index,
                        total = state.categories.size,
                        selected = state.editor?.categoryId == category.id,
                        highlighted = highlightedCategoryId == category.id,
                        isDragging = isDragging,
                        draggedOffset = if (isDragging) draggedOffset else 0f,
                        reorderEnabled = !state.isOrderSaving,
                        onClick = { onEdit(category.id) },
                        onMoveUp = { onMoveUp(category.id) },
                        onMoveDown = { onMoveDown(category.id) },
                        onDragStart = { onDragStart(category) },
                        onDrag = { onDrag(category, it) },
                        onDragEnd = { onDragEnd(category) },
                        onDragCancel = onDragCancel,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

internal const val CATEGORY_ADD_FAB_TEST_TAG = "category_add_fab"
internal const val CATEGORY_EMPTY_ADD_TEST_TAG = "category_empty_add"
internal const val CATEGORY_LIST_TEST_TAG = "category_list"
internal const val CATEGORY_ROW_TEST_TAG_PREFIX = "category_row_"
internal const val CATEGORY_REORDER_HANDLE_TEST_TAG_PREFIX = "category_reorder_handle_"
internal const val CATEGORY_HIGHLIGHT_DURATION_MILLIS = 2_500L
internal const val CATEGORY_ICON_LIST_TEST_TAG = "category_icon_list"
internal const val CATEGORY_ICON_PICKER_TEST_TAG = "category_icon_picker"
internal const val CATEGORY_ICON_SEARCH_TEST_TAG = "category_icon_search"
internal const val CATEGORY_ICON_RESULTS_TEST_TAG = "category_icon_results"
internal const val CATEGORY_ICON_OPTION_TEST_TAG_PREFIX = "category_icon_option_"
internal const val CATEGORY_ICON_GROUP_TEST_TAG_PREFIX = "category_icon_group_"

internal val CategoryColorIdKey = SemanticsPropertyKey<String>("CategoryColorId")
internal val CategoryColorArgbKey = SemanticsPropertyKey<Long>("CategoryColorArgb")
internal val CategoryIconIdKey = SemanticsPropertyKey<String>("CategoryIconId")
internal val CategoryIconLabelKey = SemanticsPropertyKey<String>("CategoryIconLabel")
internal val CategoryNewlyAddedKey = SemanticsPropertyKey<Boolean>("CategoryNewlyAdded")

@Composable
private fun CategoryListRow(
    category: Category,
    index: Int,
    total: Int,
    selected: Boolean,
    highlighted: Boolean,
    isDragging: Boolean,
    draggedOffset: Float,
    reorderEnabled: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Boolean,
    onMoveDown: () -> Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val categoryColor = mataCategoryColor(category.colorIndex)
    val elevation by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 0.dp,
        animationSpec = tween(durationMillis = 150),
        label = "category-row-elevation",
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.02f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "category-row-scale",
    )
    val iconLabel = stringResource(
        CategoryIconOptions.firstOrNull { it.id == category.iconName }?.labelRes
            ?: R.string.category_icon_category,
    )
    val positionLabel = stringResource(R.string.category_list_position, total, index + 1)
    val reorderLabel = stringResource(R.string.category_reorder_handle, category.name)
    val moveUpLabel = stringResource(R.string.category_move_up)
    val moveDownLabel = stringResource(R.string.category_move_down)
    val accessibilityActions = buildList {
        if (reorderEnabled && index > 0) {
            add(CustomAccessibilityAction(moveUpLabel, onMoveUp))
        }
        if (reorderEnabled && index < total - 1) {
            add(CustomAccessibilityAction(moveDownLabel, onMoveDown))
        }
    }

    Surface(
        modifier = Modifier
            .testTag(CATEGORY_ROW_TEST_TAG_PREFIX + category.id)
            .semantics { this[CategoryNewlyAddedKey] = highlighted }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = draggedOffset
                scaleX = scale
                scaleY = scale
            },
        shadowElevation = elevation,
        tonalElevation = 1.dp,
        shape = mataCardSegmentShape(index, total),
        color = MaterialTheme.mataCardColor,
    ) {
        Column {
            ListItem(
            colors = ListItemDefaults.colors(
                containerColor = when {
                    highlighted -> MaterialTheme.colorScheme.tertiaryContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
            ),
            leadingContent = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        categoryIcon(category.iconName),
                        contentDescription = iconLabel,
                        tint = categoryColor,
                    )
                }
            },
            headlineContent = {
                Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            trailingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(CATEGORY_REORDER_HANDLE_TEST_TAG_PREFIX + category.id)
                        .semantics {
                            contentDescription = reorderLabel
                            customActions = accessibilityActions
                        }
                        .mataClickablePointer(reorderEnabled)
                        .pointerInput(category.id, reorderEnabled) {
                            if (!reorderEnabled) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                            ) { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount.y)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DragHandle, contentDescription = null)
                }
            },
                modifier = Modifier
                    .mataClickablePointer(enabled = !isDragging)
                    .clickable(enabled = !isDragging, onClick = onClick)
                    .semantics {
                        this.selected = selected
                        stateDescription = positionLabel
                    },
            )
            if (index < total - 1) {
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditorScreen(
    onBack: () -> Unit,
    onSaved: (String, Boolean) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CategoryEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryColors = MaterialTheme.mataColors.categoryColors
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showIconPicker by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var iconSearchQuery by rememberSaveable(state.categoryId) { mutableStateOf("") }

    fun requestBack() {
        if (state.isDirty) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = !showIconPicker, onBack = ::requestBack)
    BackHandler(enabled = showIconPicker) { showIconPicker = false }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CategoryEditorEffect.Saved -> onSaved(effect.id, effect.isNew)
                CategoryEditorEffect.Deleted -> onDeleted()
            }
        }
    }

    if (showIconPicker) {
        CategoryIconPicker(
            selectedIconId = state.iconName,
            searchQuery = iconSearchQuery,
            onSearchQueryChange = { iconSearchQuery = it },
            onBack = { showIconPicker = false },
            onSelect = { iconId ->
                viewModel.setIcon(iconId)
                showIconPicker = false
            },
        )
        return
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
                            Icons.AutoMirrored.Outlined.ArrowBack,
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
                    shape = MataCardLayout.InputShape,
                    label = { Text(stringResource(R.string.category_name_required_label)) },
                    supportingText = {
                        Text(stringResource(R.string.character_counter_format, state.name.length, 30))
                    },
                    isError = state.name.length > 30,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = categoryColors[state.colorIndex],
                        unfocusedBorderColor = categoryColors[state.colorIndex],
                    ),
                )
                Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryColors.chunked(4).forEach { rowColors ->
                        Row(
                            Modifier.fillMaxWidth().focusGroup(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            rowColors.forEach { color ->
                                val index = categoryColors.indexOf(color)
                                ColorOption(
                                    color = color,
                                    contentColor = MaterialTheme.mataColors.onCategoryColors[index],
                                    colorId = CategoryColorOptions[index].id,
                                    label = stringResource(CategoryColorNameResIds[index]),
                                    selected = state.colorIndex == index,
                                    onClick = { viewModel.setColor(index) },
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.category_icon_label), style = MaterialTheme.typography.titleMedium)
                CategoryIconField(
                    selectedIconId = state.iconName,
                    enabled = !state.isSaving,
                    onClick = {
                        iconSearchQuery = ""
                        showIconPicker = true
                    },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorScaffold(
    state: CategoryEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onIconChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    showTopBar: Boolean = true,
) {
    var showDeleteDialog by remember(state.categoryId) { mutableStateOf(false) }
    var showIconPicker by rememberSaveable(state.categoryId) { mutableStateOf(false) }
    var iconSearchQuery by rememberSaveable(state.categoryId) { mutableStateOf("") }
    val categoryColors = MaterialTheme.mataColors.categoryColors

    BackHandler(enabled = showIconPicker) { showIconPicker = false }
    if (showIconPicker) {
        CategoryIconPicker(
            selectedIconId = state.iconName,
            searchQuery = iconSearchQuery,
            onSearchQueryChange = { iconSearchQuery = it },
            onBack = { showIconPicker = false },
            onSelect = { iconId ->
                onIconChange(iconId)
                showIconPicker = false
            },
        )
        return
    }

    Scaffold(
        topBar = {
            if (showTopBar) TopAppBar(
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
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            enabled = !state.isSaving,
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(
                                    R.string.content_description_delete_category,
                                ),
                            )
                        }
                    }
                    TextButton(
                        onClick = onSave,
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
                    onValueChange = onNameChange,
                    shape = MataCardLayout.InputShape,
                    label = { Text(stringResource(R.string.category_name_required_label)) },
                    supportingText = {
                        Text(stringResource(R.string.character_counter_format, state.name.length, 30))
                    },
                    isError = state.name.length > 30,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = categoryColors[state.colorIndex],
                        unfocusedBorderColor = categoryColors[state.colorIndex],
                    ),
                )
                Text(
                    stringResource(R.string.category_color_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categoryColors.chunked(4).forEach { rowColors ->
                        Row(
                            Modifier.fillMaxWidth().focusGroup(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            rowColors.forEach { color ->
                                val index = categoryColors.indexOf(color)
                                ColorOption(
                                    color = color,
                                    contentColor = MaterialTheme.mataColors.onCategoryColors[index],
                                    colorId = CategoryColorOptions[index].id,
                                    label = stringResource(CategoryColorNameResIds[index]),
                                    selected = state.colorIndex == index,
                                    onClick = { onColorChange(index) },
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.category_icon_label),
                    style = MaterialTheme.typography.titleMedium,
                )
                CategoryIconField(
                    selectedIconId = state.iconName,
                    enabled = !state.isSaving,
                    onClick = {
                        iconSearchQuery = ""
                        showIconPicker = true
                    },
                )
                state.errorMessageRes?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                }
                if (state.isSaving) CircularProgressIndicator()
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.dialog_delete_category_title)) },
            text = { Text(stringResource(R.string.dialog_delete_category_message)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) {
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
private fun CategoryIconField(
    selectedIconId: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val option = CategoryIconOptions.firstOrNull { it.id == selectedIconId }
        ?: CategoryIconOptions.first { it.id == "Category" }
    val label = stringResource(option.labelRes)
    val selectedDescription = stringResource(R.string.category_icon_selected_format, label)
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATEGORY_ICON_LIST_TEST_TAG)
            .mataClickablePointer(enabled)
            .semantics {
                selected = true
                stateDescription = selectedDescription
                this[CategoryIconIdKey] = option.id
            },
    ) {
        ListItem(
            leadingContent = {
                Icon(option.imageVector, contentDescription = label)
            },
            headlineContent = { Text(label) },
            supportingContent = { Text(stringResource(R.string.category_icon_picker_title)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryIconPicker(
    selectedIconId: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val resources = LocalResources.current
    val localizedIcons = CategoryIconOptions.map { option ->
        LocalizedCategoryIcon(
            option = option,
            label = resources.getString(option.labelRes),
            group = resources.getString(option.groupRes),
            keywords = resources.getString(option.keywordsRes),
        )
    }
    val normalizedQuery = normalizeCategoryIconSearch(searchQuery)
    val listState = rememberLazyListState()
    val filteredIcons = if (normalizedQuery.isBlank()) {
        localizedIcons
    } else {
        localizedIcons.filter { icon ->
            normalizeCategoryIconSearch(
                listOf(icon.label, icon.group, icon.keywords, icon.option.id).joinToString(" "),
            ).contains(normalizedQuery)
        }
    }
    LaunchedEffect(normalizedQuery) {
        listState.scrollToItem(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(CATEGORY_ICON_PICKER_TEST_TAG),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.category_icon_picker_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag(CATEGORY_ICON_SEARCH_TEST_TAG),
                singleLine = true,
                label = { Text(stringResource(R.string.category_icon_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.category_icon_clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(CATEGORY_ICON_RESULTS_TEST_TAG),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredIcons.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillParentMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.category_icon_no_results))
                        }
                    }
                } else if (normalizedQuery.isBlank()) {
                    filteredIcons.groupBy { it.option.groupRes }.forEach { (groupRes, icons) ->
                        item(key = "group_$groupRes") {
                            Text(
                                stringResource(groupRes),
                                modifier = Modifier
                                    .padding(top = 12.dp, start = 4.dp)
                                    .testTag(CATEGORY_ICON_GROUP_TEST_TAG_PREFIX + groupRes),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        items(icons, key = { it.option.id }) { icon ->
                            CategoryIconPickerRow(icon, selectedIconId, onSelect)
                        }
                    }
                } else {
                    items(filteredIcons, key = { it.option.id }) { icon ->
                        CategoryIconPickerRow(icon, selectedIconId, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryIconPickerRow(
    icon: LocalizedCategoryIcon,
    selectedIconId: String,
    onSelect: (String) -> Unit,
) {
    val isSelected = selectedIconId == icon.option.id
    OutlinedCard(
        onClick = { onSelect(icon.option.id) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CATEGORY_ICON_OPTION_TEST_TAG_PREFIX + icon.option.id)
            .mataClickablePointer()
            .semantics {
                selected = isSelected
                this[CategoryIconIdKey] = icon.option.id
                this[CategoryIconLabelKey] = icon.label
            },
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            CardDefaults.outlinedCardBorder()
        },
    ) {
        ListItem(
            leadingContent = {
                Icon(icon.option.imageVector, contentDescription = icon.label)
            },
            headlineContent = { Text(icon.label) },
            supportingContent = { Text(icon.group) },
            trailingContent = {
                if (isSelected) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

private data class LocalizedCategoryIcon(
    val option: CategoryIconOption,
    val label: String,
    val group: String,
    val keywords: String,
)

internal fun normalizeCategoryIconSearch(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.JAPANESE)
        .trim()

@Composable
private fun CategoryPreview(state: CategoryEditorUiState) {
    val categoryColor = mataCategoryColor(state.colorIndex)
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = {
                Icon(
                    categoryIcon(state.iconName),
                    contentDescription = null,
                    tint = categoryColor,
                )
            },
            headlineContent = {
                Text(state.name.ifBlank { stringResource(R.string.category_name_preview) })
            },
        )
    }
}

@Composable
private fun ColorOption(
    color: Color,
    contentColor: Color,
    colorId: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .mataClickablePointer()
            .semantics {
                contentDescription = label
                this.selected = selected
                this[CategoryColorIdKey] = colorId
                this[CategoryColorArgbKey] = color.toArgb().toLong() and 0xffffffffL
            },
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
                        contentDescription = null,
                        tint = contentColor,
                    )
                }
            }
        }
    }
}
