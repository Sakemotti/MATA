package com.mochisofts.mata.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {
    @Test
    fun storedCodesAreStableAndUnknownValueFallsBackToSystem() {
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStoredValue("system"))
        assertEquals(AppTheme.LIGHT, AppTheme.fromStoredValue("light"))
        assertEquals(AppTheme.DARK, AppTheme.fromStoredValue("dark"))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStoredValue("unknown"))
        assertEquals(AppTheme.SYSTEM, AppTheme.fromStoredValue(null))
    }
}
