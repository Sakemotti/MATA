package com.mochisofts.mata.ui.categorytodolist

import com.mochisofts.mata.domain.model.Category
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryTodoListViewModelTest {
    @Test
    fun selectedCategoryShowsEveryTodayStateWithoutFiltering() {
        val category = category(id = "category", sortOrder = 1)
        val pending = todo(id = "pending", categoryId = category.id, createdAt = 3)
        val completed = todo(id = "completed", categoryId = category.id, createdAt = 1)
        val skipped = todo(id = "skipped", categoryId = category.id, createdAt = 2)
        val other = todo(id = "other", categoryId = "other-category", createdAt = 0)
        val date = LocalDate.of(2026, 8, 25)

        val state = buildCategoryTodoListUiState(
            categories = listOf(category(id = "other-category", sortOrder = 0), category),
            todos = listOf(pending, completed, skipped, other),
            todayOccurrences = listOf(
                occurrence(pending, TodoState.PENDING, date),
                occurrence(completed, TodoState.COMPLETED, date),
                occurrence(skipped, TodoState.SKIPPED, date),
            ),
            requestedCategoryId = category.id,
        )

        assertFalse(state.isLoading)
        assertEquals(category.id, state.selectedCategoryId)
        assertEquals(listOf("completed", "skipped", "pending"), state.items.map { it.todo.id })
        assertEquals(
            listOf(TodoState.COMPLETED, TodoState.SKIPPED, TodoState.PENDING),
            state.items.map { it.todayState },
        )
    }

    @Test
    fun deletedSelectionFallsBackToUncategorized() {
        val uncategorized = todo(id = "uncategorized", categoryId = null, createdAt = 1)
        val categorized = todo(id = "categorized", categoryId = "available", createdAt = 2)

        val state = buildCategoryTodoListUiState(
            categories = listOf(category(id = "available", sortOrder = 0)),
            todos = listOf(uncategorized, categorized),
            todayOccurrences = emptyList(),
            requestedCategoryId = "deleted",
        )

        assertNull(state.selectedCategoryId)
        assertEquals(listOf("uncategorized"), state.items.map { it.todo.id })
        assertNull(state.items.single().todayState)
    }

    private fun category(id: String, sortOrder: Int) = Category(
        id = id,
        name = id,
        colorIndex = 0,
        iconName = "Category",
        endHour = 0,
        sortOrder = sortOrder,
    )

    private fun todo(id: String, categoryId: String?, createdAt: Long) = Todo(
        id = id,
        title = id,
        description = "",
        categoryId = categoryId,
        startDate = LocalDate.of(2026, 1, 1),
        endDate = null,
        recurrenceRule = RecurrenceRule.daily(),
        dueMinutes = null,
        definitionRevision = 1,
        archivedAt = null,
        createdAt = createdAt,
    )

    private fun occurrence(todo: Todo, state: TodoState, date: LocalDate) = TodoOccurrence(
        todo = todo,
        category = null,
        logicalDate = date,
        state = state,
    )
}
