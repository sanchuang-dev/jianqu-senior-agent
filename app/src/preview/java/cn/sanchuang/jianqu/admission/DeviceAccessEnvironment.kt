package cn.sanchuang.jianqu.admission

import android.content.Context

/** Public preview stays usable; no production authorization or writable admission state. */
internal object DeviceAccessEnvironment {
    fun open(context: Context): DeviceAccessSource = object : DeviceAccessSource {
        override fun current() = DeviceAccess.PREVIEW_UNENFORCED
    }
}
