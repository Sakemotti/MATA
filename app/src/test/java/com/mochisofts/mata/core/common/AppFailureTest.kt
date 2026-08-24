package com.mochisofts.mata.core.common

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFailureTest {
    @Test
    fun validationFailuresKeepTheirSafeDomainReason() {
        val failure = ValidationException(ValidationError.TODO_TITLE_REQUIRED).toAppFailure()

        assertEquals(AppFailure.Validation(ValidationError.TODO_TITLE_REQUIRED), failure)
        assertEquals(FailureCategory.INPUT, failure.category)
        assertEquals(RecoveryAction.EDIT_INPUT, failure.recoveryAction)
        assertFalse(failure.retryable)
    }

    @Test
    fun missingTargetIsClassifiedSeparatelyFromInputErrors() {
        val failure = ValidationException(ValidationError.TODO_NOT_FOUND).toAppFailure()

        assertEquals(FailureCategory.NOT_FOUND, failure.category)
        assertEquals(RecoveryAction.REFRESH, failure.recoveryAction)
    }

    @Test
    fun adapterClassificationSurvivesNestedTechnicalException() {
        val technicalCause = IllegalStateException("SDK internal details")
        val failure = AppFailureException(AppFailure.Network, technicalCause).toAppFailure()

        assertSame(AppFailure.Network, failure)
        assertTrue(failure.retryable)
        assertEquals(RecoveryAction.RETRY, failure.recoveryAction)
    }

    @Test
    fun securityExceptionUsesSettingsRecovery() {
        val failure = RuntimeException(SecurityException("technical permission detail")).toAppFailure()

        assertSame(AppFailure.PermissionRequired, failure)
        assertEquals(RecoveryAction.OPEN_SETTINGS, failure.recoveryAction)
        assertFalse(failure.retryable)
    }

    @Test
    fun unexpectedExceptionDoesNotExposeTheThrowable() {
        val throwable = IllegalStateException("database table and user data")
        val failure = throwable.toAppFailure()

        assertSame(AppFailure.Unexpected, failure)
        assertFalse(failure is AppFailure.Validation)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedIntoAUserFacingFailure() {
        RuntimeException(CancellationException("screen left")).toAppFailure()
    }
}
