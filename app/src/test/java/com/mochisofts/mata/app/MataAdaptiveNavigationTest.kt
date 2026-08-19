package com.mochisofts.mata.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MataAdaptiveNavigationTest {
    @Test
    fun compactWidthUsesModalDrawer() {
        assertEquals(
            MataNavigationType.MODAL_DRAWER,
            mataNavigationTypeFor(widthDp = 599.99f, heightDp = 900f),
        )
    }

    @Test
    fun compactHeightUsesModalDrawerEvenWhenWide() {
        assertEquals(
            MataNavigationType.MODAL_DRAWER,
            mataNavigationTypeFor(widthDp = 1600f, heightDp = 479.99f),
        )
    }

    @Test
    fun mediumBoundaryUsesNavigationRail() {
        assertEquals(
            MataNavigationType.NAVIGATION_RAIL,
            mataNavigationTypeFor(widthDp = 600f, heightDp = 480f),
        )
    }

    @Test
    fun expandedWidthUsesNavigationRail() {
        assertEquals(
            MataNavigationType.NAVIGATION_RAIL,
            mataNavigationTypeFor(widthDp = 1199.99f, heightDp = 900f),
        )
    }

    @Test
    fun largeBoundaryUsesPermanentDrawer() {
        assertEquals(
            MataNavigationType.PERMANENT_DRAWER,
            mataNavigationTypeFor(widthDp = 1200f, heightDp = 900f),
        )
    }

    @Test
    fun expandedBoundaryDoesNotUseTwoPaneWhenUsableWidthIsTooSmall() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 840f,
            heightDp = 900f,
            destination = MataDestination.CALENDAR,
        )

        assertEquals(696f, result.availableContentWidthDp)
        assertEquals(false, result.useTwoPane)
    }

    @Test
    fun twoPaneStartsWhenUsableWidthReaches736Dp() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 880f,
            heightDp = 900f,
            destination = MataDestination.CALENDAR,
        )

        assertEquals(736f, result.availableContentWidthDp)
        assertEquals(true, result.useTwoPane)
    }

    @Test
    fun compactHeightPreventsTwoPane() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1600f,
            heightDp = 479.99f,
            destination = MataDestination.CALENDAR,
        )

        assertEquals(false, result.useTwoPane)
    }

    @Test
    fun singleListDestinationNeverUsesTwoPane() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1600f,
            heightDp = 900f,
            destination = MataDestination.TODOS,
        )

        assertEquals(false, result.useTwoPane)
    }
}
