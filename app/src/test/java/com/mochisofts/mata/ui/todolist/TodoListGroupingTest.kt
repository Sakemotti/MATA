package com.mochisofts.mata.ui.todolist

import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoListGroupingTest {
    @Test
    fun groupsUncategorizedFirstThenUsesCategorySortOrder() {
        val later = category(id = "later", sortOrder = 10)
        val earlier = category(id = "earlier", sortOrder = 2)

        val groups = buildTodoOccurrenceGroups(
            occurrences = listOf(
                occurrence(todo("later-todo", later.id), later),
                occurrence(todo("uncategorized", null), null),
                occurrence(todo("earlier-todo", earlier.id), earlier),
            ),
            dayEndHour = 0,
        )

        assertEquals(listOf(null, earlier.id, later.id), groups.map { it.category?.id })
    }

    @Test
    fun sortsEachCategoryByActualDeadlineThenCreationAndId() {
        val category = category(id = "night", sortOrder = 0)
        val groups = buildTodoOccurrenceGroups(
            occurrences = listOf(
                occurrence(todo("no-deadline", category.id, dueMinutes = null, createdAt = 1), category),
                occurrence(todo("after-midnight", category.id, dueMinutes = 120, createdAt = 1), category),
                occurrence(todo("same-newer", category.id, dueMinutes = 1_380, createdAt = 2), category),
                occurrence(todo("same-older", category.id, dueMinutes = 1_380, createdAt = 1), category),
            ),
            dayEndHour = 4,
        )

        assertEquals(
            listOf("same-older", "same-newer", "after-midnight", "no-deadline"),
            groups.single().occurrences.map { it.todo.id },
        )
    }

    @Test
    fun usesConfiguredGlobalEndHourForAllDeadlines() {
        val groups = buildTodoOccurrenceGroups(
            occurrences = listOf(
                occurrence(todo("no-deadline", null, dueMinutes = null), null),
                occurrence(todo("after-midnight", null, dueMinutes = 60), null),
                occurrence(todo("morning", null, dueMinutes = 8 * 60), null),
            ),
            dayEndHour = 6,
        )

        assertEquals(
            listOf("morning", "after-midnight", "no-deadline"),
            groups.single().occurrences.map { it.todo.id },
        )
    }

    private fun category(id: String, sortOrder: Int) = Category(
        id = id,
        name = id,
        colorIndex = 0,
        iconName = "Category",
        sortOrder = sortOrder,
    )

    private fun todo(
        id: String,
        categoryId: String?,
        dueMinutes: Int? = 12 * 60,
        createdAt: Long = 1,
    ) = Todo(
        id = id,
        title = id,
        description = "",
        categoryId = categoryId,
        startDate = DATE,
        endDate = null,
        recurrenceRule = RecurrenceRule.daily(),
        dueMinutes = dueMinutes,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = createdAt,
    )

    private fun occurrence(todo: Todo, category: Category?) = TodoOccurrence(
        todo = todo,
        category = category,
        logicalDate = DATE,
        state = TodoState.PENDING,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 8, 25)
    }
}
