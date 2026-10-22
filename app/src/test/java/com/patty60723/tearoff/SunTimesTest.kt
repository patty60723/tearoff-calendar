package com.patty60723.tearoff

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class SunTimesTest {
    private val taipei = Cities.DEFAULT

    private fun near(expected: String, actual: LocalTime?) {
        val diff = abs(ChronoUnit.MINUTES.between(LocalTime.parse(expected), actual!!))
        assertTrue("expected $expected, got $actual", diff <= 3)
    }

    /** 氣象署公布的臺北日出日落（夏至、冬至），容許 3 分鐘誤差 */
    @Test
    fun taipeiSolstices() {
        SunTimes.of(LocalDate.of(2026, 6, 21), taipei.lat, taipei.lon).let {
            near("05:05", it.sunrise)
            near("18:47", it.sunset)
        }
        SunTimes.of(LocalDate.of(2026, 12, 21), taipei.lat, taipei.lon).let {
            near("06:35", it.sunrise)
            near("17:10", it.sunset)
        }
    }

    @Test
    fun everyCityEveryDayHasBoth() {
        for (city in Cities.all) {
            var day = LocalDate.of(2026, 1, 1)
            repeat(365) {
                val t = SunTimes.of(day, city.lat, city.lon)
                assertTrue("${city.name} $day", t.sunrise!! < LocalTime.of(7, 30) && t.sunset!! > LocalTime.of(16, 30))
                day = day.plusDays(1)
            }
        }
    }
}
