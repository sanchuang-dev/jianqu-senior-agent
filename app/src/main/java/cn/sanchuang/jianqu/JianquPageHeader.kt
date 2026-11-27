package cn.sanchuang.jianqu

import android.content.Context
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/** Common secondary-page chrome; callers retain all navigation and status semantics. */
internal object JianquPageHeader {
    private fun dp(context: Context, n: Int) =
        (context.resources.displayMetrics.density * n + .5f).toInt()

    fun backButton(context: Context, description: String, onBack: () -> Unit): View =
        FrameLayout(context).apply {
            val ui = JianquColors(context)
            background = RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.jq_ripple)),
                null, JianquDrawables.shape(context, Color.WHITE, JianquDimensions.tileRadiusDp))
            minimumWidth = dp(context, JianquDimensions.compactActionHeightDp)
            minimumHeight = minimumWidth
            contentDescription = description
            isFocusable = true
            addView(JianquIcons.image(context, R.drawable.ic_jq_glass_back, ui.ink),
                FrameLayout.LayoutParams(dp(context, JianquDimensions.navigationIconDp),
                    dp(context, JianquDimensions.navigationIconDp), Gravity.CENTER))
            setOnClickListener { onBack() }
        }

    fun create(context: Context, title: String, backDescription: String = "返回桌面",
               status: String? = null, statusColor: Int? = null,
               onBack: () -> Unit): LinearLayout = LinearLayout(context).apply {
        val ui = JianquColors(context)
        fun px(n: Int) = dp(context, n)
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(JianquDimensions.largeActionHeightDp)
        setPadding(px(JianquSpacing.space8Dp), px(JianquSpacing.space4Dp),
            px(JianquSpacing.space16Dp), px(JianquSpacing.space4Dp))
        addView(backButton(context, backDescription, onBack),
            LinearLayout.LayoutParams(px(JianquDimensions.compactActionHeightDp),
                px(JianquDimensions.compactActionHeightDp)))
        addView(JianquTypography.text(context, title, JianquTypography.navigationTitleSp, ui.ink, true),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = px(JianquSpacing.space4Dp) })
        if (status != null) addView(JianquTypography.text(context, status,
            JianquTypography.supportingSp, statusColor ?: ui.muted, false).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxWidth = px((context.resources.configuration.screenWidthDp / 3).coerceAtLeast(100))
        }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = px(JianquSpacing.space8Dp) })
    }
}
