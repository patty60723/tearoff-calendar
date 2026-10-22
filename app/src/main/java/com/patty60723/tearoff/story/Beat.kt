package com.patty60723.tearoff.story

import org.json.JSONArray
import org.json.JSONObject

/**
 * 故事的一個片段。`when` 裡的條件全部符合才會被挑到，沒寫的條件就是不限。
 * 格式見故事設定文件的「可擴展的故事格式」。
 */
data class Beat(
    val id: String,
    /** 主線：第幾頁一定出現這段 */
    val page: Int? = null,
    val chapters: List<Int> = emptyList(),
    val festival: String? = null,
    /** 節氣名稱；"*" 表示任何節氣當天 */
    val solarTerm: String? = null,
    val yiCategory: String? = null,
    val jiCategory: String? = null,
    val wearing: String? = null,
    /** 天氣關鍵字：晴、熱、陰、霧、雨、雷雨、冷、雪 */
    val weather: String? = null,
    val seasons: List<String> = emptyList(),
    val weight: Int = 1,
    val text: String,
    val action: String = "idle",
    /** 看完這段之後長出來的部位或拿到的配件 */
    val unlock: List<String> = emptyList(),
    /** 撕頁前要先問使用者的事，目前只有 "name" */
    val ask: String? = null,
) {
    val tier: Tier
        get() = when {
            page != null -> Tier.MAIN
            festival != null -> Tier.FESTIVAL
            solarTerm != null -> Tier.SOLAR_TERM
            else -> Tier.DAILY
        }

    /** 同一天有好幾段符合時，排前面的優先。 */
    enum class Tier { MAIN, FESTIVAL, SOLAR_TERM, DAILY }
}

/** 讀故事包（一個 JSON 檔裝很多片段）。 */
object StoryParser {
    fun parse(json: String): List<Beat> {
        val beats = JSONObject(json).getJSONArray("beats")
        return List(beats.length()) { i ->
            val o = beats.getJSONObject(i)
            val w = o.optJSONObject("when") ?: JSONObject()
            Beat(
                id = o.getString("id"),
                page = if (w.has("page")) w.getInt("page") else null,
                chapters = w.optJSONArray("chapter").ints(),
                festival = w.string("festival"),
                solarTerm = w.string("solarTerm"),
                yiCategory = w.string("yiCategory"),
                jiCategory = w.string("jiCategory"),
                wearing = w.string("wearing"),
                weather = w.string("weather"),
                seasons = w.optJSONArray("season").strings(),
                weight = o.optInt("weight", 1),
                text = o.getString("text"),
                action = o.optString("action", "idle"),
                unlock = o.optJSONArray("unlock").strings(),
                ask = o.string("ask"),
            )
        }
    }

    private fun JSONObject.string(key: String) = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONArray?.strings() = if (this == null) emptyList() else List(length()) { getString(it) }
    private fun JSONArray?.ints() = if (this == null) emptyList() else List(length()) { getInt(it) }
}
