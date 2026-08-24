package com.mochisofts.mata.ui.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivePaneLayoutTest {
    @Test
    fun minimumTwoPaneWidth_preservesListAndDetailMinimums() {
        val widths = archivePaneWidths(availableWidthDp = 736f)

        assertEquals(320f, widths.listWidthDp)
        assertEquals(392f, widths.detailWidthDp)
        assertEquals(736f, widths.listWidthDp + 24f + widths.detailWidthDp)
    }

    @Test
    fun widerLayout_keepsListWithinSpecifiedRange() {
        val widths = archivePaneWidths(availableWidthDp = 1_000f)

        assertTrue(widths.listWidthDp in 320f..400f)
        assertTrue(widths.detailWidthDp >= 392f)
    }
}
