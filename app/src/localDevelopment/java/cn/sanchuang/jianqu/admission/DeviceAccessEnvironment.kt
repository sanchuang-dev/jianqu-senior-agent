package cn.sanchuang.jianqu.admission

import cn.sanchuang.jianqu.JianquDimensions

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquTypography

internal object DeviceAccessEnvironment {
    fun open(context: Context): DeviceAccessSource = LocalDeviceAccess(context.applicationContext)
}

/** Only compiled into the separately identified localDevelopment APK. Never reads legacy rental prefs. */
private class LocalDeviceAccess(context: Context) : DeviceAccessSource {
    private val preferences = context.getSharedPreferences("local_admission_scenarios", Context.MODE_PRIVATE)
    private val scenarios = listOf(
        DeviceAccess.NOT_RENTED to "尚未租赁",
        DeviceAccess.ACTIVE to "租赁有效",
        DeviceAccess.EXPIRED to "租赁已到期",
        DeviceAccess.OFFLINE_BLOCKED to "离线 · 无有效凭证",
        DeviceAccess.OFFLINE_VALID to "离线 · 有效凭证（模拟）",
        DeviceAccess.CHECKING to "检查中（模拟）"
    )
    override fun current(): DeviceAccess = scenarios.firstOrNull {
        it.first.name == preferences.getString("scenario", null)
    }?.first ?: DeviceAccess.NOT_RENTED

    private val localCheckout = LocalCheckout { select(DeviceAccess.ACTIVE, resetCheckout = false) }
    override fun checkout(): LeaseCheckout = localCheckout

    private fun select(state: DeviceAccess, resetCheckout: Boolean = true) {
        preferences.edit().putString("scenario", state.name).apply()
        if (resetCheckout) localCheckout.reset()
    }

    override fun developerAction(activity: Activity, changed: () -> Unit): () -> Unit = {
        AlertDialog.Builder(activity)
            .setTitle("设备租赁 · 开发场景")
            .setSingleChoiceItems(scenarios.map { it.second }.toTypedArray(),
                scenarios.indexOfFirst { it.first == current() }) { dialog, index ->
                select(scenarios[index].first)
                dialog.dismiss()
                changed()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun developerControl(activity: Activity, changed: () -> Unit): View {
        val ui = JianquColors(activity)
        val open = developerAction(activity, changed)
        return JianquTypography.text(activity, "开发调试", JianquTypography.supportingSp, ui.accentStrong, true).apply {
            gravity = Gravity.CENTER
            minHeight = (JianquDimensions.compactActionHeightDp * resources.displayMetrics.density + .5f).toInt()
            background = JianquDrawables.touch(activity, ui.surface, stroke = ui.border)
            isClickable = true
            isFocusable = true
            contentDescription = "设备租赁开发调试"
            setOnClickListener { open() }
        }
    }
}

/** Fake order service, restricted to the localDevelopment source set. */
private class LocalCheckout(private val confirmed: () -> Unit) : LeaseCheckout {
    override var term = LeaseTerm.MONTH
        private set
    override var phase = CheckoutPhase.IDLE
        private set

    fun reset() {
        term = LeaseTerm.MONTH
        phase = CheckoutPhase.IDLE
    }
    override fun choose(term: LeaseTerm) {
        if (phase != CheckoutPhase.PENDING && phase != CheckoutPhase.CONFIRMED) this.term = term
    }
    override fun start() { phase = phase.transition(CheckoutEvent.START) }
    override fun cancel() { phase = phase.transition(CheckoutEvent.CANCEL) }
    override fun recheck() { phase = phase.transition(CheckoutEvent.RECHECK) }

    override fun developmentActions(activity: Activity, changed: () -> Unit): View? {
        if (phase != CheckoutPhase.PENDING) return null
        val ui = JianquColors(activity)
        return JianquTypography.text(activity, "模拟付款结果", JianquTypography.bodySp, ui.accentStrong, true).apply {
            gravity = Gravity.CENTER
            minHeight = (JianquDimensions.largeActionHeightDp * resources.displayMetrics.density + .5f).toInt()
            background = JianquDrawables.touch(activity, ui.accentSoft, stroke = ui.accentSoftBorder)
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle("开发测试 · 不产生真实付款")
                    .setItems(arrayOf("模拟服务端确认成功", "模拟付款失败", "模拟订单超时")) { _, index ->
                        val event = listOf(CheckoutEvent.CONFIRM, CheckoutEvent.FAIL, CheckoutEvent.TIMEOUT)[index]
                        phase = phase.transition(event)
                        if (phase == CheckoutPhase.CONFIRMED) confirmed()
                        changed()
                    }.setNegativeButton("取消", null).show()
            }
            isFocusable = true
        }
    }
}
