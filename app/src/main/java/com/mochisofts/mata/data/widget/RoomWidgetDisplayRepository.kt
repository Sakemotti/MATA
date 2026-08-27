package com.mochisofts.mata.data.widget

import android.content.Context
import androidx.room.withTransaction
import com.mochisofts.mata.R
import com.mochisofts.mata.data.local.CategoryDao
import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.HolidayDao
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.data.local.TodoDao
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionDao
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.repository.toDomain
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.RecurrenceProgress
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.WidgetCategoryGroup
import com.mochisofts.mata.domain.model.WidgetDisplayModel
import com.mochisofts.mata.domain.model.WidgetTodoItem
import com.mochisofts.mata.domain.model.deadlineAt
import com.mochisofts.mata.domain.model.logicalDate
import com.mochisofts.mata.domain.model.logicalDayEnd
import com.mochisofts.mata.domain.model.occursOn
import com.mochisofts.mata.domain.model.recurrencePeriod
import com.mochisofts.mata.domain.model.usesHolidayData
import com.mochisofts.mata.domain.repository.HolidayRepository
import com.mochisofts.mata.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class RoomWidgetDisplayRepository @Inject constructor(
    private val database: MataDatabase,
    private val todoDao: TodoDao,
    private val categoryDao: CategoryDao,
    private val executionDao: TodoExecutionDao,
    private val holidayDao: HolidayDao,
    private val settingsRepository: SettingsRepository,
    private val holidayRepository: HolidayRepository,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) {
    suspend fun createSnapshot(): WidgetDisplayModel {
        val now = ZonedDateTime.now(clock)
        val dayEndHour = settingsRepository.dayEndHour.first()
        val weekStart = settingsRepository.weekStart.first()
        val holidayState = holidayRepository.currentSnapshot()
        val data = database.withTransaction {
            val todos = todoDao.findAllActive()
            val categories = categoryDao.findAll()
            val currentLogicalDate = logicalDate(now, dayEndHour)
            WidgetSourceData(
                todos = todos,
                categories = categories,
                executions = executionDao.findBetween(
                    currentLogicalDate.withDayOfMonth(1).minusDays(7).toString(),
                    currentLogicalDate.toString(),
                ),
                holidays = holidayDao.findAll().mapTo(mutableSetOf()) { LocalDate.parse(it.date) },
            )
        }
        return buildWidgetDisplayModel(
            now = now,
            source = data,
            dayEndHour = dayEndHour,
            weekStart = weekStart,
            holidayState = holidayState,
            uncategorizedName = context.getString(R.string.label_uncategorized),
            logicalDateLabel = { date ->
                context.getString(R.string.widget_logical_date_format, date.monthValue, date.dayOfMonth)
            },
            deadlineLabel = { isNextDay, hour, minute ->
                context.getString(
                    if (isNextDay) R.string.widget_deadline_next_day_format
                    else R.string.widget_deadline_format,
                    hour,
                    minute,
                )
            },
        )
    }
}

internal data class WidgetSourceData(
    val todos: List<TodoEntity>,
    val categories: List<CategoryEntity>,
    val executions: List<TodoExecutionEntity>,
    val holidays: Set<LocalDate>,
)

internal fun buildWidgetDisplayModel(
    now: ZonedDateTime,
    source: WidgetSourceData,
    dayEndHour: Int,
    weekStart: DayOfWeek,
    holidayState: HolidaySnapshot,
    uncategorizedName: String,
    logicalDateLabel: (LocalDate) -> String,
    deadlineLabel: (Boolean, Int, Int) -> String,
): WidgetDisplayModel {
    val categories = source.categories.associateBy(CategoryEntity::id)
    val executions = source.executions.associateBy { it.todoId to it.logicalDate }
    val executionsByTodo = source.executions.groupBy(TodoExecutionEntity::todoId)
    val createdAtByTodo = source.todos.associate { it.id to it.createdAt }
    val groupedItems = linkedMapOf<String?, MutableList<WidgetTodoItem>>()
    var provisional = false

    source.todos.forEach { entity ->
        val todo = entity.toDomain()
        val category = entity.categoryId?.let(categories::get)
        val targetDate = logicalDate(now, dayEndHour)
        if (!todo.occursOn(targetDate, source.holidays)) return@forEach
        if (executions[todo.id to targetDate.toString()] != null) return@forEach

        val progress = todo.recurrencePeriod(targetDate, weekStart)?.let { period ->
            val completed = executionsByTodo[todo.id].orEmpty().count { execution ->
                TodoState.fromStoredValue(execution.status) == TodoState.COMPLETED &&
                    LocalDate.parse(execution.logicalDate) in period.startDate..period.endDate
            }
            RecurrenceProgress(period, completed)
        }
        if (progress?.isAchieved == true) return@forEach

        val deadline = deadlineAt(targetDate, dayEndHour, todo.dueMinutes, now.zone)
        groupedItems.getOrPut(entity.categoryId) { mutableListOf() } += WidgetTodoItem(
            todoId = todo.id,
            definitionRevision = todo.definitionRevision,
            title = todo.title,
            logicalDate = targetDate.toString(),
            deadlineAt = deadline.toInstant().toEpochMilli(),
            deadlineLabel = deadlineLabel(
                deadline.toLocalDate().isAfter(targetDate),
                deadline.hour,
                deadline.minute,
            ),
            overdue = !now.isBefore(deadline),
            completedCount = progress?.completedCount,
            requiredCount = progress?.period?.requiredCount,
        )
        if (todo.recurrenceRule.usesHolidayData() &&
            !holidayState.isDefinitive(targetDate.year)
        ) {
            provisional = true
        }
    }

    val groups = groupedItems.mapNotNull { (categoryId, items) ->
        if (items.isEmpty()) return@mapNotNull null
        val category = categoryId?.let(categories::get)
        val groupDate = logicalDate(now, dayEndHour)
        WidgetCategoryGroup(
            categoryId = categoryId,
            categoryName = category?.name ?: uncategorizedName,
            colorIndex = category?.colorIndex ?: DEFAULT_UNCATEGORIZED_COLOR,
            iconName = category?.iconName ?: DEFAULT_UNCATEGORIZED_ICON,
            sortOrder = category?.sortOrder ?: -1,
            logicalDate = groupDate.toString(),
            logicalDateLabel = groupDate.takeIf { it != now.toLocalDate() }?.let(logicalDateLabel),
            items = items.sortedWith(
                compareBy<WidgetTodoItem>(WidgetTodoItem::deadlineAt)
                    .thenBy { item -> createdAtByTodo.getValue(item.todoId) }
                    .thenBy(WidgetTodoItem::todoId),
            ),
        )
    }.sortedWith(compareBy<WidgetCategoryGroup>(WidgetCategoryGroup::sortOrder).thenBy { it.categoryId ?: "" })

    val dayBoundary = logicalDayEnd(
        logicalDate(now, dayEndHour),
        dayEndHour,
        now.zone,
    ).toInstant().toEpochMilli()
    val nextCalendarDay = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
    val nextRefreshAt = (listOf(dayBoundary, nextCalendarDay) + groups.flatMap { group ->
        group.items.map(WidgetTodoItem::deadlineAt)
    }).filter { it > now.toInstant().toEpochMilli() }.minOrNull()
        ?: now.plusHours(1).toInstant().toEpochMilli()

    return WidgetDisplayModel(
        generatedAt = now.toInstant().toEpochMilli(),
        calendarDate = now.toLocalDate().toString(),
        totalCount = groups.sumOf { it.items.size },
        groups = groups,
        holidayDataProvisional = provisional,
        nextRefreshAt = nextRefreshAt,
    )
}

private const val DEFAULT_UNCATEGORIZED_COLOR = 15
private const val DEFAULT_UNCATEGORIZED_ICON = "Category"
