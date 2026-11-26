package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquTypography
import cn.sanchuang.jianqu.JianquDimensions

/** Standalone gate. No access to normal Launcher navigation. */
internal class LeaseGateView(private val activity: Activity) {
    private val ui = JianquColors(activity)
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + .5f).toInt()

    fun render(state: DeviceAccess, developer: View?, retry: () -> Unit, network: () -> Unit): View {
        val root = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(ui.background)
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        root.addView(body)
        fun line(value: String, size: Float, color: Int, top: Int, display: Boolean = false) {
            val view = if (display) JianquTypography.display(activity, value, size, color)
                else JianquTypography.text(activity, value, size, color, false)
            view.gravity = Gravity.CENTER
            body.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        }
        fun action(value: String, run: () -> Unit) {
            body.addView(JianquTypography.text(activity, value, 18f, ui.accentStrong, true).apply {
                gravity = Gravity.CENTER
                minHeight = dp(JianquDimensions.largeActionHeightDp)
                background = JianquDrawables.touch(activity, ui.surface, stroke = ui.border)
                setOnClickListener { run() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        }
        line("见趣", 24f, ui.ink, 0, true)
        val title = when (state) {
            DeviceAccess.NOT_RENTED -> "开启设备租赁"
            DeviceAccess.EXPIRED -> "设备租赁已到期"
            DeviceAccess.OFFLINE_BLOCKED -> "暂时无法验证租赁状态"
            else -> "正在检查设备状态"
        }
        line(title, JianquTypography.pageTitleSp, ui.ink, 36, true)
        line("当前尚未进入见趣桌面。", 17f, ui.muted, 16)
        if (developer != null) line("本地开发 · 所有授权状态均为模拟", 14f, ui.accentStrong, 16)
        action("重新检查", retry)
        action("网络设置", network)
        line("如遇紧急情况，请使用可靠电话或联系现场人员。", 15f, ui.muted, 24)
        developer?.let { body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) }) }
        return root
    }
}
