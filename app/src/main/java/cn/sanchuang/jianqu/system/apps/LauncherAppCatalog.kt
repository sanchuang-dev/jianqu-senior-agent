package cn.sanchuang.jianqu.system.apps

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Process
import android.os.UserHandle
import java.text.Collator
import java.util.Locale

/**
 * Android-system boundary for the real "all apps" catalog.
 *
 * UI code owns rendering and async orchestration; this adapter owns LauncherApps details only.
 * Exceptions are intentionally not swallowed so the caller can show a real failure state.
 */
internal class LauncherAppCatalog private constructor(
    private val launcherApps: LauncherApps,
    private val callbackHandler: Handler,
    private val onCatalogChanged: () -> Unit
) {
    private val currentUser = Process.myUserHandle()

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = onCatalogChanged()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = onCatalogChanged()
        override fun onPackageChanged(packageName: String, user: UserHandle) = onCatalogChanged()
        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = onCatalogChanged()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean
        ) = onCatalogChanged()
    }

    fun start() {
        launcherApps.registerCallback(packageCallback, callbackHandler)
    }

    fun stop() {
        launcherApps.unregisterCallback(packageCallback)
    }

    fun load(): List<LauncherActivityInfo> =
        launcherApps.getActivityList(null, currentUser)
            .sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.label.toString() })

    fun launch(entry: LauncherActivityInfo) {
        launcherApps.startMainActivity(entry.componentName, entry.user, null, null)
    }

    companion object {
        fun open(
            context: Context,
            callbackHandler: Handler,
            onCatalogChanged: () -> Unit
        ): LauncherAppCatalog =
            LauncherAppCatalog(
                launcherApps = context.applicationContext.getSystemService(LauncherApps::class.java),
                callbackHandler = callbackHandler,
                onCatalogChanged = onCatalogChanged
            )
    }
}
