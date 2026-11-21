package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
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
import java.text.Collator
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.min

/**
 * Android HOME is a stable place to return to, not the device's entire application catalog.
 * All ten home functions are fixed; only the "全部应用" page enumerates real installed apps.
 * Other business/communication functions are intentionally honest navigation placeholders
 * until their independent JQ-005..011 contracts are implemented.
 */
class LauncherActivity : Activity() {
    private enum class Page { HOME, ALL_APPS, SETTINGS, HELP }

    private data class QuickAction(val title: String, val icon: Int, val color: Int)

    private val backgroundColor = Color.rgb(250, 248, 244)
    private val ink = Color.rgb(48, 53, 53)
    private val muted = Color.rgb(112, 119, 117)
    private val terracotta = Color.rgb(159, 88, 69)
    private val divider = Color.rgb(235, 231, 225)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var apps: LauncherApps
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

    private val quickActions = listOf(
        QuickAction("家人通话", 0, Color.rgb(226, 242, 237)),
        QuickAction("家庭相册", 1, Color.rgb(251, 237, 217)),
        QuickAction("影视节目", 2, Color.rgb(225, 236, 248)),
        QuickAction("精选商城", 3, Color.rgb(250, 226, 219)),
        QuickAction("音乐戏曲", 4, Color.rgb(246, 232, 243)),
        QuickAction("健康讲座", 5, Color.rgb(229, 242, 226)),
        QuickAction("建政栏目", 6, Color.rgb(232, 236, 246)),
        QuickAction("我的服务", 7, Color.rgb(248, 231, 227)),
        QuickAction("设置", 8, Color.rgb(235, 235, 235)),
        QuickAction("全部应用", 9, Color.rgb(230, 227, 245))
    )

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refreshApps()
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = getSystemService(LauncherApps::class.java)
        apps.registerCallback(packageCallback, main)
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
        apps.unregisterCallback(packageCallback)
        searchUpdate?.let(main::removeCallbacks)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private val landscape get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun shape(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun touch(color: Int, radius: Int, border: Int? = null): RippleDrawable =
        RippleDrawable(
            ColorStateList.valueOf(Color.argb(35, 123, 83, 64)),
            shape(color, radius, border),
            null
        )

    private fun text(
        value: String,
        size: Float,
        color: Int = ink,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
    }

    private fun LinearLayout.addSpace(height: Int) {
        addView(View(context), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun showHome() {
        page = Page.HOME
        val side = if (landscape) 28 else 20
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(side), dp(if (landscape) 10 else 16), dp(side), dp(12))
        }
        setContentView(root)

        val brand = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        brand.addView(ImageView(this).apply {
            setImageResource(R.drawable.sanchuang_logo)
            contentDescription = "三创企业标志"
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        brand.addView(text("三创  ·  SANCHUANG", 15f, ink, true),
            LinearLayout.LayoutParams(-2, dp(36)).apply { leftMargin = dp(9) })
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(42)))

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
        body.addSpace(if (landscape) 12 else 19)
        buildCompanion(body)
        body.addSpace(if (landscape) 12 else 19)

        val section = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        section.addView(text("常用功能", 18f, ink, true), LinearLayout.LayoutParams(0, dp(34), 1f))
        section.addView(text("固定入口", 12f, muted))
        body.addView(section)
        body.addSpace(8)
        buildQuickActions(body)
        body.addSpace(10)

        buildSos(root)
    }

    private fun buildEnvironment(body: LinearLayout) {
        val line = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        val clocks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clocks.addView(TextClock(this).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            textSize = if (landscape) 34f else 42f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        })
        clocks.addView(TextClock(this).apply {
            format12Hour = "M月d日 EEEE"
            format24Hour = "M月d日 EEEE"
            textSize = 14f
            setTextColor(muted)
        })
        line.addView(clocks, LinearLayout.LayoutParams(0, dp(if (landscape) 80 else 100), 1f))

        val weather = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(15), 0, dp(10), 0)
            background = touch(Color.rgb(244, 239, 229), 16)
            isClickable = true
            isFocusable = true
            contentDescription = "当地天气，查看未来七天预报，目前未接入天气服务"
            setOnClickListener {
                returningFromWeather = true
                startActivity(Intent(this@LauncherActivity, WeatherActivity::class.java))
            }
        }
        val chosenCity = WeatherCity.current(this)
        val weatherHeading = if (chosenCity.isEmpty()) "☁  当地天气" else "☁  $chosenCity · 天气"
        weather.addView(text(weatherHeading, 16f, ink, true))
        weather.addView(text("七日预报 · 待接入  ›", 12f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        line.addView(weather, LinearLayout.LayoutParams(
            dp(if (landscape) 245 else 180), dp(if (landscape) 72 else 88)
        ))
        body.addView(line)
    }

    private fun buildCompanion(body: LinearLayout) {
        val tile = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(13), dp(16), dp(13))
            background = RippleDrawable(
                ColorStateList.valueOf(Color.argb(35, 120, 70, 55)),
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.rgb(250, 231, 218), Color.rgb(252, 242, 227))).apply {
                    cornerRadius = dp(20).toFloat()
                }, null
            )
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
        val orb = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(255, 249, 232), Color.rgb(241, 152, 125), Color.rgb(214, 119, 127))).apply {
                shape = GradientDrawable.OVAL
            }
        }
        orb.addView(View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.argb(130, 255, 255, 255))
                shape = GradientDrawable.OVAL
            }
        }, FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER))
        tile.addView(orb, LinearLayout.LayoutParams(dp(if (landscape) 58 else 70), dp(if (landscape) 58 else 70)))
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text("小暖", if (landscape) 21f else 25f, ink, true))
        copy.addView(text("想聊聊天，还是一起做点什么？", if (landscape) 13f else 14f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        copy.addView(text("本地模拟体验 · 非真实 AI", 12f, terracotta),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        tile.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(16) })
        tile.addView(text("›", 35f, terracotta))
        body.addView(tile, LinearLayout.LayoutParams(-1, dp(if (landscape) 98 else 118)))
    }

    private fun buildQuickActions(body: LinearLayout) {
        val cols = if (landscape) 5 else 2
        quickActions.chunked(cols).forEach { group ->
            val row = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                orientation = LinearLayout.HORIZONTAL
            }
            group.forEachIndexed { index, item ->
                row.addView(makeQuickAction(item), LinearLayout.LayoutParams(0, dp(if (landscape) 88 else 87), 1f).apply {
                    if (index > 0) leftMargin = dp(9)
                })
            }
            body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
    }

    private fun makeQuickAction(action: QuickAction): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (landscape) 7 else 11), dp(6), dp(4), dp(6))
            background = touch(Color.rgb(255, 254, 252), 16, divider)
            isClickable = true
            isFocusable = true
            contentDescription = action.title
            val iconBox = FrameLayout(this@LauncherActivity).apply {
                background = shape(action.color, 13)
            }
            iconBox.addView(HomeSymbol(this@LauncherActivity, action.icon, ink),
                FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
            addView(iconBox, LinearLayout.LayoutParams(dp(if (landscape) 44 else 54), dp(if (landscape) 44 else 54)))
            addView(text(action.title, if (landscape) 14f else 16f, ink, true),
                LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(if (landscape) 7 else 10) })
            setOnClickListener { openQuickAction(action.icon, action.title) }
        }
    }

    private fun buildSos(root: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            background = touch(Color.rgb(253, 234, 229), 16, Color.rgb(244, 209, 199))
            isClickable = true
            isFocusable = true
            contentDescription = "SOS 紧急求助，当前为未接入真实求救功能的原型"
            setOnClickListener {
                AlertDialog.Builder(this@LauncherActivity)
                    .setTitle("紧急求助尚未启用")
                    .setMessage("当前是功能原型，无法发送求助、通知护理站或拨打急救电话。遇到紧急情况，请立即联系现场人员或使用可靠的电话求助。")
                    .setPositiveButton("我知道了", null)
                    .show()
            }
        }
        val icon = text("SOS", 17f, Color.rgb(157, 50, 43), true)
        row.addView(icon)
        row.addView(text("  紧急求助", 17f, Color.rgb(132, 52, 48), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("原型未接入  ›", 12f, Color.rgb(157, 80, 77)))
        root.addView(row, LinearLayout.LayoutParams(-1, dp(65)).apply { topMargin = dp(6) })
    }

    private fun openQuickAction(kind: Int, name: String) {
        when (kind) {
            9 -> showAllApps()
            7 -> startActivity(Intent(this, DemoActivity::class.java))
            8 -> showSettings()
            else -> unavailable(name, "“" + name + "”是第二期固定入口，目前尚未接入实际服务。没有发起通话、消费或其他外部操作。")
        }
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
        root.addView(text("设置", 30f, ink, true), LinearLayout.LayoutParams(-1, dp(53)))
        root.addView(text("管理本机桌面，系统设置由 Android 负责。", 14f, muted),
            LinearLayout.LayoutParams(-1, dp(38)))

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(16))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        content.addView(text("桌面偏好", 17f, ink, true),
            LinearLayout.LayoutParams(-1, dp(38)))
        val city = WeatherCity.current(this)
        addSettingsItem(content, "天气城市",
            if (city.isEmpty()) "尚未选择城市 · 天气数据未接入" else "$city · 天气数据未接入") {
            showCityDialog()
        }
        addSettingsItem(content, "Wi-Fi 与网络", "打开 Android 的网络设置") {
            openAndroidSettings(Settings.ACTION_WIFI_SETTINGS)
        }
        addSettingsItem(content, "屏幕与亮度", "打开 Android 的显示设置") {
            openAndroidSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }
        addSettingsItem(content, "默认主屏幕", "选择或更改 Android HOME 应用") {
            openAndroidSettings(Settings.ACTION_HOME_SETTINGS)
        }

        content.addSpace(12)
        content.addView(text("了解设备", 17f, ink, true),
            LinearLayout.LayoutParams(-1, dp(40)))
        addSettingsItem(content, "首次使用与帮助", "默认桌面、全部应用与安全提示") {
            showHelp()
        }
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "未知"
        addSettingsItem(content, "关于闺蜜机", "版本 $version · 原型演示") {
            AlertDialog.Builder(this)
                .setTitle("闺蜜机（演示）")
                .setMessage("版本 $version\n普通第三方 Android Launcher。本期小暖和设备订阅为本地模拟，其他服务需分别接入。")
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun addSettingsItem(
        container: LinearLayout,
        title: String,
        description: String,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(11), dp(15), dp(11))
            background = touch(Color.WHITE, 14, divider)
            isClickable = true
            isFocusable = true
            contentDescription = "$title，$description"
            setOnClickListener { action() }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(title, 17f, ink, true))
        copy.addView(text(description, 13f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text("›", 26f, muted))
        container.addView(row, LinearLayout.LayoutParams(-1, dp(78)).apply {
            bottomMargin = dp(9)
        })
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
        root.addView(text("首次使用与帮助", 28f, ink, true),
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
                background = shape(Color.WHITE, 14, divider)
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
        root.addView(text("全部应用", 30f, ink, true), LinearLayout.LayoutParams(-1, dp(56)))
        installedApps = emptyList()
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchInput = EditText(this).apply {
            hint = "搜索已安装应用"
            setSingleLine(true)
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = shape(Color.WHITE, 14, divider)
            setPadding(dp(14), dp(5), dp(10), dp(5))
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
        root.addView(searchRow, LinearLayout.LayoutParams(-1, dp(65)))
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("设备上可启动的应用", 15f, muted),
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
                    apps.getActivityList(null, Process.myUserHandle())
                        .sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.label.toString() })
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
            it.label.toString().contains(query, ignoreCase = true) ||
                it.componentName.packageName.contains(query, ignoreCase = true)
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
                background = touch(Color.WHITE, 14)
                setPadding(dp(6), dp(12), dp(6), dp(7))
                contentDescription = "打开 " + entry.label
                isClickable = true
                isFocusable = true
                setOnClickListener { open(entry.componentName, entry.user) }
            }
            box.addView(ImageView(this).apply {
                setImageDrawable(entry.getIcon(0))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }, LinearLayout.LayoutParams(dp(60), dp(60)))
            box.addView(text(entry.label.toString(), 15f, ink).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(1), dp(6), dp(1), 0)
            }, LinearLayout.LayoutParams(-1, dp(49)))
            catalog.addView(box, GridLayout.LayoutParams().apply {
                width = cell
                height = dp(140)
                setMargins(0, 0, dp(10), dp(10))
            })
        }
    }

    private fun open(component: ComponentName, user: UserHandle) {
        try {
            restoreHomeOnResume = true
            apps.startMainActivity(component, user, null, null)
        } catch (e: Exception) {
            restoreHomeOnResume = false
            Log.w("JianquLauncher", "Application disappeared before launch", e)
            Toast.makeText(this, "应用无法打开，可能已卸载", Toast.LENGTH_SHORT).show()
            refreshApps()
        }
    }

    /** Compact, dependency-free line symbols. These identify prototype services, not installed apps. */
    private class HomeSymbol(context: Context, private val kind: Int, color: Int) : View(context) {
        private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Reuse immutable geometry: Android redraws launcher icons frequently while scrolling.
        private val cameraPath = Path().apply {
            moveTo(69f, 41f); lineTo(85f, 31f); lineTo(85f, 68f); lineTo(69f, 58f); close()
        }
        private val galleryPath = Path().apply {
            moveTo(21f, 72f); lineTo(42f, 49f); lineTo(56f, 63f); lineTo(66f, 55f); lineTo(80f, 73f)
        }
        private val moviePath = Path().apply {
            moveTo(43f, 37f); lineTo(66f, 50f); lineTo(43f, 63f); close()
        }
        private val bagArc = RectF(37f, 19f, 63f, 51f)
        private val musicLeft = RectF(22f, 63f, 49f, 79f)
        private val musicRight = RectF(53f, 54f, 79f, 71f)
        private val heartPath = Path().apply {
            moveTo(50f, 78f)
            cubicTo(32f, 64f, 19f, 52f, 19f, 39f)
            cubicTo(19f, 22f, 40f, 19f, 50f, 35f)
            cubicTo(62f, 19f, 81f, 24f, 81f, 39f)
            cubicTo(81f, 53f, 68f, 65f, 50f, 78f)
            close()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scale = min(width, height) / 100f
            canvas.save()
            canvas.translate((width - scale * 100f) / 2f, (height - scale * 100f) / 2f)
            canvas.scale(scale, scale)
            when (kind) {
                0 -> {
                    canvas.drawRoundRect(16f, 30f, 63f, 70f, 7f, 7f, pen)
                    canvas.drawPath(cameraPath, pen)
                }
                1 -> {
                    canvas.drawRoundRect(16f, 20f, 84f, 80f, 7f, 7f, pen)
                    canvas.drawCircle(63f, 37f, 7f, pen)
                    canvas.drawPath(galleryPath, pen)
                }
                2 -> {
                    canvas.drawRoundRect(15f, 23f, 85f, 77f, 7f, 7f, pen)
                    canvas.drawPath(moviePath, pen)
                }
                3 -> {
                    canvas.drawRoundRect(25f, 36f, 75f, 81f, 5f, 5f, pen)
                    canvas.drawArc(bagArc, 180f, 180f, false, pen)
                }
                4 -> {
                    canvas.drawLine(47f, 28f, 47f, 70f, pen)
                    canvas.drawLine(47f, 28f, 77f, 20f, pen)
                    canvas.drawLine(77f, 20f, 77f, 59f, pen)
                    pen.style = Paint.Style.FILL
                    canvas.drawOval(musicLeft, pen)
                    canvas.drawOval(musicRight, pen)
                    pen.style = Paint.Style.STROKE
                }
                5 -> {
                    canvas.drawCircle(50f, 50f, 30f, pen)
                    canvas.drawLine(50f, 33f, 50f, 67f, pen)
                    canvas.drawLine(33f, 50f, 67f, 50f, pen)
                }
                6 -> {
                    canvas.drawRoundRect(19f, 19f, 81f, 81f, 5f, 5f, pen)
                    canvas.drawLine(29f, 36f, 71f, 36f, pen)
                    canvas.drawLine(29f, 48f, 71f, 48f, pen)
                    canvas.drawLine(29f, 60f, 57f, 60f, pen)
                    canvas.drawLine(29f, 72f, 64f, 72f, pen)
                }
                7 -> {
                    canvas.drawPath(heartPath, pen)
                }
                8 -> {
                    canvas.drawCircle(50f, 50f, 16f, pen)
                    for (i in 0..7) {
                        val a = i * Math.PI / 4
                        val dx = kotlin.math.cos(a).toFloat()
                        val dy = kotlin.math.sin(a).toFloat()
                        canvas.drawLine(50f + dx * 29, 50f + dy * 29, 50f + dx * 39, 50f + dy * 39, pen)
                    }
                }
                9 -> {
                    for (x in listOf(22f, 55f)) for (y in listOf(22f, 55f)) {
                        canvas.drawRoundRect(x, y, x + 23f, y + 23f, 4f, 4f, pen)
                    }
                }
            }
            canvas.restore()
        }
    }
}
