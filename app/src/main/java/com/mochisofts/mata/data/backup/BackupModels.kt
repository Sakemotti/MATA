package com.mochisofts.mata.data.backup

import com.mochisofts.mata.domain.model.AppTheme
import java.time.DayOfWeek

internal const val BACKUP_FORMAT_ID = "com.mochisofts.mata.backup"
internal const val BACKUP_FORMAT_VERSION = 3
internal const val MIN_SUPPORTED_BACKUP_FORMAT_VERSION = 1
internal const val BACKUP_MIME_TYPE = "application/zip"
internal const val BACKUP_EXTENSION = ".mata-backup"

data class BackupSettings(
    val dayEndHour: Int,
    val weekStartDay: DayOfWeek,
    val showCompletedTodos: Boolean,
    val theme: AppTheme,
)

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
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
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

internal class BackupFormatException(
    val code: BackupErrorCode = BackupErrorCode.INVALID_FILE,
    message: String,
) : Exception(message)
