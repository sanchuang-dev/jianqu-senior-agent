package cn.sanchuang.jianqu

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Decorative companion component, not a theme token. */
internal fun companionOrb(context: Context): FrameLayout = FrameLayout(context).apply {
    val colors = JianquColors(context)
    val density = resources.displayMetrics.density
    fun dp(n: Int) = (density * n + 0.5f).toInt()
    background = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(colors.surface, colors.orbCore, colors.orbEdge)
    ).apply { shape = GradientDrawable.OVAL }
    addView(View(context).apply {
        alpha = 0.51f
        background = GradientDrawable().apply {
            setColor(colors.surface)
            shape = GradientDrawable.OVAL
        }
    }, FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
}
