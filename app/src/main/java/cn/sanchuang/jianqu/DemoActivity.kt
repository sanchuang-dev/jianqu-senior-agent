package cn.sanchuang.jianqu

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

    private val bg = Color.rgb(245, 246, 248)
    private val ink = Color.rgb(27, 30, 35)
    private val muted = Color.rgb(92, 100, 112)
    private val accent = Color.rgb(228, 87, 46)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatRequested = intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true
        page = if (chatRequested && planFor(devices[0]) != null) Page.CHAT else Page.HOME
        draw()
        if (chatRequested && page == Page.HOME) {
            Toast.makeText(this, "请先模拟开通设备服务，即可体验小暖", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        // Shared-device safety: never reopen a previous person's mock dialogue.
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

    @Deprecated("Uses simple activity back navigation for the prototype.")
    override fun onBackPressed() {
        if (page == Page.HOME) {
            super.onBackPressed()
        } else {
            conversation.clear()
            page = Page.HOME
            draw()
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

    private fun draw() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }
        val back = TextView(this).apply {
            text = "‹ 返回"
            textSize = 18f
            setTextColor(accent)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(48)
            setOnClickListener { onBackPressed() }
        }
        bar.addView(back, LinearLayout.LayoutParams(dp(88), dp(52)))
        bar.addView(TextView(this).apply {
            text = when (page) {
                Page.HOME -> "我的闺蜜机"
                Page.PLANS -> "设备订阅"
                Page.CHAT -> "小暖"
            }
            textSize = 21f
            setTextColor(ink)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(bar)

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(4), dp(22), dp(28))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        when (page) {
            Page.HOME -> showHome(body)
            Page.PLANS -> showPlans(body)
            Page.CHAT -> showChat(body)
        }
    }

    private fun planFor(device: String): Plan? =
        plans.firstOrNull { it.id == prefs.getString("subscription_$device", null) }

    private fun showHome(body: LinearLayout) {
        line(body, "这台机器的服务", 27f, bold = true, top = 4)
        line(body, "设备编号：JQ-DEMO-01 · 非真实设备身份", 15f, color = muted)
        val current = planFor(devices[0])
        line(body, if (current == null) "尚未开通服务" else "服务可用 · ${current.title}", 22f, accent, true, 22)
        line(body, current?.details ?: "选择一个示例套餐，体验设备订阅后的服务。", 17f, muted)
        button(body, if (current == null) "模拟开通当前设备" else "更换套餐 / 批量演示", true) {
            navigate(Page.PLANS)
        }
        if (current != null) {
            line(body, "已开通：小暖角色互动、应用启动演示", 17f, top = 22)
            button(body, "进入小暖 · AI 生活伙伴", true) { navigate(Page.CHAT) }
        }
        line(body, "模拟设备列表", 22f, bold = true, top = 32)
        line(body, "用于演示一笔操作可以为多台机器开通；没有后台。", 15f, color = muted)
        devices.forEach { device ->
            line(body, "$device    ${planFor(device)?.title ?: "未开通"}", 17f, top = 16)
        }
        button(body, "重置全部演示状态") {
            AlertDialog.Builder(this)
                .setTitle("重置演示？")
                .setMessage("清除这三个模拟设备的套餐，不影响系统或其他应用。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置") { _, _ ->
                    prefs.edit().clear().apply()
                    conversation.clear()
                    selectedPlan = plans.first().id
                    batch = false
                    draw()
                }.show()
        }
        line(body, "离线原型，不会扣费，也不会绑定真实设备。", 14f, muted, top = 26)
    }

    private fun showPlans(body: LinearLayout) {
        line(body, "选择演示套餐", 27f, bold = true, top = 4)
        line(body, "仅模拟开通本机服务，不产生订单或实际收费。", 16f, muted)
        plans.forEach { plan ->
            val selected = selectedPlan == plan.id
            button(body, "${if (selected) "✓ " else ""}${plan.title} · ${plan.details}", selected) {
                selectedPlan = plan.id
                draw()
            }
        }
        val check = CheckBox(this).apply {
            text = "同时为另外两台模拟设备开通（批量演示）"
            textSize = 17f
            setTextColor(ink)
            isChecked = batch
            minHeight = dp(56)
            setOnCheckedChangeListener { _, value -> batch = value }
        }
        body.addView(check, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        line(body, "当前设备：JQ-DEMO-01，其余两台只是虚构演示记录。", 15f, muted)
        button(body, "确认模拟开通", true) { confirmSubscription() }
        button(body, "返回我的闺蜜机") { navigate(Page.HOME) }
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
                page = if (chatRequested) Page.CHAT else Page.HOME
                draw()
                Toast.makeText(this, "模拟开通成功", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun showChat(body: LinearLayout) {
        if (planFor(devices[0]) == null) {
            navigate(Page.HOME)
            return
        }
        line(body, "小暖", 30f, bold = true, top = 4)
        line(body, "模拟 AI · 预设对白，不是真实模型", 15f, accent, top = 3)
        line(body, "选一种风格，看看小暖会怎样回应。", 17f, muted)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        styles.forEachIndexed { index, style ->
            val active = index == styleIndex
            val chip = TextView(this).apply {
                text = style.label
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (active) Color.WHITE else ink)
                setTypeface(null, Typeface.BOLD)
                background = bgShape(if (active) accent else Color.WHITE)
                setOnClickListener {
                    styleIndex = index
                    conversation.clear()
                    draw()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                leftMargin = if (index > 0) dp(8) else 0
            })
        }
        body.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
        line(body, "当前风格：${styles[styleIndex].title}", 17f, bold = true, top = 18)
        conversation.forEach { exchange ->
            line(body, "你：${exchange.prompt}", 17f, muted, top = 16)
            line(body, "小暖：${exchange.reply}", 18f, top = 8)
        }
        line(body, "点一句话，看看预设回答：", 16f, muted, top = 22)
        styles[styleIndex].lines.forEach { prompt ->
            button(body, prompt.prompt) {
                if (conversation.size >= 4) conversation.removeAt(0)
                conversation.add(prompt)
                draw()
            }
        }
        button(body, "让小暖帮我打开一个应用", true) { confirmAppLaunch() }
        button(body, "清空本轮对话") {
            conversation.clear()
            draw()
        }
        button(body, "退出小暖") { navigate(Page.HOME) }
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
