package com.mochisofts.mata.app

import android.content.Intent
import android.os.Bundle
import com.mochisofts.mata.core.navigation.CategoryTodoListRoute
import com.mochisofts.mata.core.navigation.TodoListRoute
import java.time.LocalDate
import java.util.UUID

internal sealed interface ExternalNavigation {
    data object Fallback : ExternalNavigation

    data class Notification(
        val todoId: String,
        val logicalDate: LocalDate,
        val candidateKey: String,
    ) : ExternalNavigation

    data class Widget(
        val selectedDate: LocalDate,
        val mode: String,
        val categoryKey: String?,
        val todoId: String?,
    ) : ExternalNavigation
}

internal data class ExternalNavigationResolution(
    val route: Any,
)

internal fun resolvedWidgetRoute(
    request: ExternalNavigation.Widget,
    selectedCategoryKey: String?,
): Any = if (request.mode == MainActivity.WIDGET_MODE_CATEGORY) {
    CategoryTodoListRoute(selectedCategoryKey = selectedCategoryKey)
} else {
    TodoListRoute(selectedDate = request.selectedDate.toString())
}

internal fun parseExternalNavigation(
    action: String?,
    todoId: String? = null,
    logicalDate: String? = null,
    candidateKey: String? = null,
    widgetDate: String? = null,
    widgetMode: String? = null,
    widgetCategoryKey: String? = null,
): ExternalNavigation? = when (action) {
    MainActivity.ACTION_OPEN_NOTIFICATION -> run {
        ExternalNavigation.Notification(
            todoId = todoId?.takeIf(String::isCanonicalUuid)
                ?: return@run ExternalNavigation.Fallback,
            logicalDate = logicalDate.toSupportedDate()
                ?: return@run ExternalNavigation.Fallback,
            candidateKey = candidateKey?.takeIf(String::isSafeCandidateKey)
                ?: return@run ExternalNavigation.Fallback,
        )
    }

    MainActivity.ACTION_OPEN_WIDGET -> run {
        val targetTodoId = todoId?.takeIf(String::isCanonicalUuid)
            ?: if (todoId == null) null else return@run ExternalNavigation.Fallback
        val mode = when (widgetMode) {
            MainActivity.WIDGET_MODE_DATE -> MainActivity.WIDGET_MODE_DATE
            MainActivity.WIDGET_MODE_CATEGORY -> MainActivity.WIDGET_MODE_CATEGORY
            else -> return@run ExternalNavigation.Fallback
        }
        val categoryKey = when (mode) {
            MainActivity.WIDGET_MODE_CATEGORY -> widgetCategoryKey?.takeIf { key ->
                key == MainActivity.WIDGET_UNCATEGORIZED_KEY || key.isCanonicalUuid()
            } ?: return@run ExternalNavigation.Fallback
            else -> null
        }
        ExternalNavigation.Widget(
            selectedDate = widgetDate.toSupportedDate()
                ?: return@run ExternalNavigation.Fallback,
            mode = mode,
            categoryKey = categoryKey,
            todoId = targetTodoId,
        )
    }

    else -> null
}

internal fun Intent.toExternalNavigation(): ExternalNavigation? = parseExternalNavigation(
    action = action,
    todoId = getStringExtra(MainActivity.EXTRA_TODO_ID),
    logicalDate = getStringExtra(MainActivity.EXTRA_LOGICAL_DATE),
    candidateKey = getStringExtra(MainActivity.EXTRA_CANDIDATE_KEY),
    widgetDate = getStringExtra(MainActivity.EXTRA_WIDGET_DATE),
    widgetMode = getStringExtra(MainActivity.EXTRA_WIDGET_MODE),
    widgetCategoryKey = getStringExtra(MainActivity.EXTRA_WIDGET_CATEGORY_KEY),
)

internal fun resolveExternalNavigationOnCreate(
    restoringActivity: Boolean,
    restoredPending: ExternalNavigation?,
    intentRequest: ExternalNavigation?,
): ExternalNavigation? = if (restoringActivity) restoredPending else intentRequest

internal fun Bundle.putPendingExternalNavigation(request: ExternalNavigation?) {
    remove(KEY_EXTERNAL_TYPE)
    remove(KEY_EXTERNAL_TODO_ID)
    remove(KEY_EXTERNAL_DATE)
    remove(KEY_EXTERNAL_CANDIDATE)
    remove(KEY_EXTERNAL_MODE)
    remove(KEY_EXTERNAL_CATEGORY)
    when (request) {
        ExternalNavigation.Fallback -> putString(KEY_EXTERNAL_TYPE, TYPE_FALLBACK)
        is ExternalNavigation.Notification -> {
            putString(KEY_EXTERNAL_TYPE, TYPE_NOTIFICATION)
            putString(KEY_EXTERNAL_TODO_ID, request.todoId)
            putString(KEY_EXTERNAL_DATE, request.logicalDate.toString())
            putString(KEY_EXTERNAL_CANDIDATE, request.candidateKey)
        }
        is ExternalNavigation.Widget -> {
            putString(KEY_EXTERNAL_TYPE, TYPE_WIDGET)
            putString(KEY_EXTERNAL_DATE, request.selectedDate.toString())
            putString(KEY_EXTERNAL_MODE, request.mode)
            request.todoId?.let { putString(KEY_EXTERNAL_TODO_ID, it) }
            request.categoryKey?.let { putString(KEY_EXTERNAL_CATEGORY, it) }
        }
        null -> Unit
    }
}

internal fun Bundle.pendingExternalNavigation(): ExternalNavigation? = when (
    getString(KEY_EXTERNAL_TYPE)
) {
    TYPE_FALLBACK -> ExternalNavigation.Fallback
    TYPE_NOTIFICATION -> parseExternalNavigation(
        action = MainActivity.ACTION_OPEN_NOTIFICATION,
        todoId = getString(KEY_EXTERNAL_TODO_ID),
        logicalDate = getString(KEY_EXTERNAL_DATE),
        candidateKey = getString(KEY_EXTERNAL_CANDIDATE),
    )
    TYPE_WIDGET -> parseExternalNavigation(
        action = MainActivity.ACTION_OPEN_WIDGET,
        widgetDate = getString(KEY_EXTERNAL_DATE),
        widgetMode = getString(KEY_EXTERNAL_MODE),
        widgetCategoryKey = getString(KEY_EXTERNAL_CATEGORY),
        todoId = getString(KEY_EXTERNAL_TODO_ID),
    )
    else -> null
}

private fun String?.toSupportedDate(): LocalDate? = this?.let { value ->
    runCatching { LocalDate.parse(value) }
        .getOrNull()
        ?.takeIf { date -> date.year in MIN_SUPPORTED_YEAR..MAX_SUPPORTED_YEAR }
}

private fun String.isCanonicalUuid(): Boolean = length == UUID_TEXT_LENGTH &&
    runCatching { UUID.fromString(this).toString().equals(this, ignoreCase = true) }.getOrDefault(false)

private fun String.isSafeCandidateKey(): Boolean =
    length in 1..MAX_CANDIDATE_KEY_LENGTH && none(Char::isISOControl)

private const val MIN_SUPPORTED_YEAR = 1
private const val MAX_SUPPORTED_YEAR = 9_999
private const val UUID_TEXT_LENGTH = 36
private const val MAX_CANDIDATE_KEY_LENGTH = 512
private const val KEY_EXTERNAL_TYPE = "pending_external_type"
private const val KEY_EXTERNAL_TODO_ID = "pending_external_todo_id"
private const val KEY_EXTERNAL_DATE = "pending_external_date"
private const val KEY_EXTERNAL_CANDIDATE = "pending_external_candidate"
private const val KEY_EXTERNAL_MODE = "pending_external_mode"
private const val KEY_EXTERNAL_CATEGORY = "pending_external_category"
private const val TYPE_NOTIFICATION = "notification"
private const val TYPE_WIDGET = "widget"
private const val TYPE_FALLBACK = "fallback"
