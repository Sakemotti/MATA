package com.mochisofts.mata.data.ads

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.mochisofts.mata.BuildConfig
import com.mochisofts.mata.domain.model.AdsConsentEvent
import com.mochisofts.mata.domain.model.AdsRuntimeState
import com.mochisofts.mata.domain.model.AdsSdkInitialization
import com.mochisofts.mata.domain.repository.AdsConsentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class GoogleAdsConsentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : AdsConsentRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val consentRequestStarted = AtomicBoolean(false)
    private val privacyOptionsShowing = AtomicBoolean(false)
    private val initializationMutex = Mutex()
    private val _state = MutableStateFlow(AdsRuntimeState())
    override val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<AdsConsentEvent>(extraBufferCapacity = 4)
    override val events: Flow<AdsConsentEvent> = _events.asSharedFlow()

    override fun gatherConsent(activity: Activity) {
        if (!consentRequestStarted.compareAndSet(false, true)) return
        if (activity.isFinishing || activity.isDestroyed) {
            _state.update { it.copy(consentUpdateAttempted = true, isGatheringConsent = false) }
            return
        }
        scope.launch(Dispatchers.Main.immediate) {
            _state.update {
                it.copy(consentUpdateAttempted = true, isGatheringConsent = true)
            }
            val parameters = ConsentRequestParameters.Builder().build()
            consentInformation.requestConsentInfoUpdate(
                activity,
                parameters,
                {
                    publishConsentInformation(isGathering = true)
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        publishConsentInformation(
                            isGathering = false,
                            incrementRevision = formError == null,
                        )
                    }
                },
                {
                    publishConsentInformation(isGathering = false)
                },
            )
            // A previous session can already permit ads immediately after the update request starts.
            publishConsentInformation(isGathering = true)
        }
    }

    override fun showPrivacyOptions(activity: Activity) {
        if (!_state.value.privacyOptionsRequired) return
        if (!privacyOptionsShowing.compareAndSet(false, true)) return
        if (activity.isFinishing || activity.isDestroyed) {
            privacyOptionsShowing.set(false)
            _events.tryEmit(AdsConsentEvent.PRIVACY_OPTIONS_ERROR)
            return
        }
        scope.launch(Dispatchers.Main.immediate) {
            _state.update { it.copy(isShowingPrivacyOptions = true) }
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                privacyOptionsShowing.set(false)
                publishConsentInformation(
                    isShowingPrivacyOptions = false,
                    incrementRevision = formError == null,
                )
                if (formError != null) {
                    _events.tryEmit(AdsConsentEvent.PRIVACY_OPTIONS_ERROR)
                }
            }
        }
    }

    private fun publishConsentInformation(
        isGathering: Boolean = _state.value.isGatheringConsent,
        isShowingPrivacyOptions: Boolean = _state.value.isShowingPrivacyOptions,
        incrementRevision: Boolean = false,
    ) {
        val canRequestAds = runCatching { consentInformation.canRequestAds() }.getOrDefault(false)
        val privacyOptionsRequired = runCatching {
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        }.getOrDefault(false)
        _state.update { current ->
            current.copy(
                consentUpdateAttempted = true,
                canRequestAds = canRequestAds,
                privacyOptionsRequired = privacyOptionsRequired,
                isGatheringConsent = isGathering,
                isShowingPrivacyOptions = isShowingPrivacyOptions,
                consentRevision = current.consentRevision + if (incrementRevision) 1 else 0,
            )
        }
        scope.launch { initializeAdsIfAllowed() }
    }

    private suspend fun initializeAdsIfAllowed() {
        initializationMutex.withLock {
            val current = _state.value
            if (!current.consentUpdateAttempted ||
                !current.canRequestAds ||
                current.sdkInitialization != AdsSdkInitialization.NOT_INITIALIZED
            ) {
                return
            }
            _state.update { it.copy(sdkInitialization = AdsSdkInitialization.INITIALIZING) }
            val initialized = runCatching {
                withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine { continuation ->
                        MobileAds.initialize(
                            context,
                            InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build(),
                        ) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
            }.isSuccess
            _state.update {
                it.copy(
                    sdkInitialization = if (initialized) {
                        AdsSdkInitialization.INITIALIZED
                    } else {
                        AdsSdkInitialization.FAILED
                    },
                )
            }
        }
    }
}
