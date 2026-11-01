package com.patty60723.tearoff.mowan

import com.nlf.calendar.Solar
import com.patty60723.tearoff.TaiwanHolidays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** D-28：節日日期（春節初一到初三、聖誕 12/25、中秋八月十五，閏八月不算） */
class FestivalThemeTest {
    private fun d(s: String) = LocalDate.parse(s)

    /** 2025–2030 每年的春節初一、中秋（手寫的已知日期，不是用同一個函式庫算出來的） */
    private val springDay1 = listOf("2025-01-29", "2026-02-17", "2027-02-06", "2028-01-26", "2029-02-13", "2030-02-03")
    private val moonDay = listOf("2025-10-06", "2026-09-25", "2027-09-15", "2028-10-03", "2029-09-22", "2030-09-12")

    @Test
    fun springIsTheFirstThreeDaysOfTheLunarYear() {
        for (s in springDay1) {
            val first = d(s)
            assertNull("$s 前一天（除夕）不是", FestivalTheme.of(first.minusDays(1)))
            for (i in 0L..2L) assertEquals("$s 初${i + 1}", Festival.SPRING, FestivalTheme.of(first.plusDays(i)))
            assertNull("$s 初四不是", FestivalTheme.of(first.plusDays(3)))
        }
    }

    @Test
    fun moonIsTheFifteenthOfTheEighthMonth() {
        for (s in moonDay) {
            assertEquals(s, Festival.MOON, FestivalTheme.of(d(s)))
            assertNull("$s 前一天", FestivalTheme.of(d(s).minusDays(1)))
            assertNull("$s 後一天", FestivalTheme.of(d(s).plusDays(1)))
        }
    }

    @Test
    fun xmasIsDecemberTwentyFifth() {
        for (y in 2025..2030) {
            assertEquals("$y", Festival.XMAS, FestivalTheme.of(LocalDate.of(y, 12, 25)))
            assertNull("$y 12/24", FestivalTheme.of(LocalDate.of(y, 12, 24)))
            assertNull("$y 12/26", FestivalTheme.of(LocalDate.of(y, 12, 26)))
        }
    }

    /** 閏八月十五不是中秋（找出 1900–2100 所有閏八月十五） */
    @Test
    fun leapEighthMonthIsNotMoonFestival() {
        var leap = 0
        var day = LocalDate.of(1901, 1, 1)
        while (day.year < 2100) {
            val lunar = Solar.fromYmd(day.year, day.monthValue, day.dayOfMonth).lunar
            if (lunar.month == -8 && lunar.day == 15) {
                leap++
                assertNull("$day 是閏八月十五", FestivalTheme.of(day))
            }
            day = day.plusDays(1)
        }
        assertTrue("1900–2100 應該有閏八月", leap > 0)
    }

    /** 不看日曆上印的節日文字：12/25 印的是「行憲紀念日」也一樣是聖誕 */
    @Test
    fun doesNotDependOnTheLabel() {
        val xmas = LocalDate.of(2026, 12, 25)
        val solar = Solar.fromYmd(2026, 12, 25)
        val labels = TaiwanHolidays.holidays(xmas, solar.lunar, null)
        assertTrue("假日清單印的是行憲紀念日：$labels", "行憲紀念日" in labels && "聖誕節" !in labels)
        assertEquals(Festival.XMAS, FestivalTheme.of(xmas))
    }

    /** 2025–2030 每一天：結果跟日曆印的節日一致（只是對照，[FestivalTheme] 沒有用到這些文字） */
    @Test
    fun agreesWithTheCalendarEveryDay() {
        var day = LocalDate.of(2025, 1, 1)
        var count = 0
        while (day.year <= 2030) {
            val lunar = Solar.fromYmd(day.year, day.monthValue, day.dayOfMonth).lunar
            val labels = TaiwanHolidays.holidays(day, lunar, null) + TaiwanHolidays.observances(day, lunar)
            val want = when {
                "春節" in labels -> Festival.SPRING
                "中秋節" in labels -> Festival.MOON
                "聖誕節" in labels -> Festival.XMAS
                else -> null
            }
            assertEquals("$day $labels", want, FestivalTheme.of(day))
            if (want != null) count++
            day = day.plusDays(1)
        }
        assertEquals("6 年 × (3 + 1 + 1) 天", 30, count)
    }

    /** 跨午夜：同一天任何時間結果一樣；過了 00:00 就看新的日期 */
    @Test
    fun midnight() {
        val eve = LocalDateTime.of(2027, 2, 5, 23, 59, 59)
        assertNull("除夕 23:59", FestivalTheme.of(eve.toLocalDate()))
        assertEquals("初一 00:00", Festival.SPRING, FestivalTheme.of(eve.plusSeconds(1).toLocalDate()))
        val third = LocalDateTime.of(2027, 2, 8, 23, 59, 59)
        assertEquals("初三 23:59", Festival.SPRING, FestivalTheme.of(third.toLocalDate()))
        assertNull("初四 00:00", FestivalTheme.of(third.plusSeconds(1).toLocalDate()))
    }
}
