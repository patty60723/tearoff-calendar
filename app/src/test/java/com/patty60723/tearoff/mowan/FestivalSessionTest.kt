package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import com.patty60723.tearoff.mowan.FestivalSession.Mode
import com.patty60723.tearoff.mowan.Mowan.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * 節日狀態機（P6-1）：原型 mowan-festival.html 的 T01–T09 移植，加上 D-33 到 D-36 的規則。
 * 原型用「第幾天」當 key、存在記憶體；這裡 key 是日期 + 節日，存在 [FestivalSession.MainLog]。
 */
class FestivalSessionTest {
    private val day1 = LocalDate.of(2027, 2, 6)   // 春節初一
    private val xmas = LocalDate.of(2027, 12, 25)
    private val plain = LocalDate.of(2027, 3, 1)

    private inner class Sim(
        var date: LocalDate = day1,
        var weather: Weather = Weather.SUNNY,
        var page: Int = 14,
        val log: FestivalSession.MainLog = FestivalSession.MemoryLog(),
        var reduced: Boolean = false,
        seed: Int = 1,
    ) {
        val session = FestivalSession(log, Random(seed))
        var now = 0L
        val festival get() = FestivalTheme.of(date)
        val scene get() = FestivalFixtures.scene(page, festival, weather)
        fun enter() = session.enter(date, festival, now, scene, reduced)
        fun step(ms: Long) {
            var t = 0L
            while (t < ms) { now += 50; t += 50; session.update(now, scene, reduced) }
        }
        fun changeWeather(w: Weather) { val from = weather; weather = w; session.weatherChanged(from, w) }
        fun mains() = session.starts.getValue(Mode.MAIN)
        fun idles() = session.starts.getValue(Mode.IDLE)
    }

    private val springMain = Action.BOW.durMs

    // ---------- 原型 P1-01 ----------

    @Test
    fun t01_firstEntryPlaysMainOnce() {
        val m = Sim(); m.enter()
        assertEquals(Mode.MAIN, m.session.mode)
        assertEquals(1, m.mains())
    }

    @Test
    fun t02_weatherChangesDuringMainDoNotRestartIt() {
        val m = Sim(); m.enter(); val start = m.session.startMs
        m.step(800); m.changeWeather(Weather.RAINY); m.step(400); m.changeWeather(Weather.CLOUDY); m.step(400); m.changeWeather(Weather.SUNNY)
        assertEquals(Mode.MAIN, m.session.mode)
        assertEquals(start, m.session.startMs)
        assertEquals(1, m.mains())
    }

    @Test
    fun t03_weatherChangesAfterMainKeepIdle() {
        val m = Sim(); m.enter(); m.step(springMain + 100)
        assertEquals(Mode.BASE, m.session.mode)
        for (w in listOf(Weather.RAINY, Weather.CLOUDY, Weather.SUNNY, Weather.THUNDER, Weather.SNOW)) m.changeWeather(w)
        m.enter()
        assertEquals(Mode.BASE, m.session.mode)
        assertEquals(1, m.mains())
    }

    @Test
    fun t04_debugReplayStillWorks() {
        val m = Sim(); m.enter(); m.step(springMain + 100)
        assertTrue(m.session.replay(m.now, m.scene, false))
        assertEquals(Mode.MAIN, m.session.mode)
        assertEquals(2, m.mains())
    }

    @Test
    fun t05_storyInterruptsMainThenBackToIdleWithoutReplay() {
        val m = Sim(); m.enter(); m.step(1000)
        assertTrue(m.session.story(m.now, 2000, false))
        assertEquals(Mode.STORY, m.session.mode)
        m.step(2100)
        assertEquals(Mode.BASE, m.session.mode)
        assertEquals(1, m.mains())
        assertTrue(m.session.events.any { "回到節日待機" in it })
    }

    @Test
    fun t06_noFestivalNoMainNoIdleReplayIgnored() {
        val m = Sim(date = plain); m.enter()
        assertFalse(m.session.replay(0, m.scene, false))
        m.step(120_000)
        assertEquals(0, m.mains())
        assertEquals(0, m.idles())
        assertTrue((m.log as FestivalSession.MemoryLog).keys.isEmpty())
    }

    @Test
    fun t07_unseenFestivalPlaysSeenOneDoesNot() {
        val m = Sim(); m.enter(); m.step(springMain + 100)
        m.date = xmas; m.enter()
        assertEquals(Mode.MAIN, m.session.mode)
        m.step(Action.SOCK.durMs + 100)
        m.date = day1; m.enter()
        assertEquals(Mode.BASE, m.session.mode)
        assertEquals(2, m.mains())
    }

    /** T08：隔天再播。春節初一、初二、初三是三個不同的日期，各自演一次 */
    @Test
    fun t08_nextDayPlaysAgain() {
        val m = Sim()
        for (i in 0L..2L) {
            m.date = day1.plusDays(i); m.enter()
            assertEquals("初${i + 1}", Mode.MAIN, m.session.mode)
            m.step(springMain + 100)
        }
        assertEquals(3, m.mains())
        assertEquals(
            listOf("2027-02-06:SPRING", "2027-02-07:SPRING", "2027-02-08:SPRING"),
            (m.log as FestivalSession.MemoryLog).keys.toList(),
        )
        m.date = day1.plusDays(3); m.enter()
        assertEquals("初四不是節日", 3, m.mains())
    }

    @Test
    fun t09_idleOnlyFromBaseAndWeatherDoesNotInterruptIt() {
        val m = Sim(); m.enter()
        m.step(springMain + 100 + FestivalSession.IDLE_MAX_MS)
        assertTrue("待機演過", m.idles() > 0 || m.session.mode == Mode.IDLE)
        while (m.session.mode != Mode.IDLE) m.step(50)
        val s0 = m.session.startMs
        m.changeWeather(Weather.RAINY)
        m.step(50)
        assertEquals(Mode.IDLE, m.session.mode)
        assertEquals(s0, m.session.startMs)
        assertEquals(Action.LOOK_ENVELOPE, m.session.action)
        assertEquals(1, m.mains())
    }

    // ---------- D-33：開始播就記下來 ----------

    @Test
    fun recordedWhenItStartsSoARestartDoesNotReplay() {
        val log = FestivalSession.MemoryLog()
        val first = Sim(log = log); first.enter(); first.step(300)
        assertEquals(Mode.MAIN, first.session.mode)
        assertEquals(setOf("2027-02-06:SPRING"), log.keys)
        // 演到一半切到背景、程式被關掉，重開（新的 Session、同一份紀錄）
        val again = Sim(log = log); again.enter()
        assertEquals(Mode.BASE, again.session.mode)
        assertEquals(0, again.mains())
    }

    @Test
    fun keyIsDateAndFestival() {
        assertEquals("2027-12-25:XMAS", FestivalSession.key(xmas, Festival.XMAS))
        val m = Sim(date = xmas); m.enter()
        assertEquals(setOf("2027-12-25:XMAS"), (m.log as FestivalSession.MemoryLog).keys)
    }

    @Test
    fun enteringTheSameDayAgainDoesNothing() {
        val m = Sim(); m.enter(); m.step(500)
        val start = m.session.startMs
        m.enter()
        assertEquals(start, m.session.startMs)
        assertEquals(1, m.mains())
    }

    // ---------- D-34：Reduced Motion ----------

    @Test
    fun reducedMotionHoldsTheKeyPoseAndStillRecords() {
        val m = Sim(reduced = true); m.enter()
        assertEquals(Mode.MAIN, m.session.mode)
        assertTrue(m.session.held)
        assertEquals(Action.BOW.keyMs, m.session.actionTime(m.now + 1234))
        assertEquals(setOf("2027-02-06:SPRING"), (m.log as FestivalSession.MemoryLog).keys)
        m.step(springMain + 100)
        assertEquals(Mode.BASE, m.session.mode)
        m.step(120_000)
        assertEquals("關閉動畫時不排待機", 0, m.idles())
        // 打開動畫：重新排待機
        m.reduced = false
        m.session.reducedChanged(false, m.now)
        m.step(FestivalSession.IDLE_MAX_MS + 100)
        assertTrue(m.idles() > 0)
    }

    @Test
    fun resumedRestartsTheIdleCountdown() {
        val m = Sim(); m.enter()
        m.step(springMain + 100)
        val due = m.session.nextIdleMs
        // 看不到的期間沒有 update；回來時已經超過原本排定的時間
        m.now = due + 60_000
        m.session.resumed(m.now, reduced = false)
        assertTrue("從回來的時間重新倒數 20–40 秒", m.session.nextIdleMs - m.now in FestivalSession.IDLE_MIN_MS..FestivalSession.IDLE_MAX_MS)
        m.session.update(m.now, m.scene, false)
        assertEquals("回來時不補播待機", 0, m.idles())
        // 演出中回來：不動演出、也不動待機排程
        m.session.replay(m.now, m.scene, false)
        val start = m.session.startMs
        m.session.resumed(m.now + 500, reduced = false)
        assertEquals(Mode.MAIN, m.session.mode)
        assertEquals(start, m.session.startMs)
    }

    // ---------- D-35：層級鎖定 ----------

    @Test
    fun levelIsLockedUntilTheEnd() {
        val m = Sim(); m.enter()
        assertEquals(Level.FULL, m.session.level)
        m.changeWeather(Weather.RAINY)
        m.step(1000)
        assertEquals("演出中換成雨天，層級不變", Level.FULL, m.session.level)
        assertEquals("新的場景已經是撐傘", Level.RAIN, FestivalActions.levelFor(Action.BOW, m.scene))
        m.step(springMain)
        assertEquals(Mode.BASE, m.session.mode)
        assertTrue(m.session.replay(m.now, m.scene, false))
        assertEquals("下一次演出用新的層級", Level.RAIN, m.session.level)
    }

    @Test
    fun levelComesFromCapabilities() {
        for ((page, want) in listOf(1 to Level.LINE, 2 to Level.HEAD, 4 to Level.ONE_HAND, 5 to Level.FULL, 14 to Level.FULL)) {
            val m = Sim(page = page); m.enter()
            assertEquals("第 $page 頁", want, m.session.level)
        }
    }

    // ---------- D-36：跨午夜 ----------

    @Test
    fun midnightDuringMainFinishesThenUsesTheNewDate() {
        val eve = LocalDate.of(2027, 2, 5)
        val m = Sim(date = eve); m.enter()
        assertEquals("除夕沒有節日", Mode.BASE, m.session.mode)
        m.date = day1; m.enter()
        assertEquals(Mode.MAIN, m.session.mode)
        m.step(1000)
        // 演到一半過了午夜（初一 → 初二）：先演完
        m.date = day1.plusDays(1); m.enter()
        assertEquals(day1, m.session.date)
        assertEquals(Mode.MAIN, m.session.mode)
        m.step(springMain)
        assertEquals("演完就換新日期，初二自己的主演出", day1.plusDays(1), m.session.date)
        assertEquals(Mode.MAIN, m.session.mode)
        assertEquals(2, m.mains())
    }

    @Test
    fun midnightIntoAPlainDayStopsIdles() {
        val m = Sim(date = day1.plusDays(2)); m.enter(); m.step(springMain + 100)
        m.date = day1.plusDays(3); m.enter()
        assertNull(m.session.festival)
        m.step(120_000)
        assertEquals(0, m.idles())
    }

    // ---------- 待機間隔、優先順序 ----------

    @Test
    fun idleIntervalIsTwentyToFortySecondsAndReproducible() {
        fun gaps(seed: Int): List<Long> {
            val m = Sim(seed = seed); m.enter(); m.step(springMain + 100)
            val out = ArrayList<Long>()
            repeat(5) {
                val from = m.now
                while (m.session.mode != Mode.IDLE) m.step(50)
                out += m.now - from
                m.step(Action.LOOK_ENVELOPE.durMs + 50)
            }
            return out
        }
        val a = gaps(7)
        assertEquals("同一個種子結果一樣", a, gaps(7))
        a.forEach { assertTrue("$it", it in 20_000L..40_100L) }
    }

    @Test
    fun modesMapToTheDirector() {
        assertEquals(MowanDirector.Activity.FESTIVAL_MAIN, Mode.MAIN.activity)
        assertEquals(MowanDirector.Activity.FESTIVAL_IDLE, Mode.IDLE.activity)
        assertEquals(MowanDirector.Activity.GROWTH_STORY, Mode.STORY.activity)
        assertNull(Mode.BASE.activity)
    }

    @Test
    fun idleCannotInterruptMain() {
        val m = Sim(); m.enter()
        m.step(FestivalSession.IDLE_MAX_MS) // 主演出 4.2 秒後才開始排待機
        assertEquals(1, m.mains())
        assertTrue(m.session.events.none { "被擋下" in it })
    }

    // ---------- 日期邊界（P6-1 審核強化） ----------

    /** 演出中跨午夜：演完才用新日期，新日期自己的每日一次照樣判斷 */
    @Test
    fun pendingDateIsUsedAfterTheMainEnds() {
        val m = Sim(); m.enter(); m.step(1000)
        m.date = day1.plusDays(1); m.enter()
        assertEquals("還在演初一", day1, m.session.date)
        m.step(springMain - 1000 + 50)
        assertEquals(day1.plusDays(1), m.session.date)
        assertEquals("初二自己的主演出", Mode.MAIN, m.session.mode)
        assertEquals(setOf("2027-02-06:SPRING", "2027-02-07:SPRING"), (m.log as FestivalSession.MemoryLog).keys)
    }

    /** 演出中多次換模擬日期：演完用最後一次的日期 */
    @Test
    fun latestPendingDateWins() {
        val m = Sim(); m.enter(); m.step(500)
        for (d in listOf(xmas, plain, day1.plusDays(2))) { m.date = d; m.enter() }
        m.step(springMain)
        assertEquals(day1.plusDays(2), m.session.date)
        assertEquals(Festival.SPRING, m.session.festival)
        assertEquals("中間經過的聖誕沒有被記成演過", setOf("2027-02-06:SPRING", "2027-02-08:SPRING"), (m.log as FestivalSession.MemoryLog).keys)
    }

    /** 換過去又換回原日期：不套用過時的 pending */
    @Test
    fun switchingBackDropsTheStalePendingDate() {
        val m = Sim(); m.enter(); m.step(500)
        m.date = xmas; m.enter()
        m.date = day1; m.enter()
        m.step(springMain + 100)
        assertEquals(day1, m.session.date)
        assertEquals(Mode.BASE, m.session.mode)
        assertEquals(1, m.mains())
        assertEquals(setOf("2027-02-06:SPRING"), (m.log as FestivalSession.MemoryLog).keys)
        // 之後過了很久也不會冒出聖誕
        m.step(120_000)
        assertEquals(day1, m.session.date)
        assertEquals(1, m.mains())
    }

    /** 故事事件中跨午夜也一樣：故事結束才換日期 */
    @Test
    fun pendingDateAlsoWaitsForStory() {
        val m = Sim(date = plain); m.enter()
        m.session.story(m.now, 2000, false)
        m.date = day1; m.enter()
        assertEquals(plain, m.session.date)
        m.step(2100)
        assertEquals(day1, m.session.date)
        assertEquals(Mode.MAIN, m.session.mode)
    }

    /** 日期改了、節日沒變（初一 → 初二）：依新日期判斷，各自一次；已經演過的日期重進不演 */
    @Test
    fun sameFestivalNewDateIsJudgedByDate() {
        val log = FestivalSession.MemoryLog()
        val m = Sim(log = log); m.enter(); m.step(springMain + 100)
        m.date = day1.plusDays(1); m.enter()
        assertEquals(Mode.MAIN, m.session.mode)
        m.step(springMain + 100)
        // 重開 App 回到初二（同一份紀錄）：不演
        val again = Sim(date = day1.plusDays(1), log = log); again.enter()
        assertEquals(Mode.BASE, again.session.mode)
        // 模擬日期倒回初一：初一已經演過，也不演
        again.date = day1; again.enter()
        assertEquals(Mode.BASE, again.session.mode)
        assertEquals(0, again.mains())
    }

    /** 待機中換日期：不用等，直接換 */
    @Test
    fun dateChangeDuringIdleAppliesImmediately() {
        val m = Sim(); m.enter(); m.step(springMain + 100)
        while (m.session.mode != Mode.IDLE) m.step(50)
        m.date = day1.plusDays(1); m.enter()
        assertEquals(day1.plusDays(1), m.session.date)
        assertEquals(Mode.MAIN, m.session.mode)
    }
}
