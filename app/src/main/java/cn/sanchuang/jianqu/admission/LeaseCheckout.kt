package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.view.View

internal enum class LeaseTerm(val months: Int, val label: String) {
    MONTH(1, "月付"), QUARTER(3, "季付"), HALF_YEAR(6, "半年付"), YEAR(12, "年付");
    // Demonstration amounts only; production price must be returned by the order service.
    val exampleAmountYuan: Int get() = 180 * months
}

internal enum class CheckoutPhase { IDLE, PENDING, FAILED, TIMED_OUT, CANCELLED, CONFIRMED }
internal enum class CheckoutEvent { START, CANCEL, FAIL, TIMEOUT, CONFIRM, RECHECK }

internal fun CheckoutPhase.transition(event: CheckoutEvent): CheckoutPhase = when {
    event == CheckoutEvent.START && this != CheckoutPhase.PENDING && this != CheckoutPhase.CONFIRMED -> CheckoutPhase.PENDING
    this == CheckoutPhase.PENDING -> when (event) {
        CheckoutEvent.CANCEL -> CheckoutPhase.CANCELLED
        CheckoutEvent.FAIL -> CheckoutPhase.FAILED
        CheckoutEvent.TIMEOUT -> CheckoutPhase.TIMED_OUT
        CheckoutEvent.CONFIRM -> CheckoutPhase.CONFIRMED
        else -> this
    }
    else -> this
}

internal interface LeaseCheckout {
    val term: LeaseTerm
    val phase: CheckoutPhase
    fun choose(term: LeaseTerm)
    fun start()
    fun cancel()
    fun recheck()
    fun developmentActions(activity: Activity, changed: () -> Unit): View? = null
}
