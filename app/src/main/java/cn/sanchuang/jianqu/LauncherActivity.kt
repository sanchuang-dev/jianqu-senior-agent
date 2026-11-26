package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.LauncherActivityInfo
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import cn.sanchuang.jianqu.admission.DeviceAccess
import cn.sanchuang.jianqu.admission.DeviceAccessEnvironment
import cn.sanchuang.jianqu.admission.LeaseGateView
import cn.sanchuang.jianqu.system.apps.LauncherAppCatalog
import cn.sanchuang.jianqu.system.apps.LauncherAppSearch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Android HOME is a stable place to return to, not the device's entire application catalog.
 * All ten home functions are fixed; only the "全部应用" page enumerates real installed apps.
 * Other business/communication functions are intentionally honest navigation placeholders
 * until their independent JQ-005..011 contracts are implemented.
 */
class LauncherActivity : Activity() {
    private val ui = JianquColors(this)
    private enum class Page { HOME, ALL_APPS, SETTINGS, HELP, RENTAL_GATE }
    private val access by lazy { DeviceAccessEnvironment.open(this) }
    private var renderedGateState: DeviceAccess? = null
    private val desktopBars by lazy { LauncherSystemBars(window) }

    private enum class QuickActionDestination { PLACEHOLDER, FAMILY_CALL, CONTENT_ALBUM, CONTENT_VIDEO, STOREFRONT, CONTENT_MUSIC, CONTENT_HEALTH, CONTENT_NEWS, SERVICES, SETTINGS, ALL_APPS }

    private data class QuickAction(
        val title: String,
        val iconRes: Int,
        val color: Int,
        val destination: QuickActionDestination
    )

    private val backgroundColor get() = ui.background
    private val ink get() = ui.ink
    private val muted get() = ui.muted
    private val terracotta get() = ui.accentStrong
    private val divider get() = ui.border
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var appCatalogAdapter: LauncherAppCatalog
    private lateinit var catalog: GridLayout
    private lateinit var appCount: TextView
    private lateinit var searchInput: EditText
    private var installedApps: List<LauncherActivityInfo> = emptyList()
    private var appLoadVersion = 0
    private var searchUpdate: Runnable? = null
    private val uiPreferences by lazy { getSharedPreferences("launcher_ui", MODE_PRIVATE) }
    private var page = Page.HOME
    private var restoreHomeOnResume = false
    private var returningFromWeather = false

    private val quickActions by lazy { listOf(
        QuickAction("家人通话", R.drawable.ic_home_video_camera, ui.familyCallIcon, QuickActionDestination.FAMILY_CALL),
        QuickAction("家庭相册", R.drawable.ic_home_images, ui.albumIcon, QuickActionDestination.CONTENT_ALBUM),
        QuickAction("影视节目", R.drawable.ic_home_film_strip, ui.videoIcon, QuickActionDestination.CONTENT_VIDEO),
        QuickAction("本地生活", R.drawable.ic_home_shopping_bag_open, ui.storeIcon, QuickActionDestination.STOREFRONT),
        QuickAction("音乐戏曲", R.drawable.ic_home_music_notes, ui.musicIcon, QuickActionDestination.CONTENT_MUSIC),
        QuickAction("健康中心", R.drawable.ic_home_stethoscope, ui.healthIcon, QuickActionDestination.CONTENT_HEALTH),
        QuickAction("建政栏目", R.drawable.ic_home_newspaper, ui.newsIcon, QuickActionDestination.CONTENT_NEWS),
        QuickAction("我的服务", R.drawable.ic_home_hand_heart, ui.servicesIcon, QuickActionDestination.SERVICES),
        QuickAction("设置", R.drawable.ic_home_gear, ui.settingsIcon, QuickActionDestination.SETTINGS),
        QuickAction("全部应用", R.drawable.ic_home_squares_four, ui.allAppsIcon, QuickActionDestination.ALL_APPS)
    ) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appCatalogAdapter = LauncherAppCatalog.open(this, main) { refreshApps() }
        appCatalogAdapter.start()
        showHome()
        if (savedInstanceState == null && !uiPreferences.getBoolean("intro_seen", false)) {
            main.post {
                if (!isFinishing && !isDestroyed && page == Page.HOME) showFirstUseGuide()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        restoreHomeOnResume = false
        showHome()
    }

    override fun onUserLeaveHint() {
        if (page == Page.ALL_APPS) restoreHomeOnResume = true
        super.onUserLeaveHint()
    }

    override fun onStop() {
        if (page == Page.ALL_APPS) restoreHomeOnResume = true
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!admit()) return
        if (page == Page.RENTAL_GATE) {
            showHome()
            return
        }
        if (returningFromWeather) {
            returningFromWeather = false
            showHome()
        } else if (restoreHomeOnResume) {
            restoreHomeOnResume = false
            showHome()
        } else if (page == Page.ALL_APPS) {
            refreshApps()
        }
    }

    @Deprecated("Android back navigation callback; HOME must remain the root destination.")
    override fun onBackPressed() {
        if (!admit()) return
        when (page) {
            Page.RENTAL_GATE -> showHome()
            Page.ALL_APPS, Page.SETTINGS -> showHome()
            Page.HELP -> showSettings()
            Page.HOME -> Unit // A launcher never closes itself on Back.
        }
    }

    override fun onDestroy() {
        appCatalogAdapter.stop()
        searchUpdate?.let(main::removeCallbacks)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun shape(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        JianquDrawables.shape(this, color, radius, stroke)

    private fun touch(color: Int, radius: Int, border: Int? = null): RippleDrawable =
        JianquDrawables.touch(this, color, radius, border)

    private fun text(
        value: String,
        size: Float,
        color: Int = ink,
        bold: Boolean = false
    ): TextView = JianquTypography.text(this, value, size, color, bold)

    private fun LinearLayout.addSpace(height: Int) {
        addView(View(context), LinearLayout.LayoutParams(1, dp(height)))
    }

    /** One admission check for every path that can reveal the normal desktop. */
    private fun admit(): Boolean {
        val state = access.refresh()
        if (state.allowsHome) return true
        if (page != Page.RENTAL_GATE || renderedGateState != state) renderGate(state)
        return false
    }

    private fun renderGate(state: DeviceAccess) {
        page = Page.RENTAL_GATE
        renderedGateState = state
        restoreHomeOnResume = false
        returningFromWeather = false
        appLoadVersion++
        searchUpdate?.let(main::removeCallbacks)
        desktopBars.apply(edgeToEdge = true)
        setContentView(LeaseGateView(this).render(
            state, access.developerControl(this) {
                val next = access.refresh(); if (next.allowsHome) showHome() else renderGate(next)
            }, access.checkout(),
            retry = { val next = access.refresh(); if (next.allowsHome) showHome() else renderGate(next) },
            network = { openAndroidSettings(Settings.ACTION_WIFI_SETTINGS) },
            changed = { val next = access.refresh(); if (next.allowsHome) showHome() else renderGate(next) }
        ))
    }

    private fun homeColumn() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun homeRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun homeIcon(resource: Int): ImageView = ImageView(this).apply {
        if (resource != 0) setImageResource(resource)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private class HomeGlass(private val wallpaper: View) {
        private val panels = ArrayList<View>()

        fun surface(panel: View, radius: Int = JianquDimensions.companionRadiusDp): RippleDrawable {
            panels.add(panel)
            val context = wallpaper.context
            val content = FrostedWallpaperBackground(wallpaper, panel, R.drawable.jq_home_background,
                radius, android.graphics.Color.TRANSPARENT, context.getColor(R.color.jq_home_glass),
                context.getColor(R.color.jq_home_glass_edge))
            return RippleDrawable(android.content.res.ColorStateList.valueOf(context.getColor(R.color.jq_ripple)),
                content, JianquDrawables.shape(context, android.graphics.Color.WHITE, radius))
        }

        fun invalidate() = panels.forEach { it.invalidate() }
    }

    private fun showHome() {
        if (!admit()) return
        page = Page.HOME
        renderedGateState = null
        val wallpaper = FrameLayout(this).apply {
            setBackgroundColor(getColor(R.color.jq_home_canvas))
            addView(ImageView(this@LauncherActivity).apply {
                setImageResource(R.drawable.jq_home_background)
                scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val root = homeColumn().apply {
            val side = if (landscape) 20 else 16
            setPadding(dp(side), dp(10), dp(side), dp(10))
        }
        wallpaper.addView(root, FrameLayout.LayoutParams(-1, -1))
        val glass = HomeGlass(wallpaper)
        wallpaper.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> glass.invalidate() }
        desktopBars.apply(edgeToEdge = true)
        LauncherSystemBars.insetContent(root)
        setContentView(wallpaper)
        root.requestApplyInsets()
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
        val developer = access.developerAction(this) { showHome() }
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
        if (access.current() == DeviceAccess.PREVIEW_UNENFORCED) {
            root.addView(text("公开预览 · 租赁未接入", 12f, muted))
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
        }
        val body = homeColumn().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(4)) }
        scroll.addView(body)
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> glass.invalidate() }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        if (landscape && resources.configuration.screenWidthDp >= 660) {
            val top = homeRow().apply { gravity = Gravity.TOP }
            val companion = homeColumn()
            val environment = homeColumn()
            buildCompanion(companion, glass)
            buildEnvironment(environment, glass)
            top.addView(companion, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(environment, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
            body.addView(top, LinearLayout.LayoutParams(-1, -2))
        } else {
            buildEnvironment(body, glass)
            body.addSpace(10)
            buildCompanion(body, glass)
        }
        body.addSpace(12)
        buildQuickActions(body, glass)
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

    private fun buildEnvironment(body: LinearLayout, glass: HomeGlass) {
        val place = WeatherCity.place(this)
        val forecast = place?.let { WeatherData.cached(this, it) }
        val clock = homeColumn()
        clock.addView(TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = if (landscape) 42f else 44f
            setTextColor(ink)
            typeface = Typeface.create("serif", Typeface.NORMAL)
            includeFontPadding = false
        })
        clock.addView(TextClock(this).apply {
            format12Hour = "M月d日 EEEE"
            format24Hour = "M月d日 EEEE"
            textSize = 14f
            setTextColor(muted)
        })
        fun openWeather() {
            if (!admit()) return
            returningFromWeather = true
            startActivity(Intent(this, WeatherActivity::class.java))
        }
        val summary = homeRow().apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = glass.surface(this)
            contentDescription = "天气，查看七日预报"
            isFocusable = true
            setOnClickListener { openWeather() }
        }
        summary.addView(clock, LinearLayout.LayoutParams(0, -2, 1f))
        val weather = homeColumn().apply { gravity = Gravity.CENTER_HORIZONTAL }
        if (forecast != null) {
            weather.addView(homeIcon(weatherIcon(forecast.days.first().symbol)), LinearLayout.LayoutParams(dp(46), dp(46)))
            weather.addView(text("${forecast.nearTemperature}°C", 25f, ink, true).apply { gravity = Gravity.CENTER })
        }
        val label = when {
            place == null -> "选择城市"
            !WeatherData.consented(this) -> "${place.name} · 启用天气"
            forecast == null -> "${place.name} · 查询天气"
            forecast.stale -> "${place.name} · 缓存过期"
            else -> "${place.name} · ${forecast.days.first().symbol}"
        }
        weather.addView(text(label, 13f, muted).apply { gravity = Gravity.CENTER; maxLines = 2 })
        summary.addView(weather, LinearLayout.LayoutParams(0, -2, 0.85f))
        body.addView(summary, LinearLayout.LayoutParams(-1, -2))

    }

    private fun buildCompanion(body: LinearLayout, glass: HomeGlass) {
        val tile = homeColumn().apply {
            setPadding(dp(12), dp(if (landscape && resources.configuration.screenWidthDp >= 660) 0 else 8), dp(12), dp(8))
            gravity = Gravity.CENTER_VERTICAL
            contentDescription = "小暖，进入本地模拟陪伴交互"
            isFocusable = true
            setOnClickListener {
                if (!admit()) return@setOnClickListener
                startActivity(Intent(this@LauncherActivity, DemoActivity::class.java)
                    .putExtra(DemoActivity.EXTRA_OPEN_CHAT, true))
            }
        }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..17 -> "下午好"
            else -> "晚上好"
        }
        tile.addView(JianquTypography.display(this, greeting + "，\n今天想做点什么？", if (landscape) 28f else 25f, ink))
        val talk = homeRow().apply {
            background = glass.surface(this, 24)
            setPadding(dp(12), 0, dp(12), 0)
        }
        talk.addView(homeIcon(R.drawable.jq_home_voice), LinearLayout.LayoutParams(dp(32), dp(32)))
        talk.addView(text("和小暖聊聊吧…", 16f, ink), LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        tile.addView(talk, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(12) })
        tile.addView(text("本地模拟体验 · 非真实 AI", 12f, muted), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        body.addView(tile, LinearLayout.LayoutParams(-1, -2))
    }

    private fun homeActionIcon(action: QuickAction): Int = when (action.destination) {
        QuickActionDestination.FAMILY_CALL -> R.drawable.jq_home_contacts
        QuickActionDestination.CONTENT_ALBUM -> R.drawable.jq_home_album
        QuickActionDestination.CONTENT_VIDEO -> R.drawable.jq_home_video
        QuickActionDestination.STOREFRONT -> R.drawable.jq_home_shopping
        QuickActionDestination.CONTENT_HEALTH -> R.drawable.jq_home_health
        QuickActionDestination.CONTENT_MUSIC -> R.drawable.jq_home_music_opera
        QuickActionDestination.CONTENT_NEWS -> R.drawable.jq_home_governance
        QuickActionDestination.SERVICES -> R.drawable.jq_home_my_services
        QuickActionDestination.SETTINGS -> R.drawable.jq_home_settings
        QuickActionDestination.ALL_APPS -> R.drawable.jq_home_all_apps
        else -> 0
    }

    private fun buildQuickActions(body: LinearLayout, glass: HomeGlass) {
        val width = resources.configuration.screenWidthDp
        val cols = if (landscape && width >= 800) 8 else if (width >= 480) 4 else 2
        quickActions.take(8).chunked(cols).forEach { group ->
            val row = homeRow()
            group.forEachIndexed { index, item ->
                row.addView(makeQuickAction(item, glass), LinearLayout.LayoutParams(0, dp(90), 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun makeQuickAction(action: QuickAction, glass: HomeGlass): View =
        homeColumn().apply {
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(4), dp(3), dp(4))
            background = glass.surface(this)
            isFocusable = true
            contentDescription = action.title
            addView(homeIcon(homeActionIcon(action)), LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(text(action.title, 15f, ink, true).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
            setOnClickListener { openQuickAction(action.destination, action.title) }
        }

    private fun buildHomeHeaderActions(header: LinearLayout) {
        fun iconButton(resource: Int, description: String, action: () -> Unit) {
            val button = FrameLayout(this).apply {
                background = RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.jq_ripple)), null,
                    JianquDrawables.shape(this@LauncherActivity, android.graphics.Color.WHITE, 24))
                isFocusable = true
                contentDescription = description
                tooltipText = description
                addView(homeIcon(resource), FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
                setOnClickListener { action() }
            }
            header.addView(button, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(8) })
        }
        quickActions.filter { it.destination == QuickActionDestination.SETTINGS ||
            it.destination == QuickActionDestination.ALL_APPS }.forEach { item ->
            iconButton(homeActionIcon(item), item.title) { openQuickAction(item.destination, item.title) }
        }
        iconButton(R.drawable.jq_home_alarm, "SOS 紧急求助，当前为未接入真实求救功能的原型") {
            if (admit()) startActivity(Intent(this, SosActivity::class.java))
        }
    }

    private fun openQuickAction(destination: QuickActionDestination, name: String) {
        if (!admit()) return
        when (destination) {
            QuickActionDestination.ALL_APPS -> showAllApps()
            QuickActionDestination.FAMILY_CALL -> startActivity(Intent(this, FamilyCallActivity::class.java))
            QuickActionDestination.STOREFRONT -> startActivity(Intent(this, StorefrontActivity::class.java))
            QuickActionDestination.CONTENT_ALBUM -> openContent(ContentGalleryActivity.ALBUM)
            QuickActionDestination.CONTENT_VIDEO -> openContent(ContentGalleryActivity.VIDEO)
            QuickActionDestination.CONTENT_MUSIC -> openContent(ContentGalleryActivity.MUSIC)
            QuickActionDestination.CONTENT_HEALTH -> startActivity(Intent(this, HealthCenterActivity::class.java))
            QuickActionDestination.CONTENT_NEWS -> openContent(ContentGalleryActivity.NEWS)
            QuickActionDestination.SERVICES -> startActivity(Intent(this, DemoActivity::class.java))
            QuickActionDestination.SETTINGS -> showSettings()
            QuickActionDestination.PLACEHOLDER -> unavailable(
                name,
                "“" + name + "”是第二期固定入口，目前尚未接入实际服务。没有发起通话、消费或其他外部操作。"
            )
        }
    }

    private fun openContent(section: String) {
        startActivity(Intent(this, ContentGalleryActivity::class.java)
            .putExtra(ContentGalleryActivity.EXTRA_SECTION, section))
    }

    private fun unavailable(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("返回桌面", null)
            .show()
    }

    private fun showSettings() {
        if (!admit()) return
        page = Page.SETTINGS
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(19), dp(16), dp(19), dp(16))
        }
        desktopBars.apply(edgeToEdge = false)
        setContentView(root)
        root.addView(text("‹  返回桌面", 18f, terracotta, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { showHome() }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(JianquTypography.display(this, "设置", JianquTypography.pageTitleSp, ink), LinearLayout.LayoutParams(-1, dp(58)))
        root.addView(text("管理本机桌面与系统设置", 16f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val preferences = settingsGroup(content, "桌面偏好")
        val city = WeatherCity.current(this)
        addSettingsItem(preferences, "天气城市",
            if (city.isEmpty()) "尚未选择城市 · 不读取设备定位" else "$city · 点击更换") {
            showCityDialog()
        }
        addSettingsItem(preferences, "Wi-Fi 与网络", "打开 Android 的网络设置") {
            openAndroidSettings(Settings.ACTION_WIFI_SETTINGS)
        }
        addSettingsItem(preferences, "屏幕与亮度", "打开 Android 的显示设置") {
            openAndroidSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }
        addSettingsItem(preferences, "默认主屏幕", "选择或更改 Android HOME 应用") {
            openAndroidSettings(Settings.ACTION_HOME_SETTINGS)
        }

        content.addSpace(20)
        val device = settingsGroup(content, "了解设备")
        addSettingsItem(device, "首次使用与帮助", "默认桌面、全部应用与安全提示") {
            showHelp()
        }
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "未知"
        addSettingsItem(device, "关于闺蜜机", "版本 $version · 原型演示") {
            AlertDialog.Builder(this)
                .setTitle("闺蜜机（演示）")
                .setMessage("版本 $version\n普通第三方 Android Launcher。本期小暖和设备订阅为本地模拟，其他服务需分别接入。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun settingsGroup(parent: LinearLayout, title: String): LinearLayout {
        parent.addView(JianquTypography.display(this, title, JianquTypography.sectionSp, ink),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(ui.surface, JianquDimensions.cardRadiusDp, divider)
            setPadding(dp(1), dp(1), dp(1), dp(1))
            clipToOutline = true
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun addSettingsItem(
        container: LinearLayout,
        title: String,
        description: String,
        action: () -> Unit
    ) {
        if (container.childCount > 0) {
            container.addView(View(this).apply { setBackgroundColor(divider) },
                LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(18); rightMargin = dp(18) })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(76)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = touch(ui.surface, 0)
            isClickable = true
            isFocusable = true
            contentDescription = "$title，$description"
            setOnClickListener { action() }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(title, 18f, ink, true))
        copy.addView(text(description, 14f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("›", 26f, muted))
        container.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showCityDialog() = WeatherCity.showPicker(this) { showSettings() }

    private fun openAndroidSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (error: Exception) {
            Log.w("JianquLauncher", "Android settings unavailable: $action", error)
            Toast.makeText(this, "当前设备无法打开该项系统设置", Toast.LENGTH_LONG).show()
        }
    }

    private fun showHelp() {
        if (!admit()) return
        page = Page.HELP
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
        desktopBars.apply(edgeToEdge = false)
        setContentView(root)
        root.addView(text("‹  返回设置", 18f, terracotta, true).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(-1, dp(49)))
        root.addView(JianquTypography.display(this, "首次使用与帮助", JianquTypography.pageTitleSp, ink),
            LinearLayout.LayoutParams(-1, dp(70)))
        val scroll = ScrollView(this)
        val items = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(items)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        listOf(
            "01 · 设为默认桌面" to "系统首次询问时选择「闺蜜机（演示）」；也可从设置中的「默认主屏幕」进入 Android 选择页。",
            "02 · 找到全部应用" to "首页固定位置 10「全部应用」展示本机可启动的真实 App，支持输入名称搜索，点图标即可打开。",
            "03 · 返回设备桌面" to "使用设备的 Home 键回到闺蜜机主页；在子页面点击左上角返回按钮。",
            "04 · 服务与求助说明" to "天气、家人视频与 SOS 尚未接入真实服务。SOS 当前不能报警或通知护理人员；紧急情况请联系现场人员或使用可靠电话。"
        ).forEach { (heading, detail) ->
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(17), dp(18), dp(17), dp(18))
                background = shape(ui.surface, 14, divider)
            }
            block.addView(text(heading, 18f, ink, true))
            block.addView(text(detail, 15f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            items.addView(block, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }
    }

    private fun showFirstUseGuide() {
        AlertDialog.Builder(this)
            .setTitle("欢迎使用闺蜜机")
            .setMessage("这是 Android 桌面原型。\n\n• 想查找其他 App？点「全部应用」。\n• 按设备 Home 键可返回闺蜜机。\n• 在「设置」里可选择默认主屏幕和城市。\n\n天气、通话与 SOS 尚未接入真实服务。")
            .setPositiveButton("开始使用") { _, _ -> markIntroSeen() }
            .setNeutralButton("查看设置") { _, _ ->
                markIntroSeen()
                showSettings()
            }
            .setNegativeButton("稍后") { _, _ -> markIntroSeen() }
            .setOnCancelListener { markIntroSeen() }
            .show()
    }

    private fun markIntroSeen() {
        uiPreferences.edit().putBoolean("intro_seen", true).apply()
    }
    private fun showAllApps() {
        if (!admit()) return
        page = Page.ALL_APPS
        restoreHomeOnResume = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(19), dp(16), dp(19), dp(12))
        }
        desktopBars.apply(edgeToEdge = false)
        setContentView(root)

        val back = text("‹  返回桌面", 18f, terracotta, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { showHome() }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(JianquTypography.display(this, "全部应用", JianquTypography.pageTitleSp, ink), LinearLayout.LayoutParams(-1, dp(58)))
        installedApps = emptyList()
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(ui.surface, JianquDimensions.cardRadiusDp, divider)
            setPadding(dp(1), 0, dp(4), 0)
        }
        searchInput = EditText(this).apply {
            hint = "搜索已安装应用"
            setSingleLine(true)
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setPadding(dp(17), dp(5), dp(10), dp(5))
            contentDescription = "搜索已安装应用"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    searchUpdate?.let(main::removeCallbacks)
                    val task = Runnable {
                        if (page == Page.ALL_APPS) renderApps(installedApps)
                    }
                    searchUpdate = task
                    main.postDelayed(task, 130)
                }
            })
        }
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, dp(52), 1f))
        searchRow.addView(text("清除", 15f, terracotta, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { searchInput.text.clear() }
        }, LinearLayout.LayoutParams(dp(58), dp(52)))
        root.addView(searchRow, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8); bottomMargin = dp(6) })
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("已安装应用", 16f, ink, true),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        appCount = text("正在读取…", 14f, muted)
        header.addView(appCount)
        root.addView(header)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        catalog = GridLayout(this).apply {
            columnCount = when {
                resources.configuration.screenWidthDp >= 960 -> 6
                resources.configuration.screenWidthDp >= 720 -> 5
                resources.configuration.screenWidthDp >= 520 -> 4
                else -> 3
            }
        }
        scroll.addView(catalog)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        refreshApps()
    }

    private fun refreshApps() {
        if (page != Page.ALL_APPS || isDestroyed || worker.isShutdown || !::catalog.isInitialized) return
        val request = ++appLoadVersion
        try {
            worker.execute {
                val result = runCatching {
                    appCatalogAdapter.load()
                }
                main.post {
                    if (page == Page.ALL_APPS && request == appLoadVersion &&
                        !isDestroyed && !isFinishing) {
                        result.onSuccess { entries ->
                            installedApps = entries
                            renderApps(entries)
                        }.onFailure { error ->
                            Log.e("JianquLauncher", "Cannot load installed applications", error)
                            catalog.removeAllViews()
                            appCount.text = getString(R.string.apps_unavailable)
                            catalog.addView(text(getString(R.string.apps_load_error), 17f, muted))
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // Package callback can race Activity shutdown.
        }
    }

    private fun renderApps(entries: List<LauncherActivityInfo>) {
        if (page != Page.ALL_APPS || !::catalog.isInitialized || !::searchInput.isInitialized) return
        val query = searchInput.text.toString().trim()
        val visibleApps = if (query.isEmpty()) entries else entries.filter {
            LauncherAppSearch.matches(query, it.label.toString(), it.componentName.packageName)
        }
        catalog.removeAllViews()
        appCount.text = if (query.isEmpty()) getString(R.string.app_count, visibleApps.size)
            else "找到 " + visibleApps.size + " / " + entries.size + " 个应用"
        if (visibleApps.isEmpty()) {
            val message = if (query.isEmpty()) getString(R.string.empty_apps) else "没有找到“" + query + "”"
            catalog.addView(text(message, 17f, muted))
            return
        }
        val columns = catalog.columnCount
        val pixels = resources.displayMetrics.widthPixels
        val cell = ((pixels - dp(38) - dp(columns * 10)) / columns).coerceAtLeast(dp(90))
        visibleApps.forEach { entry ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                background = touch(ui.background, JianquDimensions.tileRadiusDp)
                setPadding(dp(6), dp(8), dp(6), dp(6))
                contentDescription = "打开 " + entry.label
                isClickable = true
                isFocusable = true
                setOnClickListener { open(entry) }
            }
            box.addView(ImageView(this).apply {
                setImageDrawable(entry.getIcon(0))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(60), dp(60)))
            box.addView(text(entry.label.toString(), 16f, ink).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(1), dp(6), dp(1), 0)
            }, LinearLayout.LayoutParams(-1, dp(49)))
            catalog.addView(box, GridLayout.LayoutParams().apply {
                width = cell
                height = dp(124)
                setMargins(0, 0, dp(10), dp(10))
            })
        }
    }

    private fun open(entry: LauncherActivityInfo) {
        if (!admit()) return
        try {
            restoreHomeOnResume = true
            appCatalogAdapter.launch(entry)
        } catch (e: Exception) {
            restoreHomeOnResume = false
            Log.w("JianquLauncher", "Application disappeared before launch", e)
            Toast.makeText(this, "应用无法打开，可能已卸载", Toast.LENGTH_SHORT).show()
            refreshApps()
        }
    }


}
