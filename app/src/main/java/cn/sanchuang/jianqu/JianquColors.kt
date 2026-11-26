package cn.sanchuang.jianqu

import android.content.Context

/** Semantic colors backed by Android resources; safe to construct before Activity.attach. */
internal class JianquColors(private val context: Context) {
    val background: Int get() = context.getColor(R.color.jq_background)
    val surface: Int get() = context.getColor(R.color.jq_surface)
    val surfaceSecondary: Int get() = context.getColor(R.color.jq_surface_secondary)
    val ink: Int get() = context.getColor(R.color.jq_ink)
    val muted: Int get() = context.getColor(R.color.jq_muted)
    val border: Int get() = context.getColor(R.color.jq_border)
    val accent: Int get() = context.getColor(R.color.jq_accent)
    val accentStrong: Int get() = context.getColor(R.color.jq_accent_strong)
    val onAccent: Int get() = context.getColor(R.color.jq_on_accent)
    val accentSecondary: Int get() = context.getColor(R.color.jq_accent_secondary)
    val success: Int get() = context.getColor(R.color.jq_success)
    val accentSoft: Int get() = context.getColor(R.color.jq_accent_soft)
    val accentSoftBorder: Int get() = context.getColor(R.color.jq_accent_soft_border)
    val orbCore: Int get() = context.getColor(R.color.jq_orb_core)
    val orbEdge: Int get() = context.getColor(R.color.jq_orb_edge)
    val ripple: Int get() = context.getColor(R.color.jq_ripple)
    val danger: Int get() = context.getColor(R.color.jq_danger)
    val onDanger: Int get() = context.getColor(R.color.jq_on_danger)
    val dangerSurface: Int get() = context.getColor(R.color.jq_danger_surface)
    val dangerBorder: Int get() = context.getColor(R.color.jq_danger_border)
    val familyCallIcon: Int get() = context.getColor(R.color.jq_family_call_icon)
    val albumIcon: Int get() = context.getColor(R.color.jq_album_icon)
    val videoIcon: Int get() = context.getColor(R.color.jq_video_icon)
    val storeIcon: Int get() = context.getColor(R.color.jq_store_icon)
    val musicIcon: Int get() = context.getColor(R.color.jq_music_icon)
    val healthIcon: Int get() = context.getColor(R.color.jq_health_icon)
    val newsIcon: Int get() = context.getColor(R.color.jq_news_icon)
    val servicesIcon: Int get() = context.getColor(R.color.jq_services_icon)
    val settingsIcon: Int get() = context.getColor(R.color.jq_settings_icon)
    val allAppsIcon: Int get() = context.getColor(R.color.jq_all_apps_icon)
    val successTrack: Int get() = context.getColor(R.color.jq_success_track)
    val companionEnd: Int get() = context.getColor(R.color.jq_companion_end)
    val companionStart: Int get() = accentSoft
    val messageUser: Int get() = accentSoft
    val messageAssistant: Int get() = surface
}
