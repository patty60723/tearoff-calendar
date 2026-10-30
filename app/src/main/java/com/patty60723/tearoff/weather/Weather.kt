package com.patty60723.tearoff.weather

import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToInt

/** 天空的樣子，從 WMO 天氣代碼來。 */
enum class Sky(val label: String) {
    CLEAR("晴"),
    PARTLY("晴時多雲"),
    CLOUDY("陰"),
    FOG("霧"),
    RAIN("雨"),
    HEAVY_RAIN("大雨"),
    THUNDER("雷雨"),
    SNOW("雪");

    companion object {
        /** https://open-meteo.com/en/docs 的 WMO weather code */
        fun ofCode(code: Int): Sky = when (code) {
            0, 1 -> CLEAR
            2 -> PARTLY
            3 -> CLOUDY
            45, 48 -> FOG
            65, 67, 82 -> HEAVY_RAIN
            in 51..67, in 80..82 -> RAIN
            in 71..77, 85, 86 -> SNOW
            in 95..99 -> THUNDER
            else -> CLOUDY
        }
    }
}

/**
 * 今天的天氣分類。[keyword] 也是故事片段 `"weather"` 條件用的字；墨丸的天氣姿勢見 `MowanSettings.weatherOf`。
 */
enum class Mood(
    val keyword: String,
    /** 點天氣時故事卡上的那句話 */
    val line: String,
) {
    // D-17：V3 晴天是伸懶腰、沒有草帽，文案配合伸懶腰
    SUNNY("晴", "太陽暖暖的，伸伸懶腰再出門。"),
    HOT("熱", "好熱……一直擦汗。"),
    CLOUDY("陰", "抬頭看看，雲飄過去了。"),
    FOG("霧", "起霧了，前面看不太清楚。"),
    RAIN("雨", "下雨了，撐起小傘。"),
    THUNDER("雷雨", "轟隆隆！抱緊傘不要怕。"),
    COLD("冷", "好冷，抱緊自己發抖。"),
    SNOW("雪", "下雪了！冷得一直發抖。"),
}

data class Weather(
    val city: String,
    val date: LocalDate,
    val sky: Sky,
    /** 現在的溫度 */
    val now: Int,
    val min: Int,
    val max: Int,
    /** 抓到資料的時間（毫秒） */
    val fetchedAt: Long,
) {
    val mood: Mood
        get() = when {
            sky == Sky.THUNDER -> Mood.THUNDER
            sky == Sky.RAIN || sky == Sky.HEAVY_RAIN -> Mood.RAIN
            sky == Sky.SNOW -> Mood.SNOW
            min <= 14 -> Mood.COLD
            sky == Sky.FOG -> Mood.FOG
            sky == Sky.CLOUDY -> Mood.CLOUDY
            max >= 30 -> Mood.HOT
            sky == Sky.PARTLY -> Mood.CLOUDY
            else -> Mood.SUNNY
        }

    /** 「雨 22°」 */
    val short: String get() = "${sky.label} $now°"

    /** 「雨 22°（20–25°）」 */
    val long: String get() = "$short（$min–$max°）"

    companion object {
        /** 讀 Open-Meteo 的回傳；格式不對就是 null。 */
        fun parse(json: String, city: String, fetchedAt: Long): Weather? = runCatching {
            val root = JSONObject(json)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")
            Weather(
                city = city,
                date = LocalDate.parse(daily.getJSONArray("time").getString(0)),
                // 一整天的天氣比當下更適合印在日曆上；但如果現在正在下雨，就以現在為準
                sky = Sky.ofCode(daily.getJSONArray("weather_code").getInt(0)).let { day ->
                    val now = Sky.ofCode(current.getInt("weather_code"))
                    if (now.ordinal >= Sky.RAIN.ordinal) now else day
                },
                now = current.getDouble("temperature_2m").roundToInt(),
                min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
                max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                fetchedAt = fetchedAt,
            )
        }.getOrNull()

        fun url(lat: Double, lon: Double) =
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=Asia%2FTaipei&forecast_days=1"
    }
}
