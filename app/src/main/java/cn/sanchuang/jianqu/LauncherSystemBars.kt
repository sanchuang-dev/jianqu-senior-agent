package cn.sanchuang.jianqu

import android.graphics.Color
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController

/** Desktop wallpaper may extend beneath system bars; controls must remain in the safe area. */
internal class LauncherSystemBars(private val window: Window) {
    private val decor = window.decorView
    private val originalSystemUi = decor.systemUiVisibility
    private val originalDivider = window.navigationBarDividerColor
    private val originalStatus = window.statusBarColor
    private val originalNavigation = window.navigationBarColor
    private val originalStatusContrast = window.isStatusBarContrastEnforced
    private val originalNavigationContrast = window.isNavigationBarContrastEnforced

    fun apply(edgeToEdge: Boolean) {
        window.setDecorFitsSystemWindows(!edgeToEdge)
        // API 30 DecorView also honors the legacy layout flags for three-button navigation.
        val layoutFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        decor.systemUiVisibility = if (edgeToEdge) originalSystemUi or layoutFlags else originalSystemUi
        window.navigationBarDividerColor = if (edgeToEdge) Color.TRANSPARENT else originalDivider
        window.statusBarColor = if (edgeToEdge) Color.TRANSPARENT else originalStatus
        window.navigationBarColor = if (edgeToEdge) Color.TRANSPARENT else originalNavigation
        window.isStatusBarContrastEnforced = if (edgeToEdge) false else originalStatusContrast
        window.isNavigationBarContrastEnforced = if (edgeToEdge) false else originalNavigationContrast
        // Both current desktop surfaces are light. A dark wallpaper must opt into light icons.
        val darkIcons = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        decor.windowInsetsController?.setSystemBarsAppearance(darkIcons, darkIcons)
    }

    companion object {
        fun insetContent(view: View) {
            val left = view.paddingLeft
            val top = view.paddingTop
            val right = view.paddingRight
            val bottom = view.paddingBottom
            view.setOnApplyWindowInsetsListener { v, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom)
                insets
            }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.requestApplyInsets()
                    v.removeOnAttachStateChangeListener(this)
                }
                override fun onViewDetachedFromWindow(v: View) = Unit
            })
            if (view.isAttachedToWindow) view.requestApplyInsets()
        }
    }
}
