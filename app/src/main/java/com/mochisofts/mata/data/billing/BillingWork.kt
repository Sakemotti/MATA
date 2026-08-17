package com.mochisofts.mata.data.billing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mochisofts.mata.domain.model.EntitlementState
import com.mochisofts.mata.domain.model.EntitlementStatus
import com.mochisofts.mata.domain.repository.EntitlementRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingWorkScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    fun sync(status: EntitlementStatus) {
        val verifiedState = status.state.takeIf(EntitlementState::isVerified)
            ?: status.lastVerifiedState
        if (verifiedState == EntitlementState.PENDING) {
            workManager.enqueueUniquePeriodicWork(
                PENDING_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BillingReconcileWorker>(12, TimeUnit.HOURS).build(),
            )
        } else {
            workManager.cancelUniqueWork(PENDING_WORK)
        }

        if (verifiedState == EntitlementState.PURCHASED_UNACKNOWLEDGED) {
            workManager.enqueueUniqueWork(
                ACKNOWLEDGEMENT_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<BillingReconcileWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(ACKNOWLEDGEMENT_WORK)
        }
    }

    internal companion object {
        const val PENDING_WORK = "billing-pending-reconciliation"
        const val ACKNOWLEDGEMENT_WORK = "billing-acknowledgement"
    }
}

class BillingReconcileWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            BillingWorkerEntryPoint::class.java,
        ).entitlementRepository()
        runCatching { repository.refresh() }.getOrElse { return Result.retry() }
        val status = repository.state.value.entitlement
        val verifiedState = status.state.takeIf(EntitlementState::isVerified)
            ?: status.lastVerifiedState
        return if (verifiedState == EntitlementState.PURCHASED_UNACKNOWLEDGED) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BillingWorkerEntryPoint {
    fun entitlementRepository(): EntitlementRepository
}
