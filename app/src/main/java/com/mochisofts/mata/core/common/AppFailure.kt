package com.mochisofts.mata.core.common

import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

enum class FailureCategory {
    INPUT,
    BUSINESS_RULE,
    NOT_FOUND,
    CONFLICT,
    TEMPORARY_LOCAL,
    NETWORK,
    PERMISSION,
    DATA_INTEGRITY,
    UNEXPECTED,
}

enum class RecoveryAction {
    EDIT_INPUT,
    RETRY,
    REFRESH,
    OPEN_SETTINGS,
    RESTART_OR_RESTORE,
}

sealed interface AppFailure {
    val category: FailureCategory
    val retryable: Boolean
    val recoveryAction: RecoveryAction

    data class Validation(val error: ValidationError) : AppFailure {
        override val category: FailureCategory = error.failureCategory()
        override val retryable: Boolean = false
        override val recoveryAction: RecoveryAction = when (category) {
            FailureCategory.NOT_FOUND, FailureCategory.CONFLICT -> RecoveryAction.REFRESH
            else -> RecoveryAction.EDIT_INPUT
        }
    }

    data object Conflict : AppFailure {
        override val category = FailureCategory.CONFLICT
        override val retryable = false
        override val recoveryAction = RecoveryAction.REFRESH
    }

    data object TemporaryLocal : AppFailure {
        override val category = FailureCategory.TEMPORARY_LOCAL
        override val retryable = true
        override val recoveryAction = RecoveryAction.RETRY
    }

    data object Network : AppFailure {
        override val category = FailureCategory.NETWORK
        override val retryable = true
        override val recoveryAction = RecoveryAction.RETRY
    }

    data object PermissionRequired : AppFailure {
        override val category = FailureCategory.PERMISSION
        override val retryable = false
        override val recoveryAction = RecoveryAction.OPEN_SETTINGS
    }

    data object DataIntegrity : AppFailure {
        override val category = FailureCategory.DATA_INTEGRITY
        override val retryable = false
        override val recoveryAction = RecoveryAction.RESTART_OR_RESTORE
    }

    data object Unexpected : AppFailure {
        override val category = FailureCategory.UNEXPECTED
        override val retryable = true
        override val recoveryAction = RecoveryAction.RETRY
    }
}

/** Carries a classified failure across layer boundaries without exposing a technical message to UI. */
class AppFailureException(
    val failure: AppFailure,
    cause: Throwable? = null,
) : Exception(null, cause)

/**
 * Converts legacy Throwable-based repository results at the UI boundary.
 * Adapters should use [AppFailureException] when the context is needed to classify the failure.
 */
fun Throwable.toAppFailure(): AppFailure {
    val chain = causeChain()
    chain.filterIsInstance<CancellationException>().firstOrNull()?.let { throw it }
    chain.filterIsInstance<AppFailureException>().firstOrNull()?.let { return it.failure }
    chain.filterIsInstance<ValidationException>().firstOrNull()?.let {
        return AppFailure.Validation(it.error)
    }
    if (chain.any { it is SecurityException }) return AppFailure.PermissionRequired
    return AppFailure.Unexpected
}

private fun Throwable.causeChain(): List<Throwable> {
    val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    return buildList {
        var current: Throwable? = this@causeChain
        while (current != null && visited.add(current)) {
            add(current)
            current = current.cause
        }
    }
}

private fun ValidationError.failureCategory(): FailureCategory = when (this) {
    ValidationError.TODO_CATEGORY_NOT_FOUND,
    ValidationError.TODO_NOT_FOUND,
    ValidationError.HISTORY_RECORD_NOT_FOUND,
    -> FailureCategory.NOT_FOUND

    ValidationError.TODO_ALREADY_ACTED,
    ValidationError.TODO_REQUIRED_COUNT_REACHED,
    ValidationError.TODO_NOT_ACTIVE,
    ValidationError.TODO_ACTION_DATE_INVALID,
    ValidationError.TODO_ACTION_CANNOT_UNDO,
    ValidationError.HISTORY_COMPLETION_NOT_UNDOABLE,
    -> FailureCategory.BUSINESS_RULE

    else -> FailureCategory.INPUT
}
