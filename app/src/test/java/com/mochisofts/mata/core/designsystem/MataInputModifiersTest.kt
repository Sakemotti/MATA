package com.mochisofts.mata.core.designsystem

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MataInputModifiersTest {
    @Test
    fun pageUp_scrollsBackward() {
        assertEquals(-1, mataPageScrollDirection(Key.PageUp))
    }

    @Test
    fun pageDown_scrollsForward() {
        assertEquals(1, mataPageScrollDirection(Key.PageDown))
    }

    @Test
    fun arrowKeys_areLeftForFocusTraversal() {
        assertNull(mataPageScrollDirection(Key.DirectionUp))
        assertNull(mataPageScrollDirection(Key.DirectionDown))
    }
}
