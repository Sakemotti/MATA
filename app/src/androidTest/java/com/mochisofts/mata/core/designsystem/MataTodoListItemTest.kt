package com.mochisofts.mata.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MataTodoListItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reservedSlotsKeepBodyBoundsAlignedWithVisibleControls() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(Modifier.width(360.dp)) {
                    TestTodoListItem(
                        bodyTag = "reserved-body",
                        leadingContent = null,
                        trailingContent = null,
                    )
                    TestTodoListItem(
                        bodyTag = "visible-body",
                        leadingContent = { Box(Modifier.size(24.dp)) },
                        trailingContent = { Box(Modifier.size(24.dp)) },
                    )
                }
            }
        }

        val reservedBounds = composeRule.onNodeWithTag("reserved-body")
            .fetchSemanticsNode()
            .boundsInRoot
        val visibleBounds = composeRule.onNodeWithTag("visible-body")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(reservedBounds.left, visibleBounds.left, POSITION_TOLERANCE_PX)
        assertEquals(reservedBounds.right, visibleBounds.right, POSITION_TOLERANCE_PX)
    }

    @Test
    fun reservedStatusSlotKeepsBodyBoundsAlignedWithVisibleStatus() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(Modifier.width(360.dp)) {
                    TestTodoListItem(
                        bodyTag = "empty-status-body",
                        leadingContent = null,
                        trailingContent = null,
                        trailingSlotWidth = MataTodoListItemDefaults.StatusSlotWidth,
                    )
                    TestTodoListItem(
                        bodyTag = "visible-status-body",
                        leadingContent = null,
                        trailingContent = { Box(Modifier.fillMaxWidth().height(24.dp)) },
                        trailingSlotWidth = MataTodoListItemDefaults.StatusSlotWidth,
                    )
                }
            }
        }

        val emptyBounds = composeRule.onNodeWithTag("empty-status-body")
            .fetchSemanticsNode()
            .boundsInRoot
        val visibleBounds = composeRule.onNodeWithTag("visible-status-body")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(emptyBounds.left, visibleBounds.left, POSITION_TOLERANCE_PX)
        assertEquals(emptyBounds.right, visibleBounds.right, POSITION_TOLERANCE_PX)
    }
}

@Composable
private fun TestTodoListItem(
    bodyTag: String,
    leadingContent: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
    trailingSlotWidth: Dp = MataTodoListItemDefaults.ActionSlotWidth,
) {
    MataTodoListItem(
        headlineContent = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .testTag(bodyTag),
            )
        },
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        reserveLeadingSpace = true,
        reserveTrailingSpace = true,
        trailingSlotWidth = trailingSlotWidth,
    )
}

private const val POSITION_TOLERANCE_PX = 0.5f
