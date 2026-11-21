package cn.sanchuang.jianqu

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * JQ-010: the store target has not been approved.
 *
 * This is a local, non-transactional handoff surface. It never accesses an
 * external store, asks for account credentials, or claims a purchase succeeded.
 * Example failure states are manually selected and are NOT network diagnostics.
 */
class StorefrontActivity : Activity() {
    private enum class State { NOT_CONFIGURED, LOGIN_EXAMPLE, DEVICE_OFFLINE_EXAMPLE,
        NETWORK_EXAMPLE, STORE_UNAVAILABLE_EXAMPLE }

    private val ui = LauncherUiTokens
    private val ink = ui.ink
    private val muted = ui.muted
    private val accent = ui.terracotta
    private var state = State.NOT_CONFIGURED
    private var resumeNeedsRedraw = false

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun wide() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 650

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A shared device must never restore a previous user's example session.
        state = State.NOT_CONFIGURED
        draw()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        draw()
    }

    override fun onStop() {
        super.onStop()
        state = State.NOT_CONFIGURED
        resumeNeedsRedraw = true
    }

    override fun onResume() {
        super.onResume()
        if (resumeNeedsRedraw) {
            resumeNeedsRedraw = false
            draw()
        }
    }

    @Deprecated("The store handoff always returns directly to HOME.")
    override fun onBackPressed() = leave()

    private fun leave() {
        state = State.NOT_CONFIGURED
        finish()
    }

    private fun choose(next: State) {
        state = next
        draw()
    }

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun line(parent: LinearLayout, value: String, size: Float = 16f,
                     color: Int = ink, bold: Boolean = false, top: Int = 10): TextView =
        text(value, size, color, bold).also {
            parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(top)
            })
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(if (wide()) 21 else 18)
        setPadding(padding, dp(19), padding, dp(20))
        background = ui.shape(this@StorefrontActivity, ui.surface, ui.cardRadiusDp, ui.divider)
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) =
        text(label, 16f, if (primary) Color.WHITE else accent, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(ui.minActionHeightDp)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = ui.touch(this@StorefrontActivity,
                if (primary) accent else ui.surface,
                ui.cardRadiusDp,
                if (primary) null else ui.divider)
            isClickable = true
            isFocusable = true
            contentDescription = label
            setOnClickListener { onClick() }
        }

    private fun addButton(parent: LinearLayout, label: String, primary: Boolean = false,
                          top: Int = 11, onClick: () -> Unit) {
        parent.addView(button(label, primary, onClick),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(5), dp(16), dp(5))
        addView(button("‹") { leave() }.apply {
            textSize = 31f
            contentDescription = "返回桌面"
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(text("精选商城", 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                leftMargin = dp(14)
            })
        if (wide()) {
            addView(text("本地承接原型 · 不可交易", 14f, accent, true))
        }
    }

    private fun introduction() = card().apply {
        val mark = FrameLayout(this@StorefrontActivity).apply {
            background = ui.shape(this@StorefrontActivity,
                ui.storeIcon, ui.iconWellRadiusDp)
        }
        mark.addView(ImageView(this@StorefrontActivity).apply {
            setImageResource(R.drawable.ic_home_shopping_bag_open)
            imageTintList = ColorStateList.valueOf(ink)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        addView(mark, LinearLayout.LayoutParams(dp(68), dp(68)))

        line(this, "精选商城", if (wide()) ui.headingCompactSp else ui.headingLargeSp,
            ink, true, 16)
        line(this, "现有商城的统一入口", 17f, ink, true, 10)
        line(this, "商品、订单和付款由现有商城负责，Launcher 不处理交易。",
            16f, muted, top = 12)
        line(this, "当前未指定商城 App 或网页地址。", 15f, accent, true, 18)
    }

    private fun stateTitle() = when (state) {
        State.NOT_CONFIGURED -> "商城尚未接入"
        State.LOGIN_EXAMPLE -> "演示：需要登录"
        State.DEVICE_OFFLINE_EXAMPLE -> "演示：设备未联网"
        State.NETWORK_EXAMPLE -> "演示：网络中断"
        State.STORE_UNAVAILABLE_EXAMPLE -> "演示：商城暂不可用"
    }

    private fun stateExplanation() = when (state) {
        State.NOT_CONFIGURED ->
            "尚未配置经过确认的商城目标，目前不能浏览商品、下单或支付。"
        State.LOGIN_EXAMPLE ->
            "此处只演示未登录提示；没有接入商城账号，也不会让你输入密码。"
        State.DEVICE_OFFLINE_EXAMPLE ->
            "设备未联网时，应先检查系统网络。这只是手动选择的示例，并非设备检测结果。"
        State.NETWORK_EXAMPLE ->
            "连接中断时应保留返回路径。本页没有发送请求，也未真实检测网络。"
        State.STORE_UNAVAILABLE_EXAMPLE ->
            "商城服务不可用时应清晰告知。本页并未检查远端服务的运行状态。"
    }

    private fun statusPanel() = card().apply {
        line(this, if (state == State.NOT_CONFIGURED) "当前接入状态" else "异常状态演示",
            14f, accent, true, 0)
        line(this, stateTitle(), ui.headingCompactSp, ink, true, 13)
        line(this, stateExplanation(), 16f, muted, top = 12)

        if (state != State.NOT_CONFIGURED) {
            line(this, "模拟界面 · 非实时检测 · 未执行任何交易", 14f, accent,
                true, 13)
            addButton(this, "恢复实际状态：尚未接入", top = 17) {
                choose(State.NOT_CONFIGURED)
            }
        } else {
            line(this, "接入状态：无目标 · 无登录 · 无交易", 14f, muted, top = 15)
        }
        addButton(this, "返回桌面", primary = true, top = 18) { leave() }

        line(this, "查看异常状态示例", 18f, ink, true, 20)
        line(this, "选择后仅切换界面，不代表真实检测或登录。", 14f, muted, top = 7)

        val options = listOf(
            "未登录示例" to State.LOGIN_EXAMPLE,
            "设备离线示例" to State.DEVICE_OFFLINE_EXAMPLE,
            "断网示例" to State.NETWORK_EXAMPLE,
            "商城不可用示例" to State.STORE_UNAVAILABLE_EXAMPLE
        )
        options.chunked(2).forEach { pair ->
            val row = LinearLayout(this@StorefrontActivity).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            pair.forEachIndexed { i, item ->
                row.addView(button(item.first) { choose(item.second) },
                    LinearLayout.LayoutParams(0, dp(ui.minActionHeightDp), 1f).apply {
                        if (i > 0) leftMargin = dp(9)
                    })
            }
            addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(9)
            })
        }
    }

    private fun draw() {
        val wideScreen = wide()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
        }
        root.addView(header(), LinearLayout.LayoutParams(-1, dp(72)))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val columns = LinearLayout(this).apply {
            orientation = if (wideScreen) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(if (wideScreen) 18 else 15), dp(4),
                dp(if (wideScreen) 18 else 15), dp(20))
        }
        if (wideScreen) {
            columns.addView(introduction(), LinearLayout.LayoutParams(0, -2, .82f).apply {
                rightMargin = dp(14)
            })
            columns.addView(statusPanel(), LinearLayout.LayoutParams(0, -2, 1.18f))
        } else {
            columns.addView(introduction(), LinearLayout.LayoutParams(-1, -2))
            columns.addView(statusPanel(), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            })
        }
        scroll.addView(columns)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text(
            "原型展示 · 无商城连接、真实商品、订单或支付能力",
            12f, muted
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
        }, LinearLayout.LayoutParams(-1, dp(36)))
        setContentView(root)
    }
}
