package com.mochisofts.mata.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsModelsTest {
    @Test
    fun bannerCanLoadOnlyWhenConsentAndSdkAreReady() {
        val ready = AdsRuntimeState(
            consentUpdateAttempted = true,
            canRequestAds = true,
            sdkInitialization = AdsSdkInitialization.INITIALIZED,
        )

        assertTrue(ready.canLoadBanner)
        assertFalse(ready.copy(consentUpdateAttempted = false).canLoadBanner)
        assertFalse(ready.copy(canRequestAds = false).canLoadBanner)
        assertFalse(ready.copy(isGatheringConsent = true).canLoadBanner)
        assertFalse(ready.copy(isShowingPrivacyOptions = true).canLoadBanner)
        assertFalse(
            ready.copy(sdkInitialization = AdsSdkInitialization.INITIALIZING).canLoadBanner,
        )
    }

    @Test
    fun bannerDisplayConditionsReturnSpecificBlockingReason() {
        val eligible = BannerDisplayConditions(
            runtimeAllowsAds = true,
            isForeground = true,
            isScreenVisible = true,
            isImeVisible = false,
            hasOverlay = false,
            hasValidConfiguration = true,
        )

        assertEquals(BannerDisplayDecision.SHOW, eligible.evaluate())
        assertEquals(
            BannerDisplayDecision.INVALID_CONFIGURATION,
            eligible.copy(hasValidConfiguration = false).evaluate(),
        )
        assertEquals(
            BannerDisplayDecision.RUNTIME_NOT_READY,
            eligible.copy(runtimeAllowsAds = false).evaluate(),
        )
        assertEquals(
            BannerDisplayDecision.BACKGROUND,
            eligible.copy(isForeground = false).evaluate(),
        )
        assertEquals(
            BannerDisplayDecision.SCREEN_HIDDEN,
            eligible.copy(isScreenVisible = false).evaluate(),
        )
        assertEquals(
            BannerDisplayDecision.IME_VISIBLE,
            eligible.copy(isImeVisible = true).evaluate(),
        )
        assertEquals(
            BannerDisplayDecision.OVERLAY_VISIBLE,
            eligible.copy(hasOverlay = true).evaluate(),
        )
    }
}
