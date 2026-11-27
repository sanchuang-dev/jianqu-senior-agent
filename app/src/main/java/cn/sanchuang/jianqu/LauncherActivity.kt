package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
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
import android.widget.TextView
import android.widget.Toast
import cn.sanchuang.jianqu.admission.DeviceAccess
import cn.sanchuang.jianqu.admission.DeviceAccessEnvironment
import cn.sanchuang.jianqu.admission.LeaseGateView
import cn.sanchuang.jianqu.home.HomeAction
import cn.sanchuang.jianqu.home.HomeScreen
import cn.sanchuang.jianqu.settings.SettingsScreen
import cn.sanchuang.jianqu.system.apps.HomeAppSearchDialog
import cn.sanchuang.jianqu.system.apps.LauncherAppCatalog
import cn.sanchuang.jianqu.system.apps.LauncherAppSearch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Android HOME is a stable place to return to, not the device's entire application catalog.
 * All ten home functions are fixed; the app catalog and HOME search share real installed apps.
 * Routes retain the admission check; downstream screens own their real or prototype capability.
 */
class LauncherActivity : Activity() {
    private val ui = JianquColors(this)
    private enum class Page { HOME, ALL_APPS, SETTINGS, RENTAL_GATE }
    private var settingsSection = SettingsScreen.Section.ENVIRONMENT
    private val access by lazy { DeviceAccessEnvironment.open(this) }
    private var renderedGateState: DeviceAccess? = null
    private val desktopBars by lazy { LauncherSystemBars(window) }

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
    private var homeSearch: HomeAppSearchDialog? = null
    private val uiPreferences by lazy { getSharedPreferences("launcher_ui", MODE_PRIVATE) }
    private var page = Page.HOME
    private var restoreHomeOnResume = false
    private var returningFromWeather = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appCatalogAdapter = LauncherAppCatalog.open(this, main) { refreshApps() }
        appCatalogAdapter.start()
        settingsSection = SettingsScreen.Section.entries.firstOrNull {
            it.name == savedInstanceState?.getString("settings_section")
        } ?: SettingsScreen.Section.ENVIRONMENT
        if (savedInstanceState?.getBoolean("settings_open") == true) showSettings() else showHome()
        if (savedInstanceState == null && !uiPreferences.getBoolean("intro_seen", false)) {
            main.post {
                if (!isFinishing && !isDestroyed && page == Page.HOME) showFirstUseGuide()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settings_open", page == Page.SETTINGS)
        outState.putString("settings_section", settingsSection.name)
        super.onSaveInstanceState(outState)
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
        homeSearch?.dismiss()
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
            Page.HOME -> Unit // A launcher never closes itself on Back.
        }
    }

    override fun onDestroy() {
        homeSearch?.dismiss()
        appCatalogAdapter.stop()
        searchUpdate?.let(main::removeCallbacks)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()

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
        homeSearch?.dismiss()
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

    private fun showHome() {
        if (!admit()) return
        homeSearch?.dismiss()
        page = Page.HOME
        renderedGateState = null
        desktopBars.apply(edgeToEdge = true)
        val screen = HomeScreen(
            activity = this,
            previewUnenforced = access.current() == DeviceAccess.PREVIEW_UNENFORCED,
            developer = access.developerAction(this) { showHome() },
            onAction = ::openQuickAction,
            onSearch = ::showHomeSearch,
            onSos = { if (admit()) startActivity(Intent(this, SosActivity::class.java)) },
            onWeather = {
                if (admit()) {
                    returningFromWeather = true
                    startActivity(Intent(this, WeatherActivity::class.java))
                }
            },
            onCompanion = {
                if (admit()) startActivity(Intent(this, DemoActivity::class.java)
                    .putExtra(DemoActivity.EXTRA_OPEN_CHAT, true))
            }
        )
        setContentView(screen.render())
    }

    private fun openQuickAction(destination: HomeAction) {
        if (!admit()) return
        when (destination) {
            HomeAction.ALL_APPS -> showAllApps()
            HomeAction.FAMILY_CALL -> startActivity(Intent(this, FamilyCallActivity::class.java))
            HomeAction.STOREFRONT -> startActivity(Intent(this, StorefrontActivity::class.java))
            HomeAction.CONTENT_ALBUM -> openContent(ContentGalleryActivity.ALBUM)
            HomeAction.CONTENT_VIDEO -> openContent(ContentGalleryActivity.VIDEO)
            HomeAction.CONTENT_MUSIC -> openContent(ContentGalleryActivity.MUSIC)
            HomeAction.CONTENT_HEALTH -> startActivity(Intent(this, HealthCenterActivity::class.java))
            HomeAction.CONTENT_NEWS -> openContent(ContentGalleryActivity.NEWS)
            HomeAction.SERVICES -> startActivity(Intent(this, DemoActivity::class.java))
            HomeAction.SETTINGS -> {
                settingsSection = SettingsScreen.Section.ENVIRONMENT
                showSettings()
            }

        }
    }

    private fun openContent(section: String) {
        startActivity(Intent(this, ContentGalleryActivity::class.java)
            .putExtra(ContentGalleryActivity.EXTRA_SECTION, section))
    }

    private fun showSettings() {
        if (!admit()) return
        page = Page.SETTINGS
        desktopBars.apply(edgeToEdge = false)
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "未知"
        setContentView(SettingsScreen(
            activity = this,
            initialSection = settingsSection,
            city = WeatherCity.current(this),
            version = version,
            onSectionChanged = { settingsSection = it },
            onBack = ::showHome,
            onSystemSettings = { if (admit()) openAndroidSettings(it) },
            onCity = { if (admit()) WeatherCity.showPicker(this) { showSettings() } },
            onHelp = { if (admit()) showHelp() },
            onAbout = {
                if (admit()) AlertDialog.Builder(this)
                    .setTitle("见趣 · 闺蜜机")
                    .setMessage("版本 $version\n\n当前为预览版本，小暖与租赁尚未接入真实服务。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        ).render())
    }

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
        AlertDialog.Builder(this)
            .setTitle("使用帮助")
            .setMessage("按设备 Home 键返回桌面；在首页右上角搜索或打开全部应用。\n\n家人通话与 SOS 尚未接入真实服务。紧急情况请联系现场人员或使用可靠电话。")
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showFirstUseGuide() {
        AlertDialog.Builder(this)
            .setTitle("欢迎使用闺蜜机")
            .setMessage("按设备 Home 键返回桌面，使用右上角搜索或打开全部应用。\n\n通话与 SOS 尚未接入真实服务。")
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
            setPadding(dp(19), dp(JianquSpacing.space16Dp), dp(19), dp(JianquSpacing.space12Dp))
        }
        desktopBars.apply(edgeToEdge = false)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            addView(JianquPageHeader.create(this@LauncherActivity, "全部应用", onBack = ::showHome))
            addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(screen)
        installedApps = emptyList()
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = shape(ui.surface, JianquDimensions.cardRadiusDp, divider)
            setPadding(dp(1), 0, dp(JianquSpacing.space4Dp), 0)
        }
        searchInput = EditText(this).apply {
            hint = "搜索已安装应用"
            setSingleLine(true)
            textSize = JianquTypography.bodySp
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            background = null
            setPadding(dp(17), dp(5), dp(JianquSpacing.space10Dp), dp(5))
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
        searchRow.addView(text("清除", JianquTypography.bodyCompactSp, terracotta, true).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { searchInput.text.clear() }
        }, LinearLayout.LayoutParams(dp(58), dp(52)))
        root.addView(searchRow, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(JianquSpacing.space8Dp); bottomMargin = dp(JianquSpacing.space6Dp) })
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("已安装应用", JianquTypography.bodySp, ink, true),
            LinearLayout.LayoutParams(0, dp(46), 1f))
        appCount = text("正在读取…", JianquTypography.supportingSp, muted)
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

    private fun showHomeSearch() {
        if (!admit() || homeSearch?.isShowing == true) return
        val wallpaper = findViewById<FrameLayout>(android.R.id.content).getChildAt(0)
        val dialog = HomeAppSearchDialog(this, wallpaper, onOpen = ::open, onRetry = ::refreshApps)
        homeSearch = dialog
        dialog.setOnDismissListener {
            if (homeSearch === dialog) homeSearch = null
        }
        dialog.show()
        refreshApps()
    }

    private fun refreshApps() {
        val dialog = homeSearch?.takeIf { it.isShowing }
        if (isDestroyed || worker.isShutdown || (dialog == null &&
                (page != Page.ALL_APPS || !::catalog.isInitialized))) return
        val request = ++appLoadVersion
        try {
            worker.execute {
                val result = runCatching {
                    appCatalogAdapter.load()
                }
                main.post {
                    if (request == appLoadVersion && !isDestroyed && !isFinishing &&
                        dialog != null && homeSearch === dialog && dialog.isShowing && page == Page.HOME) {
                        result.onSuccess { dialog.showApps(it) }.onFailure {
                            Log.e("JianquLauncher", "Cannot load installed applications", it)
                            dialog.showError()
                        }
                    } else if (dialog == null && page == Page.ALL_APPS && request == appLoadVersion &&
                        !isDestroyed && !isFinishing) {
                        result.onSuccess { entries ->
                            installedApps = entries
                            renderApps(entries)
                        }.onFailure { error ->
                            Log.e("JianquLauncher", "Cannot load installed applications", error)
                            catalog.removeAllViews()
                            appCount.text = getString(R.string.apps_unavailable)
                            catalog.addView(text(getString(R.string.apps_load_error), JianquTypography.bodyEmphasisSp, muted))
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
            catalog.addView(text(message, JianquTypography.bodyEmphasisSp, muted))
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
                setPadding(dp(JianquSpacing.space6Dp), dp(JianquSpacing.space8Dp), dp(JianquSpacing.space6Dp), dp(JianquSpacing.space6Dp))
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
            box.addView(text(entry.label.toString(), JianquTypography.bodySp, ink).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(1), dp(JianquSpacing.space6Dp), dp(1), 0)
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
            homeSearch?.dismiss()
        } catch (e: Exception) {
            restoreHomeOnResume = false
            Log.w("JianquLauncher", "Application disappeared before launch", e)
            Toast.makeText(this, "应用无法打开，可能已卸载", Toast.LENGTH_SHORT).show()
            refreshApps()
        }
    }


}
