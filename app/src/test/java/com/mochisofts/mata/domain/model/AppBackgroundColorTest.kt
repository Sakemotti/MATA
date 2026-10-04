package com.mochisofts.mata.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppBackgroundColorTest {
    @Test
    fun storedValuesRoundTripAndUnknownValuesUseDefault() {
        AppBackgroundColor.entries.forEach { backgroundColor ->
            assertEquals(
                backgroundColor,
                AppBackgroundColor.fromStoredValue(backgroundColor.code),
            )
        }
        assertEquals(AppBackgroundColor.DEFAULT, AppBackgroundColor.fromStoredValue(null))
        assertEquals(AppBackgroundColor.DEFAULT, AppBackgroundColor.fromStoredValue("unknown"))
    }
}
