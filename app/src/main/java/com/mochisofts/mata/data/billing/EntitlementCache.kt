package com.mochisofts.mata.data.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mochisofts.mata.domain.model.EntitlementState
import com.mochisofts.mata.domain.model.REMOVE_ADS_PRODUCT_ID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

internal data class CachedEntitlement(
    val state: EntitlementState?,
    val verifiedAt: Long?,
    val acknowledged: Boolean,
)

@Singleton
class EntitlementCache @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    internal suspend fun read(): CachedEntitlement {
        val preferences = dataStore.data.first()
        val productId = preferences[PRODUCT_ID]
        return if (productId == null || productId == REMOVE_ADS_PRODUCT_ID) {
            CachedEntitlement(
                state = EntitlementState.fromStoredValue(preferences[STATE])
                    ?.takeIf(EntitlementState::isVerified),
                verifiedAt = preferences[VERIFIED_AT],
                acknowledged = preferences[ACKNOWLEDGED] ?: false,
            )
        } else {
            CachedEntitlement(null, null, false)
        }
    }

    internal suspend fun write(state: EntitlementState, verifiedAt: Long, acknowledged: Boolean) {
        require(state.isVerified)
        dataStore.edit { preferences ->
            preferences[PRODUCT_ID] = REMOVE_ADS_PRODUCT_ID
            preferences[STATE] = state.code
            preferences[VERIFIED_AT] = verifiedAt
            preferences[ACKNOWLEDGED] = acknowledged
        }
    }

    private companion object {
        val PRODUCT_ID = stringPreferencesKey("billing_product_id")
        val STATE = stringPreferencesKey("billing_last_verified_state")
        val VERIFIED_AT = longPreferencesKey("billing_last_verified_at")
        val ACKNOWLEDGED = booleanPreferencesKey("billing_acknowledged")
    }
}
