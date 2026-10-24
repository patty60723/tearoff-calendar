package com.patty60723.tearoff.mowan

/**
 * 墨丸現在在演什麼（動畫優先順序，整合規格 §5.4，Confirmed）：
 * 成長故事 > 節日主要演出 > 節日待機 > 天氣日常 > 一般待機。
 *
 * W6 只有「天氣日常」和「一般待機」真的會出現；成長故事、節日演出要到 P6 才做，
 * 到時候由播放的人呼叫 [start]／[finish]。這裡先把介面和規則定好：
 * 只要有比天氣日常更優先的演出在播，雷雨的驚嚇就跳過、不補播（見 [MowanTimeline]）。
 */
class MowanDirector {
    /** 數字越小越優先 */
    enum class Activity { GROWTH_STORY, FESTIVAL_MAIN, FESTIVAL_IDLE, WEATHER_DAILY, IDLE }

    private val playing = HashSet<Activity>()

    /** 開始播一段更高優先的演出（成長故事、節日演出） */
    fun start(activity: Activity) {
        require(activity < Activity.WEATHER_DAILY) { "天氣日常與一般待機由天氣決定，不用 start" }
        playing += activity
    }

    fun finish(activity: Activity) {
        playing -= activity
    }

    /** 現在最優先的那一個；沒有演出時看天氣 */
    fun current(weather: Mowan.Weather): Activity =
        playing.minOrNull() ?: if (weather == Mowan.Weather.NONE) Activity.IDLE else Activity.WEATHER_DAILY

    /** 有沒有比天氣日常更優先的演出在播 */
    fun aboveWeather(): Boolean = playing.isNotEmpty()
}
