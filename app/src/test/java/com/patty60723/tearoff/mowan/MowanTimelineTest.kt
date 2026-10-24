package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.random.Random

/** W5：時間軸、打雷排程、Reduced Motion。時間由測試傳入，亂數用固定的數列，所以每次結果都一樣。 */
class MowanTimelineTest {
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }
    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))
    private val full = Capabilities.of(parts(14))

    /** 依序回傳指定的間隔（毫秒），用完就重複最後一個 */
    private class Intervals(vararg val ms: Long) : Random() {
        var calls = 0
        override fun nextBits(bitCount: Int) = error("不該用到")
        override fun nextLong(from: Long, until: Long): Long {
            val v = ms[minOf(calls++, ms.size - 1)]
            require(v in from until until) { "$v 不在 $from–${until - 1}" }
            return v
        }
    }

    private val t0 = 1_000_000L

    private fun thunder(vararg intervals: Long, priority: () -> Boolean = { false }) =
        MowanTimeline(Intervals(*intervals), priority).also { it.setWeather(Mowan.Weather.THUNDER, t0) }

    // ---------- 打雷排程 ----------

    @Test
    fun intervalsStayInTheProposedRange() {
        val r = Random(42)
        val draws = (0 until 2000).map { r.nextLong(MowanTimeline.THUNDER_MIN_MS, MowanTimeline.THUNDER_MAX_MS + 1) }
        assertTrue(draws.all { it in 15_000L..40_000L })
        assertTrue("間隔要有變化", draws.toSet().size > 100)
        // 真的排程：一小時裡，打雷的次數在 3600/40 到 3600/15 之間
        val tl = MowanTimeline(Random(7)).also { it.setWeather(Mowan.Weather.THUNDER, t0) }
        var strikes = 0
        var inStrike = false
        for (now in t0..(t0 + 3_600_000L) step 100) {
            val s = tl.sample(now, false).sinceStrikeMs != null
            if (s && !inStrike) strikes++
            inStrike = s
        }
        assertTrue("一小時打了 $strikes 次", strikes in 90..240)
    }

    @Test
    fun strikeFiresEndsAndRepeats() {
        val tl = thunder(20_000, 30_000)
        assertNull("一開始不打雷", tl.sample(t0, false).sinceStrikeMs)
        assertNull("第一次不會在 15 秒內", tl.sample(t0 + 19_999, false).sinceStrikeMs)
        assertEquals(0.0, tl.sample(t0 + 20_000, false).sinceStrikeMs!!, 0.0)
        assertEquals(400.0, tl.sample(t0 + 20_400, false).sinceStrikeMs!!, 0.0)
        assertNull("0.8 秒後驚嚇結束", tl.sample(t0 + 20_800, false).sinceStrikeMs)
        assertNull(tl.sample(t0 + 49_999, false).sinceStrikeMs)
        assertEquals("下一次在 30 秒後", 0.0, tl.sample(t0 + 50_000, false).sinceStrikeMs!!, 0.0)
    }

    @Test
    fun strikeEnvelopeEndsWithThePose() {
        // 時間軸說「打雷結束」的那一刻，姿勢也已經完全回到撐傘（不會卡在驚嚇）
        val end = Poses.thunder(5000.0, full, MowanTimeline.STRIKE_MS.toDouble())
        assertEquals(Face.RAINY, end.face)
        assertEquals(0.0, end.effects.bolt, 0.0)
        assertEquals(0.0, end.headDx, 0.0)
    }

    @Test
    fun skippedStrikesAreNeverReplayed() {
        // 更高優先的演出在播：輪到的那次跳過，演出結束後也不補
        var busy = true
        val tl = thunder(20_000, 30_000, priority = { busy })
        assertNull(tl.sample(t0 + 20_100, false).sinceStrikeMs)
        busy = false
        assertNull("不補播", tl.sample(t0 + 20_300, false).sinceStrikeMs)
        assertNotNull("下一次照常", tl.sample(t0 + 50_100, false).sinceStrikeMs)
    }

    @Test
    fun longGapDoesNotCatchUp() {
        // App 在背景 10 分鐘：回來時不會連續補打好幾次
        val tl = thunder(20_000)
        tl.sample(t0, false)
        // 600 秒剛好是第 30 次的時間點；回來時已經過了 500 ms，還在 0.8 秒內，所以只看到這一次，前面 29 次都不補
        val back = t0 + 600_000 + 500
        val s = tl.sample(back, false)
        assertEquals(500.0, s.sinceStrikeMs!!, 0.0)
        assertNull(tl.sample(back + 1000, false).sinceStrikeMs)
    }

    @Test
    fun weatherChangeResetsClockAndSchedule() {
        val tl = thunder(20_000, 25_000, 18_000)
        assertNotNull(tl.sample(t0 + 20_100, false).sinceStrikeMs)
        // 驚嚇中換成雨天：馬上停止、時鐘從 0 開始
        tl.setWeather(Mowan.Weather.RAINY, t0 + 20_200)
        val rain = tl.sample(t0 + 20_200, false)
        assertNull(rain.sinceStrikeMs)
        assertEquals(0.0, rain.clockMs, 0.0)
        // 再換回雷雨：重新排程，時鐘也從 0 開始。
        // （第一次打雷時已經排好下一次的 25 秒；換掉天氣就作廢，重新抽到的是 18 秒）
        tl.setWeather(Mowan.Weather.THUNDER, t0 + 30_000)
        assertEquals(0.0, tl.sample(t0 + 30_000, false).clockMs, 0.0)
        assertNull("原本排在 45 秒的那次已經作廢", tl.sample(t0 + 45_100, false).sinceStrikeMs)
        assertNull(tl.sample(t0 + 47_999, false).sinceStrikeMs)
        assertNotNull(tl.sample(t0 + 48_000, false).sinceStrikeMs)
    }

    @Test
    fun sameWeatherAgainDoesNotRestart() {
        // 天氣資料更新（還是同一種天氣）：時鐘不重來
        val tl = MowanTimeline(Random(1)).also { it.setWeather(Mowan.Weather.SUNNY, t0) }
        tl.setWeather(Mowan.Weather.SUNNY, t0 + 3000)
        assertEquals(4000.0, tl.sample(t0 + 4000, false).clockMs, 0.0)
    }

    @Test
    fun onlyThunderHasStrikes() {
        for (w in Mowan.Weather.entries - Mowan.Weather.THUNDER) {
            val tl = MowanTimeline(Random(3)).also { it.setWeather(w, t0) }
            for (now in t0..(t0 + 120_000) step 50) assertNull("$w 不該打雷", tl.sample(now, false).sinceStrikeMs)
        }
    }

    // ---------- Reduced Motion ----------

    @Test
    fun reducedMotionShowsTheStillPose() {
        for (w in Mowan.Weather.entries) {
            val tl = MowanTimeline(Intervals(20_000)).also { it.setWeather(w, t0) }
            for (now in listOf(t0, t0 + 1234, t0 + 20_100, t0 + 99_999)) {
                val s = tl.sample(now, true)
                assertTrue(s.still)
                assertEquals("$w 代表格", MowanTimeline.stillClock(w), s.clockMs, 0.0)
                assertEquals("$w 筆觸固定", 0, s.variant)
                assertEquals(0, s.rainStep)
                assertNull("$w 不打雷", s.sinceStrikeMs)
            }
        }
    }

    @Test
    fun reducedMotionSkipsStrikesAndRecovers() {
        val tl = thunder(20_000, 20_000)
        assertNull(tl.sample(t0 + 20_100, true).sinceStrikeMs)
        // 動畫打開：不會補剛才那次，時鐘照常走（沒有卡在代表格）
        val back = tl.sample(t0 + 20_300, false)
        assertNull(back.sinceStrikeMs)
        assertEquals(20_300.0, back.clockMs, 0.0)
        assertTrue(!back.still)
        assertNotNull("下一次照常", tl.sample(t0 + 40_100, false).sinceStrikeMs)
    }

    @Test
    fun stillFramesKeepEveryPartAndProp() {
        // 靜止畫面跟會動的畫面畫的部位、道具一樣（不會少了雨傘、包袱、紙帽）；雷雨的靜止畫面沒有閃電
        for (page in listOf(1, 3, 4, 5, 8, 14)) for (w in Mowan.Weather.entries) {
            val moving = Mowan.frame(parts(page), 0, true, w, 1000.0, 8).ops.mapTo(HashSet()) { it.part }
            val still = Mowan.frame(parts(page), 0, true, w, MowanTimeline.stillClock(w), 0, still = true).ops.mapTo(HashSet()) { it.part }
            val character = setOf("umbrella", "umbrellaGrip", "bundle", "paperHat", "body", "head", "armL", "armR", "legL", "legR", "scarf", "eyes", "mouth", "flick")
            assertEquals("第 $page 頁 $w", moving intersect character, still intersect character)
            assertTrue("第 $page 頁 $w 靜止時不該有閃電", "bolt" !in still)
        }
    }

    // ---------- 同一條時間軸、循環不跳 ----------

    private val periods = mapOf(
        Mowan.Weather.SUNNY to Poses.SUNNY_MS, Mowan.Weather.RAINY to Poses.RAINY_MS, Mowan.Weather.HOT to Poses.HOT_MS,
        Mowan.Weather.CLOUDY to Poses.CLOUDY_MS, Mowan.Weather.FOG to Poses.FOG_MS, Mowan.Weather.THUNDER to Poses.RAINY_MS,
        Mowan.Weather.COLD to Poses.COLD_MS, Mowan.Weather.SNOW to Poses.SNOW_MS,
    )

    private fun pose(w: Mowan.Weather, t: Double, since: Double?) = when (w) {
        Mowan.Weather.SUNNY -> Poses.sunny(t)
        Mowan.Weather.RAINY -> Poses.rainy(t)
        Mowan.Weather.HOT -> Poses.hot(t, full)
        Mowan.Weather.CLOUDY -> Poses.cloudy(t, full)
        Mowan.Weather.FOG -> Poses.fog(t, full)
        Mowan.Weather.THUNDER -> Poses.thunder(t, full, since)
        Mowan.Weather.COLD -> Poses.cold(t, full)
        Mowan.Weather.SNOW -> Poses.snow(t, full)
        Mowan.Weather.NONE -> Poses.NORMAL
    }

    @Test
    fun loopsCrossPeriodBoundariesSmoothly() {
        // 照 App 的重畫間隔（33 ms）跑過三個週期，每一個週期的接縫都不能跳
        for ((w, period) in periods) {
            val tl = MowanTimeline(Intervals(20_000)).also { it.setWeather(w, t0) }
            var prev: Pose? = null
            var now = t0
            while (now <= t0 + (3 * period).toLong() + 100) {
                val s = tl.sample(now, false)
                val p = pose(w, s.clockMs, s.sinceStrikeMs)
                prev?.let { q ->
                    val step = maxOf(
                        hypot(p.armL.last().x - q.armL.last().x, p.armL.last().y - q.armL.last().y),
                        hypot(p.armR.last().x - q.armR.last().x, p.armR.last().y - q.armR.last().y),
                    )
                    // 熱天抬手、放手是大動作（D-22 定案的上限 4.0）；其他 1.5
                    val limit = if (w == Mowan.Weather.HOT) 4.0 else 1.5
                    assertTrue("$w ${now - t0}ms 手尖一格跳 $step", step <= limit)
                    assertTrue("$w ${now - t0}ms 頭一格轉 ${p.headAngle - q.headAngle}", kotlin.math.abs(p.headAngle - q.headAngle) <= 1.0)
                }
                prev = p
                now += MowanView.POSE_FRAME_MS
            }
        }
    }

    @Test
    fun boilKeepsSixFramesPerSecondAcrossTheLoop() {
        // 筆觸跟姿勢同一個時鐘：任何時候都是每 1/6 秒換一張，順序 0,3,1,4,2
        val tl = MowanTimeline(Random(1)).also { it.setWeather(Mowan.Weather.HOT, t0) }
        val seen = (0 until 60).map { tl.sample(t0 + 3_200 * 5 + (it * 1000 / 6) + 1, false).variant }
        for (i in 1 until seen.size) assertEquals(Mowan.SEQUENCE[(Mowan.SEQUENCE.indexOf(seen[i - 1]) + 1) % 5], seen[i])
    }

    @Test
    fun everythingFollowsOneTimeline() {
        // 同一個時間點：frame() 畫出來的包袱、圍巾、手臂、表情，跟用同一個時間算出的姿勢完全一樣
        for ((w, t, since) in listOf(Triple(Mowan.Weather.SUNNY, 2750.0, null), Triple(Mowan.Weather.THUNDER, 5150.0, 150.0), Triple(Mowan.Weather.COLD, 350.0, null))) {
            val byFrame = Mowan.frame(parts(14), 2, true, w, t, 9, sinceStrikeMs = since)
            val byPose = Mowan.render(parts(14), 2, true, pose(w, t, since), w, 9, clockMs = t)
            assertEquals("$w 筆畫數", byPose.ops.size, byFrame.ops.size)
            for (part in listOf("bundle", "scarf", "armL", "armR", "eyes", "mouth")) {
                assertEquals("$w $part", byPose.ops.filter { it.part == part }.map { it.path.commands }, byFrame.ops.filter { it.part == part }.map { it.path.commands })
            }
            assertEquals(byPose.head, byFrame.head)
        }
    }
}
