package com.patty60723.tearoff.mowan

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** D-31（P6-4a）：節日當天整天不打雷，不只主演出期間；不是節日照常打雷 */
class FestivalThunderTest {
    private val t0 = 1_000_000L

    private fun strikes(allowed: Boolean, priority: Boolean = false): Int {
        val tl = MowanTimeline(Random(3), higherPriorityPlaying = { priority }, thunderAllowed = { allowed })
        tl.setWeather(Mowan.Weather.THUNDER, t0)
        var n = 0
        var last: Double? = null
        // 10 分鐘，每 50 ms 取樣一次
        for (t in 0 until 600_000 step 50) {
            val s = tl.sample(t0 + t, reducedMotion = false).sinceStrikeMs
            if (s != null && (last == null || s < last)) n++
            last = s
        }
        return n
    }

    @Test
    fun festivalDayNeverStrikes() {
        assertTrue("不是節日：10 分鐘內會打雷", strikes(allowed = true) > 5)
        assertTrue("節日當天、沒有演出：一次都不打", strikes(allowed = false) == 0)
        assertTrue("節日當天、正在演出：一次都不打", strikes(allowed = false, priority = true) == 0)
    }

    @Test
    fun rainStillFallsOnFestivalDays() {
        val tl = MowanTimeline(Random(3), thunderAllowed = { false }).also { it.setWeather(Mowan.Weather.THUNDER, t0) }
        val a = tl.sample(t0 + 1000, false)
        val b = tl.sample(t0 + 2000, false)
        assertNull(a.sinceStrikeMs)
        assertTrue("雨絲照樣往下", b.rainStep > a.rainStep)
    }
}
