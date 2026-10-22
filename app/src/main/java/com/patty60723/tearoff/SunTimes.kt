package com.patty60723.tearoff

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * 日出日落，不用連網，用「Almanac for Computers」的公式算，誤差約一兩分鐘。
 * 以太陽上緣碰到地平線為準（含大氣折射），跟氣象署公布的時間同一個定義。
 */
object SunTimes {
    data class Times(val sunrise: LocalTime?, val sunset: LocalTime?)

    private const val ZENITH = 90.833

    fun of(date: LocalDate, lat: Double, lon: Double, zone: ZoneId = ZoneId.of("Asia/Taipei")): Times {
        val offsetHours = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        return Times(
            event(date, lat, lon, rising = true)?.let { toLocal(it + offsetHours) },
            event(date, lat, lon, rising = false)?.let { toLocal(it + offsetHours) },
        )
    }

    /** 回傳 UTC 的小時（0–24），極地沒有日出或日落時是 null。 */
    private fun event(date: LocalDate, lat: Double, lon: Double, rising: Boolean): Double? {
        val n = date.dayOfYear
        val lngHour = lon / 15
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24
        val m = 0.9856 * t - 3.289
        val l = norm360(m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634)

        var ra = norm360(atanD(0.91764 * tanD(l)))
        ra += floor(l / 90) * 90 - floor(ra / 90) * 90
        ra /= 15

        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(ZENITH) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1 || cosH < -1) return null

        val h = (if (rising) 360 - acosD(cosH) else acosD(cosH)) / 15
        val localMean = h + ra - 0.06571 * t - 6.622
        return norm24(localMean - lngHour)
    }

    private fun toLocal(hours: Double): LocalTime {
        val minutes = (norm24(hours) * 60).let { Math.round(it).toInt() } % (24 * 60)
        return LocalTime.of(minutes / 60, minutes % 60)
    }

    private fun norm360(x: Double) = ((x % 360) + 360) % 360
    private fun norm24(x: Double) = ((x % 24) + 24) % 24
    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun atanD(x: Double) = Math.toDegrees(atan(x))
    private fun acosD(x: Double) = Math.toDegrees(acos(x))
}
