package cn.sanchuang.jianqu

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.TextView

internal object JianquTypography {
    const val headingLargeSp = 25f
    const val headingCompactSp = 21f
    const val bodySp = 16f
    const val supportingSp = 14f
    const val pageTitleSp = 32f
    const val sectionSp = 22f

    /** Daylight Living uses serif display titles; Android system fallback covers CJK. */
    fun display(context: Context, value: String, size: Float, color: Int): TextView =
        text(context, value, size, color, false).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }

    fun text(context: Context, value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            if (bold) setTypeface(null, Typeface.BOLD)
        }
}
