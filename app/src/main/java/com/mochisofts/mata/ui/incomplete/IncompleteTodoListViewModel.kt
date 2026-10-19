package com.mochisofts.mata.ui.incomplete

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.R
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.core.navigation.TODO_EDITOR_RESULT_KEY
import com.mochisofts.mata.domain.model.IncompleteTodoGroup
import com.mochisofts.mata.domain.model.IncompleteTodoSource
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class IncompleteTodoListUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val hasHistoryError: Boolean = false,
    val isReconciling: Boolean = true,
    val groups: List<IncompleteTodoGroup> = emptyList(),
    val busyTodoId: String? = null,
)

@HiltViewModel
class IncompleteTodoListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todoRepository: TodoRepository,
    private val historyReconciler: HistoryReconciler,
    adsConsentRepository: AdsConsentRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IncompleteTodoListUiState())
    val uiState = _uiState.asStateFlow()
    val adsRuntimeState = adsConsentRepository.state
    private val messagesChannel = Channel<Int>(Channel.BUFFERED)
    val messages = messagesChannel.receiveAsFlow()
    private var loadJob: Job? = null
    private var reconciliationJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            savedStateHandle.getStateFlow<Int?>(TODO_EDITOR_RESULT_KEY, null).filterNotNull().collect {
                savedStateHandle[TODO_EDITOR_RESULT_KEY] = null
                messagesChannel.send(it)
            }
        }
    }

    fun refresh(reconcile: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                todoRepository.observeIncompleteTodos().collect { groups ->
                    _uiState.update { it.copy(isLoading = false, hasLoadError = false, groups = groups) }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, hasLoadError = true) }
            }
        }
        if (reconcile && reconciliationJob?.isActive != true) {
            _uiState.update { it.copy(isReconciling = true, hasHistoryError = false) }
            reconciliationJob = viewModelScope.launch {
                try {
                    do {
                        val result = historyReconciler.reconcile()
                        yield()
                    } while (result.hasMore)
                    _uiState.update { it.copy(isReconciling = false) }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    _uiState.update { it.copy(isReconciling = false, hasHistoryError = true) }
                }
            }
        }
    }

    fun history(todoId: String, page: Int) = todoRepository.observeIncompleteHistory(todoId, 20, page * 20)

    fun dismiss(source: IncompleteTodoSource) {
        val state = _uiState.value
        if (state.busyTodoId != null || state.isReconciling || state.hasLoadError || state.hasHistoryError) return
        _uiState.update { it.copy(busyTodoId = source.todoId) }
        viewModelScope.launch {
            val result = todoRepository.dismissIncompleteTodos(source)
            _uiState.update { it.copy(busyTodoId = null) }
            messagesChannel.send(if (result.isSuccess) R.string.incomplete_hidden else R.string.incomplete_action_error)
        }
    }
}
