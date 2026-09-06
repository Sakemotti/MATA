package com.mochisofts.mata.core.backup

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow

const val BACKUP_MIME_TYPE = "application/zip"

data class BackupCounts(
    val categories: Int,
    val todos: Int,
    val notifications: Int,
    val executions: Int,
    val periodResults: Int,
    val runtimeStates: Int,
) {
    val totalRecords: Long
        get() = categories.toLong() + todos + notifications + executions + periodResults + runtimeStates
}

data class BackupManifest(
    val backupId: String,
    val createdAt: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val roomSchemaVersion: Int,
    val dataSha256: String,
    val dataUncompressedBytes: Long,
    val counts: BackupCounts,
    val formatVersion: Int,
)

data class BackupSummary(
    val manifest: BackupManifest,
    val archivedTodoCount: Int,
)

enum class BackupOperationType {
    CREATE,
    RESTORE_VALIDATION,
    RESTORE,
}

enum class BackupOperationStatus {
    IDLE,
    RUNNING,
    AWAITING_CONFIRMATION,
    SUCCEEDED,
    FAILED,
}

enum class BackupOperationPhase {
    NONE,
    PREPARING,
    WRITING,
    VALIDATING,
    RESTORING,
    REBUILDING,
    ROLLING_BACK,
}

enum class BackupErrorCode {
    INVALID_FILE,
    UNSUPPORTED_VERSION,
    STORAGE_UNAVAILABLE,
    NOT_ENOUGH_SPACE,
    INCOMPLETE_FILE_REMAINS,
    RESTORE_ROLLED_BACK,
    INTERNAL,
}

data class BackupOperationState(
    val operationId: String? = null,
    val type: BackupOperationType? = null,
    val status: BackupOperationStatus = BackupOperationStatus.IDLE,
    val phase: BackupOperationPhase = BackupOperationPhase.NONE,
    val progress: Int? = null,
    val summary: BackupSummary? = null,
    val errorCode: BackupErrorCode? = null,
) {
    val blocksDataChanges: Boolean
        get() = status == BackupOperationStatus.RUNNING ||
            status == BackupOperationStatus.AWAITING_CONFIRMATION
}

/** Android document and worker boundary consumed by app and UI orchestration. */
interface BackupGateway {
    val state: StateFlow<BackupOperationState>
    fun suggestedFileName(): String
    fun startCreate(uri: Uri): Boolean
    fun startRestoreValidation(uri: Uri): Boolean
    fun confirmRestore(): Boolean
    fun cancelRestoreConfirmation()
    fun acknowledgeResult()
    suspend fun recoverInterruptedOperation()
}
