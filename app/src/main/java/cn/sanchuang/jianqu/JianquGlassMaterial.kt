package cn.sanchuang.jianqu

import android.content.Context
import android.graphics.Color
import android.view.View

/** Shared wallpaper-glass materials. Geometry stays with the consuming component. */
internal enum class JianquGlassMaterial(
    private val tintRes: Int,
    private val edgeRes: Int,
    internal val blurRadius: Int
) {
    TILE(R.color.jq_glass_tile_tint, R.color.jq_glass_tile_edge, 2),
    PANEL(R.color.jq_glass_panel_tint, R.color.jq_glass_panel_edge, 2),
    OVERLAY(R.color.jq_glass_overlay_tint, R.color.jq_glass_overlay_edge, 4);

    fun background(root: View, panel: View, wallpaperRes: Int, radiusDp: Int,
                   scrim: Int = Color.TRANSPARENT): FrostedWallpaperBackground {
        val context = panel.context
        return FrostedWallpaperBackground(root, panel, wallpaperRes, radiusDp, scrim,
            context.getColor(tintRes), context.getColor(edgeRes), blurRadius)
    }

    companion object {
        const val overlayRadiusDp = 24
        const val overlayDim = .06f
        fun controlTint(context: Context) = context.getColor(R.color.jq_glass_control_tint)
        fun separator(context: Context) = context.getColor(R.color.jq_glass_separator)
    }
}
