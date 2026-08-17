package com.mochisofts.mata.app

import android.app.Application
import com.mochisofts.mata.data.notification.NotificationChannels
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.data.holiday.HolidayWorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@HiltAndroidApp
class MataApplication : Application() {
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var historyReconciler: HistoryReconciler
    @Inject lateinit var holidayRepository: HolidayRepository
    @Inject lateinit var holidayWorkScheduler: HolidayWorkScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        holidayWorkScheduler.schedulePeriodic()
        applicationScope.launch {
            if (runCatching { holidayRepository.needsRefresh() }.getOrDefault(true)) {
                holidayWorkScheduler.enqueueImmediate()
            }
            runCatching {
                do {
                    val result = historyReconciler.reconcile()
                    if (result.hasMore) yield()
                } while (result.hasMore)
            }
            notificationScheduler.reconcileAll()
        }
    }
}
