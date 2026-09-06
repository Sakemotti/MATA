package com.mochisofts.mata.core.ads

import android.app.Activity
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android host boundary for Google consent UI.
 *
 * The Activity dependency intentionally stays in core instead of leaking into the Android-free
 * domain layer. The data implementation adapts UMP callbacks to application-owned state/events.
 */
interface AdsConsentRepository {
    val state: StateFlow<AdsRuntimeState>
    val events: Flow<AdsConsentEvent>
    fun gatherConsent(activity: Activity)
    fun showPrivacyOptions(activity: Activity)
}
