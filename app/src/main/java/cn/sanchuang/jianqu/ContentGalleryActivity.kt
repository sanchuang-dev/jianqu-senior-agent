package cn.sanchuang.jianqu

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * JQ-009: one local UI contract for four content sections and a separate
 * consent-first album flow. No content fetch, media decode, gallery access,
 * publishing rights, streaming connection, or personal persistence.
 */
class ContentGalleryActivity : Activity() {
    companion object {
        const val EXTRA_SECTION = "section"
        const val ALBUM = "album"
        const val VIDEO = "video"
        const val MUSIC = "music"
        const val HEALTH = "health"
        const val NEWS = "news"
    }

    private enum class Screen {
        LIST, DETAIL, PREVIEW, EMPTY, OFFLINE,
        ALBUM_GATE, ALBUM_DENIED, ALBUM_GALLERY, ALBUM_EMPTY, ALBUM_VIEWER
    }
    private enum class PreviewState { IDLE, PLAYING, PAUSED }
    private data class DemoEntry(
        val title: String, val category: String, val badge: String,
        val summary: String, val rights: String = "内容源与使用授权均未接入"
    )

    private val section by lazy {
        when (intent.getStringExtra(EXTRA_SECTION)) {
            ALBUM, VIDEO, MUSIC, HEALTH, NEWS -> intent.getStringExtra(EXTRA_SECTION)!!
            else -> VIDEO
        }
    }
    private val library = mapOf(
        VIDEO to listOf(
            DemoEntry("演示片单 A", "录播", "续播待接入", "录播详情及续播入口示范，没有视频资源或历史进度。"),
            DemoEntry("演示节目 B", "直播", "直播源未接通", "演示直播节目的栏目标签；没有实际直播流。")
        ),
        MUSIC to listOf(
            DemoEntry("演示音频 A", "戏曲", "音频未接入", "戏曲曲目资料页及播放控制的结构示意。"),
            DemoEntry("演示音频 B", "评书", "音频未接入", "评书章节与返回操作示意，不包含任何录音。")
        ),
        HEALTH to listOf(
            DemoEntry("演示课程 A", "科普", "内容待审核", "健康科普课程的字段示例，不构成医疗建议。"),
            DemoEntry("演示课程 B", "广告待审", "不可作为科普", "展示商业推广与健康科普必须分开的界面属性。")
        ),
        NEWS to listOf(
            DemoEntry("演示条目 A", "事实示意", "来源未核实", "事实与来源字段的展示样例；此条不是新闻事实。"),
            DemoEntry("演示条目 B", "评论示意", "观点演示", "评论文章与事实报道的区分样例；没有真实刊发内容。")
        )
    )
    private val albumLabels = listOf("示意相框 01", "示意相框 02", "示意相框 03")
    private var screen = Screen.LIST
    private var selectedCategory = "全部"
    private var selectedItem = 0
    private var albumIndex = 0
    private var preview = PreviewState.IDLE
    private var returnFromBackground = false

    private val ui = JianquColors(this)
    private val ink get() = ui.ink
    private val muted get() = ui.muted
    private val accent get() = ui.accentStrong
    private fun dp(x: Int) = (resources.displayMetrics.density * x + 0.5f).toInt()
    private fun wide() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 650

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Shared-device privacy: never restore an album or browsing session.
        screen = if (section == ALBUM) Screen.ALBUM_GATE else Screen.LIST
        draw()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        draw()
    }

    override fun onStop() {
        super.onStop()
        reset()
        returnFromBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (returnFromBackground) {
            returnFromBackground = false
            draw()
        }
    }

    private fun reset() {
        screen = if (section == ALBUM) Screen.ALBUM_GATE else Screen.LIST
        selectedItem = 0
        selectedCategory = "全部"
        albumIndex = 0
        preview = PreviewState.IDLE
    }

    private fun go(target: Screen) {
        screen = target
        if (target == Screen.PREVIEW) preview = PreviewState.IDLE
        draw()
    }

    private fun back() = when (screen) {
        Screen.LIST, Screen.ALBUM_GATE -> {
            reset()
            finish()
        }
        Screen.DETAIL, Screen.EMPTY, Screen.OFFLINE -> go(Screen.LIST)
        Screen.PREVIEW -> go(Screen.DETAIL)
        Screen.ALBUM_VIEWER -> go(Screen.ALBUM_GALLERY)
        Screen.ALBUM_GALLERY, Screen.ALBUM_DENIED, Screen.ALBUM_EMPTY -> go(Screen.ALBUM_GATE)
    }

    @Deprecated("Local back navigation is an explicit part of this prototype.")
    override fun onBackPressed() = back()

    private fun title() = when (section) {
        ALBUM -> "家庭相册"
        VIDEO -> "影视节目"
        MUSIC -> "音乐戏曲"
        HEALTH -> "健康讲座"
        else -> "建政栏目"
    }

    private fun accentColor() = when (section) {
        ALBUM -> ui.albumIcon
        VIDEO -> ui.videoIcon
        MUSIC -> ui.musicIcon
        HEALTH -> ui.healthIcon
        else -> ui.newsIcon
    }

    private fun iconResource() = when (section) {
        ALBUM -> R.drawable.ic_home_images
        VIDEO -> R.drawable.ic_home_film_strip
        MUSIC -> R.drawable.ic_home_music_notes
        HEALTH -> R.drawable.ic_home_stethoscope
        else -> R.drawable.ic_home_newspaper
    }

    private fun description() = when (section) {
        ALBUM -> "来源未确定，尚未授权访问私人照片。"
        VIDEO -> "影视列表、录播与直播的界面模板。"
        MUSIC -> "戏曲、评书的音频内容界面模板。"
        HEALTH -> "区分科普与广告，课程来源尚待核实。"
        else -> "事实和评论独立标注，来源与日期需审核。"
    }

    private fun text(value: String, size: Float = JianquTypography.bodySp, color: Int = ink, bold: Boolean = false) =
        JianquTypography.text(this, value, size, color, bold)

    private fun line(parent: LinearLayout, message: String, size: Float = JianquTypography.bodySp,
                     color: Int = ink, bold: Boolean = false, top: Int = 10): TextView {
        val view = text(message, size, color, bold)
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
        return view
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = dp(if (wide()) 20 else 18)
        setPadding(pad, pad, pad, pad)
        background = JianquDrawables.shape(this@ContentGalleryActivity, ui.surface, JianquDimensions.cardRadiusDp, ui.border)
    }

    private fun button(title: String, primary: Boolean = false, click: () -> Unit): TextView =
        text(title, 16f, if (primary) ui.onAccent else accent, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(JianquDimensions.minActionHeightDp)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = JianquDrawables.touch(this@ContentGalleryActivity,
                if (primary) accent else ui.surface, JianquDimensions.cardRadiusDp,
                if (primary) null else ui.border)
            isFocusable = true
            isClickable = true
            contentDescription = title
            setOnClickListener { click() }
        }

    private fun addButton(parent: LinearLayout, title: String, primary: Boolean = false,
                          top: Int = 12, action: () -> Unit) {
        parent.addView(button(title, primary, action),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun header(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(5), dp(16), dp(5))
        addView(button("‹", false) { back() }.apply {
            textSize = 31f
            contentDescription = if (screen == Screen.LIST || screen == Screen.ALBUM_GATE)
                "返回桌面" else "返回上一级"
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(text(title(), 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(14) })
        if (wide()) addView(text("本地原型 · 无真实内容服务", 13f, accent, true))
    }

    private fun intro(): LinearLayout = card().apply {
        val iconBg = FrameLayout(this@ContentGalleryActivity).apply {
            background = JianquDrawables.shape(this@ContentGalleryActivity, accentColor(), JianquDimensions.iconWellRadiusDp)
        }
        iconBg.addView(ImageView(this@ContentGalleryActivity).apply {
            setImageResource(iconResource())
            imageTintList = ColorStateList.valueOf(ink)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        addView(iconBg, LinearLayout.LayoutParams(dp(66), dp(66)))
        line(this, title(), JianquTypography.headingLargeSp, ink, true, 14)
        line(this, description(), 16f, muted, top = 12)
        line(this, "界面数据全部虚构 · 不代表资源已接通", 14f, accent, true, 19)
        if (section == ALBUM) {
            line(this, "未读取设备相册、微信或家庭云盘。", 15f, muted, top = 14)
        } else {
            line(this, "版权、来源、发布日期与持续播放记录均待接入。", 15f, muted, top = 14)
        }
    }

    private fun contentEntries() = library[section].orEmpty()

    private fun categories(): List<String> = when (section) {
        VIDEO -> listOf("全部", "录播", "直播")
        MUSIC -> listOf("全部", "戏曲", "评书")
        HEALTH -> listOf("全部", "科普", "广告待审")
        NEWS -> listOf("全部", "事实示意", "评论示意")
        else -> listOf("全部")
    }

    private fun listPanel(): LinearLayout = card().apply {
        line(this, "演示栏目", JianquTypography.headingCompactSp, ink, true, 0)
        line(this, "请选择分类，点击条目查看共用详情页。", 15f, muted, top = 9)
        val chips = LinearLayout(this@ContentGalleryActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        categories().forEachIndexed { index, name ->
            val active = selectedCategory == name
            val chip = text(name, 15f, if (active) ui.onAccent else accent, active).apply {
                gravity = Gravity.CENTER
                setPadding(dp(13), dp(6), dp(13), dp(6))
                minHeight = dp(42)
                background = JianquDrawables.touch(this@ContentGalleryActivity,
                    if (active) accent else ui.surface, JianquDimensions.cardRadiusDp,
                    if (active) null else ui.border)
                isClickable = true
                isFocusable = true
                setOnClickListener { selectedCategory = name; draw() }
            }
            chips.addView(chip, LinearLayout.LayoutParams(-2, -2).apply {
                if (index > 0) leftMargin = dp(8)
            })
        }
        addView(HorizontalScrollView(this@ContentGalleryActivity).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }, LinearLayout.LayoutParams(-1, dp(49)).apply { topMargin = dp(12) })

        val visible = contentEntries().withIndex().filter {
            selectedCategory == "全部" || it.value.category == selectedCategory
        }
        visible.forEach { pair ->
            val item = pair.value
            val row = LinearLayout(this@ContentGalleryActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(11), dp(14), dp(11))
                background = JianquDrawables.touch(this@ContentGalleryActivity, ui.background,
                    JianquDimensions.cardRadiusDp, ui.border)
                isClickable = true
                isFocusable = true
                contentDescription = "查看内容：${item.title}"
                setOnClickListener {
                    selectedItem = pair.index
                    go(Screen.DETAIL)
                }
            }
            line(row, item.title + " ›", 17f, ink, true, 0)
            line(row, "${item.category} · ${item.badge} · 虚构示例", 14f, accent, top = 7)
            addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        addButton(this, "模拟无资源空状态", top = 17) { go(Screen.EMPTY) }
        addButton(this, "模拟断网状态", top = 9) { go(Screen.OFFLINE) }
    }

    private fun detailPanel(): LinearLayout = card().apply {
        val item = contentEntries().getOrNull(selectedItem) ?: return@apply
        line(this, "内容详情 · 共用模板", 14f, accent, true, 0)
        line(this, item.title, JianquTypography.headingCompactSp, ink, true, 13)
        line(this, "分类：${item.category}   属性：${item.badge}", 16f, ink, top = 13)
        line(this, item.summary, 16f, muted, top = 13)
        line(this, "来源：${item.rights}", 14f, muted, top = 14)
        line(this, "发布日期：未提供 · 播放资源：未接入", 14f, muted, top = 8)
        if (section == HEALTH)
            line(this, "科普不是诊疗建议；广告类必须先审查资质与标注。", 15f, accent, top = 11)
        if (section == NEWS)
            line(this, "事实条目需真实来源；评论内容需显式注明观点。", 15f, accent, top = 11)
        if (section == VIDEO)
            line(this, "直播源未接通；续播记录未接入本机或账户。", 15f, accent, top = 11)
        addButton(this, if (section == NEWS) "打开阅读界面（示意）" else "进入播放界面（示意）",
            primary = true, top = 20) { go(Screen.PREVIEW) }
        addButton(this, "返回栏目列表") { go(Screen.LIST) }
    }

    private fun previewPanel(): LinearLayout = card().apply {
        val item = contentEntries().getOrNull(selectedItem) ?: return@apply
        line(this, if (section == NEWS) "阅读界面（示意）" else "播放界面（示意）",
            JianquTypography.headingCompactSp, ink, true, 0)
        line(this, item.title + " · ${item.category}", 16f, ink, top = 12)
        val previewBox = text(if (section == NEWS)
            "正文占位 · 不含真实新闻或评论文章\n\n来源与内容事实均未提供"
        else "无媒体画面 / 无音频数据\n不解码、不播放、不传输",
            18f, muted, false).apply {
            gravity = Gravity.CENTER
            background = JianquDrawables.shape(this@ContentGalleryActivity, ui.background,
                JianquDimensions.cardRadiusDp, ui.border)
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        addView(previewBox, LinearLayout.LayoutParams(-1, dp(if (wide()) 140 else 220)).apply {
            topMargin = dp(15)
        })
        if (section != NEWS) {
            val stateLabel = when (preview) {
                PreviewState.IDLE -> "未开始 · 原型控件"
                PreviewState.PLAYING -> "模拟播放态 · 实际没有播放媒体"
                PreviewState.PAUSED -> "模拟暂停态 · 实际没有播放媒体"
            }
            line(this, stateLabel, 15f, accent, true, 13)
            addButton(this, when (preview) {
                PreviewState.IDLE -> "模拟开始播放"
                PreviewState.PLAYING -> "模拟暂停播放"
                PreviewState.PAUSED -> "模拟继续播放"
            }, primary = true) {
                preview = when (preview) {
                    PreviewState.IDLE -> PreviewState.PLAYING
                    PreviewState.PLAYING -> PreviewState.PAUSED
                    PreviewState.PAUSED -> PreviewState.PLAYING
                }
                draw()
            }
            addButton(this, "停止模拟播放") { preview = PreviewState.IDLE; draw() }
        } else {
            line(this, "本文无真实内容，事实与评论只是待接入字段。", 15f, accent, top = 15)
        }
        addButton(this, "返回内容详情") { go(Screen.DETAIL) }
    }

    private fun emptyPanel(offline: Boolean): LinearLayout = card().apply {
        line(this, if (offline) "模拟断网 · 内容暂不可用" else "暂无可用内容",
            JianquTypography.headingCompactSp, ink, true, 0)
        line(this, if (offline)
            "这是手动切换的断网演示，不表示真实网络已断开。"
        else "没有已授权资源或内容源。不能以虚构片单充当真实播放服务。",
            16f, muted, top = 16)
        addButton(this, "返回演示栏目", primary = true, top = 22) { go(Screen.LIST) }
    }

    private fun albumPanel(): LinearLayout = card().apply {
        when (screen) {
            Screen.ALBUM_GATE -> {
                line(this, "家庭照片尚未接入", JianquTypography.headingCompactSp, ink, true, 0)
                line(this, "相册来源未确定，也没有任何照片访问授权。", 16f, muted, top = 15)
                line(this, "真实照片只应在本人授权、身份明确后打开；公共设备不能展示上一位使用者的私人相册。", 15f, muted, top = 13)
                addButton(this, "体验抽象示例图集（非真实照片）", primary = true, top = 22) {
                    go(Screen.ALBUM_GALLERY)
                }
                addButton(this, "模拟未授权状态") { go(Screen.ALBUM_DENIED) }
                addButton(this, "模拟空图集") { go(Screen.ALBUM_EMPTY) }
            }
            Screen.ALBUM_DENIED -> {
                line(this, "尚未获得照片授权", JianquTypography.headingCompactSp, ink, true, 0)
                line(this, "没有访问设备图库或任何家庭账户。本状态是演示，不代表已经弹出 Android 权限请求。", 16f, muted, top = 18)
                addButton(this, "返回来源说明", primary = true, top = 22) { go(Screen.ALBUM_GATE) }
            }
            Screen.ALBUM_EMPTY -> {
                line(this, "没有可展示的照片", JianquTypography.headingCompactSp, ink, true, 0)
                line(this, "这里没有读取真实相册，空图集是手动选择的示意状态。", 16f, muted, top = 18)
                addButton(this, "返回来源说明", primary = true, top = 22) { go(Screen.ALBUM_GATE) }
            }
            Screen.ALBUM_GALLERY -> {
                line(this, "抽象示例图集", JianquTypography.headingCompactSp, ink, true, 0)
                line(this, "仅是三张无人物、无照片的示意相框。", 15f, muted, top = 9)
                albumLabels.forEachIndexed { idx, label ->
                    val row = text("$label   ›", 17f, ink, true).apply {
                        setPadding(dp(18), dp(14), dp(18), dp(14))
                        minHeight = dp(68)
                        background = JianquDrawables.touch(this@ContentGalleryActivity,
                            ui.background, JianquDimensions.cardRadiusDp, ui.border)
                        isFocusable = true
                        isClickable = true
                        setOnClickListener { albumIndex = idx; go(Screen.ALBUM_VIEWER) }
                    }
                    addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
                }
                addButton(this, "结束示例，返回相册说明", top = 16) { go(Screen.ALBUM_GATE) }
            }
            else -> Unit
        }
    }

    private fun albumViewer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ui.background)
        addView(header(), LinearLayout.LayoutParams(-1, dp(72)))
        val pane = FrameLayout(this@ContentGalleryActivity).apply {
            background = JianquDrawables.shape(this@ContentGalleryActivity, ui.surface, JianquDimensions.cardRadiusDp, ui.border)
            val label = text(
                "◇\n${albumLabels[albumIndex]}\n\n抽象相框 · 非真实照片",
                if (wide()) 28f else 24f, muted, true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(20), dp(20), dp(20))
            }
            addView(label, FrameLayout.LayoutParams(-1, -1))
        }
        addView(pane, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(8)
        })
        val navigation = LinearLayout(this@ContentGalleryActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
        }
        navigation.addView(button("上一张") {
            albumIndex = (albumIndex + albumLabels.size - 1) % albumLabels.size
            draw()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        navigation.addView(button("返回图集", true) { go(Screen.ALBUM_GALLERY) },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                leftMargin = dp(9)
                rightMargin = dp(9)
            })
        navigation.addView(button("下一张") {
            albumIndex = (albumIndex + 1) % albumLabels.size
            draw()
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        addView(navigation, LinearLayout.LayoutParams(-1, dp(78)))
        addView(footer(), LinearLayout.LayoutParams(-1, dp(36)))
    }

    private fun footer() = text("本地模拟 · 无真实版权媒体 / 无私人照片 / 无账户与播放历史",
        12f, muted).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9), 0, dp(9), 0)
    }

    private fun draw() {
        if (screen == Screen.ALBUM_VIEWER) {
            setContentView(albumViewer())
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
        }
        root.addView(header(), LinearLayout.LayoutParams(-1, dp(72)))
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val container = LinearLayout(this).apply {
            orientation = if (wide()) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(if (wide()) 18 else 15), dp(4),
                dp(if (wide()) 18 else 15), dp(20))
        }
        val left = intro()
        val right = when (screen) {
            Screen.LIST -> listPanel()
            Screen.DETAIL -> detailPanel()
            Screen.PREVIEW -> previewPanel()
            Screen.EMPTY -> emptyPanel(false)
            Screen.OFFLINE -> emptyPanel(true)
            else -> albumPanel()
        }
        if (wide()) {
            container.addView(left, LinearLayout.LayoutParams(0, -2, .82f).apply {
                rightMargin = dp(14)
            })
            container.addView(right, LinearLayout.LayoutParams(0, -2, 1.18f))
        } else {
            container.addView(left, LinearLayout.LayoutParams(-1, -2))
            container.addView(right, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            })
        }
        scroll.addView(container)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(footer(), LinearLayout.LayoutParams(-1, dp(36)))
        setContentView(root)
    }
}
