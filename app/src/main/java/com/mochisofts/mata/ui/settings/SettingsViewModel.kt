package com.mochisofts.mata.ui.settings

import android.app.Activity
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.BillingEvent
import com.mochisofts.mata.domain.model.BillingLaunchResult
import com.mochisofts.mata.domain.model.BillingState
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.EntitlementRepository
import com.mochisofts.mata.data.backup.BackupCoordinator
import com.mochisofts.mata.data.backup.BackupErrorCode
import com.mochisofts.mata.data.backup.BackupOperationState
import com.mochisofts.mata.data.backup.BackupOperationStatus
import com.mochisofts.mata.data.backup.BackupOperationType
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SavingSetting {
    END_HOUR,
    WEEK_START,
    SHOW_COMPLETED,
    THEME,
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val hasLoadError: Boolean = false,
    val endHour: Int = 0,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val showCompleted: Boolean = false,
    val theme: AppTheme = AppTheme.SYSTEM,
    val notificationCount: Int = 0,
    val notificationSystemState: NotificationSystemState = NotificationSystemState(
        canPostNotifications = false,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    ),
    val savingSetting: SavingSetting? = null,
    val backupOperation: BackupOperationState = BackupOperationState(),
    val billing: BillingState = BillingState(),
)

sealed interface SettingsEffect {
    data class Message(@StringRes val messageRes: Int) : SettingsEffect
    data object RestoreCompleted : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val backupCoordinator: BackupCoordinator? = null,
    private val entitlementRepository: EntitlementRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = effectsChannel.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            notificationScheduler.notificationCount.collect { count ->
                _uiState.update { state -> state.copy(notificationCount = count) }
            }
        }
        refreshNotificationStatus(reconcile = false)
        viewModelScope.launch {
            entitlementRepository.state.collect { billing ->
                _uiState.update { it.copy(billing = billing) }
            }
        }
        viewModelScope.launch {
            entitlementRepository.events.collect { event ->
                val message = when (event) {
                    BillingEvent.PURCHASED -> R.string.settings_ads_purchase_success
                    BillingEvent.PENDING -> R.string.settings_ads_purchase_pending_message
                    BillingEvent.RESTORED -> R.string.settings_ads_restore_success
                    BillingEvent.NOTHING_TO_RESTORE -> R.string.settings_ads_nothing_to_restore
                    BillingEvent.ERROR -> R.string.settings_ads_billing_error
                    BillingEvent.USER_CANCELED -> null
                }
                if (message != null) effectsChannel.send(SettingsEffect.Message(message))
            }
        }
        backupCoordinator?.let { coordinator ->
            viewModelScope.launch {
                coordinator.state.collect { operation ->
                    _uiState.update { it.copy(backupOperation = operation) }
                    when (operation.status) {
                        BackupOperationStatus.SUCCEEDED -> {
                            if (operation.type == BackupOperationType.RESTORE) {
                                effectsChannel.send(SettingsEffect.RestoreCompleted)
                            } else {
                                effectsChannel.send(SettingsEffect.Message(R.string.backup_create_success))
                            }
                            coordinator.acknowledgeResult()
                        }
                        BackupOperationStatus.FAILED -> {
                            effectsChannel.send(SettingsEffect.Message(operation.errorMessage()))
                            coordinator.acknowledgeResult()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    fun retry() = load()

    fun setEndHour(value: Int) = save(SavingSetting.END_HOUR) {
        repository.setUncategorizedEndHour(value)
    }

    fun setWeekStart(value: DayOfWeek) = save(SavingSetting.WEEK_START) {
        repository.setWeekStart(value)
    }

    fun setShowCompleted(value: Boolean) = save(SavingSetting.SHOW_COMPLETED) {
        repository.setShowCompleted(value)
    }

    fun setTheme(value: AppTheme) = save(SavingSetting.THEME) {
        repository.setTheme(value)
    }

    fun purchaseAdRemoval(activity: Activity) {
        viewModelScope.launch {
            val message = runCatching { entitlementRepository.launchPurchase(activity) }
                .fold(
                    onSuccess = { result ->
                        when (result) {
                            BillingLaunchResult.STARTED,
                            BillingLaunchResult.ALREADY_IN_PROGRESS,
                            -> null
                            BillingLaunchResult.PRODUCT_UNAVAILABLE ->
                                R.string.settings_ads_product_unavailable
                            BillingLaunchResult.BILLING_UNAVAILABLE ->
                                R.string.settings_ads_billing_unavailable
                            BillingLaunchResult.ERROR -> R.string.settings_ads_billing_error
                        }
                    },
                    onFailure = { R.string.settings_ads_billing_error },
                )
            if (message != null) effectsChannel.send(SettingsEffect.Message(message))
        }
    }

    fun restoreAdRemoval() {
        viewModelScope.launch {
            runCatching { entitlementRepository.restore() }
                .onFailure {
                    effectsChannel.send(
                        SettingsEffect.Message(R.string.settings_ads_billing_error),
                    )
                }
        }
    }

    fun retryBilling() {
        viewModelScope.launch {
            runCatching { entitlementRepository.refresh() }
                .onFailure {
                    effectsChannel.send(
                        SettingsEffect.Message(R.string.settings_ads_billing_error),
                    )
                }
        }
    }

    fun suggestedBackupFileName(): String = backupCoordinator?.suggestedFileName().orEmpty()

    fun createTargetSelected(uri: Uri?) {
        if (uri != null && backupCoordinator?.startCreate(uri) == false) {
            viewModelScope.launch {
                effectsChannel.send(SettingsEffect.Message(R.string.backup_operation_already_running))
            }
        }
    }

    fun restoreFileSelected(uri: Uri?) {
        if (uri != null && backupCoordinator?.startRestoreValidation(uri) == false) {
            viewModelScope.launch {
                effectsChannel.send(SettingsEffect.Message(R.string.backup_operation_already_running))
            }
        }
    }

    fun confirmRestore() {
        if (backupCoordinator?.confirmRestore() == false) {
            viewModelScope.launch {
                effectsChannel.send(SettingsEffect.Message(R.string.backup_restore_start_error))
            }
        }
    }

    fun cancelRestoreConfirmation() {
        backupCoordinator?.cancelRestoreConfirmation()
    }

    fun refreshNotificationStatus(reconcile: Boolean = true) {
        _uiState.update { state ->
            state.copy(notificationSystemState = notificationScheduler.systemState())
        }
        if (reconcile) {
            viewModelScope.launch { runCatching { notificationScheduler.reconcileAll() } }
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, hasLoadError = false) }
        loadJob = viewModelScope.launch {
            combine(
                repository.uncategorizedEndHour,
                repository.weekStart,
                repository.showCompleted,
                repository.theme,
            ) { endHour, weekStart, showCompleted, theme ->
                SettingsSnapshot(endHour, weekStart, showCompleted, theme)
            }.catch {
                _uiState.update { state ->
                    state.copy(isLoading = false, hasLoadError = true)
                }
            }.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        hasLoadError = false,
                        endHour = settings.endHour,
                        weekStart = settings.weekStart,
                        showCompleted = settings.showCompleted,
                        theme = settings.theme,
                    )
                }
            }
        }
    }

    private fun save(setting: SavingSetting, operation: suspend () -> Unit) {
        if (_uiState.value.savingSetting != null) return
        _uiState.update { it.copy(savingSetting = setting) }
        viewModelScope.launch {
            runCatching { operation() }
                .onFailure {
                    effectsChannel.send(SettingsEffect.Message(R.string.settings_save_error))
                }
                .onSuccess {
                    if (setting == SavingSetting.END_HOUR || setting == SavingSetting.WEEK_START) {
                        runCatching { notificationScheduler.reconcileAll() }
                    }
                }
            _uiState.update { state ->
                state.copy(savingSetting = null)
            }
        }
    }

    private data class SettingsSnapshot(
        val endHour: Int,
        val weekStart: DayOfWeek,
        val showCompleted: Boolean,
        val theme: AppTheme,
    )
}

@StringRes
private fun BackupOperationState.errorMessage(): Int = when (errorCode) {
    BackupErrorCode.INVALID_FILE -> R.string.backup_invalid_file
    BackupErrorCode.UNSUPPORTED_VERSION -> R.string.backup_unsupported_version
    BackupErrorCode.STORAGE_UNAVAILABLE -> R.string.backup_storage_error
    BackupErrorCode.NOT_ENOUGH_SPACE -> R.string.backup_not_enough_space
    BackupErrorCode.INCOMPLETE_FILE_REMAINS -> R.string.backup_incomplete_file_remains
    BackupErrorCode.RESTORE_ROLLED_BACK -> R.string.backup_restore_rolled_back
    BackupErrorCode.INTERNAL, null -> R.string.backup_operation_error
}
