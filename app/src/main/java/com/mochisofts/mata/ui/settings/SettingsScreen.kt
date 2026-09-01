package com.mochisofts.mata.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import com.mochisofts.mata.R
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.app.MataAdaptiveNavigation
import com.mochisofts.mata.app.MataDestination
import com.mochisofts.mata.app.MataNavigationType
import com.mochisofts.mata.core.designsystem.mataClickablePointer
import com.mochisofts.mata.core.designsystem.mataPageKeyScroll
import com.mochisofts.mata.core.designsystem.MataSnackbarHost
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.BillingOperation
import com.mochisofts.mata.domain.model.BillingState
import com.mochisofts.mata.domain.model.EntitlementState
import com.mochisofts.mata.data.backup.BACKUP_MIME_TYPE
import com.mochisofts.mata.data.backup.BackupOperationPhase
import com.mochisofts.mata.data.backup.BackupOperationState
import com.mochisofts.mata.data.backup.BackupOperationStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDestination: (MataDestination) -> Unit,
    onOpenSourceLicenses: () -> Unit,
    onRestoreCompleted: () -> Unit = { onDestination(MataDestination.TODOS) },
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var showEndHourSheet by remember { mutableStateOf(false) }
    var showWeekStartSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showBackupWarning by remember { mutableStateOf(false) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE),
        viewModel::createTargetSelected,
    )
    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
        viewModel::restoreFileSelected,
    )
    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshNotificationStatus()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationStatus()
    }

    LaunchedEffect(viewModel, resources) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.Message -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                SettingsEffect.RestoreCompleted -> {
                    Toast.makeText(context, R.string.backup_restore_success, Toast.LENGTH_SHORT).show()
                    onRestoreCompleted()
                }
            }
        }
    }

    MataAdaptiveNavigation(
        selected = MataDestination.SETTINGS,
        drawerState = drawerState,
        onSelect = onDestination,
    ) { layoutInfo ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings_title)) },
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
                )
            },
            snackbarHost = { MataSnackbarHost(snackbarHostState) },
        ) { padding ->
            when {
                state.isLoading -> {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.hasLoadError -> {
                    Column(
                        Modifier.fillMaxSize().padding(padding).padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.settings_loading_error))
                        TextButton(onClick = viewModel::retry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
                else -> {
                    val settingsEnabled = state.savingSetting == null &&
                        !state.backupOperation.blocksDataChanges
                    val scrollState = rememberScrollState()
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .mataPageKeyScroll(scrollState)
                            .verticalScroll(scrollState),
                    ) {
                        SettingsSectionHeader(R.string.settings_section_general)
                        SettingsValueRow(
                            title = stringResource(R.string.settings_end_hour_title),
                            value = stringResource(R.string.hour_format, state.endHour),
                            description = stringResource(R.string.settings_end_hour_description),
                            isSaving = state.savingSetting == SavingSetting.END_HOUR,
                            enabled = settingsEnabled,
                            onClick = { showEndHourSheet = true },
                        )
                        Text(
                            text = if (state.endHour == 0) {
                                stringResource(R.string.day_boundary_midnight)
                            } else {
                                stringResource(
                                    R.string.day_boundary_format,
                                    state.endHour,
                                    state.endHour - 1,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_week_start_title),
                            value = weekdayName(state.weekStart),
                            description = stringResource(R.string.settings_week_start_description),
                            isSaving = state.savingSetting == SavingSetting.WEEK_START,
                            enabled = settingsEnabled,
                            onClick = { showWeekStartSheet = true },
                        )

                        SettingsSectionHeader(R.string.settings_section_display)
                        ShowCompletedRow(
                            checked = state.showCompleted,
                            isSaving = state.savingSetting == SavingSetting.SHOW_COMPLETED,
                            enabled = settingsEnabled,
                            onCheckedChange = viewModel::setShowCompleted,
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_theme_title),
                            value = themeName(state.theme),
                            description = stringResource(R.string.settings_theme_description),
                            isSaving = state.savingSetting == SavingSetting.THEME,
                            enabled = settingsEnabled,
                            onClick = { showThemeSheet = true },
                        )

                        SettingsSectionHeader(R.string.settings_section_notifications)
                        SettingsValueRow(
                            title = stringResource(R.string.settings_notification_permission_title),
                            value = stringResource(
                                if (state.notificationSystemState.canPostNotifications) {
                                    R.string.settings_permission_granted
                                } else {
                                    R.string.settings_permission_not_granted
                                },
                            ),
                            description = stringResource(R.string.settings_notification_permission_description),
                            isSaving = false,
                            enabled = settingsEnabled,
                            onClick = {
                                systemSettingsLauncher.launch(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            },
                        )
                        if (state.notificationSystemState.exactAlarmRelevant) {
                            HorizontalDivider()
                            SettingsValueRow(
                                title = stringResource(R.string.settings_exact_alarm_title),
                                value = stringResource(
                                    if (state.notificationSystemState.canScheduleExactAlarms) {
                                        R.string.settings_permission_granted
                                    } else {
                                        R.string.settings_permission_not_granted
                                    },
                                ),
                                description = stringResource(
                                    if (state.notificationSystemState.canScheduleExactAlarms) {
                                        R.string.settings_exact_alarm_description
                                    } else {
                                        R.string.settings_exact_alarm_fallback_description
                                    },
                                ),
                                isSaving = false,
                                enabled = settingsEnabled && state.notificationCount > 0,
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        systemSettingsLauncher.launch(
                                            Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:${context.packageName}"),
                                            ),
                                        )
                                    }
                                },
                            )
                        }

                        SettingsSectionHeader(R.string.settings_section_data)
                        SettingsValueRow(
                            title = stringResource(R.string.nav_archived_todos),
                            value = stringResource(R.string.settings_open_management),
                            description = stringResource(R.string.settings_archived_todos_description),
                            isSaving = false,
                            enabled = settingsEnabled,
                            onClick = { onDestination(MataDestination.ARCHIVE) },
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.backup_create_title),
                            value = stringResource(R.string.backup_create_value),
                            description = stringResource(R.string.backup_create_description),
                            isSaving = false,
                            enabled = settingsEnabled,
                            onClick = { showBackupWarning = true },
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.backup_restore_title),
                            value = stringResource(R.string.backup_restore_value),
                            description = stringResource(R.string.backup_restore_description),
                            isSaving = false,
                            enabled = settingsEnabled,
                            onClick = {
                                openDocumentLauncher.launch(
                                    arrayOf(BACKUP_MIME_TYPE, "application/octet-stream"),
                                )
                            },
                        )

                        SettingsSectionHeader(R.string.settings_section_ads)
                        BillingSettings(
                            billing = state.billing,
                            adsRuntime = state.adsRuntime,
                            canLaunchPurchase = activity != null,
                            onPurchase = {
                                activity?.let(viewModel::purchaseAdRemoval)
                            },
                            onRetry = viewModel::retryBilling,
                            onRestore = viewModel::restoreAdRemoval,
                            onPrivacyOptions = {
                                activity?.let(viewModel::showPrivacyOptions)
                            },
                        )

                        SettingsSectionHeader(R.string.settings_section_app_info)
                        SettingsStaticRow(
                            title = stringResource(R.string.settings_app_name_title),
                            value = stringResource(R.string.app_name),
                            description = stringResource(R.string.settings_app_name_description),
                        )
                        HorizontalDivider()
                        SettingsStaticRow(
                            title = stringResource(R.string.settings_version_title),
                            value = if (BuildConfig.DEBUG) {
                                stringResource(
                                    R.string.settings_version_debug_format,
                                    BuildConfig.VERSION_NAME,
                                )
                            } else {
                                BuildConfig.VERSION_NAME
                            },
                            description = stringResource(R.string.settings_version_description),
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_licenses_title),
                            value = stringResource(R.string.settings_licenses_value),
                            description = stringResource(R.string.settings_licenses_description),
                            isSaving = false,
                            enabled = true,
                            onClick = onOpenSourceLicenses,
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_privacy_policy_title),
                            value = stringResource(R.string.settings_privacy_policy_value),
                            description = stringResource(R.string.settings_privacy_policy_description),
                            isSaving = false,
                            enabled = true,
                            onClick = {
                                if (!openLegalDocument(
                                        context,
                                        BuildConfig.PRIVACY_POLICY_URL,
                                        PRIVACY_POLICY_PATH,
                                    )
                                ) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            resources.getString(R.string.settings_external_link_error),
                                        )
                                    }
                                }
                            },
                        )
                        HorizontalDivider()
                        SettingsValueRow(
                            title = stringResource(R.string.settings_terms_title),
                            value = stringResource(R.string.settings_terms_value),
                            description = stringResource(R.string.settings_terms_description),
                            isSaving = false,
                            enabled = true,
                            onClick = {
                                if (!openLegalDocument(
                                        context,
                                        BuildConfig.TERMS_URL,
                                        TERMS_PATH,
                                    )
                                ) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            resources.getString(R.string.settings_external_link_error),
                                        )
                                    }
                                }
                            },
                        )
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showEndHourSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.settings_end_hour_title),
            options = (0..23).toList(),
            selected = state.endHour,
            label = { stringResource(R.string.hour_format, it) },
            onSelect = {
                showEndHourSheet = false
                viewModel.setEndHour(it)
            },
            onDismiss = { showEndHourSheet = false },
        )
    }
    if (showWeekStartSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.settings_week_start_title),
            options = DayOfWeek.entries,
            selected = state.weekStart,
            label = { weekdayName(it) },
            onSelect = {
                showWeekStartSheet = false
                viewModel.setWeekStart(it)
            },
            onDismiss = { showWeekStartSheet = false },
        )
    }
    if (showThemeSheet) {
        SelectionBottomSheet(
            title = stringResource(R.string.settings_theme_title),
            options = AppTheme.entries,
            selected = state.theme,
            label = { themeName(it) },
            onSelect = {
                showThemeSheet = false
                viewModel.setTheme(it)
            },
            onDismiss = { showThemeSheet = false },
        )
    }
    if (showBackupWarning) {
        AlertDialog(
            onDismissRequest = { showBackupWarning = false },
            title = { Text(stringResource(R.string.backup_warning_title)) },
            text = { Text(stringResource(R.string.backup_warning_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupWarning = false
                        createDocumentLauncher.launch(viewModel.suggestedBackupFileName())
                    },
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    if (state.backupOperation.status == BackupOperationStatus.AWAITING_CONFIRMATION) {
        RestoreConfirmationDialog(
            operation = state.backupOperation,
            onConfirm = viewModel::confirmRestore,
            onCancel = viewModel::cancelRestoreConfirmation,
        )
    }
    if (state.backupOperation.status == BackupOperationStatus.RUNNING) {
        BackupProgressDialog(state.backupOperation)
    }
}

@Composable
private fun RestoreConfirmationDialog(
    operation: BackupOperationState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val summary = operation.summary ?: return
    val manifest = summary.manifest
    val timePattern = stringResource(R.string.date_time_pattern)
    val createdAt = remember(manifest.createdAt, timePattern) {
        DateTimeFormatter.ofPattern(timePattern)
            .format(Instant.ofEpochMilli(manifest.createdAt).atZone(ZoneId.systemDefault()))
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_restore_confirm_created_at, createdAt))
                Text(stringResource(R.string.backup_restore_confirm_app_version, manifest.appVersionName))
                Text(stringResource(R.string.backup_restore_confirm_todos, manifest.counts.todos))
                Text(stringResource(R.string.backup_restore_confirm_archived, summary.archivedTodoCount))
                Text(stringResource(R.string.backup_restore_confirm_categories, manifest.counts.categories))
                Text(stringResource(R.string.backup_restore_confirm_notifications, manifest.counts.notifications))
                Text(stringResource(R.string.backup_restore_confirm_history, manifest.counts.executions))
                Text(stringResource(R.string.backup_restore_confirm_periods, manifest.counts.periodResults))
                Text(
                    stringResource(R.string.backup_restore_confirm_warning),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun BackupProgressDialog(operation: BackupOperationState) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_progress_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator()
                Text(stringResource(operation.phase.labelResource()))
                operation.progress?.let {
                    Text(stringResource(R.string.backup_progress_percent, it))
                }
            }
        },
        confirmButton = {},
    )
}

@StringRes
private fun BackupOperationPhase.labelResource(): Int = when (this) {
    BackupOperationPhase.NONE,
    BackupOperationPhase.PREPARING,
    -> R.string.backup_phase_preparing
    BackupOperationPhase.WRITING -> R.string.backup_phase_writing
    BackupOperationPhase.VALIDATING -> R.string.backup_phase_validating
    BackupOperationPhase.RESTORING -> R.string.backup_phase_restoring
    BackupOperationPhase.REBUILDING -> R.string.backup_phase_rebuilding
    BackupOperationPhase.ROLLING_BACK -> R.string.backup_phase_rolling_back
}

@Composable
private fun SettingsSectionHeader(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    description: String,
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(value)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null)
            }
        },
        modifier = Modifier
            .mataClickablePointer(enabled)
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun SettingsStaticRow(
    title: String,
    value: String,
    description: String,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(value)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}

@Composable
private fun BillingSettings(
    billing: BillingState,
    adsRuntime: AdsRuntimeState,
    canLaunchPurchase: Boolean,
    onPurchase: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onPrivacyOptions: () -> Unit,
) {
    val operationInProgress = billing.operation != BillingOperation.IDLE
    val product = billing.product
    val entitlement = billing.entitlement
    val status = when (entitlement.state) {
        EntitlementState.UNKNOWN -> BillingRowStatus(
            value = stringResource(R.string.settings_ads_loading),
            description = stringResource(R.string.settings_ads_loading_description),
            showProgress = true,
        )
        EntitlementState.NOT_PURCHASED -> if (product != null) {
            BillingRowStatus(
                value = product.formattedPrice,
                description = stringResource(R.string.settings_ads_purchase_description),
                actionLabel = stringResource(R.string.settings_ads_purchase_action),
                actionEnabled = !operationInProgress && canLaunchPurchase,
                onAction = onPurchase,
                showProgress = operationInProgress,
            )
        } else {
            BillingRowStatus(
                value = stringResource(R.string.settings_ads_product_unavailable),
                description = stringResource(R.string.settings_ads_product_retry_description),
                actionLabel = stringResource(R.string.action_retry),
                actionEnabled = !operationInProgress,
                onAction = onRetry,
                showProgress = operationInProgress,
            )
        }
        EntitlementState.PENDING -> BillingRowStatus(
            value = stringResource(R.string.settings_ads_purchase_pending),
            description = stringResource(R.string.settings_ads_pending_description),
            showProgress = operationInProgress,
        )
        EntitlementState.PURCHASED_UNACKNOWLEDGED,
        EntitlementState.PURCHASED,
        -> BillingRowStatus(
            value = stringResource(R.string.settings_ads_removed),
            description = stringResource(R.string.settings_ads_removed_description),
            showProgress = operationInProgress,
        )
        EntitlementState.ERROR -> BillingRowStatus(
            value = stringResource(R.string.settings_ads_connection_error),
            description = stringResource(
                if (entitlement.adsRemoved) {
                    R.string.settings_ads_error_removed_description
                } else {
                    R.string.settings_ads_error_description
                },
            ),
            actionLabel = stringResource(R.string.action_retry),
            actionEnabled = !operationInProgress,
            onAction = onRetry,
            showProgress = operationInProgress,
        )
        EntitlementState.UNAVAILABLE -> BillingRowStatus(
            value = stringResource(R.string.settings_ads_billing_unavailable),
            description = stringResource(R.string.settings_ads_unavailable_description),
            showProgress = operationInProgress,
        )
    }

    BillingStatusRow(status)
    HorizontalDivider()
    SettingsValueRow(
        title = stringResource(R.string.settings_ads_restore_title),
        value = stringResource(R.string.settings_ads_restore_value),
        description = stringResource(R.string.settings_ads_restore_description),
        isSaving = billing.operation == BillingOperation.RESTORING,
        enabled = !operationInProgress,
        onClick = onRestore,
    )
    if (adsRuntime.privacyOptionsRequired) {
        HorizontalDivider()
        SettingsValueRow(
            title = stringResource(R.string.settings_ads_privacy_options_title),
            value = stringResource(R.string.settings_ads_privacy_options_value),
            description = stringResource(R.string.settings_ads_privacy_options_description),
            isSaving = adsRuntime.isShowingPrivacyOptions,
            enabled = canLaunchPurchase && !adsRuntime.isShowingPrivacyOptions,
            onClick = onPrivacyOptions,
        )
    }
}

private data class BillingRowStatus(
    val value: String,
    val description: String,
    val actionLabel: String? = null,
    val actionEnabled: Boolean = false,
    val onAction: () -> Unit = {},
    val showProgress: Boolean = false,
)

@Composable
private fun BillingStatusRow(status: BillingRowStatus) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_ads_remove_title)) },
        supportingContent = {
            Column {
                Text(status.value)
                Text(status.description, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            if (status.showProgress) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (status.actionLabel != null) {
                TextButton(
                    enabled = status.actionEnabled,
                    onClick = status.onAction,
                ) {
                    Text(status.actionLabel)
                }
            }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ShowCompletedRow(
    checked: Boolean,
    isSaving: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_completed_title)) },
        supportingContent = { Text(stringResource(R.string.settings_show_completed_description)) },
        trailingContent = {
            if (isSaving) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Switch(checked = checked, onCheckedChange = null)
            }
        },
        modifier = Modifier
            .mataClickablePointer(enabled)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectionBottomSheet(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .selectableGroup()
                .mataPageKeyScroll(listState),
            state = listState,
        ) {
            items(options) { option ->
                val isSelected = option == selected
                ListItem(
                    headlineContent = { Text(label(option)) },
                    trailingContent = {
                        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                    modifier = Modifier
                        .mataClickablePointer()
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        ),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun weekdayName(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.weekday_monday_full
        DayOfWeek.TUESDAY -> R.string.weekday_tuesday_full
        DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday_full
        DayOfWeek.THURSDAY -> R.string.weekday_thursday_full
        DayOfWeek.FRIDAY -> R.string.weekday_friday_full
        DayOfWeek.SATURDAY -> R.string.weekday_saturday_full
        DayOfWeek.SUNDAY -> R.string.weekday_sunday_full
    },
)

@Composable
private fun themeName(theme: AppTheme): String = stringResource(
    when (theme) {
        AppTheme.SYSTEM -> R.string.settings_theme_system
        AppTheme.LIGHT -> R.string.settings_theme_light
        AppTheme.DARK -> R.string.settings_theme_dark
    },
)
