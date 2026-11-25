package cn.sanchuang.jianqu.demo.rental

import android.content.SharedPreferences

/**
 * Compatibility boundary for the existing local rental prototype.
 *
 * Keep the existing preference file and keys until the prototype is intentionally retired;
 * real rental/payment state must later come from a trusted service boundary instead.
 */
internal class DemoRentalPrefs(
    private val preferences: SharedPreferences
) {
    companion object {
        const val PREFERENCES_NAME = "jianqu_local_demo"
        private const val KEY_EXPIRY_MS = "rental_expiry_ms"
        private const val KEY_SERVICE_IDS = "rental_service_ids"
    }

    fun load(allowedServiceIds: Iterable<String>): DemoRentalState {
        val savedServiceIds =
            preferences.getStringSet(KEY_SERVICE_IDS, emptySet())?.toSet().orEmpty()
        return DemoRentalState(
            expiryMs = preferences.getLong(KEY_EXPIRY_MS, 0L),
            serviceIds = DemoRentalPolicy.sanitizeServiceIds(savedServiceIds, allowedServiceIds)
        )
    }

    fun save(state: DemoRentalState) {
        preferences.edit()
            .putLong(KEY_EXPIRY_MS, state.expiryMs)
            .putStringSet(KEY_SERVICE_IDS, state.serviceIds.toSet())
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_EXPIRY_MS)
            .remove(KEY_SERVICE_IDS)
            .apply()
    }
}
