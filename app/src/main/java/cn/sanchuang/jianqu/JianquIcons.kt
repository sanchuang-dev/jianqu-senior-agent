package cn.sanchuang.jianqu

import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Native vector assets, shared icon-label layout and semantic tint. */
internal object JianquIcons {
    fun image(context: Context, drawable: Int, color: Int): ImageView = ImageView(context).apply {
        setImageResource(drawable)
        setColorFilter(color)
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun label(context: Context, drawable: Int, title: String, size: Float, color: Int,
              iconColor: Int = color, bold: Boolean = true): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
        addView(image(context, drawable, iconColor), LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(JianquTypography.text(context, title, size, color, bold),
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(7) })
    }
}
