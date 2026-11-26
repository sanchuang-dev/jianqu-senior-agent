package cn.sanchuang.jianqu.admission

import android.app.Activity
import android.view.View

internal enum class DeviceAccess {
    CHECKING, NOT_RENTED, ACTIVE, EXPIRED, OFFLINE_BLOCKED, OFFLINE_VALID,
    // Existing public preview is not a commercial rental build. This is not a grant.
    PREVIEW_UNENFORCED;

    val allowsHome: Boolean
        get() = this == ACTIVE || this == OFFLINE_VALID || this == PREVIEW_UNENFORCED
}

internal interface DeviceAccessSource {
    fun current(): DeviceAccess
    fun refresh(): DeviceAccess = current()
    fun developerControl(activity: Activity, changed: () -> Unit): View? = null
}
