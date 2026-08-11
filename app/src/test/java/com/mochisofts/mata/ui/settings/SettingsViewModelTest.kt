package com.mochisofts.mata.ui.settings

import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun valuesLoadAndSuccessfulChangesAreReflected() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(repository, FakeNotificationScheduler())

        assertFalse(viewModel.uiState.value.isLoading)
        viewModel.setEndHour(4)
        viewModel.setWeekStart(DayOfWeek.SUNDAY)
        viewModel.setShowCompleted(true)
        viewModel.setTheme(AppTheme.DARK)

        assertEquals(4, viewModel.uiState.value.endHour)
        assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.weekStart)
        assertEquals(true, viewModel.uiState.value.showCompleted)
        assertEquals(AppTheme.DARK, viewModel.uiState.value.theme)
        assertNull(viewModel.uiState.value.savingSetting)
    }

    @Test
    fun failedSaveKeepsPreviousValueAndEmitsMessage() = runTest {
        val repository = FakeSettingsRepository().apply { failNextSave = true }
        val viewModel = SettingsViewModel(repository, FakeNotificationScheduler())

        viewModel.setEndHour(4)

        assertEquals(0, viewModel.uiState.value.endHour)
        assertEquals(
            R.string.settings_save_error,
            (viewModel.effects.first() as SettingsEffect.Message).messageRes,
        )
    }

    private class FakeSettingsRepository : SettingsRepository {
        private val showCompletedState = MutableStateFlow(false)
        private val todoListModeState = MutableStateFlow("DATE")
        private val endHourState = MutableStateFlow(0)
        private val weekStartState = MutableStateFlow(DayOfWeek.MONDAY)
        private val themeState = MutableStateFlow(AppTheme.SYSTEM)
        private val notificationPermissionRequestedState = MutableStateFlow(false)

        var failNextSave = false

        override val showCompleted: Flow<Boolean> = showCompletedState
        override val todoListMode: Flow<String> = todoListModeState
        override val uncategorizedEndHour: Flow<Int> = endHourState
        override val weekStart: Flow<DayOfWeek> = weekStartState
        override val theme: Flow<AppTheme> = themeState
        override val notificationPermissionRequested: Flow<Boolean> =
            notificationPermissionRequestedState

        override suspend fun setShowCompleted(value: Boolean) {
            beforeSave()
            showCompletedState.value = value
        }

        override suspend fun setTodoListMode(value: String) {
            beforeSave()
            todoListModeState.value = value
        }

        override suspend fun setUncategorizedEndHour(value: Int) {
            beforeSave()
            endHourState.value = value
        }

        override suspend fun setWeekStart(value: DayOfWeek) {
            beforeSave()
            weekStartState.value = value
        }

        override suspend fun setTheme(value: AppTheme) {
            beforeSave()
            themeState.value = value
        }

        override suspend fun setNotificationPermissionRequested(value: Boolean) {
            beforeSave()
            notificationPermissionRequestedState.value = value
        }

        private fun beforeSave() {
            if (failNextSave) {
                failNextSave = false
                error("save failed")
            }
        }
    }

    private class FakeNotificationScheduler : NotificationScheduler {
        override val notificationCount: Flow<Int> = MutableStateFlow(0)

        override fun systemState() = NotificationSystemState(
            canPostNotifications = true,
            runtimePermissionRelevant = false,
            runtimePermissionGranted = true,
            exactAlarmRelevant = false,
            canScheduleExactAlarms = true,
        )

        override suspend fun reconcileTodo(todoId: String) = Unit
        override suspend fun reconcileAll() = Unit
        override suspend fun cancelTodo(todoId: String) = Unit
    }
}
