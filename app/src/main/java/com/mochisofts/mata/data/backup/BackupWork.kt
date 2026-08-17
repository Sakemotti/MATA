package com.mochisofts.mata.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BackupOperationStore,
    private val clock: Clock,
) {
    val state = store.state

    fun suggestedFileName(): String = "MATA_backup_${FILE_TIME_FORMAT.format(LocalDateTime.now(clock))}$BACKUP_EXTENSION"

    fun startCreate(uri: Uri): Boolean = start(uri, BackupOperationType.CREATE)

    fun startRestoreValidation(uri: Uri): Boolean = start(uri, BackupOperationType.RESTORE_VALIDATION)

    fun confirmRestore(): Boolean {
        val current = store.state.value
        if (current.status != BackupOperationStatus.AWAITING_CONFIRMATION || current.summary == null) return false
        store.beginRestore()
        enqueue(current.operationId ?: return false, BackupOperationType.RESTORE)
        return true
    }

    fun cancelRestoreConfirmation() {
        val current = store.state.value
        if (current.status != BackupOperationStatus.AWAITING_CONFIRMATION) return
        current.operationId?.let { operationFiles(context, it).deleteAll() }
        releaseUri(store.uri())
        store.clear()
    }

    fun acknowledgeResult() {
        if (store.state.value.status in setOf(BackupOperationStatus.SUCCEEDED, BackupOperationStatus.FAILED)) {
            store.clear()
        }
    }

    suspend fun recoverInterruptedOperation() = withContext(Dispatchers.IO) {
        val current = store.state.value
        val operationId = current.operationId ?: return@withContext
        val files = operationFiles(context, operationId)
        when (current.status) {
            BackupOperationStatus.AWAITING_CONFIRMATION -> {
                if (!files.data.exists()) {
                    releaseUri(store.uri())
                    files.deleteAll()
                    store.fail(BackupErrorCode.STORAGE_UNAVAILABLE)
                }
            }
            BackupOperationStatus.RUNNING -> {
                val active = runCatching {
                    WorkManager.getInstance(context).getWorkInfosForUniqueWork(WORK_NAME).get()
                        .any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
                }.getOrDefault(false)
                if (!active) {
                    if (current.type == BackupOperationType.RESTORE && files.data.exists()) {
                        enqueue(operationId, BackupOperationType.RESTORE)
                    } else {
                        if (current.type == BackupOperationType.CREATE) deleteDocument(store.uri())
                        releaseUri(store.uri())
                        files.deleteAll()
                        store.fail(BackupErrorCode.STORAGE_UNAVAILABLE)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun start(uri: Uri, type: BackupOperationType): Boolean {
        val operationId = UUID.randomUUID().toString()
        takeUriPermission(uri, type)
        if (!store.start(operationId, type, uri.toString())) {
            releaseUri(uri.toString())
            return false
        }
        operationFiles(context, operationId).apply {
            deleteAll()
            directory.mkdirs()
        }
        enqueue(operationId, type)
        return true
    }

    private fun enqueue(operationId: String, type: BackupOperationType) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(
                workDataOf(
                    INPUT_OPERATION_ID to operationId,
                    INPUT_OPERATION_TYPE to type.name,
                ),
            )
            .addTag(operationId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun takeUriPermission(uri: Uri, type: BackupOperationType) {
        val flags = if (type == BackupOperationType.CREATE) {
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
    }

    private fun releaseUri(value: String?) {
        val uri = value?.let(Uri::parse) ?: return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun deleteDocument(value: String?): Boolean {
        val uri = value?.let(Uri::parse) ?: return true
        return runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
    }

    private companion object {
        val FILE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }
}

class BackupWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val operationId = inputData.getString(INPUT_OPERATION_ID) ?: return Result.failure()
        val type = inputData.getString(INPUT_OPERATION_TYPE)
            ?.let { runCatching { BackupOperationType.valueOf(it) }.getOrNull() }
            ?: return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            BackupWorkerEntryPoint::class.java,
        )
        val store = dependencies.store()
        if (store.state.value.operationId != operationId) return Result.success()
        val files = operationFiles(applicationContext, operationId)
        return dependencies.mutationGate().withBackupOperation {
            try {
                when (type) {
                    BackupOperationType.CREATE -> create(dependencies, store, files)
                    BackupOperationType.RESTORE_VALIDATION -> validate(dependencies, store, files)
                    BackupOperationType.RESTORE -> restore(dependencies, store, files)
                }
                Result.success()
            } catch (error: Exception) {
                handleFailure(type, store, files, error)
                Result.failure()
            }
        }
    }

    private suspend fun create(
        dependencies: BackupWorkerEntryPoint,
        store: BackupOperationStore,
        files: OperationFiles,
    ) {
        val uri = requiredUri(store)
        store.updateProgress(BackupOperationPhase.PREPARING)
        applicationContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
            dependencies.writer().write(output, store::updateProgress)
        } ?: throw FileNotFoundException("Output URI cannot be opened")
        store.updateProgress(BackupOperationPhase.VALIDATING)
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            dependencies.reader().extractAndValidate(input, files.data, store::updateProgress)
        } ?: throw FileNotFoundException("Output URI cannot be verified")
        files.deleteAll()
        releaseUri(uri)
        store.succeed()
    }

    private suspend fun validate(
        dependencies: BackupWorkerEntryPoint,
        store: BackupOperationStore,
        files: OperationFiles,
    ) {
        val uri = requiredUri(store)
        val summary = applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            dependencies.reader().extractAndValidate(input, files.data, store::updateProgress)
        } ?: throw FileNotFoundException("Input URI cannot be opened")
        store.awaitConfirmation(summary)
    }

    private suspend fun restore(
        dependencies: BackupWorkerEntryPoint,
        store: BackupOperationStore,
        files: OperationFiles,
    ) {
        val summary = store.state.value.summary ?: throw BackupFormatException(message = "Restore summary missing")
        if (!files.data.exists()) throw FileNotFoundException("Validated data is missing")
        dependencies.restorer().restore(
            dataFile = files.data,
            summary = summary,
            rollbackArchive = files.rollbackArchive,
            rollbackData = files.rollbackData,
            onProgress = store::updateProgress,
        )
        val uri = requiredUri(store)
        files.deleteAll()
        releaseUri(uri)
        store.succeed()
    }

    private fun handleFailure(
        type: BackupOperationType,
        store: BackupOperationStore,
        files: OperationFiles,
        error: Exception,
    ) {
        val uri = store.uri()?.let(Uri::parse)
        var errorCode = when (error) {
            is BackupFormatException -> error.code
            is FileNotFoundException, is SecurityException -> BackupErrorCode.STORAGE_UNAVAILABLE
            else -> if (type == BackupOperationType.RESTORE) {
                BackupErrorCode.RESTORE_ROLLED_BACK
            } else {
                BackupErrorCode.INTERNAL
            }
        }
        if (type == BackupOperationType.CREATE && uri != null) {
            val deleted = runCatching {
                applicationContext.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
            if (!deleted) errorCode = BackupErrorCode.INCOMPLETE_FILE_REMAINS
        }
        files.deleteAll()
        uri?.let(::releaseUri)
        store.fail(errorCode)
    }

    private fun requiredUri(store: BackupOperationStore): Uri =
        store.uri()?.let(Uri::parse) ?: throw FileNotFoundException("Operation URI is missing")

    private fun releaseUri(uri: Uri) {
        runCatching {
            applicationContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            applicationContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupWorkerEntryPoint {
    fun writer(): BackupArchiveWriter
    fun reader(): BackupArchiveReader
    fun restorer(): BackupArchiveRestorer
    fun store(): BackupOperationStore
    fun mutationGate(): DataMutationGate
}

internal data class OperationFiles(
    val directory: File,
    val data: File,
    val rollbackArchive: File,
    val rollbackData: File,
) {
    fun deleteAll() {
        data.delete()
        rollbackData.delete()
        rollbackArchive.delete()
        directory.delete()
    }
}

internal fun operationFiles(context: Context, operationId: String): OperationFiles {
    val directory = File(context.filesDir, "backup_operations/$operationId")
    return OperationFiles(
        directory = directory,
        data = File(directory, "data.json"),
        rollbackArchive = File(directory, "rollback.mata-backup"),
        rollbackData = File(directory, "rollback-data.json"),
    )
}

internal const val WORK_NAME = "manual-backup-or-restore"
private const val INPUT_OPERATION_ID = "operation_id"
private const val INPUT_OPERATION_TYPE = "operation_type"
