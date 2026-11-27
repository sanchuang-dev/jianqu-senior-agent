package cn.sanchuang.jianqu.settings

import android.app.Activity
import android.content.res.Configuration
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDimensions
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquIcons
import cn.sanchuang.jianqu.JianquPageHeader
import cn.sanchuang.jianqu.JianquSpacing
import cn.sanchuang.jianqu.JianquTypography
import cn.sanchuang.jianqu.R

/** Settings rendering only; LauncherActivity retains admission and external navigation. */
internal class SettingsScreen(
    private val activity: Activity,
    initialSection: Section,
    private val city: String,
    private val version: String,
    private val onSectionChanged: (Section) -> Unit,
    private val onBack: () -> Unit,
    private val onSystemSettings: (String) -> Unit,
    private val onCity: () -> Unit,
    private val onHelp: () -> Unit,
    private val onAbout: () -> Unit
) {
    enum class Section(val title: String, val icon: Int) {
        ENVIRONMENT("设备与环境", R.drawable.ic_jq_network),
        MANAGEMENT("设备管理", R.drawable.ic_jq_settings_device),
        ABOUT("关于与帮助", R.drawable.ic_jq_settings_info)
    }

    private data class Item(
        val title: String, val icon: Int, val value: String? = null, val open: () -> Unit
    )

    private val ui = JianquColors(activity)
    private var selected = initialSection
    private val wide = activity.resources.configuration.let {
        it.orientation == Configuration.ORIENTATION_LANDSCAPE && it.screenWidthDp >= 660
    }
    private val groups = linkedMapOf(
        Section.ENVIRONMENT to listOf(
            Item("网络", R.drawable.ic_jq_network) { onSystemSettings(Settings.ACTION_WIFI_SETTINGS) },
            Item("显示与亮度", R.drawable.ic_jq_settings_display) { onSystemSettings(Settings.ACTION_DISPLAY_SETTINGS) },
            Item("声音", R.drawable.ic_jq_settings_sound) { onSystemSettings(Settings.ACTION_SOUND_SETTINGS) },
            Item("天气城市", R.drawable.ic_jq_settings_weather, city.ifBlank { "未选择" }, onCity)
        ),
        Section.MANAGEMENT to listOf(
            Item("默认主屏幕", R.drawable.ic_jq_settings_device, "系统设置") { onSystemSettings(Settings.ACTION_HOME_SETTINGS) }
        ),
        Section.ABOUT to listOf(
            Item("关于闺蜜机", R.drawable.ic_jq_settings_info, version, onAbout),
            Item("使用帮助", R.drawable.ic_jq_support, open = onHelp)
        )
    )

    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + .5f).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun text(value: String, size: Float, color: Int = ui.ink, bold: Boolean = false) =
        JianquTypography.text(activity, value, size, color, bold)
    private fun icon(resource: Int, color: Int = ui.muted) = JianquIcons.image(activity, resource, color).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun LinearLayout.space(height: Int) = addView(View(context), LinearLayout.LayoutParams(1, dp(height)))
    private fun LinearLayout.addIcon(resource: Int, color: Int = ui.muted) =
        addView(icon(resource, color), LinearLayout.LayoutParams(dp(JianquDimensions.utilityIconDp), dp(JianquDimensions.utilityIconDp)))

    fun render(): View {
        val root = column().apply {
            setBackgroundColor(ui.background)
            val side = dp(if (wide) JianquSpacing.space24Dp else JianquSpacing.space16Dp)
            setPadding(side, dp(JianquSpacing.space12Dp), side, dp(JianquSpacing.space16Dp))
        }
        val screen = column().apply {
            setBackgroundColor(ui.background)
            addView(JianquPageHeader.create(activity, "设置", onBack = onBack))
            addView(root, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        root.space(JianquSpacing.space20Dp)
        if (wide) {
            val split = row().apply { gravity = Gravity.TOP }
            val rail = column()
            val detail = column()
            val railScroll = scroll(rail)
            val detailScroll = scroll(detail)
            split.addView(railScroll, LinearLayout.LayoutParams(dp(216), -1))
            split.addView(detailScroll, LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(JianquSpacing.space24Dp)
            })
            fun update() {
                rail.removeAllViews()
                detail.removeAllViews()
                Section.entries.forEach { section ->
                    val active = section == selected
                    val nav = row().apply {
                        minimumHeight = dp(JianquDimensions.largeActionHeightDp)
                        setPadding(dp(JianquSpacing.space16Dp), dp(JianquSpacing.space12Dp),
                            dp(JianquSpacing.space16Dp), dp(JianquSpacing.space12Dp))
                        background = JianquDrawables.touch(activity,
                            if (active) ui.accentSoft else ui.surface, JianquDimensions.tileRadiusDp,
                            if (active) ui.accentSoftBorder else null)
                        isSelected = active
                        isFocusable = true
                        contentDescription = section.title
                        addIcon(section.icon, if (active) ui.accentStrong else ui.muted)
                        addView(text(section.title, JianquTypography.bodyLargeSp,
                            if (active) ui.accentStrong else ui.ink, active),
                            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(JianquSpacing.space12Dp) })
                        setOnClickListener {
                            selected = section
                            onSectionChanged(section)
                            update()
                            detailScroll.scrollTo(0, 0)
                        }
                    }
                    rail.addView(nav, LinearLayout.LayoutParams(-1, -2).apply {
                        bottomMargin = dp(JianquSpacing.space8Dp)
                    })
                }
                addGroup(detail, selected)
            }
            update()
            root.addView(split, LinearLayout.LayoutParams(-1, 0, 1f))
        } else {
            val content = column()
            Section.entries.forEachIndexed { index, section ->
                if (index > 0) content.space(JianquSpacing.space24Dp)
                addGroup(content, section)
            }
            root.addView(scroll(content), LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return screen
    }

    private fun scroll(content: LinearLayout) = ScrollView(activity).apply {
        isFillViewport = false
        addView(content, FrameLayout.LayoutParams(-1, -2))
    }

    private fun addGroup(parent: LinearLayout, section: Section) {
        parent.addView(JianquTypography.display(activity, section.title, JianquTypography.sectionSp, ui.ink),
            LinearLayout.LayoutParams(-1, -2).apply {
                leftMargin = dp(JianquSpacing.space4Dp)
                bottomMargin = dp(JianquSpacing.space12Dp)
            })
        val card = column().apply {
            background = JianquDrawables.shape(activity, ui.surface, JianquDimensions.cardRadiusDp, ui.border)
            clipToOutline = true
        }
        groups.getValue(section).forEachIndexed { index, item ->
            if (index > 0) card.addView(View(activity).apply { setBackgroundColor(ui.border) },
                LinearLayout.LayoutParams(-1, dp(1)).apply {
                    leftMargin = dp(JianquSpacing.space16Dp)
                    rightMargin = dp(JianquSpacing.space16Dp)
                })
            card.addView(row().apply {
                minimumHeight = dp(JianquDimensions.largeActionHeightDp)
                setPadding(dp(JianquSpacing.space16Dp), dp(JianquSpacing.space16Dp),
                    dp(JianquSpacing.space16Dp), dp(JianquSpacing.space16Dp))
                background = JianquDrawables.touch(activity, ui.surface, 0)
                contentDescription = listOfNotNull(item.title, item.value).joinToString("，")
                isFocusable = true
                addIcon(item.icon)
                addView(text(item.title, JianquTypography.bodyLargeSp),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(JianquSpacing.space12Dp) })
                if (item.value != null) addView(text(item.value, JianquTypography.supportingSp, ui.muted),
                    LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(JianquSpacing.space8Dp) })
                addView(icon(R.drawable.ic_jq_glass_back).apply { rotation = 180f },
                    LinearLayout.LayoutParams(dp(20), dp(20)).apply { leftMargin = dp(JianquSpacing.space8Dp) })
                setOnClickListener { item.open() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2))
    }
}
