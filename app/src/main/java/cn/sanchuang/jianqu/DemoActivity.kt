package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.content.res.Configuration
import android.view.View
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * JQ-003 local mock: never communicates with payment, AI, account or device-ID services.
 * Only fictional device entitlements are stored; conversations stay in memory.
 */
class DemoActivity : Activity() {
    companion object {
        const val EXTRA_OPEN_CHAT = "cn.sanchuang.jianqu.extra.OPEN_CHAT"
    }
    private enum class Page { HOME, PLANS, CHAT }
    private data class Plan(val id: String, val title: String, val details: String)
    private data class Line(val prompt: String, val reply: String)
    private data class Style(val title: String, val label: String, val lines: List<Line>)

    private val plans = listOf(
        Plan("companion", "陪伴体验包", "小暖角色切换 · 预设陪伴对话"),
        Plan("daily", "生活体验包", "小暖日常问候 · 本机应用启动演示")
    )
    private val styles = listOf(
        Style("严厉的慈父", "慈父", listOf(
            Line("我不想起床。", "天亮了，慢慢坐起来，先喝口水。今天也要打起精神。"),
            Line("今天做点什么？", "吃好早饭，找件自己喜欢的小事做。日子要过得有滋味。"),
            Line("我想听戏。", "好主意。去找一出喜欢的戏，听完再来和我聊聊。"),
            Line("你会一直在吗？", "想聊的时候可以来找小暖，也别忘了和身边的人常联系。")
        )),
        Style("高冷的御姐", "御姐", listOf(
            Line("有点无聊。", "那就挑一件有趣的事做。你可比自己想的有办法。"),
            Line("我想出门走走。", "不错。出门前看看天气，带好钥匙，慢慢走。"),
            Line("今天心情不好。", "我听着。不必急着把心情整理得很漂亮。"),
            Line("我想放点音乐。", "找首熟悉的好歌。好音乐从来不用赶时间。")
        )),
        Style("傻甜的小妹", "小妹", listOf(
            Line("早上好！", "早安呀！今天的小太阳已经上线啦！"),
            Line("讲个笑话。", "一只猫说要减肥，结果先把体重秤藏起来啦。"),
            Line("想回忆以前。", "好呀！以前你最爱去哪里玩？"),
            Line("想找人说话。", "我在这里陪你聊聊，也可以给家人朋友问声好呀。")
        ))
    )

    private val devices = listOf("演示设备 01", "演示设备 02", "演示设备 03")
    private val prefs by lazy { getSharedPreferences("jianqu_local_demo", MODE_PRIVATE) }
    private var page = Page.HOME
    private var selectedPlan = plans.first().id
    private var batch = false
    private var styleIndex = 0
    private val conversation = mutableListOf<Line>()
    private var redrawOnResume = false
    private var chatRequested = false

    private val bg = Color.rgb(250, 248, 244)
    private val ink = Color.rgb(48, 53, 53)
    private val muted = Color.rgb(112, 119, 117)
    private val accent = Color.rgb(159, 88, 69)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatRequested = intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true
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
            Page.PLANS -> navigate(Page.HOME)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    private fun bgShape(color: Int): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }

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
            background = bgShape(if (primary) accent else Color.WHITE)
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
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 650

    private fun label(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
        }

    private fun paper(color: Int = Color.WHITE, outlined: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(12).toFloat()
            if (outlined) setStroke(dp(1), Color.rgb(232, 223, 214))
        }

    private fun actionText(value: String, primary: Boolean = false, click: () -> Unit) =
        label(value, 16f, if (primary) Color.WHITE else accent, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = paper(if (primary) accent else Color.WHITE, !primary)
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
        bar.addView(actionText("‹  返回", false) { onBackPressed() },
            LinearLayout.LayoutParams(dp(112), dp(52)))
        bar.addView(label(if (page == Page.HOME) "我的服务" else "设备订阅", 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(12) })
        bar.addView(label("本机演示", 14f, muted), LinearLayout.LayoutParams(-2, dp(52)))
        root.addView(bar)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (page == Page.HOME) showHome(body) else showPlans(body)
    }

    private fun planFor(device: String): Plan? =
        plans.firstOrNull { it.id == prefs.getString("subscription_$device", null) }

    private fun showHome(body: LinearLayout) {
        val landscape = wide()
        body.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val status = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(18))
            background = paper(outlined = true)
        }
        line(status, "当前设备 · JQ-DEMO-01", 15f, muted, top = 0)
        val current = planFor(devices[0])
        line(status, if (current == null) "尚未模拟开通" else "服务可用 · ${current.title}",
            25f, ink, true, 16)
        line(status, current?.details ?: "这是本机的设备服务演示，非真实账户与支付。", 16f, muted, top = 12)
        line(status, "设备资格和小暖本地模拟对话彼此独立。", 15f, muted, top = 15)
        line(status, "模拟设备记录", 18f, ink, true, 24)
        devices.forEach { device ->
            line(status, "$device  ·  ${planFor(device)?.title ?: "未开通"}", 16f, top = 13)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(18))
            background = paper(outlined = true)
        }
        line(actions, "设备服务", 24f, ink, true, 0)
        line(actions, "管理演示套餐，不影响实际 Android 应用或设备权限。", 16f, muted, top = 10)
        button(actions, if (current == null) "查看演示套餐" else "更换演示套餐", true) {
            navigate(Page.PLANS)
        }
        line(actions, "需要展示多台设备开通？", 16f, muted, top = 24)
        button(actions, "批量开通演示设备") {
            batch = true
            navigate(Page.PLANS)
        }
        button(actions, "重置演示数据") {
            AlertDialog.Builder(this)
                .setTitle("重置设备演示？")
                .setMessage("仅清除三个模拟设备的套餐记录；不影响系统和其他应用。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置") { _, _ ->
                    prefs.edit().clear().apply()
                    conversation.clear()
                    selectedPlan = plans.first().id
                    batch = false
                    draw()
                }.show()
        }
        line(actions, "本地原型：不会扣费，不会绑定真实设备。", 14f, muted, top = 24)
        if (landscape) {
            body.addView(status, LinearLayout.LayoutParams(0, -2, 1.05f).apply {
                rightMargin = dp(14)
            })
            body.addView(actions, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            body.addView(status, LinearLayout.LayoutParams(-1, -2))
            body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            })
        }
    }

    private fun showPlans(body: LinearLayout) {
        val landscape = wide()
        body.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val planColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(18))
            background = paper(outlined = true)
        }
        line(planColumn, "选择演示套餐", 23f, ink, true, top = 0)
        line(planColumn, "仅用于本机体验，不产生订单和费用。", 15f, muted, top = 9)
        plans.forEach { plan ->
            val selected = selectedPlan == plan.id
            button(planColumn, "${if (selected) "✓ " else ""}${plan.title} · ${plan.details}", selected) {
                selectedPlan = plan.id
                draw()
            }
        }
        val confirmColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(18))
            background = paper(outlined = true)
        }
        line(confirmColumn, "开通范围", 23f, ink, true, top = 0)
        line(confirmColumn, "当前设备：JQ-DEMO-01", 16f, muted, top = 12)
        val check = CheckBox(this).apply {
            text = "同时为另外两台模拟设备开通"
            textSize = 17f
            setTextColor(ink)
            isChecked = batch
            minHeight = dp(56)
            setOnCheckedChangeListener { _, value -> batch = value }
        }
        confirmColumn.addView(check, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        line(confirmColumn, "批量记录是虚构演示，不对应真实设备。", 15f, muted, top = 6)
        button(confirmColumn, "确认模拟开通", true) { confirmSubscription() }
        button(confirmColumn, "返回我的服务") { navigate(Page.HOME) }
        if (landscape) {
            body.addView(planColumn, LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(14)
            })
            body.addView(confirmColumn, LinearLayout.LayoutParams(0, -2, 1f))
        } else {
            body.addView(planColumn, LinearLayout.LayoutParams(-1, -2))
            body.addView(confirmColumn, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            })
        }
    }

    private fun confirmSubscription() {
        val plan = plans.first { it.id == selectedPlan }
        val count = if (batch) 3 else 1
        AlertDialog.Builder(this)
            .setTitle("确认模拟开通")
            .setMessage("为 $count 台演示设备开通「${plan.title}」？\n\n原型演示，不会扣费。")
            .setNegativeButton("取消", null)
            .setPositiveButton("确认") { _, _ ->
                val edit = prefs.edit()
                devices.take(count).forEach { edit.putString("subscription_$it", plan.id) }
                edit.apply()
                page = Page.HOME
                draw()
                Toast.makeText(this, "模拟开通成功", Toast.LENGTH_SHORT).show()
            }.show()
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
        header.addView(actionText("‹ 返回桌面") {
            conversation.clear()
            finish()
        }, LinearLayout.LayoutParams(dp(135), dp(50)))
        header.addView(label("小暖", 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(50), 1f).apply { leftMargin = dp(18) })
        header.addView(actionText("清空本轮") {
            conversation.clear()
            drawChat()
        }, LinearLayout.LayoutParams(dp(115), dp(50)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(64)))

        val area = LinearLayout(this).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(12))
        }
        root.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))

        // Portrait: brief identity section above chat; landscape: dedicated left identity rail.
        val profile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(if (landscape) 28 else 9), dp(14), dp(12))
        }
        val orb = label("✦", 34f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.rgb(246, 213, 193), Color.rgb(182, 125, 103), Color.rgb(228, 196, 174)
            )).apply { shape = GradientDrawable.OVAL }
            contentDescription = "小暖陪伴标志"
        }
        profile.addView(orb, LinearLayout.LayoutParams(dp(68), dp(68)))
        profile.addView(label("小暖", 28f, ink, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(-1, dp(42)))
        profile.addView(label("陪你聊聊生活中的小事", 15f, muted).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(29)))
        profile.addView(label("本地模拟对话 · 非真实 AI", 14f, accent, true).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, dp(32)))
        val stylesRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        styles.forEachIndexed { index, style ->
            val selected = styleIndex == index
            stylesRow.addView(label(style.label, 16f, if (selected) Color.WHITE else ink, true).apply {
                gravity = Gravity.CENTER
                background = paper(if (selected) accent else Color.WHITE, !selected)
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
            container.addView(label("想聊些什么？从下方选一句开始吧。", 17f, muted).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-1, dp(104)))
        } else {
            conversation.forEach { exchange ->
                val userRow = LinearLayout(this).apply { gravity = Gravity.END }
                userRow.addView(label("你：${exchange.prompt}", 17f).apply {
                    background = paper(Color.rgb(239, 229, 218))
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                }, LinearLayout.LayoutParams(-2, -2))
                container.addView(userRow, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(12)
                })
                val replyRow = LinearLayout(this).apply { gravity = Gravity.START }
                replyRow.addView(label("小暖：${exchange.reply}", 17f).apply {
                    background = paper(Color.rgb(249, 247, 244), outlined = true)
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
