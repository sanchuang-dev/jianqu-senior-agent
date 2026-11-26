package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquIcons
import cn.sanchuang.jianqu.R
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquTypography
import cn.sanchuang.jianqu.JianquDimensions
import cn.sanchuang.jianqu.LauncherSystemBars

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
        val headline = when (state) {
            DeviceAccess.NOT_RENTED -> if (pending) "即将开启新生活" else "美好生活，\n从见趣开始"
            DeviceAccess.EXPIRED -> "期待继续陪伴您"
            DeviceAccess.OFFLINE_BLOCKED -> "暂时无法验证租赁状态"
            else -> "正在检查设备状态"
        }
        intro(if (!wide) headline.replace("\n", "") else headline, if (wide) 34f else 28f,
            top = if (wide) 28 else 16, display = true)
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
        val root = FrameLayout(activity).apply { setBackgroundColor(ui.background) }
        root.addView(ImageView(activity).apply {
            setImageResource(R.drawable.jq_daylight_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(activity).apply {
            setBackgroundColor(activity.getColor(R.color.jq_lease_scrim))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(activity).apply { isFillViewport = true }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val frame = FrameLayout(activity).apply { setPadding(dp(20), dp(14), dp(20), dp(14)) }
        scroll.addView(frame)
        val shell = column()
        frame.addView(shell, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        LauncherSystemBars.insetContent(scroll)
        // The measured viewport excludes the navigation bar even when it is on the side.
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val available = (frame.width - frame.paddingLeft - frame.paddingRight).coerceAtLeast(1)
            val width = available.coerceAtMost(dp(if (wide) 1040 else 520))
            if (shell.layoutParams.width != width) {
                shell.layoutParams = shell.layoutParams.apply { this.width = width }
            }
        }
        val rentalPage = state == DeviceAccess.NOT_RENTED || state == DeviceAccess.EXPIRED
        val pending = rentalPage && checkout?.phase == CheckoutPhase.PENDING
        val intro = welcome(state, pending, developer != null)
        val card = column().apply {
            val padding = dp(if (wide) 18 else 20)
            setPadding(padding, padding, padding, padding)
        }
        card.background = LeaseFrostedBackground(root, card)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> card.invalidate() }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> card.invalidate() }

        if (rentalPage && checkout != null) {
            if (pending) payment(card, checkout, changed) else rental(card, checkout, state, changed)
        } else {
            copy(card, if (state == DeviceAccess.OFFLINE_BLOCKED) "连接网络后重试" else "设备状态验证",
                22f, display = true)
            copy(card, "暂时不能进入桌面。", 16f, ui.muted, 12)
            action(card, "重新检查", true, 20, retry)
        }

        // Secondary actions belong with the welcome copy, not in a full-width button bar.
        val utilities = row().apply { gravity = if (wide) Gravity.START else Gravity.CENTER }
        fun utility(icon: Int, title: String, description: String, click: () -> Unit) =
            JianquIcons.label(activity, icon, title, 15f, ui.ink, bold = false).apply {
                minimumHeight = dp(JianquDimensions.compactActionHeightDp)
                setPadding(dp(8), 0, dp(8), 0)
                background = utilityFeedback()
                contentDescription = description
                setOnClickListener { click() }
                isFocusable = true
            }
        fun addUtility(view: View) {
            utilities.addView(view, LinearLayout.LayoutParams(-2, dp(JianquDimensions.compactActionHeightDp)).apply {
                if (utilities.childCount > 0) leftMargin = dp(12)
            })
        }
        addUtility(utility(R.drawable.ic_jq_network, "网络", "网络设置", network))
        addUtility(utility(R.drawable.ic_jq_support, "客服", "扫码联系客服") { showSupport() })
        developer?.let {
            it.background = utilityFeedback()
            if (it is TextView) {
                it.text = "调试"
                it.textSize = 15f
                it.setTypeface(null, android.graphics.Typeface.NORMAL)
                it.setTextColor(ui.ink)
                val icon = activity.getDrawable(R.drawable.ic_jq_development)?.mutate()
                icon?.setTint(ui.ink)
                it.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
                it.compoundDrawablePadding = dp(7)
                it.gravity = Gravity.CENTER
                it.setPadding(dp(8), 0, dp(8), 0)
            }
            addUtility(it)
        }
        add(intro, utilities, 18)

        if (wide) {
            val content = row()
            content.addView(intro, LinearLayout.LayoutParams(0, -2, 0.95f))
            content.addView(card, LinearLayout.LayoutParams(0, -2, 1.05f).apply { leftMargin = dp(22) })
            add(shell, content)
        } else {
            add(shell, intro)
            add(shell, card, 12)
        }
        return root
    }

    private fun utilityFeedback() = android.graphics.drawable.RippleDrawable(
        android.content.res.ColorStateList.valueOf(activity.getColor(R.color.jq_ripple)),
        null,
        JianquDrawables.shape(activity, ui.surface, JianquDimensions.tileRadiusDp)
    )

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
                    background = JianquDrawables.touch(activity, if (selected) ui.accentSoft else activity.getColor(R.color.jq_lease_glass_choice),
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
        add(card, JianquIcons.label(activity, R.drawable.ic_jq_wechat, "微信扫码付款 · 演示", 14f,
            ui.muted, ui.wechatBrand, false), 8)
    }

    private fun payment(card: LinearLayout, checkout: LeaseCheckout, changed: () -> Unit) {
        add(card, JianquIcons.label(activity, R.drawable.ic_jq_wechat, "微信扫码付款 · 演示", 20f,
            ui.ink, ui.wechatBrand).apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL })
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

    private fun showSupport() {
        val body = column().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(12), dp(24), dp(10))
        }
        add(body, JianquIcons.label(activity, R.drawable.ic_jq_wechat, "微信扫码联系客服", 18f, ui.ink, ui.wechatBrand))
        val placeholder = FrameLayout(activity).apply {
            background = JianquDrawables.shape(activity, ui.surfaceSecondary, JianquDimensions.cardRadiusDp)
            addView(JianquIcons.image(activity, R.drawable.ic_jq_qr_code, ui.muted),
                FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER))
            contentDescription = "客服二维码尚未配置"
        }
        body.addView(placeholder, LinearLayout.LayoutParams(dp(148), dp(148)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
        })
        copy(body, "客服二维码尚未配置", 16f, ui.ink, 12, bold = true).gravity = Gravity.CENTER
        copy(body, "请联系设备提供方。当前图案不可扫描。", 14f, ui.muted, 8).gravity = Gravity.CENTER
        android.app.AlertDialog.Builder(activity)
            .setView(body)
            .setPositiveButton("关闭", null)
            .show()
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
