package cn.sanchuang.jianqu

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView

internal object JianquTypography {
    const val captionSp = 12f
    const val captionLargeSp = 13f
    const val supportingSp = 14f
    const val bodyCompactSp = 15f
    const val bodySp = 16f
    const val bodyEmphasisSp = 17f
    const val bodyLargeSp = 18f
    const val headingSmallSp = 20f
    const val headingCompactSp = 21f
    const val sectionSp = 22f
    const val toolbarTitleSp = 23f
    const val titleCompactSp = 24f
    const val headingLargeSp = 25f
    const val displayCompactSp = 28f
    const val navigationTitleSp = titleCompactSp
    const val pageTitleSp = 32f
    const val displayLargeSp = 34f

    fun displayTypeface(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun applyWeight(view: TextView, bold: Boolean) {
        view.setTypeface(null, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** Display titles and clock use the system sans-serif family; Android fallback covers CJK. */
    fun display(context: Context, value: String, size: Float, color: Int): TextView =
        text(context, value, size, color, false).apply {
            typeface = displayTypeface()
        }

    fun text(context: Context, value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            if (bold) applyWeight(this, true)
        }
}
