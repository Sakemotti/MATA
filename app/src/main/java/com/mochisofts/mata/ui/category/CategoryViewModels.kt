package com.mochisofts.mata.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.core.navigation.CategoryEditorRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    repository: CategoryRepository,
) : ViewModel() {
    val categories: StateFlow<List<Category>> = repository.observeCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
}

data class CategoryEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val colorIndex: Int = 8,
    val iconName: String = "Category",
    val endHour: Int = 0,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
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
                        error = "カテゴリが見つかりません",
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
        _uiState.update { it.copy(isSaving = true, error = null) }
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
                    it.copy(isSaving = false, error = throwable.message ?: "カテゴリを保存できませんでした")
                }
            }
        }
    }

    fun delete() {
        val categoryId = route.categoryId ?: return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            repository.deleteCategory(categoryId)
                .onSuccess { effectsChannel.send(CategoryEditorEffect.Deleted) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isSaving = false, error = "カテゴリを削除できませんでした")
                    }
                }
        }
    }

    private fun edit(transform: CategoryEditorUiState.() -> CategoryEditorUiState) {
        _uiState.update { state -> state.transform().copy(isDirty = true, error = null) }
    }
}
