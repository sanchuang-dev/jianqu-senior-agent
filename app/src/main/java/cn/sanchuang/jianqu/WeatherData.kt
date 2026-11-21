package cn.sanchuang.jianqu

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** MET Norway Locationforecast/2.0 compact: no API key, no device GPS, HTTPS only. */
internal object WeatherData {
    const val PROVIDER_URL = "https://api.met.no/weatherapi/locationforecast/2.0/compact"
    const val TERMS_URL = "https://api.met.no/doc/TermsOfService"
    const val LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"
    private const val UA = "JianquLauncher/0.1.0-dev (https://github.com/sanchuang-dev/jianqu-senior-agent)"
    private const val CACHE_PREFS = "met_weather_cache"
    private const val THROTTLE_UNTIL = "throttle_until"
    private const val THROTTLE_MS = 10L * 60L * 1000L
    private val executor = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val chinaZone = ZoneId.of("Asia/Shanghai")
    private val dayFormat = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

    data class Day(val date: LocalDate, val high: Int, val low: Int, val symbol: String) {
        val title: String get() = date.format(dayFormat)
    }
    data class Forecast(
        val city: WeatherCity.Place,
        val days: List<Day>,
        val nearTemperature: Int,
        val updatedAt: String,
        val expiresAt: Long,
        val stale: Boolean
    )
    private data class Cache(val city: String, val body: String, val expires: Long, val lastModified: String)

    class Request {
        @Volatile var cancelled = false
            private set
        @Volatile var connection: HttpURLConnection? = null
        fun cancel() {
            cancelled = true
            connection?.disconnect()
        }
    }

    fun consented(context: Context): Boolean =
        context.getSharedPreferences("launcher_ui", Context.MODE_PRIVATE)
            .getBoolean("met_terms_accepted", false)

    fun acceptTerms(context: Context) {
        context.getSharedPreferences("launcher_ui", Context.MODE_PRIVATE)
            .edit().putBoolean("met_terms_accepted", true).apply()
    }

    /** Returns null for missing or corrupt cache. Never presents samples as real data. */
    fun cached(context: Context, city: WeatherCity.Place): Forecast? {
        if (!consented(context)) return null
        val cache = readCache(context, city) ?: return null
        return runCatching { parse(cache.body, city, cache.expires) }.getOrNull()
    }

    private fun readCache(context: Context, city: WeatherCity.Place): Cache? {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getString("city", "") != city.name) return null
        val body = prefs.getString("body", null) ?: return null
        return Cache(city.name, body, prefs.getLong("expires", 0), prefs.getString("modified", "").orEmpty())
    }

    private fun saveCache(context: Context, cache: Cache) {
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("city", cache.city)
            .putString("body", cache.body)
            .putLong("expires", cache.expires)
            .putString("modified", cache.lastModified)
            .apply()
    }

    /** One request at a time, only initiated by an open forecast screen after user consent. */
    fun load(context: Context, city: WeatherCity.Place, callback: (Forecast?, String?) -> Unit): Request {
        val request = Request()
        val app = context.applicationContext
        executor.execute {
            val cache = readCache(app, city)
            try {
                if (!consented(app)) throw IOException("天气服务尚未启用")
                if (request.cancelled) return@execute
                val now = System.currentTimeMillis()
                val available = cache?.let { runCatching { parse(it.body, city, it.expires) }.getOrNull() }
                if (cache != null && cache.expires > now) {
                    // Never repeat the same request before MET's Expires, even if the local body is corrupt.
                    post(request, callback, available, if (available == null) "本机缓存异常，等待服务商缓存到期后重新获取" else null)
                    return@execute
                }
                if (request.cancelled) return@execute
                // A server throttle applies to this entire app, not just the currently selected city.
                val retryAfter = app.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                    .getLong(THROTTLE_UNTIL, 0L)
                if (retryAfter > now) {
                    post(request, callback, available, "天气服务正在限流，请稍后再试")
                    return@execute
                }
                // An invalid cache must not send If-Modified-Since: HTTP 304 cannot repair its body.
                val response = fetch(city, if (available != null) cache else null, request)
                if (request.cancelled) return@execute
                val fresh = parse(response.body, city, response.expires)
                saveCache(app, response)
                post(request, callback, fresh, null)
            } catch (error: Exception) {
                if (request.cancelled) return@execute
                if (error.message == "HTTP 429") {
                    // Persist a conservative backoff across screen visits and process restarts.
                    app.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                        .putLong(THROTTLE_UNTIL, System.currentTimeMillis() + THROTTLE_MS).apply()
                }
                Log.w("JianquWeather", "Unable to refresh MET weather", error)
                val prior = cache?.let { runCatching { parse(it.body, city, it.expires) }.getOrNull() }
                val message = when {
                    error is java.net.UnknownHostException -> "网络不可用，暂时无法更新"
                    error is java.net.SocketTimeoutException -> "天气服务连接超时"
                    error.message?.contains("HTTP 429") == true -> "天气服务请求过于频繁，请稍后再试"
                    error.message?.contains("HTTP 403") == true -> "天气服务拒绝请求，请检查配置"
                    else -> "天气暂时获取失败"
                }
                post(request, callback, prior, message)
            }
        }
        return request
    }

    private fun post(
        request: Request,
        callback: (Forecast?, String?) -> Unit,
        forecast: Forecast?,
        error: String?
    ) {
        ui.post { if (!request.cancelled) callback(forecast, error) }
    }

    private fun fetch(city: WeatherCity.Place, old: Cache?, request: Request): Cache {
        val lat = String.format(Locale.US, "%.4f", city.lat)
        val lon = String.format(Locale.US, "%.4f", city.lon)
        val connection = (URL("$PROVIDER_URL?lat=$lat&lon=$lon").openConnection() as HttpURLConnection)
        request.connection = connection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 12000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", UA)
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
            if (old != null && old.lastModified.isNotBlank()) {
                connection.setRequestProperty("If-Modified-Since", old.lastModified)
            }
            val code = connection.responseCode
            if (request.cancelled) throw IOException("Cancelled")
            // Never send a repeat request before the server-specified Expires time.
            val expiry = httpDate(connection.getHeaderField("Expires")).takeIf {
                it > System.currentTimeMillis()
            } ?: System.currentTimeMillis() + 6L * 60L * 60L * 1000L
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED && old != null) {
                return old.copy(expires = expiry)
            }
            if (code != HttpURLConnection.HTTP_OK && code != 203) throw IOException("HTTP $code")
            val raw = connection.inputStream
            val stream = when (connection.contentEncoding?.lowercase(Locale.ROOT)) {
                "gzip" -> GZIPInputStream(raw)
                "deflate" -> InflaterInputStream(raw)
                else -> raw
            }
            val bytes = ByteArrayOutputStream()
            stream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    if (request.cancelled) throw IOException("Cancelled")
                    val n = input.read(buffer)
                    if (n == -1) break
                    bytes.write(buffer, 0, n)
                    if (bytes.size() > 1_500_000) throw IOException("Weather response too large")
                }
            }
            val body = bytes.toString(Charsets.UTF_8.name())
            if (body.isBlank()) throw IOException("Empty response")
            return Cache(city.name, body, expiry, connection.getHeaderField("Last-Modified").orEmpty())
        } finally {
            connection.disconnect()
            request.connection = null
        }
    }

    private fun httpDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0
        return runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time ?: 0
        }.getOrDefault(0)
    }

    /** The daily extrema below are derived from MET's sampled forecast temperatures, not observed highs/lows. */
    fun parse(body: String, city: WeatherCity.Place, expires: Long, now: Instant = Instant.now()): Forecast {
        val data = JSONObject(body).getJSONObject("properties")
        val points = data.getJSONArray("timeseries")
        val today = now.atZone(chinaZone).toLocalDate()
        data class Sample(val instant: Instant, val temp: Double, val icon: String)
        val byDay = linkedMapOf<LocalDate, MutableList<Sample>>()
        for (index in 0 until points.length()) {
            val point = points.getJSONObject(index)
            val at = runCatching { Instant.parse(point.getString("time")) }.getOrNull() ?: continue
            if (at < now.minusSeconds(1800)) continue
            val day = at.atZone(chinaZone).toLocalDate()
            if (day < today || day >= today.plusDays(7)) continue
            val details = point.getJSONObject("data")
            val instant = details.optJSONObject("instant")?.optJSONObject("details") ?: continue
            val temp = instant.optDouble("air_temperature", Double.NaN)
            if (!temp.isFinite()) continue
            val icon = listOf("next_6_hours", "next_1_hours", "next_12_hours")
                .firstNotNullOfOrNull { name ->
                    details.optJSONObject(name)?.optJSONObject("summary")
                        ?.optString("symbol_code")?.takeIf { it.isNotEmpty() }
                }.orEmpty()
            byDay.getOrPut(day) { mutableListOf() }.add(Sample(at, temp, icon))
        }
        val days = byDay.map { (date, samples) ->
            val high = kotlin.math.round(samples.maxOf { it.temp }).toInt()
            val low = kotlin.math.round(samples.minOf { it.temp }).toInt()
            val midday = samples.minByOrNull {
                kotlin.math.abs(it.instant.atZone(chinaZone).hour - 12)
            }
            Day(date, high, low, describe(midday?.icon.orEmpty()))
        }
        if (days.isEmpty()) throw IOException("Weather contains no usable future forecast")
        val current = kotlin.math.round(byDay.values.first().first().temp).toInt()
        return Forecast(city, days, current,
            data.optJSONObject("meta")?.optString("updated_at").orEmpty(),
            expires, expires <= System.currentTimeMillis())
    }

    fun describe(code: String): String {
        val value = code.lowercase(Locale.ROOT)
        return when {
            value.contains("thunder") -> "雷阵雨"
            value.contains("snow") -> "雪"
            value.contains("sleet") -> "雨夹雪"
            value.contains("fog") -> "雾"
            value.contains("rain") -> "雨"
            value.contains("partlycloudy") -> "多云间晴"
            value.contains("cloudy") -> "多云"
            value.contains("fair") -> "晴间多云"
            value.contains("clearsky") -> "晴"
            else -> "预报中"
        }
    }
}
