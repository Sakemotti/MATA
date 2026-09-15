package com.mochisofts.mata.ui.settings

import android.app.Activity
import android.net.Uri
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.pressBack
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.R
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.core.backup.BackupCounts
import com.mochisofts.mata.core.backup.BackupGateway
import com.mochisofts.mata.core.backup.BackupManifest
import com.mochisofts.mata.core.backup.BackupOperationPhase
import com.mochisofts.mata.core.backup.BackupOperationState
import com.mochisofts.mata.core.backup.BackupOperationStatus
import com.mochisofts.mata.core.backup.BackupOperationType
import com.mochisofts.mata.core.backup.BackupSummary
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
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
    fun st022_backupCreationShowsPersonalDataAndStorageWarningFirst() {
        setScreen()

        composeRule.onNodeWithText(text(R.string.backup_create_title)).performScrollTo().performClick()

        composeRule.onNodeWithText(text(R.string.backup_warning_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.backup_warning_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_continue)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
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
    ): SettingsScreenTestRepository {
        val repository = SettingsScreenTestRepository(showCompleted)
        val viewModel = SettingsViewModel(
            repository = repository,
            notificationScheduler = SettingsScreenTestNotificationScheduler(),
            backupGateway = backupGateway,
            adsConsentRepository = adsConsentRepository,
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                SettingsScreen(
                    onDestination = onDestination,
                    onOpenSourceLicenses = {},
                    viewModel = viewModel,
                )
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
    private val themeState = MutableStateFlow(AppTheme.SYSTEM)
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

private class SettingsScreenTestNotificationScheduler : NotificationScheduler {
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

private class SettingsScreenTestBackupGateway(
    initialState: BackupOperationState = BackupOperationState(),
) : BackupGateway {
    val runtimeState = MutableStateFlow(initialState)
    var confirmCalls = 0
    var cancelCalls = 0

    override val state: StateFlow<BackupOperationState> = runtimeState

    override fun suggestedFileName() = "MATA_backup_test.mata-backup"

    override fun startCreate(uri: Uri) = true

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

    override fun acknowledgeResult() = Unit

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
