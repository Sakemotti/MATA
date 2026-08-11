package com.mochisofts.mata.app

import android.app.Application
import com.mochisofts.mata.data.notification.NotificationChannels
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HistoryReconciler
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        applicationScope.launch {
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
