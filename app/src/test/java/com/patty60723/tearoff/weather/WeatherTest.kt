package com.patty60723.tearoff.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeatherTest {
    /** Open-Meteo 文件上的回傳格式 */
    private fun sample(dayCode: Int, nowCode: Int, now: Double, min: Double, max: Double) = """
        {"latitude":25.0,"longitude":121.5,"timezone":"Asia/Taipei",
         "current_units":{"time":"iso8601","temperature_2m":"°C","weather_code":"wmo code"},
         "current":{"time":"2026-10-22T13:00","interval":900,"temperature_2m":$now,"weather_code":$nowCode},
         "daily_units":{"time":"iso8601","weather_code":"wmo code"},
         "daily":{"time":["2026-10-22"],"weather_code":[$dayCode],"temperature_2m_max":[$max],"temperature_2m_min":[$min]}}
    """.trimIndent()

    @Test
    fun parses() {
        val w = Weather.parse(sample(61, 3, 22.4, 20.1, 25.6), "臺北市", 0)!!
        assertEquals(LocalDate.of(2026, 10, 22), w.date)
        assertEquals(Sky.RAIN, w.sky)
        assertEquals(22, w.now)
        assertEquals(20, w.min)
        assertEquals(26, w.max)
        assertEquals("雨 22°", w.short)
        assertEquals("雨 22°（20–26°）", w.long)
        assertEquals(Mood.RAIN, w.mood)
    }

    @Test
    fun rainingNowWinsOverTheDay() {
        assertEquals(Sky.THUNDER, Weather.parse(sample(2, 95, 27.0, 24.0, 30.0), "臺北市", 0)!!.sky)
        // 現在晴但今天會下雨：以今天為準
        assertEquals(Sky.RAIN, Weather.parse(sample(63, 0, 27.0, 24.0, 30.0), "臺北市", 0)!!.sky)
    }

    @Test
    fun moods() {
        fun mood(code: Int, min: Int, max: Int) = Weather("臺北市", LocalDate.now(), Sky.ofCode(code), max, min, max, 0).mood
        assertEquals(Mood.HOT, mood(0, 26, 33))
        assertEquals(Mood.SUNNY, mood(1, 20, 27))
        assertEquals(Mood.CLOUDY, mood(3, 20, 27))
        assertEquals(Mood.CLOUDY, mood(2, 20, 27))
        assertEquals(Mood.COLD, mood(3, 9, 15))
        assertEquals(Mood.RAIN, mood(81, 9, 15))
        assertEquals(Mood.FOG, mood(45, 16, 22))
        assertEquals(Mood.SNOW, mood(73, -2, 3))
    }

    @Test
    fun badJsonIsNull() {
        assertNull(Weather.parse("{}", "臺北市", 0))
        assertNull(Weather.parse("not json", "臺北市", 0))
    }
}
