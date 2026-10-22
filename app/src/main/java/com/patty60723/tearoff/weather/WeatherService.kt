package com.patty60723.tearoff.weather

import android.content.Context
import com.patty60723.tearoff.Cities
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 天氣：從 Open-Meteo 抓，存在手機裡，一小時內不重抓。
 * 抓資料要在背景執行緒呼叫 [refreshIfStale]。
 */
object WeatherService {
    private const val PREFS = "weather"
    private const val KEY_JSON = "json"
    private const val KEY_CITY = "city"
    private const val KEY_AT = "fetchedAt"
    private const val KEY_FAKE = "fakeMood"

    private const val FRESH_MS = 60 * 60 * 1000L
    /** 超過這麼久就不顯示，免得顯示昨天的天氣 */
    private const val SHOW_MS = 6 * 60 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 現在能顯示的天氣；沒有網路、還沒抓過或太舊就是 null。除錯時可能是假的。 */
    fun current(context: Context, now: Long = System.currentTimeMillis()): Weather? {
        fakeMood(context)?.let { return fake(context, it, now) }
        val p = prefs(context)
        val json = p.getString(KEY_JSON, null) ?: return null
        val city = p.getString(KEY_CITY, null) ?: return null
        if (city != Cities.selected(context).name) return null
        val at = p.getLong(KEY_AT, 0)
        if (now - at > SHOW_MS) return null
        return Weather.parse(json, city, at)
    }

    /** 太舊或換了城市就重抓。回傳有沒有抓到新的。會連網，不要在主執行緒呼叫。 */
    fun refreshIfStale(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val p = prefs(context)
        val city = Cities.selected(context)
        val fresh = p.getString(KEY_CITY, null) == city.name && now - p.getLong(KEY_AT, 0) < FRESH_MS
        if (fresh) return false
        val json = runCatching { download(Weather.url(city.lat, city.lon)) }.getOrNull() ?: return false
        if (Weather.parse(json, city.name, now) == null) return false
        p.edit().putString(KEY_JSON, json).putString(KEY_CITY, city.name).putLong(KEY_AT, now).apply()
        return true
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        try {
            if (connection.responseCode != 200) error("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    // ---- 除錯用：假裝天氣 ----

    fun fakeMood(context: Context): Mood? =
        prefs(context).getString(KEY_FAKE, null)?.let { name -> Mood.entries.firstOrNull { it.name == name } }

    fun setFakeMood(context: Context, mood: Mood?) {
        prefs(context).edit().apply { if (mood == null) remove(KEY_FAKE) else putString(KEY_FAKE, mood.name) }.apply()
    }

    private fun fake(context: Context, mood: Mood, now: Long): Weather {
        val (sky, temp) = when (mood) {
            Mood.SUNNY -> Sky.CLEAR to 25
            Mood.HOT -> Sky.CLEAR to 33
            Mood.CLOUDY -> Sky.CLOUDY to 24
            Mood.FOG -> Sky.FOG to 18
            Mood.RAIN -> Sky.RAIN to 22
            Mood.THUNDER -> Sky.THUNDER to 27
            Mood.COLD -> Sky.CLOUDY to 11
            Mood.SNOW -> Sky.SNOW to 2
        }
        return Weather(Cities.selected(context).name, LocalDate.now(), sky, temp, temp - 3, temp + 3, now)
    }
}
