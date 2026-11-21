package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Real, opted-in MET Norway forecast. Weather works without device location or an API key. */
class WeatherActivity : Activity() {
    private val backgroundColor = Color.rgb(250, 248, 244)
    private val ink = Color.rgb(48, 53, 53)
    private val muted = Color.rgb(112, 119, 117)
    private val accent = Color.rgb(159, 88, 69)
    private val border = Color.rgb(235, 231, 225)
    private var request: WeatherData.Request? = null
    private var currentPlace: WeatherCity.Place? = null
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private var latest: WeatherData.Forecast? = null
    private var lastError: String? = null

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
        render()
    }

    override fun onStop() {
        request?.cancel()
        request = null
        super.onStop()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            AlertDialog.Builder(this).setMessage("请用浏览器打开：$url")
                .setPositiveButton("知道了", null).show()
        }
    }

    private fun showTerms() {
        AlertDialog.Builder(this)
            .setTitle("启用免费真实天气")
            .setMessage(
                "数据由挪威气象局 MET Norway 免费提供，无需 API Key。" +
                    "\n\n使用前请阅读并接受服务条款。启用后，仅向天气服务发送您所选城市的中心坐标，" +
                    "不读取设备位置；请求会显示设备公网 IP。天气数据遵循 CC BY 4.0 署名要求。" +
                    "\n\n本原型不保证天气服务始终在线。"
            )
            .setNeutralButton("查看服务条款") { _, _ -> openUrl(WeatherData.TERMS_URL) }
            .setNegativeButton("暂不启用", null)
            .setPositiveButton("我已阅读并同意启用") { _, _ ->
                WeatherData.acceptTerms(this)
                render()
            }
            .show()
    }

    private fun render() {
        request?.cancel()
        request = null
        latest = null
        lastError = null
        val place = WeatherCity.place(this)
        currentPlace = place
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
        }, LinearLayout.LayoutParams(-1, dp(45)))
        root.addView(label("七日天气预报", 30f, ink, true),
            LinearLayout.LayoutParams(-1, dp(61)))

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(5), 0, dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val cityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(11), dp(12), dp(11))
            background = rounded(Color.WHITE, 14, border)
        }
        cityRow.addView(label(
            if (place == null) "请选择城市" else place.name + " · 手动选择",
            19f, ink, true
        ), LinearLayout.LayoutParams(0, dp(53), 1f))
        cityRow.addView(action(if (place == null) "选城市" else "换城市") {
            WeatherCity.showPicker(this) { render() }
        }, LinearLayout.LayoutParams(dp(102), dp(49)))
        content.addView(cityRow)

        status = label("读取预报中…", 14f, muted).apply {
            setPadding(dp(10), dp(10), dp(8), dp(9))
            minHeight = dp(56)
        }
        content.addView(status, LinearLayout.LayoutParams(-1, -2))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)

        val credit = label(
            "数据：MET Norway · CC BY 4.0\n每日高低温由预报采样推算，并非实测气温。",
            13f, muted
        ).apply {
            setPadding(dp(10), dp(15), dp(10), dp(15))
            isClickable = true
            isFocusable = true
            contentDescription = "查看 MET Norway 气象数据许可"
            setOnClickListener { openUrl(WeatherData.LICENSE_URL) }
        }
        content.addView(credit, LinearLayout.LayoutParams(-1, -2))

        when {
            place == null -> {
                status.text = if (WeatherCity.current(this).isBlank()) {
                    "选择城市后获取真实天气，不需要定位授权。"
                } else "尚不支持当前城市，请从列表中重新选择。"
                showMessage("您还没有选择受支持的城市。")
            }
            !WeatherData.consented(this) -> {
                status.text = "天气服务尚未启用"
                showMessage("使用前需由您本人查看并接受免费服务条款。")
                results.addView(action("启用免费天气") { showTerms() },
                    LinearLayout.LayoutParams(-1, dp(57)))
            }
            else -> {
                val cached = WeatherData.cached(this, place)
                if (cached != null) showForecast(cached, null)
                status.text = if (cached?.stale == false) "使用有效缓存 · 无需再次请求"
                    else if (cached != null) "历史缓存 · 正在更新"
                    else "正在获取天气…"
                fetch(place)
            }
        }
    }

    private fun fetch(place: WeatherCity.Place) {
        request = WeatherData.load(this, place) { forecast, error ->
            if (isFinishing || isDestroyed || currentPlace != place) return@load
            lastError = error
            if (forecast == null) {
                status.text = error ?: "天气数据暂不可用"
                showMessage("没有可用预报。请检查网络，稍后重新打开天气页。")
            } else {
                showForecast(forecast, error)
                status.text = when {
                    error != null -> error + if (forecast.stale) " · 显示过期缓存" else " · 显示缓存"
                    forecast.stale -> "显示缓存数据 · 可能已过期"
                    else -> "预报已获取 · 下次更新遵循服务商缓存时间"
                }
            }
        }
    }

    private fun showMessage(message: String) {
        results.removeAllViews()
        results.addView(label(message, 17f, muted).apply {
            setPadding(dp(17), dp(19), dp(17), dp(19))
            background = rounded(Color.WHITE, 13, border)
        })
    }

    private fun showForecast(forecast: WeatherData.Forecast, error: String?) {
        latest = forecast
        results.removeAllViews()
        val state = if (forecast.stale) "过期预报 · 请谨慎参考" else "未来七日 · 预报数据"
        results.addView(label(state, 20f, ink, true),
            LinearLayout.LayoutParams(-1, dp(50)))
        val localTime = runCatching {
            Instant.parse(forecast.updatedAt)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("M月d日 HH:mm", Locale.CHINA))
        }.getOrDefault("未知")
        results.addView(label("MET 更新：$localTime（北京时间） · 近期预报约 ${forecast.nearTemperature}°", 14f, muted),
            LinearLayout.LayoutParams(-1, dp(37)))
        if (error != null) {
            results.addView(label("提示：$error", 14f, accent),
                LinearLayout.LayoutParams(-1, dp(43)))
        }
        forecast.days.forEachIndexed { index, day ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(7), dp(12), dp(7))
                background = rounded(Color.WHITE, 13, border)
            }
            val date = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            date.addView(label(day.title, 17f, ink, true))
            date.addView(label(if (index == 0) "今日剩余时段" else "预报采样", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            row.addView(date, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(label(day.symbol, 15f, ink),
                LinearLayout.LayoutParams(dp(90), -2))
            row.addView(label("${day.high}° / ${day.low}°", 17f, ink, true),
                LinearLayout.LayoutParams(dp(105), -2))
            results.addView(row, LinearLayout.LayoutParams(-1, dp(76)).apply {
                bottomMargin = dp(8)
            })
        }
    }
}
