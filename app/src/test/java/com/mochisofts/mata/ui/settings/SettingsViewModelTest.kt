package com.mochisofts.mata.ui.settings

import android.app.Activity
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.R
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.BillingEvent
import com.mochisofts.mata.domain.model.BillingLaunchResult
import com.mochisofts.mata.domain.model.BillingProduct
import com.mochisofts.mata.domain.model.BillingState
import com.mochisofts.mata.domain.model.EntitlementState
import com.mochisofts.mata.domain.model.EntitlementStatus
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.REMOVE_ADS_PRODUCT_ID
import com.mochisofts.mata.domain.repository.EntitlementRepository
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val viewModel = SettingsViewModel(
            repository,
            FakeNotificationScheduler(),
            entitlementRepository = FakeEntitlementRepository(),
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
            entitlementRepository = FakeEntitlementRepository(),
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
    fun billingStateIsReflectedInUiState() = runTest {
        val entitlementRepository = FakeEntitlementRepository()
        val viewModel = SettingsViewModel(
            FakeSettingsRepository(),
            FakeNotificationScheduler(),
            entitlementRepository = entitlementRepository,
            adsConsentRepository = FakeAdsConsentRepository(),
        )
        val billing = BillingState(
            entitlement = EntitlementStatus(
                state = EntitlementState.NOT_PURCHASED,
                lastVerifiedState = EntitlementState.NOT_PURCHASED,
            ),
            product = BillingProduct(REMOVE_ADS_PRODUCT_ID, "￥500"),
        )

        entitlementRepository.mutableState.value = billing

        assertEquals(billing, viewModel.uiState.value.billing)
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

    private class FakeEntitlementRepository : EntitlementRepository {
        val mutableState = MutableStateFlow(BillingState())
        private val mutableEvents = MutableSharedFlow<BillingEvent>()

        override val state: StateFlow<BillingState> = mutableState
        override val events: Flow<BillingEvent> = mutableEvents

        override suspend fun start() = Unit
        override suspend fun refresh() = Unit
        override suspend fun restore() = Unit
        override suspend fun launchPurchase(activity: Activity) = BillingLaunchResult.STARTED
    }

    private class FakeAdsConsentRepository : AdsConsentRepository {
        override val state: StateFlow<AdsRuntimeState> = MutableStateFlow(AdsRuntimeState())
        override val events: Flow<AdsConsentEvent> = MutableSharedFlow()

        override fun gatherConsent(activity: Activity) = Unit
        override fun showPrivacyOptions(activity: Activity) = Unit
    }
}
