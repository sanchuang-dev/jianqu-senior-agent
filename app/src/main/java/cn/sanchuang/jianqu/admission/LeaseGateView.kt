package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.content.res.Configuration
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
import cn.sanchuang.jianqu.companionOrb

/** Standalone gate. It never grants access or writes a rental entitlement. */
internal class LeaseGateView(private val activity: Activity) {
    private val ui = JianquColors(activity)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + .5f).toInt()
    private val wide get() = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        activity.resources.configuration.screenWidthDp >= 660

    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
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
    private fun button(title: String, primary: Boolean, run: () -> Unit): TextView =
        JianquTypography.text(activity, title, 17f, if (primary) ui.onAccent else ui.ink, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(if (primary) JianquDimensions.largeActionHeightDp else JianquDimensions.compactActionHeightDp)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = JianquDrawables.touch(activity, if (primary) ui.accentStrong else ui.surface,
                stroke = if (primary) null else ui.border)
            setOnClickListener { run() }
            isFocusable = true
        }
    private fun action(parent: LinearLayout, title: String, primary: Boolean = false, top: Int = 10, run: () -> Unit) =
        add(parent, button(title, primary, run), top)

    private fun welcome(state: DeviceAccess, pending: Boolean, developer: Boolean): LinearLayout {
        val pane = column().apply {
            gravity = if (wide) Gravity.CENTER_VERTICAL else Gravity.CENTER_HORIZONTAL
            setPadding(dp(if (wide) 22 else 8), dp(8), dp(if (wide) 24 else 8), dp(8))
        }
        fun intro(value: String, size: Float, color: Int = ui.ink, top: Int = 0, display: Boolean = false, bold: Boolean = false) =
            copy(pane, value, size, color, top, display, bold).apply {
                gravity = if (wide) Gravity.START else Gravity.CENTER
            }
        intro("见趣 · 闺蜜机", 18f, bold = true)
        val halo = FrameLayout(activity).apply {
            background = JianquDrawables.shape(activity, ui.accentSoft, 100)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val ring = View(activity).apply { background = JianquDrawables.shape(activity, ui.accentSoftBorder, 100) }
        val orbSize = if (wide) 78 else 60
        halo.addView(ring, FrameLayout.LayoutParams(dp(orbSize + 30), dp(orbSize + 30), Gravity.CENTER))
        halo.addView(companionOrb(activity), FrameLayout.LayoutParams(dp(orbSize), dp(orbSize), Gravity.CENTER))
        val haloSize = orbSize + 56
        pane.addView(halo, LinearLayout.LayoutParams(dp(haloSize), dp(haloSize)).apply {
            topMargin = dp(if (wide) 22 else 16)
            gravity = if (wide) Gravity.START else Gravity.CENTER_HORIZONTAL
        })
        val headline = when (state) {
            DeviceAccess.NOT_RENTED -> if (pending) "即将开启新生活" else "美好生活，\n从见趣开始"
            DeviceAccess.EXPIRED -> "期待继续陪伴您"
            DeviceAccess.OFFLINE_BLOCKED -> "暂时无法验证租赁状态"
            else -> "正在检查设备状态"
        }
        intro(if (!wide) headline.replace("\n", "") else headline, if (wide) 34f else 28f,
            top = 16, display = true)
        intro(when (state) {
            DeviceAccess.NOT_RENTED -> "开通本机租赁，即可使用见趣桌面。"
            DeviceAccess.EXPIRED -> "设备租赁已到期，续租后即可恢复使用。"
            DeviceAccess.OFFLINE_BLOCKED -> "请检查网络后重新验证。"
            else -> "请稍后重新检查设备状态。"
        }, 16f, ui.muted, 12)
        if (developer) intro("本地开发 · 所有订单与授权均为模拟", 13f, ui.muted, 16)
        return pane
    }

    fun render(state: DeviceAccess, developer: View?, checkout: LeaseCheckout?,
               retry: () -> Unit, network: () -> Unit, changed: () -> Unit): View {
        val root = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(ui.background)
        }
        val frame = FrameLayout(activity).apply { setPadding(dp(20), dp(14), dp(20), dp(14)) }
        root.addView(frame)
        val shell = column()
        val available = (activity.resources.displayMetrics.widthPixels - dp(40)).coerceAtLeast(dp(200))
        frame.addView(shell, FrameLayout.LayoutParams(available.coerceAtMost(dp(if (wide) 1040 else 520)),
            -2, Gravity.CENTER))
        val rentalPage = state == DeviceAccess.NOT_RENTED || state == DeviceAccess.EXPIRED
        val pending = rentalPage && checkout?.phase == CheckoutPhase.PENDING
        val intro = welcome(state, pending, developer != null)
        val card = column().apply {
            val padding = dp(if (wide) 18 else 20)
            setPadding(padding, padding, padding, padding)
            background = JianquDrawables.shape(activity, ui.surface, JianquDimensions.companionRadiusDp, ui.border)
        }

        if (rentalPage && checkout != null) {
            if (pending) payment(card, checkout, changed) else rental(card, checkout, state, changed)
        } else {
            copy(card, if (state == DeviceAccess.OFFLINE_BLOCKED) "连接网络后重试" else "设备状态验证",
                22f, display = true)
            copy(card, "暂时不能进入桌面。", 16f, ui.muted, 12)
            action(card, "重新检查", true, 20, retry)
        }

        if (wide) {
            val content = row()
            content.addView(intro, LinearLayout.LayoutParams(0, -2, 0.95f))
            content.addView(card, LinearLayout.LayoutParams(0, -2, 1.05f).apply { leftMargin = dp(22) })
            add(shell, content)
        } else {
            add(shell, intro)
            add(shell, card, 12)
        }
        val footer = row().apply { gravity = Gravity.CENTER }
        fun utility(title: String, click: () -> Unit) = JianquTypography.text(activity, title, 14f, ui.muted, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(JianquDimensions.compactActionHeightDp)
            background = JianquDrawables.touch(activity, ui.background, JianquDimensions.tileRadiusDp)
            setOnClickListener { click() }
            isFocusable = true
        }
        footer.addView(utility("网络设置", network), LinearLayout.LayoutParams(0, -2, 1f))
        footer.addView(utility("联系客服") {
            android.app.AlertDialog.Builder(activity).setTitle("客服暂未接入")
                .setMessage("请联系设备提供方。紧急情况请使用可靠电话或联系现场人员。")
                .setPositiveButton("知道了", null).show()
        }, LinearLayout.LayoutParams(0, -2, 1f))
        developer?.let {
            it.background = JianquDrawables.touch(activity, ui.background, JianquDimensions.tileRadiusDp)
            if (it is TextView) it.setTextColor(ui.muted)
            footer.addView(it, LinearLayout.LayoutParams(0, -2, 1f))
        }
        add(shell, footer, 8)
        copy(shell, "紧急情况请使用可靠电话或联系现场人员。", 13f, ui.muted).gravity = Gravity.CENTER
        return root
    }

    private fun rental(card: LinearLayout, checkout: LeaseCheckout, state: DeviceAccess, changed: () -> Unit) {
        copy(card, "设备租赁", 19f, bold = true)
        val price = row()
        price.addView(JianquTypography.text(activity, "¥" + LeaseTerm.MONTH.exampleAmountYuan, 42f, ui.ink, true))
        price.addView(JianquTypography.text(activity, " / 月", 17f, ui.muted, false))
        add(card, price, 3)
        copy(card, "本机 JQ-DEMO-01 · 演示设备", 13f, ui.muted, 2)
        when (checkout.phase) {
            CheckoutPhase.FAILED -> copy(card, "模拟付款失败，请重新发起。", 15f, ui.danger, 8)
            CheckoutPhase.TIMED_OUT -> copy(card, "模拟订单已超时，请重新发起。", 15f, ui.danger, 8)
            CheckoutPhase.CANCELLED -> copy(card, "订单已取消（演示）。", 15f, ui.muted, 8)
            else -> Unit
        }
        copy(card, "选择租期", 15f, ui.muted, 12, bold = true)
        LeaseTerm.entries.chunked(2).forEach { terms ->
            val choices = row()
            terms.forEachIndexed { index, term ->
                val selected = term == checkout.term
                val choice = JianquTypography.text(activity, term.label + "  ¥" + term.exampleAmountYuan,
                    15f, if (selected) ui.accentStrong else ui.ink, selected).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(JianquDimensions.compactActionHeightDp)
                    background = JianquDrawables.touch(activity, if (selected) ui.accentSoft else ui.surfaceSecondary,
                        radius = JianquDimensions.tileRadiusDp, stroke = if (selected) ui.accentStrong else null)
                    contentDescription = term.label + " ¥" + term.exampleAmountYuan + if (selected) " 已选" else ""
                    setOnClickListener { checkout.choose(term); changed() }
                    isFocusable = true
                }
                choices.addView(choice, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(8) })
            }
            add(card, choices, 6)
        }
        action(card, (if (state == DeviceAccess.EXPIRED) "续租" else "立即开通") + " · ¥" +
            checkout.term.exampleAmountYuan, true, 12) { checkout.start(); changed() }
        copy(card, "支持家人微信扫码付款 · 当前仅演示", 13f, ui.muted, 8).gravity = Gravity.CENTER
    }

    private fun payment(card: LinearLayout, checkout: LeaseCheckout, changed: () -> Unit) {
        copy(card, "微信扫码付款 · 演示", 20f, bold = true)
        val summary = row()
        val code = DemoCode(activity)
        val size = if (wide) 98 else 128
        summary.addView(code, LinearLayout.LayoutParams(dp(size), dp(size)))
        val details = column()
        copy(details, "¥" + checkout.term.exampleAmountYuan, if (wide) 34f else 38f, ui.ink, bold = true)
        copy(details, checkout.term.label + " · 等待付款确认", 15f, ui.muted, 5)
        copy(details, "不可扫码付款", 14f, ui.accentStrong, 5, bold = true)
        summary.addView(details, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(14) })
        add(card, summary, 12)
        action(card, "重新查询付款状态", true, 12) { checkout.recheck(); changed() }
        checkout.developmentActions(activity, changed)?.let { add(card, it, 8) }
        val cancel = JianquTypography.text(activity, "取消订单", 15f, ui.muted, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(JianquDimensions.compactActionHeightDp)
            background = JianquDrawables.touch(activity, ui.surface)
            setOnClickListener { checkout.cancel(); changed() }
            isFocusable = true
        }
        add(card, cancel, 4)
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
