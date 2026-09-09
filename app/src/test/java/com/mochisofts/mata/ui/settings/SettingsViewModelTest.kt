package com.mochisofts.mata.ui.settings

import android.app.Activity
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun st042_fullPartialAndOperationFailuresRemainRetryable() = runTest {
        val repository = RetrySettingsRepository()
        val scheduler = FaultNotificationScheduler(failState = true)
        val ads = FakeAdsConsentRepository()
        val viewModel = SettingsViewModel(repository, scheduler, adsConsentRepository = ads)
        runCurrent()

        assertTrue(viewModel.uiState.value.hasLoadError)
        assertTrue(viewModel.uiState.value.hasNotificationStatusError)

        repository.failLoad = false
        scheduler.failState = false
        viewModel.retry()
        viewModel.refreshNotificationStatus()
        runCurrent()
        assertFalse(viewModel.uiState.value.hasLoadError)
        assertFalse(viewModel.uiState.value.hasNotificationStatusError)
        assertEquals(4, viewModel.uiState.value.endHour)

        repository.failSave = true
        viewModel.setEndHour(8)
        runCurrent()
        assertEquals(4, viewModel.uiState.value.endHour)
        assertEquals(
            R.string.settings_save_error,
            (viewModel.effects.first() as SettingsEffect.Message).messageRes,
        )

        ads.eventFlow.emit(AdsConsentEvent.PRIVACY_OPTIONS_ERROR)
        runCurrent()
        assertEquals(
            R.string.settings_ads_privacy_options_error,
            (viewModel.effects.first() as SettingsEffect.Message).messageRes,
        )
    }

    @Test
    fun valuesLoadAndSuccessfulChangesAreReflected() = runTest {
        val repository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(
            repository,
            FakeNotificationScheduler(),
            adsConsentRepository = FakeAdsConsentRepository(),
        )

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
        val viewModel = SettingsViewModel(
            repository,
            FakeNotificationScheduler(),
            adsConsentRepository = FakeAdsConsentRepository(),
        )

        viewModel.setEndHour(4)

        assertEquals(0, viewModel.uiState.value.endHour)
        assertEquals(
            R.string.settings_save_error,
            (viewModel.effects.first() as SettingsEffect.Message).messageRes,
        )
    }

    @Test
    fun loadFailureCanBeRetriedWithoutKeepingFallbackValuesVisible() = runTest {
        val repository = RetrySettingsRepository()
        val viewModel = SettingsViewModel(
            repository,
            FakeNotificationScheduler(),
            adsConsentRepository = FakeAdsConsentRepository(),
        )

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(true, viewModel.uiState.value.hasLoadError)

        repository.failLoad = false
        viewModel.retry()

        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasLoadError)
        assertEquals(4, viewModel.uiState.value.endHour)
        assertEquals(DayOfWeek.SUNDAY, viewModel.uiState.value.weekStart)
        assertEquals(true, viewModel.uiState.value.showCompleted)
        assertEquals(AppTheme.DARK, viewModel.uiState.value.theme)
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
        override val dayEndHour: Flow<Int> = endHourState
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

        override suspend fun setDayEndHour(value: Int) {
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

    private class FakeAdsConsentRepository : AdsConsentRepository {
        override val state: StateFlow<AdsRuntimeState> = MutableStateFlow(AdsRuntimeState())
        val eventFlow = MutableSharedFlow<AdsConsentEvent>()
        override val events: Flow<AdsConsentEvent> = eventFlow

        override fun gatherConsent(activity: Activity) = Unit
        override fun showPrivacyOptions(activity: Activity) = Unit
    }

    private class RetrySettingsRepository : SettingsRepository {
        var failLoad = true
        var failSave = false
        override val showCompleted: Flow<Boolean>
            get() = loadFlow(true)
        override val todoListMode: Flow<String>
            get() = loadFlow("DATE")
        override val dayEndHour: Flow<Int>
            get() = loadFlow(4)
        override val weekStart: Flow<DayOfWeek>
            get() = loadFlow(DayOfWeek.SUNDAY)
        override val theme: Flow<AppTheme>
            get() = loadFlow(AppTheme.DARK)
        override val notificationPermissionRequested: Flow<Boolean>
            get() = loadFlow(false)

        private fun <T> loadFlow(value: T): Flow<T> = flow {
            if (failLoad) error("load failed")
            emit(value)
        }

        override suspend fun setShowCompleted(value: Boolean) = beforeSave()
        override suspend fun setTodoListMode(value: String) = beforeSave()
        override suspend fun setDayEndHour(value: Int) = beforeSave()
        override suspend fun setWeekStart(value: DayOfWeek) = beforeSave()
        override suspend fun setTheme(value: AppTheme) = beforeSave()
        override suspend fun setNotificationPermissionRequested(value: Boolean) = beforeSave()

        private fun beforeSave() {
            if (failSave) error("save failed")
        }
    }

    private class FaultNotificationScheduler(var failState: Boolean) : NotificationScheduler {
        override val notificationCount: Flow<Int> = MutableStateFlow(0)
        override fun systemState(): NotificationSystemState {
            if (failState) error("system state failed")
            return NotificationSystemState(
                canPostNotifications = true,
                runtimePermissionRelevant = false,
                runtimePermissionGranted = true,
                exactAlarmRelevant = false,
                canScheduleExactAlarms = true,
            )
        }
        override suspend fun reconcileTodo(todoId: String) = Unit
        override suspend fun reconcileAll() = Unit
        override suspend fun cancelTodo(todoId: String) = Unit
    }
}
