package com.mochisofts.mata.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.R
import com.mochisofts.mata.core.common.ValidationException
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.WidgetInstanceStateDao
import com.mochisofts.mata.data.local.WidgetInstanceStateEntity
import com.mochisofts.mata.data.widget.LOAD_LOADING
import com.mochisofts.mata.data.widget.WidgetRefreshCoordinator
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.data.widget.activeAppWidgetIds
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.WidgetDisplayModel
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.ui.ads.MataBannerAd
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WidgetTodoActionActivity : ComponentActivity() {
    private val viewModel: WidgetTodoActionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(true)
        val request = intent.toWidgetTodoActionRequest()
        if (request == null) {
            finish()
            return
        }
        viewModel.initialize(request)
        viewModel.gatherConsent(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { finish() }
            }
        }
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val theme by viewModel.theme.collectAsStateWithLifecycle()
            val adsRuntime by viewModel.adsRuntime.collectAsStateWithLifecycle()
            val lifecycleOwner = LocalLifecycleOwner.current
            val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
            MataTheme(appTheme = theme) {
                WidgetTodoActionDialog(
                    state = state,
                    adsRuntime = adsRuntime,
                    isForeground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED),
                    onComplete = { viewModel.perform(WidgetTodoAction.COMPLETE) },
                    onSkip = { viewModel.perform(WidgetTodoAction.SKIP) },
                    onCancel = ::finish,
                )
            }
        }
    }
}

@Composable
internal fun WidgetTodoActionDialog(
    state: WidgetTodoActionUiState,
    adsRuntime: AdsRuntimeState,
    isForeground: Boolean,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.title != null -> {
                    Text(
                        text = stringResource(R.string.widget_action_prompt, state.title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.widget_action_unavailable),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            state.errorMessageRes?.let { messageRes ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(messageRes),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            MataBannerAd(
                runtimeState = adsRuntime,
                isForeground = isForeground,
                isScreenVisible = true,
                isImeVisible = false,
                hasOverlay = false,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !state.isSubmitting) {
                    Text(stringResource(R.string.action_cancel))
                }
                if (state.title != null) {
                    TextButton(onClick = onSkip, enabled = !state.isSubmitting && state.actionsEnabled) {
                        Text(stringResource(R.string.action_skip))
                    }
                    Button(onClick = onComplete, enabled = !state.isSubmitting && state.actionsEnabled) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_complete))
                    }
                }
            }
        }
    }
}

internal data class WidgetTodoActionUiState(
    val isLoading: Boolean = true,
    val title: String? = null,
    val actionsEnabled: Boolean = false,
    val isSubmitting: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
)

internal enum class WidgetTodoAction {
    COMPLETE,
    SKIP,
}

internal data class WidgetTodoActionRequest(
    val todoId: String,
    val logicalDate: LocalDate,
    val expectedRevision: Int,
    val appWidgetId: Int,
    val snapshotVersion: Int,
) {
    fun isStructurallyValid(): Boolean =
        todoId.isNotBlank() &&
            expectedRevision > 0 &&
            appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            snapshotVersion == WidgetDisplayModel.CURRENT_VERSION
}

@HiltViewModel
internal class WidgetTodoActionViewModel @Inject constructor(
    private val coordinator: WidgetTodoActionCoordinator,
    private val adsConsentRepository: AdsConsentRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WidgetTodoActionUiState())
    val uiState: StateFlow<WidgetTodoActionUiState> = _uiState
    val adsRuntime = adsConsentRepository.state
    val theme = settingsRepository.theme
        .catch { emit(AppTheme.SYSTEM) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.SYSTEM)
    private val finishChannel = Channel<Unit>(Channel.BUFFERED)
    val effects = finishChannel.receiveAsFlow()
    private var request: WidgetTodoActionRequest? = null

    fun initialize(value: WidgetTodoActionRequest) {
        if (request != null) return
        request = value
        viewModelScope.launch {
            coordinator.load(value)
                .onSuccess { title ->
                    _uiState.value = WidgetTodoActionUiState(
                        isLoading = false,
                        title = title,
                        actionsEnabled = true,
                    )
                }
                .onFailure {
                    _uiState.value = WidgetTodoActionUiState(
                        isLoading = false,
                    )
                }
        }
    }

    fun gatherConsent(activity: Activity) {
        adsConsentRepository.gatherConsent(activity)
    }

    fun perform(action: WidgetTodoAction) {
        val currentRequest = request ?: return
        val currentState = _uiState.value
        if (!currentState.actionsEnabled || currentState.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessageRes = null) }
        viewModelScope.launch {
            coordinator.perform(currentRequest, action)
                .onSuccess { finishChannel.send(Unit) }
                .onFailure { throwable ->
                    val isValidationFailure = throwable is ValidationException ||
                        throwable is WidgetTodoActionUnavailableException
                    _uiState.update {
                        if (isValidationFailure) {
                            WidgetTodoActionUiState(isLoading = false)
                        } else {
                            it.copy(
                                isSubmitting = false,
                                errorMessageRes = throwable.toUserMessageRes(
                                    if (action == WidgetTodoAction.COMPLETE) {
                                        R.string.error_todo_complete_failed
                                    } else {
                                        R.string.error_todo_skip_failed
                                    },
                                ),
                            )
                        }
                    }
                }
        }
    }
}

@Singleton
internal class WidgetTodoActionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoRepository: TodoRepository,
    private val executionDao: TodoExecutionDao,
    private val widgetStateDao: WidgetInstanceStateDao,
    private val widgetUpdater: WidgetUpdater,
    private val refreshCoordinator: WidgetRefreshCoordinator,
    private val clock: Clock,
) {
    suspend fun load(request: WidgetTodoActionRequest): Result<String> {
        val result = runCatching { currentTodo(request).title }
        if (result.isFailure && request.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            runCatching { refreshCoordinator.refreshAll(request.appWidgetId) }
        }
        return result
    }

    suspend fun perform(request: WidgetTodoActionRequest, action: WidgetTodoAction): Result<Unit> {
        val todo = runCatching { currentTodo(request) }.getOrElse { throwable ->
            runCatching { refreshCoordinator.refreshAll(request.appWidgetId) }
            return Result.failure(throwable)
        }
        val operationId = UUID.randomUUID().toString()
        val result = when (action) {
            WidgetTodoAction.COMPLETE -> todoRepository.setCompleted(
                todoId = request.todoId,
                logicalDate = request.logicalDate,
                completed = true,
                operationId = operationId,
            )
            WidgetTodoAction.SKIP -> todoRepository.setSkipped(
                todoId = request.todoId,
                logicalDate = request.logicalDate,
                skipped = true,
                operationId = operationId,
            )
        }
        if (result.isFailure) {
            if (result.exceptionOrNull() is ValidationException) {
                runCatching { refreshCoordinator.refreshAll(request.appWidgetId) }
            }
            return result
        }
        if (action == WidgetTodoAction.COMPLETE) {
            runCatching { recordCompletionUndo(request.appWidgetId, operationId, todo.title) }
        }
        runCatching { refreshCoordinator.refreshAll(request.appWidgetId) }
        return Result.success(Unit)
    }

    private suspend fun currentTodo(request: WidgetTodoActionRequest): Todo {
        if (!request.isStructurallyValid() ||
            request.appWidgetId !in activeAppWidgetIds(context)
        ) {
            throw WidgetTodoActionUnavailableException()
        }
        val todo = todoRepository.getTodo(request.todoId)
        if (!todo.isAvailableFor(request)) throw WidgetTodoActionUnavailableException()
        return checkNotNull(todo)
    }

    private suspend fun recordCompletionUndo(appWidgetId: Int, operationId: String, title: String) {
        if (executionDao.findByOperationId(operationId) == null) return
        val now = clock.millis()
        val expiresAt = now + WIDGET_UNDO_DURATION_MILLIS
        val previous = widgetStateDao.find(appWidgetId)
        widgetStateDao.upsert(
            (previous ?: emptyWidgetActionState(appWidgetId, now)).copy(
                undoOperationId = operationId,
                undoTodoTitle = title,
                undoExpiresAt = expiresAt,
                updatedAt = now,
            ),
        )
        widgetUpdater.scheduleUndoExpiry(appWidgetId, expiresAt)
    }
}

internal fun Todo?.isAvailableFor(request: WidgetTodoActionRequest): Boolean =
    this != null && archivedAt == null && definitionRevision >= request.expectedRevision

internal class WidgetTodoActionUnavailableException : IllegalStateException()

internal fun widgetTodoActionIntent(
    context: Context,
    request: WidgetTodoActionRequest,
): Intent = Intent(context, WidgetTodoActionActivity::class.java).apply {
    action = ACTION_WIDGET_TODO
    data = Uri.Builder()
        .scheme("mata")
        .authority("widget-action")
        .appendPath(request.todoId)
        .appendQueryParameter(EXTRA_LOGICAL_DATE, request.logicalDate.toString())
        .appendQueryParameter(EXTRA_APP_WIDGET_ID, request.appWidgetId.toString())
        .appendQueryParameter(EXTRA_DEFINITION_REVISION, request.expectedRevision.toString())
        .appendQueryParameter(EXTRA_SNAPSHOT_VERSION, request.snapshotVersion.toString())
        .build()
    putExtra(EXTRA_TODO_ID, request.todoId)
    putExtra(EXTRA_LOGICAL_DATE, request.logicalDate.toString())
    putExtra(EXTRA_DEFINITION_REVISION, request.expectedRevision)
    putExtra(EXTRA_APP_WIDGET_ID, request.appWidgetId)
    putExtra(EXTRA_SNAPSHOT_VERSION, request.snapshotVersion)
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}

private fun Intent.toWidgetTodoActionRequest(): WidgetTodoActionRequest? {
    if (action != ACTION_WIDGET_TODO) return null
    val todoId = getStringExtra(EXTRA_TODO_ID) ?: return null
    val logicalDate = getStringExtra(EXTRA_LOGICAL_DATE)?.let { value ->
        runCatching { LocalDate.parse(value) }.getOrNull()
    } ?: return null
    if (!hasExtra(EXTRA_DEFINITION_REVISION) ||
        !hasExtra(EXTRA_APP_WIDGET_ID) ||
        !hasExtra(EXTRA_SNAPSHOT_VERSION)
    ) return null
    return WidgetTodoActionRequest(
        todoId = todoId,
        logicalDate = logicalDate,
        expectedRevision = getIntExtra(EXTRA_DEFINITION_REVISION, -1),
        appWidgetId = getIntExtra(EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        snapshotVersion = getIntExtra(EXTRA_SNAPSHOT_VERSION, -1),
    )
}

private fun emptyWidgetActionState(appWidgetId: Int, now: Long) = WidgetInstanceStateEntity(
    appWidgetId = appWidgetId,
    snapshotVersion = WidgetDisplayModel.CURRENT_VERSION,
    snapshotJson = null,
    lastSuccessAt = null,
    loadState = LOAD_LOADING,
    errorCode = null,
    lastFailureAt = null,
    undoOperationId = null,
    undoTodoTitle = null,
    undoExpiresAt = null,
    nextRefreshAt = null,
    updatedAt = now,
)

private const val ACTION_WIDGET_TODO = "com.mochisofts.mata.action.WIDGET_TODO"
private const val EXTRA_TODO_ID = "widget_action_todo_id"
private const val EXTRA_LOGICAL_DATE = "widget_action_logical_date"
private const val EXTRA_DEFINITION_REVISION = "widget_action_definition_revision"
private const val EXTRA_APP_WIDGET_ID = "widget_action_app_widget_id"
private const val EXTRA_SNAPSHOT_VERSION = "widget_action_snapshot_version"
private const val WIDGET_UNDO_DURATION_MILLIS = 15_000L
