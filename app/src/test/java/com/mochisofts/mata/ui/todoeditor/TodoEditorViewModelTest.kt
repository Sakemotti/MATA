package com.mochisofts.mata.ui.todoeditor

import androidx.lifecycle.SavedStateHandle
import com.mochisofts.mata.MainDispatcherRule
import com.mochisofts.mata.domain.model.AppTheme
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.RecurrenceDayFilter
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newTodoUsesCurrentLogicalDateAndRejectsEarlierDate() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(
            todoRepository = repository,
            settingsRepository = FakeSettingsRepository(dayEndHour = 4),
            clock = fixedClock("2026-09-03T02:00:00+09:00"),
        )
        runCurrent()

        assertEquals(LocalDate.of(2026, 9, 2), viewModel.uiState.value.today)
        assertEquals(LocalDate.of(2026, 9, 2), viewModel.uiState.value.startDate)

        viewModel.setTitle("朝のルーチン")
        viewModel.setStartDate(LocalDate.of(2026, 9, 1))
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.save()
        runCurrent()
        assertEquals(0, repository.saveCount)

        viewModel.setStartDate(LocalDate.of(2026, 9, 2))
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()

        assertEquals(LocalDate.of(2026, 9, 2), repository.lastSave?.startDate)
        assertEquals(TodoEditorEffect.Saved(isNew = true), viewModel.effects.first())
    }

    @Test
    fun existingTodoWithPastStartDateCanStillBeSaved() {
        val state = TodoEditorUiState(
            isLoading = false,
            isNew = false,
            title = "以前からのルーチン",
            today = LocalDate.of(2026, 9, 3),
            startDate = LocalDate.of(2025, 1, 1),
        )

        assertTrue(state.canSave)
    }

    @Test
    fun monthlyNthWeekdaysRequireASelectionAndSaveMultipleValues() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(
            todoRepository = repository,
            clock = fixedClock("2026-09-03T12:00:00+09:00"),
        )
        runCurrent()

        val firstMonday = MonthlyNthWeekday(1, DayOfWeek.MONDAY)
        val thirdFriday = MonthlyNthWeekday(3, DayOfWeek.FRIDAY)
        viewModel.setTitle("月次レビュー")
        viewModel.setStartDate(LocalDate.of(2026, 9, 7))
        viewModel.setRecurrence(RecurrenceType.MONTHLY_NTH_WEEKDAYS)

        assertEquals(setOf(firstMonday), viewModel.uiState.value.monthlyNthWeekdays)
        viewModel.toggleMonthlyNthWeekday(firstMonday.ordinal, firstMonday.dayOfWeek)
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()
        assertEquals(0, repository.saveCount)

        viewModel.toggleMonthlyNthWeekday(firstMonday.ordinal, firstMonday.dayOfWeek)
        viewModel.toggleMonthlyNthWeekday(thirdFriday.ordinal, thirdFriday.dayOfWeek)
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.save()
        runCurrent()

        assertEquals(
            setOf(firstMonday, thirdFriday),
            repository.lastSave?.recurrenceRule?.monthlyNthWeekdays,
        )
    }

    @Test
    fun weeklyCountPresetAndPeriodAreSavedTogether() = runTest {
        val repository = FakeTodoRepository()
        val viewModel = createViewModel(todoRepository = repository)
        runCurrent()

        viewModel.setTitle("週末チャレンジ")
        viewModel.setRecurrence(RecurrenceType.WEEKLY_COUNT)
        viewModel.setPeriodWeeks(2)
        viewModel.setWeeklyCount(4)
        viewModel.setDayFilter(RecurrenceDayFilter.WEEKENDS_HOLIDAYS)
        viewModel.save()
        runCurrent()

        val rule = requireNotNull(repository.lastSave).recurrenceRule
        assertEquals(RecurrenceType.WEEKLY_COUNT, rule.type)
        assertEquals(2, rule.periodWeeks)
        assertEquals(4, rule.requiredCount)
        assertEquals(RecurrenceDayFilter.WEEKENDS_HOLIDAYS, rule.dayFilter)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), rule.selectedWeekdays)
    }

    @Test
    fun inputLimitsAndRecurrenceValuesControlSaveAvailability() = runTest {
        val viewModel = createViewModel()
        runCurrent()

        viewModel.setTitle("a".repeat(100))
        viewModel.setDescription("b".repeat(1000))
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.setTitle("a".repeat(101))
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.setTitle("有効なタイトル")
        viewModel.setDescription("b".repeat(1001))
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.setDescription("")
        viewModel.setRecurrence(RecurrenceType.EVERY_N_DAYS)
        viewModel.setIntervalDays("0")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.setIntervalDays("12a34")
        assertEquals("123", viewModel.uiState.value.intervalDaysInput)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun notificationPermissionExplanationCompletesSaveAndReconcilesTodo() = runTest {
        val repository = FakeTodoRepository()
        val settings = FakeSettingsRepository()
        val scheduler = FakeNotificationScheduler(
            state = NotificationSystemState(
                canPostNotifications = false,
                runtimePermissionRelevant = true,
                runtimePermissionGranted = false,
                exactAlarmRelevant = false,
                canScheduleExactAlarms = true,
            ),
        )
        val viewModel = createViewModel(
            todoRepository = repository,
            settingsRepository = settings,
            notificationScheduler = scheduler,
        )
        runCurrent()

        viewModel.setTitle("通知付きTODO")
        viewModel.upsertNotification(
            id = null,
            relation = NotificationRelation.AT,
            amount = 99,
            unit = NotificationUnit.DAY,
        )
        viewModel.save()
        runCurrent()

        assertEquals(true, settings.notificationPermissionRequestedState.value)
        assertEquals(TodoEditorEffect.ExplainNotificationPermission, viewModel.effects.first())

        viewModel.notificationPermissionRequestFinished()
        runCurrent()

        assertEquals(listOf("saved-todo"), scheduler.reconciledTodoIds)
        assertEquals(TodoEditorEffect.Saved(isNew = true), viewModel.effects.first())
    }

    @Test
    fun saveFailureKeepsDraftAndAllowsRetry() = runTest {
        val repository = FakeTodoRepository().apply {
            saveResult = Result.failure(IllegalStateException("save failed"))
        }
        val viewModel = createViewModel(todoRepository = repository)
        runCurrent()

        viewModel.setTitle("保存に失敗するTODO")
        viewModel.save()
        runCurrent()

        assertEquals("保存に失敗するTODO", viewModel.uiState.value.title)
        assertTrue(viewModel.uiState.value.isDirty)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.errorMessageRes)
        assertTrue(viewModel.uiState.value.canSave)
    }

    private fun createViewModel(
        todoRepository: FakeTodoRepository = FakeTodoRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        notificationScheduler: FakeNotificationScheduler = FakeNotificationScheduler(),
        clock: Clock = fixedClock("2026-09-03T12:00:00+09:00"),
    ) = TodoEditorViewModel(
        savedStateHandle = SavedStateHandle(),
        todoRepository = todoRepository,
        categoryRepository = FakeCategoryRepository(),
        settingsRepository = settingsRepository,
        notificationScheduler = notificationScheduler,
        holidayRepository = FakeHolidayRepository(),
        clock = clock,
    )

    private fun fixedClock(value: String): Clock {
        val zone = ZoneId.of("Asia/Tokyo")
        return Clock.fixed(ZonedDateTime.parse(value).toInstant(), zone)
    }
}

private data class SaveTodoCall(
    val id: String?,
    val title: String,
    val description: String,
    val categoryId: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val recurrenceRule: RecurrenceRule,
    val dueMinutes: Int?,
    val notifications: List<TodoNotification>,
)

private class FakeTodoRepository(
    private val todos: Map<String, Todo> = emptyMap(),
) : TodoRepository {
    var saveResult: Result<String> = Result.success("saved-todo")
    var saveCount: Int = 0
    var lastSave: SaveTodoCall? = null

    override fun observeOccurrences(selectedDate: LocalDate): Flow<List<TodoOccurrence>> =
        flowOf(emptyList())

    override fun observeTodos(): Flow<List<Todo>> = flowOf(todos.values.toList())

    override suspend fun getTodo(id: String): Todo? = todos[id]

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
    ): Result<String> {
        saveCount += 1
        lastSave = SaveTodoCall(
            id = id,
            title = title,
            description = description,
            categoryId = categoryId,
            startDate = startDate,
            endDate = endDate,
            recurrenceRule = recurrenceRule,
            dueMinutes = dueMinutes,
            notifications = notifications,
        )
        return saveResult
    }

    override suspend fun setCompleted(
        todoId: String,
        logicalDate: LocalDate,
        completed: Boolean,
        operationId: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun setSkipped(
        todoId: String,
        logicalDate: LocalDate,
        skipped: Boolean,
        operationId: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun archiveTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun restoreTodo(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteTodo(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeCategoryRepository : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> = flowOf(emptyList())
    override suspend fun getCategory(id: String): Category? = null

    override suspend fun saveCategory(
        id: String?,
        name: String,
        colorIndex: Int,
        iconName: String,
    ): Result<String> = Result.success(id ?: "category")

    override suspend fun reorderCategories(orderedIds: List<String>): Result<Unit> =
        Result.success(Unit)

    override suspend fun deleteCategory(id: String): Result<Unit> = Result.success(Unit)
}

private class FakeSettingsRepository(
    dayEndHour: Int = 0,
) : SettingsRepository {
    private val showCompletedState = MutableStateFlow(false)
    private val todoListModeState = MutableStateFlow("DATE")
    private val dayEndHourState = MutableStateFlow(dayEndHour)
    private val weekStartState = MutableStateFlow(DayOfWeek.MONDAY)
    private val themeState = MutableStateFlow(AppTheme.SYSTEM)
    val notificationPermissionRequestedState = MutableStateFlow(false)

    override val showCompleted: Flow<Boolean> = showCompletedState
    override val todoListMode: Flow<String> = todoListModeState
    override val dayEndHour: Flow<Int> = dayEndHourState
    override val weekStart: Flow<DayOfWeek> = weekStartState
    override val theme: Flow<AppTheme> = themeState
    override val notificationPermissionRequested: Flow<Boolean> =
        notificationPermissionRequestedState

    override suspend fun setShowCompleted(value: Boolean) {
        showCompletedState.value = value
    }

    override suspend fun setTodoListMode(value: String) {
        todoListModeState.value = value
    }

    override suspend fun setDayEndHour(value: Int) {
        dayEndHourState.value = value
    }

    override suspend fun setWeekStart(value: DayOfWeek) {
        weekStartState.value = value
    }

    override suspend fun setTheme(value: AppTheme) {
        themeState.value = value
    }

    override suspend fun setNotificationPermissionRequested(value: Boolean) {
        notificationPermissionRequestedState.value = value
    }
}

private class FakeNotificationScheduler(
    var state: NotificationSystemState = NotificationSystemState(
        canPostNotifications = true,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    ),
) : NotificationScheduler {
    override val notificationCount: Flow<Int> = flowOf(0)
    val reconciledTodoIds = mutableListOf<String>()

    override fun systemState(): NotificationSystemState = state

    override suspend fun reconcileTodo(todoId: String) {
        reconciledTodoIds += todoId
    }

    override suspend fun reconcileAll() = Unit
    override suspend fun cancelTodo(todoId: String) = Unit
}

private class FakeHolidayRepository : HolidayRepository {
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
