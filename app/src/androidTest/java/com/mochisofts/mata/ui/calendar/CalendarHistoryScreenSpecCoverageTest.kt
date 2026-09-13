package com.mochisofts.mata.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mochisofts.mata.core.designsystem.MataTheme
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CalendarHistoryScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                DayHistoryCard(day, null, {}, {}, {})
            }
        }

        val sectionTops = listOf("未完了", "スキップ", "完了", "期間結果")
            .map { composeRule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(sectionTops.zipWithNext().all { (first, second) -> first < second })

        val completedOnly = historyDay(
            entries = listOf(historyEntry("完了だけのTODO", TodoState.COMPLETED)),
        )
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                DayHistoryCard(completedOnly, null, {}, {}, {})
            }
        }
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

private fun historyEntry(title: String, state: TodoState): HistoryEntry = HistoryEntry(
    id = "$title-id",
    todoId = "$title-todo",
    logicalDate = TEST_DATE,
    state = state,
    actedAt = if (state == TodoState.PENDING) null else 1L,
    finalizedAt = null,
    snapshot = historySnapshot(title),
    canUndoAction = state == TodoState.COMPLETED || state == TodoState.SKIPPED,
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
