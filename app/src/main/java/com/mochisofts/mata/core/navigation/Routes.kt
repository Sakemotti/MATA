package com.mochisofts.mata.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data class TodoListRoute(
    val selectedDate: String? = null,
    val initialMode: String? = null,
    val selectedCategoryKey: String? = null,
)

@Serializable
data class TodoEditorRoute(val todoId: String? = null)

@Serializable
data object CategoryListRoute

@Serializable
data class CategoryEditorRoute(val categoryId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data object CalendarHistoryRoute

@Serializable
data object ArchivedTodoListRoute

@Serializable
data class ArchivedTodoDetailRoute(val todoId: String)

@Serializable
data class PlaceholderRoute(val destination: String)
