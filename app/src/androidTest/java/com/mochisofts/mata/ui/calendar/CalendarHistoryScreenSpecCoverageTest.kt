package com.mochisofts.mata.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataSnackbarHost
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.domain.model.HistoryActionUndoToken
import com.mochisofts.mata.domain.model.HistoryDay
import com.mochisofts.mata.domain.model.HistoryDayState
import com.mochisofts.mata.domain.model.HistoryDaySummary
import com.mochisofts.mata.domain.model.HistoryEntry
import com.mochisofts.mata.domain.model.HistoryTodoSnapshot
import com.mochisofts.mata.domain.model.PeriodHistoryEntry
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.ui.common.TodoDetailFullScreen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarHistoryScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

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
                CalendarHistoryEffectHandler(effects, hostState, restored::add)
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { assertTrue(effects.tryEmit(CalendarHistoryEffect.ActionUndone(token))) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { assertEquals(listOf(token), restored) }
        composeRule.onAllNodesWithText(text(R.string.action_undo)).assertCountEquals(0)

        composeRule.runOnIdle { assertTrue(effects.tryEmit(CalendarHistoryEffect.ActionUndone(token))) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(text(R.string.action_undo)).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(5_100)
        composeRule.onNodeWithText(text(R.string.action_undo)).assertDoesNotExist()
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

    private fun text(resourceId: Int): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId)
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
