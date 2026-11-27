package cn.sanchuang.jianqu.admission

import org.junit.Assert.assertEquals
import org.junit.Test

class LeaseCheckoutTest {
    @Test fun exampleAmounts() {
        assertEquals(listOf(180, 540, 1080, 2160), LeaseTerm.entries.map { it.exampleAmountYuan })
    }
    @Test fun confirmRequiresPending() {
        assertEquals(CheckoutPhase.IDLE, CheckoutPhase.IDLE.transition(CheckoutEvent.CONFIRM))
        assertEquals(CheckoutPhase.PENDING, CheckoutPhase.IDLE.transition(CheckoutEvent.START))
        assertEquals(CheckoutPhase.PENDING, CheckoutPhase.PENDING.transition(CheckoutEvent.RECHECK))
        assertEquals(CheckoutPhase.CONFIRMED, CheckoutPhase.PENDING.transition(CheckoutEvent.CONFIRM))
    }
    @Test fun failureTimeoutCancelCanRestartButCannotConfirmWithoutNewOrder() {
        listOf(CheckoutEvent.FAIL, CheckoutEvent.TIMEOUT, CheckoutEvent.CANCEL).forEach {
            val result = CheckoutPhase.PENDING.transition(it)
            assertEquals(result, result.transition(CheckoutEvent.CONFIRM))
            assertEquals(CheckoutPhase.PENDING, result.transition(CheckoutEvent.START))
        }
    }
}
