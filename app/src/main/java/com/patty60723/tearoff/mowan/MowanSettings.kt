package com.patty60723.tearoff.mowan

import android.content.Context
import com.patty60723.tearoff.weather.Mood

/** 墨丸 V3 的設定（SharedPreferences "mowan"）。 */
object MowanSettings {
    private const val PREFS = "mowan"
    private const val KEY_STORY_CARD = "storyCard"

    /** 故事卡上用 V3 墨丸還是舊火柴人。整合完成（P7）前預設是舊的，只能從除錯選單打開。 */
    fun storyCard(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_STORY_CARD, false)

    fun setStoryCard(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_STORY_CARD, on).apply()
    }

    /** 天氣 → V3 場景。只有晴、雨有定案的設計；熱、陰、霧、冷、雪先用一般站姿（D-10）。 */
    fun weatherOf(mood: Mood): Mowan.Weather = when (mood) {
        Mood.SUNNY -> Mowan.Weather.SUNNY
        Mood.RAIN, Mood.THUNDER -> Mowan.Weather.RAINY
        else -> Mowan.Weather.NONE
    }
}
