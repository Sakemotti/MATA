package com.mochisofts.mata.app

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupCoordinatorTest {
    @Test
    fun repeatedStart_runsRequiredRecoveryOnlyOnce() {
        val fixture = Fixture()

        fixture.coordinator.start()
        fixture.coordinator.start()
        fixture.scope.advanceUntilIdle()

        assertEquals(1, fixture.recovery.callCount)
        assertEquals(StartupState.Ready(appVersionChanged = false), fixture.coordinator.state.value)
    }

    @Test
    fun failureCanBeRetriedWithoutPublishingReadyEarly() {
        val fixture = Fixture(failuresBeforeSuccess = 1, appVersionChanged = true)

        fixture.coordinator.start()
        fixture.scope.advanceUntilIdle()
        assertEquals(StartupState.Failed, fixture.coordinator.state.value)

        fixture.coordinator.retry()
        assertEquals(StartupState.Initializing, fixture.coordinator.state.value)
        fixture.scope.advanceUntilIdle()

        assertEquals(2, fixture.recovery.callCount)
        assertEquals(StartupState.Ready(appVersionChanged = true), fixture.coordinator.state.value)
    }

    @Test
    fun retryIsIgnoredUnlessStartupFailed() {
        val fixture = Fixture()

        fixture.coordinator.retry()
        fixture.scope.advanceUntilIdle()

        assertEquals(0, fixture.recovery.callCount)
        assertEquals(StartupState.Initializing, fixture.coordinator.state.value)
    }

    private class Fixture(
        failuresBeforeSuccess: Int = 0,
        appVersionChanged: Boolean = false,
    ) {
        private val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val recovery = FakeStartupRecovery(failuresBeforeSuccess, appVersionChanged)
        val coordinator = StartupCoordinator(recovery, scope)
    }

    private class FakeStartupRecovery(
        private var failuresRemaining: Int,
        private val appVersionChanged: Boolean,
    ) : StartupRecovery {
        var callCount = 0
            private set

        override suspend fun recover(): StartupRecoveryResult {
            callCount += 1
            if (failuresRemaining > 0) {
                failuresRemaining -= 1
                error("Injected startup failure")
            }
            return StartupRecoveryResult(appVersionChanged)
        }
    }
}
