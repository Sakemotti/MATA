package com.mochisofts.mata.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MataAppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
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
}
