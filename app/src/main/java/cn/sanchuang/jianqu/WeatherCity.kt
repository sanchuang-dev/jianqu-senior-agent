package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.Context

/**
 * Named city centres, not device locations. A selected city is an explicit preference.
 * The exact same name is shared by launcher HOME, settings and forecast details.
 */
internal object WeatherCity {
    data class Place(val name: String, val lat: Double, val lon: Double, val alias: String = "")

    private const val PREFERENCES = "launcher_ui"
    private const val KEY_CITY = "weather_city"
    val supported = listOf(
        Place("广州", 23.1291, 113.2644, "Guangzhou"),
        Place("深圳", 22.5431, 114.0579, "Shenzhen"),
        Place("韶关", 24.8104, 113.5972, "Shaoguan"),
        Place("佛山", 23.0215, 113.1214, "Foshan"),
        Place("东莞", 23.0207, 113.7518, "Dongguan"),
        Place("珠海", 22.2710, 113.5767, "Zhuhai"),
        Place("北京", 39.9042, 116.4074, "Beijing"),
        Place("上海", 31.2304, 121.4737, "Shanghai"),
        Place("杭州", 30.2741, 120.1551, "Hangzhou"),
        Place("南京", 32.0603, 118.7969, "Nanjing"),
        Place("苏州", 31.2989, 120.5853, "Suzhou"),
        Place("武汉", 30.5928, 114.3055, "Wuhan"),
        Place("成都", 30.5728, 104.0668, "Chengdu"),
        Place("重庆", 29.5630, 106.5516, "Chongqing"),
        Place("长沙", 28.2282, 112.9388, "Changsha"),
        Place("西安", 34.3416, 108.9398, "Xi'an"),
        Place("厦门", 24.4798, 118.0894, "Xiamen"),
        Place("南宁", 22.8170, 108.3669, "Nanning"),
        Place("昆明", 25.0389, 102.7183, "Kunming"),
        Place("天津", 39.3434, 117.3616, "Tianjin"),
        Place("青岛", 36.0671, 120.3826, "Qingdao"),
        Place("哈尔滨", 45.8038, 126.5350, "Harbin")
    )

    fun current(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CITY, "").orEmpty().trim()

    fun place(context: Context): Place? = find(current(context))

    fun find(value: String): Place? = supported.firstOrNull {
        it.name.equals(value.trim(), ignoreCase = true) ||
            it.alias.equals(value.trim(), ignoreCase = true)
    }

    fun showPicker(activity: Activity, onSaved: () -> Unit) {
        val names = supported.map { it.name }.toMutableList().apply { add("清除城市") }
        AlertDialog.Builder(activity)
            .setTitle("选择城市 · 不读取设备定位")
            .setItems(names.toTypedArray()) { _, index ->
                val place = supported.getOrNull(index)
                val prefs = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                prefs.edit().apply {
                    if (place == null) remove(KEY_CITY) else putString(KEY_CITY, place.name)
                }.apply()
                onSaved()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
