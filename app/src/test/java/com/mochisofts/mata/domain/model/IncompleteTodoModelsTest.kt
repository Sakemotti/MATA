package com.mochisofts.mata.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncompleteTodoModelsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val todo = Todo("a", "日課", "", null, today.minusDays(10), null,
        RecurrenceRule.daily(), null, 1, null, 0)
    private fun pending(date: LocalDate = today) = TodoOccurrence(todo, null, date, TodoState.PENDING)
    private fun summary(id: String = "a") = IncompleteTodoSummary(id, 3, today.minusDays(3), today.minusDays(1))

    @Test fun groupsDailyHistoryAndCurrentOccurrenceWithoutDuplicatingDefinitions() {
        val group = buildIncompleteTodoGroups(listOf(todo), emptyList(), listOf(summary()), listOf(pending()), emptyMap()).single()
        assertEquals(4, group.count)
        assertEquals(3, group.pastCount)
        assertEquals(today.minusDays(3), group.oldestDate)
        assertEquals(today, group.source.throughDate)
    }

    @Test fun expiredDefinitionStillShowsPastMissesAndArchivedDefinitionDoesNot() {
        val expired = todo.copy(endDate = today.minusDays(1))
        assertEquals(1, buildIncompleteTodoGroups(listOf(expired), emptyList(), listOf(summary()), emptyList(), emptyMap()).size)
        assertTrue(buildIncompleteTodoGroups(listOf(expired.copy(archivedAt = 1)), emptyList(), listOf(summary()), emptyList(), emptyMap()).isEmpty())
    }

    @Test fun dismissedCurrentOccurrenceStaysHiddenButNewOccurrenceReturns() {
        assertTrue(buildIncompleteTodoGroups(listOf(todo), emptyList(), emptyList(), listOf(pending()), mapOf("a" to today)).isEmpty())
        val next = pending(today.plusDays(1))
        assertEquals(1, buildIncompleteTodoGroups(listOf(todo), emptyList(), emptyList(), listOf(next), mapOf("a" to today)).size)
    }

    @Test fun countPeriodUsesPeriodEndIdentityInsteadOfReappearingEveryDay() {
        val period = RecurrencePeriod(today.minusDays(1), today.plusDays(5), 3)
        val occurrence = pending().copy(progress = RecurrenceProgress(period, 1))
        val group = buildIncompleteTodoGroups(listOf(todo), emptyList(), emptyList(), listOf(occurrence), emptyMap()).single()
        assertEquals(period.endDate, group.source.throughDate)
        assertTrue(buildIncompleteTodoGroups(listOf(todo), emptyList(), emptyList(), listOf(occurrence.copy(logicalDate = today.plusDays(1))), mapOf("a" to period.endDate)).isEmpty())
    }

    @Test fun completedAndSkippedCurrentOccurrencesAreExcluded() {
        listOf(TodoState.COMPLETED, TodoState.SKIPPED, TodoState.MISSED).forEach { state ->
            assertTrue(buildIncompleteTodoGroups(listOf(todo), emptyList(), emptyList(), listOf(pending().copy(state = state)), emptyMap()).isEmpty())
        }
    }

    @Test fun categoryOrderAndOldestMissProvideStableOrdering() {
        val categories = listOf(Category("work", "仕事", 0, "Work", 1), Category("home", "日常", 0, "Home", 0))
        val todos = listOf(todo.copy(id = "work", categoryId = "work"), todo.copy(id = "home", categoryId = "home"), todo)
        val groups = buildIncompleteTodoGroups(todos, categories, listOf(summary("work"), summary("home"), summary()), emptyList(), emptyMap())
        assertEquals(listOf("a", "home", "work"), groups.map { it.todo.id })
    }
}
