package com.patty60723.tearoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AlmanacTest {
    private fun page(y: Int, m: Int, d: Int) = Almanac.page(LocalDate.of(y, m, d))

    @Test
    fun lunarNewYear2026() {
        val eve = page(2026, 2, 16)
        assertTrue(eve.festivals.contains("除夕"))
        assertTrue(eve.isRedDay)

        val newYear = page(2026, 2, 17)
        assertEquals("正月初一", newYear.lunarDate)
        assertEquals("丙午年", newYear.ganZhiYear)
        assertEquals("馬", newYear.zodiac)
        assertEquals("春節", newYear.label)
    }

    @Test
    fun lunarFestivals2026() {
        assertEquals("端午節", page(2026, 6, 19).label)
        assertEquals("中秋節", page(2026, 9, 25).label)
        assertEquals("清明節", page(2026, 4, 5).label)
        assertEquals("清明", page(2026, 4, 5).solarTerm)
    }

    @Test
    fun redDays() {
        // 2026-10-21 是星期三，不是假日
        val wed = page(2026, 10, 21)
        assertEquals("星期三", wed.weekday)
        assertFalse(wed.isRedDay)
        assertTrue(page(2026, 10, 24).isRedDay) // 星期六
        assertTrue(page(2026, 10, 10).isRedDay)
        assertEquals("國慶日", page(2026, 10, 10).label)
        assertEquals("母親節", page(2026, 5, 10).label)
    }

    @Test
    fun yiJiIsTraditional() {
        val simplified = "开动纳订问归进门坟盖财买会亲医学发无龙马鸡猪东闰腊节宁"
        var day = LocalDate.of(2026, 1, 1)
        repeat(366) {
            val p = Almanac.page(day)
            assertTrue(p.yi.isNotEmpty())
            for (text in p.yi + p.ji + listOfNotNull(p.solarTerm) + p.chong + p.sha + p.lunarDate) {
                assertTrue("$day: $text", text.none { it in simplified })
            }
            day = day.plusDays(1)
        }
    }

    @Test
    fun lunarDayNames() {
        assertEquals("初一", Almanac.lunarDayName(1))
        assertEquals("初十", Almanac.lunarDayName(10))
        assertEquals("十五", Almanac.lunarDayName(15))
        assertEquals("二十", Almanac.lunarDayName(20))
        assertEquals("廿一", Almanac.lunarDayName(21))
        assertEquals("三十", Almanac.lunarDayName(30))
    }
}
