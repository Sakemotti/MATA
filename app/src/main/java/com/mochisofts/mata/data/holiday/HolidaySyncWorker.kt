package com.mochisofts.mata.data.holiday

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

class HolidaySyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            HolidayWorkerEntryPoint::class.java,
        )
        val repository = dependencies.holidayRepository()
        if (!processPendingGeneration(repository, dependencies.notificationScheduler())) {
            return if (runAttemptCount < MAX_RETRY_INDEX) Result.retry() else terminalFailure()
        }

        val refresh = repository.refresh()
        if (refresh.successful) {
            return if (processPendingGeneration(repository, dependencies.notificationScheduler())) {
                Result.success()
            } else if (runAttemptCount < MAX_RETRY_INDEX) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
        return if (refresh.retryable && runAttemptCount < MAX_RETRY_INDEX) {
            Result.retry()
        } else {
            terminalFailure()
        }
    }

    private fun terminalFailure(): Result = if (inputData.getBoolean(INPUT_IS_PERIODIC, false)) {
        // A periodic request must remain scheduled even after one update cycle exhausts its retries.
        Result.success()
    } else {
        Result.failure()
    }

    private suspend fun processPendingGeneration(
        repository: HolidayRepository,
        scheduler: NotificationScheduler,
    ): Boolean {
        val generation = repository.pendingNotificationGeneration() ?: return true
        return runCatching {
            scheduler.reconcileAll()
            repository.markNotificationGenerationProcessed(generation)
        }.isSuccess
    }

    private companion object {
        const val MAX_RETRY_INDEX = 4
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HolidayWorkerEntryPoint {
    fun holidayRepository(): HolidayRepository
    fun notificationScheduler(): NotificationScheduler
}

@Singleton
class HolidayWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic() {
        val request = PeriodicWorkRequest.Builder(
            HolidaySyncWorker::class.java,
            7,
            TimeUnit.DAYS,
            1,
            TimeUnit.DAYS,
        )
            .setConstraints(constraints)
            .setInputData(workDataOf(INPUT_IS_PERIODIC to true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueImmediate() {
        val request = OneTimeWorkRequest.Builder(HolidaySyncWorker::class.java)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "holiday-periodic-sync"
        const val IMMEDIATE_WORK_NAME = "holiday-immediate-sync"
    }
}

private const val INPUT_IS_PERIODIC = "is_periodic"
