package cn.sanchuang.jianqu.demo.rental

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoRentalPolicyTest {
    private val allowedServiceIds = listOf("companion", "wellness", "cinema", "family", "local")

    @Test
    fun inactiveRentalStartsFromCurrentTime() {
        val now = 1_000_000L

        val renewed = DemoRentalPolicy.renew(
            current = DemoRentalState(),
            nowMs = now,
            selectedServiceIds = setOf("companion"),
            allowedServiceIds = allowedServiceIds
        )

        assertEquals(now + DemoRentalPolicy.TERM_MS, renewed.expiryMs)
        assertEquals(linkedSetOf("companion"), renewed.serviceIds)
    }

    @Test
    fun activeRentalExtendsFromExistingExpiry() {
        val now = 1_000_000L
        val currentExpiry = now + 5_000L

        val renewed = DemoRentalPolicy.renew(
            current = DemoRentalState(expiryMs = currentExpiry),
            nowMs = now,
            selectedServiceIds = setOf("family", "wellness"),
            allowedServiceIds = allowedServiceIds
        )

        assertEquals(currentExpiry + DemoRentalPolicy.TERM_MS, renewed.expiryMs)
        assertEquals(linkedSetOf("wellness", "family"), renewed.serviceIds)
    }

    @Test
    fun unknownServiceIdsAreDiscardedInCatalogOrder() {
        val sanitized = DemoRentalPolicy.sanitizeServiceIds(
            serviceIds = linkedSetOf("unknown", "local", "companion"),
            allowedServiceIds = allowedServiceIds
        )

        assertEquals(linkedSetOf("companion", "local"), sanitized)
    }
}
