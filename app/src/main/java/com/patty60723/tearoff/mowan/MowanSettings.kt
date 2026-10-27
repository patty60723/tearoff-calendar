package com.patty60723.tearoff.mowan

import android.content.Context
import com.patty60723.tearoff.weather.Mood

/** 墨丸 V3 的設定（SharedPreferences "mowan"）。 */
object MowanSettings {
    private const val PREFS = "mowan"
    private const val KEY_STORY_CARD = "storyCard"
    private const val KEY_HAT_ON = "paperHatOn"
    private const val KEY_BUNDLE_ON = "bundleOn"

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

    /**
     * 包袱有沒有背著（D-05 Confirmed）。「取得」由故事決定（第 14 頁解鎖，取得後永久擁有），這裡只存「背著」。
     * 預設背著；可以自由卸下、再背上。目前切換入口只在除錯選單。
     */
    fun bundleOn(context: Context): Boolean = prefs(context).getBoolean(KEY_BUNDLE_ON, true)

    fun setBundleOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_BUNDLE_ON, on).apply()
    }

    /**
     * 節日主演出「演過了」的紀錄（D-33、D-45）：最近 [PLAYED_KEEP] 個「日期:節日」key，存在同一個 prefs。
     * 開始播的時候就寫入（同步寫，避免剛開始演就被系統關掉、重開又演一次）。
     */
    class FestivalLog(context: Context) : FestivalSession.MainLog {
        private val prefs = prefs(context.applicationContext)

        fun keys(): List<String> = prefs.getString(KEY_FESTIVAL_PLAYED, "").orEmpty().split('|').filter { it.isNotEmpty() }

        override fun played(key: String) = key in keys()

        override fun markPlayed(key: String) {
            val keep = ((keys() - key) + key).takeLast(PLAYED_KEEP)
            prefs.edit().putString(KEY_FESTIVAL_PLAYED, keep.joinToString("|")).commit()
        }
    }

    private const val KEY_FESTIVAL_PLAYED = "festivalMainPlayed"
    const val PLAYED_KEEP = 10

    private const val KEY_FESTIVAL_HEADWEAR = "festivalHeadwear"

    /** 節日頭飾開關（只在除錯選單，D-05）：關掉時節日也照紙帽的裝備狀態畫。預設戴節日頭飾 */
    fun festivalHeadwear(context: Context): Boolean = prefs(context).getBoolean(KEY_FESTIVAL_HEADWEAR, true)

    fun setFestivalHeadwear(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_FESTIVAL_HEADWEAR, on).apply()
    }

    /** 故事卡、日曆頁、小工具共用的美術設定（除了節日頭飾開關，其他都是審核確認的預設值） */
    fun art(context: Context): ArtConfig =
        if (festivalHeadwear(context)) ArtConfig.DEFAULT else ArtConfig.DEFAULT.copy(festivalHeadwear = false)

    /** 故事從頭開始時，裝備狀態也回到預設 */
    fun resetEquipment(context: Context) {
        prefs(context).edit().remove(KEY_HAT_ON).remove(KEY_BUNDLE_ON).apply()
    }

    /** 天氣 → V3 場景（8 種都有定稿，W6） */
    fun weatherOf(mood: Mood): Mowan.Weather = when (mood) {
        Mood.SUNNY -> Mowan.Weather.SUNNY
        Mood.HOT -> Mowan.Weather.HOT
        Mood.CLOUDY -> Mowan.Weather.CLOUDY
        Mood.FOG -> Mowan.Weather.FOG
        Mood.RAIN -> Mowan.Weather.RAINY
        Mood.THUNDER -> Mowan.Weather.THUNDER
        Mood.COLD -> Mowan.Weather.COLD
        Mood.SNOW -> Mowan.Weather.SNOW
    }
}
