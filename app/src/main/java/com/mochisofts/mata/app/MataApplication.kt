package com.mochisofts.mata.app

import android.app.Application
import com.mochisofts.mata.data.notification.NotificationChannels
import com.mochisofts.mata.domain.repository.NotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MataApplication : Application() {
    @Inject lateinit var notificationScheduler: NotificationScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        applicationScope.launch { notificationScheduler.reconcileAll() }
    }
}
