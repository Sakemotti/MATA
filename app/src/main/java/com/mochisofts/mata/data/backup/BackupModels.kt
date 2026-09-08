package com.mochisofts.mata.data.backup

import com.mochisofts.mata.core.backup.BackupErrorCode
import com.mochisofts.mata.domain.model.AppTheme
import java.time.DayOfWeek

internal const val BACKUP_FORMAT_ID = "com.mochisofts.mata.backup"
internal const val BACKUP_FORMAT_VERSION = 4
internal const val MIN_SUPPORTED_BACKUP_FORMAT_VERSION = 1
internal const val BACKUP_EXTENSION = ".mata-backup"

data class BackupSettings(
    val dayEndHour: Int,
    val weekStartDay: DayOfWeek,
    val showCompletedTodos: Boolean,
    val theme: AppTheme,
)

internal class BackupFormatException(
    val code: BackupErrorCode = BackupErrorCode.INVALID_FILE,
    message: String,
) : Exception(message)
