package cn.sanchuang.jianqu

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable

internal object JianquDrawables {
    fun shape(context: Context, color: Int, radius: Int = JianquDimensions.cardRadiusDp, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            val density = context.resources.displayMetrics.density
            fun dp(n: Int) = (density * n + 0.5f).toInt()
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    fun touch(context: Context, color: Int, radius: Int = JianquDimensions.cardRadiusDp, stroke: Int? = null): RippleDrawable =
        ripple(context, shape(context, color, radius, stroke))

    fun ripple(context: Context, content: GradientDrawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.jq_ripple)), content, null)
}
