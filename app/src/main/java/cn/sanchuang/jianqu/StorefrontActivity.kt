package cn.sanchuang.jianqu

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Shared visual shell for the two local, non-transactional service prototypes. */
internal abstract class ServicePreviewActivity : Activity() {
    protected val ui = JianquColors(this)
    protected fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    protected fun wide() = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels &&
        resources.displayMetrics.widthPixels / resources.displayMetrics.density >= 650f
    protected fun text(value: String, size: Float = JianquTypography.bodySp, color: Int = ui.ink, bold: Boolean = false) =
        JianquTypography.text(this, value, size, color, bold)
    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    protected fun line(parent: LinearLayout, value: String, size: Float = JianquTypography.bodySp,
                       color: Int = ui.ink, bold: Boolean = false, top: Int = 8): TextView =
        text(value, size, color, bold).also { add(parent, it, top) }
    protected fun add(parent: LinearLayout, view: View, top: Int = 10) {
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    protected fun panel(color: Int = ui.surface, border: Int? = ui.border) = column().apply {
        setPadding(dp(JianquSpacing.space20Dp), dp(19), dp(JianquSpacing.space20Dp), dp(JianquSpacing.space20Dp))
        background = JianquDrawables.shape(this@ServicePreviewActivity, color, 18, border)
    }
    protected fun action(parent: LinearLayout, title: String, desc: String, icon: Int, tint: Int,
                         color: Int = ui.ink, click: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(74); setPadding(dp(JianquSpacing.space12Dp), dp(JianquSpacing.space10Dp), dp(JianquSpacing.space12Dp), dp(JianquSpacing.space10Dp))
            background = JianquDrawables.touch(this@ServicePreviewActivity, ui.surface, 14, ui.border)
            isClickable = true; isFocusable = true; contentDescription = "$title，$desc"
            setOnClickListener { click() }
        }
        row.addView(ImageView(this).apply {
            setImageResource(icon); scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(JianquSpacing.space10Dp), dp(JianquSpacing.space10Dp), dp(JianquSpacing.space10Dp), dp(JianquSpacing.space10Dp))
            background = JianquDrawables.shape(this@ServicePreviewActivity, tint, 12)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val copy = column().apply {
            line(this, title, JianquTypography.bodyEmphasisSp, color, true, 0)
            line(this, desc, JianquTypography.captionLargeSp, ui.muted, top = 5)
        }
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(JianquSpacing.space12Dp) })
        row.addView(text("›", JianquTypography.headingLargeSp, color), LinearLayout.LayoutParams(dp(20), -2))
        add(parent, row, 10)
    }
    protected fun shell(title: String, primary: LinearLayout, secondary: LinearLayout, back: () -> Unit = { finish() }) {
        val root = column().apply { setBackgroundColor(ui.background) }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(JianquSpacing.space20Dp), dp(JianquSpacing.space8Dp), dp(JianquSpacing.space20Dp), dp(JianquSpacing.space8Dp))
        }
        bar.addView(text("‹", 35f, ui.accentStrong, true).apply {
            gravity = Gravity.CENTER; contentDescription = "返回"; isClickable = true; isFocusable = true
            background = JianquDrawables.touch(this@ServicePreviewActivity, ui.surface, 14, ui.border)
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        bar.addView(text(title, JianquTypography.titleCompactSp, ui.ink, true), LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(13) })
        bar.addView(text("原型演示", JianquTypography.captionLargeSp, ui.muted))
        root.addView(bar)
        val content = column().apply {
            orientation = if (wide()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(JianquSpacing.space20Dp), dp(JianquSpacing.space8Dp), dp(JianquSpacing.space20Dp), dp(JianquSpacing.space24Dp))
        }
        if (wide()) {
            content.addView(primary, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(JianquSpacing.space16Dp) })
            content.addView(secondary, LinearLayout.LayoutParams(0, -2, 1.2f))
        } else { add(content, primary, 0); add(content, secondary, 14) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); render() }
    protected abstract fun render()
}

/** Local Life supplies the planned benefit; no store integration or entitlement exists here. */
internal class StorefrontActivity : ServicePreviewActivity() {
    private var selected = 0
    private val categories = listOf("优惠购", "本地好物", "社区福利")
    private val descriptions = listOf("专享折扣 · 优惠商品", "家门口的精选好物", "邻里活动与生活福利")
    override fun onStop() { super.onStop(); selected = 0 }
    override fun onResume() { super.onResume(); render() }
    override fun render() {
        val hero = panel(ui.storeIcon, null).apply {
            line(this, "惠享 · 本地优惠", JianquTypography.bodyLargeSp, ui.accentStrong, true, 0)
            line(this, "9.9 折", 54f, ui.accentStrong, true, 12)
            line(this, "本地生活专享优惠", JianquTypography.sectionSp, ui.ink, true, 5)
            line(this, "示例权益 · 实际折扣待定", JianquTypography.captionLargeSp, ui.muted, top = 10)
            val example = panel(ui.surface, null).apply {
                line(this, "优惠购 · 价格示例", JianquTypography.supportingSp, ui.muted, true, 0)
                line(this, "¥100.00  →  ¥99.00", JianquTypography.headingLargeSp, ui.accentStrong, true, 8)
                line(this, "本次示例优惠 ¥1.00", JianquTypography.supportingSp, ui.muted, top = 6)
            }
            add(this, example, 20)
        }
        val body = column().apply {
            line(this, "家门口的好生活", JianquTypography.titleCompactSp, ui.ink, true, 0)
            line(this, "由三创本地生活提供", JianquTypography.supportingSp, ui.muted, top = 6)
            categories.forEachIndexed { i, title ->
                action(this, title, descriptions[i], R.drawable.ic_home_shopping_bag_open,
                    if (i == selected) ui.storeIcon else ui.albumIcon,
                    if (i == selected) ui.accentStrong else ui.ink) {
                    selected = i
                    render()
                }
            }
            val detail = panel(ui.surface).apply {
                line(this, categories[selected], 19f, ui.accentStrong, true, 0)
                line(this, listOf("订阅专享优惠商品，示例为 9.9 折。", "精选社区商品与便民服务。", "附近社区活动与生活福利。")[selected], JianquTypography.bodySp, top = 9)
                line(this, "权益展示原型 · 暂不可购买", JianquTypography.captionLargeSp, ui.muted, top = 9)
            }
            add(this, detail, 14)
        }
        shell("本地生活", hero, body)
    }
}
