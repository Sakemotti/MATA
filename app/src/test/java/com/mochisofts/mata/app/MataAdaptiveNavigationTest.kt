package com.mochisofts.mata.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun todoListDestinationsNeverUseTwoPane() {
        listOf(MataDestination.TODOS, MataDestination.CATEGORY_TODOS).forEach { destination ->
            val result = mataAdaptiveLayoutInfoFor(
                widthDp = 1600f,
                heightDp = 900f,
                destination = destination,
            )

            assertEquals(false, result.useTwoPane)
            assertEquals(744f, result.availableContentWidthDp)
        }
    }

    @Test
    fun separatingVerticalHingeDefinesPhysicalTwoPaneGap() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1000f,
            heightDp = 900f,
            destination = MataDestination.CATEGORIES,
            foldingFeatures = listOf(verticalFeature(leftDp = 500f, rightDp = 520f)),
        )

        assertEquals(true, result.useTwoPane)
        assertEquals(null, result.safeContentRegion)
        assertNotNull(result.twoPaneHinge)
        assertEquals(346f, result.twoPaneHinge?.startPaneWidthDp)
        assertEquals(24f, result.twoPaneHinge?.gapDp)
        assertEquals(406f, result.twoPaneHinge?.endPaneWidthDp)
    }

    @Test
    fun zeroWidthFoldKeepsControlsTwentyFourDpAway() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1000f,
            heightDp = 900f,
            destination = MataDestination.CALENDAR,
            foldingFeatures = listOf(verticalFeature(leftDp = 510f, rightDp = 510f)),
        )

        assertEquals(true, result.useTwoPane)
        assertEquals(24f, result.twoPaneHinge?.gapDp)
    }

    @Test
    fun unbalancedVerticalHingeFallsBackToLargerSafeRegion() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1000f,
            heightDp = 900f,
            destination = MataDestination.ARCHIVE,
            foldingFeatures = listOf(verticalFeature(leftDp = 300f, rightDp = 320f)),
        )

        assertEquals(false, result.useTwoPane)
        assertNull(result.twoPaneHinge)
        assertEquals(242f, result.safeContentRegion?.leftDp)
        assertEquals(920f, result.safeContentRegion?.rightDp)
        assertEquals(614f, result.availableContentWidthDp)
    }

    @Test
    fun horizontalHingeUsesOneSafeRegionWithoutTabletopLayout() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1000f,
            heightDp = 900f,
            destination = MataDestination.CALENDAR,
            foldingFeatures = listOf(
                MataFoldingFeatureInfo(
                    leftDp = 0f,
                    topDp = 400f,
                    rightDp = 1000f,
                    bottomDp = 420f,
                    orientation = MataFoldingOrientation.HORIZONTAL,
                    isSeparating = true,
                    isOccluding = false,
                )
            ),
        )

        assertEquals(false, result.useTwoPane)
        assertEquals(422f, result.safeContentRegion?.topDp)
        assertEquals(900f, result.safeContentRegion?.bottomDp)
    }

    @Test
    fun flatNonOccludingFoldDoesNotChangeLayout() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 880f,
            heightDp = 900f,
            destination = MataDestination.CALENDAR,
            foldingFeatures = listOf(
                verticalFeature(
                    leftDp = 440f,
                    rightDp = 440f,
                    isSeparating = false,
                    isOccluding = false,
                )
            ),
        )

        assertEquals(true, result.useTwoPane)
        assertNull(result.twoPaneHinge)
        assertNull(result.safeContentRegion)
    }

    @Test
    fun multipleHingesUseLargestContinuousRegion() {
        val result = mataAdaptiveLayoutInfoFor(
            widthDp = 1200f,
            heightDp = 900f,
            destination = MataDestination.CATEGORIES,
            foldingFeatures = listOf(
                verticalFeature(leftDp = 600f, rightDp = 600f),
                verticalFeature(leftDp = 900f, rightDp = 900f),
            ),
        )

        assertEquals(false, result.useTwoPane)
        assertNull(result.twoPaneHinge)
        assertEquals(0f, result.safeContentRegion?.leftDp)
        assertEquals(348f, result.safeContentRegion?.rightDp)
    }

    private fun verticalFeature(
        leftDp: Float,
        rightDp: Float,
        isSeparating: Boolean = true,
        isOccluding: Boolean = false,
    ) = MataFoldingFeatureInfo(
        leftDp = leftDp,
        topDp = 0f,
        rightDp = rightDp,
        bottomDp = 900f,
        orientation = MataFoldingOrientation.VERTICAL,
        isSeparating = isSeparating,
        isOccluding = isOccluding,
    )
}
