package com.mochisofts.mata.ui.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPaneLayoutTest {
    @Test
    fun minimumTwoPaneWidth_preservesListAndEditorMinimums() {
        val widths = categoryPaneWidths(availableWidthDp = 736f)

        assertEquals(320f, widths.listWidthDp)
        assertEquals(392f, widths.editorWidthDp)
        assertEquals(736f, widths.listWidthDp + 24f + widths.editorWidthDp)
    }

    @Test
    fun widerLayout_keepsListWithinSpecifiedRange() {
        val widths = categoryPaneWidths(availableWidthDp = 1_000f)

        assertTrue(widths.listWidthDp in 320f..400f)
        assertTrue(widths.editorWidthDp >= 392f)
    }
}
