package com.mochisofts.mata.ui.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.BannerDisplayConditions
import com.mochisofts.mata.domain.model.BannerDisplayDecision
import com.mochisofts.mata.domain.model.evaluate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBUG_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
private val BANNER_AD_UNIT_ID_PATTERN = Regex("ca-app-pub-\\d{16}/\\d{10}")

@Composable
fun MataBannerAd(
    runtimeState: AdsRuntimeState,
    isForeground: Boolean,
    isScreenVisible: Boolean,
    isImeVisible: Boolean,
    hasOverlay: Boolean,
    modifier: Modifier = Modifier,
    applyBottomSafeInset: Boolean = false,
) {
    val isPreview = LocalInspectionMode.current
    val validConfiguration = remember {
        if (BuildConfig.DEBUG) {
            BuildConfig.ADMOB_BANNER_AD_UNIT_ID == DEBUG_BANNER_AD_UNIT_ID
        } else {
            BANNER_AD_UNIT_ID_PATTERN.matches(BuildConfig.ADMOB_BANNER_AD_UNIT_ID) &&
                BuildConfig.ADMOB_BANNER_AD_UNIT_ID != DEBUG_BANNER_AD_UNIT_ID
        }
    }
    val decision = BannerDisplayConditions(
        runtimeAllowsAds = runtimeState.canLoadBanner,
        isForeground = isForeground,
        isScreenVisible = isScreenVisible,
        isImeVisible = isImeVisible,
        hasOverlay = hasOverlay,
        hasValidConfiguration = validConfiguration && !isPreview,
    ).evaluate()
    val canLoad = decision == BannerDisplayDecision.SHOW

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val requestedWidth = maxWidth.coerceAtMost(840.dp).value.toInt()
        var stableWidth by remember { mutableIntStateOf(requestedWidth) }
        LaunchedEffect(canLoad, requestedWidth) {
            if (!canLoad || requestedWidth <= 0) {
                stableWidth = 0
            } else if (stableWidth <= 0) {
                stableWidth = requestedWidth
            } else if (stableWidth != requestedWidth) {
                delay(300)
                stableWidth = requestedWidth
            }
        }
        if (canLoad && stableWidth > 0) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BannerAdHost(
                    widthDp = stableWidth,
                    consentRevision = runtimeState.consentRevision,
                    applyBottomSafeInset = applyBottomSafeInset,
                )
            }
        }
    }
}

@Composable
private fun BannerAdHost(
    widthDp: Int,
    consentRevision: Long,
    applyBottomSafeInset: Boolean,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return
    val scope = rememberCoroutineScope()
    val adSize = remember(activity, widthDp) {
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(activity, widthDp)
    }
    val adView = remember(activity, widthDp, consentRevision) { AdView(activity) }
    var loadState by remember(adView) { mutableStateOf(BannerLoadState.LOADING) }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    LaunchedEffect(adView, adSize) {
        loadState = BannerLoadState.LOADING
        val request = BannerAdRequest.Builder(BuildConfig.ADMOB_BANNER_AD_UNIT_ID, adSize).build()
        adView.loadAd(
            request,
            object : AdLoadCallback<BannerAd> {
                override fun onAdLoaded(ad: BannerAd) {
                    scope.launch { loadState = BannerLoadState.LOADED }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    scope.launch { loadState = BannerLoadState.FAILED }
                }
            },
        )
    }

    val loadedModifier = if (applyBottomSafeInset) {
        Modifier.windowInsetsPadding(
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
        )
    } else {
        Modifier
    }
    AndroidView(
        factory = { adView },
        modifier = if (loadState == BannerLoadState.FAILED) {
            Modifier.size(0.dp)
        } else {
            loadedModifier.width(adSize.width.dp).height(adSize.height.dp)
        },
    )
}

private enum class BannerLoadState {
    LOADING,
    LOADED,
    FAILED,
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
