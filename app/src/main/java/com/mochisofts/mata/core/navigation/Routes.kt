package com.mochisofts.mata.core.navigation

import androidx.annotation.StringRes
import com.mochisofts.mata.R
import kotlinx.serialization.Serializable

const val UNCATEGORIZED_CATEGORY_KEY = "__uncategorized__"
const val TODO_EDITOR_RESULT_KEY = "todo_editor_result"
const val TODO_EDITOR_CATEGORY_RESULT_KEY = "todo_editor_category_result"

@StringRes
fun todoEditorSavedMessageRes(isNew: Boolean): Int =
    if (isNew) R.string.message_todo_added else R.string.message_todo_updated

@Serializable
data class TodoListRoute(
    val selectedDate: String? = null,
    val showTodoNotFound: Boolean = false,
)

@Serializable
data class CategoryTodoListRoute(
    val selectedCategoryKey: String? = null,
)

@Serializable
data class TodoEditorRoute(
    val todoId: String? = null,
    val initialDate: String? = null,
)

@Serializable
data object CategoryListRoute

@Serializable
data class CategoryEditorRoute(
    val categoryId: String? = null,
    val selectForTodoEditor: Boolean = false,
)

@Serializable
data object SettingsRoute

@Serializable
data object OpenSourceLicensesRoute

@Serializable
data object CalendarHistoryRoute

@Serializable
data object ArchivedTodoListRoute

@Serializable
data class ArchivedTodoDetailRoute(val todoId: String)
