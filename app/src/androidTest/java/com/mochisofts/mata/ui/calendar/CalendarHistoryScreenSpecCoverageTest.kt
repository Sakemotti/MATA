package com.mochisofts.mata.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataSnackbarHost
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.navigation.MataAdaptiveLayoutInfo
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.core.designsystem.navigation.MataNavigationType
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.HistoryActionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryDayState
import com.mochisofts.mata.domain.model.HistoryDaySummary
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryMonth
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.PeriodHistoryEntry
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.repository.HistoryRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.ui.common.TodoDetailFullScreen
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarHistoryScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ch001_drawerOpensCalendarAsSelectedFullScreenDestination() {
        val destinations = mutableListOf<MataDestination>()
        setFullScreen(destinations::add)

        composeRule.onNodeWithContentDescription(text(R.string.content_description_open_menu))
            .performClick()
        composeRule.onNode(
            hasText(text(R.string.nav_calendar_history)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.nav_todo_list)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { destinations.isNotEmpty() }
        composeRule.runOnIdle { assertEquals(listOf(MataDestination.TODOS), destinations) }
    }

    @Test
    fun ch003_monthButtonsSwipeAndPickerMoveAcrossAllowedMonthsOnly() {
        val selectedByPicker = mutableListOf<YearMonth>()
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                var state by remember { mutableStateOf(calendarState(YearMonth.of(2026, 8))) }
                var showPicker by remember { mutableStateOf(false) }
                val selectMonth: (YearMonth) -> Unit = { month -> state = calendarState(month) }
                Column(modifierWidth) {
                    MonthControls(
                        state = state,
                        onPrevious = { selectMonth(state.displayedMonth.minusMonths(1)) },
                        onNext = {
                            state.displayedMonth.plusMonths(1)
                                .takeUnless { it.isAfter(YearMonth.from(state.today)) }
                                ?.let(selectMonth)
                        },
                        onSelectMonth = { showPicker = true },
                    )
                    MonthGrid(
                        state = state,
                        onSelectDate = {},
                        onPrevious = { selectMonth(state.displayedMonth.minusMonths(1)) },
                        onNext = { selectMonth(state.displayedMonth.plusMonths(1)) },
                        modifier = Modifier.testTag(MONTH_GRID_TAG),
                    )
                }
                if (showPicker) {
                    MonthPickerDialog(
                        displayedMonth = state.displayedMonth,
                        today = state.today,
                        onSelect = {
                            selectedByPicker += it
                            selectMonth(it)
                            showPicker = false
                        },
                        onDismiss = { showPicker = false },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_previous_month))
            .performClick()
        composeRule.onNodeWithText("2026年7月").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_next_month))
            .performClick()
        composeRule.onNodeWithText("2026年8月").assertIsDisplayed()

        composeRule.onNodeWithTag(MONTH_GRID_TAG).performTouchInput { swipeRight() }
        composeRule.onNodeWithText("2026年7月").assertIsDisplayed()
        composeRule.onNodeWithTag(MONTH_GRID_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("2026年8月").performClick()
        composeRule.onNodeWithText(text(R.string.calendar_history_select_month)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(listOf(YearMonth.of(2026, 8)), selectedByPicker) }

        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_next_month))
            .performClick()
        composeRule.onNodeWithText("2026年9月").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_next_month))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(MONTH_GRID_TAG).performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("2026年9月").assertIsDisplayed()
    }

    @Test
    fun ch008_selectedTodayRemainsLegibleAcrossLightDarkAndDynamicThemes() {
        var theme by mutableStateOf(AppTheme.LIGHT)
        var dynamicColor by mutableStateOf(false)
        var selectedDate by mutableStateOf(TEST_DATE)
        var colorScheme: ColorScheme? = null
        val state = calendarState(YearMonth.of(2026, 9))
        val otherDate = TEST_DATE.minusDays(1)
        val selectedOther = fullDate(otherDate) + text(R.string.calendar_history_selected_suffix)
        val unselectedToday = fullDate(TEST_DATE) + text(R.string.calendar_history_today_suffix)
        val selectedToday = fullDate(TEST_DATE) +
            text(R.string.calendar_history_selected_suffix) +
            text(R.string.calendar_history_today_suffix)
        composeRule.setContent {
            MataTheme(appTheme = theme, useDynamicColor = dynamicColor) {
                val currentColors = MaterialTheme.colorScheme
                SideEffect { colorScheme = currentColors }
                Box(Modifier.width(360.dp).height(360.dp)) {
                    MonthGrid(
                        state.copy(selectedDate = selectedDate),
                        {},
                        {},
                        {},
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        fun verifyTheme(expectedTheme: AppTheme, expectedDynamicColor: Boolean) {
            composeRule.runOnIdle {
                theme = expectedTheme
                dynamicColor = expectedDynamicColor
                selectedDate = otherDate
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(selectedOther)
                .assertExists()
                .assertIsSelected()
                .assertIsEnabled()
            composeRule.onNodeWithContentDescription(unselectedToday)
                .assertExists()
                .assertIsEnabled()
            composeRule.runOnIdle { selectedDate = TEST_DATE }
            composeRule.onNodeWithContentDescription(selectedToday)
                .assertExists()
                .assertIsSelected()
                .assertIsEnabled()
            composeRule.runOnIdle {
                val colors = requireNotNull(colorScheme)
                assertContrastAtLeast(
                    foreground = calendarDayTextColor(selected = true, colors),
                    background = colors.primaryContainer,
                    minimum = 4.5f,
                )
                assertTrue(
                    "today border must differ from selected background",
                    colors.primary != colors.primaryContainer,
                )
            }
        }

        verifyTheme(AppTheme.LIGHT, expectedDynamicColor = false)
        verifyTheme(AppTheme.DARK, expectedDynamicColor = false)
        verifyTheme(AppTheme.LIGHT, expectedDynamicColor = true)
    }

    @Test
    fun ch032_rotationRestoresMonthDayListDetailAndUndoMessageWhileFreshOpenUsesToday() {
        val selectedDate = LocalDate.of(2026, 8, 6)
        val entries = listOf(historyEntry("回転復元履歴 1", TodoState.COMPLETED)) +
            (2..24).map { index -> historyEntry("回転復元履歴 $index", TodoState.PENDING) }
        val token = undoToken("rotation-undo", TodoState.COMPLETED)
        val repository = CalendarScreenTestHistoryRepository(
            day = historyDay(entries = entries),
            undoResult = Result.success(token),
        )
        val savedStateHandle = SavedStateHandle()
        val viewModel = CalendarHistoryViewModel(
            savedStateHandle = savedStateHandle,
            historyRepository = repository,
            settingsRepository = CalendarScreenTestSettingsRepository(),
            clock = TEST_CLOCK,
        )
        viewModel.selectDate(selectedDate)
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MataTheme(useDynamicColor = false) {
                CalendarHistoryScreen(onDestination = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("2026年8月").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("回転復元履歴 24").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("回転復元履歴 24").performClick()
        composeRule.onNodeWithText("TODO詳細").assertIsDisplayed()
        viewModel.undoAction(requireNotNull(entries.first().id))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text(R.string.action_undo)).fetchSemanticsNodes().isNotEmpty()
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("TODO詳細").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").performClick()
        composeRule.onNodeWithText("2026年8月").assertIsDisplayed()
        composeRule.onNodeWithText("回転復元履歴 24").assertIsDisplayed()
        composeRule.onNodeWithText("回転復元履歴 1").assertIsNotDisplayed()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertIsDisplayed()

        val freshViewModel = CalendarHistoryViewModel(
            savedStateHandle = SavedStateHandle(),
            historyRepository = CalendarScreenTestHistoryRepository(),
            settingsRepository = CalendarScreenTestSettingsRepository(),
            clock = TEST_CLOCK,
        )
        assertEquals(TEST_DATE, freshViewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.from(TEST_DATE), freshViewModel.uiState.value.displayedMonth)
    }

    @Test
    fun ch035_maximumFontAndCompactDisplayKeepAllCalendarHistoryActionsReachable() {
        val completed = historyEntry("最大表示の完了TODO", TodoState.COMPLETED)
        val skipped = historyEntry("最大表示のスキップTODO", TodoState.SKIPPED)
        val pending = historyEntry("最大表示の未完了TODO", TodoState.PENDING)
        val period = periodEntry("最大表示の期間結果TODO")
        val state = calendarState(YearMonth.from(TEST_DATE)).copy(
            day = historyDay(
                summary = HistoryDaySummary(
                    date = TEST_DATE,
                    completedCount = 1,
                    plannedCount = 3,
                    state = HistoryDayState.UNACHIEVED,
                    hasAchievedPeriod = false,
                    hasUnachievedPeriod = true,
                ),
                entries = listOf(pending, skipped, completed),
                periodResults = listOf(period),
            ),
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = 2f),
                ) {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        CalendarHistoryBody(
                            state = state,
                            layoutInfo = compactCalendarLayout.copy(
                                windowWidthDp = 320f,
                                windowHeightDp = 640f,
                                availableContentWidthDp = 288f,
                            ),
                            historyListState = rememberLazyListState(),
                            onPreviousMonth = {},
                            onNextMonth = {},
                            onSelectMonth = {},
                            onSelectDate = {},
                            onRetry = {},
                            onEntryClick = {},
                            onPeriodClick = {},
                            onUndoAction = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_previous_month))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_next_month))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            fullDate(TEST_DATE) + text(R.string.calendar_history_selected_suffix) +
                text(R.string.calendar_history_today_suffix),
        ).assertExists()
        listOf(
            "2026年9月6日（日）",
            "完了 1 / 3",
            "未達成",
            pending.snapshot.title,
            skipped.snapshot.title,
            completed.snapshot.title,
            period.snapshot.title,
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNode(toggleState(ToggleableState.On), useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_undo_skip))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun chd03_outsideMonthIsMutedAndFutureDateIsSemanticallyDisabled() {
        val selectedDates = mutableListOf<LocalDate>()
        val outsideDate = LocalDate.of(2026, 8, 31)
        val futureDate = LocalDate.of(2026, 9, 7)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                MonthGrid(
                    state = calendarState(YearMonth.of(2026, 9)),
                    onSelectDate = selectedDates::add,
                    onPrevious = {},
                    onNext = {},
                    modifier = modifierWidth,
                )
            }
        }

        composeRule.onNodeWithContentDescription(fullDate(outsideDate))
            .assertExists()
            .assertIsEnabled()
            .performClick()
        composeRule.onNodeWithContentDescription(fullDate(futureDate))
            .assertExists()
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(listOf(outsideDate), selectedDates)
            assertEquals(0.48f, calendarDayAlpha(inMonth = false, enabled = true))
            assertEquals(0.48f, calendarDayAlpha(inMonth = true, enabled = false))
            assertEquals(1f, calendarDayAlpha(inMonth = true, enabled = true))
        }
    }

    @Test
    fun ch033_calendarScreenHasNoSearchFilterShareExportFabOrBannerControls() {
        setFullScreen()

        listOf("検索", "絞り込み", "共有", "出力", "TODOを追加", "広告").forEach { prohibited ->
            composeRule.onAllNodesWithText(prohibited, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun chd01_onlySummaryAndHistoryScrollWhileMonthCalendarStaysFixed() {
        val entries = (1..24).map { index ->
            historyEntry("長い履歴 $index", TodoState.PENDING)
        }
        val state = calendarState(YearMonth.of(2026, 9)).copy(
            day = historyDay(entries = entries),
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                val listState = rememberLazyListState()
                Box(Modifier.width(360.dp).height(760.dp)) {
                    CalendarHistoryBody(
                        state = state,
                        layoutInfo = compactCalendarLayout,
                        historyListState = listState,
                        onPreviousMonth = {},
                        onNextMonth = {},
                        onSelectMonth = {},
                        onSelectDate = {},
                        onRetry = {},
                        onEntryClick = {},
                        onPeriodClick = {},
                        onUndoAction = {},
                    )
                }
            }
        }

        val monthNode = composeRule.onNodeWithText("2026年9月")
        val initialTop = monthNode.fetchSemanticsNode().boundsInRoot.top
        repeat(12) {
            composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        }

        monthNode.assertIsDisplayed()
        composeRule.onNodeWithText("長い履歴 24").assertIsDisplayed()
        assertEquals(initialTop, monthNode.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun chd02_currentMonthRejectsNextButtonAndFutureSwipe() {
        var nextCalls = 0
        val state = calendarState(YearMonth.of(2026, 9))
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(modifierWidth) {
                    MonthControls(state, {}, { nextCalls += 1 }, {})
                    MonthGrid(
                        state = state,
                        onSelectDate = {},
                        onPrevious = {},
                        onNext = { nextCalls += 1 },
                        modifier = Modifier.testTag(MONTH_GRID_TAG),
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription(text(R.string.calendar_history_next_month))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(MONTH_GRID_TAG).performTouchInput { swipeLeft() }

        composeRule.runOnIdle { assertEquals(0, nextCalls) }
        composeRule.onNodeWithText("2026年9月").assertIsDisplayed()
    }

    @Test
    fun chd07_longExecutionAndPeriodDetailsShareScrollableDefinitionAndCloseWithBack() {
        val longSnapshot = historySnapshot("全項目を持つ長文TODO").copy(
            description = "長い説明です。".repeat(80),
            endDate = TEST_DATE.plusMonths(6),
            dueDate = TEST_DATE.plusDays(1),
            carryOverEnabled = true,
            scheduledLogicalDate = TEST_DATE.minusDays(1),
            resolvedLogicalDate = TEST_DATE,
            notifications = listOf(
                TodoNotification("before", NotificationRelation.BEFORE, 1, NotificationUnit.HOUR),
                TodoNotification("at", NotificationRelation.AT, 0, NotificationUnit.MINUTE),
            ),
        )
        val execution = HistoryDialogItem.Execution(
            historyEntry("全項目を持つ長文TODO", TodoState.COMPLETED).copy(snapshot = longSnapshot),
        )
        val period = HistoryDialogItem.Period(
            periodEntry("全項目を持つ長文TODO").copy(snapshot = longSnapshot),
        )
        var item by mutableStateOf<HistoryDialogItem?>(execution)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                item?.let { current ->
                    TodoDetailFullScreen(current.detailModalData()) { item = null }
                }
            }
        }

        composeRule.onNodeWithText("TODO詳細").assertIsDisplayed()
        composeRule.onNodeWithText("スケジュール").assertExists()
        composeRule.onNodeWithText("通知").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("履歴").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("対象論理日").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()

        composeRule.runOnIdle { item = period }
        composeRule.onNodeWithText("対象期間").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("期間結果").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithText("TODO詳細").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(null, item) }
    }

    @Test
    fun ch015_summaryShowsDateStateCountAndProgressWhilePeriodOnlyDayIsNotEmpty() {
        val period = periodEntry("期間結果だけのTODO")
        val day = historyDay(
            summary = HistoryDaySummary(
                date = TEST_DATE,
                completedCount = 2,
                plannedCount = 4,
                state = HistoryDayState.UNACHIEVED,
                hasAchievedPeriod = false,
                hasUnachievedPeriod = true,
            ),
            periodResults = listOf(period),
        )

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(modifierWidth) {
                    DaySummaryHeader(day)
                    DayHistoryCard(day, null, {}, {}, {})
                }
            }
        }

        composeRule.onNodeWithText("2026年9月6日（日）").assertIsDisplayed()
        composeRule.onNodeWithText("未達成").assertIsDisplayed()
        composeRule.onNodeWithText("完了 2 / 4").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.5f, 0f..1f),
            ),
        ).assertExists()
        composeRule.onNodeWithText("期間結果").assertExists()
        composeRule.onNodeWithText(period.snapshot.title).assertExists()
        composeRule.onNodeWithText("この日の履歴はありません").assertDoesNotExist()
    }

    @Test
    fun ch016_nonEmptySectionsAppearInRequiredOrderAndEmptySectionsAreOmitted() {
        val day = historyDay(
            entries = listOf(
                historyEntry("未完了TODO", TodoState.PENDING),
                historyEntry("スキップTODO", TodoState.SKIPPED),
                historyEntry("完了TODO", TodoState.COMPLETED),
            ),
            periodResults = listOf(periodEntry("期間TODO")),
        )
        val completedOnly = historyDay(
            entries = listOf(historyEntry("完了だけのTODO", TodoState.COMPLETED)),
        )
        val displayedDay = mutableStateOf(day)

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                DayHistoryCard(displayedDay.value, null, {}, {}, {})
            }
        }

        val sectionTops = listOf("未完了", "スキップ", "完了", "期間結果")
            .map { composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(sectionTops.zipWithNext().all { (first, second) -> first < second })

        composeRule.runOnIdle { displayedDay.value = completedOnly }
        composeRule.onNodeWithText("完了").assertExists()
        composeRule.onNodeWithText("未完了").assertDoesNotExist()
        composeRule.onNodeWithText("スキップ").assertDoesNotExist()
        composeRule.onNodeWithText("期間結果").assertDoesNotExist()
    }

    @Test
    fun ch021_executionAndPeriodRowsOpenReadOnlyFullScreenDetailAndClose() {
        val execution = historyEntry("日単位履歴のTODO", TodoState.COMPLETED)
        val period = periodEntry("期間結果のTODO")

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                var dialogItem by remember { mutableStateOf<HistoryDialogItem?>(null) }
                Column(modifierWidth) {
                    HistoryEntryRow(execution, null, { dialogItem = HistoryDialogItem.Execution(it) }, {})
                    PeriodResultRow(period) { dialogItem = HistoryDialogItem.Period(it) }
                }
                dialogItem?.let { item ->
                    TodoDetailFullScreen(
                        data = item.detailModalData(),
                        onDismiss = { dialogItem = null },
                    )
                }
            }
        }

        composeRule.onNodeWithText(execution.snapshot.title).performClick()
        assertReadOnlyDetail("対象論理日")
        composeRule.onNodeWithContentDescription("閉じる").performClick()
        composeRule.onNodeWithText("TODO詳細").assertDoesNotExist()

        composeRule.onNodeWithText(period.snapshot.title).performClick()
        assertReadOnlyDetail("対象期間")
        composeRule.onNodeWithContentDescription("閉じる").performClick()
        composeRule.onNodeWithText("TODO詳細").assertDoesNotExist()
    }

    @Test
    fun ch026_emptyDayShowsHistoryEmptyMessage() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                DayHistoryCard(historyDay(), null, {}, {}, {})
            }
        }

        composeRule.onNodeWithText("この日の履歴はありません").assertIsDisplayed()
        composeRule.onNodeWithText("未完了").assertDoesNotExist()
        composeRule.onNodeWithText("期間結果").assertDoesNotExist()
    }

    @Test
    fun ch024_undoSnackbarRestoresWithinFiveSecondsAndExpiresAfterward() {
        val effects = MutableSharedFlow<CalendarHistoryEffect>(extraBufferCapacity = 2)
        val token = undoToken("undo-completed", TodoState.COMPLETED)
        val restored = mutableListOf<HistoryActionUndoToken>()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                val hostState = remember { SnackbarHostState() }
                MataSnackbarHost(hostState)
                CalendarHistoryEffectHandler(
                    effects = effects,
                    snackbarHostState = hostState,
                    onRestoreAction = restored::add,
                    undoWindowMillis = TEST_UNDO_WINDOW_MILLIS,
                )
            }
        }
        assertEquals(5_000L, CALENDAR_HISTORY_UNDO_WINDOW_MILLIS)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { assertTrue(effects.tryEmit(CalendarHistoryEffect.ActionUndone(token))) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(listOf(token), restored) }
        composeRule.onAllNodesWithText(text(R.string.action_undo)).assertCountEquals(0)

        composeRule.runOnIdle { assertTrue(effects.tryEmit(CalendarHistoryEffect.ActionUndone(token))) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText(text(R.string.action_undo)).fetchSemanticsNodes().isEmpty()
        }
        composeRule.runOnIdle { assertEquals(listOf(token), restored) }
    }

    @Test
    fun ch025_onlyEligibleHistoryRowsExposeUndoAndEveryDetailRemainsReadOnly() {
        val pending = historyEntry("未完了TODO", TodoState.PENDING)
        val completed = historyEntry("操作可能な完了", TodoState.COMPLETED)
        val skipped = historyEntry("操作可能なスキップ", TodoState.SKIPPED)
        val fixedPast = historyEntry("確定済みの完了", TodoState.COMPLETED, canUndoAction = false)
        val displayedDay = mutableStateOf(
            historyDay(
                entries = listOf(pending, skipped, completed, fixedPast),
                periodResults = listOf(periodEntry("確定済み期間")),
            ),
        )
        val undoneIds = mutableListOf<String>()
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                var dialogItem by remember { mutableStateOf<HistoryDialogItem?>(null) }
                Column(modifierWidth) {
                    DayHistoryCard(
                        displayedDay.value,
                        null,
                        { dialogItem = HistoryDialogItem.Execution(it) },
                        { dialogItem = HistoryDialogItem.Period(it) },
                        undoneIds::add,
                    )
                }
                dialogItem?.let { item ->
                    TodoDetailFullScreen(item.detailModalData()) { dialogItem = null }
                }
            }
        }

        composeRule.onAllNodes(hasToggleableState(), useUnmergedTree = true).assertCountEquals(1)
        composeRule.onNode(toggleState(ToggleableState.On), useUnmergedTree = true).performClick()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_undo_skip))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(requireNotNull(completed.id), requireNotNull(skipped.id)), undoneIds)
            displayedDay.value = historyDay(entries = listOf(pending))
        }

        composeRule.onNodeWithText(pending.snapshot.title).performClick()
        assertReadOnlyDetail("対象論理日")
    }

    private fun assertReadOnlyDetail(expectedHistoryField: String) {
        composeRule.onNodeWithText("TODO詳細").assertIsDisplayed()
        composeRule.onNodeWithText("スケジュール").assertIsDisplayed()
        composeRule.onNodeWithText(expectedHistoryField).assertExists()
        composeRule.onNodeWithContentDescription("閉じる").assertIsDisplayed()
        composeRule.onNodeWithText("編集").assertDoesNotExist()
        composeRule.onNodeWithText("元に戻す").assertDoesNotExist()
        composeRule.onNodeWithText("スキップ").assertDoesNotExist()
        composeRule.onNodeWithText("アーカイブ").assertDoesNotExist()
        composeRule.onNodeWithText("削除").assertDoesNotExist()
    }

    private fun setFullScreen(onDestination: (MataDestination) -> Unit = {}) {
        val viewModel = CalendarHistoryViewModel(
            savedStateHandle = SavedStateHandle(),
            historyRepository = CalendarScreenTestHistoryRepository(),
            settingsRepository = CalendarScreenTestSettingsRepository(),
            clock = TEST_CLOCK,
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                CalendarHistoryScreen(
                    onDestination = onDestination,
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("2026年9月").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(resourceId: Int): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId)

    private fun fullDate(date: LocalDate): String = date.format(
        DateTimeFormatter.ofPattern(text(R.string.date_pattern_full), Locale.JAPANESE),
    )
}

private val modifierWidth = androidx.compose.ui.Modifier.width(360.dp)

private fun historyDay(
    summary: HistoryDaySummary = HistoryDaySummary(
        date = TEST_DATE,
        completedCount = 0,
        plannedCount = 0,
        state = null,
        hasAchievedPeriod = false,
        hasUnachievedPeriod = false,
    ),
    entries: List<HistoryEntry> = emptyList(),
    periodResults: List<PeriodHistoryEntry> = emptyList(),
): HistoryDay = HistoryDay(TEST_DATE, summary, entries, periodResults)

private fun historyEntry(
    title: String,
    state: TodoState,
    canUndoAction: Boolean = state == TodoState.COMPLETED || state == TodoState.SKIPPED,
): HistoryEntry = HistoryEntry(
    id = "$title-id",
    todoId = "$title-todo",
    logicalDate = TEST_DATE,
    state = state,
    actedAt = if (state == TodoState.PENDING) null else 1L,
    finalizedAt = null,
    snapshot = historySnapshot(title),
    canUndoAction = canUndoAction,
)

private fun calendarState(month: YearMonth): CalendarHistoryUiState {
    val selectedDate = month.atDay(6)
    return CalendarHistoryUiState(
        displayedMonth = month,
        selectedDate = selectedDate,
        today = LocalDate.of(2026, 9, 6),
        gridDates = calendarGridDates(month, DayOfWeek.MONDAY),
        isMonthLoading = false,
        isDayLoading = false,
    )
}

private fun undoToken(id: String, state: TodoState) = HistoryActionUndoToken(
    id = id,
    operationId = "$id-operation",
    todoId = "$id-todo",
    logicalDate = TEST_DATE,
    state = state,
    actedAt = 1,
    finalizedAt = 1,
    definitionRevision = 1,
    snapshotVersion = 1,
    snapshotJson = "{}",
)

private fun hasToggleableState() = SemanticsMatcher("has toggleable state") {
    it.config.contains(SemanticsProperties.ToggleableState)
}

private fun toggleState(state: ToggleableState) = SemanticsMatcher.expectValue(
    SemanticsProperties.ToggleableState,
    state,
)

private fun periodEntry(title: String): PeriodHistoryEntry = PeriodHistoryEntry(
    id = "$title-period",
    todoId = "$title-todo",
    periodType = RecurrenceType.WEEKLY_COUNT,
    periodStart = TEST_DATE.minusDays(6),
    periodEnd = TEST_DATE,
    requiredCount = 2,
    completedCount = 1,
    achieved = false,
    displayDate = TEST_DATE,
    finalizedAt = 1L,
    snapshot = historySnapshot(title),
)

private fun historySnapshot(title: String): HistoryTodoSnapshot = HistoryTodoSnapshot(
    todoId = "$title-todo",
    definitionRevision = 1,
    title = title,
    description = "履歴に保存された説明",
    startDate = TEST_DATE,
    endDate = null,
    recurrenceRule = RecurrenceRule.daily(),
    dueMinutes = 9 * 60,
    notifications = emptyList(),
    categoryId = "category",
    categoryName = "日常",
    categoryColorIndex = 1,
    categoryIconName = "Home",
    categorySortOrder = 0,
    endHour = 0,
    weekStart = DayOfWeek.MONDAY,
    createdAt = 0L,
)

private val TEST_DATE: LocalDate = LocalDate.of(2026, 9, 6)
private const val MONTH_GRID_TAG = "calendar-history-month-grid"
private const val TEST_UNDO_WINDOW_MILLIS = 100L

private val TEST_CLOCK: Clock = Clock.fixed(
    TEST_DATE.atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant(),
    ZoneId.of("Asia/Tokyo"),
)

private val compactCalendarLayout = MataAdaptiveLayoutInfo(
    navigationType = MataNavigationType.MODAL_DRAWER,
    windowWidthDp = 360f,
    windowHeightDp = 760f,
    outerMarginDp = 16f,
    availableContentWidthDp = 328f,
    useTwoPane = false,
)

private class CalendarScreenTestHistoryRepository(
    private val day: HistoryDay = historyDay(),
    private val undoResult: Result<HistoryActionUndoToken> =
        Result.failure(UnsupportedOperationException()),
) : HistoryRepository {
    private val month = MutableStateFlow(HistoryMonth(emptyMap()))

    override fun observeMonth(startDate: LocalDate, endDate: LocalDate): Flow<HistoryMonth> = month

    override fun observeDay(date: LocalDate): Flow<HistoryDay> = MutableStateFlow(day.forDate(date))

    override suspend fun undoAction(executionId: String): Result<HistoryActionUndoToken> =
        undoResult

    override suspend fun restoreAction(token: HistoryActionUndoToken): Result<Unit> =
        Result.failure(UnsupportedOperationException())
}

private fun HistoryDay.forDate(date: LocalDate): HistoryDay = copy(
    date = date,
    summary = summary.copy(date = date),
    entries = entries.map { entry ->
        entry.copy(
            logicalDate = date,
            snapshot = entry.snapshot.copy(
                scheduledLogicalDate = date,
                resolvedLogicalDate = date,
            ),
        )
    },
)

private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    val ratio = (lighter + 0.05f) / (darker + 0.05f)
    assertTrue("contrast ratio $ratio was below $minimum", ratio >= minimum)
}

private class CalendarScreenTestSettingsRepository : SettingsRepository {
    override val showCompleted = MutableStateFlow(false)
    override val todoListMode = MutableStateFlow("DATE")
    override val dayEndHour = MutableStateFlow(0)
    override val weekStart = MutableStateFlow(DayOfWeek.MONDAY)
    override val theme = MutableStateFlow(AppTheme.SYSTEM)
    override val notificationPermissionRequested = MutableStateFlow(false)
    override val archiveSortOrder = MutableStateFlow(ArchiveSortOrder.NEWEST)
    override suspend fun setShowCompleted(value: Boolean) { showCompleted.value = value }
    override suspend fun setTodoListMode(value: String) { todoListMode.value = value }
    override suspend fun setDayEndHour(value: Int) { dayEndHour.value = value }
    override suspend fun setWeekStart(value: DayOfWeek) { weekStart.value = value }
    override suspend fun setTheme(value: AppTheme) { theme.value = value }
    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequested.value = value
    }
    override suspend fun setArchiveSortOrder(value: ArchiveSortOrder) { archiveSortOrder.value = value }
}
