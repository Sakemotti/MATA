package com.mochisofts.mata.store

import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StoreScreenshotSeedEntryPoint {
    fun database(): MataDatabase
    fun settingsRepository(): SettingsRepository
    fun notificationScheduler(): NotificationScheduler
    fun widgetUpdater(): WidgetUpdater
}
