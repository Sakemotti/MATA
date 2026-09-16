package com.mochisofts.mata.ui.category

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.R
import com.mochisofts.mata.core.navigation.CategoryEditorRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.CategoryTodoCounts
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryListUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val categories: List<Category> = emptyList(),
    val isReordering: Boolean = false,
    val isOrderSaving: Boolean = false,
    val editor: CategoryEditorUiState? = null,
    val deletion: CategoryDeletionUiState? = null,
)

data class CategoryDeletionUiState(
    val category: Category,
    val counts: CategoryTodoCounts? = null,
    val isDeleting: Boolean = false,
)

sealed interface CategoryListEffect {
    data class OrderSaved(val position: Int, val total: Int) : CategoryListEffect
    data class CategorySaved(val id: String, val isNew: Boolean) : CategoryListEffect
    data object CategoryDeleted : CategoryListEffect
    data class Message(@StringRes val messageRes: Int) : CategoryListEffect
}

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<CategoryListEffect>(Channel.BUFFERED)
    val effects: Flow<CategoryListEffect> = effectsChannel.receiveAsFlow()

    private var persistedCategories: List<Category> = emptyList()
    private var editorLoadRequest = 0
    private var categoriesLoadJob: Job? = null

    init {
        loadCategories()
        when (savedStateHandle.get<String>(EDITOR_MODE_KEY)) {
            EDITOR_MODE_NEW -> restoreEditorDraft(isNew = true)
            EDITOR_MODE_EDIT -> {
                val categoryId = savedStateHandle.get<String>(EDITOR_CATEGORY_ID_KEY)
                if (categoryId != null &&
                    savedStateHandle.get<Boolean>(EDITOR_DRAFT_PRESENT_KEY) == true
                ) {
                    restoreEditorDraft(isNew = false, categoryId = categoryId)
                } else {
                    categoryId?.let(::openEditor)
                }
            }
        }
    }

    fun retryLoad() = loadCategories()

    private fun loadCategories() {
        categoriesLoadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hasLoadError = false) }
        categoriesLoadJob = viewModelScope.launch {
            repository.observeCategories()
                .catch {
                    _uiState.update { state ->
                        state.copy(isLoading = false, hasLoadError = true)
                    }
                }
                .collect { categories ->
                    persistedCategories = categories
                    _uiState.update { state ->
                        if (state.isReordering || state.isOrderSaving) state
                        else state.copy(
                            isLoading = false,
                            hasLoadError = false,
                            categories = categories,
                        )
                    }
                }
        }
    }

    fun startReordering(): Boolean {
        if (_uiState.value.isOrderSaving) return false
        _uiState.update { it.copy(isReordering = true) }
        return true
    }

    fun moveReorderingCategory(categoryId: String, targetCategoryId: String): Boolean {
        val state = _uiState.value
        if (!state.isReordering || state.isOrderSaving || categoryId == targetCategoryId) return false
        val fromIndex = state.categories.indexOfFirst { it.id == categoryId }
        val targetIndex = state.categories.indexOfFirst { it.id == targetCategoryId }
        if (fromIndex == -1 || targetIndex == -1) return false
        _uiState.update { current ->
            current.copy(categories = current.categories.move(fromIndex, targetIndex))
        }
        return true
    }

    fun finishReordering(categoryId: String) {
        val state = _uiState.value
        if (!state.isReordering || state.isOrderSaving) return
        persistOrder(categoryId)
    }

    fun cancelReordering() {
        if (!_uiState.value.isReordering) return
        _uiState.update {
            it.copy(categories = persistedCategories, isReordering = false, isOrderSaving = false)
        }
    }

    fun moveCategoryOneStep(categoryId: String, offset: Int): Boolean {
        val state = _uiState.value
        if (state.isReordering || state.isOrderSaving || offset !in listOf(-1, 1)) return false
        val fromIndex = state.categories.indexOfFirst { it.id == categoryId }
        val targetIndex = fromIndex + offset
        if (fromIndex == -1 || targetIndex !in state.categories.indices) return false
        _uiState.update {
            it.copy(
                categories = it.categories.move(fromIndex, targetIndex),
                isReordering = true,
            )
        }
        persistOrder(categoryId)
        return true
    }

    fun openNewEditor() {
        editorLoadRequest += 1
        clearEditorDraft()
        setEditorState(CategoryEditorUiState(isLoading = false, isNew = true))
    }

    fun openEditor(categoryId: String) {
        val request = ++editorLoadRequest
        clearEditorDraft()
        savedStateHandle[EDITOR_MODE_KEY] = EDITOR_MODE_EDIT
        savedStateHandle[EDITOR_CATEGORY_ID_KEY] = categoryId
        _uiState.update {
            it.copy(
                editor = CategoryEditorUiState(
                    isLoading = true,
                    isNew = false,
                    categoryId = categoryId,
                ),
            )
        }
        viewModelScope.launch {
            val categoryResult = runCatching { repository.getCategory(categoryId) }
            if (request != editorLoadRequest) return@launch
            val category = categoryResult.getOrNull()
            setEditorState(
                if (category == null) {
                    _uiState.value.editor?.copy(
                        isLoading = false,
                        errorMessageRes = if (categoryResult.isFailure) {
                            R.string.category_list_load_error
                        } else {
                            R.string.error_category_not_found
                        },
                    )
                } else {
                    CategoryEditorUiState(
                        isLoading = false,
                        isNew = false,
                        categoryId = category.id,
                        name = category.name,
                        colorIndex = category.colorIndex,
                        iconName = category.iconName,
                    )
                },
            )
        }
    }

    fun closeEditor() {
        editorLoadRequest += 1
        clearEditorDraft()
        _uiState.update { it.copy(editor = null) }
    }

    fun setEditorName(value: String) = editEditor { copy(name = value) }
    fun setEditorColor(value: Int) = editEditor { copy(colorIndex = value) }
    fun setEditorIcon(value: String) = editEditor { copy(iconName = value) }

    fun saveEditor() {
        val editor = _uiState.value.editor ?: return
        if (!editor.canSave || !editor.isDirty) return
        updateEditorState { copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            repository.saveCategory(
                id = editor.categoryId,
                name = editor.name,
                colorIndex = editor.colorIndex,
                iconName = editor.iconName,
            ).onSuccess { id ->
                updateEditorState {
                    copy(
                        categoryId = id,
                        isNew = false,
                        isDirty = false,
                        isSaving = false,
                    )
                }
                effectsChannel.send(CategoryListEffect.CategorySaved(id, editor.isNew))
            }.onFailure { throwable ->
                updateEditorState {
                    copy(
                        isSaving = false,
                        errorMessageRes = throwable.toUserMessageRes(
                            R.string.error_category_save_failed,
                        ),
                    )
                }
            }
        }
    }

    fun deleteEditor() {
        val categoryId = _uiState.value.editor?.categoryId ?: return
        updateEditorState { copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    closeEditor()
                    effectsChannel.send(CategoryListEffect.CategoryDeleted)
                }
                .onFailure {
                    updateEditorState {
                        copy(
                            isSaving = false,
                            errorMessageRes = R.string.error_category_delete_failed,
                        )
                    }
                }
        }
    }

    fun requestDelete(categoryId: String) {
        val state = _uiState.value
        if (state.isReordering || state.isOrderSaving || state.deletion != null) return
        val category = state.categories.firstOrNull { it.id == categoryId } ?: return
        _uiState.update { it.copy(deletion = CategoryDeletionUiState(category = category)) }
        viewModelScope.launch {
            runCatching { repository.getTodoCounts(categoryId) }
                .onSuccess { counts ->
                    _uiState.update { current ->
                        val deletion = current.deletion
                        if (deletion?.category?.id == categoryId) {
                            current.copy(deletion = deletion.copy(counts = counts))
                        } else {
                            current
                        }
                    }
                }
                .onFailure {
                    _uiState.update { current ->
                        if (current.deletion?.category?.id == categoryId) {
                            current.copy(deletion = null)
                        } else {
                            current
                        }
                    }
                    effectsChannel.send(
                        CategoryListEffect.Message(R.string.error_category_delete_preview_failed),
                    )
                }
        }
    }

    fun cancelDelete() {
        _uiState.update { state ->
            if (state.deletion?.isDeleting == true) state else state.copy(deletion = null)
        }
    }

    fun confirmDelete() {
        val deletion = _uiState.value.deletion ?: return
        if (deletion.counts == null || deletion.isDeleting) return
        val categoryId = deletion.category.id
        _uiState.update { state ->
            state.copy(deletion = state.deletion?.copy(isDeleting = true))
        }
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess {
                    if (_uiState.value.editor?.categoryId == categoryId) closeEditor()
                    _uiState.update { it.copy(deletion = null) }
                    effectsChannel.send(CategoryListEffect.CategoryDeleted)
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(deletion = state.deletion?.copy(isDeleting = false))
                    }
                    effectsChannel.send(
                        CategoryListEffect.Message(R.string.error_category_delete_failed),
                    )
                }
        }
    }

    private fun editEditor(
        transform: CategoryEditorUiState.() -> CategoryEditorUiState,
    ) {
        updateEditorState {
            transform().copy(
                isDirty = true,
                errorMessageRes = null,
            )
        }
    }

    private fun restoreEditorDraft(isNew: Boolean, categoryId: String? = null) {
        if (savedStateHandle.get<Boolean>(EDITOR_DRAFT_PRESENT_KEY) != true) {
            if (isNew) openNewEditor() else categoryId?.let(::openEditor)
            return
        }
        setEditorState(
            CategoryEditorUiState(
                isLoading = false,
                isNew = isNew,
                categoryId = categoryId,
                name = savedStateHandle[EDITOR_NAME_KEY] ?: "",
                colorIndex = savedStateHandle[EDITOR_COLOR_KEY] ?: 8,
                iconName = savedStateHandle[EDITOR_ICON_KEY] ?: "Category",
                isDirty = savedStateHandle[EDITOR_DIRTY_KEY] ?: false,
                errorMessageRes = savedStateHandle[EDITOR_ERROR_KEY],
            ),
        )
    }

    private fun setEditorState(editor: CategoryEditorUiState?) {
        _uiState.update { it.copy(editor = editor) }
        editor?.let(::persistEditorDraft)
    }

    private fun updateEditorState(transform: CategoryEditorUiState.() -> CategoryEditorUiState) {
        val editor = _uiState.value.editor?.transform() ?: return
        setEditorState(editor)
    }

    private fun persistEditorDraft(editor: CategoryEditorUiState) {
        savedStateHandle[EDITOR_MODE_KEY] = if (editor.isNew) EDITOR_MODE_NEW else EDITOR_MODE_EDIT
        savedStateHandle[EDITOR_CATEGORY_ID_KEY] = editor.categoryId
        savedStateHandle[EDITOR_DRAFT_PRESENT_KEY] = !editor.isLoading
        if (editor.isLoading) return
        savedStateHandle[EDITOR_NAME_KEY] = editor.name
        savedStateHandle[EDITOR_COLOR_KEY] = editor.colorIndex
        savedStateHandle[EDITOR_ICON_KEY] = editor.iconName
        savedStateHandle[EDITOR_DIRTY_KEY] = editor.isDirty
        if (editor.errorMessageRes == null) {
            savedStateHandle.remove<Int>(EDITOR_ERROR_KEY)
        } else {
            savedStateHandle[EDITOR_ERROR_KEY] = editor.errorMessageRes
        }
    }

    private fun clearEditorDraft() {
        savedStateHandle.remove<String>(EDITOR_MODE_KEY)
        savedStateHandle.remove<String>(EDITOR_CATEGORY_ID_KEY)
        savedStateHandle.remove<Boolean>(EDITOR_DRAFT_PRESENT_KEY)
        savedStateHandle.remove<String>(EDITOR_NAME_KEY)
        savedStateHandle.remove<Int>(EDITOR_COLOR_KEY)
        savedStateHandle.remove<String>(EDITOR_ICON_KEY)
        savedStateHandle.remove<Boolean>(EDITOR_DIRTY_KEY)
        savedStateHandle.remove<Int>(EDITOR_ERROR_KEY)
    }

    private fun persistOrder(categoryId: String) {
        val orderedCategories = _uiState.value.categories
        if (orderedCategories.map(Category::id) == persistedCategories.map(Category::id)) {
            _uiState.update { it.copy(isReordering = false) }
            return
        }
        _uiState.update { it.copy(isReordering = false, isOrderSaving = true) }
        viewModelScope.launch {
            repository.reorderCategories(orderedCategories.map(Category::id))
                .onSuccess {
                    persistedCategories = orderedCategories
                    _uiState.update {
                        it.copy(categories = orderedCategories, isOrderSaving = false)
                    }
                    val position = orderedCategories.indexOfFirst { it.id == categoryId } + 1
                    effectsChannel.send(CategoryListEffect.OrderSaved(position, orderedCategories.size))
                }
                .onFailure {
                    _uiState.update {
                        it.copy(categories = persistedCategories, isOrderSaving = false)
                    }
                    effectsChannel.send(
                        CategoryListEffect.Message(R.string.error_category_reorder_failed),
                    )
                }
        }
    }

    private companion object {
        const val EDITOR_MODE_KEY = "category_editor_mode"
        const val EDITOR_CATEGORY_ID_KEY = "category_editor_category_id"
        const val EDITOR_DRAFT_PRESENT_KEY = "category_editor_draft_present"
        const val EDITOR_NAME_KEY = "category_editor_name"
        const val EDITOR_COLOR_KEY = "category_editor_color"
        const val EDITOR_ICON_KEY = "category_editor_icon"
        const val EDITOR_DIRTY_KEY = "category_editor_dirty"
        const val EDITOR_ERROR_KEY = "category_editor_error"
        const val EDITOR_MODE_NEW = "new"
        const val EDITOR_MODE_EDIT = "edit"
    }
}

private fun <T> List<T>.move(fromIndex: Int, targetIndex: Int): List<T> =
    toMutableList().apply { add(targetIndex, removeAt(fromIndex)) }

data class CategoryEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val categoryId: String? = null,
    val name: String = "",
    val colorIndex: Int = 8,
    val iconName: String = "Category",
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
) {
    val canSave: Boolean
        get() = !isLoading && !isSaving && name.trim().isNotEmpty() && name.length <= 30
}

sealed interface CategoryEditorEffect {
    data class Saved(val id: String, val isNew: Boolean) : CategoryEditorEffect
    data object Deleted : CategoryEditorEffect
}

@HiltViewModel
class CategoryEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<CategoryEditorRoute>()
    private val _uiState = MutableStateFlow(CategoryEditorUiState(isNew = route.categoryId == null))
    val uiState: StateFlow<CategoryEditorUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<CategoryEditorEffect>(Channel.BUFFERED)
    val effects: Flow<CategoryEditorEffect> = effectsChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val category = route.categoryId?.let { repository.getCategory(it) }
            _uiState.update { state ->
                when {
                    route.categoryId != null && category == null -> state.copy(
                        isLoading = false,
                        errorMessageRes = R.string.error_category_not_found,
                    )
                    category == null -> state.copy(isLoading = false)
                    else -> state.copy(
                        isLoading = false,
                        name = category.name,
                        colorIndex = category.colorIndex,
                        iconName = category.iconName,
                    )
                }
            }
        }
    }

    fun setName(value: String) = edit { copy(name = value) }
    fun setColor(value: Int) = edit { copy(colorIndex = value) }
    fun setIcon(value: String) = edit { copy(iconName = value) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || !state.isDirty) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            repository.saveCategory(
                id = route.categoryId,
                name = state.name,
                colorIndex = state.colorIndex,
                iconName = state.iconName,
            ).onSuccess { id ->
                effectsChannel.send(CategoryEditorEffect.Saved(id, route.categoryId == null))
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = throwable.toUserMessageRes(R.string.error_category_save_failed),
                    )
                }
            }
        }
    }

    fun delete() {
        val categoryId = route.categoryId ?: return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess { effectsChannel.send(CategoryEditorEffect.Deleted) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            errorMessageRes = R.string.error_category_delete_failed,
                        )
                    }
                }
        }
    }

    private fun edit(transform: CategoryEditorUiState.() -> CategoryEditorUiState) {
        _uiState.update {
            state -> state.transform().copy(isDirty = true, errorMessageRes = null)
        }
    }
}
