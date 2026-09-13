package com.mochisofts.mata.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

object MataCardLayout {
    val PageHorizontalPadding = 16.dp
    val PageVerticalPadding = 12.dp
    val CardSpacing = 12.dp
    val PageContentPadding = PaddingValues(
        horizontal = PageHorizontalPadding,
        vertical = PageVerticalPadding,
    )
    val SectionContentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
    val Shape = RoundedCornerShape(24.dp)
    val InputShape = RoundedCornerShape(16.dp)
}

val MaterialTheme.mataPageColor
    @Composable
    @ReadOnlyComposable
    get() = if (colorScheme.background.luminance() < 0.5f) {
        colorScheme.surface
    } else {
        colorScheme.surfaceContainer
    }

val MaterialTheme.mataCardColor
    @Composable
    @ReadOnlyComposable
    get() = if (colorScheme.background.luminance() < 0.5f) {
        colorScheme.surfaceContainerHigh
    } else {
        colorScheme.surfaceContainerLowest
    }

fun mataCardSegmentShape(index: Int, count: Int): Shape = when {
    count <= 1 -> MataCardLayout.Shape
    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    index == count - 1 -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    else -> RoundedCornerShape(0.dp)
}

@Composable
fun MataSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = MataCardLayout.SectionContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MataCardLayout.Shape,
        color = MaterialTheme.mataCardColor,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}
