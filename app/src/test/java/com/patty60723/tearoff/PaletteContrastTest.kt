package com.patty60723.tearoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P7-7：每個月的 `deep`（中文月份、星期、日期數字、封面年份）對紙色（#FBF8F2）的對比（純 JVM，不畫圖）。
 * 門檻 4.5:1；設計目標 4.8:1 只記錄（1 月 4.71、4 月 4.72 低於目標，不在 P7-7 範圍）。
 * 這是「對紙色」：疊在山丘上的實際對比另外量（WIDE 日期，獨立追蹤；8 月 #84693D 在山丘上仍 < 4.5）。
 */
class PaletteContrastTest {
    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test
    fun deepOnPaperIsAtLeast45() {
        val below = (1..12).filter { ratio(Palettes.forMonth(it).deep, Palettes.PAPER) < 4.5 }
        assertEquals("deep 對紙色未達 4.5 的月份", emptyList<Int>(), below)
    }

    @Test
    fun augustDeepIsTheApprovedColor() {
        assertEquals(0xFF84693D.toInt(), Palettes.forMonth(8).deep)
        assertTrue(ratio(Palettes.forMonth(8).deep, Palettes.PAPER) in 4.86..4.88)
    }
}
