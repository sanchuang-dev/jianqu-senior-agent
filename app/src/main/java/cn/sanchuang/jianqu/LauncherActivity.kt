package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
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
    private enum class Page { HOME, ALL_APPS, SETTINGS, HELP }

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
        when (page) {
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

    private fun showHome() {
        page = Page.HOME
        val side = if (landscape) 28 else 20
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(side), dp(if (landscape) 10 else 12), dp(side), dp(10))
        }
        setContentView(root)

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), 0, dp(2), dp(12))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        buildEnvironment(body)
        body.addSpace(if (landscape) 12 else 12)
        buildCompanion(body)
        body.addSpace(if (landscape) 12 else 12)

        val section = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        section.addView(JianquTypography.display(this, "常用功能", JianquTypography.sectionSp, ink), LinearLayout.LayoutParams(0, dp(34), 1f))
        body.addView(section)
        body.addSpace(4)
        buildQuickActions(body)
        body.addSpace(4)

        buildSos(root)
    }

    private fun buildEnvironment(body: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        // Date and weather form one left-aligned environment group.
        // The right-hand area stays visually empty until artwork is supplied.
        val environment = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        environment.addView(TextClock(this).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            textSize = if (landscape) 36f else 44f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        })
        environment.addView(TextClock(this).apply {
            format12Hour = "M月d日 EEEE"
            format24Hour = "M月d日 EEEE"
            textSize = 14f
            setTextColor(muted)
        })

        val weatherPlace = WeatherCity.place(this)
        val cachedWeather = weatherPlace?.let { WeatherData.cached(this, it) }
        val weatherLabel = when {
            weatherPlace == null -> "☁  选择城市  ›"
            !WeatherData.consented(this) -> "☁  ${weatherPlace.name} · 启用天气  ›"
            cachedWeather == null -> "☁  ${weatherPlace.name} · 查询天气  ›"
            cachedWeather.stale ->
                "☁  ${weatherPlace.name} · ${cachedWeather.nearTemperature}° · 缓存过期  ›"
            else ->
                "☁  ${weatherPlace.name} · ${cachedWeather.nearTemperature}° · ${cachedWeather.days.first().symbol}  ›"
        }
        environment.addView(text(weatherLabel, if (landscape) 15f else 13f, terracotta).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            background = touch(backgroundColor, 12)
            setPadding(dp(3), 0, dp(3), 0)
            isClickable = true
            isFocusable = true
            contentDescription = "天气，查看七日预报"
            setOnClickListener {
                returningFromWeather = true
                startActivity(Intent(this@LauncherActivity, WeatherActivity::class.java))
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(2) })

        val infoWidth = if (landscape) 360 else 300
        row.addView(environment, LinearLayout.LayoutParams(dp(infoWidth), -2))
        // Reserve horizontal space for future authorized artwork without forcing extra vertical space.
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        body.addView(row)
    }

    private fun buildCompanion(body: LinearLayout) {
        val tile = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(13), dp(16), dp(13))
            background = touch(ui.companionStart, JianquDimensions.companionRadiusDp, ui.accentSoftBorder)
            contentDescription = "小暖，进入本地模拟陪伴交互"
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(
                    Intent(this@LauncherActivity, DemoActivity::class.java)
                        .putExtra(DemoActivity.EXTRA_OPEN_CHAT, true)
                )
            }
        }
        val orb = companionOrb(this)
        tile.addView(orb, LinearLayout.LayoutParams(dp(if (landscape) 58 else 70), dp(if (landscape) 58 else 70)))
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(JianquTypography.display(this, "小暖", if (landscape) 25f else 29f, ink))
        copy.addView(text("想聊聊天，还是一起做点什么？", if (landscape) 13f else 14f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        copy.addView(text("本地模拟体验 · 非真实 AI", 12f, terracotta),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        tile.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
        tile.addView(text("›", 35f, terracotta))
        body.addView(tile, LinearLayout.LayoutParams(-1, dp(if (landscape) 98 else 108)))
    }

    private fun buildQuickActions(body: LinearLayout) {
        val cols = if (landscape) 5 else 2
        quickActions.chunked(cols).forEach { group ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            group.forEachIndexed { index, item ->
                row.addView(makeQuickAction(item), LinearLayout.LayoutParams(0, dp(if (landscape) 84 else 74), 1f).apply {
                    if (index > 0) leftMargin = dp(8)
                })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun makeQuickAction(action: QuickAction): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (landscape) 7 else 11), dp(6), dp(4), dp(6))
            background = touch(ui.surface, JianquDimensions.tileRadiusDp, divider)
            isClickable = true
            isFocusable = true
            contentDescription = action.title
            val iconBox = FrameLayout(this@LauncherActivity).apply {
                background = shape(action.color, JianquDimensions.iconWellRadiusDp)
            }
            iconBox.addView(ImageView(this@LauncherActivity).apply {
                setImageResource(action.iconRes)
                imageTintList = ColorStateList.valueOf(ink)
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(
                dp(if (landscape) 30 else 32), dp(if (landscape) 30 else 32), Gravity.CENTER
            ))
            addView(iconBox, LinearLayout.LayoutParams(dp(if (landscape) 42 else 46), dp(if (landscape) 42 else 46)))
            addView(text(action.title, if (landscape) 15f else 17f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(if (landscape) 7 else 10) })
            setOnClickListener { openQuickAction(action.destination, action.title) }
        }
    }

    private fun buildSos(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = touch(ui.dangerSurface, 16, ui.dangerBorder)
            isClickable = true
            isFocusable = true
            contentDescription = "SOS 紧急求助，当前为未接入真实求救功能的原型"
            setOnClickListener {
                startActivity(Intent(this@LauncherActivity, SosActivity::class.java))
            }
        }
        val icon = text("SOS", 17f, ui.danger, true)
        row.addView(icon)
        row.addView(text("  紧急求助", 17f, ui.danger, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("原型未接入  ›", 12f, ui.danger))
        root.addView(row, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(6) })
    }

    private fun openQuickAction(destination: QuickActionDestination, name: String) {
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
        page = Page.SETTINGS
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(19), dp(16), dp(19), dp(16))
        }
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
        page = Page.HELP
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(20), dp(16), dp(20), dp(20))
        }
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
        page = Page.ALL_APPS
        restoreHomeOnResume = false
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(19), dp(16), dp(19), dp(12))
        }
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
