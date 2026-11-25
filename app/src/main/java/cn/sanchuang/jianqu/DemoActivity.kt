package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.widget.FrameLayout
import android.view.View
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.res.Configuration
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.widget.ImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import cn.sanchuang.jianqu.demo.rental.DemoRentalPolicy
import cn.sanchuang.jianqu.demo.rental.DemoRentalPrefs

/**
 * JQ-003 local mock: never communicates with payment, AI, account or device-ID services.
 * Only local rental expiry and demo service selections are stored; conversations stay in memory.
 */
class DemoActivity : Activity() {
    companion object {
        const val EXTRA_OPEN_CHAT = "cn.sanchuang.jianqu.extra.OPEN_CHAT"
    }
    private enum class Page { HOME, PAY, CHAT }
    private data class ServiceOption(
        val id: String,
        val name: String,
        val shortName: String,
        val description: String,
        val note: String,
        val backgroundColor: Int,
        val iconRes: Int
    )
    private data class Line(val prompt: String, val reply: String)
    private data class Style(val title: String, val label: String, val tone: String, val welcome: String, val lines: List<Line>)

    private val serviceOptions = listOf(
        ServiceOption("companion", "知己", "人格陪伴", "小暖人格互动", "语音与个性记忆", LauncherUiTokens.companionStart, R.drawable.ic_home_hand_heart),
        ServiceOption("wellness", "元气", "健康关怀", "康复训练、健康打卡", "健康计划与课程", LauncherUiTokens.healthIcon, R.drawable.ic_home_stethoscope),
        ServiceOption("cinema", "光影", "影视娱乐", "影视、戏曲与音乐", "精选视听内容", LauncherUiTokens.videoIcon, R.drawable.ic_home_film_strip),
        ServiceOption("family", "牵挂", "家人关怀", "家人通话与相册", "亲情互动", LauncherUiTokens.albumIcon, R.drawable.ic_home_video_camera),
        ServiceOption("local", "惠享", "本地优惠", "本地生活优惠购", "9.9 折 · 示例权益", LauncherUiTokens.storeIcon, R.drawable.ic_home_shopping_bag_open)
    )
    // A draft selection is never an entitlement until the demo payment is confirmed.
    private val pendingServiceIds = linkedSetOf<String>()

    // Same four prompts across characters make the persona differences observable,
    // rather than changing both the question and the answer when a style is selected.
    private val styles = listOf(
        Style("严厉的慈父", "慈父", "讲原则，也会照顾你", "有什么话就直说。别一个人闷在心里。", listOf(
            Line("心情不太好。", "心里不痛快就说出来，别硬扛着。先喝口水，坐稳了，咱们把事情一件一件理清楚。"),
            Line("想出门走走。", "可以去。先看天气，带好钥匙和手机，路上别着急。走得稳，比走得远重要。"),
            Line("一个人在家有点闷。", "老闷在屋里可不行。起来活动活动，再给熟悉的朋友打个电话，别总把自己关着。"),
            Line("听点什么好？", "先听一段喜欢的戏，舒舒心。听一会儿也记得歇歇眼睛，别一直盯着屏幕。")
        )),
        Style("高冷的御姐", "御姐", "话不多，却有自己的分寸", "想说什么就说。不必先把心情整理好。", listOf(
            Line("心情不太好。", "不用勉强自己说没事。你说，我听着。事情再乱，也总能一件件收拾。"),
            Line("想出门走走。", "去吧。天凉就添件衣服，阳光好就多走两步。别赶，风景又不会跑。"),
            Line("一个人在家有点闷。", "那就换换空气。挑一件你真想做的事，别让无聊替你安排今天。"),
            Line("听点什么好？", "听一首熟悉的老歌，或者看部好电影。你想热闹一点，还是安静一点？")
        )),
        Style("傻甜的小妹", "小妹", "元气十足，爱逗你开心", "嘿嘿，今天想聊点什么呀？小妹在呢！", listOf(
            Line("心情不太好。", "哎呀，心情打结啦？先送你一个云朵抱抱！想吐槽就说，小妹认真听着呢。"),
            Line("想出门走走。", "好耶，出去晒晒太阳！钥匙、手机、外套，咱们一样一样检查好再出发～"),
            Line("一个人在家有点闷。", "那我们找点乐子！听首歌、翻翻老照片，或者给朋友打声招呼？"),
            Line("听点什么好？", "我投戏曲一票！听得高兴了，再换首老歌，今天的快乐慢慢攒起来～")
        ))
    )

    private val rentalPrefs by lazy {
        DemoRentalPrefs(getSharedPreferences(DemoRentalPrefs.PREFERENCES_NAME, MODE_PRIVATE))
    }
    private var page = Page.HOME
    private var styleIndex = 0
    private val conversation = mutableListOf<Line>()
    private var redrawOnResume = false
    private var chatRequested = false

    private val bg = LauncherUiTokens.background
    private val ink = LauncherUiTokens.ink
    private val muted = LauncherUiTokens.muted
    private val accent = LauncherUiTokens.terracotta

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatRequested = intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true
        pendingServiceIds.clear()
        pendingServiceIds.addAll(rentalState().serviceIds)
        // JQ-007: the free local script demo is not gated by fictional device subscriptions.
        page = if (chatRequested) Page.CHAT else Page.HOME
        draw()
    }

    override fun onStop() {
        super.onStop()
        // A shared device must never reveal a previous person's dialogue after backgrounding.
        conversation.clear()
        if (page == Page.CHAT) {
            page = Page.HOME
            redrawOnResume = true
        }
    }

    override fun onResume() {
        super.onResume()
        if (redrawOnResume) {
            redrawOnResume = false
            draw()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation is the same encounter, not a new user's session.
        // Rebuild the responsive layout without persisting the dialogue to disk.
        draw()
    }

    @Deprecated("Uses simple activity back navigation for the prototype.")
    override fun onBackPressed() {
        when (page) {
            Page.CHAT, Page.HOME -> {
                conversation.clear()
                finish() // Return to the true Launcher HOME, not an extra service landing page.
            }
            Page.PAY -> navigate(Page.HOME)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun pressable(color: Int, outlined: Boolean = false) = RippleDrawable(
        ColorStateList.valueOf(Color.argb(35, 123, 83, 64)),
        paper(color, outlined),
        null
    )

    /** 56dp icon-only back target with the same touch treatment as HOME. */
    private fun backControl(description: String, click: () -> Unit): FrameLayout =
        FrameLayout(this).apply {
            background = pressable(LauncherUiTokens.surface, outlined = true)
            isClickable = true
            isFocusable = true
            contentDescription = description
            setOnClickListener { click() }
            addView(object : View(this@DemoActivity) {
                private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = dp(2).toFloat()
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f
                    canvas.drawLine(cx + dp(11), cy, cx - dp(10), cy, stroke)
                    canvas.drawLine(cx - dp(10), cy, cx - dp(2), cy - dp(8), stroke)
                    canvas.drawLine(cx - dp(10), cy, cx - dp(2), cy + dp(8), stroke)
                }
            }, FrameLayout.LayoutParams(-1, -1))
        }

    private fun line(
        parent: LinearLayout, text: String, size: Float = 18f,
        color: Int = ink, bold: Boolean = false, top: Int = 12
    ) {
        val v = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            setLineSpacing(dp(3).toFloat(), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        parent.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun button(parent: LinearLayout, title: String, primary: Boolean = false, run: () -> Unit) {
        val view = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(if (primary) Color.WHITE else ink)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = dp(56)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = pressable(if (primary) accent else LauncherUiTokens.surface, !primary)
            isClickable = true
            isFocusable = true
            setOnClickListener { run() }
        }
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun navigate(to: Page) {
        if (page == Page.CHAT) conversation.clear()
        page = to
        draw()
    }

    private fun wide(): Boolean =
        // Some Android 11 emulator/display overrides retain a stale Configuration orientation.
        // Use the actual window dimensions so narrow portrait layouts never get two columns.
        resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels &&
            resources.displayMetrics.widthPixels / resources.displayMetrics.density >= 650f

    private fun label(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

    private fun paper(color: Int = LauncherUiTokens.surface, outlined: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(LauncherUiTokens.cardRadiusDp).toFloat()
            if (outlined) setStroke(dp(1), LauncherUiTokens.divider)
        }

    private fun actionText(value: String, primary: Boolean = false, click: () -> Unit) =
        label(value, 16f, if (primary) Color.WHITE else accent, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = pressable(if (primary) accent else LauncherUiTokens.surface, !primary)
            isClickable = true
            isFocusable = true
            setOnClickListener { click() }
        }

    private fun draw() {
        if (page == Page.CHAT) {
            drawChat()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        bar.addView(backControl(if (page == Page.PAY) "返回设备租赁" else "返回桌面") {
            onBackPressed()
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        bar.addView(
            label(if (page == Page.HOME) "设备租赁" else "扫码付款", 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(12) }
        )
        bar.addView(label("原型演示", 14f, muted), LinearLayout.LayoutParams(-2, dp(52)))
        root.addView(bar)

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (page == Page.HOME) showRental(body) else showPayment(body)
    }

    private fun allowedServiceIds(): List<String> = serviceOptions.map { it.id }

    private fun rentalState() = rentalPrefs.load(allowedServiceIds())

    private fun rentalDate(millis: Long): String =
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(millis))

    private fun serviceNames(ids: Set<String>): String =
        serviceOptions.filter { it.id in ids }.joinToString("、") { it.shortName }

    private fun renderServiceChoices(grid: LinearLayout, summary: TextView) {
        grid.removeAllViews()
        serviceOptions.chunked(2).forEachIndexed { rowIndex, options ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            options.forEachIndexed { index, service ->
                val selected = service.id in pendingServiceIds
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp(116)
                    setPadding(dp(13), dp(12), dp(13), dp(12))
                    background = LauncherUiTokens.touch(
                        this@DemoActivity,
                        service.backgroundColor,
                        14,
                        if (selected) accent else LauncherUiTokens.divider
                    )
                    isClickable = true
                    isFocusable = true
                    contentDescription = "${service.name}·${service.shortName}，${if (selected) "已选" else "未选"}，点击切换"
                    setOnClickListener {
                        if (selected) pendingServiceIds.remove(service.id)
                        else pendingServiceIds.add(service.id)
                        renderServiceChoices(grid, summary)
                    }
                }
                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                titleRow.addView(label(service.name, 20f, ink, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
                titleRow.addView(label(if (selected) "✓ 已选" else "＋ 添加", 13f,
                    if (selected) accent else muted, selected))
                tile.addView(titleRow)
                val pictogram = ImageView(this).apply {
                    setImageResource(service.iconRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                tile.addView(pictogram, LinearLayout.LayoutParams(dp(33), dp(33)).apply {
                    topMargin = dp(10)
                })
                line(tile, service.shortName, 15f, ink, true, top = 8)
                line(tile, service.description, 14f, ink, top = 7)
                line(tile, service.note, 12f, muted, top = 5)
                row.addView(tile, LinearLayout.LayoutParams(0, -2, if (options.size == 1) 2f else 1f).apply {
                    if (index > 0) leftMargin = dp(9)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                if (rowIndex > 0) topMargin = dp(9)
            })
        }
        summary.text = if (pendingServiceIds.isEmpty()) {
            "本次仅租赁设备"
        } else {
            "已选择 ${pendingServiceIds.size} 项：${serviceNames(pendingServiceIds)}"
        }
    }

    private fun showRental(body: LinearLayout) {
        val landscape = wide()
        val currentRental = rentalState()
        val expiry = currentRental.expiryMs
        val active = currentRental.isActive(System.currentTimeMillis())
        val committedServices = if (active) currentRental.serviceIds else emptySet()
        body.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        val overview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(23), dp(24), dp(25))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(LauncherUiTokens.companionStart, LauncherUiTokens.companionEnd)
            ).apply { cornerRadius = dp(18).toFloat() }
        }
        line(overview, "见趣 · 闺蜜机", 21f, ink, true, 0)
        line(overview, "本机编号  ${DemoRentalPolicy.DEVICE_ID}", 15f, muted, top = 10)
        val state = when {
            active -> "租赁有效"
            expiry > 0L -> "租赁已到期"
            else -> "尚未租赁"
        }
        line(overview, state, 32f, if (active) accent else ink, true, 25)
        line(overview,
            if (expiry > 0L) "租期至 ${rentalDate(expiry)}" else "租用这台设备，按期续租",
            17f, muted, top = 9
        )
        if (active) {
            line(overview, "本期服务组合", 16f, ink, true, top = 22)
            line(overview,
                if (committedServices.isEmpty()) "仅设备租赁" else serviceNames(committedServices),
                16f, accent, top = 7
            )
            line(overview, "服务权益仅作原型演示", 13f, muted, top = 6)
        }

        val action = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(18))
            background = paper(outlined = true)
        }
        line(action, "选择你的服务", 23f, ink, true, 0)
        line(action, "机器租赁为基础，服务可自由组合", 15f, muted, top = 7)

        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        action.addView(grid, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })
        val summary = label("", 15f, ink, true)
        action.addView(summary, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(13)
        })
        renderServiceChoices(grid, summary)
        line(action, "服务价格待定 · 当前仅演示组合选择", 13f, muted, top = 6)
        button(action, if (active) "扫码续租" else "扫码开通", true) {
            navigate(Page.PAY)
        }
        line(action, "付款演示不会实际扣费", 13f, muted, top = 8)

        val resetLink = label("重置演示", 14f, muted).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                AlertDialog.Builder(this@DemoActivity)
                    .setTitle("重置本机租赁？")
                    .setMessage("将清除本机的演示租期及已选服务。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("重置") { _, _ ->
                        rentalPrefs.clear()
                        pendingServiceIds.clear()
                        draw()
                    }.show()
            }
        }
        action.addView(resetLink, LinearLayout.LayoutParams(-1, dp(44)).apply {
            topMargin = dp(5)
        })
        if (landscape) {
            body.addView(overview, LinearLayout.LayoutParams(0, -2, 0.85f).apply {
                rightMargin = dp(15)
            })
            body.addView(action, LinearLayout.LayoutParams(0, -2, 1.25f))
        } else {
            body.addView(overview, LinearLayout.LayoutParams(-1, -2))
            body.addView(action, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(15)
            })
        }
    }

    private fun showPayment(body: LinearLayout) {
        val landscape = wide()
        body.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        val order = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(21), dp(24), dp(22))
        }
        line(order, "本机租赁 · 订单演示", 23f, ink, true, 0)
        line(order, "¥99", 44f, accent, true, 18)
        line(order, "设备租金演示价 · 30 天", 15f, muted, top = 3)
        line(order, "租赁设备  ${DemoRentalPolicy.DEVICE_ID}", 16f, ink, top = 23)
        val currentRental = rentalState()
        if (currentRental.isActive(System.currentTimeMillis())) {
            line(order, "现有租期至 ${rentalDate(currentRental.expiryMs)}", 15f, muted, top = 10)
        }
        line(order, "本次服务组合", 17f, ink, true, top = 22)
        line(order,
            if (pendingServiceIds.isEmpty()) "仅设备租赁"
            else serviceNames(pendingServiceIds),
            19f, accent, true, top = 7
        )
        line(order, "服务套餐价格尚未制定，不计入示例金额", 14f, muted, top = 10)

        val payment = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            background = paper(outlined = true)
        }
        line(payment, "手机扫码付款", 23f, ink, true, 0)
        val qr = ImageView(this).apply {
            setImageResource(R.drawable.qr_rental_demo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "二维码演示图，不支持真实付款"
        }
        val qrSize = dp(if (landscape) 184 else 226)
        payment.addView(qr, LinearLayout.LayoutParams(qrSize, qrSize).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(10)
        })
        line(payment, "演示二维码 · 无法真实付款", 14f, muted, top = 7)
        button(payment, "模拟付款完成", true) { completeDemoPayment() }
        if (landscape) {
            body.addView(order, LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(18)
            })
            body.addView(payment, LinearLayout.LayoutParams(0, -2, 1.1f))
        } else {
            body.addView(order, LinearLayout.LayoutParams(-1, -2))
            body.addView(payment, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun completeDemoPayment() {
        // The only path that changes this machine's demo lease and demo service choices.
        // An unconfirmed draft is never persisted.
        val renewed = DemoRentalPolicy.renew(
            current = rentalState(),
            nowMs = System.currentTimeMillis(),
            selectedServiceIds = pendingServiceIds,
            allowedServiceIds = allowedServiceIds()
        )
        rentalPrefs.save(renewed)
        page = Page.HOME
        draw()
    }

    private fun drawChat() {
        val landscape = wide()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(17), dp(6), dp(17), dp(6))
        }
        header.addView(backControl("返回桌面") {
            conversation.clear()
            finish()
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        header.addView(label("小暖", 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(56), 1f).apply { leftMargin = dp(14) })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(72)))

        val area = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        root.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))

        // Portrait: brief identity section above chat; landscape: dedicated left identity rail.
        val profile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(if (landscape) 25 else 14), dp(14), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(LauncherUiTokens.companionStart, LauncherUiTokens.companionEnd)
            ).apply { cornerRadius = dp(LauncherUiTokens.companionRadiusDp).toFloat() }
        }
        val orb = LauncherUiTokens.companionOrb(this)
        orb.contentDescription = "小暖陪伴标志"
        profile.addView(orb, LinearLayout.LayoutParams(dp(68), dp(68)))
        profile.addView(label(styles[styleIndex].title, 25f, ink, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, dp(42)))
        profile.addView(label(styles[styleIndex].tone, 15f, muted).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(30)))
        profile.addView(label("本地模拟对话 · 非真实 AI", 14f, accent, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(32)))
        val stylesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        styles.forEachIndexed { index, style ->
            val selected = styleIndex == index
            stylesRow.addView(label(style.label, 16f, if (selected) Color.WHITE else ink, true).apply {
                gravity = Gravity.CENTER
                background = pressable(if (selected) accent else LauncherUiTokens.surface, !selected)
                isClickable = true
                isFocusable = true
                contentDescription = "切换小暖风格：${style.title}"
                setOnClickListener {
                    styleIndex = index
                    conversation.clear()
                    drawChat()
                }
            }, LinearLayout.LayoutParams(0, dp(50), 1f).apply {
                if (index > 0) leftMargin = dp(7)
            })
        }
        profile.addView(stylesRow, LinearLayout.LayoutParams(-1, dp(50)).apply {
            topMargin = dp(9)
        })
        if (landscape) {
            profile.addView(label("当前风格 · ${styles[styleIndex].title}", 15f, muted).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(46)))
            area.addView(profile, LinearLayout.LayoutParams(dp(294), -1).apply {
                rightMargin = dp(14)
            })
        } else {
            area.addView(profile, LinearLayout.LayoutParams(-1, -2))
        }

        val chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = paper(outlined = true)
            setPadding(dp(12), dp(9), dp(12), dp(12))
        }
        val history = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(8))
        }
        history.addView(messages)
        chat.addView(history, LinearLayout.LayoutParams(-1, 0, 1f))
        renderMessages(messages, history)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(label("点一句话，看看小暖如何回应", 14f, muted),
            LinearLayout.LayoutParams(-1, dp(29)))
        val options = styles[styleIndex].lines
        for (row in 0 until 2) {
            val optionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 2) {
                val line = options[row * 2 + col]
                optionRow.addView(actionText(line.prompt) {
                    if (conversation.size >= 4) conversation.removeAt(0)
                    conversation.add(line)
                    renderMessages(messages, history)
                }, LinearLayout.LayoutParams(0, dp(49), 1f).apply {
                    if (col > 0) leftMargin = dp(7)
                })
            }
            controls.addView(optionRow, LinearLayout.LayoutParams(-1, dp(49)).apply {
                if (row > 0) topMargin = dp(7)
            })
        }
        controls.addView(actionText("打开本机应用 · 需要确认") { confirmAppLaunch() },
            LinearLayout.LayoutParams(-1, dp(49)).apply { topMargin = dp(9) })
        chat.addView(controls, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9)
        })
        area.addView(chat, if (landscape) LinearLayout.LayoutParams(0, -1, 1f)
            else LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun renderMessages(container: LinearLayout, scroll: ScrollView) {
        container.removeAllViews()
        if (conversation.isEmpty()) {
            container.addView(label(styles[styleIndex].welcome, 17f, muted).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(104)))
        } else {
            conversation.forEach { exchange ->
                val userRow = LinearLayout(this).apply { gravity = Gravity.END }
                userRow.addView(label("你：${exchange.prompt}", 17f).apply {
                    background = paper(LauncherUiTokens.messageUser)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                }, LinearLayout.LayoutParams(-2, -2))
                container.addView(userRow, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(12)
                })
                val replyRow = LinearLayout(this).apply { gravity = Gravity.START }
                replyRow.addView(label("小暖 · ${styles[styleIndex].label}：${exchange.reply}", 17f).apply {
                    background = paper(LauncherUiTokens.messageAssistant, outlined = true)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                }, LinearLayout.LayoutParams(-2, -2))
                container.addView(replyRow, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                })
            }
        }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun confirmAppLaunch() {
        val launcher = getSystemService(LauncherApps::class.java)
        val available = try {
            launcher.getActivityList(null, Process.myUserHandle())
                .filter { it.componentName.packageName != packageName }
        } catch (error: Exception) {
            Log.e("JianquDemo", "Unable to enumerate installed applications", error)
            Toast.makeText(this, "应用目录读取失败，请稍后重试", Toast.LENGTH_LONG).show()
            return
        }
        val target = available.firstOrNull {
            val name = it.label.toString().lowercase()
            listOf("相册", "照片", "photos", "gallery").any { keyword -> name.contains(keyword) }
        } ?: available.firstOrNull {
            val name = it.label.toString().lowercase()
            listOf("时钟", "clock").any { keyword -> name.contains(keyword) }
        } ?: available.firstOrNull()
        if (target == null) {
            Toast.makeText(this, "没有找到可打开的已安装应用", Toast.LENGTH_LONG).show()
            return
        }
        val label = target.label.toString()
        AlertDialog.Builder(this)
            .setTitle("小暖 · 应用打开演示")
            .setMessage("准备打开「$label」。\n\n这是实际启动本机应用，不是 AI 推理。")
            .setNegativeButton("取消", null)
            .setPositiveButton("打开应用") { _, _ ->
                try {
                    launcher.startMainActivity(target.componentName, target.user, null, null)
                } catch (error: Exception) {
                    Log.e("JianquDemo", "Cannot launch requested application", error)
                    Toast.makeText(this, "无法打开「$label」，应用可能已停用或卸载", Toast.LENGTH_LONG).show()
                }
            }.show()
    }
}
