package com.patty60723.tearoff.mowan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * P7-2：點故事卡上的墨丸，天氣反應經由中性姿勢自然銜接（D-48）。
 * 用 [MowanTimeline] 與 [Mowan.frame] 量畫出來的每一筆：點擊那一刻不跳、過渡中每 16ms 只移動一點點、
 * 過渡結束剛好接上這一輪的起點；環境時鐘與打雷排程不受影響。
 */
class WeatherReactionTest {
    private val parts = FestivalFixtures.parts(14)
    private val weathers = Mowan.Weather.entries.filter { it != Mowan.Weather.NONE }

    private fun frame(w: Mowan.Weather, s: MowanTimeline.Sample) = Mowan.frame(
        parts, 0, true, w, s.clockMs, s.rainStep, sinceStrikeMs = s.sinceStrikeMs, still = s.still,
        poseClockMs = s.poseClockMs, blendFromPoseMs = s.blendFromPoseMs, blendU = s.blendU,
    )

    /** 角色本身的圖層（天空、雲、雨、霧這些環境照原本的時間走，跟點擊無關，不比） */
    private val character = setOf(Mowan.Layer.BUNDLE, Mowan.Layer.SHAFT, Mowan.Layer.BODY, Mowan.Layer.SCARF, Mowan.Layer.HEAD, Mowan.Layer.HAND, Mowan.Layer.CANOPY)

    /** 每一輪本來就重新開始的效果（汗滴、白氣）：過渡的一半換成新的一滴／一口，不比連續 */
    private var cycleEffects = emptySet<String>()

    /** 兩張影格裡角色的每一筆控制點最大差多少（viewBox 單位，角色約 120 寬），含頭與傘面的變換；筆數或指令不同時是 null */
    private fun diff(a: Mowan.Frame, b: Mowan.Frame): Double? {
        val ao = a.ops.filter { it.layer in character && it.part !in cycleEffects }
        val bo = b.ops.filter { it.layer in character && it.part !in cycleEffects }
        if (ao.size != bo.size) return null
        var m = maxOf(Math.abs(a.head.dy - b.head.dy), Math.abs(a.head.dx - b.head.dx), Math.abs(a.head.rotDeg - b.head.rotDeg) * Math.PI / 180 * 15)
        val ac = a.canopy
        val bc = b.canopy
        if ((ac == null) != (bc == null)) return null
        if (ac != null && bc != null) m = maxOf(m, Math.abs(ac.dx - bc.dx), Math.abs(ac.dy - bc.dy), Math.abs(ac.rotDeg - bc.rotDeg) * Math.PI / 180 * 25)
        for (i in ao.indices) {
            val x = ao[i].path.commands
            val y = bo[i].path.commands
            if (x.size != y.size) return null
            for (k in x.indices) {
                val p = x[k]
                val q = y[k]
                if (p::class != q::class) return null
                val d = when (p) {
                    is PathData.Cmd.MoveTo -> { q as PathData.Cmd.MoveTo; maxOf(Math.abs(p.x - q.x), Math.abs(p.y - q.y)) }
                    is PathData.Cmd.LineTo -> { q as PathData.Cmd.LineTo; maxOf(Math.abs(p.x - q.x), Math.abs(p.y - q.y)) }
                    is PathData.Cmd.QuadTo -> { q as PathData.Cmd.QuadTo; listOf(p.x1 - q.x1, p.y1 - q.y1, p.x - q.x, p.y - q.y).maxOf { Math.abs(it) } }
                    is PathData.Cmd.ArcTo -> { q as PathData.Cmd.ArcTo; listOf(p.x - q.x, p.y - q.y, p.rx - q.rx, p.ry - q.ry).maxOf { Math.abs(it) } }
                    PathData.Cmd.Close -> 0.0
                }
                m = maxOf(m, d)
            }
        }
        return m
    }

    private fun timeline(w: Mowan.Weather, start: Long = 1_000L) = MowanTimeline(Random(7)).apply { setWeather(w, start) }

    /** 點擊時刻：一輪裡的各個位置（含動作最大的地方），也有已經播了好幾輪的時候 */
    private val taps = listOf(700L, 1_900L, 2_750L, 3_600L, 4_900L, 13_777L, 61_234L)

    @Test
    fun noJumpWhenTapped() {
        for (w in weathers) for (tap in taps) {
            val t = timeline(w)
            val now = 1_000L + tap
            val before = frame(w, t.sample(now, false))
            t.replay(now, Mowan.reactionBlendMs(parts, w, t.poseClockAt(now)))
            val after = frame(w, t.sample(now, false))
            val d = diff(before, after)
            assertTrue("$w 點在 $tap ms：點下去那一刻跳了 $d", d != null && d < 1e-6)
        }
    }

    @Test
    fun smoothDuringTheBlendAndEndsAtTheCycleStart() {
        for (w in weathers) for (tap in taps) {
            val t = timeline(w)
            val now = 1_000L + tap
            val blend = Mowan.reactionBlendMs(parts, w, t.poseClockAt(now))
            assertTrue("$w $tap：過渡 $blend ms", blend in 300L..600L)
            t.replay(now, blend)
            cycleEffects = setOf("sweat", "breath")
            var prev = frame(w, t.sample(now, false))
            var worst = 0.0
            var ms = 16L
            while (ms <= blend + 32) {
                val f = frame(w, t.sample(now + ms, false))
                val d = diff(prev, f)
                // 筆數改變只會發生在表情、汗滴這種分段的地方（過渡的一半），那一格不比位置
                if (d != null) worst = maxOf(worst, d)
                prev = f
                ms += 16
            }
            // 原本的天氣動作本身每 16ms 也會動；過渡疊上去之後，一格最多移動 4 個單位（角色約 120 寬）
            assertTrue("$w 點在 $tap ms：過渡中一格最大移動 $worst", worst < 4.0)
            cycleEffects = emptySet()
            // 過渡結束：就是這一輪的起點（跟剛換成這個天氣時的第一格一樣）
            val end = t.sample(now + blend, false)
            assertEquals("$w $tap 姿勢時間回到 0", 0.0, end.poseClockMs, 0.0)
            assertNull(end.blendFromPoseMs)
            val start = Mowan.frame(parts, 0, true, w, end.clockMs, end.rainStep, sinceStrikeMs = end.sinceStrikeMs, poseClockMs = 0.0)
            val d = diff(frame(w, end), start)
            assertTrue("$w $tap：過渡結束跟起點差 $d", d != null && d < 1e-6)
        }
    }

    @Test
    fun blendIsLongerWhenThePoseIsFartherFromTheStart() {
        // 晴天伸懶腰：就在起點時不用過渡；起點附近用最短的過渡；伸到最高的時候比較久
        val t = timeline(Mowan.Weather.SUNNY)
        val atStart = Mowan.reactionBlendMs(parts, Mowan.Weather.SUNNY, 0.0)
        val near = Mowan.reactionBlendMs(parts, Mowan.Weather.SUNNY, 200.0)
        val peak = Mowan.reactionBlendMs(parts, Mowan.Weather.SUNNY, Poses.SUNNY_MS / 2)
        assertEquals("就在起點：直接開始", 0L, atStart)
        assertTrue("起點附近 $near ms", near in 300L..360L)
        assertTrue("伸最高時 $peak ms，比起點附近久", peak in 300L..600L && peak > near)
        assertTrue(t.poseClockAt(1_000L) == 0.0)
    }

    @Test
    fun environmentClockAndThunderAreUnchanged() {
        val a = timeline(Mowan.Weather.THUNDER)
        val b = timeline(Mowan.Weather.THUNDER)
        b.replay(5_000L, 400L)
        var strikesA = 0
        var strikesB = 0
        for (now in 1_000L..120_000L step 50) {
            val sa = a.sample(now, false)
            val sb = b.sample(now, false)
            assertEquals("環境時鐘（雲、雨）不受點擊影響", sa.clockMs, sb.clockMs, 0.0)
            assertEquals(sa.rainStep, sb.rainStep)
            assertEquals("打雷排程不變、不會多打一次雷", sa.sinceStrikeMs, sb.sinceStrikeMs)
            if (sa.sinceStrikeMs == 0.0) strikesA++
            if (sb.sinceStrikeMs == 0.0) strikesB++
        }
        assertEquals(strikesA, strikesB)
    }

    @Test
    fun oneCycleCountsAsReplayingThenTapsRestartAgain() {
        val t = timeline(Mowan.Weather.RAINY)
        assertFalse(t.replaying(5_000L))
        t.replay(5_000L, 400L)
        assertTrue("過渡中", t.replaying(5_200L))
        assertTrue("重播的那一輪", t.replaying(5_000L + 400 + Poses.RAINY_MS.toLong() - 1))
        assertFalse("播完了", t.replaying(5_000L + 400 + Poses.RAINY_MS.toLong()))
        // 換天氣：重播的狀態清掉，從頭開始
        t.setWeather(Mowan.Weather.SNOW, 6_000L)
        assertFalse(t.replaying(6_001L))
        assertEquals(1.0, t.sample(6_001L, false).poseClockMs, 0.0)
    }

    @Test
    fun reducedMotionStaysStill() {
        val t = timeline(Mowan.Weather.SUNNY)
        t.replay(3_000L, 400L)
        val s = t.sample(3_100L, true)
        assertTrue(s.still)
        assertNull(s.blendFromPoseMs)
        assertEquals(MowanTimeline.stillClock(Mowan.Weather.SUNNY), s.poseClockMs, 0.0)
    }
}
