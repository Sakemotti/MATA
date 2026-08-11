package com.mochisofts.mata.ui.common

import androidx.annotation.StringRes
import com.mochisofts.mata.R
import com.mochisofts.mata.core.common.ValidationError
import com.mochisofts.mata.core.common.ValidationException

@StringRes
fun Throwable.toUserMessageRes(@StringRes fallback: Int): Int =
    when ((this as? ValidationException)?.error) {
        ValidationError.CATEGORY_NAME_REQUIRED -> R.string.error_category_name_required
        ValidationError.CATEGORY_NAME_TOO_LONG -> R.string.error_category_name_too_long
        ValidationError.CATEGORY_COLOR_INVALID -> R.string.error_category_color_invalid
        ValidationError.CATEGORY_END_HOUR_INVALID -> R.string.error_category_end_hour_invalid
        ValidationError.CATEGORY_NAME_DUPLICATE -> R.string.error_category_name_duplicate
        ValidationError.TODO_TITLE_REQUIRED -> R.string.error_todo_title_required
        ValidationError.TODO_TITLE_TOO_LONG -> R.string.error_todo_title_too_long
        ValidationError.TODO_DESCRIPTION_TOO_LONG -> R.string.error_todo_description_too_long
        ValidationError.TODO_DUE_TIME_INVALID -> R.string.error_todo_due_time_invalid
        ValidationError.TODO_CATEGORY_NOT_FOUND -> R.string.error_todo_category_not_found
        ValidationError.TODO_DATE_IN_PAST -> R.string.error_todo_date_in_past
        ValidationError.TODO_END_DATE_BEFORE_START -> R.string.error_todo_end_date_before_start
        ValidationError.TODO_RECURRENCE_RULE_INVALID -> R.string.error_todo_recurrence_rule_invalid
        ValidationError.TODO_NOTIFICATION_TOO_MANY -> R.string.error_todo_notification_too_many
        ValidationError.TODO_NOTIFICATION_AMOUNT_INVALID -> R.string.error_todo_notification_amount_invalid
        ValidationError.TODO_NOTIFICATION_DUPLICATE -> R.string.error_todo_notification_duplicate
        ValidationError.TODO_NOTIFICATION_AFTER_REQUIRES_DEADLINE ->
            R.string.error_todo_notification_after_requires_deadline
        ValidationError.TODO_NOTIFICATION_AFTER_DAY_END -> R.string.error_todo_notification_after_day_end
        ValidationError.TODO_ALREADY_ACTED -> R.string.error_todo_already_acted
        ValidationError.TODO_REQUIRED_COUNT_REACHED -> R.string.error_todo_required_count_reached
        ValidationError.TODO_NOT_FOUND -> R.string.error_todo_not_found
        ValidationError.TODO_NOT_ACTIVE -> R.string.error_todo_not_active
        ValidationError.TODO_ACTION_DATE_INVALID -> R.string.error_todo_action_date_invalid
        ValidationError.TODO_ACTION_CANNOT_UNDO -> R.string.error_todo_action_cannot_undo
        null -> fallback
    }
