package cn.sanchuang.jianqu.home

import cn.sanchuang.jianqu.R

/** Declaration order is the HOME display order; every entry has one icon and one route. */
internal enum class HomeAction(val title: String, val iconRes: Int, val header: Boolean = false) {
    FAMILY_CALL("家人通话", R.drawable.jq_home_contacts),
    CONTENT_ALBUM("家庭相册", R.drawable.jq_home_album),
    CONTENT_VIDEO("影视节目", R.drawable.jq_home_video),
    STOREFRONT("本地生活", R.drawable.jq_home_shopping),
    CONTENT_MUSIC("音乐戏曲", R.drawable.jq_home_music_opera),
    CONTENT_HEALTH("健康中心", R.drawable.jq_home_health),
    CONTENT_NEWS("建政栏目", R.drawable.jq_home_governance),
    SERVICES("我的服务", R.drawable.jq_home_my_services),
    SETTINGS("设置", R.drawable.jq_home_settings, header = true),
    ALL_APPS("全部应用", R.drawable.jq_home_all_apps, header = true)
}
