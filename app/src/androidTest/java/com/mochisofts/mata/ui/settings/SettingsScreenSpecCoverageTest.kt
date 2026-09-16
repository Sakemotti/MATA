package com.mochisofts.mata.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.R
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.core.backup.BackupCounts
import com.mochisofts.mata.core.backup.BACKUP_MIME_TYPE
import com.mochisofts.mata.core.backup.BackupGateway
import com.mochisofts.mata.core.backup.BackupManifest
import com.mochisofts.mata.core.backup.BackupOperationPhase
import com.mochisofts.mata.core.backup.BackupOperationState
import com.mochisofts.mata.core.backup.BackupOperationStatus
import com.mochisofts.mata.core.backup.BackupOperationType
import com.mochisofts.mata.core.backup.BackupSummary
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.mataUsesDarkTheme
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.repository.NotificationChangeImpact
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun st001_drawerShowsSettingsAsTheSelectedFullScreenDestination() {
        val destinations = mutableListOf<MataDestination>()
        setScreen(onDestination = destinations::add)

        composeRule.onNodeWithContentDescription(text(R.string.content_description_open_menu))
            .performClick()
        composeRule.onNode(
            hasText(text(R.string.nav_settings)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.nav_todo_list)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { destinations.isNotEmpty() }
        composeRule.runOnIdle { assertEquals(listOf(MataDestination.TODOS), destinations) }
    }

    @Test
    fun st002_sectionsAppearInSpecifiedTopToBottomOrder() {
        setScreen()

        val sectionTitles = listOf(
            text(R.string.settings_section_general),
            text(R.string.settings_section_display),
            text(R.string.settings_section_notifications),
            text(R.string.settings_section_data),
            text(R.string.settings_section_app_info),
        )
        val renderedTexts = composeRule.onAllNodes(
            SemanticsMatcher("has text") {
                it.config.contains(SemanticsProperties.Text)
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { node ->
            node.config[SemanticsProperties.Text].map { it.text }
        }
        val positions = sectionTitles.map(renderedTexts::indexOf)

        assertTrue("All settings sections must exist: $positions", positions.all { it >= 0 })
        assertEquals("Settings sections must retain their specified order", positions.sorted(), positions)
    }

    @Test
    fun st003_rowsExposeTitleValueDescriptionAndExpectedActionType() {
        setScreen()

        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.hour_format, 0)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_end_hour_description)).assertIsDisplayed()
        listOf(
            R.string.settings_end_hour_title,
            R.string.settings_licenses_title,
            R.string.backup_create_title,
        ).forEach { resourceId ->
            composeRule.onNode(
                hasClickAction() and hasText(text(resourceId)),
            ).assertExists()
        }
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
                hasAnyDescendant(hasText(text(R.string.settings_show_completed_title))),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNode(
            hasText(text(R.string.settings_app_name_title)) and
                hasText(text(R.string.app_name)) and
                !hasClickAction(),
        ).assertExists()
    }

    @Test
    fun st005_endHourOffersEveryWholeHourAndPersistsSelection() {
        val repository = setScreen()

        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).performClick()
        val options = composeRule.onNodeWithTag(SETTINGS_SELECTION_OPTIONS_TEST_TAG)
        (0..23).forEach { hour ->
            options.performScrollToIndex(hour)
            selectionOption(text(R.string.hour_format, hour)).assertExists()
        }
        selectionOption(text(R.string.hour_format, 23)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { repository.endHour.value == 23 }
        composeRule.onNodeWithText(text(R.string.hour_format, 23)).assertIsDisplayed()
    }

    @Test
    fun st006_endHourExplainsItsGlobalAndDynamicLogicalDayBoundary() {
        setScreen()

        composeRule.onNodeWithText(text(R.string.settings_end_hour_description))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.day_boundary_midnight)).assertIsDisplayed()

        selectEndHour(4)
        composeRule.onNodeWithText(text(R.string.day_boundary_format, 4, 3))
            .assertIsDisplayed()

        selectEndHour(12)
        composeRule.onNodeWithText(text(R.string.day_boundary_format, 12, 11))
            .assertIsDisplayed()
    }

    @Test
    fun st008_invalidNotificationImpactCanBeCancelledOrConfirmedBeforeChanging() {
        val scheduler = SettingsScreenTestNotificationScheduler(
            endHourImpact = NotificationChangeImpact(todoCount = 2, notificationCount = 3),
        )
        val repository = setScreen(notificationScheduler = scheduler)
        var reconcileCallsBeforeChange = 0
        composeRule.runOnIdle { reconcileCallsBeforeChange = scheduler.reconcileCalls }

        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).performClick()
        composeRule.onNodeWithTag(SETTINGS_SELECTION_OPTIONS_TEST_TAG).performScrollToIndex(4)
        selectionOption(text(R.string.hour_format, 4)).performClick()

        composeRule.onNodeWithText(text(R.string.settings_end_hour_impact_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            text(R.string.settings_end_hour_impact_message, 2, 3),
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, repository.endHour.value) }
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeRule.onNodeWithText(text(R.string.settings_end_hour_impact_title)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, repository.endHour.value) }

        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).performClick()
        composeRule.onNodeWithTag(SETTINGS_SELECTION_OPTIONS_TEST_TAG).performScrollToIndex(4)
        selectionOption(text(R.string.hour_format, 4)).performClick()
        composeRule.onNodeWithText(text(R.string.action_change)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { repository.endHour.value == 4 }
        composeRule.onNodeWithText(
            text(R.string.settings_invalid_notifications_suppressed, 3),
        ).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(4, 4, 4), scheduler.previewedEndHours)
            assertEquals(reconcileCallsBeforeChange + 1, scheduler.reconcileCalls)
        }
    }

    @Test
    fun st009_weekStartOffersAllSevenDaysAndPersistsSelection() {
        val repository = setScreen()

        composeRule.onNodeWithText(text(R.string.settings_week_start_title))
            .performScrollTo().performClick()
        val options = composeRule.onNodeWithTag(SETTINGS_SELECTION_OPTIONS_TEST_TAG)
        val weekdays = listOf(
            R.string.weekday_monday_full,
            R.string.weekday_tuesday_full,
            R.string.weekday_wednesday_full,
            R.string.weekday_thursday_full,
            R.string.weekday_friday_full,
            R.string.weekday_saturday_full,
            R.string.weekday_sunday_full,
        )
        weekdays.forEachIndexed { index, resourceId ->
            options.performScrollToIndex(index)
            selectionOption(text(resourceId)).assertExists()
        }
        selectionOption(text(R.string.weekday_sunday_full)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.weekStart.value == DayOfWeek.SUNDAY
        }
        composeRule.onNodeWithText(text(R.string.weekday_sunday_full)).assertIsDisplayed()
    }

    @Test
    fun st013_themeChoicesPersistImmediatelyAndOnlySystemFollowsDeviceTheme() {
        val repository = setScreen()

        selectTheme(repository, AppTheme.LIGHT, R.string.settings_theme_light)
        selectTheme(repository, AppTheme.DARK, R.string.settings_theme_dark)
        selectTheme(repository, AppTheme.SYSTEM, R.string.settings_theme_system)

        composeRule.runOnIdle {
            assertFalse(mataUsesDarkTheme(AppTheme.LIGHT, systemInDarkTheme = false))
            assertFalse(mataUsesDarkTheme(AppTheme.LIGHT, systemInDarkTheme = true))
            assertTrue(mataUsesDarkTheme(AppTheme.DARK, systemInDarkTheme = false))
            assertTrue(mataUsesDarkTheme(AppTheme.DARK, systemInDarkTheme = true))
            assertFalse(mataUsesDarkTheme(AppTheme.SYSTEM, systemInDarkTheme = false))
            assertTrue(mataUsesDarkTheme(AppTheme.SYSTEM, systemInDarkTheme = true))
        }
    }

    @Test
    fun st014_notificationPermissionOpensAndroidSettingsAndRefreshesAfterReturn() {
        val deniedState = NotificationSystemState(
            canPostNotifications = false,
            runtimePermissionRelevant = true,
            runtimePermissionGranted = false,
            exactAlarmRelevant = false,
            canScheduleExactAlarms = true,
        )
        val scheduler = SettingsScreenTestNotificationScheduler(initialSystemState = deniedState)
        val lifecycleOwner = SettingsScreenTestLifecycleOwner()
        val launchedIntents = mutableListOf<Intent>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }
        setScreen(
            notificationScheduler = scheduler,
            systemSettingsIntentLauncher = launchedIntents::add,
            lifecycleOwner = lifecycleOwner,
        )

        composeRule.onNodeWithText(text(R.string.settings_notification_permission_title))
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            val intent = launchedIntents.single()
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
            assertEquals(
                InstrumentationRegistry.getInstrumentation().targetContext.packageName,
                intent.getStringExtra(Settings.EXTRA_APP_PACKAGE),
            )
            assertFalse(scheduler.systemStateValue.canPostNotifications)
        }
        composeRule.onNodeWithText(text(R.string.settings_permission_not_granted))
            .assertIsDisplayed()

        val callsBeforeReturn = scheduler.systemStateCalls
        val reconcilesBeforeReturn = scheduler.reconcileCalls
        composeRule.runOnIdle {
            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
            scheduler.systemStateValue = deniedState.copy(
                canPostNotifications = true,
                runtimePermissionGranted = true,
            )
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            scheduler.systemStateCalls > callsBeforeReturn &&
                scheduler.reconcileCalls > reconcilesBeforeReturn
        }
        composeRule.onNodeWithText(text(R.string.settings_permission_granted))
            .assertIsDisplayed()
    }

    @Test
    fun st015_exactAlarmRowOnlyAppearsWhenRelevantAndRefreshesAfterSettingsReturn() {
        val notRelevantState = NotificationSystemState(
            canPostNotifications = true,
            runtimePermissionRelevant = false,
            runtimePermissionGranted = true,
            exactAlarmRelevant = false,
            canScheduleExactAlarms = true,
        )
        val scheduler = SettingsScreenTestNotificationScheduler(
            initialNotificationCount = 1,
            initialSystemState = notRelevantState,
        )
        val lifecycleOwner = SettingsScreenTestLifecycleOwner()
        val launchedIntents = mutableListOf<Intent>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }
        setScreen(
            notificationScheduler = scheduler,
            systemSettingsIntentLauncher = launchedIntents::add,
            lifecycleOwner = lifecycleOwner,
        )

        composeRule.onNodeWithText(text(R.string.settings_exact_alarm_title))
            .assertDoesNotExist()

        val callsBeforeRelevant = scheduler.systemStateCalls
        val reconcilesBeforeRelevant = scheduler.reconcileCalls
        composeRule.runOnIdle {
            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
            scheduler.systemStateValue = notRelevantState.copy(
                exactAlarmRelevant = true,
                canScheduleExactAlarms = false,
            )
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            scheduler.systemStateCalls > callsBeforeRelevant &&
                scheduler.reconcileCalls > reconcilesBeforeRelevant &&
                composeRule.onAllNodesWithText(text(R.string.settings_exact_alarm_title))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text(R.string.settings_exact_alarm_title))
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithText(text(R.string.settings_permission_not_granted))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_exact_alarm_fallback_description))
            .assertIsDisplayed()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            composeRule.onNodeWithText(text(R.string.settings_exact_alarm_title)).performClick()
            composeRule.runOnIdle {
                val intent = launchedIntents.single()
                assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
                assertEquals(
                    "package:${InstrumentationRegistry.getInstrumentation().targetContext.packageName}",
                    intent.dataString,
                )
            }
        } else {
            composeRule.runOnIdle { assertTrue(launchedIntents.isEmpty()) }
        }

        val callsBeforeGrantedReturn = scheduler.systemStateCalls
        val reconcilesBeforeGrantedReturn = scheduler.reconcileCalls
        composeRule.runOnIdle {
            lifecycleOwner.moveTo(Lifecycle.State.CREATED)
            scheduler.systemStateValue = scheduler.systemStateValue.copy(
                canScheduleExactAlarms = true,
            )
            lifecycleOwner.moveTo(Lifecycle.State.RESUMED)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            scheduler.systemStateCalls > callsBeforeGrantedReturn &&
                scheduler.reconcileCalls > reconcilesBeforeGrantedReturn
        }
        composeRule.onNodeWithText(text(R.string.settings_exact_alarm_description))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.settings_permission_granted))
            .assertCountEquals(2)
    }

    @Test
    fun completedVisibilitySwitchPersistsImmediately() {
        val repository = setScreen(showCompleted = false)
        composeRule.onNodeWithText(text(R.string.settings_show_completed_title)).performScrollTo()
        val switch = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
            useUnmergedTree = true,
        )

        switch.assertIsOff().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { repository.showCompleted.value }
        switch.assertIsOn()
    }

    @Test
    fun st017_settingsContentHasNoDuplicateNavigationToOtherScreens() {
        setScreen()

        listOf(
            R.string.nav_todo_list,
            R.string.nav_category_todo_list,
            R.string.nav_calendar_history,
            R.string.nav_category_management,
            R.string.nav_archived_todos,
        ).forEach { destinationLabel ->
            composeRule.onNodeWithText(text(destinationLabel)).assertIsNotDisplayed()
        }
    }

    @Test
    fun app007_installedAppDisablesOsBackupAndOffersOnlyManualBackupActions() {
        setScreen()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)

        composeRule.onAllNodesWithText(text(R.string.backup_create_title)).assertCountEquals(1)
        composeRule.onAllNodesWithText(text(R.string.backup_restore_title)).assertCountEquals(1)
        composeRule.onNodeWithText(text(R.string.backup_create_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.backup_restore_title))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("自動バックアップ").assertDoesNotExist()
    }

    @Test
    fun st022_backupCreationShowsPersonalDataAndStorageWarningFirst() {
        setScreen()

        composeRule.onNodeWithText(text(R.string.backup_create_title)).performScrollTo().performClick()

        composeRule.onNodeWithText(text(R.string.backup_warning_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.backup_warning_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_continue)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun st018_backupUsesSafSuggestedNameWithoutStoragePermissionAndCancellationIsSilent() {
        val backupGateway = SettingsScreenTestBackupGateway()
        val requestedNames = mutableListOf<String>()
        lateinit var viewModel: SettingsViewModel
        setScreen(
            backupGateway = backupGateway,
            onViewModelCreated = { viewModel = it },
            createBackupTargetRequester = { suggestedName ->
                requestedNames += suggestedName
                viewModel.createTargetSelected(null)
            },
        )

        composeRule.onNodeWithText(text(R.string.backup_create_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(text(R.string.action_continue)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(backupGateway.suggestedFileName()), requestedNames)
            assertTrue(backupGateway.createUris.isEmpty())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val createDocumentIntent = ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
                .createIntent(context, requestedNames.single())
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, createDocumentIntent.action)
            assertEquals(BACKUP_MIME_TYPE, createDocumentIntent.type)
            assertEquals(requestedNames.single(), createDocumentIntent.getStringExtra(Intent.EXTRA_TITLE))
            val requestedPermissions = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            ).requestedPermissions.orEmpty().toSet()
            assertFalse("android.permission.READ_EXTERNAL_STORAGE" in requestedPermissions)
            assertFalse("android.permission.WRITE_EXTERNAL_STORAGE" in requestedPermissions)
            assertFalse("android.permission.MANAGE_EXTERNAL_STORAGE" in requestedPermissions)
        }
        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).assertExists()
        composeRule.onNodeWithText(text(R.string.backup_operation_error)).assertDoesNotExist()
    }

    @Test
    fun st030_settingsContainNoStandaloneAdvertisingSectionOrPlacementRow() {
        setScreen()

        composeRule.onNodeWithText("広告", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("バナー", substring = true).assertDoesNotExist()
    }

    @Test
    fun st031_settingsContainNoPaidProductPricePurchaseOrAdRemovalControls() {
        setScreen()

        listOf("有料", "価格", "購入", "広告削除").forEach { forbiddenText ->
            composeRule.onNodeWithText(forbiddenText, substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun st032_privacyOptionsRowIsHiddenWhenUmpDoesNotRequireIt() {
        val adsConsentRepository = SettingsScreenTestAdsConsentRepository(
            initialState = AdsRuntimeState(privacyOptionsRequired = false),
        )

        setScreen(adsConsentRepository = adsConsentRepository)

        composeRule.onNodeWithText(text(R.string.settings_ads_privacy_options_title))
            .assertDoesNotExist()
    }

    @Test
    fun st033_requiredPrivacyOptionsAppearInsideAppInformation() {
        val adsConsentRepository = SettingsScreenTestAdsConsentRepository(
            initialState = AdsRuntimeState(privacyOptionsRequired = true),
        )

        setScreen(adsConsentRepository = adsConsentRepository)

        composeRule.onNodeWithText(text(R.string.settings_ads_privacy_options_title))
            .performScrollTo()
            .assertIsDisplayed()
        val renderedTexts = composeRule.onAllNodes(
            SemanticsMatcher("has text") {
                it.config.contains(SemanticsProperties.Text)
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().flatMap { node ->
            node.config[SemanticsProperties.Text].map { it.text }
        }
        val positions = listOf(
            R.string.settings_section_app_info,
            R.string.settings_privacy_policy_title,
            R.string.settings_ads_privacy_options_title,
            R.string.settings_terms_title,
        ).map { resourceId -> renderedTexts.indexOf(text(resourceId)) }
        assertTrue(
            "privacy options must stay inside app information: $positions",
            positions.all { it >= 0 },
        )
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun st034_privacyOptionsRowLaunchesTheUmpFormOnce() {
        val adsConsentRepository = SettingsScreenTestAdsConsentRepository(
            initialState = AdsRuntimeState(privacyOptionsRequired = true),
        )

        setScreen(adsConsentRepository = adsConsentRepository)
        composeRule.onNodeWithText(text(R.string.settings_ads_privacy_options_title))
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, adsConsentRepository.showPrivacyOptionsCalls) }
    }

    @Test
    fun st036_umpFormFailureKeepsSettingsUsableAndShowsAnError() {
        val adsConsentRepository = SettingsScreenTestAdsConsentRepository(
            initialState = AdsRuntimeState(privacyOptionsRequired = true),
        )
        val repository = setScreen(adsConsentRepository = adsConsentRepository)

        composeRule.runOnIdle {
            assertTrue(adsConsentRepository.eventFlow.tryEmit(AdsConsentEvent.PRIVACY_OPTIONS_ERROR))
        }

        composeRule.onNodeWithText(text(R.string.settings_ads_privacy_options_error))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).assertExists()
        composeRule.runOnIdle { assertEquals(0, repository.endHour.value) }
    }

    @Test
    fun st037_appInfoShowsRequiredItemsAndOmitsWebsiteAndContact() {
        setScreen()

        listOf(
            R.string.settings_app_name_title,
            R.string.settings_version_title,
            R.string.settings_licenses_title,
            R.string.settings_privacy_policy_title,
            R.string.settings_terms_title,
        ).forEach { resourceId ->
            composeRule.onNodeWithText(text(resourceId)).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNode(
            hasText(text(R.string.app_name)) and
                hasText(text(R.string.settings_app_name_title)),
        ).assertIsDisplayed()
        composeRule.onNodeWithText("開発者Webサイト").assertDoesNotExist()
        composeRule.onNodeWithText("お問い合わせ").assertDoesNotExist()
    }

    @Test
    fun st038_debugVersionIsClearlyDistinguishable() {
        setScreen()
        val expectedVersion = text(
            R.string.settings_version_debug_format,
            BuildConfig.VERSION_NAME,
        )

        composeRule.onNodeWithText(expectedVersion).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun st027_successfulRestoreIsAcknowledgedAndReturnsToTodoList() {
        val backupGateway = SettingsScreenTestBackupGateway()
        var restoreCompletedCalls = 0
        setScreen(
            backupGateway = backupGateway,
            onRestoreCompleted = { restoreCompletedCalls += 1 },
        )

        composeRule.runOnIdle {
            backupGateway.runtimeState.value = BackupOperationState(
                operationId = "successful-restore",
                type = BackupOperationType.RESTORE,
                status = BackupOperationStatus.SUCCEEDED,
                phase = BackupOperationPhase.REBUILDING,
                progress = 100,
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) { restoreCompletedCalls == 1 }
        composeRule.runOnIdle { assertEquals(1, backupGateway.acknowledgeCalls) }
    }

    @Test
    fun st040_legalLinksOpenApprovedUrlsAndFailureKeepsSettingsVisible() {
        val opened = mutableListOf<Pair<String, String>>()
        var simulateBrowserFailure = false
        setScreen(
            legalDocumentOpener = { _: Context, url: String, expectedPath: String ->
                if (simulateBrowserFailure) {
                    false
                } else {
                    opened += url to expectedPath
                    true
                }
            },
        )

        composeRule.onNodeWithText(text(R.string.settings_privacy_policy_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(text(R.string.settings_terms_title))
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    BuildConfig.PRIVACY_POLICY_URL to PRIVACY_POLICY_PATH,
                    BuildConfig.TERMS_URL to TERMS_PATH,
                ),
                opened,
            )
            assertFalse(isApprovedLegalUrl("not-a-url", PRIVACY_POLICY_PATH))
            simulateBrowserFailure = true
        }

        composeRule.onNodeWithText(text(R.string.settings_privacy_policy_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(text(R.string.settings_external_link_error))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).assertExists()
    }

    @Test
    fun st045_maximumFontAndCompactDisplayKeepEverySettingAndActionReachable() {
        val adsConsentRepository = SettingsScreenTestAdsConsentRepository(
            initialState = AdsRuntimeState(privacyOptionsRequired = true),
        )
        val notificationScheduler = SettingsScreenTestNotificationScheduler(
            initialNotificationCount = 1,
            initialSystemState = NotificationSystemState(
                canPostNotifications = true,
                runtimePermissionRelevant = true,
                runtimePermissionGranted = true,
                exactAlarmRelevant = true,
                canScheduleExactAlarms = true,
            ),
        )
        val repository = setScreen(
            adsConsentRepository = adsConsentRepository,
            notificationScheduler = notificationScheduler,
            maximumFontCompactDisplay = true,
        )

        listOf(
            R.string.settings_section_general,
            R.string.settings_end_hour_title,
            R.string.settings_end_hour_description,
            R.string.day_boundary_midnight,
            R.string.settings_week_start_title,
            R.string.settings_week_start_description,
            R.string.settings_section_display,
            R.string.settings_show_completed_title,
            R.string.settings_show_completed_description,
            R.string.settings_theme_title,
            R.string.settings_theme_description,
            R.string.settings_section_notifications,
            R.string.settings_notification_permission_title,
            R.string.settings_notification_permission_description,
            R.string.settings_exact_alarm_title,
            R.string.settings_exact_alarm_description,
            R.string.settings_section_data,
            R.string.backup_create_title,
            R.string.backup_create_value,
            R.string.backup_create_description,
            R.string.backup_restore_title,
            R.string.backup_restore_value,
            R.string.backup_restore_description,
            R.string.settings_section_app_info,
            R.string.settings_app_name_title,
            R.string.settings_version_title,
            R.string.settings_licenses_title,
            R.string.settings_privacy_policy_title,
            R.string.settings_ads_privacy_options_title,
            R.string.settings_ads_privacy_options_value,
            R.string.settings_ads_privacy_options_description,
            R.string.settings_terms_title,
        ).forEach { resourceId ->
            composeRule.onNodeWithText(text(resourceId))
                .performScrollTo()
                .assertIsDisplayed()
        }

        val completedSwitch = composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
                hasAnyDescendant(hasText(text(R.string.settings_show_completed_title))),
            useUnmergedTree = true,
        )
        completedSwitch.performScrollTo().assertIsDisplayed().assertIsOff().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.showCompleted.value }
        completedSwitch.assertIsOn()

        composeRule.onNodeWithText(text(R.string.backup_create_title))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(text(R.string.backup_warning_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.backup_warning_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        composeRule.onNodeWithText(text(R.string.settings_ads_privacy_options_title))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(1, adsConsentRepository.showPrivacyOptionsCalls)
        }
    }

    @Test
    fun st025_restoreConfirmationShowsMetadataCountsAndIrreversibleWarning() {
        val backupGateway = SettingsScreenTestBackupGateway(restoreConfirmationState())
        setScreen(backupGateway = backupGateway)

        composeRule.onNodeWithText(text(R.string.backup_restore_confirm_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("作成日時:", substring = true).assertIsDisplayed()
        listOf(
            text(R.string.backup_restore_confirm_app_version, "3.0-test"),
            text(R.string.backup_restore_confirm_todos, 12),
            text(R.string.backup_restore_confirm_archived, 2),
            text(R.string.backup_restore_confirm_categories, 3),
            text(R.string.backup_restore_confirm_notifications, 4),
            text(R.string.backup_restore_confirm_history, 5),
            text(R.string.backup_restore_confirm_periods, 6),
            text(R.string.backup_restore_confirm_warning),
            text(R.string.backup_restore_action),
            text(R.string.action_cancel),
        ).forEach { expected -> composeRule.onNodeWithText(expected).assertIsDisplayed() }
    }

    @Test
    fun std04_restoreCanBeCancelledBeforeStartButNotWhileRunning() {
        val backupGateway = SettingsScreenTestBackupGateway(restoreConfirmationState())
        setScreen(backupGateway = backupGateway)

        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(1, backupGateway.cancelCalls) }
        composeRule.onNodeWithText(text(R.string.backup_restore_confirm_title))
            .assertDoesNotExist()

        composeRule.runOnIdle {
            backupGateway.runtimeState.value = restoreConfirmationState()
        }
        composeRule.onNodeWithText(text(R.string.backup_restore_action)).performClick()
        composeRule.runOnIdle { assertEquals(1, backupGateway.confirmCalls) }
        composeRule.onNodeWithText(text(R.string.backup_progress_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).assertDoesNotExist()

        pressBack()
        composeRule.onNodeWithText(text(R.string.backup_progress_title)).assertIsDisplayed()
    }

    private fun setScreen(
        showCompleted: Boolean = false,
        adsConsentRepository: SettingsScreenTestAdsConsentRepository =
            SettingsScreenTestAdsConsentRepository(),
        backupGateway: SettingsScreenTestBackupGateway? = null,
        onDestination: (MataDestination) -> Unit = {},
        onRestoreCompleted: () -> Unit = {},
        legalDocumentOpener: (Context, String, String) -> Boolean = ::openLegalDocument,
        notificationScheduler: SettingsScreenTestNotificationScheduler =
            SettingsScreenTestNotificationScheduler(),
        createBackupTargetRequester: ((String) -> Unit)? = null,
        systemSettingsIntentLauncher: ((Intent) -> Unit)? = null,
        lifecycleOwner: LifecycleOwner? = null,
        onViewModelCreated: (SettingsViewModel) -> Unit = {},
        maximumFontCompactDisplay: Boolean = false,
    ): SettingsScreenTestRepository {
        val repository = SettingsScreenTestRepository(showCompleted)
        val viewModel = SettingsViewModel(
            repository = repository,
            notificationScheduler = notificationScheduler,
            backupGateway = backupGateway,
            adsConsentRepository = adsConsentRepository,
        )
        onViewModelCreated(viewModel)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                val screen: @Composable () -> Unit = {
                    SettingsScreen(
                        onDestination = onDestination,
                        onOpenSourceLicenses = {},
                        onRestoreCompleted = onRestoreCompleted,
                        legalDocumentOpener = legalDocumentOpener,
                        createBackupTargetRequester = createBackupTargetRequester,
                        systemSettingsIntentLauncher = systemSettingsIntentLauncher,
                        viewModel = viewModel,
                    )
                }
                val lifecycleContent: @Composable () -> Unit = {
                    if (lifecycleOwner == null) {
                        screen()
                    } else {
                        CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                            screen()
                        }
                    }
                }
                if (maximumFontCompactDisplay) {
                    val baseDensity = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(baseDensity.density, fontScale = 2f),
                    ) {
                        Box(Modifier.width(320.dp).height(640.dp)) {
                            lifecycleContent()
                        }
                    }
                } else {
                    lifecycleContent()
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text(R.string.settings_end_hour_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
        return repository
    }

    private fun selectEndHour(hour: Int) {
        composeRule.onNodeWithText(text(R.string.settings_end_hour_title)).performClick()
        composeRule.onNodeWithTag(SETTINGS_SELECTION_OPTIONS_TEST_TAG).performScrollToIndex(hour)
        selectionOption(text(R.string.hour_format, hour)).performClick()
        composeRule.onNodeWithText(text(R.string.hour_format, hour)).assertIsDisplayed()
    }

    private fun selectTheme(
        repository: SettingsScreenTestRepository,
        theme: AppTheme,
        labelResource: Int,
    ) {
        composeRule.onNodeWithText(text(R.string.settings_theme_title))
            .performScrollTo()
            .performClick()
        listOf(
            R.string.settings_theme_system,
            R.string.settings_theme_light,
            R.string.settings_theme_dark,
        ).forEach { resourceId ->
            selectionOption(text(resourceId)).assertIsDisplayed()
        }
        selectionOption(text(labelResource)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            repository.themeState.value == theme
        }
        composeRule.onNodeWithText(text(labelResource)).assertIsDisplayed()
    }

    private fun restoreConfirmationState() = BackupOperationState(
        operationId = "restore-operation",
        type = BackupOperationType.RESTORE_VALIDATION,
        status = BackupOperationStatus.AWAITING_CONFIRMATION,
        phase = BackupOperationPhase.VALIDATING,
        summary = BackupSummary(
            manifest = BackupManifest(
                backupId = "backup-id",
                createdAt = 1_786_417_200_000L,
                appVersionName = "3.0-test",
                appVersionCode = 3,
                roomSchemaVersion = 5,
                dataSha256 = "a".repeat(64),
                dataUncompressedBytes = 1_024,
                counts = BackupCounts(
                    categories = 3,
                    todos = 12,
                    notifications = 4,
                    executions = 5,
                    periodResults = 6,
                    runtimeStates = 7,
                ),
                formatVersion = 4,
            ),
            archivedTodoCount = 2,
        ),
    )

    private fun text(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getString(resourceId, *formatArgs)

    private fun selectionOption(label: String) = composeRule.onNode(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and
            hasText(label),
    )
}

private class SettingsScreenTestRepository(showCompletedInitially: Boolean) : SettingsRepository {
    override val showCompleted = MutableStateFlow(showCompletedInitially)
    private val todoListModeState = MutableStateFlow("DATE")
    val endHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    val themeState = MutableStateFlow(AppTheme.SYSTEM)
    private val permissionRequestedState = MutableStateFlow(false)

    override val todoListMode: Flow<String> = todoListModeState
    override val dayEndHour: Flow<Int> = endHour
    override val theme: Flow<AppTheme> = themeState
    override val notificationPermissionRequested: Flow<Boolean> = permissionRequestedState

    override suspend fun setShowCompleted(value: Boolean) {
        showCompleted.emit(value)
    }

    override suspend fun setTodoListMode(value: String) {
        todoListModeState.emit(value)
    }

    override suspend fun setDayEndHour(value: Int) {
        endHour.emit(value)
    }

    override suspend fun setWeekStart(value: DayOfWeek) {
        weekStart.emit(value)
    }

    override suspend fun setTheme(value: AppTheme) {
        themeState.emit(value)
    }

    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        permissionRequestedState.emit(value)
    }
}

private class SettingsScreenTestNotificationScheduler(
    private val endHourImpact: NotificationChangeImpact = NotificationChangeImpact(),
    initialNotificationCount: Int = 0,
    initialSystemState: NotificationSystemState = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    ),
) : NotificationScheduler {
    override val notificationCount: Flow<Int> = MutableStateFlow(initialNotificationCount)
    val previewedEndHours = mutableListOf<Int>()
    var reconcileCalls = 0
    var systemStateCalls = 0
    var systemStateValue = initialSystemState

    override fun systemState(): NotificationSystemState {
        systemStateCalls += 1
        return systemStateValue
    }

    override suspend fun reconcileTodo(todoId: String) = Unit
    override suspend fun previewDayEndHourChange(newEndHour: Int): NotificationChangeImpact {
        previewedEndHours += newEndHour
        return endHourImpact
    }

    override suspend fun reconcileAll() {
        reconcileCalls += 1
    }
    override suspend fun cancelTodo(todoId: String) = Unit
}

private class SettingsScreenTestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = registry

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

private class SettingsScreenTestBackupGateway(
    initialState: BackupOperationState = BackupOperationState(),
) : BackupGateway {
    val runtimeState = MutableStateFlow(initialState)
    var confirmCalls = 0
    var cancelCalls = 0
    var acknowledgeCalls = 0
    val createUris = mutableListOf<Uri>()

    override val state: StateFlow<BackupOperationState> = runtimeState

    override fun suggestedFileName() = "MATA_backup_test.mata-backup"

    override fun startCreate(uri: Uri): Boolean {
        createUris += uri
        return true
    }

    override fun startRestoreValidation(uri: Uri) = true

    override fun confirmRestore(): Boolean {
        confirmCalls += 1
        runtimeState.value = BackupOperationState(
            operationId = "restore-operation",
            type = BackupOperationType.RESTORE,
            status = BackupOperationStatus.RUNNING,
            phase = BackupOperationPhase.RESTORING,
            progress = 50,
        )
        return true
    }

    override fun cancelRestoreConfirmation() {
        cancelCalls += 1
        runtimeState.value = BackupOperationState()
    }

    override fun acknowledgeResult() {
        acknowledgeCalls += 1
    }

    override suspend fun recoverInterruptedOperation() = Unit
}

private class SettingsScreenTestAdsConsentRepository(
    initialState: AdsRuntimeState = AdsRuntimeState(),
) : AdsConsentRepository {
    val runtimeState = MutableStateFlow(initialState)
    val eventFlow = MutableSharedFlow<AdsConsentEvent>(extraBufferCapacity = 1)
    var showPrivacyOptionsCalls = 0

    override val state: StateFlow<AdsRuntimeState> = runtimeState
    override val events: Flow<AdsConsentEvent> = eventFlow

    override fun gatherConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) {
        showPrivacyOptionsCalls += 1
    }
}
