package com.mochisofts.mata.widget

import android.appwidget.AppWidgetManager
import com.mochisofts.mata.domain.model.RecurrencePeriod
import com.mochisofts.mata.domain.model.RecurrenceProgress
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import com.mochisofts.mata.domain.model.TodoState
import com.mochisofts.mata.domain.model.WidgetDisplayModel
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTodoActionRequestTest {
    private val request = WidgetTodoActionRequest(
        todoId = "todo-one",
        logicalDate = LocalDate.of(2026, 8, 26),
        expectedRevision = 2,
        appWidgetId = 42,
        snapshotVersion = WidgetDisplayModel.CURRENT_VERSION,
    )

    @Test
    fun structurallyValidRequestRequiresCurrentSnapshotAndWidgetInstance() {
        assertTrue(request.isStructurallyValid())
        assertFalse(request.copy(todoId = "").isStructurallyValid())
        assertFalse(request.copy(expectedRevision = 0).isStructurallyValid())
        assertFalse(
            request.copy(appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID).isStructurallyValid(),
        )
        assertFalse(request.copy(snapshotVersion = -1).isStructurallyValid())
    }

    @Test
    fun currentOccurrenceMustRemainPendingForTheRequestedLogicalDate() {
        val occurrence = occurrence(revision = 2, archivedAt = null)

        assertTrue(occurrence.isAvailableFor(request))
        assertTrue(
            occurrence.copy(todo = occurrence.todo.copy(definitionRevision = 3))
                .isAvailableFor(request),
        )
        assertFalse(
            occurrence.copy(todo = occurrence.todo.copy(definitionRevision = 1))
                .isAvailableFor(request),
        )
        assertFalse(
            occurrence.copy(todo = occurrence.todo.copy(archivedAt = 1L))
                .isAvailableFor(request),
        )
        assertFalse(
            occurrence.copy(logicalDate = request.logicalDate.plusDays(1)).isAvailableFor(request),
        )
        assertFalse(occurrence.copy(state = TodoState.COMPLETED).isAvailableFor(request))
        assertFalse(occurrence.copy(state = TodoState.SKIPPED).isAvailableFor(request))
        assertFalse(
            occurrence.copy(
                progress = RecurrenceProgress(
                    period = RecurrencePeriod(
                        startDate = request.logicalDate,
                        endDate = request.logicalDate.plusDays(6),
                        requiredCount = 1,
                    ),
                    completedCount = 1,
                ),
            ).isAvailableFor(request),
        )
    }

    private fun occurrence(revision: Int, archivedAt: Long?) = TodoOccurrence(
        todo = todo(revision, archivedAt),
        category = null,
        logicalDate = request.logicalDate,
        state = TodoState.PENDING,
    )

    private fun todo(revision: Int, archivedAt: Long?) = Todo(
        id = "todo-one",
        title = "朝のストレッチ",
        description = "",
        categoryId = null,
        startDate = LocalDate.of(2026, 8, 1),
        endDate = null,
        recurrenceRule = RecurrenceRule.daily(),
        dueMinutes = null,
        definitionRevision = revision,
        archivedAt = archivedAt,
        createdAt = 1L,
    )
}
