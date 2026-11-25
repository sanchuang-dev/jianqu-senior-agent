package cn.sanchuang.jianqu.demo.rental

/**
 * Local-only rental state used by the current prototype.
 *
 * This is deliberately named Demo: it is not a server entitlement, payment fact, member
 * record, or production authorization source.
 */
internal data class DemoRentalState(
    val expiryMs: Long = 0L,
    val serviceIds: Set<String> = emptySet()
) {
    fun isActive(nowMs: Long): Boolean = expiryMs > nowMs
}

internal object DemoRentalPolicy {
    const val DEVICE_ID = "JQ-DEMO-01"
    const val TERM_DAYS = 30L
    const val TERM_MS = TERM_DAYS * 24L * 60L * 60L * 1000L

    fun sanitizeServiceIds(
        serviceIds: Set<String>,
        allowedServiceIds: Iterable<String>
    ): Set<String> =
        allowedServiceIds.filterTo(linkedSetOf()) { it in serviceIds }

    fun renew(
        current: DemoRentalState,
        nowMs: Long,
        selectedServiceIds: Set<String>,
        allowedServiceIds: Iterable<String>
    ): DemoRentalState =
        DemoRentalState(
            expiryMs = maxOf(nowMs, current.expiryMs) + TERM_MS,
            serviceIds = sanitizeServiceIds(selectedServiceIds, allowedServiceIds)
        )
}
