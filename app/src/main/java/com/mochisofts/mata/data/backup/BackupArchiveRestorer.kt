package com.mochisofts.mata.data.backup

import androidx.room.withTransaction
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.PeriodResultDao
import com.mochisofts.mata.data.local.PeriodResultEntity
import com.mochisofts.mata.data.local.ScheduledNotificationDao
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.local.TodoNotificationDao
import com.mochisofts.mata.data.local.TodoNotificationEntity
import com.mochisofts.mata.data.local.TodoRuntimeStateDao
import com.mochisofts.mata.data.local.TodoRuntimeStateEntity
import com.mochisofts.mata.data.local.WidgetInstanceStateDao
import com.mochisofts.mata.data.repository.DataStoreSettingsRepository
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.NotificationScheduler
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.yield

@Singleton
class BackupArchiveRestorer @Inject constructor(
    private val database: MataDatabase,
    private val categoryDao: CategoryDao,
    private val todoDao: TodoDao,
    private val notificationDao: TodoNotificationDao,
    private val executionDao: TodoExecutionDao,
    private val periodResultDao: PeriodResultDao,
    private val runtimeStateDao: TodoRuntimeStateDao,
    private val scheduledNotificationDao: ScheduledNotificationDao,
    private val widgetInstanceStateDao: WidgetInstanceStateDao,
    private val settingsRepository: DataStoreSettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val historyReconciler: HistoryReconciler,
    private val widgetUpdater: WidgetUpdater,
    private val writer: BackupArchiveWriter,
    private val reader: BackupArchiveReader,
) {
    suspend fun restore(
        dataFile: File,
        summary: BackupSummary,
        rollbackArchive: File,
        rollbackData: File,
        onProgress: (BackupOperationPhase, Int?) -> Unit,
    ) {
        if (dataFile.usableSpace < summary.manifest.dataUncompressedBytes) {
            throw BackupFormatException(BackupErrorCode.NOT_ENOUGH_SPACE, "Not enough temporary storage")
        }
        if (!rollbackArchive.exists()) {
            onProgress(BackupOperationPhase.PREPARING, null)
            FileOutputStream(rollbackArchive).use { writer.write(it) }
            FileInputStream(rollbackArchive).use { input ->
                reader.extractAndValidate(input, rollbackData)
            }
            rollbackData.delete()
        }
        val oldSettings = settingsRepository.backupSnapshot()
        cancelScheduledNotifications()
        try {
            replaceData(dataFile, summary.manifest, onProgress)
        } catch (error: Exception) {
            onProgress(BackupOperationPhase.ROLLING_BACK, null)
            runCatching { settingsRepository.restoreBackupSettings(oldSettings) }
            runCatching { notificationScheduler.reconcileAll() }
            throw error
        }

        onProgress(BackupOperationPhase.REBUILDING, null)
        runCatching {
            do {
                val result = historyReconciler.reconcile()
                if (result.hasMore) yield()
            } while (result.hasMore)
        }
        runCatching { notificationScheduler.reconcileAll() }
        widgetUpdater.requestUpdate()
        rollbackArchive.delete()
        rollbackData.delete()
    }

    private suspend fun replaceData(
        dataFile: File,
        manifest: BackupManifest,
        onProgress: (BackupOperationPhase, Int?) -> Unit,
    ) {
        database.withTransaction {
            scheduledNotificationDao.deleteAllForRestore()
            widgetInstanceStateDao.deleteAll()
            executionDao.deleteAllForRestore()
            periodResultDao.deleteAllForRestore()
            runtimeStateDao.deleteAllForRestore()
            notificationDao.deleteAllForRestore()
            todoDao.deleteAllForRestore()
            categoryDao.deleteAllForRestore()
            val parsed = reader.parseValidatedData(
                dataFile,
                manifest,
                DatabaseSink(),
            ) { _, progress -> onProgress(BackupOperationPhase.RESTORING, progress) }
            settingsRepository.restoreBackupSettings(parsed.settings)
            val actual = BackupCounts(
                categories = categoryDao.backupCount(),
                todos = todoDao.backupCount(),
                notifications = notificationDao.count(),
                executions = executionDao.backupCount(),
                periodResults = periodResultDao.backupCount(),
                runtimeStates = runtimeStateDao.backupCount(),
            )
            if (actual != manifest.counts || settingsRepository.backupSnapshot() != parsed.settings) {
                throw BackupFormatException(message = "Restored data verification failed")
            }
        }
    }

    private suspend fun cancelScheduledNotifications() {
        scheduledNotificationDao.findTodoIds().forEach { todoId ->
            notificationScheduler.cancelTodo(todoId)
        }
    }

    private inner class DatabaseSink : BackupDataSink {
        override suspend fun category(value: CategoryEntity) = categoryDao.insertBackup(value)
        override suspend fun todo(value: TodoEntity) = todoDao.insertBackup(value)
        override suspend fun notification(value: TodoNotificationEntity) = notificationDao.insertBackup(value)
        override suspend fun execution(value: TodoExecutionEntity) = executionDao.insert(value)
        override suspend fun periodResult(value: PeriodResultEntity) = periodResultDao.insertBackup(value)
        override suspend fun runtimeState(value: TodoRuntimeStateEntity) = runtimeStateDao.insertBackup(value)
    }
}
