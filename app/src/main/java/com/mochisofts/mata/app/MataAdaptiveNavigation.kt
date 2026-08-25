package com.mochisofts.mata.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.launch

enum class MataNavigationType {
    MODAL_DRAWER,
    NAVIGATION_RAIL,
    PERMANENT_DRAWER,
}

data class MataAdaptiveLayoutInfo(
    val navigationType: MataNavigationType,
    val windowWidthDp: Float,
    val windowHeightDp: Float,
    val outerMarginDp: Float,
    val availableContentWidthDp: Float,
    val useTwoPane: Boolean,
    val twoPaneHinge: MataTwoPaneHinge? = null,
    val safeContentRegion: MataSafeContentRegion? = null,
)

enum class MataFoldingOrientation {
    VERTICAL,
    HORIZONTAL,
}

data class MataFoldingFeatureInfo(
    val leftDp: Float,
    val topDp: Float,
    val rightDp: Float,
    val bottomDp: Float,
    val orientation: MataFoldingOrientation,
    val isSeparating: Boolean,
    val isOccluding: Boolean,
)

data class MataTwoPaneHinge(
    val startPaneWidthDp: Float,
    val gapDp: Float,
    val endPaneWidthDp: Float,
)

data class MataSafeContentRegion(
    val leftDp: Float,
    val topDp: Float,
    val rightDp: Float,
    val bottomDp: Float,
) {
    val widthDp: Float get() = (rightDp - leftDp).coerceAtLeast(0f)
    val heightDp: Float get() = (bottomDp - topDp).coerceAtLeast(0f)
}

internal val LocalMataFoldingFeatures = staticCompositionLocalOf<List<FoldingFeature>> {
    emptyList()
}

internal fun mataNavigationTypeFor(
    widthDp: Float,
    heightDp: Float,
): MataNavigationType = when {
    heightDp < 480 || widthDp < 600 -> MataNavigationType.MODAL_DRAWER
    widthDp < 1200 -> MataNavigationType.NAVIGATION_RAIL
    else -> MataNavigationType.PERMANENT_DRAWER
}

internal fun mataAdaptiveLayoutInfoFor(
    widthDp: Float,
    heightDp: Float,
    destination: MataDestination,
    foldingFeatures: List<MataFoldingFeatureInfo> = emptyList(),
): MataAdaptiveLayoutInfo {
    val navigationType = mataNavigationTypeFor(widthDp, heightDp)
    val navigationWidthDp = when (navigationType) {
        MataNavigationType.MODAL_DRAWER -> 0f
        MataNavigationType.NAVIGATION_RAIL -> 80f
        MataNavigationType.PERMANENT_DRAWER -> 240f
    }
    val outerMarginDp = when {
        widthDp < 600f -> 16f
        widthDp < 840f -> 24f
        widthDp < 1200f -> 32f
        else -> 48f
    }
    val maximumContentWidthDp = when (destination) {
        MataDestination.CALENDAR -> 1200f
        MataDestination.SETTINGS -> 720f
        MataDestination.TODOS,
        MataDestination.CATEGORIES,
        MataDestination.ARCHIVE,
        -> 840f
    }
    val contentWidthDp = (widthDp - navigationWidthDp).coerceAtLeast(0f)
    val framedContentWidthDp = contentWidthDp
        .coerceAtLeast(0f)
        .coerceAtMost(maximumContentWidthDp)
    val supportsTwoPane = destination == MataDestination.CALENDAR ||
        destination == MataDestination.CATEGORIES ||
        destination == MataDestination.ARCHIVE
    val frameStartDp = (contentWidthDp - framedContentWidthDp) / 2f
    val frameEndDp = frameStartDp + framedContentWidthDp
    val innerStartDp = frameStartDp + outerMarginDp
    val innerEndDp = frameEndDp - outerMarginDp
    val blockingFeatures = foldingFeatures
        .filter { it.isSeparating || it.isOccluding }
        .mapNotNull { it.toContentFeature(navigationWidthDp, contentWidthDp, heightDp) }
    val intersectingFeatures = blockingFeatures.filter {
        it.intersects(frameStartDp, 0f, frameEndDp, heightDp)
    }
    val standardAvailableWidthDp = (framedContentWidthDp - outerMarginDp * 2f)
        .coerceAtLeast(0f)
    val standardTwoPane = supportsTwoPane &&
        widthDp >= 840f &&
        heightDp >= 480f &&
        standardAvailableWidthDp >= 736f
    val verticalHinge = intersectingFeatures.singleOrNull()
        ?.takeIf { feature ->
            intersectingFeatures.size == 1 &&
                feature.orientation == MataFoldingOrientation.VERTICAL &&
                feature.leftDp > innerStartDp &&
                feature.rightDp < innerEndDp
        }
    val twoPaneHinge = verticalHinge?.let { hinge ->
        val startWidth = hinge.leftDp - innerStartDp
        val endWidth = innerEndDp - hinge.rightDp
        val requiredStartWidth = when (destination) {
            MataDestination.CALENDAR -> 360f
            MataDestination.CATEGORIES,
            MataDestination.ARCHIVE,
            -> 320f
            else -> Float.MAX_VALUE
        }
        val requiredEndWidth = when (destination) {
            MataDestination.CALENDAR -> 352f
            MataDestination.CATEGORIES,
            MataDestination.ARCHIVE,
            -> 392f
            else -> Float.MAX_VALUE
        }
        if (
            standardTwoPane &&
            startWidth >= requiredStartWidth &&
            endWidth >= requiredEndWidth
        ) {
            MataTwoPaneHinge(
                startPaneWidthDp = startWidth,
                gapDp = hinge.rightDp - hinge.leftDp,
                endPaneWidthDp = endWidth,
            )
        } else {
            null
        }
    }
    val safeContentRegion = if (intersectingFeatures.isNotEmpty() && twoPaneHinge == null) {
        largestSafeContentRegion(contentWidthDp, heightDp, blockingFeatures)
    } else {
        null
    }
    val availableContentWidthDp = if (safeContentRegion == null) {
        standardAvailableWidthDp
    } else {
        (safeContentRegion.widthDp.coerceAtMost(maximumContentWidthDp) - outerMarginDp * 2f)
            .coerceAtLeast(0f)
    }
    return MataAdaptiveLayoutInfo(
        navigationType = navigationType,
        windowWidthDp = widthDp,
        windowHeightDp = heightDp,
        outerMarginDp = outerMarginDp,
        availableContentWidthDp = availableContentWidthDp,
        useTwoPane = when {
            twoPaneHinge != null -> true
            safeContentRegion != null -> false
            else -> standardTwoPane
        },
        twoPaneHinge = twoPaneHinge,
        safeContentRegion = safeContentRegion,
    )
}

private fun MataFoldingFeatureInfo.toContentFeature(
    navigationWidthDp: Float,
    contentWidthDp: Float,
    contentHeightDp: Float,
): MataFoldingFeatureInfo? {
    if (
        orientation == MataFoldingOrientation.VERTICAL &&
        (rightDp <= navigationWidthDp || leftDp >= navigationWidthDp + contentWidthDp)
    ) {
        return null
    }
    if (
        orientation == MataFoldingOrientation.HORIZONTAL &&
        (bottomDp <= 0f || topDp >= contentHeightDp)
    ) {
        return null
    }
    val localLeft = (leftDp - navigationWidthDp).coerceIn(0f, contentWidthDp)
    val localRight = (rightDp - navigationWidthDp).coerceIn(0f, contentWidthDp)
    val localTop = topDp.coerceIn(0f, contentHeightDp)
    val localBottom = bottomDp.coerceIn(0f, contentHeightDp)
    val center = if (orientation == MataFoldingOrientation.VERTICAL) {
        (localLeft + localRight) / 2f
    } else {
        (localTop + localBottom) / 2f
    }
    val thickness = if (orientation == MataFoldingOrientation.VERTICAL) {
        localRight - localLeft
    } else {
        localBottom - localTop
    }
    val avoidanceThickness = thickness.coerceAtLeast(MINIMUM_FOLD_AVOIDANCE_DP)
    val result = if (orientation == MataFoldingOrientation.VERTICAL) {
        copy(
            leftDp = (center - avoidanceThickness / 2f).coerceIn(0f, contentWidthDp),
            topDp = 0f,
            rightDp = (center + avoidanceThickness / 2f).coerceIn(0f, contentWidthDp),
            bottomDp = contentHeightDp,
        )
    } else {
        copy(
            leftDp = 0f,
            topDp = (center - avoidanceThickness / 2f).coerceIn(0f, contentHeightDp),
            rightDp = contentWidthDp,
            bottomDp = (center + avoidanceThickness / 2f).coerceIn(0f, contentHeightDp),
        )
    }
    return result.takeIf { it.rightDp > it.leftDp && it.bottomDp > it.topDp }
}

private fun MataFoldingFeatureInfo.intersects(
    leftDp: Float,
    topDp: Float,
    rightDp: Float,
    bottomDp: Float,
): Boolean = this.leftDp < rightDp && this.rightDp > leftDp &&
    this.topDp < bottomDp && this.bottomDp > topDp

private fun largestSafeContentRegion(
    contentWidthDp: Float,
    contentHeightDp: Float,
    blockingFeatures: List<MataFoldingFeatureInfo>,
): MataSafeContentRegion {
    var regions = listOf(
        MataSafeContentRegion(
            leftDp = 0f,
            topDp = 0f,
            rightDp = contentWidthDp,
            bottomDp = contentHeightDp,
        )
    )
    blockingFeatures.forEach { feature ->
        regions = regions.flatMap { region ->
            if (!feature.intersects(
                    region.leftDp,
                    region.topDp,
                    region.rightDp,
                    region.bottomDp,
                )
            ) {
                listOf(region)
            } else if (feature.orientation == MataFoldingOrientation.VERTICAL) {
                listOf(
                    region.copy(rightDp = feature.leftDp.coerceAtMost(region.rightDp)),
                    region.copy(leftDp = feature.rightDp.coerceAtLeast(region.leftDp)),
                ).filter { it.widthDp > 0f && it.heightDp > 0f }
            } else {
                listOf(
                    region.copy(bottomDp = feature.topDp.coerceAtMost(region.bottomDp)),
                    region.copy(topDp = feature.bottomDp.coerceAtLeast(region.topDp)),
                ).filter { it.widthDp > 0f && it.heightDp > 0f }
            }
        }
    }
    return regions.maxWithOrNull(
        compareBy<MataSafeContentRegion> { it.widthDp * it.heightDp }
            .thenByDescending { it.topDp }
            .thenByDescending { it.leftDp }
    ) ?: MataSafeContentRegion(0f, 0f, contentWidthDp, contentHeightDp)
}

private const val MINIMUM_FOLD_AVOIDANCE_DP = 24f

@Composable
fun MataAdaptiveNavigation(
    selected: MataDestination,
    drawerState: DrawerState,
    onSelect: (MataDestination) -> Unit,
    content: @Composable (MataAdaptiveLayoutInfo) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val foldingFeatures = LocalMataFoldingFeatures.current.map { feature ->
            MataFoldingFeatureInfo(
                leftDp = with(density) { feature.bounds.left.toDp().value },
                topDp = with(density) { feature.bounds.top.toDp().value },
                rightDp = with(density) { feature.bounds.right.toDp().value },
                bottomDp = with(density) { feature.bounds.bottom.toDp().value },
                orientation = if (feature.orientation == FoldingFeature.Orientation.VERTICAL) {
                    MataFoldingOrientation.VERTICAL
                } else {
                    MataFoldingOrientation.HORIZONTAL
                },
                isSeparating = feature.isSeparating,
                isOccluding = feature.occlusionType == FoldingFeature.OcclusionType.FULL,
            )
        }
        val layoutInfo = mataAdaptiveLayoutInfoFor(
            widthDp = maxWidth.value,
            heightDp = maxHeight.value,
            destination = selected,
            foldingFeatures = foldingFeatures,
        )
        val navigationType = layoutInfo.navigationType
        val contentWidth = when (selected) {
            MataDestination.CALENDAR -> 1200.dp
            MataDestination.SETTINGS -> 720.dp
            MataDestination.TODOS,
            MataDestination.CATEGORIES,
            MataDestination.ARCHIVE,
            -> 840.dp
        }
        val navigate = { destination: MataDestination ->
            if (destination != selected) onSelect(destination)
        }

        LaunchedEffect(navigationType, drawerState) {
            if (navigationType != MataNavigationType.MODAL_DRAWER) {
                drawerState.close()
            }
        }

        when (navigationType) {
            MataNavigationType.MODAL_DRAWER -> {
                val scope = rememberCoroutineScope()
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        MataNavigationDrawer(selected) { destination ->
                            scope.launch {
                                drawerState.close()
                                navigate(destination)
                            }
                        }
                    },
                ) {
                    MataContentFrame(
                        maxWidth = contentWidth,
                        safeContentRegion = layoutInfo.safeContentRegion,
                    ) {
                        content(layoutInfo)
                    }
                }
            }

            MataNavigationType.NAVIGATION_RAIL -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    MataNavigationRail(
                        selected = selected,
                        onSelect = navigate,
                        modifier = Modifier.fillMaxHeight(),
                    )
                    MataContentFrame(
                        maxWidth = contentWidth,
                        safeContentRegion = layoutInfo.safeContentRegion,
                        modifier = Modifier.weight(1f),
                    ) {
                        content(layoutInfo)
                    }
                }
            }

            MataNavigationType.PERMANENT_DRAWER -> {
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(modifier = Modifier.width(240.dp)) {
                            MataNavigationDrawerContent(
                                selected = selected,
                                onSelect = navigate,
                            )
                        }
                    },
                ) {
                    MataContentFrame(
                        maxWidth = contentWidth,
                        safeContentRegion = layoutInfo.safeContentRegion,
                    ) {
                        content(layoutInfo)
                    }
                }
            }
        }
    }
}

@Composable
fun MataContentFrame(
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    safeContentRegion: MataSafeContentRegion? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val density = LocalDensity.current
        val containerWidthDp = with(density) { constraints.maxWidth.toDp().value }
        val containerHeightDp = with(density) { constraints.maxHeight.toDp().value }
        val safeModifier = safeContentRegion?.let { region ->
            Modifier.padding(
                start = region.leftDp.dp,
                top = region.topDp.dp,
                end = (containerWidthDp - region.rightDp).coerceAtLeast(0f).dp,
                bottom = (containerHeightDp - region.bottomDp).coerceAtLeast(0f).dp,
            )
        } ?: Modifier
        Box(
            modifier = safeModifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}
