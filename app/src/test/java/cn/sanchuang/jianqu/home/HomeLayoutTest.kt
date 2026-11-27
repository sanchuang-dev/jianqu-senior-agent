package cn.sanchuang.jianqu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test fun approvedPortraitUsesFourNearlySquareTiles() {
        val layout = HomeLayout(false, 512)
        assertEquals(4, layout.columns)
        assertEquals(114, layout.tileHeightDp)
        assertEquals(184, layout.panelMinHeightDp)
        assertEquals(72, layout.panelIconDp)
    }

    @Test fun approvedLandscapeRemainsCompact() {
        val layout = HomeLayout(true, 860)
        assertTrue(layout.wide)
        assertEquals(8, layout.columns)
        assertEquals(90, layout.tileHeightDp)
        assertEquals(104, layout.panelMinHeightDp)
        assertEquals(64, layout.panelIconDp)
    }

    @Test fun columnBreakpointsAndLargePortraitLimitRemainStable() {
        assertEquals(2, HomeLayout(false, 479).columns)
        assertEquals(4, HomeLayout(false, 480).columns)
        assertEquals(4, HomeLayout(true, 799).columns)
        assertEquals(8, HomeLayout(true, 800).columns)
        assertEquals(164, HomeLayout(false, 1200).tileHeightDp)
    }
}
