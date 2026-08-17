package com.mochisofts.mata.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.HistoryReconciler
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.data.holiday.HolidayWorkScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@HiltViewModel
class MataAppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    private val historyReconciler: HistoryReconciler,
    private val holidayRepository: HolidayRepository,
    private val holidayWorkScheduler: HolidayWorkScheduler,
) : ViewModel() {
    val theme: StateFlow<AppTheme> = settingsRepository.theme
        .catch { emit(AppTheme.SYSTEM) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppTheme.SYSTEM,
        )

    fun notificationOpened(todoId: String) {
        viewModelScope.launch { notificationScheduler.reconcileTodo(todoId) }
    }

    fun appResumed() {
        viewModelScope.launch {
            if (runCatching { holidayRepository.needsRefresh() }.getOrDefault(false)) {
                holidayWorkScheduler.enqueueImmediate()
            }
            runCatching {
                do {
                    val result = historyReconciler.reconcile()
                    if (result.hasMore) yield()
                } while (result.hasMore)
            }
            runCatching { notificationScheduler.reconcileAll() }
        }
    }
}
