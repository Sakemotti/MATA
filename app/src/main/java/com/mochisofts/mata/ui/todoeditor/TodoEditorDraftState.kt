package com.mochisofts.mata.ui.todoeditor

import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.NotificationRelation
import com.mochisofts.mata.domain.model.NotificationUnit
import com.mochisofts.mata.domain.model.RecurrenceDayFilter
import com.mochisofts.mata.domain.model.RecurrenceType
import com.mochisofts.mata.domain.model.TodoNotification
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val TODO_EDITOR_DRAFT_KEY = "todo_editor_draft"

internal object TodoEditorDraftState {
    private const val CURRENT_VERSION = 1
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(state: TodoEditorUiState, todoId: String?): String = json.encodeToString(
        TodoEditorDraftPayload(
            version = CURRENT_VERSION,
            todoId = todoId,
            title = state.title,
            description = state.description,
            categoryId = state.categoryId,
            startDate = state.startDate.toString(),
            endDate = state.endDate?.toString(),
            recurrenceType = state.recurrenceType.code,
            selectedWeekdays = state.selectedWeekdays.map(DayOfWeek::getValue).sorted(),
            monthlyNthWeekdays = state.monthlyNthWeekdays
                .sortedWith(compareBy(MonthlyNthWeekday::ordinal, { it.dayOfWeek.value }))
                .map { MonthlyNthWeekdayPayload(it.ordinal, it.dayOfWeek.value) },
            monthlyDay = state.monthlyDay,
            intervalDaysInput = state.intervalDaysInput,
            weeklyCount = state.weeklyCount,
            periodWeeks = state.periodWeeks,
            dayFilter = state.dayFilter.code,
            monthlyCount = state.monthlyCount,
            dueMinutes = state.dueMinutes,
            dueDate = state.dueDate?.toString(),
            carryOverEnabled = state.carryOverEnabled,
            notifications = state.notifications.map { notification ->
                TodoNotificationPayload(
                    id = notification.id,
                    relation = notification.relation.code,
                    amount = notification.amount,
                    unit = notification.unit.code,
                )
            },
        ),
    )

    fun restore(
        encoded: String?,
        todoId: String?,
        baseState: TodoEditorUiState,
    ): TodoEditorUiState? = encoded?.let { value ->
        runCatching {
            val payload = json.decodeFromString<TodoEditorDraftPayload>(value)
            require(payload.version == CURRENT_VERSION)
            require(payload.todoId == todoId)
            baseState.copy(
                title = payload.title,
                description = payload.description,
                categoryId = payload.categoryId,
                startDate = LocalDate.parse(payload.startDate),
                endDate = payload.endDate?.let(LocalDate::parse),
                recurrenceType = RecurrenceType.fromStoredValue(payload.recurrenceType),
                selectedWeekdays = payload.selectedWeekdays.map(DayOfWeek::of).toSet(),
                monthlyNthWeekdays = payload.monthlyNthWeekdays.map {
                    MonthlyNthWeekday(it.ordinal, DayOfWeek.of(it.dayOfWeek))
                }.toSet(),
                monthlyDay = payload.monthlyDay,
                intervalDaysInput = payload.intervalDaysInput,
                weeklyCount = payload.weeklyCount,
                periodWeeks = payload.periodWeeks,
                dayFilter = RecurrenceDayFilter.fromStoredValue(payload.dayFilter),
                monthlyCount = payload.monthlyCount,
                dueMinutes = payload.dueMinutes,
                dueDate = payload.dueDate?.let(LocalDate::parse),
                carryOverEnabled = payload.carryOverEnabled,
                notifications = payload.notifications.map {
                    TodoNotification(
                        id = it.id,
                        relation = NotificationRelation.fromStoredValue(it.relation),
                        amount = it.amount,
                        unit = NotificationUnit.fromStoredValue(it.unit),
                    )
                },
            )
        }.getOrNull()
    }
}

@Serializable
private data class TodoEditorDraftPayload(
    val version: Int,
    val todoId: String?,
    val title: String,
    val description: String,
    val categoryId: String?,
    val startDate: String,
    val endDate: String?,
    val recurrenceType: String,
    val selectedWeekdays: List<Int>,
    val monthlyNthWeekdays: List<MonthlyNthWeekdayPayload>,
    val monthlyDay: Int,
    val intervalDaysInput: String,
    val weeklyCount: Int,
    val periodWeeks: Int,
    val dayFilter: String,
    val monthlyCount: Int,
    val dueMinutes: Int?,
    val dueDate: String?,
    val carryOverEnabled: Boolean,
    val notifications: List<TodoNotificationPayload>,
)

@Serializable
private data class MonthlyNthWeekdayPayload(
    val ordinal: Int,
    val dayOfWeek: Int,
)

@Serializable
private data class TodoNotificationPayload(
    val id: String,
    val relation: String,
    val amount: Int,
    val unit: String,
)
