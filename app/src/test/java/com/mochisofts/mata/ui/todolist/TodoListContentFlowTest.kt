package com.mochisofts.mata.ui.todolist

import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.Todo
import com.mochisofts.mata.domain.model.TodoOccurrence
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodoListContentFlowTest {
    @Test
    fun dateChangeWaitsForMatchingOccurrencesBeforePublishingContent() = runTest {
        val firstDate = LocalDate.of(2026, 8, 26)
        val nextDate = firstDate.plusDays(1)
        val selectedDate = MutableStateFlow(firstDate)
        val firstOccurrences = MutableStateFlow(emptyList<TodoOccurrence>())
        val nextOccurrences = MutableSharedFlow<List<TodoOccurrence>>()
        val emissions = mutableListOf<TodoListContent>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeTodoListContent(
                selectedDate = selectedDate,
                occurrencesForDate = { date ->
                    when (date) {
                        firstDate -> firstOccurrences
                        nextDate -> nextOccurrences
                        else -> error("Unexpected date: $date")
                    }
                },
                todos = MutableStateFlow(emptyList<Todo>()),
                holidaySnapshot = MutableStateFlow(HolidaySnapshot()),
            ).collect(emissions::add)
        }

        assertEquals(listOf(firstDate), emissions.map(TodoListContent::date))

        selectedDate.value = nextDate
        runCurrent()

        assertEquals(listOf(firstDate), emissions.map(TodoListContent::date))

        nextOccurrences.emit(emptyList())
        runCurrent()

        assertEquals(listOf(firstDate, nextDate), emissions.map(TodoListContent::date))
    }
}
