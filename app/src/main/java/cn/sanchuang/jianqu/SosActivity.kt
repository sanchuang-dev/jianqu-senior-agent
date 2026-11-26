package cn.sanchuang.jianqu

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * JQ-011 SOS interaction prototype. Every state is purely local and manual.
 *
 * No emergency request, alarm, call, SMS, notification, location, or network
 * operation is performed. A real responder and reliable receipt chain must be
 * decided and tested independently before this becomes a live SOS feature.
 */
class SosActivity : Activity() {
    private enum class Step {
        INTRO, CONFIRM, SIMULATED_PROCESSING, NOT_DELIVERED, NO_NETWORK,
        NO_CONTACT, NO_RESPONSE, CANCELED
    }

    private val ui = JianquColors(this)
    private val ink get() = ui.ink
    private val muted get() = ui.muted
    private val danger get() = ui.danger
    private val accent get() = ui.accentStrong

    private val warningBackground get() = ui.dangerSurface
    private val warningBorder get() = ui.dangerBorder
    private var step = Step.INTRO
    private var redrawAfterBackground = false

    private fun dp(value: Int) =
        (resources.displayMetrics.density * value + 0.5f).toInt()

    private fun wide() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            resources.configuration.screenWidthDp >= 650

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never restore another shared-device user's simulated distress state.
        step = Step.INTRO
        render()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    override fun onStop() {
        super.onStop()
        step = Step.INTRO
        redrawAfterBackground = true
    }

    override fun onResume() {
        super.onResume()
        if (redrawAfterBackground) {
            redrawAfterBackground = false
            render()
        }
    }

    @Deprecated("SOS prototype uses explicit safe return to HOME.")
    override fun onBackPressed() {
        leave()
    }

    private fun leave() {
        step = Step.INTRO
        finish()
    }

    private fun navigate(target: Step) {
        step = target
        render()
    }

    private fun label(value: String, size: Float = JianquTypography.bodySp, color: Int = ink,
                      bold: Boolean = false): TextView =
        JianquTypography.text(this, value, size, color, bold)

    private fun line(parent: LinearLayout, value: String, size: Float = JianquTypography.bodySp,
                     color: Int = ink, bold: Boolean = false, marginTop: Int = 11): TextView =
        label(value, size, color, bold).also {
            parent.addView(it, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(marginTop)
            })
        }

    private fun button(text: String, primary: Boolean = false,
                       onClick: () -> Unit): TextView =
        label(text, 16f, if (primary) ui.onAccent else accent, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(JianquDimensions.minActionHeightDp)
            setPadding(dp(11), dp(9), dp(11), dp(9))
            background = JianquDrawables.touch(
                this@SosActivity,
                if (primary) accent else ui.surface,
                JianquDimensions.cardRadiusDp,
                if (primary) null else ui.border
            )
            isClickable = true
            isFocusable = true
            contentDescription = text
            setOnClickListener { onClick() }
        }

    private fun action(parent: LinearLayout, text: String, primary: Boolean = false,
                       top: Int = 11, onClick: () -> Unit) {
        parent.addView(button(text, primary, onClick),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val padding = dp(if (wide()) 22 else 19)
        setPadding(padding, dp(21), padding, dp(22))
        background = JianquDrawables.shape(this@SosActivity, ui.surface,
            JianquDimensions.cardRadiusDp, ui.border)
    }

    private fun warning() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(13), dp(17), dp(16))
        background = JianquDrawables.shape(this@SosActivity, warningBackground,
            JianquDimensions.cardRadiusDp, warningBorder)
        line(this, "SOS 当前不可用于真实求救", 20f, danger, true, 0)
        line(this, "未接入报警、护理站或家属通知。任何演示结果都不代表救助已经送达。",
            15f, danger, false, 9)
    }

    private fun guidance() = panel().apply {
        line(this, "如果现在真的需要帮助", JianquTypography.headingCompactSp, ink, true, 0)
        line(this, "立即呼叫附近的工作人员或其他在场的人。", 16f, ink, marginTop = 17)
        line(this, "请使用能够正常拨号的可靠电话，拨打所在地的急救电话。",
            16f, ink, marginTop = 13)
        line(this, "本机未确认有电话功能，请勿依赖这里的 SOS 按钮等待救援。",
            15f, danger, true, 15)
        line(this, "未来由家属还是护理站接应、回执如何确认、备用求助渠道等，仍须另行决定和验证。",
            14f, muted, marginTop = 18)
    }

    private fun stepTitle() = when (step) {
        Step.INTRO -> "求助功能演示"
        Step.CONFIRM -> "请再次确认：仅进入演示"
        Step.SIMULATED_PROCESSING -> "模拟请求处理中 · 实际未发送"
        Step.NOT_DELIVERED -> "模拟结果：求助未送达"
        Step.NO_NETWORK -> "模拟结果：网络不可用"
        Step.NO_CONTACT -> "模拟结果：无响应联系人"
        Step.NO_RESPONSE -> "模拟结果：长时间无人回应"
        Step.CANCELED -> "已取消模拟 · 未发送求助"
    }

    private fun stepMessage() = when (step) {
        Step.INTRO ->
            "下面可以体验两步确认与异常回退，但这只是界面操作演示，无法发起真实求助。"
        Step.CONFIRM ->
            "你正在确认的是“开始界面演示”，不是向家属、护理站或急救中心发送消息。"
        Step.SIMULATED_PROCESSING ->
            "这里仅展示假设正在请求的界面状态。本机没有发送消息、拨号或等待任何真实回执。"
        Step.NOT_DELIVERED ->
            "此为手动选择的失败样例。真实服务尚未开通，没有消息发出或送达。"
        Step.NO_NETWORK ->
            "此为手动选择的断网样例，并非检测到真实网络异常。本机没有尝试发送求助。"
        Step.NO_CONTACT ->
            "此为手动选择的无联系人样例。尚未配置真实家属或护理站接应人。"
        Step.NO_RESPONSE ->
            "此为手动选择的无人回应样例。没有实际联系人收到请求，也没有真实等待或超时检测。"
        Step.CANCELED ->
            "操作已退出演示。没有呼叫电话、发送短信或通知任何人。"
    }

    private fun scenarioChoice(parent: LinearLayout, first: String,
                               firstResult: Step, second: String, secondResult: Step) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button(first) { navigate(firstResult) },
            LinearLayout.LayoutParams(0, dp(JianquDimensions.minActionHeightDp), 1f))
        row.addView(button(second) { navigate(secondResult) },
            LinearLayout.LayoutParams(0, dp(JianquDimensions.minActionHeightDp), 1f).apply {
                leftMargin = dp(9)
            })
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(10)
        })
    }

    private fun process() = panel().apply {
        line(this, "原型演示 · 没有真实求助请求", 14f, danger, true, 0)
        line(this, stepTitle(), JianquTypography.headingCompactSp, ink, true, 12)
        line(this, stepMessage(), 16f, muted, marginTop = 16)

        when (step) {
            Step.INTRO -> {
                action(this, "查看两步确认演示", primary = true, top = 22) {
                    navigate(Step.CONFIRM)
                }
                action(this, "不演示，返回桌面") { leave() }
            }
            Step.CONFIRM -> {
                action(this, "确认仅模拟，不发送求助", primary = true, top = 22) {
                    navigate(Step.SIMULATED_PROCESSING)
                }
                action(this, "取消演示", top = 11) { navigate(Step.CANCELED) }
            }
            Step.SIMULATED_PROCESSING -> {
                line(this, "手动选择一个模拟结果", 17f, ink, true, 22)
                line(this, "全部为假设状态，不代表已检查设备或联系人。", 14f, muted, marginTop = 8)
                scenarioChoice(this, "模拟未送达", Step.NOT_DELIVERED,
                    "模拟网络不可用", Step.NO_NETWORK)
                scenarioChoice(this, "模拟无联系人", Step.NO_CONTACT,
                    "模拟无人回应", Step.NO_RESPONSE)
                action(this, "取消模拟请求", top = 17) { navigate(Step.CANCELED) }
            }
            else -> {
                line(this, "如需真实救助，请立即使用左侧／上方所列的可靠方式。", 15f, danger,
                    true, 19)
                action(this, "重新开始演示", top = 22) { navigate(Step.INTRO) }
                action(this, "返回桌面", primary = true, top = 11) { leave() }
            }
        }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(5), dp(16), dp(5))
        addView(button("‹") { leave() }.apply {
            textSize = 31f
            contentDescription = "返回桌面"
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        addView(label("SOS 紧急求助", 23f, ink, true),
            LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                leftMargin = dp(14)
            })
        if (wide()) addView(label("未接入真实救援 · 仅演示", 14f, danger, true))
    }

    private fun render() {
        val horizontal = wide()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.background)
        }
        root.addView(header(), LinearLayout.LayoutParams(-1, dp(72)))

        val scroller = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(if (horizontal) 18 else 15), dp(3),
                dp(if (horizontal) 18 else 15), dp(20))
        }

        content.addView(warning(), LinearLayout.LayoutParams(-1, -2))

        val columns = LinearLayout(this).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        if (horizontal) {
            columns.addView(guidance(), LinearLayout.LayoutParams(0, -2, .84f).apply {
                rightMargin = dp(14)
            })
            columns.addView(process(), LinearLayout.LayoutParams(0, -2, 1.16f))
        } else {
            columns.addView(guidance(), LinearLayout.LayoutParams(-1, -2))
            columns.addView(process(), LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(13)
            })
        }
        content.addView(columns, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("本地 UI 原型 · 未发送、未报警、未通知任何人", 13f, danger).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
        }, LinearLayout.LayoutParams(-1, dp(38)))

        setContentView(root)
    }
}
