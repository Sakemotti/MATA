package com.mochisofts.mata.domain.model

enum class AppTheme(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStoredValue(value: String?): AppTheme =
            entries.firstOrNull { it.code == value } ?: SYSTEM
    }
}
