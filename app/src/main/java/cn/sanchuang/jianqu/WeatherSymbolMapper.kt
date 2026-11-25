package cn.sanchuang.jianqu

import java.util.Locale

/**
 * Pure mapping from MET Norway symbol codes to the short Chinese labels used by the Launcher.
 * Kept Android-free so provider vocabulary changes can be regression-tested cheaply.
 */
internal object WeatherSymbolMapper {
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
