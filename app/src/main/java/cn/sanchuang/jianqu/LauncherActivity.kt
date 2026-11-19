package cn.sanchuang.jianqu

import android.app.Activity
import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.Collator
import java.util.concurrent.Executors

/**
 * Small standalone HOME launcher. The real Android app catalog is not a mock.
 * Subscription and Xiao Nuan screens are intentionally left for JQ-003.
 */
class LauncherActivity : Activity() {
    private val bg = Color.rgb(245, 246, 248)
    private val ink = Color.rgb(27, 30, 35)
    private val secondary = Color.rgb(92, 100, 112)
    private val accent = Color.rgb(228, 87, 46)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var apps: LauncherApps
    private lateinit var catalog: GridLayout
    private lateinit var appCount: TextView

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = getSystemService(LauncherApps::class.java)
        buildHome()
        apps.registerCallback(packageCallback, main)
    }

    override fun onResume() {
        super.onResume()
        if (::catalog.isInitialized) refresh()
    }

    override fun onDestroy() {
        apps.unregisterCallback(packageCallback)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun panel(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sp
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun buildHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(22), dp(22), dp(22), dp(12))
        }
        setContentView(root)

        val brand = label("三创  /  SANCHUANG", 19f, accent, true)
        root.addView(brand, LinearLayout.LayoutParams(-1, dp(40)))

        val title = label("我的闺蜜机", 33f, ink, true)
        root.addView(title, LinearLayout.LayoutParams(-1, dp(55)))
        root.addView(label("熟悉的应用，就在这里。", 17f, secondary),
            LinearLayout.LayoutParams(-1, dp(34)))

        val device = label("设备服务 · 原型开发中", 18f, ink, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(10), 0)
            background = panel(Color.WHITE, 14)
            setOnClickListener {
                Toast.makeText(context, "订阅与小暖演示尚未加入", Toast.LENGTH_SHORT).show()
            }
        }
        val deviceLp = LinearLayout.LayoutParams(-1, dp(68)).apply {
            topMargin = dp(20)
            bottomMargin = dp(18)
        }
        root.addView(device, deviceLp)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(label("已安装应用", 23f, ink, true), LinearLayout.LayoutParams(0, dp(54), 1f))
        appCount = label("正在读取…", 14f, secondary)
        header.addView(appCount)
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
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
    }

    private fun refresh() {
        worker.execute {
            val entries = try {
                apps.getActivityList(null, Process.myUserHandle())
                    .sortedWith(compareBy(Collator.getInstance(java.util.Locale.CHINA)) { it.label.toString() })
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: RuntimeException) {
                emptyList()
            }
            main.post {
                if (!isDestroyed && !isFinishing) renderApps(entries)
            }
        }
    }

    private fun renderApps(entries: List<LauncherActivityInfo>) {
        catalog.removeAllViews()
        appCount.text = getString(R.string.app_count, entries.size)
        if (entries.isEmpty()) {
            catalog.addView(label(getString(R.string.empty_apps), 17f, secondary))
            return
        }
        val columns = catalog.columnCount
        val pixels = resources.displayMetrics.widthPixels
        val cell = ((pixels - dp(44) - dp(columns * 10)) / columns).coerceAtLeast(dp(90))
        entries.forEach { entry ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                background = panel(Color.WHITE, 14)
                setPadding(dp(6), dp(12), dp(6), dp(7))
                contentDescription = "打开 " + entry.label
                isClickable = true
                isFocusable = true
                setOnClickListener { open(entry.componentName, entry.user) }
            }
            val image = ImageView(this).apply {
                setImageDrawable(entry.getIcon(0))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = null
            }
            box.addView(image, LinearLayout.LayoutParams(dp(60), dp(60)))
            val name = label(entry.label.toString(), 15f, ink).apply {
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(1), dp(6), dp(1), 0)
            }
            box.addView(name, LinearLayout.LayoutParams(-1, dp(49)))
            catalog.addView(box, GridLayout.LayoutParams().apply {
                width = cell
                height = dp(140)
                setMargins(0, 0, dp(10), dp(10))
            })
        }
    }

    private fun open(component: ComponentName, user: UserHandle) {
        try {
            apps.startMainActivity(component, user, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "应用无法打开，可能已卸载", Toast.LENGTH_SHORT).show()
            refresh()
        }
    }
}
