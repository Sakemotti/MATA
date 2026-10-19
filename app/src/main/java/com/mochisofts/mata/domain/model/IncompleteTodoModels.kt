package com.mochisofts.mata.domain.model

import java.time.LocalDate

/** Acknowledges only occurrences shown when the action was requested, never later ones. */
data class IncompleteTodoSource(
    val todoId: String,
    val throughDate: LocalDate,
)

data class IncompleteTodoGroup(
    val todo: Todo,
    val category: Category?,
    val pastCount: Int,
    val oldestDate: LocalDate,
    val latestDate: LocalDate,
    val current: TodoOccurrence?,
    val source: IncompleteTodoSource,
) {
    val count: Int get() = pastCount + if (current != null) 1 else 0
}

data class IncompleteTodoHistory(
    val date: LocalDate,
    val periodStart: LocalDate? = null,
)

data class IncompleteTodoSummary(
    val todoId: String,
    val count: Int,
    val oldestDate: LocalDate,
    val latestDate: LocalDate,
)

/** Groups occurrences, not definitions or copies; a dismissed occurrence cannot reappear at midnight. */
fun buildIncompleteTodoGroups(
    todos: List<Todo>,
    categories: List<Category>,
    summaries: List<IncompleteTodoSummary>,
    currentOccurrences: List<TodoOccurrence>,
    dismissedThroughDates: Map<String, LocalDate>,
): List<IncompleteTodoGroup> {
    val categoryById = categories.associateBy(Category::id)
    val summariesById = summaries.associateBy(IncompleteTodoSummary::todoId)
    val currentById = currentOccurrences.filter { it.state == TodoState.PENDING }
        .associateBy { it.todo.id }
    return todos.filter { it.archivedAt == null }.mapNotNull { todo ->
        val past = summariesById[todo.id]
        val current = currentById[todo.id]?.takeIf { occurrence ->
            val key = occurrence.progress?.period?.endDate ?: occurrence.scheduledLogicalDate
            dismissedThroughDates[todo.id]?.let { key > it } ?: true
        }
        if (past == null && current == null) return@mapNotNull null
        val dates = listOfNotNull(past?.oldestDate, past?.latestDate, current?.scheduledLogicalDate)
        val through = maxOf(
            past?.latestDate ?: LocalDate.MIN,
            current?.progress?.period?.endDate ?: current?.scheduledLogicalDate ?: LocalDate.MIN,
        )
        IncompleteTodoGroup(
            todo, todo.categoryId?.let(categoryById::get), past?.count ?: 0,
            dates.min(), dates.max(), current, IncompleteTodoSource(todo.id, through),
        )
    }.sortedWith(compareBy<IncompleteTodoGroup> { it.category?.sortOrder ?: -1 }
        .thenBy { it.oldestDate }.thenBy { it.todo.createdAt }.thenBy { it.todo.id })
}

/** Editable new registration, deliberately independent of the original definition. */
data class IncompleteTodoRegistration(
    val title: String,
    val description: String,
    val categoryId: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val recurrenceRule: RecurrenceRule,
    val dueMinutes: Int?,
    val notifications: List<TodoNotification>,
    val dueDate: LocalDate?,
    val carryOverEnabled: Boolean,
)
