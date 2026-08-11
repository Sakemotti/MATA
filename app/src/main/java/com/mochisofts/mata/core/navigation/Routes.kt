package com.mochisofts.mata.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data class TodoListRoute(val selectedDate: String? = null)

@Serializable
data class TodoEditorRoute(val todoId: String? = null)

@Serializable
data object CategoryListRoute

@Serializable
data class CategoryEditorRoute(val categoryId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data class PlaceholderRoute(val destination: String)
