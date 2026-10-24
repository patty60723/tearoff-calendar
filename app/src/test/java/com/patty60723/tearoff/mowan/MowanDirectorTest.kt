package com.patty60723.tearoff.mowan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** W6：動畫優先順序，以及雷雨驚嚇遇到更高優先的演出時跳過、不補播 */
class MowanDirectorTest {
    private val t0 = 1_000_000L

    @Test
    fun priorityOrder() {
        val d = MowanDirector()
        assertEquals(MowanDirector.Activity.IDLE, d.current(Mowan.Weather.NONE))
        assertEquals(MowanDirector.Activity.WEATHER_DAILY, d.current(Mowan.Weather.SNOW))
        assertFalse(d.aboveWeather())
        d.start(MowanDirector.Activity.FESTIVAL_IDLE)
        assertEquals(MowanDirector.Activity.FESTIVAL_IDLE, d.current(Mowan.Weather.SNOW))
        d.start(MowanDirector.Activity.GROWTH_STORY)
        d.start(MowanDirector.Activity.FESTIVAL_MAIN)
        assertEquals("成長故事最優先", MowanDirector.Activity.GROWTH_STORY, d.current(Mowan.Weather.THUNDER))
        d.finish(MowanDirector.Activity.GROWTH_STORY)
        assertEquals(MowanDirector.Activity.FESTIVAL_MAIN, d.current(Mowan.Weather.THUNDER))
        d.finish(MowanDirector.Activity.FESTIVAL_MAIN)
        d.finish(MowanDirector.Activity.FESTIVAL_IDLE)
        assertFalse(d.aboveWeather())
        assertEquals(MowanDirector.Activity.WEATHER_DAILY, d.current(Mowan.Weather.THUNDER))
    }

    @Test(expected = IllegalArgumentException::class)
    fun weatherIsNotStartedByHand() {
        MowanDirector().start(MowanDirector.Activity.WEATHER_DAILY)
    }

    /** 固定間隔的亂數 */
    private class Every(val ms: Long) : Random() {
        override fun nextBits(bitCount: Int) = error("不該用到")
        override fun nextLong(from: Long, until: Long) = ms
    }

    @Test
    fun thunderStartleYieldsToHigherPriority() {
        for (activity in listOf(MowanDirector.Activity.GROWTH_STORY, MowanDirector.Activity.FESTIVAL_MAIN, MowanDirector.Activity.FESTIVAL_IDLE)) {
            val d = MowanDirector()
            val tl = MowanTimeline(Every(20_000)) { d.aboveWeather() }.also { it.setWeather(Mowan.Weather.THUNDER, t0) }
            d.start(activity)
            assertNull("$activity 在播：不嚇", tl.sample(t0 + 20_100, false).sinceStrikeMs)
            d.finish(activity)
            assertNull("$activity 結束：不補播", tl.sample(t0 + 20_300, false).sinceStrikeMs)
            assertNotNull("下一次照常", tl.sample(t0 + 40_100, false).sinceStrikeMs)
        }
    }
}
