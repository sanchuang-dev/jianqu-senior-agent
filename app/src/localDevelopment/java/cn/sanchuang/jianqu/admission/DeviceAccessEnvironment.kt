package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquTypography

internal object DeviceAccessEnvironment {
    fun open(context: Context): DeviceAccessSource = LocalDeviceAccess(context.applicationContext)
}

/** Only compiled into the separately identified localDevelopment APK. Never reads legacy rental prefs. */
private class LocalDeviceAccess(context: Context) : DeviceAccessSource {
    private val preferences = context.getSharedPreferences("local_admission_scenarios", Context.MODE_PRIVATE)
    private val scenarios = listOf(
        DeviceAccess.NOT_RENTED to "尚未租赁",
        DeviceAccess.ACTIVE to "租赁有效",
        DeviceAccess.EXPIRED to "租赁已到期",
        DeviceAccess.OFFLINE_BLOCKED to "离线 · 无有效凭证",
        DeviceAccess.OFFLINE_VALID to "离线 · 有效凭证（模拟）",
        DeviceAccess.CHECKING to "检查中（模拟）"
    )
    override fun current(): DeviceAccess = scenarios.firstOrNull {
        it.first.name == preferences.getString("scenario", null)
    }?.first ?: DeviceAccess.NOT_RENTED

    private fun select(state: DeviceAccess) {
        preferences.edit().putString("scenario", state.name).apply()
    }

    override fun developerControl(activity: Activity, changed: () -> Unit): View {
        val ui = JianquColors(activity)
        return JianquTypography.text(activity, "开发调试", 14f, ui.accentStrong, true).apply {
            gravity = Gravity.CENTER
            minHeight = (48 * resources.displayMetrics.density + .5f).toInt()
            background = JianquDrawables.touch(activity, ui.surface, stroke = ui.border)
            isClickable = true
            isFocusable = true
            contentDescription = "设备租赁开发调试"
            setOnClickListener {
                AlertDialog.Builder(activity)
                    .setTitle("设备租赁 · 开发场景")
                    .setSingleChoiceItems(scenarios.map { it.second }.toTypedArray(),
                        scenarios.indexOfFirst { it.first == current() }) { dialog, index ->
                        select(scenarios[index].first)
                        dialog.dismiss()
                        changed()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
}
