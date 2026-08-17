package com.mochisofts.mata.data.billing

import com.mochisofts.mata.domain.model.REMOVE_ADS_PRODUCT_ID

internal enum class PurchaseRecordState {
    PURCHASED,
    PENDING,
    OTHER,
}

internal data class BillingPurchaseRecord(
    val productIds: List<String>,
    val state: PurchaseRecordState,
    val hasPurchaseToken: Boolean,
    val quantity: Int,
    val acknowledged: Boolean,
    val source: Any? = null,
)

internal sealed interface PurchaseEvaluation {
    data class Purchased(val record: BillingPurchaseRecord) : PurchaseEvaluation
    data object Pending : PurchaseEvaluation
    data object NotPurchased : PurchaseEvaluation
    data object Invalid : PurchaseEvaluation
}

internal fun evaluateRemoveAdsPurchases(
    records: List<BillingPurchaseRecord>,
): PurchaseEvaluation {
    val matching = records.filter { REMOVE_ADS_PRODUCT_ID in it.productIds }
    val valid = matching.filter { it.hasPurchaseToken && it.quantity == 1 }
    valid.firstOrNull { it.state == PurchaseRecordState.PURCHASED }?.let {
        return PurchaseEvaluation.Purchased(it)
    }
    if (valid.any { it.state == PurchaseRecordState.PENDING }) return PurchaseEvaluation.Pending
    if (matching.any { it.state == PurchaseRecordState.PURCHASED || it.state == PurchaseRecordState.PENDING }) {
        return PurchaseEvaluation.Invalid
    }
    return PurchaseEvaluation.NotPurchased
}
