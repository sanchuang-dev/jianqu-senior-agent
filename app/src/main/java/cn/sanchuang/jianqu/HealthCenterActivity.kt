package cn.sanchuang.jianqu

import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Session-only check-in preview, not a personal medical or health record. */
internal class HealthCenterActivity : ServicePreviewActivity() {
    private val tasks = listOf("日常活动打卡", "康复计划打卡", "作息记录打卡")
    private val details = listOf("记录今日活动", "记录专业人员安排的康复项目", "记录今日作息")
    private val rehabTasks = listOf("上肢活动", "平衡训练", "放松练习")
    private val checked = mutableSetOf<Int>()
    private val rehabChecked = mutableSetOf<Int>()
    private var rehab = false
    private val green = Color.rgb(63, 114, 81)
    override fun onStop() {
        super.onStop()
        checked.clear()
        rehabChecked.clear()
        rehab = false
    }
    override fun onResume() { super.onResume(); render() }
    @Deprecated("Prototype navigation")
    override fun onBackPressed() {
        if (rehab) { rehab = false; render() } else finish()
    }

    private fun checkRow(parent: LinearLayout, title: String, desc: String, done: Boolean, toggle: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(60); setPadding(dp(12), dp(8), dp(12), dp(8))
            background = ui.touch(this@HealthCenterActivity, ui.surface, 12)
            isClickable = true; isFocusable = true
            contentDescription = "$title，${if (done) "已完成" else "待打卡"}"
            setOnClickListener { toggle(); render() }
        }
        val copy = column().apply {
            line(this, title, 16f, ui.ink, true, 0)
            line(this, desc, 12f, ui.muted, top = 5)
        }
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(if (done) "✓ 已完成" else "打卡", 14f, green, true))
        add(parent, row, 9)
    }
    private fun progress(parent: LinearLayout, count: Int, total: Int) {
        line(parent, "$count / $total", 44f, green, true, 8)
        line(parent, if (count == total) "示例计划已完成" else "今日打卡进度", 15f, green, top = 4)
        val segments = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (i in 0 until total) addView(View(this@HealthCenterActivity).apply {
                background = ui.shape(this@HealthCenterActivity,
                    if (i < count) green else Color.rgb(205, 222, 205), 6)
            }, LinearLayout.LayoutParams(0, dp(9), 1f).apply { if (i > 0) leftMargin = dp(5) })
        }
        add(parent, segments, 15)
    }
    private fun toggle(set: MutableSet<Int>, i: Int) { if (!set.add(i)) set.remove(i) }

    override fun render() {
        if (rehab) {
            val hero = panel(ui.healthIcon, null).apply {
                line(this, "元气 · 康复计划", 17f, green, true, 0)
                line(this, "今日康复记录", 27f, ui.ink, true, 9)
                progress(this, rehabChecked.size, rehabTasks.size)
                line(this, "仅为示例，不是训练处方。实际项目和强度由专业人员确定。", 14f, ui.muted, top = 16)
            }
            val body = column().apply {
                line(this, "训练项目示例", 24f, ui.ink, true, 0)
                rehabTasks.forEachIndexed { i, title ->
                    checkRow(this, title, "演示完成记录", i in rehabChecked) {
                        toggle(rehabChecked, i)
                        if (rehabChecked.size == rehabTasks.size) checked.add(1) else checked.remove(1)
                    }
                }
                action(this, "返回今日计划", "查看健康打卡进度", R.drawable.ic_home_hand_heart, ui.healthIcon) {
                    rehab = false; render()
                }
            }
            shell("康复训练", hero, body) { rehab = false; render() }
            return
        }
        val hero = panel(ui.healthIcon, null).apply {
            line(this, "元气 · 健康关怀", 17f, green, true, 0)
            line(this, "今日健康计划", 27f, ui.ink, true, 9)
            progress(this, checked.size, tasks.size)
            tasks.forEachIndexed { i, title ->
                checkRow(this, title, details[i], i in checked) { toggle(checked, i) }
            }
            line(this, "示例计划 · 不保存个人健康记录", 12f, ui.muted, top = 10)
        }
        val body = column().apply {
            line(this, "健康服务", 24f, ui.ink, true, 0)
            action(this, "康复训练", "查看康复计划示例", R.drawable.ic_home_stethoscope, ui.healthIcon) {
                rehab = true; render()
            }
            action(this, "健康课程", "健康科普与讲座", R.drawable.ic_home_film_strip, ui.videoIcon) {
                startActivity(Intent(this@HealthCenterActivity, ContentGalleryActivity::class.java)
                    .putExtra(ContentGalleryActivity.EXTRA_SECTION, ContentGalleryActivity.HEALTH))
            }
            action(this, "SOS 紧急求助", "独立入口 · 当前不可真实求救", R.drawable.ic_home_hand_heart, ui.storeIcon, ui.danger) {
                startActivity(Intent(this@HealthCenterActivity, SosActivity::class.java))
            }
        }
        shell("健康中心", hero, body)
    }
}
