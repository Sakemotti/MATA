package com.mochisofts.mata.data.widget

import com.mochisofts.mata.data.local.CategoryEntity
import com.mochisofts.mata.data.local.TodoEntity
import com.mochisofts.mata.data.local.TodoExecutionEntity
import com.mochisofts.mata.data.repository.RecurrenceRuleJson
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.HolidayYearState
import com.mochisofts.mata.domain.model.HolidayYearStatus
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDisplayModelTest {
    private val now = ZonedDateTime.of(2026, 8, 18, 2, 30, 0, 0, ZoneId.of("Asia/Tokyo"))

    @Test
    fun groupsByEachLogicalDate_sortsAndOmitsActedTodos() {
        val work = category("work", "仕事", endHour = 4, sortOrder = 0)
        val done = todo("done", "完了済み", work.id, dueMinutes = 60)
        val source = WidgetSourceData(
            todos = listOf(
                todo("uncategorized", "カテゴリなし", null, dueMinutes = null, createdAt = 1),
                todo("late", "日次締め", work.id, dueMinutes = null, createdAt = 3),
                todo("early", "早い期限", work.id, dueMinutes = 120, createdAt = 2),
                done,
            ),
            categories = listOf(work),
            executions = listOf(execution(done.id, "2026-08-17")),
            holidays = emptySet(),
        )

        val model = build(source)

        assertEquals(listOf(null, work.id), model.groups.map { it.categoryId })
        assertEquals("2026-08-18", model.groups[0].logicalDate)
        assertEquals("2026-08-17", model.groups[1].logicalDate)
        assertEquals("8/17分", model.groups[1].logicalDateLabel)
        assertEquals(listOf("early", "late"), model.groups[1].items.map { it.todoId })
        assertTrue(model.groups[1].items.first().overdue)
        assertFalse(model.groups[1].items.last().overdue)
        assertEquals(3, model.totalCount)
    }

    @Test
    fun countBasedTodoShowsProgressThenDisappearsWhenTargetReached() {
        val countTodo = todo(
            id = "weekly",
            title = "週2回",
            categoryId = null,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 2),
        )
        val once = execution(countTodo.id, "2026-08-17")

        val inProgress = build(
            WidgetSourceData(listOf(countTodo), emptyList(), listOf(once), emptySet()),
        )
        assertEquals(1, inProgress.totalCount)
        assertEquals(1, inProgress.groups.single().items.single().completedCount)
        assertEquals(2, inProgress.groups.single().items.single().requiredCount)

        val achieved = build(
            WidgetSourceData(
                listOf(countTodo),
                emptyList(),
                listOf(once, execution(countTodo.id, "2026-08-18", operationId = "op-2")),
                emptySet(),
            ),
            currentTime = now.plusDays(1),
        )
        assertEquals(0, achieved.totalCount)
        assertTrue(achieved.groups.isEmpty())
    }

    @Test
    fun weekdayTodoUsesHolidayCacheAndMarksUnknownYearProvisional() {
        val weekday = todo(
            id = "weekday",
            title = "平日のTODO",
            categoryId = null,
            recurrenceRule = RecurrenceRule(RecurrenceType.WEEKDAYS),
        )
        val unknown = build(WidgetSourceData(listOf(weekday), emptyList(), emptyList(), emptySet()))
        assertEquals(1, unknown.totalCount)
        assertTrue(unknown.holidayDataProvisional)

        val holidayDate = LocalDate.of(2026, 8, 18)
        val definitive = HolidaySnapshot(
            namesByDate = mapOf(holidayDate to "休日"),
            yearStates = mapOf(
                2026 to HolidayYearState(
                    year = 2026,
                    status = HolidayYearStatus.AVAILABLE_CURRENT,
                    available = true,
                ),
            ),
        )
        val holiday = build(
            source = WidgetSourceData(listOf(weekday), emptyList(), emptyList(), setOf(holidayDate)),
            holidayState = definitive,
        )
        assertEquals(0, holiday.totalCount)
        assertFalse(holiday.holidayDataProvisional)
    }

    private fun build(
        source: WidgetSourceData,
        holidayState: HolidaySnapshot = HolidaySnapshot(),
        currentTime: ZonedDateTime = now,
    ) = buildWidgetDisplayModel(
        now = currentTime,
        source = source,
        uncategorizedEndHour = 0,
        weekStart = DayOfWeek.MONDAY,
        holidayState = holidayState,
        uncategorizedName = "カテゴリ未設定",
        logicalDateLabel = { "${it.monthValue}/${it.dayOfMonth}分" },
        deadlineLabel = { nextDay, hour, minute ->
            "${if (nextDay) "翌日 " else ""}$hour:${minute.toString().padStart(2, '0')}"
        },
    )

    private fun category(id: String, name: String, endHour: Int, sortOrder: Int) = CategoryEntity(
        id = id,
        name = name,
        normalizedName = name,
        colorIndex = 4,
        iconName = "Work",
        endHour = endHour,
        sortOrder = sortOrder,
        createdAt = 1,
    )

    private fun todo(
        id: String,
        title: String,
        categoryId: String?,
        dueMinutes: Int? = null,
        createdAt: Long = 1,
        recurrenceRule: RecurrenceRule = RecurrenceRule.daily(),
    ): TodoEntity {
        val encoded = RecurrenceRuleJson.encode(recurrenceRule)
        return TodoEntity(
            id = id,
            title = title,
            description = "",
            categoryId = categoryId,
            startDate = "2026-01-01",
            endDate = null,
            recurrenceType = encoded.typeCode,
            repeatParamsVersion = encoded.paramsVersion,
            repeatParamsJson = encoded.paramsJson,
            dueMinutes = dueMinutes,
            definitionRevision = 1,
            createdAt = createdAt,
            updatedAt = createdAt,
            archivedAt = null,
        )
    }

    private fun execution(
        todoId: String,
        logicalDate: String,
        operationId: String = "op-$todoId-$logicalDate",
    ) = TodoExecutionEntity(
        id = "execution-$operationId",
        operationId = operationId,
        todoId = todoId,
        logicalDate = logicalDate,
        status = "completed",
        actedAt = 1,
        finalizedAt = 1,
        definitionRevision = 1,
        snapshotVersion = 1,
        snapshotJson = "{}",
    )
}
