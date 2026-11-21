package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Offline-only weather presentation. Every condition/temperature is an explicitly
 * fictitious example. No location permission, API, network request or cache is used.
 */
class WeatherActivity : Activity() {
    private enum class Scenario(val label: String, val explanation: String) {
        NORMAL("常规", "排版演示：未接入天气供应商，因此没有实际预报或更新时间。"),
        OFFLINE("断网", "断网情景演示：没有网络时无法取得新预报，HOME 和全部应用仍可使用。"),
        LOCATION_DENIED("拒绝定位", "定位拒绝情景演示：本原型未申请定位权限，请手动选择城市。"),
        CACHE_EXPIRED("缓存过期", "缓存过期情景演示：目前没有真实天气缓存，不能将旧数据冒充最新预报。"),
        REQUEST_FAILED("请求失败", "天气请求失败情景演示：尚未接入 API，未实际发送或失败网络请求。")
    }
    private data class Example(val condition: String, val high: Int, val low: Int)
    private val examples = listOf(
        Example("晴间多云", 27, 19), Example("多云", 25, 18),
        Example("小雨", 23, 17), Example("阴转多云", 24, 17),
        Example("晴", 28, 20), Example("阵雨", 26, 19), Example("多云", 25, 18)
    )
    private val backgroundColor = Color.rgb(250, 248, 244)
    private val ink = Color.rgb(48, 53, 53)
    private val muted = Color.rgb(112, 119, 117)
    private val accent = Color.rgb(159, 88, 69)
    private val border = Color.rgb(235, 231, 225)
    private var scenario = Scenario.NORMAL
    private lateinit var scenarioNotice: TextView
    private val scenarioButtons = mutableMapOf<Scenario, TextView>()

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun rounded(color: Int, radius: Int, outline: Int? = null) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (outline != null) setStroke(dp(1), outline)
        }
    private fun touch(color: Int, radius: Int) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(35, 128, 80, 62)),
        rounded(color, radius), null
    )
    private fun label(value: String, size: Float, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }
    private fun action(value: String, click: () -> Unit) = label(value, 16f, accent, true).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = touch(Color.rgb(248, 237, 227), 12)
        setOnClickListener { click() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val old = savedInstanceState?.getString("scenario")
        scenario = Scenario.entries.firstOrNull { it.name == old } ?: Scenario.NORMAL
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("scenario", scenario.name)
        super.onSaveInstanceState(outState)
    }

    private fun updateScenarioControls() {
        scenarioNotice.text = scenario.explanation
        scenarioButtons.forEach { (option, button) ->
            val selected = option == scenario
            button.setTextColor(if (selected) Color.WHITE else ink)
            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            button.background = touch(if (selected) Color.rgb(160, 91, 72) else Color.WHITE, 12)
        }
    }

    private fun render() {
        val city = WeatherCity.current(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        setContentView(root)
        root.addView(label("‹  返回桌面", 18f, accent, true).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "返回桌面"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(-1, dp(46)))
        root.addView(label("未来七日天气", 30f, ink, true),
            LinearLayout.LayoutParams(-1, dp(57)))
        root.addView(label("示例预报 · 不是真实天气 · 不用于出行判断", 13f, accent),
            LinearLayout.LayoutParams(-1, dp(34)))

        val scroll = ScrollView(this).apply {
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(20))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val cityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(12), dp(12))
            background = rounded(Color.WHITE, 15, border)
        }
        cityRow.addView(label(
            if (city.isEmpty()) "尚未选择城市" else "城市：" + city,
            19f, ink, true
        ), LinearLayout.LayoutParams(0, dp(53), 1f))
        cityRow.addView(action(if (city.isEmpty()) "选择城市" else "更换城市") {
            WeatherCity.showPicker(this) { render() }
        }, LinearLayout.LayoutParams(dp(110), dp(49)))
        content.addView(cityRow)

        val dataNotice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(Color.rgb(249, 237, 224), 13)
        }
        dataNotice.addView(label("数据状态：未接入真实天气", 17f, accent, true))
        dataNotice.addView(label("更新时间：无 · 数据源：固定布局示例", 13f, muted),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        content.addView(dataNotice, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(15)
        })

        content.addView(label("七日预报示例", 20f, ink, true),
            LinearLayout.LayoutParams(-1, dp(51)).apply { topMargin = dp(13) })
        if (city.isEmpty()) {
            content.addView(label(
                "未选择城市。请先手动选择城市，再查看七天排版示例；当前不会读取设备定位。",
                16f, muted
            ).apply {
                background = rounded(Color.WHITE, 14, border)
                setPadding(dp(17), dp(23), dp(17), dp(23))
            }, LinearLayout.LayoutParams(-1, -2))
        } else {
            content.addView(label("以下温度与天气均为固定虚构示例，与所选城市无关。", 13f, accent),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            val cal = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("M月d日 EEE", Locale.CHINA)
            examples.forEachIndexed { index, example ->
                val weather = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(6), dp(14), dp(6))
                    background = rounded(Color.WHITE, 13, border)
                }
                val date = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                date.addView(label(dateFormat.format(cal.time), 17f, ink, true))
                date.addView(label("第" + (index + 1) + "天 · 示例", 12f, muted),
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                weather.addView(date, LinearLayout.LayoutParams(0, -2, 1f))
                weather.addView(label(example.condition, 15f, ink),
                    LinearLayout.LayoutParams(dp(90), -2))
                weather.addView(label(example.high.toString() + "° / " + example.low + "°",
                    17f, ink, true), LinearLayout.LayoutParams(dp(105), -2))
                content.addView(weather, LinearLayout.LayoutParams(-1, dp(78)).apply {
                    bottomMargin = dp(8)
                })
                cal.add(Calendar.DATE, 1)
            }
        }

        content.addView(label("状态情景演示", 18f, ink, true),
            LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(16) })
        content.addView(label("仅切换界面文案，不改变网络、定位权限或真实数据。", 13f, muted),
            LinearLayout.LayoutParams(-1, dp(27)))
        scenarioButtons.clear()
        Scenario.entries.chunked(3).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEach { option ->
                val selected = option == scenario
                val button = label(option.label, 14f,
                    if (selected) Color.WHITE else ink, selected).apply {
                    gravity = Gravity.CENTER
                    background = touch(
                        if (selected) Color.rgb(160, 91, 72) else Color.WHITE, 12
                    )
                    isClickable = true
                    isFocusable = true
                    contentDescription = "演示状态：" + option.label
                    setOnClickListener {
                        scenario = option
                        updateScenarioControls()
                    }
                }
                scenarioButtons[option] = button
                row.addView(button, LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    rightMargin = dp(8)
                })
            }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(8)
            })
        }
        scenarioNotice = label(scenario.explanation, 15f, ink).apply {
            background = rounded(Color.WHITE, 13, border)
            setPadding(dp(15), dp(13), dp(15), dp(13))
            minHeight = dp(76)
        }
        content.addView(scenarioNotice, LinearLayout.LayoutParams(-1, -2))

        content.addView(action("为什么没有自动定位？") {
            AlertDialog.Builder(this)
                .setTitle("设备位置与隐私")
                .setMessage("本原型未请求位置权限，也不会推断设备在哪里。城市名称由使用者手动输入；此处七日数值始终是示例。真实天气服务与可选定位需另行确认后接入。")
                .setPositiveButton("知道了", null)
                .show()
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })

    }
}
