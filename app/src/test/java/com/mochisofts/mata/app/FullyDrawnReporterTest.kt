package com.mochisofts.mata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullyDrawnReporterTest {
    @Test
    fun report_invokesPlatformCallbackOnlyOnce() {
        var callCount = 0
        val reporter = OneShotFullyDrawnReporter { callCount += 1 }

        assertTrue(reporter.report())
        assertFalse(reporter.report())

        assertEquals(1, callCount)
    }
}
