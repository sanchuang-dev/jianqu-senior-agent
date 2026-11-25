package cn.sanchuang.jianqu.system.apps

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAppSearchTest {
    @Test
    fun blankQueryShowsEveryApp() {
        assertTrue(LauncherAppSearch.matches("", "相册", "com.example.gallery"))
        assertTrue(LauncherAppSearch.matches("   ", "Clock", "com.android.deskclock"))
    }

    @Test
    fun labelMatchIsCaseInsensitive() {
        assertTrue(LauncherAppSearch.matches("  alBUM  ", "Family Album", "com.example.viewer"))
    }

    @Test
    fun packageNameCanMatchWhenLabelDoesNot() {
        assertTrue(LauncherAppSearch.matches("deskclock", "时钟", "com.android.deskclock"))
    }

    @Test
    fun unrelatedQueryDoesNotMatch() {
        assertFalse(LauncherAppSearch.matches("camera", "时钟", "com.android.deskclock"))
    }
}
