package cn.sanchuang.jianqu

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

    const val cardRadiusDp = 16
    const val companionRadiusDp = 20

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
