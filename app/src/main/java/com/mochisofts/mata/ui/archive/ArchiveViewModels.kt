package com.mochisofts.mata.ui.archive

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mochisofts.mata.R
import com.mochisofts.mata.core.navigation.ArchivedTodoDetailRoute
import com.mochisofts.mata.domain.model.ArchiveActionPreview
import com.mochisofts.mata.domain.model.ArchiveHistorySummary
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.ArchivedHistoryItem
import com.mochisofts.mata.domain.model.ArchivedTodoItem
import com.mochisofts.mata.domain.repository.ArchiveRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ArchiveAction {
    RESTORE,
    DELETE,
}

data class ArchiveListUiState(
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val sortOrder: ArchiveSortOrder = ArchiveSortOrder.NEWEST,
    val preview: ArchiveActionPreview? = null,
    val previewAction: ArchiveAction? = null,
    val loadingPreviewTodoId: String? = null,
    val runningTodoId: String? = null,
)

sealed interface ArchiveListEffect {
    data class Message(@StringRes val messageRes: Int) : ArchiveListEffect
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ArchiveListViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ArchiveRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val searchActive = savedStateHandle.getStateFlow(KEY_SEARCH_ACTIVE, false)
    private val searchQuery = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")
    private val sortOrder = settingsRepository.archiveSortOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, ArchiveSortOrder.NEWEST)
    private val operationState = MutableStateFlow(OperationState())
    private val effectsChannel = Channel<ArchiveListEffect>(Channel.BUFFERED)
    val effects: Flow<ArchiveListEffect> = effectsChannel.receiveAsFlow()

    val todos: Flow<PagingData<ArchivedTodoItem>> = combine(
        searchQuery.debounce(SEARCH_DEBOUNCE_MILLIS),
        sortOrder,
    ) { query, order -> query to order }
        .flatMapLatest { (query, order) -> repository.pagedTodos(query, order) }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<ArchiveListUiState> = combine(
        searchActive,
        searchQuery,
        sortOrder,
        operationState,
    ) { active, query, order, operation ->
        ArchiveListUiState(
            searchActive = active,
            searchQuery = query,
            sortOrder = order,
            preview = operation.preview,
            previewAction = operation.action,
            loadingPreviewTodoId = operation.loadingPreviewTodoId,
            runningTodoId = operation.runningTodoId,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ArchiveListUiState(),
    )

    init {
        viewModelScope.launch {
            savedStateHandle.getStateFlow<Int?>(ARCHIVE_RESULT_KEY, null)
                .filterNotNull()
                .collect { result ->
                    savedStateHandle[ARCHIVE_RESULT_KEY] = null
                    effectsChannel.send(ArchiveListEffect.Message(result))
                }
        }
        val targetId = savedStateHandle.get<String>(KEY_ACTION_TARGET)
        val action = savedStateHandle.get<String>(KEY_ACTION)?.let { stored ->
            runCatching { ArchiveAction.valueOf(stored) }.getOrNull()
        }
        if (targetId != null && action != null) requestAction(targetId, action)
    }

    fun openSearch() {
        savedStateHandle[KEY_SEARCH_ACTIVE] = true
    }

    fun updateSearchQuery(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun closeSearch() {
        savedStateHandle[KEY_SEARCH_ACTIVE] = false
        savedStateHandle[KEY_SEARCH_QUERY] = ""
    }

    fun setSortOrder(order: ArchiveSortOrder) {
        if (order == sortOrder.value) return
        viewModelScope.launch {
            runCatching { settingsRepository.setArchiveSortOrder(order) }
                .onFailure {
                    effectsChannel.send(ArchiveListEffect.Message(R.string.archive_sort_save_error))
                }
        }
    }

    fun requestAction(todoId: String, action: ArchiveAction) {
        if (operationState.value.isBusy) return
        savedStateHandle[KEY_ACTION_TARGET] = todoId
        savedStateHandle[KEY_ACTION] = action.name
        operationState.update { it.copy(loadingPreviewTodoId = todoId, action = action) }
        viewModelScope.launch {
            repository.getActionPreview(todoId)
                .onSuccess { preview ->
                    operationState.update {
                        it.copy(preview = preview, loadingPreviewTodoId = null)
                    }
                }
                .onFailure { throwable ->
                    clearAction()
                    effectsChannel.send(
                        ArchiveListEffect.Message(
                            throwable.toUserMessageRes(R.string.archive_preview_load_error),
                        ),
                    )
                }
        }
    }

    fun dismissAction() = clearAction()

    fun confirmAction() {
        val state = operationState.value
        val preview = state.preview ?: return
        val action = state.action ?: return
        if (state.runningTodoId != null) return
        operationState.update { it.copy(runningTodoId = preview.todoId) }
        viewModelScope.launch {
            val result = when (action) {
                ArchiveAction.RESTORE -> repository.restore(preview.todoId)
                ArchiveAction.DELETE -> repository.deletePermanently(preview.todoId)
            }
            result.onSuccess {
                clearAction()
                effectsChannel.send(
                    ArchiveListEffect.Message(
                        if (action == ArchiveAction.RESTORE) {
                            R.string.archive_restore_success
                        } else {
                            R.string.archive_delete_success
                        },
                    ),
                )
            }.onFailure { throwable ->
                operationState.update { it.copy(runningTodoId = null) }
                effectsChannel.send(
                    ArchiveListEffect.Message(
                        throwable.toUserMessageRes(
                            if (action == ArchiveAction.RESTORE) {
                                R.string.archive_restore_error
                            } else {
                                R.string.archive_delete_error
                            },
                        ),
                    ),
                )
            }
        }
    }

    private fun clearAction() {
        savedStateHandle[KEY_ACTION_TARGET] = null
        savedStateHandle[KEY_ACTION] = null
        operationState.value = OperationState()
    }

    private data class OperationState(
        val preview: ArchiveActionPreview? = null,
        val action: ArchiveAction? = null,
        val loadingPreviewTodoId: String? = null,
        val runningTodoId: String? = null,
    ) {
        val isBusy: Boolean
            get() = loadingPreviewTodoId != null || runningTodoId != null
    }

    private companion object {
        const val KEY_SEARCH_ACTIVE = "archive_search_active"
        const val KEY_SEARCH_QUERY = "archive_search_query"
        const val KEY_ACTION_TARGET = "archive_action_target"
        const val KEY_ACTION = "archive_action"
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

data class ArchiveDetailUiState(
    val isLoading: Boolean = true,
    val item: ArchivedTodoItem? = null,
    val summary: ArchiveHistorySummary? = null,
    @StringRes val loadErrorRes: Int? = null,
    val preview: ArchiveActionPreview? = null,
    val previewAction: ArchiveAction? = null,
    val isLoadingPreview: Boolean = false,
    val isRunningAction: Boolean = false,
)

sealed interface ArchiveDetailEffect {
    data class Message(@StringRes val messageRes: Int) : ArchiveDetailEffect
    data class Finished(@StringRes val messageRes: Int) : ArchiveDetailEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArchiveDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ArchiveRepository,
) : ViewModel() {
    private val todoId = savedStateHandle.toRoute<ArchivedTodoDetailRoute>().todoId
    private val operationState = MutableStateFlow(OperationState())
    private val effectsChannel = Channel<ArchiveDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ArchiveDetailEffect> = effectsChannel.receiveAsFlow()

    private val content = combine(
        repository.observeTodo(todoId),
        repository.observeHistorySummary(todoId),
    ) { item, summary ->
        Pair<ArchivedTodoItem?, ArchiveHistorySummary?>(item, summary)
    }.catch { emit(null to null) }

    val uiState: StateFlow<ArchiveDetailUiState> = combine(
        content,
        operationState,
    ) { (item, summary), operation ->
        ArchiveDetailUiState(
            isLoading = false,
            item = item,
            summary = summary,
            loadErrorRes = if (item == null) R.string.error_todo_not_found else null,
            preview = operation.preview,
            previewAction = operation.action,
            isLoadingPreview = operation.isLoadingPreview,
            isRunningAction = operation.isRunningAction,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ArchiveDetailUiState(),
    )

    val history: Flow<PagingData<ArchivedHistoryItem>> = repository.pagedHistory(todoId)
        .cachedIn(viewModelScope)

    init {
        savedStateHandle.get<String>(KEY_DETAIL_ACTION)?.let { stored ->
            runCatching { ArchiveAction.valueOf(stored) }.getOrNull()?.let(::requestAction)
        }
    }

    fun requestAction(action: ArchiveAction) {
        if (operationState.value.isBusy) return
        savedStateHandle[KEY_DETAIL_ACTION] = action.name
        operationState.value = OperationState(action = action, isLoadingPreview = true)
        viewModelScope.launch {
            repository.getActionPreview(todoId)
                .onSuccess { preview ->
                    operationState.value = OperationState(action = action, preview = preview)
                }
                .onFailure { throwable ->
                    savedStateHandle[KEY_DETAIL_ACTION] = null
                    operationState.value = OperationState()
                    val message = throwable.toUserMessageRes(R.string.archive_preview_load_error)
                    if (message != R.string.error_todo_not_found) {
                        effectsChannel.send(ArchiveDetailEffect.Message(message))
                    }
                }
        }
    }

    fun dismissAction() {
        if (!operationState.value.isRunningAction) {
            savedStateHandle[KEY_DETAIL_ACTION] = null
            operationState.value = OperationState()
        }
    }

    fun confirmAction() {
        val state = operationState.value
        val preview = state.preview ?: return
        val action = state.action ?: return
        if (state.isRunningAction) return
        operationState.value = state.copy(isRunningAction = true)
        viewModelScope.launch {
            val result = when (action) {
                ArchiveAction.RESTORE -> repository.restore(preview.todoId)
                ArchiveAction.DELETE -> repository.deletePermanently(preview.todoId)
            }
            result.onSuccess {
                effectsChannel.send(
                    ArchiveDetailEffect.Finished(
                        if (action == ArchiveAction.RESTORE) {
                            R.string.archive_restore_success
                        } else {
                            R.string.archive_delete_success
                        },
                    ),
                )
            }.onFailure { throwable ->
                operationState.value = state.copy(isRunningAction = false)
                effectsChannel.send(
                    ArchiveDetailEffect.Message(
                        throwable.toUserMessageRes(
                            if (action == ArchiveAction.RESTORE) {
                                R.string.archive_restore_error
                            } else {
                                R.string.archive_delete_error
                            },
                        ),
                    ),
                )
            }
        }
    }

    private data class OperationState(
        val preview: ArchiveActionPreview? = null,
        val action: ArchiveAction? = null,
        val isLoadingPreview: Boolean = false,
        val isRunningAction: Boolean = false,
    ) {
        val isBusy: Boolean
            get() = isLoadingPreview || isRunningAction
    }

    private companion object {
        const val KEY_DETAIL_ACTION = "archive_detail_action"
    }
}

const val ARCHIVE_RESULT_KEY = "archive_result_message"
