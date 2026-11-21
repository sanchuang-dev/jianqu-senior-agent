package cn.sanchuang.jianqu

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * Launcher V0.2 shared visual primitives.
 * Both HOME and its local companion/service pages must use these tokens.
 */
internal object LauncherUiTokens {
    val background = Color.rgb(250, 248, 244)
    val ink = Color.rgb(48, 53, 53)
    val muted = Color.rgb(112, 119, 117)
    val terracotta = Color.rgb(159, 88, 69)
    val divider = Color.rgb(235, 231, 225)
    val surface = Color.rgb(255, 254, 252)
    val companionStart = Color.rgb(250, 231, 218)
    val companionEnd = Color.rgb(252, 242, 227)
    val messageUser = Color.rgb(250, 231, 218)
    val messageAssistant = surface
    // Family call uses the exact HOME action tint; danger follows HOME SOS.
    val familyCallIcon = Color.rgb(226, 242, 237)
    val danger = Color.rgb(157, 50, 43)

    const val cardRadiusDp = 16
    const val companionRadiusDp = 20
    const val iconWellRadiusDp = 13
    const val minActionHeightDp = 52
    const val headingLargeSp = 25f
    const val headingCompactSp = 21f

    /** Shared HOME and child-page card/ripple primitives. */
    fun shape(context: Context, color: Int, radius: Int = cardRadiusDp, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            val density = context.resources.displayMetrics.density
            fun dp(n: Int) = (density * n + 0.5f).toInt()
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    fun touch(context: Context, color: Int, radius: Int = cardRadiusDp, stroke: Int? = null): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.argb(35, 123, 83, 64)),
            shape(context, color, radius, stroke),
            null
        )

    fun companionOrb(context: Context): FrameLayout = FrameLayout(context).apply {
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (density * n + 0.5f).toInt()
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(255, 249, 232),
                Color.rgb(241, 152, 125),
                Color.rgb(214, 119, 127)
            )
        ).apply { shape = GradientDrawable.OVAL }
        addView(View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(130, 255, 255, 255))
                shape = GradientDrawable.OVAL
            }
        }, FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
    }
}
