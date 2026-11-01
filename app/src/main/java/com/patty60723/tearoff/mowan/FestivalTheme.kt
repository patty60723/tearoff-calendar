package com.patty60723.tearoff.mowan

import com.nlf.calendar.Solar
import java.time.LocalDate

/** 墨丸會過的節日（P6）。只有這三個；其他節日（端午、元宵等）不在 P6 範圍。 */
enum class Festival { SPRING, XMAS, MOON }

/**
 * 某一天是不是墨丸的節日（D-28，2026-10-26 確認）：
 * - 春節：農曆正月初一到初三（三天各自是不同的日期，各自可以演一次主演出，見 [FestivalSession]）
 * - 聖誕：國曆 12/25
 * - 中秋：農曆八月十五；閏八月不算
 *
 * 直接用日期算，**不看**日曆上印的節日文字（12/25 印的可能是「行憲紀念日」）。
 * 「今天」由呼叫的人給（App 用 `AppClock.today`，D-36）。
 */
object FestivalTheme {
    fun of(date: LocalDate): Festival? {
        if (date.monthValue == 12 && date.dayOfMonth == 25) return Festival.XMAS
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        // 閏月的 month 是負數，所以閏正月、閏八月都不會符合
        return when {
            lunar.month == 1 && lunar.day in 1..3 -> Festival.SPRING
            lunar.month == 8 && lunar.day == 15 -> Festival.MOON
            else -> null
        }
    }
}
