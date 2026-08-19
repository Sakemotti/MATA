package com.mochisofts.mata.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
)

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
    val framedContentWidthDp = (widthDp - navigationWidthDp)
        .coerceAtLeast(0f)
        .coerceAtMost(maximumContentWidthDp)
    val availableContentWidthDp = (framedContentWidthDp - outerMarginDp * 2f)
        .coerceAtLeast(0f)
    val supportsTwoPane = destination == MataDestination.CALENDAR ||
        destination == MataDestination.CATEGORIES ||
        destination == MataDestination.ARCHIVE
    return MataAdaptiveLayoutInfo(
        navigationType = navigationType,
        windowWidthDp = widthDp,
        windowHeightDp = heightDp,
        outerMarginDp = outerMarginDp,
        availableContentWidthDp = availableContentWidthDp,
        useTwoPane = supportsTwoPane &&
            widthDp >= 840f &&
            heightDp >= 480f &&
            availableContentWidthDp >= 736f,
    )
}

@Composable
fun MataAdaptiveNavigation(
    selected: MataDestination,
    drawerState: DrawerState,
    onSelect: (MataDestination) -> Unit,
    content: @Composable (MataAdaptiveLayoutInfo) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutInfo = mataAdaptiveLayoutInfoFor(
            widthDp = maxWidth.value,
            heightDp = maxHeight.value,
            destination = selected,
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
                    MataContentFrame(maxWidth = contentWidth) {
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
                    MataContentFrame(maxWidth = contentWidth) {
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
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            content()
        }
    }
}
