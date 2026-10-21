package com.patty60723.tearoff

import com.nlf.calendar.Lunar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 台灣的節日。這裡只算得出「規則固定」的日子；
 * 補假和調整放假每年由人事行政總處公布，之後再用資料檔補上。
 */
object TaiwanHolidays {
    /** 國定放假日，日期印紅字。 */
    fun holidays(date: LocalDate, lunar: Lunar, solarTerm: String?): List<String> {
        val result = mutableListOf<String>()
        val md = date.monthValue * 100 + date.dayOfMonth
        when (md) {
            101 -> result += "元旦"
            228 -> result += "和平紀念日"
            404 -> result += "兒童節"
            501 -> result += "勞動節"
            1010 -> result += "國慶日"
        }
        // 2025 年起恢復放假
        if (date.year >= 2025) when (md) {
            928 -> result += "教師節"
            1025 -> result += "光復節"
            1225 -> result += "行憲紀念日"
        }
        if (solarTerm == "清明") result += "清明節"

        val month = lunar.month // 閏月是負數，閏月不過節
        val day = lunar.day
        when {
            month == 1 && day == 1 -> result += "春節"
            month == 1 && day in 2..3 -> result += "春節"
            month == 5 && day == 5 -> result += "端午節"
            month == 8 && day == 15 -> result += "中秋節"
        }
        val tomorrow = lunar.next(1)
        if (tomorrow.month == 1 && tomorrow.day == 1) result += "除夕"
        return result
    }

    /** 不放假但日曆上會印的節日。 */
    fun observances(date: LocalDate, lunar: Lunar): List<String> {
        val result = mutableListOf<String>()
        when (date.monthValue * 100 + date.dayOfMonth) {
            214 -> result += "情人節"
            312 -> result += "植樹節"
            808 -> result += "父親節"
            1225 -> result += "聖誕節"
            1231 -> result += "跨年夜"
        }
        val mothersDay = date.withDayOfMonth(1).withMonth(5)
            .with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY))
        if (date == mothersDay) result += "母親節"

        when (lunar.month * 100 + lunar.day) {
            115 -> result += "元宵節"
            707 -> result += "七夕"
            715 -> result += "中元節"
            909 -> result += "重陽節"
            1208 -> result += "臘八"
            1216 -> result += "尾牙"
        }
        return result
    }
}
