package com.mochisofts.mata.app

import android.app.Application
import com.mochisofts.mata.data.notification.NotificationChannels
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.data.holiday.HolidayWorkScheduler
import com.mochisofts.mata.data.widget.WidgetUpdater
import com.mochisofts.mata.data.widget.WidgetPreviewPublisher
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import java.time.Clock
import java.time.LocalDate

@HiltAndroidApp
class MataApplication : Application() {
    @Inject lateinit var notificationScheduler: NotificationScheduler
    @Inject lateinit var historyReconciler: HistoryReconciler
    @Inject lateinit var holidayRepository: HolidayRepository
    @Inject lateinit var holidayWorkScheduler: HolidayWorkScheduler
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var widgetPreviewPublisher: WidgetPreviewPublisher
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var categoryRepository: CategoryRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var clock: Clock

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        holidayWorkScheduler.schedulePeriodic()
        widgetUpdater.ensureScheduledIfWidgetsExist()
        applicationScope.launch {
            runCatching { widgetPreviewPublisher.publishIfNeeded() }
        }
        applicationScope.launch {
            combine(
                todoRepository.observeOccurrences(LocalDate.now(clock)),
                categoryRepository.observeCategories(),
                settingsRepository.uncategorizedEndHour,
                settingsRepository.weekStart,
                settingsRepository.theme,
            ) { occurrences, categories, endHour, weekStart, theme ->
                WidgetInvalidationState(
                    occurrenceCount = occurrences.size,
                    categoryFingerprint = categories.hashCode(),
                    uncategorizedEndHour = endHour,
                    weekStartValue = weekStart.value,
                    themeCode = theme.code,
                )
            }
                .drop(1)
                .collect { widgetUpdater.requestUpdate() }
        }
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

private data class WidgetInvalidationState(
    val occurrenceCount: Int,
    val categoryFingerprint: Int,
    val uncategorizedEndHour: Int,
    val weekStartValue: Int,
    val themeCode: String,
)
