package com.mochisofts.mata.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class BackupOperationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readState())
    val state: StateFlow<BackupOperationState> = mutableState.asStateFlow()

    @Synchronized
    fun start(operationId: String, type: BackupOperationType, uri: String): Boolean {
        if (mutableState.value.blocksDataChanges) return false
        preferences.edit()
            .clear()
            .putString(KEY_OPERATION_ID, operationId)
            .putString(KEY_TYPE, type.name)
            .putString(KEY_STATUS, BackupOperationStatus.RUNNING.name)
            .putString(KEY_PHASE, BackupOperationPhase.PREPARING.name)
            .putString(KEY_URI, uri)
            .commit()
        publish()
        return true
    }

    @Synchronized
    fun updateProgress(phase: BackupOperationPhase, progress: Int? = null) {
        if (mutableState.value.phase == phase && mutableState.value.progress == progress) return
        preferences.edit()
            .putString(KEY_PHASE, phase.name)
            .apply {
                if (progress == null) remove(KEY_PROGRESS) else putInt(KEY_PROGRESS, progress.coerceIn(0, 100))
            }
            .commit()
        publish()
    }

    @Synchronized
    fun awaitConfirmation(summary: BackupSummary) {
        writeSummary(
            preferences.edit()
                .putString(KEY_STATUS, BackupOperationStatus.AWAITING_CONFIRMATION.name)
                .putString(KEY_PHASE, BackupOperationPhase.NONE.name)
                .remove(KEY_PROGRESS),
            summary,
        ).commit()
        publish()
    }

    @Synchronized
    fun beginRestore() {
        preferences.edit()
            .putString(KEY_TYPE, BackupOperationType.RESTORE.name)
            .putString(KEY_STATUS, BackupOperationStatus.RUNNING.name)
            .putString(KEY_PHASE, BackupOperationPhase.PREPARING.name)
            .remove(KEY_PROGRESS)
            .commit()
        publish()
    }

    @Synchronized
    fun succeed() {
        preferences.edit()
            .putString(KEY_STATUS, BackupOperationStatus.SUCCEEDED.name)
            .putString(KEY_PHASE, BackupOperationPhase.NONE.name)
            .remove(KEY_PROGRESS)
            .remove(KEY_URI)
            .commit()
        publish()
    }

    @Synchronized
    fun fail(code: BackupErrorCode) {
        preferences.edit()
            .putString(KEY_STATUS, BackupOperationStatus.FAILED.name)
            .putString(KEY_PHASE, BackupOperationPhase.NONE.name)
            .putString(KEY_ERROR, code.name)
            .remove(KEY_PROGRESS)
            .remove(KEY_URI)
            .commit()
        publish()
    }

    @Synchronized
    fun clear() {
        preferences.edit().clear().commit()
        publish()
    }

    fun uri(): String? = preferences.getString(KEY_URI, null)

    private fun publish() {
        mutableState.value = readState()
    }

    private fun readState(): BackupOperationState {
        val status = enumValueOrNull<BackupOperationStatus>(preferences.getString(KEY_STATUS, null))
            ?: BackupOperationStatus.IDLE
        val summary = if (preferences.contains(KEY_BACKUP_CREATED_AT)) {
            BackupSummary(
                manifest = BackupManifest(
                    backupId = preferences.getString(KEY_BACKUP_ID, "").orEmpty(),
                    createdAt = preferences.getLong(KEY_BACKUP_CREATED_AT, 0),
                    appVersionName = preferences.getString(KEY_APP_VERSION_NAME, "").orEmpty(),
                    appVersionCode = preferences.getLong(KEY_APP_VERSION_CODE, 0),
                    roomSchemaVersion = preferences.getInt(KEY_ROOM_VERSION, 0),
                    dataSha256 = preferences.getString(KEY_DATA_SHA, "").orEmpty(),
                    dataUncompressedBytes = preferences.getLong(KEY_DATA_BYTES, 0),
                    counts = BackupCounts(
                        categories = preferences.getInt(KEY_CATEGORIES, 0),
                        todos = preferences.getInt(KEY_TODOS, 0),
                        notifications = preferences.getInt(KEY_NOTIFICATIONS, 0),
                        executions = preferences.getInt(KEY_EXECUTIONS, 0),
                        periodResults = preferences.getInt(KEY_PERIOD_RESULTS, 0),
                        runtimeStates = preferences.getInt(KEY_RUNTIME_STATES, 0),
                    ),
                ),
                archivedTodoCount = preferences.getInt(KEY_ARCHIVED_TODOS, 0),
            )
        } else {
            null
        }
        return BackupOperationState(
            operationId = preferences.getString(KEY_OPERATION_ID, null),
            type = enumValueOrNull(preferences.getString(KEY_TYPE, null)),
            status = status,
            phase = enumValueOrNull(preferences.getString(KEY_PHASE, null)) ?: BackupOperationPhase.NONE,
            progress = preferences.takeIf { it.contains(KEY_PROGRESS) }?.getInt(KEY_PROGRESS, 0),
            summary = summary,
            errorCode = enumValueOrNull(preferences.getString(KEY_ERROR, null)),
        )
    }

    private fun writeSummary(
        editor: android.content.SharedPreferences.Editor,
        summary: BackupSummary,
    ): android.content.SharedPreferences.Editor = editor
        .putString(KEY_BACKUP_ID, summary.manifest.backupId)
        .putLong(KEY_BACKUP_CREATED_AT, summary.manifest.createdAt)
        .putString(KEY_APP_VERSION_NAME, summary.manifest.appVersionName)
        .putLong(KEY_APP_VERSION_CODE, summary.manifest.appVersionCode)
        .putInt(KEY_ROOM_VERSION, summary.manifest.roomSchemaVersion)
        .putString(KEY_DATA_SHA, summary.manifest.dataSha256)
        .putLong(KEY_DATA_BYTES, summary.manifest.dataUncompressedBytes)
        .putInt(KEY_CATEGORIES, summary.manifest.counts.categories)
        .putInt(KEY_TODOS, summary.manifest.counts.todos)
        .putInt(KEY_NOTIFICATIONS, summary.manifest.counts.notifications)
        .putInt(KEY_EXECUTIONS, summary.manifest.counts.executions)
        .putInt(KEY_PERIOD_RESULTS, summary.manifest.counts.periodResults)
        .putInt(KEY_RUNTIME_STATES, summary.manifest.counts.runtimeStates)
        .putInt(KEY_ARCHIVED_TODOS, summary.archivedTodoCount)

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "backup_operation"
        const val KEY_OPERATION_ID = "operation_id"
        const val KEY_TYPE = "type"
        const val KEY_STATUS = "status"
        const val KEY_PHASE = "phase"
        const val KEY_PROGRESS = "progress"
        const val KEY_URI = "uri"
        const val KEY_ERROR = "error"
        const val KEY_BACKUP_ID = "backup_id"
        const val KEY_BACKUP_CREATED_AT = "backup_created_at"
        const val KEY_APP_VERSION_NAME = "app_version_name"
        const val KEY_APP_VERSION_CODE = "app_version_code"
        const val KEY_ROOM_VERSION = "room_version"
        const val KEY_DATA_SHA = "data_sha"
        const val KEY_DATA_BYTES = "data_bytes"
        const val KEY_CATEGORIES = "categories"
        const val KEY_TODOS = "todos"
        const val KEY_NOTIFICATIONS = "notifications"
        const val KEY_EXECUTIONS = "executions"
        const val KEY_PERIOD_RESULTS = "period_results"
        const val KEY_RUNTIME_STATES = "runtime_states"
        const val KEY_ARCHIVED_TODOS = "archived_todos"
    }
}
