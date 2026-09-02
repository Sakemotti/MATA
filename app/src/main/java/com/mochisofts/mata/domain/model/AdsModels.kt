package com.mochisofts.mata.domain.model

enum class AdsSdkInitialization {
    NOT_INITIALIZED,
    INITIALIZING,
    INITIALIZED,
    FAILED,
}

data class AdsRuntimeState(
    val consentUpdateAttempted: Boolean = false,
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val isGatheringConsent: Boolean = false,
    val isShowingPrivacyOptions: Boolean = false,
    val sdkInitialization: AdsSdkInitialization = AdsSdkInitialization.NOT_INITIALIZED,
    val consentRevision: Long = 0,
) {
    val canLoadBanner: Boolean
        get() = consentUpdateAttempted &&
            canRequestAds &&
            !isGatheringConsent &&
            !isShowingPrivacyOptions &&
            sdkInitialization == AdsSdkInitialization.INITIALIZED
}

enum class AdsConsentEvent {
    PRIVACY_OPTIONS_ERROR,
}

data class BannerDisplayConditions(
    val runtimeAllowsAds: Boolean,
    val isForeground: Boolean,
    val isScreenVisible: Boolean,
    val isImeVisible: Boolean,
    val hasOverlay: Boolean,
    val hasValidConfiguration: Boolean,
)

enum class BannerDisplayDecision {
    SHOW,
    RUNTIME_NOT_READY,
    BACKGROUND,
    SCREEN_HIDDEN,
    IME_VISIBLE,
    OVERLAY_VISIBLE,
    INVALID_CONFIGURATION,
}

fun BannerDisplayConditions.evaluate(): BannerDisplayDecision = when {
    !hasValidConfiguration -> BannerDisplayDecision.INVALID_CONFIGURATION
    !runtimeAllowsAds -> BannerDisplayDecision.RUNTIME_NOT_READY
    !isForeground -> BannerDisplayDecision.BACKGROUND
    !isScreenVisible -> BannerDisplayDecision.SCREEN_HIDDEN
    isImeVisible -> BannerDisplayDecision.IME_VISIBLE
    hasOverlay -> BannerDisplayDecision.OVERLAY_VISIBLE
    else -> BannerDisplayDecision.SHOW
}
