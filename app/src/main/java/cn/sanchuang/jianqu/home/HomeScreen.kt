package cn.sanchuang.jianqu.home

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDimensions
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquGlassMaterial
import cn.sanchuang.jianqu.JianquTypography
import cn.sanchuang.jianqu.LauncherSystemBars
import cn.sanchuang.jianqu.R
import cn.sanchuang.jianqu.WeatherCity
import cn.sanchuang.jianqu.WeatherData

/** Renders HOME only. The activity retains admission, lifecycle and every navigation decision. */
internal class HomeScreen(
    private val activity: Activity,
    private val previewUnenforced: Boolean,
    private val developer: (() -> Unit)?,
    private val onAction: (HomeAction) -> Unit,
    private val onSearch: () -> Unit,
    private val onSos: () -> Unit,
    private val onWeather: () -> Unit,
    private val onCompanion: () -> Unit
) {
    private val ui = JianquColors(activity)
    private val resources get() = activity.resources
    private val homeLayout = HomeLayout(
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        resources.configuration.screenWidthDp
    )
    private val landscape get() = homeLayout.landscape
    private val ink get() = ui.ink
    private val muted get() = ui.muted
    private fun getColor(resource: Int) = activity.getColor(resource)
    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        JianquTypography.text(activity, value, size, color, bold)
    private fun LinearLayout.addSpace(height: Int) {
        addView(View(context), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun homeColumn() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun homeRow() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun homeIcon(resource: Int): ImageView = ImageView(activity).apply {
        if (resource != 0) setImageResource(resource)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private class HomeGlass(private val wallpaper: View) {
        private val panels = ArrayList<View>()

        fun surface(panel: View, radius: Int = JianquDimensions.companionRadiusDp): RippleDrawable {
            panels.add(panel)
            val context = wallpaper.context
            val content = JianquGlassMaterial.TILE.background(wallpaper, panel,
                R.drawable.jq_home_background, radius)
            return RippleDrawable(android.content.res.ColorStateList.valueOf(context.getColor(R.color.jq_ripple)),
                content, JianquDrawables.shape(context, android.graphics.Color.WHITE, radius))
        }

        fun invalidate() = panels.forEach { it.invalidate() }
    }

    fun render(): FrameLayout {
        val wallpaper = FrameLayout(activity).apply {
            setBackgroundColor(getColor(R.color.jq_home_canvas))
            addView(ImageView(activity).apply {
                setImageResource(R.drawable.jq_home_background)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val root = homeColumn().apply {
            val side = homeLayout.sidePaddingDp
            setPadding(dp(side), dp(10), dp(side), dp(10))
        }
        wallpaper.addView(root, FrameLayout.LayoutParams(-1, -1))
        val glass = HomeGlass(wallpaper)
        wallpaper.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> glass.invalidate() }
        LauncherSystemBars.insetContent(root)
        val header = homeRow()
        val brand = homeRow().apply {
            minimumHeight = dp(48)
            addView(homeIcon(R.drawable.jq_home_brand_butterfly), LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(text("见趣 · 闺蜜机", 18f, ink, true).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        if (developer != null) {
            var taps = 0
            var firstTap = 0L
            brand.setOnClickListener {
                val now = android.os.SystemClock.elapsedRealtime()
                if (taps == 0 || now - firstTap > 1500L) {
                    firstTap = now
                    taps = 0
                }
                if (++taps == 3) {
                    taps = 0
                    developer()
                }
            }
        }
        buildHomeHeaderActions(header)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        if (previewUnenforced) {
            root.addView(text("公开预览 · 租赁未接入", 12f, muted))
        }
        val scroll = ScrollView(activity).apply {
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
        }
        val body = homeColumn().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(4)) }
        scroll.addView(body)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> glass.invalidate() }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val wide = homeLayout.wide
        val top = homeRow().apply { gravity = Gravity.CENTER_VERTICAL }
        if (wide) {
            top.addView(homeGreeting().apply {
                setPadding(dp(12), 0, dp(4), 0)
            }, LinearLayout.LayoutParams(0, -2, 1.1f))
            top.addView(companionPanel(glass), LinearLayout.LayoutParams(0, -1, 0.7f).apply {
                leftMargin = dp(12)
            })
            top.addView(environmentPanel(glass), LinearLayout.LayoutParams(0, -2, 1.85f).apply {
                leftMargin = dp(12)
            })
        } else {
            body.addView(homeGreeting().apply {
                setPadding(dp(12), dp(8), dp(12), dp(8))
            }, LinearLayout.LayoutParams(-1, -2))
            body.addSpace(homeLayout.greetingGapDp)
            top.addView(environmentPanel(glass), LinearLayout.LayoutParams(0, -2, if (landscape) 1.85f else 1.65f))
            top.addView(companionPanel(glass), LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(10)
            })
        }
        body.addView(top, LinearLayout.LayoutParams(-1, -2))
        body.addSpace(homeLayout.panelGapDp)
        buildQuickActions(body, glass)
        return wallpaper
    }

    private fun weatherIcon(symbol: String): Int = when (symbol) {
        "晴" -> R.drawable.jq_home_sunny
        "多云间晴", "晴间多云" -> R.drawable.jq_home_partly_cloudy
        "多云" -> R.drawable.jq_home_cloudy
        "雨" -> R.drawable.jq_home_rain
        "雷阵雨" -> R.drawable.jq_home_thunder
        "雪" -> R.drawable.jq_home_snow
        "雾" -> R.drawable.jq_home_fog
        else -> 0
    }

    private fun environmentPanel(glass: HomeGlass): View {
        val place = WeatherCity.place(activity)
        val forecast = place?.let { WeatherData.cached(activity, it) }
        val clock = homeColumn()
        clock.addView(TextClock(activity).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = homeLayout.clockSp
            setTextColor(ink)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            includeFontPadding = false
        })
        val dateRow = homeRow()
        dateRow.addView(TextClock(activity).apply {
            format12Hour = "M月d日 EEEE"
            format24Hour = "M月d日 EEEE"
            textSize = homeLayout.weatherSp.toFloat()
            setTextColor(muted)
            if (!landscape) {
                maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(12, 16, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
        }, LinearLayout.LayoutParams(-2, -2))
        if (forecast?.stale == true) {
            dateRow.addView(View(activity).apply {
                background = JianquDrawables.shape(activity,
                    getColor(R.color.jq_status_stale), 3)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { leftMargin = dp(6) })
        }
        clock.addView(dateRow, LinearLayout.LayoutParams(-1, -2))
        val summary = (if (landscape) homeRow() else homeColumn()).apply {
            minimumHeight = dp(homeLayout.panelMinHeightDp)
            val side = homeLayout.weatherSp
            val vertical = if (landscape) 10 else 16
            setPadding(dp(side), dp(vertical), dp(side), dp(vertical))
            background = glass.surface(this)
            contentDescription = if (forecast?.stale == true) "天气数据可能已过期，查看七日预报" else "天气，查看七日预报"
            isFocusable = true
            setOnClickListener { onWeather() }
        }
        clock.gravity = if (landscape) Gravity.BOTTOM else Gravity.TOP
        summary.addView(clock, if (landscape) LinearLayout.LayoutParams(0, -1, 1f)
            else LinearLayout.LayoutParams(-1, -2))
        val weather = (if (landscape) homeColumn() else homeRow()).apply {
            gravity = if (landscape) Gravity.CENTER_HORIZONTAL or if (forecast != null) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
                else Gravity.CENTER_VERTICAL
        }
        if (forecast != null) {
            weather.addView(homeIcon(weatherIcon(forecast.days.first().symbol)),
                LinearLayout.LayoutParams(dp(homeLayout.panelIconDp), dp(homeLayout.panelIconDp)))
        }
        val label = when {
            place == null -> "选择城市"
            !WeatherData.consented(activity) -> "${place.name} · 启用天气"
            forecast == null -> "${place.name} · 查询天气"
            else -> "${place.name} · ${forecast.days.first().symbol} · ${forecast.nearTemperature}°C"
        }
        weather.addView(text(label, homeLayout.weatherSp.toFloat(), if (landscape) muted else ink).apply {
            gravity = if (landscape) Gravity.CENTER else Gravity.CENTER_VERTICAL
            maxLines = if (forecast != null) 1 else 2
            if (forecast != null) setAutoSizeTextTypeUniformWithConfiguration(
                11, homeLayout.weatherSp, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, if (landscape) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f).apply {
            if (forecast != null) leftMargin = dp(8)
        })
        summary.addView(weather, if (landscape) LinearLayout.LayoutParams(0, -1, 0.85f)
            else LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return summary
    }

    private fun companionPanel(glass: HomeGlass): View = homeColumn().apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = glass.surface(this)
        contentDescription = "小暖，进入本地模拟陪伴交互"
        isFocusable = true
        setOnClickListener { onCompanion() }
        val iconSize = homeLayout.panelIconDp
        addView(homeIcon(R.drawable.jq_home_voice), LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
        addView(text(if (landscape) "和小暖聊聊吧…" else "和小暖聊聊吧", homeLayout.companionSp, ink).apply {
            gravity = Gravity.CENTER
            maxLines = 2
        }, LinearLayout.LayoutParams(-1, -2).apply { if (!landscape) topMargin = dp(16) })
    }

    private fun homeGreeting(): View {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
        return JianquTypography.display(activity, greeting + "，\n今天想做点什么？", homeLayout.greetingSp, ink)
    }

    private fun buildQuickActions(body: LinearLayout, glass: HomeGlass) {
        HomeAction.entries.filterNot { it.header }.chunked(homeLayout.columns).forEach { group ->
            val row = homeRow()
            group.forEachIndexed { index, item ->
                row.addView(makeQuickAction(item, glass), LinearLayout.LayoutParams(0, dp(homeLayout.tileHeightDp), 1f).apply {
                    if (index > 0) leftMargin = dp(homeLayout.tileGapDp)
                })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(homeLayout.tileRowGapDp) })
        }
    }

    private fun makeQuickAction(action: HomeAction, glass: HomeGlass): View =
        homeColumn().apply {
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(4), dp(3), dp(4))
            background = glass.surface(this)
            isFocusable = true
            contentDescription = action.title
            val iconSize = homeLayout.tileIconDp
            addView(homeIcon(action.iconRes), LinearLayout.LayoutParams(dp(iconSize), dp(iconSize)))
            addView(text(action.title, homeLayout.tileLabelSp, ink, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(homeLayout.tileLabelGapDp) })
            setOnClickListener { onAction(action) }
        }

    private fun buildHomeHeaderActions(header: LinearLayout) {
        fun iconButton(resource: Int, description: String, action: () -> Unit) {
            val button = FrameLayout(activity).apply {
                background = RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.jq_ripple)), null,
                    JianquDrawables.shape(activity, android.graphics.Color.WHITE, 24))
                isFocusable = true
                contentDescription = description
                tooltipText = description
                addView(homeIcon(resource), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
                setOnClickListener { action() }
            }
            header.addView(button, LinearLayout.LayoutParams(dp(JianquDimensions.compactActionHeightDp), dp(JianquDimensions.compactActionHeightDp)).apply { leftMargin = dp(8) })
        }
        iconButton(R.drawable.ic_jq_app_search, "搜索应用") { onSearch() }
        HomeAction.entries.filter { it.header }.forEach { item ->
            iconButton(item.iconRes, item.title) { onAction(item) }
        }
        iconButton(R.drawable.jq_home_alarm, "SOS 紧急求助，当前为未接入真实求救功能的原型") {
            onSos()
        }
    }

}
