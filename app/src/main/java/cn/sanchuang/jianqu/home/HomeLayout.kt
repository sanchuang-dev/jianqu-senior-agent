package cn.sanchuang.jianqu.home

import cn.sanchuang.jianqu.JianquSpacing

import cn.sanchuang.jianqu.JianquTypography

/** HOME-only geometry. Shared colors, typography and glass materials remain in the design system. */
internal data class HomeLayout(val landscape: Boolean, val widthDp: Int) {
    val wide = landscape && widthDp >= 660
    val sidePaddingDp = if (landscape) JianquSpacing.space20Dp else JianquSpacing.space16Dp
    val greetingSp = if (landscape) JianquTypography.displayCompactSp else JianquTypography.displayLargeSp
    val greetingGapDp = if (landscape) JianquSpacing.space10Dp else JianquSpacing.space24Dp
    val panelGapDp = if (landscape) JianquSpacing.space12Dp else JianquSpacing.space28Dp
    val panelMinHeightDp = if (landscape) 104 else 184
    val panelIconDp = if (landscape) 64 else 72
    val clockSp = if (landscape) 42f else 52f
    val weatherSp = (if (landscape) JianquTypography.supportingSp else JianquTypography.bodySp).toInt()
    val companionSp = if (landscape) JianquTypography.bodyCompactSp else JianquTypography.bodyLargeSp
    val columns = if (landscape && widthDp >= 800) 8 else if (widthDp >= 480) 4 else 2
    val tileGapDp = JianquSpacing.space8Dp
    val tileHeightDp = if (landscape) 90 else
        ((widthDp - 2 * sidePaddingDp - (columns - 1) * tileGapDp) / columns).coerceIn(108, 164)
    val tileRowGapDp = if (landscape) JianquSpacing.space8Dp else JianquSpacing.space12Dp
    val tileIconDp = if (landscape) 48 else 56
    val tileLabelSp = if (landscape) JianquTypography.bodyCompactSp else JianquTypography.bodySp
    val tileLabelGapDp = if (landscape) 3 else JianquSpacing.space8Dp
}
