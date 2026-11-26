package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquTypography
import cn.sanchuang.jianqu.JianquDimensions

/** Standalone gate. It never grants access or writes a rental entitlement. */
internal class LeaseGateView(private val activity: Activity) {
    private val ui = JianquColors(activity)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + .5f).toInt()

    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun add(parent: LinearLayout, view: View, top: Int = 0) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun copy(parent: LinearLayout, value: String, size: Float, color: Int = ui.ink,
                     top: Int = 0, display: Boolean = false, bold: Boolean = false): TextView {
        val view = if (display) JianquTypography.display(activity, value, size, color)
            else JianquTypography.text(activity, value, size, color, bold)
        add(parent, view, top)
        return view
    }
    private fun action(parent: LinearLayout, title: String, primary: Boolean = false, top: Int = 12, run: () -> Unit) {
        add(parent, JianquTypography.text(activity, title, 18f, if (primary) ui.onAccent else ui.accentStrong, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(JianquDimensions.largeActionHeightDp)
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = JianquDrawables.touch(activity, if (primary) ui.accentStrong else ui.surface,
                stroke = if (primary) null else ui.border)
            setOnClickListener { run() }
            isFocusable = true
        }, top)
    }

    fun render(state: DeviceAccess, developer: View?, checkout: LeaseCheckout?,
               retry: () -> Unit, network: () -> Unit, changed: () -> Unit): View {
        val root = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(ui.background)
        }
        val frame = FrameLayout(activity).apply { setPadding(dp(22), dp(24), dp(22), dp(24)) }
        root.addView(frame)
        val body = column()
        val contentWidth = (activity.resources.displayMetrics.widthPixels - dp(44)).coerceAtLeast(dp(240))
            .coerceAtMost(dp(560))
        frame.addView(body, FrameLayout.LayoutParams(contentWidth, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        copy(body, "见趣", 22f, display = true)
        val title = when (state) {
            DeviceAccess.NOT_RENTED -> "开启你的见趣生活"
            DeviceAccess.EXPIRED -> "设备租赁已到期"
            DeviceAccess.OFFLINE_BLOCKED -> "暂时无法验证租赁状态"
            else -> "正在检查设备状态"
        }
        copy(body, title, JianquTypography.pageTitleSp, top = 22, display = true)
        val rentalPage = state == DeviceAccess.NOT_RENTED || state == DeviceAccess.EXPIRED
        copy(body, when (state) {
            DeviceAccess.NOT_RENTED -> "开通本机租赁服务后，即可进入桌面。"
            DeviceAccess.EXPIRED -> "请续租本机，恢复桌面使用。"
            DeviceAccess.OFFLINE_BLOCKED -> "请检查网络后重新验证。"
            else -> "请稍后重新检查设备状态。"
        }, 16f, ui.muted, 10)
        if (developer != null) copy(body, "本地开发 · 所有订单与授权均为模拟", 14f, ui.accentStrong, 10)
        if (rentalPage && checkout != null) {
            val card = column().apply {
                val p = dp(20)
                setPadding(p, p, p, p)
                background = JianquDrawables.shape(activity, ui.surface, JianquDimensions.cardRadiusDp, ui.border)
            }
            add(body, card, 20)
            if (checkout.phase == CheckoutPhase.PENDING) {
                copy(card, "微信扫码付款 · 演示", 22f, display = true)
                copy(card, "¥" + checkout.term.exampleAmountYuan, 42f, ui.accentStrong, 8, bold = true)
                copy(card, checkout.term.label + " · 等待付款确认", 16f, ui.muted, 8)
                val code = DemoCode(activity)
                card.addView(code, LinearLayout.LayoutParams(dp(152), dp(152)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(14)
                })
                copy(card, "演示图案 · 不可扫码付款", 15f, ui.accentStrong, 8, bold = true).gravity = Gravity.CENTER
                action(card, "重新查询付款状态", top = 14) { checkout.recheck(); changed() }
                checkout.developmentActions(activity, changed)?.let { add(card, it, 10) }
                action(card, "取消订单", top = 10) { checkout.cancel(); changed() }
            } else {
                copy(card, "设备租赁", 19f, bold = true)
                copy(card, "¥180 / 月", 38f, ui.accentStrong, 8, bold = true)
                copy(card, "示例设备 JQ-DEMO-01 · 演示金额，非真实订单", 14f, ui.muted, 8)
                when (checkout.phase) {
                    CheckoutPhase.FAILED -> copy(card, "模拟付款失败，请重新发起。", 16f, ui.danger, 12)
                    CheckoutPhase.TIMED_OUT -> copy(card, "模拟订单已超时，请重新发起。", 16f, ui.danger, 12)
                    CheckoutPhase.CANCELLED -> copy(card, "订单已取消（演示）。", 16f, ui.muted, 12)
                    else -> Unit
                }
                copy(card, "选择租期", 16f, top = 16, bold = true)
                LeaseTerm.entries.chunked(2).forEach { terms ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    terms.forEachIndexed { index, term ->
                        val selected = term == checkout.term
                        val choice = JianquTypography.text(activity, term.label + "  ¥" + term.exampleAmountYuan,
                            16f, if (selected) ui.accentStrong else ui.ink, selected).apply {
                            gravity = Gravity.CENTER
                            minHeight = dp(52)
                            background = JianquDrawables.touch(activity, if (selected) ui.accentSoft else ui.surface,
                                radius = JianquDimensions.tileRadiusDp, stroke = if (selected) ui.accentStrong else ui.border)
                            contentDescription = term.label + " ¥" + term.exampleAmountYuan + if (selected) " 已选" else ""
                            setOnClickListener { checkout.choose(term); changed() }
                            isFocusable = true
                        }
                        row.addView(choice, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(8) })
                    }
                    add(card, row, 8)
                }
                action(card, if (state == DeviceAccess.EXPIRED) "续租设备 · 演示" else "开通设备租赁 · 演示",
                    primary = true, top = 18) { checkout.start(); changed() }
                copy(card, "家人可协助扫码付款；当前仅开发演示。", 14f, ui.muted, 12)
            }
        } else {
            action(body, "重新检查", primary = true, top = 20, run = retry)
        }
        val tools = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        fun utility(title: String, click: () -> Unit) = JianquTypography.text(activity, title, 16f, ui.accentStrong, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(JianquDimensions.minActionHeightDp)
            background = JianquDrawables.touch(activity, ui.surface, stroke = ui.border)
            setOnClickListener { click() }
            isFocusable = true
        }
        tools.addView(utility("网络设置", network), LinearLayout.LayoutParams(0, -2, 1f))
        tools.addView(utility("联系客服") {
            android.app.AlertDialog.Builder(activity).setTitle("客服暂未接入")
                .setMessage("请联系设备提供方。紧急情况请使用可靠电话或联系现场人员。")
                .setPositiveButton("知道了", null).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        add(body, tools, 18)
        copy(body, "紧急情况请使用可靠电话或联系现场人员。", 14f, ui.muted, 10)
        developer?.let { add(body, it, 12) }
        return root
    }

    /** Deliberately non-decodable placeholder, never a payment QR or URL. */
    private class DemoCode(activity: Activity) : View(activity) {
        private val colors = JianquColors(activity)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init { contentDescription = "不可扫描的演示图案"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(colors.surfaceSecondary)
            val cell = width / 17f
            paint.color = colors.ink
            for (y in 2..14) for (x in 2..14) {
                if ((x * 7 + y * 11 + x * y) % 5 < 2 && x + y !in 13..19) {
                    canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
                }
            }
            paint.color = colors.accentStrong
            paint.strokeWidth = cell * 1.4f
            canvas.drawLine(cell * 3, cell * 14, cell * 14, cell * 3, paint)
        }
    }
}
