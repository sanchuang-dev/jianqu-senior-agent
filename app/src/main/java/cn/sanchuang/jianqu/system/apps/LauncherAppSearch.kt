package cn.sanchuang.jianqu.system.apps

/** Pure search rule shared by the Launcher all-apps page. */
internal object LauncherAppSearch {
    fun matches(query: String, label: String, packageName: String): Boolean {
        val normalized = query.trim()
        return normalized.isEmpty() ||
            label.contains(normalized, ignoreCase = true) ||
            packageName.contains(normalized, ignoreCase = true)
    }
}
