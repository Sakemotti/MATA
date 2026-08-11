package com.mochisofts.mata.core.common

enum class ValidationError {
    CATEGORY_NAME_REQUIRED,
    CATEGORY_NAME_TOO_LONG,
    CATEGORY_COLOR_INVALID,
    CATEGORY_END_HOUR_INVALID,
    CATEGORY_NAME_DUPLICATE,
    TODO_TITLE_REQUIRED,
    TODO_TITLE_TOO_LONG,
    TODO_DESCRIPTION_TOO_LONG,
    TODO_DUE_TIME_INVALID,
    TODO_CATEGORY_NOT_FOUND,
    TODO_DATE_IN_PAST,
    TODO_NOT_FOUND,
}

class ValidationException(
    val error: ValidationError,
) : IllegalArgumentException()
