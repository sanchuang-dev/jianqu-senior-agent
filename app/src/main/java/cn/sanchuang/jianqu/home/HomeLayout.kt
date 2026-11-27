package cn.sanchuang.jianqu.home

/** HOME-only geometry. Shared colors, typography and glass materials remain in the design system. */
internal data class HomeLayout(val landscape: Boolean, val widthDp: Int) {
    val wide = landscape && widthDp >= 660
    val sidePaddingDp = if (landscape) 20 else 16
    val greetingSp = if (landscape) 28f else 34f
    val greetingGapDp = if (landscape) 10 else 24
    val panelGapDp = if (landscape) 12 else 28
    val panelMinHeightDp = if (landscape) 104 else 184
    val panelIconDp = if (landscape) 64 else 72
    val clockSp = if (landscape) 42f else 52f
    val weatherSp = if (landscape) 14 else 16
    val companionSp = if (landscape) 15f else 18f
    val columns = if (landscape && widthDp >= 800) 8 else if (widthDp >= 480) 4 else 2
    val tileGapDp = 8
    val tileHeightDp = if (landscape) 90 else
        ((widthDp - 2 * sidePaddingDp - (columns - 1) * tileGapDp) / columns).coerceIn(108, 164)
    val tileRowGapDp = if (landscape) 8 else 12
    val tileIconDp = if (landscape) 48 else 56
    val tileLabelSp = if (landscape) 15f else 16f
    val tileLabelGapDp = if (landscape) 3 else 8
}
