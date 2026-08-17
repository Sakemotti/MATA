package com.mochisofts.mata.ui.todoeditor

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mochisofts.mata.R
import com.mochisofts.mata.core.navigation.TodoEditorRoute
import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.NotificationSystemState
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.NotificationValidationError
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoNotification
import com.mochisofts.mata.domain.model.nextNotificationCandidate
import com.mochisofts.mata.domain.model.nextOccurrenceOnOrAfter
import com.mochisofts.mata.domain.model.notificationTriggerAt
import com.mochisofts.mata.domain.model.deadlineAt
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.validateNotifications
import com.mochisofts.mata.domain.repository.CategoryRepository
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.NotificationScheduler
import com.mochisofts.mata.domain.repository.SettingsRepository
import com.mochisofts.mata.domain.repository.TodoRepository
import com.mochisofts.mata.ui.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodoEditorUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val description: String = "",
    val categories: List<Category> = emptyList(),
    val categoryId: String? = null,
    val today: LocalDate = LocalDate.MIN,
    val startDate: LocalDate = LocalDate.MIN,
    val endDate: LocalDate? = null,
    val recurrenceType: RecurrenceType = RecurrenceType.ONCE,
    val selectedWeekdays: Set<DayOfWeek> = emptySet(),
    val monthlyNthWeekdays: Set<MonthlyNthWeekday> = emptySet(),
    val monthlyDay: Int = 1,
    val intervalDaysInput: String = "1",
    val weeklyCount: Int = 1,
    val monthlyCount: Int = 1,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val dueMinutes: Int? = null,
    val uncategorizedEndHour: Int = 0,
    val notifications: List<TodoNotification> = emptyList(),
    val holidaySnapshot: HolidaySnapshot = HolidaySnapshot(),
    val notificationPreviews: Map<String, ZonedDateTime?> = emptyMap(),
    val hasPastNotificationForCurrentOccurrence: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val notificationSystemState: NotificationSystemState = NotificationSystemState(
        canPostNotifications = false,
        runtimePermissionRelevant = false,
        runtimePermissionGranted = true,
        exactAlarmRelevant = false,
        canScheduleExactAlarms = true,
    ),
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
) {
    val effectiveEndHour: Int
        get() = categories.firstOrNull { it.id == categoryId }?.endHour ?: uncategorizedEndHour

    val notificationErrors: Set<NotificationValidationError>
        get() = validateNotifications(notifications, dueMinutes, effectiveEndHour)

    val recurrenceRule: RecurrenceRule
        get() = RecurrenceRule(
            type = recurrenceType,
            selectedWeekdays = selectedWeekdays,
            monthlyNthWeekdays = monthlyNthWeekdays.takeIf {
                recurrenceType == RecurrenceType.MONTHLY_NTH_WEEKDAYS
            }.orEmpty(),
            monthlyDay = monthlyDay.takeIf { recurrenceType == RecurrenceType.MONTHLY_DAY },
            intervalDays = intervalDaysInput.toIntOrNull()
                ?.takeIf { recurrenceType == RecurrenceType.EVERY_N_DAYS },
            requiredCount = when (recurrenceType) {
                RecurrenceType.WEEKLY_COUNT -> weeklyCount
                RecurrenceType.MONTHLY_COUNT -> monthlyCount
                else -> null
            },
        )

    val canSave: Boolean
        get() = !isLoading && !isSaving && title.trim().isNotEmpty() && title.trim().length <= 100 &&
            description.length <= 1000 && recurrenceRule.isValid() &&
            notificationErrors.isEmpty() &&
            (recurrenceType == RecurrenceType.ONCE || endDate == null || !endDate.isBefore(startDate))
}

sealed interface TodoEditorEffect {
    data class Saved(val isNew: Boolean) : TodoEditorEffect
    data object Deleted : TodoEditorEffect
    data object Archived : TodoEditorEffect
    data object ExplainNotificationPermission : TodoEditorEffect
}

@HiltViewModel
class TodoEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todoRepository: TodoRepository,
    categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationScheduler: NotificationScheduler,
    holidayRepository: HolidayRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TodoEditorRoute>()
    private val today = LocalDate.now(clock)
    private val _uiState = MutableStateFlow(
        TodoEditorUiState(isNew = route.todoId == null, today = today, startDate = today),
    )
    val uiState: StateFlow<TodoEditorUiState> = _uiState.asStateFlow()

    private val effectsChannel = Channel<TodoEditorEffect>(Channel.BUFFERED)
    val effects: Flow<TodoEditorEffect> = effectsChannel.receiveAsFlow()
    private var pendingSavedResult: Pair<Boolean, String>? = null

    init {
        viewModelScope.launch {
            holidayRepository.snapshot.collect { snapshot ->
                _uiState.update { state -> refreshDerived(state.copy(holidaySnapshot = snapshot)) }
            }
        }
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { categories ->
                _uiState.update { state -> refreshDerived(state.copy(categories = categories)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.uncategorizedEndHour.collect { endHour ->
                _uiState.update { state -> refreshDerived(state.copy(uncategorizedEndHour = endHour)) }
            }
        }
        viewModelScope.launch {
            settingsRepository.notificationPermissionRequested.collect { requested ->
                _uiState.update { state -> state.copy(notificationPermissionRequested = requested) }
            }
        }
        viewModelScope.launch {
            settingsRepository.weekStart.collect { weekStart ->
                _uiState.update { state -> refreshDerived(state.copy(weekStart = weekStart)) }
            }
        }
        viewModelScope.launch {
            val todo = route.todoId?.let { todoRepository.getTodo(it) }
            _uiState.update { state -> refreshDerived(
                if (route.todoId != null && todo == null) {
                    state.copy(isLoading = false, errorMessageRes = R.string.error_todo_not_found)
                } else if (todo == null) {
                    state.copy(isLoading = false)
                } else {
                    state.copy(
                        isLoading = false,
                        title = todo.title,
                        description = todo.description,
                        categoryId = todo.categoryId,
                        startDate = todo.startDate,
                        endDate = todo.endDate.takeUnless { todo.recurrenceType == RecurrenceType.ONCE },
                        recurrenceType = todo.recurrenceType,
                        selectedWeekdays = todo.recurrenceRule.selectedWeekdays,
                        monthlyNthWeekdays = todo.recurrenceRule.monthlyNthWeekdays,
                        monthlyDay = todo.recurrenceRule.monthlyDay ?: todo.startDate.dayOfMonth,
                        intervalDaysInput = (todo.recurrenceRule.intervalDays ?: 1).toString(),
                        weeklyCount = todo.recurrenceRule.requiredCount ?: 1,
                        monthlyCount = todo.recurrenceRule.requiredCount ?: 1,
                        dueMinutes = todo.dueMinutes,
                        notifications = todo.notifications,
                    )
                }
            ) }
        }
        refreshNotificationStatus()
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setDescription(value: String) = edit { copy(description = value) }
    fun setCategory(value: String?) = edit { copy(categoryId = value) }
    fun setStartDate(value: LocalDate) = edit { copy(startDate = value) }
    fun setEndDate(value: LocalDate?) = edit { copy(endDate = value) }
    fun setRecurrence(value: RecurrenceType) = edit {
        copy(
            recurrenceType = value,
            selectedWeekdays = if (
                value == RecurrenceType.SELECTED_WEEKDAYS && selectedWeekdays.isEmpty()
            ) {
                setOf(startDate.dayOfWeek)
            } else {
                selectedWeekdays
            },
            monthlyNthWeekdays = if (
                value == RecurrenceType.MONTHLY_NTH_WEEKDAYS && monthlyNthWeekdays.isEmpty()
            ) {
                setOf(
                    MonthlyNthWeekday(
                        ordinal = (startDate.dayOfMonth - 1) / 7 + 1,
                        dayOfWeek = startDate.dayOfWeek,
                    ),
                )
            } else {
                monthlyNthWeekdays
            },
            monthlyDay = if (value == RecurrenceType.MONTHLY_DAY && monthlyDay == 1) {
                startDate.dayOfMonth
            } else {
                monthlyDay
            },
        )
    }
    fun toggleWeekday(value: DayOfWeek) = edit {
        copy(
            selectedWeekdays = if (value in selectedWeekdays) {
                selectedWeekdays - value
            } else {
                selectedWeekdays + value
            },
        )
    }
    fun toggleMonthlyNthWeekday(ordinal: Int, dayOfWeek: DayOfWeek) = edit {
        val value = MonthlyNthWeekday(ordinal, dayOfWeek)
        copy(
            monthlyNthWeekdays = if (value in monthlyNthWeekdays) {
                monthlyNthWeekdays - value
            } else {
                monthlyNthWeekdays + value
            },
        )
    }
    fun setMonthlyDay(value: Int) = edit { copy(monthlyDay = value) }
    fun setIntervalDays(value: String) = edit {
        copy(intervalDaysInput = value.filter(Char::isDigit).take(3))
    }
    fun setWeeklyCount(value: Int) = edit { copy(weeklyCount = value) }
    fun setMonthlyCount(value: Int) = edit { copy(monthlyCount = value) }
    fun setDueMinutes(value: Int?) = edit { copy(dueMinutes = value) }

    fun upsertNotification(
        id: String?,
        relation: NotificationRelation,
        amount: Int,
        unit: NotificationUnit,
    ) = edit {
        val notification = TodoNotification(
            id = id ?: UUID.randomUUID().toString(),
            relation = relation,
            amount = if (relation == NotificationRelation.AT) 0 else amount,
            unit = if (relation == NotificationRelation.AT) NotificationUnit.MINUTE else unit,
        )
        copy(
            notifications = if (id == null) {
                notifications + notification
            } else {
                notifications.map { existing -> if (existing.id == id) notification else existing }
            },
        )
    }

    fun deleteNotification(id: String) = edit {
        copy(notifications = notifications.filterNot { it.id == id })
    }

    fun refreshNotificationStatus() {
        _uiState.update { it.copy(notificationSystemState = notificationScheduler.systemState()) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            todoRepository.saveTodo(
                id = route.todoId,
                title = state.title,
                description = state.description,
                categoryId = state.categoryId,
                startDate = state.startDate,
                endDate = state.endDate.takeUnless { state.recurrenceType == RecurrenceType.ONCE },
                recurrenceRule = state.recurrenceRule,
                dueMinutes = state.dueMinutes,
                notifications = state.notifications,
            ).onSuccess { todoId ->
                val systemState = notificationScheduler.systemState()
                val shouldRequestPermission = state.notifications.isNotEmpty() &&
                    systemState.runtimePermissionRelevant &&
                    !systemState.runtimePermissionGranted &&
                    !state.notificationPermissionRequested
                if (shouldRequestPermission) {
                    settingsRepository.setNotificationPermissionRequested(true)
                    pendingSavedResult = (route.todoId == null) to todoId
                    effectsChannel.send(TodoEditorEffect.ExplainNotificationPermission)
                } else {
                    effectsChannel.send(TodoEditorEffect.Saved(route.todoId == null))
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessageRes = throwable.toUserMessageRes(R.string.error_todo_save_failed),
                    )
                }
            }
        }
    }

    fun notificationPermissionRequestFinished() {
        val (isNew, todoId) = pendingSavedResult ?: return
        pendingSavedResult = null
        refreshNotificationStatus()
        viewModelScope.launch {
            runCatching { notificationScheduler.reconcileTodo(todoId) }
            effectsChannel.send(TodoEditorEffect.Saved(isNew))
        }
    }

    fun delete() {
        val todoId = route.todoId ?: return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            todoRepository.deleteTodo(todoId)
                .onSuccess { effectsChannel.send(TodoEditorEffect.Deleted) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            errorMessageRes = R.string.error_todo_delete_failed,
                        )
                    }
                }
        }
    }

    fun archive() {
        val todoId = route.todoId ?: return
        _uiState.update { it.copy(isSaving = true, errorMessageRes = null) }
        viewModelScope.launch {
            todoRepository.archiveTodo(todoId)
                .onSuccess { effectsChannel.send(TodoEditorEffect.Archived) }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isSaving = false,
                            errorMessageRes = throwable.toUserMessageRes(
                                R.string.error_todo_archive_failed,
                            ),
                        )
                    }
                }
        }
    }

    private fun edit(transform: TodoEditorUiState.() -> TodoEditorUiState) {
        _uiState.update { state ->
            refreshDerived(state.transform().copy(isDirty = true, errorMessageRes = null))
        }
    }

    private fun refreshDerived(state: TodoEditorUiState): TodoEditorUiState {
        if (!state.recurrenceRule.isValid()) {
            return state.copy(
                notificationPreviews = emptyMap(),
                hasPastNotificationForCurrentOccurrence = false,
            )
        }
        val todo = Todo(
            id = route.todoId ?: "draft",
            title = state.title,
            description = state.description,
            categoryId = state.categoryId,
            startDate = state.startDate,
            endDate = state.endDate.takeUnless { state.recurrenceType == RecurrenceType.ONCE },
            recurrenceRule = state.recurrenceRule,
            dueMinutes = state.dueMinutes,
            definitionRevision = 1,
            archivedAt = null,
            createdAt = 0,
            notifications = state.notifications,
        )
        val now = ZonedDateTime.now(clock)
        val previews = state.notifications.associate { notification ->
            notification.id to nextNotificationCandidate(
                todo = todo,
                notification = notification,
                endHour = state.effectiveEndHour,
                now = now,
                weekStart = state.weekStart,
                holidays = state.holidaySnapshot.dates,
            )?.triggerAt
        }
        val firstOccurrence = todo.nextOccurrenceOnOrAfter(
            logicalDate(now, state.effectiveEndHour),
            state.holidaySnapshot.dates,
        )
        val hasPastCandidate = firstOccurrence != null && state.notifications.any { notification ->
            val deadline = deadlineAt(
                firstOccurrence,
                state.effectiveEndHour,
                state.dueMinutes,
                now.zone,
            )
            !notificationTriggerAt(deadline, notification).isAfter(now)
        }
        return state.copy(
            notifications = state.notifications.sortedWith(
                compareBy<TodoNotification> { previews[it.id]?.toInstant() }
                    .thenBy { it.relation.ordinal }
                    .thenBy { it.normalizedMinutes },
            ),
            notificationPreviews = previews,
            hasPastNotificationForCurrentOccurrence = hasPastCandidate,
        )
    }
}
