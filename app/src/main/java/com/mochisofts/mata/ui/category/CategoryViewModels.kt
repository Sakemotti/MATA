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
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryListUiState(
    val categories: List<Category> = emptyList(),
    val isReordering: Boolean = false,
    val isOrderSaving: Boolean = false,
)

sealed interface CategoryListEffect {
    data class OrderSaved(val position: Int, val total: Int) : CategoryListEffect
    data class Message(@StringRes val messageRes: Int) : CategoryListEffect
}

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CategoryListUiState())
    val uiState: StateFlow<CategoryListUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<CategoryListEffect>(Channel.BUFFERED)
    val effects: Flow<CategoryListEffect> = effectsChannel.receiveAsFlow()

    private var persistedCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch {
            repository.observeCategories().collect { categories ->
                persistedCategories = categories
                _uiState.update { state ->
                    if (state.isReordering || state.isOrderSaving) state
                    else state.copy(categories = categories)
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
}

private fun <T> List<T>.move(fromIndex: Int, targetIndex: Int): List<T> =
    toMutableList().apply { add(targetIndex, removeAt(fromIndex)) }

data class CategoryEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val colorIndex: Int = 8,
    val iconName: String = "Category",
    val endHour: Int = 0,
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
                        endHour = category.endHour,
                    )
                }
            }
        }
    }

    fun setName(value: String) = edit { copy(name = value) }
    fun setColor(value: Int) = edit { copy(colorIndex = value) }
    fun setIcon(value: String) = edit { copy(iconName = value) }
    fun setEndHour(value: Int) = edit { copy(endHour = value) }

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
                endHour = state.endHour,
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
