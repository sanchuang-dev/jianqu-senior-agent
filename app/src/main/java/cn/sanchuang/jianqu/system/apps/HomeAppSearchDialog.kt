package cn.sanchuang.jianqu.system.apps

import android.app.Activity
import android.app.Dialog
import android.content.pm.LauncherActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cn.sanchuang.jianqu.JianquColors
import cn.sanchuang.jianqu.JianquDrawables
import cn.sanchuang.jianqu.JianquTypography
import cn.sanchuang.jianqu.R

/** HOME search is a view of the same real system catalog, not another app source. */
internal class HomeAppSearchDialog(
    private val activity: Activity,
    private val onOpen: (LauncherActivityInfo) -> Unit,
    private val onRetry: () -> Unit
) : Dialog(activity) {
    private val ui = JianquColors(activity)
    private lateinit var input: EditText
    private lateinit var results: LinearLayout
    private var entries: List<LauncherActivityInfo>? = null
    private var failed = false
    private var columns = 1
    private fun dp(n: Int) = (activity.resources.displayMetrics.density * n + .5f).toInt()
    private fun row() = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String, size: Float, color: Int = ui.ink) =
        JianquTypography.text(activity, value, size, color, false)
    private fun action(value: String, description: String, click: () -> Unit) =
        text(value, 24f, ui.muted).apply {
            gravity = Gravity.CENTER
            contentDescription = description
            isFocusable = true
            background = JianquDrawables.touch(activity, ui.surfaceSecondary, 12)
            setOnClickListener { click() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val outer = FrameLayout(activity)
        val panel = column().apply {
            background = JianquDrawables.shape(activity, activity.getColor(R.color.jq_home_search_surface), 24, ui.border)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
        }
        outer.addView(panel, FrameLayout.LayoutParams(-1, -1).apply {
            val margin = dp(if (activity.resources.configuration.screenWidthDp >= 660) 20 else 10)
            setMargins(margin, margin, margin, margin)
        })
        val header = row()
        header.addView(action("‹", "关闭搜索") { dismiss() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        input = EditText(activity).apply {
            setSingleLine(true)
            hint = "搜索已安装应用"
            contentDescription = "搜索已安装应用"
            textSize = 20f
            setTextColor(ui.ink)
            setHintTextColor(ui.muted)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            background = null
            setPadding(dp(12), 0, dp(8), 0)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) { render() }
            })
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    activity.getSystemService(InputMethodManager::class.java)
                        .hideSoftInputFromWindow(windowToken, 0)
                    true
                } else false
            }
        }
        header.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(action("×", "清除搜索") { input.text.clear() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        panel.addView(header, LinearLayout.LayoutParams(-1, -2))
        panel.addView(View(activity).apply { setBackgroundColor(ui.border) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        results = column()
        scroll.addView(results, FrameLayout.LayoutParams(-1, -2))
        panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        scroll.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val count = ((right - left) / dp(100)).coerceIn(1, 10)
            if (count != columns) {
                columns = count
                render()
            }
        }
        setContentView(outer)
        setCanceledOnTouchOutside(true)
        outer.setOnClickListener { dismiss() }
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(.16f)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        render()
    }

    override fun show() {
        super.show()
        window?.setLayout(-1, -1)
        input.requestFocus()
        input.post {
            if (isShowing) activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun showApps(apps: List<LauncherActivityInfo>) {
        entries = apps
        failed = false
        render()
    }
    fun showError() {
        entries = null
        failed = true
        render()
    }

    private fun render() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        val apps = entries
        if (apps == null) {
            results.addView(text(if (failed) activity.getString(R.string.apps_load_error) else "正在读取…", 16f, ui.muted))
            if (failed) results.addView(action("重试", "重试读取应用") {
                failed = false
                render()
                onRetry()
            }, LinearLayout.LayoutParams(dp(88), dp(48)))
            return
        }
        val query = input.text.toString()
        val matches = apps.filter { LauncherAppSearch.matches(query, it.label.toString(), it.componentName.packageName) }
        if (matches.isEmpty()) {
            results.addView(text(if (query.isBlank()) activity.getString(R.string.empty_apps) else "没有找到匹配的应用", 16f, ui.muted).apply {
                setPadding(dp(8), dp(16), 0, 0)
            })
            return
        }
        matches.chunked(columns).forEach { group ->
            val row = row().apply { gravity = Gravity.TOP }
            group.forEach { entry ->
                val item = column().apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(activity.getColor(R.color.jq_ripple)),
                        null, JianquDrawables.shape(activity, Color.WHITE, 12))
                    setPadding(dp(4), dp(8), dp(4), dp(6))
                    isFocusable = true
                    contentDescription = "打开 " + entry.label
                    setOnClickListener { onOpen(entry) }
                }
                item.addView(ImageView(activity).apply {
                    setImageDrawable(entry.getIcon(0))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(dp(54), dp(54)))
                item.addView(text(entry.label.toString(), 14f).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(4), 0, 0)
                }, LinearLayout.LayoutParams(-1, -2))
                row.addView(item, LinearLayout.LayoutParams(0, dp(94), 1f))
            }
            repeat(columns - group.size) { row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f)) }
            results.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }
}
