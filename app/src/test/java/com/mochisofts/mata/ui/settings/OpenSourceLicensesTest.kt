package com.mochisofts.mata.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {
    @Test
    fun st039_listSearchEmptyBodyAndLoadFailureStatesAreDistinct() {
        val loaded = loadLibraries { emptyList() }
        assertTrue(loaded is LibrariesLoadState.Loaded)
        assertTrue((loaded as LibrariesLoadState.Loaded).libraries.isEmpty())
        assertTrue(loadLibraries { error("broken json") } is LibrariesLoadState.Error)
        assertTrue(matchesLibraryName("AndroidX Room", " room "))
        assertFalse(matchesLibraryName("AndroidX Room", "Compose"))
        assertEquals("Apache License", licenseBodyOrNull(listOf(" Apache License ")))
        assertNull(licenseBodyOrNull(listOf(null, "")))
    }

    @Test
    fun `library name search trims query and ignores case`() {
        assertTrue(matchesLibraryName("AndroidX Room", "  room "))
        assertTrue(matchesLibraryName("AndroidX Room", ""))
        assertFalse(matchesLibraryName("AndroidX Room", "Compose"))
    }

    @Test
    fun `license body combines distinct non-blank contents`() {
        assertEquals(
            "Apache License\n\nMIT License",
            licenseBodyOrNull(listOf(" Apache License ", null, "", "MIT License", "Apache License")),
        )
        assertNull(licenseBodyOrNull(listOf(null, "  ")))
    }
}
