package cn.sanchuang.jianqu

import android.app.Activity
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * JQ-008: local-only call-flow demonstrator. No account, contacts provider,
 * camera, microphone, WebRTC, WeChat SDK or call-service integration.
 *
 * All fictional contacts and call state are in memory. Backgrounding clears
 * the session, while in-place orientation changes only reflow the UI.
 */
class FamilyCallActivity : Activity() {
    private val ui = JianquColors(this)
    private enum class Screen { UNBOUND, CONTACTS, CONFIRM, RINGING, CONNECTED, FINISHED }
    private enum class EndReason { HUNG_UP, REFUSED, NO_ANSWER, OFFLINE, NO_CAMERA, PERMISSION_DENIED, CANCELED }
    private data class DemoContact(val title: String, val relation: String, val initial: String)

    private val contacts = listOf(
        DemoContact("演示家人 A", "女儿 · 虚构联系人", "女"),
        DemoContact("演示家人 B", "儿子 · 虚构联系人", "儿")
    )
    private var screen = Screen.UNBOUND
    private var contact: DemoContact? = null
    private var reason: EndReason? = null
    private var needsRedrawAfterBackground = false

    private val bg get() = ui.background
    private val ink get() = ui.ink
    private val muted get() = ui.muted
    private val accent get() = ui.accentStrong
    private val surface get() = ui.surface
    private val border get() = ui.border
    private val danger get() = ui.danger

    private fun dp(n: Int) = (resources.displayMetrics.density * n + 0.5f).toInt()
    private fun wide() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 650

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Intentionally ignore savedInstanceState: do not resurrect shared-device contacts.
        draw()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        draw()
    }

    override fun onStop() {
        super.onStop()
        // Switching away from a shared-device call view must leave no previous session.
        clear()
        needsRedrawAfterBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (needsRedrawAfterBackground) {
            needsRedrawAfterBackground = false
            draw()
        }
    }

    @Deprecated("Prototype uses a single explicit return-to-HOME action.")
    override fun onBackPressed() = leave()

    private fun clear() {
        screen = Screen.UNBOUND
        contact = null
        reason = null
    }

    private fun leave() {
        clear()
        finish()
    }

    private fun navigate(target: Screen, selected: DemoContact? = contact, ended: EndReason? = null) {
        contact = selected
        reason = ended
        screen = target
        draw()
    }

    private fun shape(
        fill: Int = surface,
        outline: Boolean = false,
        radius: Int = JianquDimensions.cardRadiusDp
    ): GradientDrawable =
        JianquDrawables.shape(this, fill, radius, if (outline) border else null)

    private fun ripple(fill: Int = surface, outlined: Boolean = false): RippleDrawable =
        JianquDrawables.touch(this, fill, JianquDimensions.cardRadiusDp, if (outlined) border else null)

    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false): TextView =
        JianquTypography.text(this, value, size, color, bold)

    private fun line(parent: LinearLayout, message: String, size: Float = JianquTypography.bodySp,
                     color: Int = ink, bold: Boolean = false, space: Int = 10): TextView =
        text(message, size, color, bold).also {
            parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(space) })
        }

    private fun button(label: String, primary: Boolean = false, negative: Boolean = false,
                       onClick: () -> Unit): TextView =
        text(label, JianquTypography.bodySp, if (negative) ui.onDanger else if (primary) ui.onAccent else accent, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(JianquSpacing.space12Dp), dp(JianquSpacing.space10Dp), dp(JianquSpacing.space12Dp), dp(JianquSpacing.space10Dp))
            minHeight = dp(JianquDimensions.minActionHeightDp)
            background = ripple(
                when {
                    negative -> danger
                    primary -> accent
                    else -> surface
                }, !primary && !negative
            )
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun addButton(parent: LinearLayout, label: String, primary: Boolean = false,
                          negative: Boolean = false, space: Int = 10, onClick: () -> Unit) {
        parent.addView(button(label, primary, negative, onClick),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(space) })
    }

    private fun makePanel(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(if (wide()) 22 else 19)
            setPadding(pad, dp(JianquSpacing.space18Dp), pad, dp(JianquSpacing.space20Dp))
            background = shape(surface, outline = true)
        }

    private fun titleFor(): String = when (screen) {
        Screen.UNBOUND -> "还没有绑定家人"
        Screen.CONTACTS -> "选择想联系的家人"
        Screen.CONFIRM -> "准备模拟视频呼叫"
        Screen.RINGING -> "正在等待接听"
        Screen.CONNECTED -> "演示：模拟接通"
        Screen.FINISHED -> when (reason) {
            EndReason.REFUSED -> "模拟：对方已拒接"
            EndReason.NO_ANSWER -> "模拟：无人接听"
            EndReason.OFFLINE -> "模拟：网络不可用"
            EndReason.NO_CAMERA -> "模拟：无摄像头"
            EndReason.PERMISSION_DENIED -> "模拟：权限未授予"
            EndReason.CANCELED -> "已取消模拟呼叫"
            else -> "本次演示已结束"
        }
    }

    private fun explainFor(): String = when (screen) {
        Screen.UNBOUND -> "要给真实家人打视频，需要先绑定设备与家属。现在尚未接入授权服务。"
        Screen.CONTACTS -> "以下全部是虚构演示联系人，不是你的微信好友或手机通讯录。"
        Screen.CONFIRM -> "选择好家人之后，先确认再开始演示。不会拨出真实视频电话。"
        Screen.RINGING -> "这里模拟正在等待接听。没有向微信或任何家人发送呼叫。"
        Screen.CONNECTED -> "只展示通话成功时的页面；没有音视频连接，也没有摄像头画面。"
        Screen.FINISHED -> "这是手动选择的演示结果，不代表实际网络、设备或家人的状态。"
    }

    private fun identity(): LinearLayout = makePanel().apply {
        val icon = FrameLayout(this@FamilyCallActivity).apply {
            background = shape(ui.familyCallIcon, radius = JianquDimensions.iconWellRadiusDp)
            contentDescription = "视频通话演示标志"
        }
        icon.addView(ImageView(this@FamilyCallActivity).apply {
            setImageResource(R.drawable.ic_home_video_camera)
            imageTintList = ColorStateList.valueOf(ink)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        addView(icon, LinearLayout.LayoutParams(dp(66), dp(66)))

        line(this, "家人通话 · 交互原型", JianquTypography.supportingSp, accent, true, 16)
        line(this, titleFor(), if (wide()) JianquTypography.headingCompactSp else JianquTypography.headingLargeSp, ink, true, 10)
        line(this, explainFor(), JianquTypography.bodySp, muted, space = 13)

        if (screen != Screen.UNBOUND && contact != null) {
            val who = contact!!
            line(this, "${who.title}  ·  ${who.relation}", JianquTypography.bodyEmphasisSp, ink, true, 19)
        } else if (screen == Screen.CONTACTS) {
            line(this, "本轮演示：2 位虚构家人", JianquTypography.bodySp, accent, true, 20)
        }
        if (screen == Screen.CONNECTED) {
            line(this, "视频：未采集  ·  声音：未采集", JianquTypography.bodyCompactSp, accent, true, 18)
        }
    }

    private fun contactsPanel(panel: LinearLayout) {
        line(panel, "仅用于演示的家人列表", JianquTypography.headingCompactSp, ink, true, 0)
        line(panel, "这里不会读取微信好友、电话号码或真实家庭资料。", JianquTypography.bodyCompactSp, muted, space = 9)
        contacts.forEach { person ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(JianquSpacing.space12Dp), dp(7), dp(JianquSpacing.space10Dp), dp(7))
                background = ripple(bg, true)
                isFocusable = true
                isClickable = true
                contentDescription = "选择${person.title}，${person.relation}"
                setOnClickListener { navigate(Screen.CONFIRM, person) }
            }
            row.addView(text(person.initial, 19f, accent, true).apply {
                gravity = Gravity.CENTER
                background = shape(ui.familyCallIcon, radius = JianquDimensions.iconWellRadiusDp)
            }, LinearLayout.LayoutParams(dp(50), dp(50)))
            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), 0, 0, 0)
            }
            labels.addView(text(person.title, JianquTypography.bodyLargeSp, ink, true))
            labels.addView(text(person.relation, JianquTypography.supportingSp, muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(JianquSpacing.space4Dp) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(text("›", 27f, accent), LinearLayout.LayoutParams(dp(24), -2))
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(JianquSpacing.space12Dp) })
        }
        addButton(panel, "退出演示并清除联系人", space = 20) { navigate(Screen.UNBOUND, null) }
    }

    private fun scenarios(panel: LinearLayout) {
        line(panel, "请选择模拟结果", JianquTypography.headingCompactSp, ink, true, 0)
        line(panel, "演示状态由你手动选择，不会接通真实家人。", JianquTypography.bodyCompactSp, muted, space = 8)
        val options = listOf(
            "模拟接通" to null,
            "模拟拒接" to EndReason.REFUSED,
            "模拟无人接听" to EndReason.NO_ANSWER,
            "模拟断网" to EndReason.OFFLINE,
            "模拟无摄像头" to EndReason.NO_CAMERA,
            "模拟权限拒绝" to EndReason.PERMISSION_DENIED
        )
        options.chunked(2).forEach { pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, option ->
                row.addView(button(option.first, onClick = {
                    if (option.second == null) navigate(Screen.CONNECTED)
                    else navigate(Screen.FINISHED, ended = option.second)
                }), LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    if (i > 0) leftMargin = dp(JianquSpacing.space8Dp)
                })
            }
            panel.addView(row, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(9) })
        }
        addButton(panel, "取消模拟呼叫", space = 18) {
            navigate(Screen.FINISHED, ended = EndReason.CANCELED)
        }
    }

    private fun actions(): LinearLayout = makePanel().apply {
        when (screen) {
            Screen.UNBOUND -> {
                line(this, "如何与家人联系？", JianquTypography.headingCompactSp, ink, true, 0)
                line(this, "1. 家属通过正式服务授权并绑定设备。", space = 17)
                line(this, "2. 设备确认可以联系哪些家人。", space = 13)
                line(this, "3. 双方具备摄像头、麦克风和通信能力，才可实际发起视频。", space = 13)
                addButton(this, "进入演示模式（虚构家人）", primary = true, space = 25) {
                    navigate(Screen.CONTACTS, null)
                }
                line(this, "当前没有绑定功能，也不能直接呼叫微信好友。", JianquTypography.supportingSp, muted, space = 13)
            }
            Screen.CONTACTS -> contactsPanel(this)
            Screen.CONFIRM -> {
                line(this, "确认发起视频呼叫？", JianquTypography.headingCompactSp, ink, true, 0)
                line(this, "${contact?.title ?: "虚构家人"} · 本地演示", JianquTypography.bodyEmphasisSp, ink, true, 16)
                line(this, "这一步不会发送呼叫、启动摄像头或请求麦克风权限。", JianquTypography.bodySp, muted, space = 15)
                addButton(this, "开始模拟呼叫", primary = true, space = 23) {
                    navigate(Screen.RINGING)
                }
                addButton(this, "重新选择家人") { navigate(Screen.CONTACTS, null) }
            }
            Screen.RINGING -> scenarios(this)
            Screen.CONNECTED -> {
                line(this, "视频画面未接入", JianquTypography.sectionSp, ink, true, 0)
                val blank = text("没有真实视频画面\n没有采集麦克风声音", JianquTypography.bodyEmphasisSp, muted).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(JianquSpacing.space12Dp), dp(JianquSpacing.space16Dp), dp(JianquSpacing.space12Dp), dp(JianquSpacing.space16Dp))
                    background = shape(bg, outline = true)
                }
                addView(blank, LinearLayout.LayoutParams(-1, dp(if (wide()) 120 else 160)).apply {
                    topMargin = dp(JianquSpacing.space16Dp)
                })
                addButton(this, "挂断模拟通话", negative = true, space = 18) {
                    navigate(Screen.FINISHED, ended = EndReason.HUNG_UP)
                }
            }
            Screen.FINISHED -> {
                line(this, "演示结果", JianquTypography.headingCompactSp, ink, true, 0)
                val detail = when (reason) {
                    EndReason.REFUSED -> "演示：家人拒绝接听。没有真实家人收到请求。"
                    EndReason.NO_ANSWER -> "演示：等待超时。没有真实等待或计费。"
                    EndReason.OFFLINE -> "演示：网络断开。未检测或修改本机网络。"
                    EndReason.NO_CAMERA -> "演示：设备没有摄像头。未检测或打开系统摄像头。"
                    EndReason.PERMISSION_DENIED -> "演示：摄像头或麦克风权限被拒绝。本应用未申请这些权限。"
                    EndReason.CANCELED -> "演示：呼叫已取消。没有真实呼叫发送。"
                    EndReason.HUNG_UP -> "演示：已挂断。没有实际音视频通话。"
                    null -> "本轮仅是演示，没有实际音视频通话。"
                }
                line(this, detail, JianquTypography.bodyEmphasisSp, muted, space = 16)
                addButton(this, "重新选择模拟结果", primary = true, space = 22) {
                    navigate(Screen.RINGING)
                }
                addButton(this, "返回演示家人列表") { navigate(Screen.CONTACTS, null) }
                addButton(this, "结束演示，返回桌面") { leave() }
            }
        }
    }

    private fun draw() {
        val isWide = wide()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        root.addView(JianquPageHeader.create(this, "家人通话",
            status = if (isWide) "原型演示 · 未接通微信" else null,
            statusColor = accent, onBack = ::leave), LinearLayout.LayoutParams(-1, -2))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val columns = LinearLayout(this).apply {
            orientation = if (isWide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(if (isWide) 18 else 15), dp(4), dp(if (isWide) 18 else 15), dp(18))
        }
        val intro = identity()
        val panel = actions()
        if (isWide) {
            columns.addView(intro, LinearLayout.LayoutParams(0, -2, 0.92f).apply {
                rightMargin = dp(JianquSpacing.space14Dp)
            })
            columns.addView(panel, LinearLayout.LayoutParams(0, -2, 1.08f))
        } else {
            columns.addView(intro, LinearLayout.LayoutParams(-1, -2))
            columns.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(JianquSpacing.space14Dp)
            })
        }
        scroll.addView(columns)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val disclosure = text("本地模拟流程 · 无真实联系人、呼叫、摄像头或麦克风采集", JianquTypography.captionLargeSp, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(JianquSpacing.space12Dp), 0, dp(JianquSpacing.space12Dp), 0)
        }
        root.addView(disclosure, LinearLayout.LayoutParams(-1, dp(38)))
        setContentView(root)
    }
}
