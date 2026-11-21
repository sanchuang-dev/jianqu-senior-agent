package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout

/**
 * The selected city is a local, manually entered display preference.
 * It must not be interpreted as the device's geolocation or a verified weather location.
 */
internal object WeatherCity {
    private const val PREFERENCES = "launcher_ui"
    private const val KEY_CITY = "weather_city"

    fun current(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_CITY, "").orEmpty().trim()

    fun showPicker(activity: Activity, onSaved: () -> Unit) {
        val input = EditText(activity).apply {
            setSingleLine(true)
            textSize = 18f
            hint = "例如：广州"
            filters = arrayOf(InputFilter.LengthFilter(20))
            inputType = InputType.TYPE_CLASS_TEXT
            setText(current(activity))
            setSelection(text.length)
        }
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (density * value + 0.5f).toInt()
        val wrapper = FrameLayout(activity).apply {
            setPadding(dp(20), dp(8), dp(20), dp(5))
            addView(input, FrameLayout.LayoutParams(-1, dp(58), Gravity.CENTER))
        }
        AlertDialog.Builder(activity)
            .setTitle("手动选择天气城市")
            .setMessage("仅保存本机城市名称。天气 API 尚未接入，不会获取设备位置或实时天气。")
            .setView(wrapper)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val city = input.text.toString().trim()
                val preferences = activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                preferences.edit().apply {
                    if (city.isEmpty()) remove(KEY_CITY) else putString(KEY_CITY, city)
                }.apply()
                onSaved()
            }
            .show()
    }
}
