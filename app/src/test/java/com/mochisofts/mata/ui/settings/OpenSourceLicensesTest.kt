package com.mochisofts.mata.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSourceLicensesTest {
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
