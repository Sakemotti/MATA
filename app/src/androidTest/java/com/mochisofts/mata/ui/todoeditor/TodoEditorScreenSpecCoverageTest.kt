package com.mochisofts.mata.ui.todoeditor

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.core.navigation.todoEditorSavedMessageRes
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodoEditorScreenSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun te001_newAndEditUseIndependentFullScreenModes() {
        val newViewModel = viewModel()
        val editViewModel = viewModel(todo = existingTodo())
        val displayedViewModel = mutableStateOf(newViewModel)
        setScreen(displayedViewModel)

        waitForText(text(R.string.todo_editor_add_title))
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.todo_editor_section_basic_information))
            .assertIsDisplayed()

        composeRule.runOnIdle { displayedViewModel.value = editViewModel }
        waitForText(text(R.string.todo_editor_edit_title))
        composeRule.onNodeWithText(existingTodo().title).assertIsDisplayed()
    }

    @Test
    fun te002_modesUseExpectedInitialValuesAndOnlyEditHasDestructiveActions() {
        val newViewModel = viewModel()
        val editTodo = existingTodo()
        val editViewModel = viewModel(todo = editTodo)
        val displayedViewModel = mutableStateOf(newViewModel)
        val savedMessages = mutableListOf<Int>()
        setScreen(displayedViewModel, onSaved = { isNew ->
            savedMessages += todoEditorSavedMessageRes(isNew)
        })

        waitForText(text(R.string.todo_editor_add_title))
        assertEquals("", newViewModel.uiState.value.title)
        assertEquals(TODAY, newViewModel.uiState.value.startDate)
        composeRule.onNodeWithContentDescription(text(R.string.content_description_archive_todo))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_delete_todo))
            .assertDoesNotExist()

        composeRule.runOnIdle { displayedViewModel.value = editViewModel }
        waitForText(text(R.string.todo_editor_edit_title))
        composeRule.onNode(hasSetTextAction() and hasText(editTodo.title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_archive_todo))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(text(R.string.content_description_delete_todo))
            .assertIsDisplayed()
        composeRule.runOnIdle { editViewModel.setTitle("updated title") }
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            savedMessages == listOf(R.string.message_todo_updated)
        }
    }

    @Test
    fun te004_categoryMenuKeepsUncategorizedFirstAndRepositoryOrderWithSingleSelection() {
        val categories = listOf(
            Category("second", "Second", 2, "Home", 1),
            Category("first", "First", 4, "SportsEsports", 0),
        )
        val viewModel = viewModel(categories = categories)
        setScreen(mutableStateOf(viewModel))
        waitForText(text(R.string.todo_editor_add_title))

        composeRule.onNode(
            hasText(text(R.string.label_uncategorized)) and hasClickAction(),
        ).performClick()
        val uncategorizedMenuTop = composeRule.onNodeWithTag(TODO_EDITOR_UNCATEGORIZED_MENU_TAG)
            .fetchSemanticsNode().boundsInRoot.top
        val secondTop = composeRule.onNodeWithTag(todoEditorCategoryMenuTag("second"))
            .fetchSemanticsNode().boundsInRoot.top
        val firstTop = composeRule.onNodeWithTag(todoEditorCategoryMenuTag("first"))
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue(uncategorizedMenuTop < secondTop)
        assertTrue(secondTop < firstTop)

        composeRule.onNodeWithText("First").performClick()
        composeRule.runOnIdle { assertEquals("first", viewModel.uiState.value.categoryId) }
        composeRule.onNode(
            hasText("First") and hasClickAction(),
        ).performClick()
        composeRule.onNodeWithText("Second").performClick()
        composeRule.runOnIdle { assertEquals("second", viewModel.uiState.value.categoryId) }
    }

    @Test
    fun te008_onceAndRepeatingModesShowTheirOwnDateControls() {
        val viewModel = viewModel()
        setScreen(mutableStateOf(viewModel))
        waitForText(text(R.string.todo_editor_add_title))

        composeRule.onNodeWithContentDescription(
            fieldDescription(R.string.todo_editor_execution_date_label, japaneseDate(TODAY)),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.todo_editor_specify_end_date)).assertDoesNotExist()

        composeRule.runOnIdle { viewModel.setRecurrence(RecurrenceType.DAILY) }
        composeRule.onNodeWithContentDescription(
            fieldDescription(R.string.todo_editor_start_date_label, japaneseDate(TODAY)),
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.todo_editor_specify_end_date))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.todo_editor_indefinite))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.todo_editor_due_date_label)).assertDoesNotExist()
    }

    @Test
    fun te024_saveIsEnabledOnlyForAValidDirtyDraftAndDisablesAfterRevert() {
        val todo = existingTodo()
        val viewModel = viewModel(todo = todo)
        setScreen(mutableStateOf(viewModel))
        waitForText(text(R.string.todo_editor_edit_title))
        val save = composeRule.onNodeWithText(text(R.string.action_save))

        save.assertIsNotEnabled()
        composeRule.runOnIdle { viewModel.setDescription("changed") }
        save.assertIsEnabled()
        composeRule.runOnIdle { viewModel.setTitle("") }
        save.assertIsNotEnabled()
        composeRule.runOnIdle { viewModel.setTitle(todo.title) }
        save.assertIsEnabled()
        composeRule.runOnIdle { viewModel.setDescription(todo.description) }
        save.assertIsNotEnabled()
    }

    @Test
    fun te026_saveReportsNewOrEditModeForListNavigationAndMessageSelection() {
        val newViewModel = viewModel()
        val editViewModel = viewModel(todo = existingTodo())
        val displayedViewModel = mutableStateOf(newViewModel)
        val savedModes = mutableListOf<Boolean>()
        val savedMessages = mutableListOf<Int>()
        setScreen(displayedViewModel, onSaved = { isNew ->
            savedModes += isNew
            savedMessages += todoEditorSavedMessageRes(isNew)
        })
        waitForText(text(R.string.todo_editor_add_title))

        composeRule.runOnIdle { newViewModel.setTitle("new todo") }
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { savedModes == listOf(true) }
        composeRule.runOnIdle {
            assertEquals(listOf(R.string.message_todo_added), savedMessages)
        }

        composeRule.runOnIdle { displayedViewModel.value = editViewModel }
        waitForText(text(R.string.todo_editor_edit_title))
        composeRule.runOnIdle { editViewModel.setTitle("edited todo") }
        composeRule.onNodeWithText(text(R.string.action_save)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { savedModes == listOf(true, false) }
        composeRule.runOnIdle {
            assertEquals(
                listOf(R.string.message_todo_added, R.string.message_todo_updated),
                savedMessages,
            )
        }
    }

    @Test
    fun te027_backConfirmsOnlyDirtyDraftAndContinueOrDiscardBehaveAsSelected() {
        val viewModel = viewModel()
        var backCount = 0
        setScreen(mutableStateOf(viewModel), onBack = { backCount += 1 })
        waitForText(text(R.string.todo_editor_add_title))

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.runOnIdle { assertEquals(1, backCount) }
        composeRule.onNodeWithText(text(R.string.dialog_discard_changes_title)).assertDoesNotExist()

        composeRule.runOnIdle { viewModel.setTitle("draft") }
        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.dialog_discard_changes_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_continue_editing)).performClick()
        composeRule.onNode(hasSetTextAction() and hasText("draft")).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(1, backCount) }

        composeRule.onNodeWithContentDescription(text(R.string.action_back)).performClick()
        composeRule.onNodeWithText(text(R.string.action_discard)).performClick()
        composeRule.runOnIdle { assertEquals(2, backCount) }
    }

    private fun setScreen(
        displayedViewModel: androidx.compose.runtime.MutableState<TodoEditorViewModel>,
        onBack: () -> Unit = {},
        onSaved: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                TodoEditorScreen(
                    onBack = onBack,
                    onSaved = onSaved,
                    onNotFound = {},
                    viewModel = displayedViewModel.value,
                )
            }
        }
    }

    private fun viewModel(
        todo: Todo? = null,
        categories: List<Category> = emptyList(),
    ) = TodoEditorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("todoId" to todo?.id, "initialDate" to null)),
        todoRepository = TodoEditorTestTodoRepository(todo),
        categoryRepository = TodoEditorTestCategoryRepository(categories),
        settingsRepository = TodoEditorTestSettingsRepository(),
        notificationScheduler = TodoEditorTestNotificationScheduler(),
        holidayRepository = TodoEditorTestHolidayRepository(),
        clock = FIXED_CLOCK,
    )

    private fun existingTodo() = Todo(
        id = "existing",
        title = "Existing todo",
        description = "original description",
        categoryId = null,
        startDate = TODAY,
        endDate = null,
        recurrenceRule = RecurrenceRule.daily(),
        dueMinutes = 9 * 60,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = 1,
    )

    private fun waitForText(value: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun fieldDescription(labelRes: Int, value: String): String = text(
        R.string.content_description_field_value,
        text(labelRes),
        value,
    )

    private fun japaneseDate(value: LocalDate): String = value.format(
        DateTimeFormatter.ofPattern(text(R.string.date_pattern_full), Locale.JAPANESE),
    )

    private fun text(resId: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *args)

    companion object {
        private val TODAY = LocalDate.of(2026, 9, 15)
        private val FIXED_CLOCK: Clock = Clock.fixed(
            ZonedDateTime.parse("2026-09-15T12:00:00+09:00").toInstant(),
            ZoneId.of("Asia/Tokyo"),
        )
    }
}

private class TodoEditorTestTodoRepository(todo: Todo?) : TodoRepository {
    private val todoById = todo?.let { mapOf(it.id to it) }.orEmpty()

    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> =
        flowOf(emptyList())

    override fun observeTodos(): Flow<List<Todo>> = flowOf(todoById.values.toList())
    override suspend fun getTodo(id: String): Todo? = todoById[id]

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
    ): Result<String> = Result.success(id ?: "new")

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
        scheduledLogicalDate: LocalDate,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun archiveTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun restoreTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTodo(id: String): Result<Unit> = Result.success(Unit)
}

private class TodoEditorTestCategoryRepository(
    categories: List<Category>,
) : CategoryRepository {
    private val state = MutableStateFlow(categories)
    override fun observeCategories(): Flow<List<Category>> = state
    override suspend fun getCategory(id: String): Category? = state.value.firstOrNull { it.id == id }
    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
    ): Result<String> = Result.success(id ?: "new-category")

    override suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> = Result.success(Unit)
    override suspend fun deleteCategory(id: String): Result<Unit> = Result.success(Unit)
}

private class TodoEditorTestSettingsRepository : SettingsRepository {
    override val showCompleted: Flow<Boolean> = flowOf(false)
    override val todoListMode: Flow<String> = flowOf("DATE")
    override val dayEndHour: Flow<Int> = flowOf(0)
    override val weekStart: Flow<DayOfWeek> = flowOf(DayOfWeek.MONDAY)
    override val theme: Flow<AppTheme> = flowOf(AppTheme.SYSTEM)
    override val notificationPermissionRequested: Flow<Boolean> = flowOf(false)
    override suspend fun setShowCompleted(value: Boolean) = Unit
    override suspend fun setTodoListMode(value: String) = Unit
    override suspend fun setDayEndHour(value: Int) = Unit
    override suspend fun setWeekStart(value: DayOfWeek) = Unit
    override suspend fun setTheme(value: AppTheme) = Unit
    override suspend fun setNotificationPermissionRequested(value: Boolean) = Unit
}

private class TodoEditorTestNotificationScheduler : NotificationScheduler {
    override val notificationCount: Flow<Int> = flowOf(0)
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

private class TodoEditorTestHolidayRepository : HolidayRepository {
    private val state = HolidaySnapshot()
    override val snapshot: Flow<HolidaySnapshot> = flowOf(state)
    override suspend fun currentSnapshot(): HolidaySnapshot = state
    override suspend fun needsRefresh(): Boolean = false
    override suspend fun refresh(): HolidayRefreshResult = HolidayRefreshResult(successful = true)
    override suspend fun pendingNotificationGeneration(): Long? = null
    override suspend fun markNotificationGenerationProcessed(generation: Long) = Unit
    override suspend fun pendingWidgetGeneration(): Long? = null
    override suspend fun markWidgetGenerationProcessed(generation: Long) = Unit
}
