package com.mochisofts.mata.domain.model

const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

enum class EntitlementState(val code: String) {
    UNKNOWN("unknown"),
    NOT_PURCHASED("not_purchased"),
    PENDING("pending"),
    PURCHASED_UNACKNOWLEDGED("purchased_unacknowledged"),
    PURCHASED("purchased"),
    UNAVAILABLE("unavailable"),
    ERROR("error");

    val isVerified: Boolean
        get() = this == NOT_PURCHASED || this == PENDING ||
            this == PURCHASED_UNACKNOWLEDGED || this == PURCHASED

    val grantsAdRemoval: Boolean
        get() = this == PURCHASED_UNACKNOWLEDGED || this == PURCHASED

    companion object {
        fun fromStoredValue(value: String?): EntitlementState? =
            entries.firstOrNull { it.code == value }
    }
}

data class EntitlementStatus(
    val state: EntitlementState = EntitlementState.UNKNOWN,
    val lastVerifiedState: EntitlementState? = null,
    val lastVerifiedAt: Long? = null,
    val acknowledged: Boolean = false,
) {
    val adsRemoved: Boolean
        get() = state.grantsAdRemoval ||
            ((state == EntitlementState.ERROR || state == EntitlementState.UNAVAILABLE) &&
                lastVerifiedState?.grantsAdRemoval == true)

    val canShowAds: Boolean
        get() = when (state) {
            EntitlementState.NOT_PURCHASED,
            EntitlementState.PENDING,
            -> true
            EntitlementState.ERROR,
            EntitlementState.UNAVAILABLE,
            -> lastVerifiedState == EntitlementState.NOT_PURCHASED ||
                lastVerifiedState == EntitlementState.PENDING
            else -> false
        }
}

data class BillingProduct(
    val productId: String,
    val formattedPrice: String,
)

enum class BillingOperation {
    IDLE,
    REFRESHING,
    PURCHASING,
    RESTORING,
}

data class BillingState(
    val entitlement: EntitlementStatus = EntitlementStatus(),
    val product: BillingProduct? = null,
    val operation: BillingOperation = BillingOperation.IDLE,
)

enum class BillingLaunchResult {
    STARTED,
    ALREADY_IN_PROGRESS,
    PRODUCT_UNAVAILABLE,
    BILLING_UNAVAILABLE,
    ERROR,
}

enum class BillingEvent {
    PURCHASED,
    PENDING,
    USER_CANCELED,
    RESTORED,
    NOTHING_TO_RESTORE,
    ERROR,
}
