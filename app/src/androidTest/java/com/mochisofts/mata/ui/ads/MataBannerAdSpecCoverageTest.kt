package com.mochisofts.mata.ui.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MataBannerAdSpecCoverageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun issue320_bannerStaysAboveBottomSystemArea() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                Box(Modifier.size(320.dp, 260.dp).testTag(INSET_ROOT_TAG)) {
                    MataBannerAdLayout(
                        runtimeState = eligibleRuntime(consentRevision = 1),
                        isForeground = true,
                        isScreenVisible = true,
                        isImeVisible = false,
                        hasOverlay = false,
                        hasValidConfiguration = true,
                        contentWindowInsets = WindowInsets(left = 0, top = 0, right = 0, bottom = 72),
                        modifier = Modifier.align(Alignment.BottomCenter).testTag(BANNER_AREA_TAG),
                    ) { _, _ ->
                        Box(Modifier.fillMaxWidth().height(50.dp).testTag(BANNER_CONTENT_TAG))
                    }
                }
            }
        }

        val root = composeRule.onNodeWithTag(INSET_ROOT_TAG).fetchSemanticsNode().boundsInRoot
        val banner = composeRule.onNodeWithTag(BANNER_CONTENT_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(72f, root.bottom - banner.bottom, 1f)
    }

    @Test
    fun issue320_sideSystemInsetsReduceRequestedAdWidth() {
        var requestedWidth = 0
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                InsetTestBanner(
                    insets = WindowInsets(left = 40, top = 0, right = 80, bottom = 0),
                    onRequestedWidth = { requestedWidth = it },
                )
            }
        }

        val root = bounds(INSET_ROOT_TAG)
        val banner = bounds(BANNER_CONTENT_TAG)
        assertTrue(banner.left >= root.left + 40f)
        assertTrue(banner.right <= root.right - 80f)
        assertEquals(((root.width - 120f) / composeRule.density.density).toInt(), requestedWidth)
    }

    @Test
    fun issue320_parentConsumedInsetsAreNotAppliedTwice() {
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                InsetTestBanner(
                    insets = WindowInsets(left = 40, top = 0, right = 80, bottom = 72),
                    parentConsumesInsets = true,
                )
            }
        }

        val root = bounds(INSET_ROOT_TAG)
        val banner = bounds(BANNER_CONTENT_TAG)
        assertEquals(72f, root.bottom - banner.bottom, 1f)
        assertEquals(40f, banner.left - root.left, 2f)
        assertEquals(80f, root.right - banner.right, 2f)
    }

    @Test
    fun issue320_loadFailureAndHiddenAdsKeepOnlySystemSafetySpace() {
        var loadState by mutableStateOf(BannerLoadState.LOADING)
        var foreground by mutableStateOf(true)
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                InsetTestBanner(
                    insets = WindowInsets(left = 0, top = 0, right = 0, bottom = 72),
                    loadState = loadState,
                    isForeground = foreground,
                )
            }
        }

        val loadingBounds = listOf(bounds(BANNER_AREA_TAG), bounds(BANNER_CONTENT_TAG))
        assertEquals(72f, bounds(INSET_ROOT_TAG).bottom - bounds(BANNER_CONTENT_TAG).bottom, 1f)
        composeRule.runOnIdle { loadState = BannerLoadState.LOADED }
        assertEquals(loadingBounds, listOf(bounds(BANNER_AREA_TAG), bounds(BANNER_CONTENT_TAG)))

        composeRule.runOnIdle { loadState = BannerLoadState.FAILED }
        assertEquals(0f, bounds(BANNER_CONTENT_TAG).height, 1f)
        assertEquals(72f, bounds(BANNER_AREA_TAG).height, 1f)
        composeRule.runOnIdle { foreground = false }
        composeRule.onNodeWithTag(BANNER_CONTENT_TAG).assertDoesNotExist()
        assertEquals(72f, bounds(BANNER_AREA_TAG).height, 1f)
    }

    @Test
    fun issue320_narrowerSafeWidthDoesNotClipOldBannerDuringDebounce() {
        var insets by mutableStateOf(WindowInsets(left = 0, top = 0, right = 0, bottom = 0))
        var requestedWidth = 0
        composeRule.setContent {
            MataTheme(useDynamicColor = false) {
                InsetTestBanner(insets = insets, onRequestedWidth = { requestedWidth = it })
            }
        }
        composeRule.onNodeWithTag(BANNER_CONTENT_TAG).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.runOnIdle {
                insets = WindowInsets(left = 40, top = 0, right = 80, bottom = 72)
            }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag(BANNER_CONTENT_TAG).assertDoesNotExist()
            composeRule.mainClock.advanceTimeBy(400)
            composeRule.onNodeWithTag(BANNER_CONTENT_TAG).assertIsDisplayed()
            val root = bounds(INSET_ROOT_TAG)
            val banner = bounds(BANNER_CONTENT_TAG)
            assertTrue(banner.left >= root.left + 40f)
            assertTrue(banner.right <= root.right - 80f)
            assertEquals(((root.width - 120f) / composeRule.density.density).toInt(), requestedWidth)
            assertEquals(72f, root.bottom - banner.bottom, 1f)
        } finally {
            composeRule.mainClock.autoAdvance = true
        }
    }

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
                    ) { _, consentRevision ->
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
                ) { _, consentRevision ->
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
                    ) { _, _ ->
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

    @Composable
    private fun InsetTestBanner(
        insets: WindowInsets,
        parentConsumesInsets: Boolean = false,
        loadState: BannerLoadState = BannerLoadState.LOADED,
        isForeground: Boolean = true,
        onRequestedWidth: (Int) -> Unit = {},
    ) {
        Box(Modifier.size(320.dp, 260.dp).testTag(INSET_ROOT_TAG)) {
            val parentModifier = if (parentConsumesInsets) {
                Modifier.fillMaxSize().windowInsetsPadding(insets)
            } else {
                Modifier.fillMaxSize()
            }
            Box(parentModifier) {
                MataBannerAdLayout(
                    runtimeState = eligibleRuntime(consentRevision = 1),
                    isForeground = isForeground,
                    isScreenVisible = true,
                    isImeVisible = false,
                    hasOverlay = false,
                    hasValidConfiguration = true,
                    contentWindowInsets = insets,
                    modifier = Modifier.align(Alignment.BottomCenter).testTag(BANNER_AREA_TAG),
                ) { width, _ ->
                    SideEffect { onRequestedWidth(width) }
                    Box(
                        Modifier.bannerAdSize(loadState, widthDp = width, heightDp = 50)
                            .testTag(BANNER_CONTENT_TAG),
                    )
                }
            }
        }
    }

    private fun bounds(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

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
        const val INSET_ROOT_TAG = "banner-inset-root"
    }
}
