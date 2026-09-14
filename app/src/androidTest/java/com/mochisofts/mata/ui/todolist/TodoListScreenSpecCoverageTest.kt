package com.mochisofts.mata.ui.todolist

import android.app.Activity
import android.text.format.DateFormat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.ads.AdsConsentRepository
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.designsystem.MataStatusType
import com.mochisofts.mata.core.designsystem.MataStatusTypeSemanticsKey
import com.mochisofts.mata.core.designsystem.navigation.MataDestination
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.ArchiveSortOrder
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrencePeriod
import com.mochisofts.mata.domain.model.RecurrenceProgress
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodoListScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tl001_normalLaunchAndCurrentDrawerItemKeepSingleDateTodoList() {
        val destinations = mutableListOf<MataDestination>()

        setScreen(
            repository = TestTodoRepository(emptyList()),
            onDestination = destinations::add,
        )
        waitForText(displayedDate(TODAY, isToday = true))
        composeRule.onNodeWithText(text(R.string.todo_list_title)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(text(R.string.content_description_open_menu))
            .performClick()
        val currentDestination = composeRule.onNode(
            hasText(text(R.string.nav_todo_list)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        )
        currentDestination.assertIsDisplayed().performClick()
        currentDestination.assertIsNotDisplayed()

        composeRule.onNodeWithText(text(R.string.todo_list_title)).assertIsDisplayed()
        composeRule.onNodeWithText(displayedDate(TODAY, isToday = true)).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(destinations.isEmpty()) }
    }

    @Test
    fun tl004_buttonsSwipesDatePickerAndTodayActionMoveToTheExpectedDate() {
        val repository = TestTodoRepository(
            listOf(occurrence("date-navigation", "日付移動TODO", TODAY, TodoState.PENDING)),
        )
        setScreen(repository)
        waitForText(displayedDate(TODAY, isToday = true))

        composeRule.onNodeWithContentDescription(text(R.string.content_description_next_day))
            .performClick()
        waitForText(displayedDate(TODAY.plusDays(1), isToday = false))
        composeRule.onNodeWithContentDescription(text(R.string.content_description_previous_day))
            .performClick()
        waitForText(displayedDate(TODAY, isToday = true))

        composeRule.onNodeWithTag(TODO_DATE_SWIPE_TAG).performTouchInput { swipeLeft() }
        waitForText(displayedDate(TODAY.plusDays(1), isToday = false))
        composeRule.onNodeWithTag(TODO_DATE_SWIPE_TAG).performTouchInput { swipeRight() }
        waitForText(displayedDate(TODAY, isToday = true))

        composeRule.onNodeWithText(displayedDate(TODAY, isToday = true)).performClick()
        val calendarTarget = if (TODAY.dayOfMonth == 1) {
            TODAY.plusDays(1)
        } else {
            TODAY.withDayOfMonth(1)
        }
        composeRule.onNodeWithText(datePickerDescription(calendarTarget)).performClick()
        composeRule.onNodeWithText(text(R.string.action_confirm)).performClick()
        waitForText(displayedDate(calendarTarget, isToday = false))

        composeRule.onNodeWithText(text(R.string.action_return_to_today)).performClick()
        waitForText(displayedDate(TODAY, isToday = true))
    }

    @Test
    fun tl007_eachVisibleCategoryHeaderCarriesItsNameColorAndIcon() {
        val home = Category("home", "日常", 2, "Home", 0)
        val game = Category("game", "ゲーム", 4, "SportsEsports", 1)
        setScreen(
            TestTodoRepository(
                listOf(
                    occurrence("home-row", "日常TODO", TODAY, TodoState.PENDING, category = home),
                    occurrence("game-row", "ゲームTODO", TODAY, TodoState.PENDING, category = game),
                ),
            ),
        )
        waitForText("日常TODO")

        listOf(home, game).forEach { category ->
            composeRule.onNode(
                hasTestTag(todoCategoryHeaderTag(category.id)) and
                    SemanticsMatcher.expectValue(
                        TodoCategoryColorIndexKey,
                        category.colorIndex,
                    ) and
                    SemanticsMatcher.expectValue(
                        TodoCategoryIconNameKey,
                        category.iconName,
                    ),
            ).assertIsDisplayed()
            composeRule.onNodeWithText(category.name).assertIsDisplayed()
        }
    }

    @Test
    fun tl010_longTextAndEveryStateKeepCompleteRowInformationWithoutRepeatingCategory() {
        val date = TODAY.minusDays(1)
        val category = Category("routine", "ルーチン", 1, "Repeat", 0)
        val longTitle = "長いタイトル".repeat(20)
        val longDescription = "長い説明文".repeat(50)
        val pending = occurrence("long", longTitle, date, TodoState.PENDING, category = category)
            .copy(
                todo = occurrence(
                    "long",
                    longTitle,
                    date,
                    TodoState.PENDING,
                    category = category,
                ).todo.copy(description = longDescription),
                progress = RecurrenceProgress(
                    period = RecurrencePeriod(date.minusDays(6), date, 3),
                    completedCount = 1,
                ),
            )
        val completed = occurrence("complete-info", "完了情報", date, TodoState.COMPLETED, category = category)
        val skipped = occurrence("skip-info", "スキップ情報", date, TodoState.SKIPPED, category = category)
        val missed = occurrence("missed-info", "未完了情報", date, TodoState.MISSED, category = category)

        setScreen(TestTodoRepository(listOf(pending, completed, skipped, missed)), selectedDate = date)
        waitForText(longTitle)

        composeRule.onAllNodesWithText(category.name).assertCountEquals(1)
        composeRule.onNodeWithText(longTitle).assertIsDisplayed()
        composeRule.onNodeWithText(longDescription).assertIsDisplayed()
        composeRule.onNodeWithText("1 / 3回").assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.todo_due_time_format, 8, 0))
            .assertCountEquals(4)
        composeRule.onNodeWithText(text(R.string.label_completed)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.label_skipped)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.label_missed)).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, TODO_ROW_MAX_TEXT_LINES) }
    }

    @Test
    fun tl012_overdueDueTimeAndLabelUseErrorToneWithoutChangingRowBackground() {
        val category = Category("deadline", "期限確認", 3, "Schedule", 0)
        val normal = occurrence("normal", "通常期限", TODAY, TodoState.PENDING, category = category)
        val overdue = occurrence("overdue", "超過期限", TODAY, TodoState.PENDING, category = category)
            .copy(isOverdue = true)
        setScreen(TestTodoRepository(listOf(normal, overdue)))
        waitForText(overdue.todo.title)

        composeRule.onNode(
            hasText(text(R.string.todo_due_time_format, 8, 0)) and
                SemanticsMatcher.expectValue(TodoDueToneKey, "ERROR"),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNode(
            hasText(text(R.string.todo_overdue)) and
                SemanticsMatcher.expectValue(
                    MataStatusTypeSemanticsKey,
                    MataStatusType.ERROR,
                ),
        ).assertIsDisplayed()
        val normalColor = composeRule.onNodeWithTag(todoOccurrenceRowTag(normal.todo.id))
            .fetchSemanticsNode().config[TodoRowContainerColorKey]
        val overdueColor = composeRule.onNodeWithTag(todoOccurrenceRowTag(overdue.todo.id))
            .fetchSemanticsNode().config[TodoRowContainerColorKey]
        assertEquals(normalColor, overdueColor)
    }

    @Test
    fun logicalDateDifferentFromCalendarTodayIsShownForEveryCategoryRow() {
        val logicalDate = TODAY.minusDays(1)
        val home = Category("home", "日常", 2, "Home", 0)
        val game = Category("game", "ゲーム", 4, "SportsEsports", 1)
        val repository = TestTodoRepository(
            listOf(
                occurrence("home-todo", "日常TODO", logicalDate, TodoState.PENDING, category = home),
                occurrence("game-todo", "ゲームTODO", logicalDate, TodoState.PENDING, category = game),
            ),
        )

        setScreen(repository)
        waitForText("日常TODO")

        composeRule.onNodeWithText("日常").assertIsDisplayed()
        composeRule.onNodeWithText("ゲーム").assertIsDisplayed()
        composeRule.onAllNodesWithText(displayedDate(logicalDate, isToday = false))
            .assertCountEquals(2)
    }

    @Test
    fun tl005_pastDateShowsEveryStateWithoutMutationControlsAndOpensReadOnlyDetail() {
        val date = TODAY.minusDays(1)
        val pending = occurrence("past-pending", "過去の未完了", date, TodoState.PENDING)
        val completed = occurrence("past-completed", "過去の完了", date, TodoState.COMPLETED)
        val skipped = occurrence("past-skipped", "過去のスキップ", date, TodoState.SKIPPED)
        val repository = TestTodoRepository(listOf(pending, completed, skipped))

        setScreen(repository = repository, selectedDate = date)
        waitForText(pending.todo.title)

        listOf(pending, completed, skipped).forEach { item ->
            composeRule.onNodeWithText(item.todo.title).assertIsDisplayed()
        }
        composeRule.onAllNodesWithContentDescription(text(R.string.content_description_todo_actions))
            .assertCountEquals(0)
        composeRule.onNode(
            toggleState(ToggleableState.On),
            useUnmergedTree = true,
        ).assertIsNotEnabled()
        composeRule.onNode(
            toggleState(ToggleableState.Off),
            useUnmergedTree = true,
        ).assertIsNotEnabled()

        composeRule.onNodeWithText(skipped.todo.title).performClick()
        composeRule.onNodeWithText(text(R.string.action_close)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, repository.mutationCount) }
    }

    @Test
    fun tl018_pastDetailShowsSnapshotAndOffersNoOccurrenceMutation() {
        val date = TODAY.minusDays(1)
        val category = Category("health", "健康", 2, "FitnessCenter", 0)
        val historical = occurrence(
            id = "past-detail",
            title = "過去時点のストレッチ",
            date = date,
            state = TodoState.COMPLETED,
            category = category,
        ).copy(
            todo = occurrence(
                id = "past-detail",
                title = "過去時点のストレッチ",
                date = date,
                state = TodoState.COMPLETED,
                category = category,
            ).todo.copy(
                description = "履歴に保存された説明",
                notifications = listOf(
                    TodoNotification(
                        id = "before",
                        relation = com.mochisofts.mata.domain.model.NotificationRelation.BEFORE,
                        amount = 1,
                        unit = com.mochisofts.mata.domain.model.NotificationUnit.HOUR,
                    ),
                ),
            ),
        )
        val repository = TestTodoRepository(listOf(historical))

        setScreen(repository = repository, selectedDate = date)
        waitForText(historical.todo.title)
        composeRule.onNodeWithText(historical.todo.title).performClick()

        composeRule.onAllNodesWithText(historical.todo.title).assertCountEquals(2)
        composeRule.onAllNodesWithText(historical.todo.description)
            .assertCountEquals(2)[1]
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(category.name)
            .assertCountEquals(2)[1]
            .performScrollTo()
            .assertIsDisplayed()
        listOf(
            text(R.string.calendar_history_logical_date),
            text(R.string.calendar_history_due),
            text(R.string.calendar_history_recurrence),
            text(R.string.calendar_history_notifications),
            text(R.string.calendar_history_state),
        ).forEach { value ->
            composeRule.onNodeWithText(value).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText(text(R.string.action_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_close)).assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.action_skip)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.action_archive)).assertCountEquals(0)
        composeRule.onAllNodesWithText(text(R.string.action_undo)).assertCountEquals(0)
        composeRule.runOnIdle { assertEquals(0, repository.mutationCount) }
    }

    @Test
    fun tl006_futureDateAllowsOpeningEditorButNotCompletionOrSkip() {
        val date = TODAY.plusDays(1)
        val future = occurrence("future", "未来のTODO", date, TodoState.PENDING)
        val repository = TestTodoRepository(listOf(future))
        val openedIds = mutableListOf<String>()

        setScreen(
            repository = repository,
            selectedDate = date,
            onEditTodo = openedIds::add,
        )
        waitForText(future.todo.title)

        composeRule.onAllNodes(hasToggleableState()).assertCountEquals(0)
        composeRule.onNodeWithText(future.todo.title).performClick()
        composeRule.runOnIdle { assertEquals(listOf(future.todo.id), openedIds) }

        composeRule.onNodeWithContentDescription(text(R.string.content_description_todo_actions))
            .performClick()
        composeRule.onAllNodesWithText(text(R.string.action_skip)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.action_archive)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, repository.mutationCount) }
    }

    @Test
    fun tl013_completionImmediatelyUpdatesListAndShowsNoUndoAction() {
        val pending = occurrence("complete", "完了するTODO", TODAY, TodoState.PENDING)
        val repository = TestTodoRepository(listOf(pending))

        setScreen(repository)
        waitForText(pending.todo.title)
        composeRule.onNode(toggleState(ToggleableState.Off)).performClick()

        waitForText(text(R.string.message_todo_completed))
        composeRule.onNodeWithText(pending.todo.title).assertDoesNotExist()
        composeRule.onAllNodesWithText(text(R.string.action_undo)).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, repository.completeCalls)
            assertEquals(TodoState.COMPLETED, repository.occurrences.value.single().state)
        }
    }

    @Test
    fun tl014_completedCheckboxIsReadOnlyAndDoesNotChangeProgress() {
        val completed = occurrence("completed", "完了済みTODO", TODAY, TodoState.COMPLETED)
        val repository = TestTodoRepository(listOf(completed))

        setScreen(repository = repository, showCompleted = true)
        waitForText(completed.todo.title)

        composeRule.onNode(
            toggleState(ToggleableState.On),
            useUnmergedTree = true,
        )
            .assertIsOn()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("1 / 1 完了").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(0, repository.completeCalls)
            assertEquals(TodoState.COMPLETED, repository.occurrences.value.single().state)
        }
    }

    @Test
    fun tl015_skipImmediatelyRemovesRowAndShowsNoUndoAction() {
        val pending = occurrence("skip", "スキップするTODO", TODAY, TodoState.PENDING)
        val repository = TestTodoRepository(listOf(pending))

        setScreen(repository)
        waitForText(pending.todo.title)
        composeRule.onNodeWithContentDescription(text(R.string.content_description_todo_actions))
            .performClick()
        composeRule.onNodeWithText(text(R.string.action_skip)).performClick()

        waitForText(text(R.string.message_todo_skipped))
        composeRule.onNodeWithText(pending.todo.title).assertDoesNotExist()
        composeRule.onAllNodesWithText(text(R.string.action_undo)).assertCountEquals(0)
        composeRule.runOnIdle {
            assertEquals(1, repository.skipCalls)
            assertEquals(TodoState.SKIPPED, repository.occurrences.value.single().state)
        }
    }

    @Test
    fun tl017_archiveAndDeleteExplainScopeBeforeIrreversibleAction() {
        val pending = occurrence("actions", "整理するTODO", TODAY, TodoState.PENDING)
        val repository = TestTodoRepository(listOf(pending))

        setScreen(repository)
        waitForText(pending.todo.title)

        openActionMenu()
        composeRule.onNodeWithText(text(R.string.action_archive)).performClick()
        composeRule.onNodeWithText(text(R.string.dialog_archive_todo_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            text(R.string.dialog_archive_todo_message, pending.todo.title),
        ).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, repository.archiveCalls) }
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        openActionMenu()
        composeRule.onNodeWithText(text(R.string.action_delete)).performClick()
        composeRule.onNodeWithText(text(R.string.dialog_delete_todo_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.dialog_delete_todo_message)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_delete_permanently)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, repository.deleteCalls) }
        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()
    }

    @Test
    fun tl020_pastDateNeverOffersTodoCreationEvenWhenEmpty() {
        setScreen(repository = TestTodoRepository(emptyList()), selectedDate = TODAY.minusDays(1))
        waitForText(text(R.string.empty_selected_date_todos))

        composeRule.onAllNodesWithText(text(R.string.action_add_todo)).assertCountEquals(0)
    }

    @Test
    fun tld06_reopeningResetsTransientDateAndScrollPosition() {
        val occurrences = (1..24).map { index ->
            occurrence(
                id = "restart-$index",
                title = "再表示試験$index",
                date = TODAY,
                state = TodoState.PENDING,
                createdAt = index.toLong(),
            )
        }
        val repository = TestTodoRepository(occurrences)
        val settingsRepository = TestSettingsRepository(showCompletedInitially = false)
        lateinit var reopen: () -> Unit

        composeRule.setContent {
            var generation by remember { mutableIntStateOf(0) }
            reopen = { generation += 1 }
            key(generation) {
                val viewModel = remember {
                    createViewModel(repository, settingsRepository)
                }
                MataTheme(useDynamicColor = false) {
                    TodoListScreen(
                        onAddTodo = {},
                        onEditTodo = {},
                        onDestination = { _: MataDestination -> },
                        contentReadinessEnabled = false,
                        viewModel = viewModel,
                    )
                }
            }
        }

        waitForText(displayedDate(TODAY, isToday = true))
        composeRule.onNodeWithContentDescription(text(R.string.content_description_previous_day))
            .performClick()
        waitForText(displayedDate(TODAY.minusDays(1), isToday = false))
        waitForText("再表示試験1")
        composeRule.onNodeWithTag(TODO_LIST_CONTENT_TAG)
            .performScrollToNode(hasText("再表示試験24"))
        composeRule.onNodeWithText("再表示試験24").assertIsDisplayed()
        composeRule.onNodeWithText("再表示試験1").assertIsNotDisplayed()

        composeRule.runOnIdle { reopen() }

        waitForText(displayedDate(TODAY, isToday = true))
        composeRule.onNodeWithText("再表示試験1").assertIsDisplayed()
    }

    private fun setScreen(
        repository: TestTodoRepository,
        selectedDate: LocalDate = TODAY,
        showCompleted: Boolean = false,
        onEditTodo: (String) -> Unit = {},
        onDestination: (MataDestination) -> Unit = {},
    ) {
        val viewModel = createViewModel(
            repository = repository,
            settingsRepository = TestSettingsRepository(showCompleted),
        )
        viewModel.selectDate(selectedDate)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoListScreen(
                    onAddTodo = {},
                    onEditTodo = onEditTodo,
                    onDestination = onDestination,
                    contentReadinessEnabled = false,
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun createViewModel(
        repository: TestTodoRepository,
        settingsRepository: TestSettingsRepository,
    ) = TodoListViewModel(
        savedStateHandle = SavedStateHandle(),
        todoRepository = repository,
        holidayRepository = TestHolidayRepository(),
        settingsRepository = settingsRepository,
        clock = CLOCK,
        adsConsentRepository = TestAdsConsentRepository(),
    )

    private fun openActionMenu() {
        composeRule.onNodeWithContentDescription(text(R.string.content_description_todo_actions))
            .performClick()
    }

    private fun waitForText(value: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun text(resourceId: Int, vararg args: Any): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId, *args)

    private fun displayedDate(date: LocalDate, isToday: Boolean): String {
        val shortDate = date.format(
            DateTimeFormatter.ofPattern(text(R.string.date_pattern_short), Locale.JAPANESE),
        )
        return if (isToday) text(R.string.todo_list_today_date_format, shortDate) else shortDate
    }

    private fun datePickerDescription(date: LocalDate): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(locale, "yMMMMEEEEd")
        return SimpleDateFormat(pattern, locale).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(
            Date.from(date.atStartOfDay(ZoneOffset.UTC).toInstant()),
        )
    }

    private fun occurrence(
        id: String,
        title: String,
        date: LocalDate,
        state: TodoState,
        createdAt: Long = id.hashCode().toLong(),
        category: Category? = null,
    ): TodoOccurrence {
        val todo = Todo(
            id = id,
            title = title,
            description = "試験用の説明",
            categoryId = category?.id,
            startDate = date,
            endDate = null,
            recurrenceRule = RecurrenceRule.daily(),
            dueMinutes = 8 * 60,
            definitionRevision = 1,
            archivedAt = null,
            createdAt = createdAt,
        )
        return TodoOccurrence(
            todo = todo,
            category = category,
            logicalDate = date,
            state = state,
        )
    }

    private fun hasToggleableState() = SemanticsMatcher("has toggleable state") {
        it.config.contains(SemanticsProperties.ToggleableState)
    }

    private fun toggleState(state: ToggleableState) = SemanticsMatcher.expectValue(
        SemanticsProperties.ToggleableState,
        state,
    )

    private companion object {
        val ZONE: ZoneId = ZoneId.systemDefault()
        val TODAY: LocalDate = LocalDate.now(ZONE)
        val CLOCK: Clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE)
    }
}

private class TestTodoRepository(initialOccurrences: List<TodoOccurrence>) : TodoRepository {
    val occurrences = MutableStateFlow(initialOccurrences)
    val todos = MutableStateFlow(initialOccurrences.map(TodoOccurrence::todo))
    var completeCalls = 0
        private set
    var skipCalls = 0
        private set
    var archiveCalls = 0
        private set
    var deleteCalls = 0
        private set
    val mutationCount: Int
        get() = completeCalls + skipCalls + archiveCalls + deleteCalls

    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> = occurrences
    override fun observeTodos(): Flow<List<Todo>> = todos
    override suspend fun getTodo(id: String): Todo? = todos.value.firstOrNull { it.id == id }

    override suspend fun saveTodo(
        id: String?,
        title: String,
        description: String,
        categoryId: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        recurrenceRule: RecurrenceRule,
        dueMinutes: Int?,
        notifications: List<TodoNotification>,
        dueDate: LocalDate?,
        carryOverEnabled: Boolean,
    ): Result<String> = Result.success(id ?: "todo")

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> {
        completeCalls += 1
        occurrences.value = occurrences.value.map { occurrence ->
            if (occurrence.todo.id == todoId && occurrence.logicalDate == logicalDate) {
                occurrence.copy(state = if (completed) TodoState.COMPLETED else TodoState.PENDING)
            } else {
                occurrence
            }
        }
        return Result.success(Unit)
    }

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> {
        skipCalls += 1
        occurrences.value = occurrences.value.map { occurrence ->
            if (occurrence.todo.id == todoId && occurrence.logicalDate == logicalDate) {
                occurrence.copy(state = if (skipped) TodoState.SKIPPED else TodoState.PENDING)
            } else {
                occurrence
            }
        }
        return Result.success(Unit)
    }

    override suspend fun archiveTodo(id: String): Result<Unit> {
        archiveCalls += 1
        return Result.success(Unit)
    }

    override suspend fun restoreTodo(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun deleteTodo(id: String): Result<Unit> {
        deleteCalls += 1
        return Result.success(Unit)
    }
}

private class TestSettingsRepository(showCompletedInitially: Boolean) : SettingsRepository {
    override val showCompleted = MutableStateFlow(showCompletedInitially)
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
    override suspend fun setArchiveSortOrder(value: ArchiveSortOrder) {
        archiveSortOrder.value = value
    }
}

private class TestHolidayRepository : HolidayRepository {
    override val snapshot = MutableStateFlow(HolidaySnapshot())
    override suspend fun currentSnapshot(): HolidaySnapshot = snapshot.value
    override suspend fun needsRefresh(): Boolean = false
    override suspend fun refresh() = HolidayRefreshResult(successful = true)
    override suspend fun pendingNotificationGeneration(): Long? = null
    override suspend fun markNotificationGenerationProcessed(generation: Long) = Unit
    override suspend fun pendingWidgetGeneration(): Long? = null
    override suspend fun markWidgetGenerationProcessed(generation: Long) = Unit
}

private class TestAdsConsentRepository : AdsConsentRepository {
    override val state: StateFlow<AdsRuntimeState> = MutableStateFlow(AdsRuntimeState())
    override val events: Flow<AdsConsentEvent> = MutableSharedFlow()
    override fun gatherConsent(activity: Activity) = Unit
    override fun showPrivacyOptions(activity: Activity) = Unit
}
