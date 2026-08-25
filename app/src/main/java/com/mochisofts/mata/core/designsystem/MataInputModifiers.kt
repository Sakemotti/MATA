package com.mochisofts.mata.core.designsystem

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Adds the conventional hand cursor without changing touch or focus behaviour. */
fun Modifier.mataClickablePointer(enabled: Boolean = true): Modifier =
    if (enabled) pointerHoverIcon(PointerIcon.Hand) else this

/**
 * Scrolls a focused list by one viewport with Page Up / Page Down.
 *
 * Arrow keys are deliberately left to Compose focus traversal so keyboard users can move
 * between the controls in a row. Wheel and touch scrolling continue to be handled by the
 * underlying scroll container.
 */
fun Modifier.mataPageKeyScroll(state: ScrollableState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minimumDistancePx = with(density) { MinimumPageScrollDistance.toPx() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }

    onSizeChanged { viewportHeightPx = it.height }
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val direction = mataPageScrollDirection(event.key)
                ?: return@onPreviewKeyEvent false
            val canScroll = if (direction > 0) state.canScrollForward else state.canScrollBackward
            if (!canScroll) return@onPreviewKeyEvent false

            val pageDistancePx = (viewportHeightPx * PageScrollViewportFraction)
                .coerceAtLeast(minimumDistancePx)
            scope.launch { state.scrollBy(direction * pageDistancePx) }
            true
        }
}

internal fun mataPageScrollDirection(key: Key): Int? = when (key) {
    Key.PageUp -> -1
    Key.PageDown -> 1
    else -> null
}

private val MinimumPageScrollDistance = 64.dp
private const val PageScrollViewportFraction = 0.85f
