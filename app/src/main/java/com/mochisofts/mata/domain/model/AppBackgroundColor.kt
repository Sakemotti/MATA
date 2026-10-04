package com.mochisofts.mata.domain.model

enum class AppBackgroundColor(val code: String) {
    DEFAULT("default"),
    IVORY("ivory"),
    LIGHT_GREEN("light_green"),
    LIGHT_BLUE("light_blue"),
    LIGHT_PINK("light_pink"),
    LIGHT_PURPLE("light_purple"),
    LIGHT_GRAY("light_gray");

    companion object {
        fun fromStoredValue(value: String?): AppBackgroundColor =
            entries.firstOrNull { it.code == value } ?: DEFAULT
    }
}
