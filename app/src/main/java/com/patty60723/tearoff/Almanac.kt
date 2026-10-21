package com.patty60723.tearoff

import com.nlf.calendar.Solar
import java.time.DayOfWeek
import java.time.LocalDate

/** 一頁日曆上要印的所有東西。 */
data class DayPage(
    val date: LocalDate,
    /** 星期三 */
    val weekday: String,
    /** 九月十一（閏月為「閏六月初三」） */
    val lunarDate: String,
    /** 丙午年 */
    val ganZhiYear: String,
    /** 馬 */
    val zodiac: String,
    /** 當天的節氣，沒有就是 null */
    val solarTerm: String?,
    /** 當天的節日（放假與不放假的都算），依重要程度排序 */
    val festivals: List<String>,
    /** 台灣的國定放假日或週末，日期印紅字 */
    val isRedDay: Boolean,
    val yi: List<String>,
    val ji: List<String>,
    /** 沖牛（丁丑） */
    val chong: String,
    /** 煞西 */
    val sha: String,
) {
    /** 日期下面那一小行：節日優先，再來是節氣。 */
    val label: String? get() = festivals.firstOrNull() ?: solarTerm
}

object Almanac {
    private val WEEKDAYS = mapOf(
        DayOfWeek.MONDAY to "星期一", DayOfWeek.TUESDAY to "星期二",
        DayOfWeek.WEDNESDAY to "星期三", DayOfWeek.THURSDAY to "星期四",
        DayOfWeek.FRIDAY to "星期五", DayOfWeek.SATURDAY to "星期六",
        DayOfWeek.SUNDAY to "星期日",
    )
    private val LUNAR_MONTHS = arrayOf("", "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "臘")
    private val ZODIAC = arrayOf("鼠", "牛", "虎", "兔", "龍", "蛇", "馬", "羊", "猴", "雞", "狗", "豬")

    fun lunarDayName(day: Int): String {
        val digits = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        return when {
            day <= 10 -> "初" + digits[day]
            day < 20 -> "十" + digits[day - 10]
            day == 20 -> "二十"
            day < 30 -> "廿" + digits[day - 20]
            else -> "三十"
        }
    }

    fun page(date: LocalDate): DayPage {
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar
        val month = lunar.month
        val lunarDate = (if (month < 0) "閏" else "") + LUNAR_MONTHS[kotlin.math.abs(month)] + "月" +
            lunarDayName(lunar.day)
        val solarTerm = lunar.jieQi.takeIf { it.isNotEmpty() }?.let(Hant::convert)
        val holidays = TaiwanHolidays.holidays(date, lunar, solarTerm)
        val observances = TaiwanHolidays.observances(date, lunar)
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

        return DayPage(
            date = date,
            weekday = WEEKDAYS.getValue(date.dayOfWeek),
            lunarDate = lunarDate,
            ganZhiYear = lunar.yearInGanZhi + "年",
            zodiac = ZODIAC[lunar.yearZhiIndex],
            solarTerm = solarTerm,
            festivals = (holidays + observances).distinct(),
            isRedDay = weekend || holidays.isNotEmpty(),
            yi = lunar.dayYi.map(Hant::convert),
            ji = lunar.dayJi.map(Hant::convert),
            chong = "沖" + Hant.convert(lunar.dayChongDesc).let { desc ->
                // 函式庫給的是「(丁丑)牛」，改成日曆上常見的「牛（丁丑）」
                val m = Regex("""\((.+)\)(.+)""").matchEntire(desc)
                if (m != null) "${m.groupValues[2]}（${m.groupValues[1]}）" else desc
            },
            sha = "煞" + Hant.convert(lunar.daySha),
        )
    }
}
