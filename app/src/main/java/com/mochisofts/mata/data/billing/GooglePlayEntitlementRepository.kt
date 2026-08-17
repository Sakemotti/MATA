package com.mochisofts.mata.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.mochisofts.mata.domain.model.BillingEvent
import com.mochisofts.mata.domain.model.BillingLaunchResult
import com.mochisofts.mata.domain.model.BillingOperation
import com.mochisofts.mata.domain.model.BillingProduct
import com.mochisofts.mata.domain.model.BillingState
import com.mochisofts.mata.domain.model.EntitlementState
import com.mochisofts.mata.domain.model.EntitlementStatus
import com.mochisofts.mata.domain.model.REMOVE_ADS_PRODUCT_ID
import com.mochisofts.mata.domain.repository.EntitlementRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class GooglePlayEntitlementRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val cache: EntitlementCache,
    private val workScheduler: BillingWorkScheduler,
    private val clock: Clock,
) : EntitlementRepository, PurchasesUpdatedListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val connectionMutex = Mutex()
    private val _state = MutableStateFlow(BillingState())
    override val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events: Flow<BillingEvent> = _events.asSharedFlow()
    private var cacheLoaded = false
    private var currentProductDetails: ProductDetails? = null
    private var currentOffer: ProductDetails.OneTimePurchaseOfferDetails? = null

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .enableAutoServiceReconnection()
        .build()

    override suspend fun start() {
        loadCache()
        refresh()
    }

    override suspend fun refresh() {
        reconcile(BillingOperation.REFRESHING, emitRestoreResult = false)
    }

    override suspend fun restore() {
        reconcile(BillingOperation.RESTORING, emitRestoreResult = true)
    }

    override suspend fun launchPurchase(activity: Activity): BillingLaunchResult = operationMutex.withLock {
        if (_state.value.operation != BillingOperation.IDLE) {
            return BillingLaunchResult.ALREADY_IN_PROGRESS
        }
        if (activity.isFinishing || activity.isDestroyed) return BillingLaunchResult.ERROR
        loadCache()
        _state.update { it.copy(operation = BillingOperation.PURCHASING) }
        val connection = ensureConnected()
        if (connection.responseCode != BillingClient.BillingResponseCode.OK) {
            updateFailure(connection)
            return failureLaunchResult(connection)
        }
        val productQuery = queryProduct()
        if (productQuery.first.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(operation = BillingOperation.IDLE, product = null) }
            return failureLaunchResult(productQuery.first)
        }
        val details = productQuery.second.productDetailsList
            .firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
        val offer = details?.let(::selectPurchaseOffer)
        if (details == null || offer == null) {
            currentProductDetails = null
            currentOffer = null
            _state.update { it.copy(operation = BillingOperation.IDLE, product = null) }
            return BillingLaunchResult.PRODUCT_UNAVAILABLE
        }
        updateProduct(details, offer)
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken ?: return BillingLaunchResult.PRODUCT_UNAVAILABLE)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .setIsOfferPersonalized(false)
            .build()
        val result = withContext(Dispatchers.Main.immediate) {
            billingClient.launchBillingFlow(activity, flowParams)
        }
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> BillingLaunchResult.STARTED
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                _state.update { it.copy(operation = BillingOperation.IDLE) }
                scope.launch { restore() }
                BillingLaunchResult.STARTED
            }
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED,
            -> {
                updateFailure(result)
                BillingLaunchResult.BILLING_UNAVAILABLE
            }
            else -> {
                _state.update { it.copy(operation = BillingOperation.IDLE) }
                BillingLaunchResult.ERROR
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            operationMutex.withLock {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        val evaluation = evaluate(purchases.orEmpty())
                        applyEvaluation(evaluation)
                        _state.update { it.copy(operation = BillingOperation.IDLE) }
                        when (evaluation) {
                            is PurchaseEvaluation.Purchased -> _events.emit(BillingEvent.PURCHASED)
                            PurchaseEvaluation.Pending -> _events.emit(BillingEvent.PENDING)
                            PurchaseEvaluation.Invalid,
                            PurchaseEvaluation.NotPurchased,
                            -> _events.emit(BillingEvent.ERROR)
                        }
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        _state.update { it.copy(operation = BillingOperation.IDLE) }
                        _events.emit(BillingEvent.USER_CANCELED)
                    }
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                        _state.update { it.copy(operation = BillingOperation.IDLE) }
                        scope.launch { restore() }
                    }
                    else -> {
                        _state.update { it.copy(operation = BillingOperation.IDLE) }
                        _events.emit(BillingEvent.ERROR)
                    }
                }
            }
        }
    }

    private suspend fun reconcile(operation: BillingOperation, emitRestoreResult: Boolean) {
        operationMutex.withLock {
            loadCache()
            if (_state.value.operation == BillingOperation.PURCHASING) return
            _state.update { it.copy(operation = operation) }
            val connection = ensureConnected()
            if (connection.responseCode != BillingClient.BillingResponseCode.OK) {
                updateFailure(connection)
                if (emitRestoreResult) _events.emit(BillingEvent.ERROR)
                return
            }
            val purchaseResult = queryPurchases()
            if (purchaseResult.first.responseCode != BillingClient.BillingResponseCode.OK) {
                updateFailure(purchaseResult.first)
                if (emitRestoreResult) _events.emit(BillingEvent.ERROR)
                return
            }
            val evaluation = evaluate(purchaseResult.second)
            applyEvaluation(evaluation)

            val productResult = queryProduct()
            if (productResult.first.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = productResult.second.productDetailsList
                    .firstOrNull { it.productId == REMOVE_ADS_PRODUCT_ID }
                val offer = details?.let(::selectPurchaseOffer)
                if (details != null && offer != null) updateProduct(details, offer) else clearProduct()
            } else {
                clearProduct()
            }
            _state.update { it.copy(operation = BillingOperation.IDLE) }
            if (emitRestoreResult) {
                _events.emit(
                    when (evaluation) {
                        is PurchaseEvaluation.Purchased -> BillingEvent.RESTORED
                        PurchaseEvaluation.Pending -> BillingEvent.PENDING
                        PurchaseEvaluation.NotPurchased -> BillingEvent.NOTHING_TO_RESTORE
                        PurchaseEvaluation.Invalid -> BillingEvent.ERROR
                    },
                )
            }
        }
    }

    private suspend fun applyEvaluation(evaluation: PurchaseEvaluation) {
        when (evaluation) {
            is PurchaseEvaluation.Purchased -> {
                val purchase = evaluation.record.source as? Purchase ?: run {
                    updateTransientState(EntitlementState.ERROR)
                    return
                }
                if (purchase.isAcknowledged) {
                    updateVerified(EntitlementState.PURCHASED, acknowledged = true)
                } else {
                    updateVerified(EntitlementState.PURCHASED_UNACKNOWLEDGED, acknowledged = false)
                    val result = acknowledge(purchase.purchaseToken)
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        updateVerified(EntitlementState.PURCHASED, acknowledged = true)
                    }
                }
            }
            PurchaseEvaluation.Pending -> updateVerified(EntitlementState.PENDING, acknowledged = false)
            PurchaseEvaluation.NotPurchased ->
                updateVerified(EntitlementState.NOT_PURCHASED, acknowledged = false)
            PurchaseEvaluation.Invalid -> updateTransientState(EntitlementState.ERROR)
        }
    }

    private fun evaluate(purchases: List<Purchase>): PurchaseEvaluation = evaluateRemoveAdsPurchases(
        purchases.map { purchase ->
            BillingPurchaseRecord(
                productIds = purchase.products,
                state = when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> PurchaseRecordState.PURCHASED
                    Purchase.PurchaseState.PENDING -> PurchaseRecordState.PENDING
                    else -> PurchaseRecordState.OTHER
                },
                hasPurchaseToken = purchase.purchaseToken.isNotBlank(),
                quantity = purchase.quantity,
                acknowledged = purchase.isAcknowledged,
                source = purchase,
            )
        },
    )

    private suspend fun updateVerified(state: EntitlementState, acknowledged: Boolean) {
        val now = clock.millis()
        cache.write(state, now, acknowledged)
        val status = EntitlementStatus(
            state = state,
            lastVerifiedState = state,
            lastVerifiedAt = now,
            acknowledged = acknowledged,
        )
        _state.update { it.copy(entitlement = status) }
        workScheduler.sync(status)
    }

    private fun updateTransientState(state: EntitlementState) {
        val current = _state.value.entitlement
        val status = current.copy(state = state)
        _state.update { it.copy(entitlement = status, operation = BillingOperation.IDLE) }
        workScheduler.sync(status)
    }

    private fun updateFailure(result: BillingResult) {
        updateTransientState(
            if (result.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
                result.responseCode == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED
            ) {
                EntitlementState.UNAVAILABLE
            } else {
                EntitlementState.ERROR
            },
        )
    }

    private suspend fun loadCache() {
        if (cacheLoaded) return
        val cached = cache.read()
        val status = EntitlementStatus(
            state = EntitlementState.UNKNOWN,
            lastVerifiedState = cached.state,
            lastVerifiedAt = cached.verifiedAt,
            acknowledged = cached.acknowledged,
        )
        _state.update { it.copy(entitlement = status) }
        workScheduler.sync(status)
        cacheLoaded = true
    }

    private fun updateProduct(
        details: ProductDetails,
        offer: ProductDetails.OneTimePurchaseOfferDetails,
    ) {
        currentProductDetails = details
        currentOffer = offer
        _state.update {
            it.copy(product = BillingProduct(REMOVE_ADS_PRODUCT_ID, offer.formattedPrice))
        }
    }

    private fun clearProduct() {
        currentProductDetails = null
        currentOffer = null
        _state.update { it.copy(product = null) }
    }

    private fun selectPurchaseOffer(
        details: ProductDetails,
    ): ProductDetails.OneTimePurchaseOfferDetails? =
        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull {
                it.offerToken != null && it.rentalDetails == null && it.preorderDetails == null
            }
            ?: details.oneTimePurchaseOfferDetails?.takeIf { it.offerToken != null }

    private suspend fun ensureConnected(): BillingResult = connectionMutex.withLock {
        if (billingClient.isReady) return successResult()
        suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) continuation.resume(billingResult)
                    }

                    override fun onBillingServiceDisconnected() = Unit
                },
            )
        }
    }

    private suspend fun queryPurchases(): Pair<BillingResult, List<Purchase>> =
        suspendCancellableCoroutine { continuation ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (continuation.isActive) continuation.resume(result to purchases)
            }
        }

    private suspend fun queryProduct(): Pair<BillingResult, QueryProductDetailsResult> =
        suspendCancellableCoroutine { continuation ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(REMOVE_ADS_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
            billingClient.queryProductDetailsAsync(params) { result, details ->
                if (continuation.isActive) continuation.resume(result to details)
            }
        }

    private suspend fun acknowledge(purchaseToken: String): BillingResult =
        suspendCancellableCoroutine { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    private fun failureLaunchResult(result: BillingResult): BillingLaunchResult =
        if (result.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ||
            result.responseCode == BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED
        ) {
            BillingLaunchResult.BILLING_UNAVAILABLE
        } else {
            BillingLaunchResult.ERROR
        }

    private fun successResult(): BillingResult = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.OK)
        .build()
}
