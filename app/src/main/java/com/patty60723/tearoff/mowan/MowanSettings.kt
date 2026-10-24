package com.patty60723.tearoff.mowan

import android.content.Context
import com.patty60723.tearoff.weather.Mood

/** 墨丸 V3 的設定（SharedPreferences "mowan"）。 */
object MowanSettings {
    private const val PREFS = "mowan"
    private const val KEY_STORY_CARD = "storyCard"
    private const val KEY_HAT_ON = "paperHatOn"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 故事卡上用 V3 墨丸還是舊火柴人。整合完成（P7）前預設是舊的，只能從除錯選單打開。 */
    fun storyCard(context: Context): Boolean = prefs(context).getBoolean(KEY_STORY_CARD, false)

    fun setStoryCard(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_STORY_CARD, on).apply()
    }

    /**
     * 紙帽有沒有戴著。「拿到」由故事決定（第 11 頁解鎖，見 [Capabilities.paperHat]），這裡只存「戴著」。
     * 紙帽是持續配件（Confirmed），所以預設戴著；怎麼脫、什麼時候自動收起還沒定（D-05），目前只有除錯選單能切換。
     */
    fun hatOn(context: Context): Boolean = prefs(context).getBoolean(KEY_HAT_ON, true)

    fun setHatOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_HAT_ON, on).apply()
    }

    /** 故事從頭開始時，裝備狀態也回到預設 */
    fun resetEquipment(context: Context) {
        prefs(context).edit().remove(KEY_HAT_ON).apply()
    }

    /** 天氣 → V3 場景。只有晴、雨有定案的設計；熱、陰、霧、冷、雪先用一般站姿（D-10）。 */
    fun weatherOf(mood: Mood): Mowan.Weather = when (mood) {
        Mood.SUNNY -> Mowan.Weather.SUNNY
        Mood.RAIN, Mood.THUNDER -> Mowan.Weather.RAINY
        else -> Mowan.Weather.NONE
    }
}
