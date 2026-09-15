package com.mochisofts.mata.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.mochisofts.mata.R
import com.mochisofts.mata.core.designsystem.MataTheme
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AdsSdkInitialization
import com.mochisofts.mata.ui.todolist.TodoListTopBarActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MataBannerAdSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tl028_consentRevisionReplacesAndConsentLossDisposesBannerResource() {
        var runtimeState by mutableStateOf(eligibleRuntime(consentRevision = 1))
        val created = mutableListOf<Long>()
        val disposed = mutableListOf<Long>()

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Box(Modifier.fillMaxWidth()) {
                    MataBannerAdLayout(
                        runtimeState = runtimeState,
                        isForeground = true,
                        isScreenVisible = true,
                        isImeVisible = false,
                        hasOverlay = false,
                        hasValidConfiguration = true,
                    ) { _, consentRevision, _ ->
                        val resource = rememberDisposableResource(
                            key = consentRevision,
                            create = {
                                created += consentRevision
                                TestBannerResource(consentRevision)
                            },
                            dispose = { resource -> disposed += resource.consentRevision },
                        )
                        Box(
                            Modifier
                                .testTag(bannerTag(resource.consentRevision))
                                .fillMaxWidth()
                                .height(50.dp),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag(bannerTag(1)).assertIsDisplayed()

        composeRule.runOnIdle {
            runtimeState = eligibleRuntime(consentRevision = 2)
        }
        composeRule.onNodeWithTag(bannerTag(2)).assertIsDisplayed()
        composeRule.onNodeWithTag(bannerTag(1)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(1L, 2L), created)
            assertEquals(listOf(1L), disposed)
            runtimeState = runtimeState.copy(
                canRequestAds = false,
                consentRevision = 3,
            )
        }
        composeRule.onNodeWithTag(bannerTag(2)).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(listOf(1L, 2L), disposed)
            runtimeState = eligibleRuntime(consentRevision = 4)
        }
        composeRule.onNodeWithTag(bannerTag(4)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(1L, 2L, 4L), created)
        }
    }

    @Test
    fun st035_umpChangesDisposeExistingAdsAndReevaluateTheNewConsentState() {
        var runtimeState by mutableStateOf(eligibleRuntime(consentRevision = 1))
        val created = mutableListOf<Long>()
        val disposed = mutableListOf<Long>()

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                MataBannerAdLayout(
                    runtimeState = runtimeState,
                    isForeground = true,
                    isScreenVisible = true,
                    isImeVisible = false,
                    hasOverlay = false,
                    hasValidConfiguration = true,
                ) { _, consentRevision, _ ->
                    val resource = rememberDisposableResource(
                        key = consentRevision,
                        create = {
                            created += consentRevision
                            TestBannerResource(consentRevision)
                        },
                        dispose = { resource -> disposed += resource.consentRevision },
                    )
                    Box(
                        Modifier
                            .testTag(bannerTag(resource.consentRevision))
                            .fillMaxWidth()
                            .height(50.dp),
                    )
                }
            }
        }
        composeRule.onNodeWithTag(bannerTag(1)).assertIsDisplayed()

        composeRule.runOnIdle {
            runtimeState = runtimeState.copy(isShowingPrivacyOptions = true)
        }
        composeRule.onNodeWithTag(bannerTag(1)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf(1L), disposed) }

        composeRule.runOnIdle {
            runtimeState = eligibleRuntime(consentRevision = 2)
        }
        composeRule.onNodeWithTag(bannerTag(2)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(1L, 2L), created) }

        composeRule.runOnIdle {
            runtimeState = runtimeState.copy(
                canRequestAds = false,
                consentRevision = 3,
            )
        }
        composeRule.onNodeWithTag(bannerTag(2)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(listOf(1L, 2L), disposed) }
    }

    @Test
    fun tl030_loadingAndLoadedBannerKeepReservedLayoutWhileTopActionChanges() {
        var loadState by mutableStateOf(BannerLoadState.LOADING)
        var isToday by mutableStateOf(true)

        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag(TOP_ACTION_AREA_TAG),
                    ) {
                        TodoListTopBarActions(
                            isToday = isToday,
                            showCompleted = false,
                            onToggleCompleted = {},
                            onToday = {},
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .testTag(LIST_AREA_TAG),
                    ) {
                        Text("TODO一覧領域")
                    }
                    MataBannerAdLayout(
                        runtimeState = eligibleRuntime(consentRevision = 1),
                        isForeground = true,
                        isScreenVisible = true,
                        isImeVisible = false,
                        hasOverlay = false,
                        hasValidConfiguration = true,
                        modifier = Modifier.testTag(BANNER_AREA_TAG),
                    ) { _, _, _ ->
                        Box(
                            Modifier
                                .testTag(BANNER_CONTENT_TAG)
                                .bannerAdSize(loadState, widthDp = 320, heightDp = 50),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(text(R.string.content_description_show_completed_todos))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_return_to_today)).assertDoesNotExist()
        val loadingBounds = layoutBounds()

        composeRule.runOnIdle { loadState = BannerLoadState.LOADED }
        composeRule.waitForIdle()
        assertEquals(loadingBounds, layoutBounds())

        composeRule.runOnIdle { isToday = false }
        composeRule.onNodeWithText(text(R.string.content_description_show_completed_todos))
            .assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.action_return_to_today)).assertIsDisplayed()
        assertEquals(loadingBounds, layoutBounds())
    }

    private fun layoutBounds() = listOf(
        composeRule.onNodeWithTag(TOP_ACTION_AREA_TAG).fetchSemanticsNode().boundsInRoot,
        composeRule.onNodeWithTag(LIST_AREA_TAG).fetchSemanticsNode().boundsInRoot,
        composeRule.onNodeWithTag(BANNER_AREA_TAG).fetchSemanticsNode().boundsInRoot,
        composeRule.onNodeWithTag(BANNER_CONTENT_TAG).fetchSemanticsNode().boundsInRoot,
    )

    private fun text(resourceId: Int): String = InstrumentationRegistry
        .getInstrumentation().targetContext.getString(resourceId)

    private fun eligibleRuntime(consentRevision: Long) = AdsRuntimeState(
        consentUpdateAttempted = true,
        canRequestAds = true,
        sdkInitialization = AdsSdkInitialization.INITIALIZED,
        consentRevision = consentRevision,
    )

    private data class TestBannerResource(val consentRevision: Long)

    private fun bannerTag(consentRevision: Long) = "banner-resource:$consentRevision"

    private companion object {
        const val TOP_ACTION_AREA_TAG = "top-action-area"
        const val LIST_AREA_TAG = "list-area"
        const val BANNER_AREA_TAG = "banner-area"
        const val BANNER_CONTENT_TAG = "banner-content"
    }
}
