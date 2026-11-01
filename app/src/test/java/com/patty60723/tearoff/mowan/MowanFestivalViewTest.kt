package com.patty60723.tearoff.mowan

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.patty60723.tearoff.mowan.FestivalSession.Mode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import kotlin.random.Random

/**
 * P6-4a：節日接進 MowanView。時間、可見比例、視窗焦點由測試控制（Robolectric 不會真的捲動、也沒有真的焦點），
 * 觸發的事件（捲動、視窗焦點、可見度）照真的呼叫。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MowanFestivalViewTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val parent = FrameLayout(activity).also { activity.setContentView(it) }
    private val spring = LocalDate.of(2027, 2, 6)
    private val plain = LocalDate.of(2027, 3, 1)
    private val log = FestivalSession.MemoryLog()

    private var now = 10_000_000L
    private var fraction = 1f
    private var focused = true
    private var reduced = false

    private fun newView(sharedLog: FestivalSession.MainLog = log) = MowanView(activity).apply {
        parts = FestivalFixtures.parts(14)
        clock = { now }
        visibleFraction = { fraction }
        windowFocused = { focused }
        reducedMotion = { reduced }
        mainLog = sharedLog
        random = Random(1)
    }

    private fun attach(v: MowanView) {
        parent.addView(v, FrameLayout.LayoutParams(240, 320))
        parent.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 480, 640)
    }

    private val view = newView().also(::attach)

    private fun draw(v: MowanView = view) = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    /** 捲動事件（ViewTreeObserver.dispatchOnScrollChanged 不是公開的，用反射呼叫，確認監聽有註冊） */
    private fun scroll(v: MowanView = view) {
        android.view.ViewTreeObserver::class.java.getDeclaredMethod("dispatchOnScrollChanged").apply { isAccessible = true }.invoke(v.viewTreeObserver)
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun mains(v: MowanView = view) = v.session.starts.getValue(Mode.MAIN)

    // ---------- D-40：第一次可見才觸發，事件驅動 ----------

    @Test
    fun triggersOnlyWhenHalfVisibleFocusedAndForeground() {
        fraction = 0.3f
        view.today = spring
        assertEquals("不到一半", Mode.BASE, view.session.mode)
        assertTrue("沒記錄", log.keys.isEmpty())
        fraction = 0.6f; focused = false
        scroll()
        assertEquals("沒有焦點", Mode.BASE, view.session.mode)
        focused = true
        view.onWindowFocusChanged(true)
        assertEquals("拿到焦點：開始主演出", Mode.MAIN, view.session.mode)
        assertEquals(setOf("2027-02-06:SPRING"), log.keys)
    }

    @Test
    fun scrollingIntoViewTriggers() {
        fraction = 0f
        view.today = spring
        assertEquals(Mode.BASE, view.session.mode)
        fraction = 0.5f
        scroll()
        assertEquals("捲到一半就觸發", Mode.MAIN, view.session.mode)
    }

    @Test
    fun hiddenViewDoesNotTrigger() {
        view.visibility = View.GONE
        view.today = spring
        scroll()
        assertTrue("V3 關閉（故事卡的墨丸隱藏）時不觸發、不記錄（D-42）", log.keys.isEmpty())
        view.visibility = View.VISIBLE
        assertEquals("打開後第一次可見才演", Mode.MAIN, view.session.mode)
    }

    // ---------- 滑出、滑回、背景 ----------

    @Test
    fun slidingOutStopsDrawingAndSlidingBackDoesNotReplay() {
        view.today = spring
        draw()
        assertTrue("演出中每 33 ms 重畫", view.isTicking)
        assertEquals(MowanView.POSE_FRAME_MS, view.lastDelayMs)
        val start = view.session.startMs
        fraction = 0f
        scroll()
        assertFalse("完全滑出：不重畫", view.isTicking)
        draw()
        assertFalse("滑出時畫了一次也不排下一次", view.isTicking)
        now += 1000
        fraction = 1f
        scroll()
        draw()
        assertTrue(view.isTicking)
        assertEquals("滑回來不重播", start, view.session.startMs)
        assertEquals(1, mains())
    }

    @Test
    fun backgroundAndBackDoesNotReplay() {
        view.today = spring
        draw()
        view.visibility = View.INVISIBLE
        assertFalse(view.isTicking)
        now += 500
        view.visibility = View.VISIBLE
        draw()
        assertEquals(1, mains())
        assertEquals(Mode.MAIN, view.session.mode)
    }

    // ---------- 持久化 ----------

    @Test
    fun recreatingTheViewDoesNotReplay() {
        view.today = spring
        assertEquals(Mode.MAIN, view.session.mode)
        parent.removeView(view)
        val again = newView().also(::attach)
        again.today = spring
        scroll(again)
        assertEquals("同一份紀錄：不重播", Mode.BASE, again.session.mode)
        assertEquals(0, mains(again))
    }

    @Test
    fun sharedPreferencesLogSurvivesAndKeepsTen() {
        val prefsLog = MowanSettings.FestivalLog(activity)
        val v = newView(prefsLog)
        parent.removeView(view)
        attach(v)
        v.today = spring
        assertEquals(Mode.MAIN, v.session.mode)
        // 「重開 App」：新的紀錄物件讀同一個 prefs
        assertTrue(MowanSettings.FestivalLog(activity).played("2027-02-06:SPRING"))
        val fresh = MowanSettings.FestivalLog(activity)
        for (i in 1..12) fresh.markPlayed("2030-01-%02d:SPRING".format(i))
        val keys = MowanSettings.FestivalLog(activity).keys()
        assertEquals("最多 10 個（D-45）", MowanSettings.PLAYED_KEEP, keys.size)
        assertEquals("丟掉最舊的", "2030-01-03:SPRING", keys.first())
        assertFalse(MowanSettings.FestivalLog(activity).played("2027-02-06:SPRING"))
    }

    // ---------- 跨午夜、除錯重播 ----------

    @Test
    fun midnightKeepsTheRunningMainThenSwitches() {
        view.today = spring
        draw()
        val level = view.session.level
        now += 1000
        view.today = spring.plusDays(1)
        draw()
        assertEquals("演出中：日期還是初一", spring, view.session.date)
        assertEquals("層級不變", level, view.session.level)
        now += FestivalActions.Action.BOW.durMs
        draw()
        assertEquals("演完換成初二", spring.plusDays(1), view.session.date)
        assertEquals("初二自己的主演出", Mode.MAIN, view.session.mode)
        assertEquals(setOf("2027-02-06:SPRING", "2027-02-07:SPRING"), log.keys)
    }

    @Test
    fun debugReplayKeepsTheDailyRecord() {
        view.today = spring
        now += FestivalActions.Action.BOW.durMs + 100
        draw()
        assertEquals(Mode.BASE, view.session.mode)
        assertTrue(view.replayFestival())
        assertEquals(Mode.MAIN, view.session.mode)
        assertEquals(2, mains())
        assertEquals("重播不改紀錄", setOf("2027-02-06:SPRING"), log.keys)
        // 重開：一樣不重播；隔天照樣演
        val again = newView()
        parent.removeView(view)
        attach(again)
        again.today = spring
        assertEquals(Mode.BASE, again.session.mode)
        again.today = spring.plusDays(1)
        assertEquals(Mode.MAIN, again.session.mode)
    }

    // ---------- 雷雨、優先順序 ----------

    @Test
    fun noThunderAllDayOnFestivalDays() {
        view.weather = Mowan.Weather.THUNDER
        view.today = spring
        now += FestivalActions.Action.BOW.durMs + 100
        draw()
        assertEquals("演完了", Mode.BASE, view.session.mode)
        var strikes = 0
        repeat(12_000) {
            now += 50
            if (view.timeline.sample(now, false).sinceStrikeMs != null) strikes++
        }
        assertEquals("節日當天整天不打雷（10 分鐘）", 0, strikes)
        view.today = plain
        repeat(12_000) {
            now += 50
            if (view.timeline.sample(now, false).sinceStrikeMs != null) strikes++
        }
        assertTrue("不是節日照常打雷", strikes > 0)
    }

    @Test
    fun directorFollowsTheMain() {
        view.today = spring
        draw()
        assertTrue(view.director.aboveWeather())
        assertEquals(MowanDirector.Activity.FESTIVAL_MAIN, view.director.current(Mowan.Weather.RAINY))
        now += FestivalActions.Action.BOW.durMs + 100
        draw()
        assertFalse(view.director.aboveWeather())
    }

    // ---------- 沒有節日：跟以前一樣 ----------

    @Test
    fun noFestivalDrawsAndSchedulesLikeBefore() {
        val before = newView().also { parent.removeView(view); attach(it) }
        val after = newView().also(::attach).apply { today = plain }
        for (w in listOf(Mowan.Weather.NONE, Mowan.Weather.SUNNY, Mowan.Weather.RAINY, Mowan.Weather.THUNDER)) {
            before.weather = w
            after.weather = w
            now += 700
            val a = draw(before)
            val b = draw(after)
            assertArrayEquals("$w 畫面一樣", pixels(a), pixels(b))
            assertEquals("$w 重畫排程一樣", before.isTicking, after.isTicking)
            assertEquals("$w 重畫間隔一樣", before.lastDelayMs, after.lastDelayMs)
        }
        assertNull(after.festival)
        assertFalse(after.director.aboveWeather())
    }

    // ---------- Reduced Motion ----------

    @Test
    fun reducedMotionHoldsTheKeyPoseThenReturns() {
        reduced = true
        view.today = spring
        assertEquals(Mode.MAIN, view.session.mode)
        assertTrue("停在代表姿勢", view.session.held)
        assertEquals("照樣記錄", setOf("2027-02-06:SPRING"), log.keys)
        draw()
        assertTrue("排一次重畫回到站姿", view.isTicking)
        assertEquals("剛好在演出時間結束時", FestivalActions.Action.BOW.durMs, view.lastDelayMs)
        now += FestivalActions.Action.BOW.durMs
        draw()
        assertEquals(Mode.BASE, view.session.mode)
        assertFalse("回到站姿後不再排重畫", view.isTicking)
        now += 120_000
        draw()
        assertEquals("關閉動畫時不排待機", 0, view.session.starts.getValue(Mode.IDLE))
    }

    // ---------- 待機只在看得到時（D-46），重畫排程 ----------

    private val canvas = Canvas(Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888))

    /** 照 View 自己排的下一次重畫往前走 [ms] 毫秒，每次記下（重畫的時間, 模式, 下一次間隔） */
    private fun runFor(ms: Long, v: MowanView = view): List<Triple<Long, Mode, Long>> {
        val end = now + ms
        val out = ArrayList<Triple<Long, Mode, Long>>()
        while (now < end) {
            assertTrue("一直排著下一次重畫", v.isTicking)
            now += v.lastDelayMs
            v.draw(canvas)
            out += Triple(now, v.session.mode, v.lastDelayMs)
        }
        return out
    }

    private val boilMs = kotlin.math.ceil(Mowan.BOIL_MS).toLong()

    /**
     * 筆觸影格的重畫間隔：到下一張筆觸（約 166 ms）。既有排程把間隔捨去成整數毫秒，會落在影格邊界前一點點，
     * 所以接著補一次 1 ms（P5 以來就是這樣，沒有節日時也一樣；這裡只確認回到這個排程，不是 33 ms）。
     */
    private fun boilDelay(r: Triple<Long, Mode, Long>) = r.third <= 1 || r.third >= boilMs - 2

    /** 每秒換幾張筆觸（間隔約 166 ms 的那些重畫） */
    private fun boilsPerSecond(rs: List<Triple<Long, Mode, Long>>) =
        rs.count { it.third >= boilMs - 2 } * 1000.0 / (rs.last().first - rs.first().first)

    @Test
    fun idleRunsOnlyWhileVisible() {
        view.today = spring
        now += FestivalActions.Action.BOW.durMs + 100
        draw()
        fraction = 0f
        scroll()
        assertFalse("滑出：不重畫", view.isTicking)
        now += 60_000
        // 看不到時沒有任何重畫，所以沒有待機
        assertEquals(0, view.session.starts.getValue(Mode.IDLE))
        fraction = 1f
        scroll()
        val back = now
        draw()
        assertEquals("回來時不補播錯過的待機", Mode.BASE, view.session.mode)
        val idleAt = runFor(FestivalSession.IDLE_MAX_MS + 1000).first { it.second == Mode.IDLE }.first
        assertTrue("看得到以後重新倒數 20–40 秒才演待機", idleAt - back in FestivalSession.IDLE_MIN_MS..FestivalSession.IDLE_MAX_MS + boilMs)
        assertEquals(1, view.session.starts.getValue(Mode.IDLE))
    }

    @Test
    fun backgroundDoesNotCountTowardsTheIdle() {
        view.today = spring
        now += FestivalActions.Action.BOW.durMs + 100
        draw()
        view.visibility = View.INVISIBLE
        assertFalse("背景：不重畫", view.isTicking)
        now += 60_000
        view.visibility = View.VISIBLE
        val back = now
        draw()
        assertEquals("回來時不補播", Mode.BASE, view.session.mode)
        assertTrue(view.session.nextIdleMs - back >= FestivalSession.IDLE_MIN_MS)
    }

    @Test
    fun boilAfterTheMainThenIdleOnTimeAtThePoseRateThenBoilAgain() {
        view.today = spring
        draw()
        // 主演出：每 33 ms
        val main = runFor(FestivalActions.Action.BOW.durMs - 100)
        assertTrue(main.all { it.second == Mode.MAIN && it.third == MowanView.POSE_FRAME_MS })
        val toBase = runFor(500).first { it.second == Mode.BASE }
        val due = view.session.nextIdleMs
        assertTrue("演完排 20–40 秒後的待機", due - toBase.first in FestivalSession.IDLE_MIN_MS..FestivalSession.IDLE_MAX_MS)
        // 1. 演完：一般站姿的 6 FPS 筆觸，只在換筆觸影格時重畫
        val untilIdle = runFor(due - now + boilMs)
        val base = untilIdle.takeWhile { it.second == Mode.BASE }
        assertTrue("排定時間以前都是站姿", base.all { it.first < due })
        assertTrue("不再 33 ms 重畫", base.drop(1).all(::boilDelay))
        val perSecond = boilsPerSecond(base)
        assertTrue("筆觸約每秒 6 張（$perSecond）", perSecond in 5.0..7.0)
        // 2. 待機準時：在排定時間後的第一次筆觸重畫開始（最多晚一張筆觸影格）
        val idle = untilIdle[base.size]
        assertEquals(Mode.IDLE, idle.second)
        assertTrue("待機開始時間 ${idle.first - due} ms", idle.first - due in 0..boilMs)
        assertEquals(1, view.session.starts.getValue(Mode.IDLE))
        // 3. 待機中：每 33 ms
        val idleDur = view.session.action!!.durMs
        val during = runFor(idleDur - (now - view.session.startMs) - 100)
        assertTrue(during.all { it.second == Mode.IDLE && it.third == MowanView.POSE_FRAME_MS })
        // 4. 待機結束：回到筆觸排程，重新排下一次待機
        val after = runFor(3000)
        val back = after.indexOfFirst { it.second == Mode.BASE }
        assertTrue(back >= 0)
        val rest = after.drop(back + 1)
        assertTrue("回到站姿後不再 33 ms 重畫", rest.all { it.second == Mode.BASE && boilDelay(it) })
        assertTrue("筆觸約每秒 6 張", boilsPerSecond(rest) in 5.0..7.0)
        assertTrue(view.session.nextIdleMs - after[back].first in FestivalSession.IDLE_MIN_MS..FestivalSession.IDLE_MAX_MS)
        assertEquals("主演出沒有重播", 1, mains())
    }

    @Test
    fun weatherAndIdleRunTogetherWithoutReplayingTheMain() {
        view.weather = Mowan.Weather.RAINY
        view.today = spring
        draw()
        runFor(FestivalActions.Action.BOW.durMs + 100)
        assertEquals(Mode.BASE, view.session.mode)
        // 有天氣動畫：站姿也每 33 ms（雨在動），待機照樣準時
        val due = view.session.nextIdleMs
        val untilIdle = runFor(due - now + MowanView.POSE_FRAME_MS)
        val base = untilIdle.takeWhile { it.second == Mode.BASE }
        assertTrue(base.all { it.first < due && it.third == MowanView.POSE_FRAME_MS })
        val idle = untilIdle[base.size]
        assertEquals(Mode.IDLE, idle.second)
        assertTrue(idle.first - due in 0..MowanView.POSE_FRAME_MS)
        val start = view.session.startMs
        // 待機中換天氣：待機繼續、不重播主演出
        view.weather = Mowan.Weather.SUNNY
        val during = runFor(300)
        assertTrue(during.all { it.second == Mode.IDLE && it.third == MowanView.POSE_FRAME_MS })
        assertEquals(start, view.session.startMs)
        view.weather = Mowan.Weather.SNOW
        runFor(view.session.action!!.durMs)
        assertEquals(Mode.BASE, view.session.mode)
        assertEquals("主演出沒有重播", 1, mains())
        assertEquals(1, view.session.starts.getValue(Mode.IDLE))
        assertTrue("雪還在下：33 ms", view.lastDelayMs == MowanView.POSE_FRAME_MS)
    }

    @Test
    fun reducedMotionSchedulesNothingAfterTheHold() {
        reduced = true
        view.weather = Mowan.Weather.RAINY
        view.today = spring
        draw()
        assertEquals(FestivalActions.Action.BOW.durMs, view.lastDelayMs)
        now += view.lastDelayMs
        draw()
        assertEquals(Mode.BASE, view.session.mode)
        assertFalse("有天氣也不排重畫", view.isTicking)
        assertEquals(Long.MAX_VALUE, view.session.nextIdleMs)
    }
}
