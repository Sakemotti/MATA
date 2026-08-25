package com.mochisofts.mata.data.notification

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mochisofts.mata.data.local.ScheduledNotificationDao
import com.mochisofts.mata.domain.repository.NotificationScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class NotificationReconcileWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationReconcileWorkerEntryPoint::class.java,
        )
        val succeeded = runCatching {
            dependencies.notificationScheduler().rebuildAll()
            dependencies.scheduledNotificationDao()
                .countByState(AndroidNotificationScheduler.STATE_FAILED) == 0
        }.getOrDefault(false)
        return when {
            succeeded -> Result.success()
            runAttemptCount < MAX_RETRY_INDEX -> Result.retry()
            else -> Result.failure()
        }
    }

    private companion object {
        const val MAX_RETRY_INDEX = 4
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationReconcileWorkerEntryPoint {
    fun notificationScheduler(): NotificationScheduler
    fun scheduledNotificationDao(): ScheduledNotificationDao
}

@Singleton
class NotificationReconcileWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueueRebuild() {
        val request = OneTimeWorkRequest.Builder(NotificationReconcileWorker::class.java)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "notification-alarm-rebuild"
    }
}
