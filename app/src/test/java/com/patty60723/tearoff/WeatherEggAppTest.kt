package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.mowan.MowanDirector
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.mowan.WeatherEgg
import com.patty60723.tearoff.mowan.WeatherEggSession.Outcome
import com.patty60723.tearoff.mowan.WeatherEggSession.State
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.time.LocalDate
import kotlin.math.abs

/**
 * P7-9b：天氣接力彩蛋接進正式 App（D-48 補充）。
 * - 手勢：點天氣資訊播彩蛋、不撕頁；從紙角拖是撕頁；在天氣資訊上按下後移動超過 touchSlop 是撕頁、不是點擊；撕頁中不啟動；播放中開始撕頁會取消。
 * - 播放：晴、雨、雪三種；動畫前、中、後日曆頁、操作區、故事卡的位置都不變，也沒有要求重新排版。
 * - 小墨丸：播放時靜態圖藏起來、疊層畫動畫（不會兩隻）；第 0 格跟靜態圖一模一樣；播完換回靜態圖。
 * - 狀態：連點忽略、播完 1 秒內不重播、離開 App／換頁／天氣更新／Reduced Motion 取消；結束後沒有殘留的疊層、掛勾或排程。
 * - 優先順序：成長故事不反應；節日、Reduced Motion、另外五種天氣、還沒長齊只換一句話（固定高度）。
 * - 故事卡墨丸原本的點擊（P7-2）照舊；小工具的靜態圖不受影響。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WeatherEggAppTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val plain = LocalDate.of(2027, 10, 21)
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        MowanSettings.resetEquipment(context)
        RuntimeEnvironment.setFontScale(1f)
        controller = null
    }

    private fun open(day: LocalDate = plain, mood: Mood? = Mood.SUNNY, page: Int = 13): MainActivity {
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, mood)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(page, day); tear(day) }
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller = c
        return c.get().also { relayout(it) }
    }

    private fun relayout(a: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val d = a.window.decorView
        d.measure(View.MeasureSpec.makeMeasureSpec(d.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(d.height, View.MeasureSpec.EXACTLY))
        d.layout(d.left, d.top, d.right, d.bottom)
    }

    private val MainActivity.content get() = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout
    private val MainActivity.mowan get() = findViewById<MowanView>(R.id.story_mowan)
    private val MainActivity.pad get() = findViewById<TearPageView>(R.id.page)
    private val MainActivity.still get() = pad.findViewById<ImageView>(R.id.mowan_still)
    private val MainActivity.sun get() = pad.findViewById<TextView>(R.id.sun)
    private val MainActivity.text get() = findViewById<TextView>(R.id.story_text).text.toString()
    private fun line(mood: Mood) = "${StoryBook(context).name}：${mood.line}"

    private fun advance(ms: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** 日曆頁、操作區、故事卡、故事文字在內容裡的位置 */
    private fun places(a: MainActivity): List<Rect> = listOf(
        a.pad, a.findViewById(R.id.action_slot), a.mowan.parent as View, a.findViewById(R.id.story_text), a.mowan,
    ).map { GlyphInk.rectIn(a.content, it) }

    private fun center(a: MainActivity, v: View): Pair<Float, Float> {
        val loc = IntArray(2).also { v.getLocationInWindow(it) }
        return loc[0] + v.width / 2f to loc[1] + v.height / 2f
    }

    private fun touch(a: MainActivity, action: Int, x: Float, y: Float, downAt: Long) {
        val e = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0)
        a.window.decorView.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun tapSun(a: MainActivity) {
        val (x, y) = center(a, a.sun)
        val t = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, x, y, t)
        touch(a, MotionEvent.ACTION_UP, x, y, t)
    }

    /** 沒有任何殘留：疊層空了、靜態墨丸顯示、沒有排程、大墨丸交還 */
    private fun assertClean(a: MainActivity, what: String) {
        assertFalse("$what：沒有在播", a.egg.playing)
        assertFalse("$what：疊層清空", a.eggOverlay.active)
        assertFalse("$what：沒有排著下一格", a.egg.ticking)
        assertEquals("$what：靜態墨丸顯示", View.VISIBLE, a.still.visibility)
        val canvas = Canvas(Bitmap.createBitmap(a.eggOverlay.width.coerceAtLeast(1), a.eggOverlay.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888))
        a.eggOverlay.draw(canvas)
        assertFalse("$what：疊層不畫小墨丸", a.eggOverlay.lastSmallDrawn)
        assertEquals("$what：疊層不畫素材", 0, a.eggOverlay.lastMotes)
    }

    private fun drawBig(a: MainActivity) = a.mowan.draw(Canvas(Bitmap.createBitmap(a.mowan.width, a.mowan.height, Bitmap.Config.ARGB_8888)))

    // ---------- 手勢 ----------

    @Test
    fun tappingTheWeatherInfoPlaysTheEggAndDoesNotPeel() {
        val a = open()
        assertNull(a.lastWeatherTap)
        tapSun(a)
        assertEquals(Outcome.PLAY, a.lastWeatherTap)
        assertFalse("沒有撕頁", a.pad.busy)
        assertTrue(a.egg.playing)
        assertTrue(a.eggOverlay.active)
        assertTrue(a.mowan.eggPlaying)
        assertEquals("靜態墨丸藏起來（不會兩隻）", View.INVISIBLE, a.still.visibility)
        assertEquals("日曆頁還在", View.VISIBLE, a.pad.getChildAt(0).visibility)
    }

    @Test
    fun theWeatherInfoIsInsideTheTearCornerButTheCornerItselfStillPeelsAtOnce() {
        val a = open()
        // 天氣資訊在左下撕頁區裡
        val r = Rect(0, 0, a.sun.width, a.sun.height).also { a.pad.offsetDescendantRectToMyCoords(a.sun, it) }
        val zone = a.pad.width * 0.35f
        assertTrue("天氣資訊在左下撕頁區裡：$r", r.centerX() < zone && r.centerY() > a.pad.height - zone)
        // 紙角本身（不在天氣資訊上）：按下就開始撕，跟以前一樣
        val loc = IntArray(2).also { a.pad.getLocationInWindow(it) }
        val t = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, loc[0] + 3f, loc[1] + a.pad.height - 3f, t)
        assertTrue("左下紙角按下就撕", a.pad.busy)
        assertNull("不是點天氣", a.lastWeatherTap)
        touch(a, MotionEvent.ACTION_UP, loc[0] + 3f, loc[1] + a.pad.height - 3f, t)
        advance(600)
        assertFalse(a.pad.busy)
        // 右下紙角也一樣
        val t2 = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, loc[0] + a.pad.width - 3f, loc[1] + a.pad.height - 3f, t2)
        assertTrue("右下紙角按下就撕", a.pad.busy)
        touch(a, MotionEvent.ACTION_UP, loc[0] + a.pad.width - 3f, loc[1] + a.pad.height - 3f, t2)
        advance(600)
        assertNull(a.lastWeatherTap)
    }

    @Test
    fun draggingFromTheWeatherInfoPeelsAndIsNotATap() {
        val a = open()
        val slop = ViewConfiguration.get(a).scaledTouchSlop
        val (x, y) = center(a, a.sun)
        val t = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, x, y, t)
        assertFalse("按下還沒撕（等看是點擊還是拖曳）", a.pad.busy)
        // touchSlop 以內：還不算拖曳
        touch(a, MotionEvent.ACTION_MOVE, x + slop * 0.5f, y, t)
        assertFalse(a.pad.busy)
        // 超過 touchSlop：開始撕頁
        touch(a, MotionEvent.ACTION_MOVE, x + slop * 3f, y - slop * 3f, t)
        assertTrue("拖曳超過 touchSlop 就是撕頁", a.pad.busy)
        touch(a, MotionEvent.ACTION_UP, x + slop * 3f, y - slop * 3f, t)
        assertNull("不誤觸彩蛋", a.lastWeatherTap)
        assertFalse(a.egg.playing)
        advance(600)
        assertFalse(a.pad.busy)
    }

    @Test
    fun aSmallWobbleWithinTouchSlopIsStillATap() {
        val a = open()
        val slop = ViewConfiguration.get(a).scaledTouchSlop
        val (x, y) = center(a, a.sun)
        val t = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, x, y, t)
        touch(a, MotionEvent.ACTION_MOVE, x + slop * 0.6f, y - slop * 0.6f, t)
        touch(a, MotionEvent.ACTION_UP, x + slop * 0.6f, y - slop * 0.6f, t)
        assertEquals(Outcome.PLAY, a.lastWeatherTap)
        assertFalse(a.pad.busy)
    }

    @Test
    fun aCancelledTouchIsNeitherATapNorATear() {
        val a = open()
        val (x, y) = center(a, a.sun)
        val t = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, x, y, t)
        touch(a, MotionEvent.ACTION_CANCEL, x, y, t)
        assertNull(a.lastWeatherTap)
        assertFalse(a.pad.busy)
    }

    @Test
    fun tearingDoesNotStartTheEgg() {
        val a = open()
        a.pad.holdPeel(rightCorner = true, x = a.pad.width * 0.6f, y = a.pad.height * 0.6f)
        assertTrue(a.pad.busy)
        a.tapWeather()
        assertEquals(Outcome.IGNORED, a.lastWeatherTap)
        assertFalse(a.egg.playing)
    }

    @Test
    fun startingToTearCancelsTheEggAndTheBigMowanBlendsBack() {
        val a = open(mood = Mood.RAIN)
        tapSun(a)
        advance(4_000)
        assertTrue(a.egg.playing)
        // 手指從右下紙角開始撕
        val loc = IntArray(2).also { a.pad.getLocationInWindow(it) }
        val t = SystemClock.uptimeMillis()
        touch(a, MotionEvent.ACTION_DOWN, loc[0] + a.pad.width - 3f, loc[1] + a.pad.height - 3f, t)
        assertTrue(a.pad.busy)
        assertClean(a, "開始撕頁")
        // 大墨丸用一小段時間接回日常，之後自己交還
        assertTrue("還在接回日常", a.mowan.eggPlaying)
        advance(WeatherEggController.BLEND_OUT_MS + 50)
        drawBig(a)
        assertFalse("接回日常以後交還", a.mowan.eggPlaying)
        touch(a, MotionEvent.ACTION_UP, loc[0] + a.pad.width - 3f, loc[1] + a.pad.height - 3f, t)
        advance(600)
        assertFalse(a.pad.busy)
        assertEquals("撕頁的那一張快照裡有靜態墨丸", View.VISIBLE, a.still.visibility)
    }

    @Test
    fun theTearButtonAlsoCancelsTheEgg() {
        val a = open()
        tapSun(a)
        advance(1_000)
        a.pad.peelAway()
        assertClean(a, "按鈕撕頁")
    }

    // ---------- 播放：三種天氣、位置不變、清理 ----------

    @Test
    fun allThreeWeathersPlayThroughWithoutMovingAnythingAndCleanUp() {
        val out = File("build/screenshots/p7-9b").apply { mkdirs() }
        val report = StringBuilder("# P7-9b 天氣接力彩蛋：播放流程（Robolectric，411dp）\n\n")
        report.append("| 天氣 | 時間 | 靜態墨丸 | 疊層小墨丸 | 素材 | 大墨丸 | 位置（日曆頁、操作區、故事卡、文字、大墨丸） |\n|---|---|---|---|---|---|---|\n")
        for ((mood, script) in listOf(Mood.SUNNY to WeatherEgg.Sunny, Mood.RAIN to WeatherEgg.Rainy, Mood.SNOW to WeatherEgg.Snowy)) {
            val a = open(mood = mood)
            val before = places(a)
            val story = a.text
            val parts = Mowan.partsOf(StoryBook(context).parts)
            tapSun(a)
            assertEquals("$mood", Outcome.PLAY, a.lastWeatherTap)
            assertEquals(script, a.egg.session.script)
            val start = SystemClock.uptimeMillis()
            val canvas = Canvas(Bitmap.createBitmap(a.eggOverlay.width, a.eggOverlay.height, Bitmap.Config.ARGB_8888))
            var sawSmallMotes = false
            var sawBigPose = false
            val checkpoints = listOf(0L, 150, 600, 1_200, 2_000, 2_700, 3_200, script.smallEndMs.toLong() + 100, script.smallEndMs.toLong() + 400, 4_600, 5_400, 6_300, script.durMs.toLong() - 100)
            for (at in checkpoints.sorted()) {
                advance(start + at - SystemClock.uptimeMillis())
                val t = (SystemClock.uptimeMillis() - start).toDouble()
                assertTrue("$mood $at 還在播", a.egg.playing)
                val smallOn = t < script.smallEndMs + WeatherEggOverlay.FADE_MS
                // 小墨丸：動畫與靜態圖同一格切換，永遠只有一隻
                assertEquals("$mood $at 靜態墨丸", if (smallOn) View.INVISIBLE else View.VISIBLE, a.still.visibility)
                a.eggOverlay.draw(canvas)
                assertEquals("$mood $at 疊層小墨丸", smallOn, a.eggOverlay.lastSmallDrawn)
                if (a.eggOverlay.lastMotes > 0) sawSmallMotes = true
                // 大墨丸：接手以前就是日常，接手以後由分鏡決定
                val daily = Mowan.dailyPose(parts, script.weather, a.mowan.timeline.poseClockAt(SystemClock.uptimeMillis()))
                val pose = a.mowan.egg!!.invoke(daily, SystemClock.uptimeMillis())
                if (pose != daily) sawBigPose = true
                if (at <= 2_700) assertEquals("$mood $at 大墨丸還是日常", daily, pose)
                drawBig(a)
                // 版面：位置不變、沒有要求重新排版、故事文字不變
                assertEquals("$mood $at 位置不變", before, places(a))
                assertFalse("$mood $at 沒有要求重新排版", a.content.isLayoutRequested)
                assertEquals("$mood $at 故事文字不變", story, a.text)
                report.append("| $mood | $at ms | ${if (a.still.visibility == View.VISIBLE) "顯示" else "藏起來"} | ${if (a.eggOverlay.lastSmallDrawn) "畫" else "不畫"} | ${a.eggOverlay.lastMotes} | ${if (pose == daily) "日常" else "彩蛋姿勢"} | 不變 |\n")
            }
            assertTrue("$mood 有畫天氣素材", sawSmallMotes)
            assertTrue("$mood 大墨丸有接手", sawBigPose)
            // 播完
            advance(200)
            assertClean(a, "$mood 播完")
            assertFalse("$mood 大墨丸交還", a.mowan.eggPlaying)
            assertEquals("$mood 位置不變", before, places(a))
            relayout(a)
            assertEquals("$mood 重新排版後位置也不變", before, places(a))
            // 播完 1 秒內不重播
            assertEquals(State.COOLDOWN, a.egg.session.state(SystemClock.uptimeMillis()))
            tapSun(a)
            assertEquals("$mood 播完 1 秒內不重播", Outcome.IGNORED, a.lastWeatherTap)
            assertFalse(a.egg.playing)
            advance(1_000)
            tapSun(a)
            assertEquals("$mood 1 秒後可以再播", Outcome.PLAY, a.lastWeatherTap)
            a.egg.cancel()
            assertClean(a, "$mood 取消")
            report.append("| $mood | 播完 | 顯示 | 不畫 | 0 | 日常 | 不變 |\n")
            controller?.pause()?.stop()?.destroy()
        }
        File(out, "flow.md").writeText(report.toString())
    }

    @Test
    fun theFirstSmallFrameIsExactlyTheStaticImageAndTheLastOneToo() {
        for (mood in listOf(Mood.SUNNY, Mood.RAIN, Mood.SNOW)) {
            val a = open(mood = mood)
            val static = (a.still.drawable as BitmapDrawable).bitmap
            tapSun(a)
            assertTrue(a.egg.playing)
            val first = a.eggOverlay.smallFrame()!!
            assertEquals("$mood 大小一樣", static.width to static.height, first.width to first.height)
            assertEquals("$mood 第 0 格跟靜態圖一模一樣（不同的像素數）", 0, differing(static, first, 0))
            // 中間真的在動
            advance(1_900)
            assertTrue("$mood 中間跟靜態圖不一樣", differing(static, a.eggOverlay.smallFrame()!!, 24) > 0)
            // 換回靜態圖前的最後一格：角色一樣，雨雪幾乎已經淡回靜態的位置
            val script = a.egg.session.script!!
            advance((script.smallEndMs + WeatherEggOverlay.FADE_MS).toLong() - 1_900 - 3)
            assertEquals("還在畫小墨丸", View.INVISIBLE, a.still.visibility)
            assertEquals("$mood 最後一格跟靜態圖看不出差別", 0, differing(static, a.eggOverlay.smallFrame()!!, 12))
            a.egg.cancel()
            controller?.pause()?.stop()?.destroy()
        }
    }

    /** 兩張圖有幾個像素的任何一個色版差超過 [tolerance] */
    private fun differing(a: Bitmap, b: Bitmap, tolerance: Int): Int {
        val pa = IntArray(a.width * a.height).also { a.getPixels(it, 0, a.width, 0, 0, a.width, a.height) }
        val pb = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return pa.indices.count { i ->
            val x = pa[i]; val y = pb[i]
            (0..24 step 8).any { abs(((x ushr it) and 0xFF) - ((y ushr it) and 0xFF)) > tolerance }
        }
    }

    // ---------- 連點、取消 ----------

    @Test
    fun tappingAgainWhilePlayingIsIgnored() {
        val a = open()
        tapSun(a)
        advance(1_000)
        val t1 = a.egg.session.t(SystemClock.uptimeMillis())!!
        tapSun(a)
        assertEquals("播放中再點忽略", Outcome.IGNORED, a.lastWeatherTap)
        assertEquals("沒有重來", t1, a.egg.session.t(SystemClock.uptimeMillis())!!, 0.0)
        a.tapWeather()
        a.tapWeather()
        assertEquals(t1, a.egg.session.t(SystemClock.uptimeMillis())!!, 0.0)
        assertTrue(a.egg.playing)
    }

    @Test
    fun leavingTheAppCancels() {
        val a = open()
        tapSun(a)
        advance(2_000)
        controller!!.pause()
        assertClean(a, "離開 App")
        assertFalse("大墨丸直接回日常", a.mowan.eggPlaying)
        controller!!.resume()
        relayout(a)
        assertClean(a, "回到 App（不續播、不補播）")
    }

    @Test
    fun switchingPagesCancels() {
        val a = open()
        tapSun(a)
        advance(2_000)
        a.findViewById<Button>(R.id.prev).performClick()
        relayout(a)
        assertClean(a, "換頁")
        assertFalse(a.mowan.eggPlaying)
        // 昨天那頁沒有印天氣：不能點
        assertNull(a.lastWeatherTap.takeIf { it == Outcome.PLAY && a.egg.playing })
        a.lastWeatherTap = null
        tapSun(a)
        assertNull("別天那頁點了沒有反應", a.lastWeatherTap)
        advance(600)
    }

    @Test
    fun aWeatherUpdateCancelsAndTheNewDailyTakesOver() {
        val a = open(mood = Mood.SUNNY)
        tapSun(a)
        advance(4_500)
        assertTrue(a.egg.playing)
        WeatherService.setFakeMood(context, Mood.RAIN)
        a.onWeatherRefreshed()
        relayout(a)
        assertClean(a, "天氣更新")
        assertFalse(a.mowan.eggPlaying)
        assertEquals("換成新天氣的日常", Mowan.Weather.RAINY, a.mowan.weather)
    }

    @Test
    fun reducedMotionTurnedOnWhilePlayingCancels() {
        val a = open()
        tapSun(a)
        advance(1_000)
        a.mowan.reducedMotion = { true }
        advance(50)
        assertClean(a, "中途開啟移除動畫")
        assertFalse(a.mowan.eggPlaying)
    }

    // ---------- 優先順序、只換一句話 ----------

    @Test
    fun reducedMotionOnlyShowsTheLineAtAFixedHeight() {
        val a = open()
        a.mowan.reducedMotion = { true }
        val before = places(a)
        val story = a.text
        tapSun(a)
        assertEquals(Outcome.TEXT_ONLY, a.lastWeatherTap)
        assertFalse(a.egg.playing)
        assertFalse(a.eggOverlay.active)
        assertEquals(View.VISIBLE, a.still.visibility)
        assertEquals(line(Mood.SUNNY), a.text)
        relayout(a)
        assertEquals("故事卡高度與位置不變", before.map { it.top to it.bottom }.take(3), places(a).map { it.top to it.bottom }.take(3))
        advance(6_100)
        assertEquals("6 秒後回到故事", story, a.text)
        relayout(a)
        assertEquals(before, places(a))
    }

    @Test
    fun festivalDayOnlyShowsTheLine() {
        val spring = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == Festival.SPRING }
        val a = open(spring)
        tapSun(a)
        assertEquals(Outcome.TEXT_ONLY, a.lastWeatherTap)
        assertFalse(a.egg.playing)
        assertEquals(line(Mood.SUNNY), a.text)
    }

    @Test
    fun growthStoryHasPriority() {
        val a = open()
        val story = a.text
        a.mowan.director.start(MowanDirector.Activity.GROWTH_STORY)
        tapSun(a)
        assertEquals(Outcome.IGNORED, a.lastWeatherTap)
        assertFalse(a.egg.playing)
        assertEquals(story, a.text)
        // 播到一半成長故事開始：彩蛋讓開
        a.mowan.director.finish(MowanDirector.Activity.GROWTH_STORY)
        tapSun(a)
        assertTrue(a.egg.playing)
        advance(1_000)
        a.mowan.director.start(MowanDirector.Activity.GROWTH_STORY)
        advance(50)
        assertClean(a, "成長故事開始")
    }

    @Test
    fun theOtherFiveWeathersOnlyShowTheLine() {
        for (mood in listOf(Mood.HOT, Mood.CLOUDY, Mood.FOG, Mood.THUNDER, Mood.COLD)) {
            val a = open(mood = mood)
            tapSun(a)
            assertEquals("$mood", Outcome.TEXT_ONLY, a.lastWeatherTap)
            assertFalse(a.egg.playing)
            assertEquals(line(mood), a.text)
            controller?.pause()?.stop()?.destroy()
        }
    }

    @Test
    fun beforeMowanIsFullyGrownOnlyTheLineShows() {
        // 第 2 頁：還沒有手腳
        val a = open(page = 1)
        tapSun(a)
        assertEquals(Outcome.TEXT_ONLY, a.lastWeatherTap)
        assertFalse(a.egg.playing)
    }

    @Test
    fun withoutWeatherDataNothingHappens() {
        val a = open(mood = null)
        val story = a.text
        tapSun(a)
        advance(600)
        assertNull(a.lastWeatherTap)
        assertFalse(a.egg.playing)
        assertEquals(story, a.text)
        a.tapWeather()
        assertNull(a.lastWeatherTap)
    }

    // ---------- 既有的互動、小工具 ----------

    @Test
    fun theStoryCardMowanTapStillWorksAndOnlyChangesTheLineDuringTheEgg() {
        val a = open()
        // 平常：P7-2 的點擊照舊（重播日常）
        assertEquals(MowanView.Reaction.REPLAY, a.mowan.reactionNow())
        // 彩蛋中：只換台詞，不重播日常、不打斷彩蛋
        tapSun(a)
        advance(4_000)
        assertEquals(MowanView.Reaction.LINE_ONLY, a.mowan.reactionNow())
        assertTrue(a.mowan.performClick())
        assertEquals(line(Mood.SUNNY), a.text)
        assertFalse(a.mowan.timeline.replaying(SystemClock.uptimeMillis()))
        assertTrue("彩蛋沒有被打斷", a.egg.playing)
        // 播完以後又可以重播日常
        advance(5_000)
        assertFalse(a.egg.playing)
        assertEquals(MowanView.Reaction.REPLAY, a.mowan.reactionNow())
    }

    @Test
    fun theWidgetAndTheStaticImageAreNotRedrawnByTheEgg() {
        val a = open(mood = Mood.SNOW)
        val static = (a.still.drawable as BitmapDrawable).bitmap
        val copy = static.copy(Bitmap.Config.ARGB_8888, false)
        val rendered = MowanBitmap.renderCount
        tapSun(a)
        for (i in 0 until 20) advance(500)
        assertFalse(a.egg.playing)
        assertEquals("彩蛋沒有重畫任何靜態圖（日曆頁、小工具）", rendered, MowanBitmap.renderCount)
        assertTrue("日曆頁還是同一張靜態圖", (a.still.drawable as BitmapDrawable).bitmap === static)
        assertEquals("靜態圖的內容沒有被改到", 0, differing(copy, static, 0))
        // 小工具的版面沒有彩蛋的任何東西：同一個版面檔、同一張靜態圖
        val views = CalendarWidget.build(context, Almanac.page(plain), PageLayout.TALL, 48f)
        val widget = views.apply(context, android.widget.FrameLayout(context))
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.mowan_still).visibility)
        assertFalse("小工具的天氣資訊不能點", widget.findViewById<View>(R.id.sun).hasOnClickListeners())
    }

    @Test
    fun talkBackCanTriggerItFromTheWeatherInfoAndTheOverlayIsHidden() {
        val a = open()
        val info = a.sun.createAccessibilityNodeInfo()
        assertTrue(info.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label == context.getString(R.string.weather_egg_action) })
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, a.eggOverlay.importantForAccessibility)
        assertFalse("疊層不吃觸控", a.eggOverlay.isClickable)
        assertTrue(a.sun.performClick())
        assertEquals(Outcome.PLAY, a.lastWeatherTap)
    }

    // ---------- 小螢幕、大字體 ----------

    @Test
    fun smallScreenWithLargeFontAlsoPlaysWithoutMovingAnything() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        RuntimeEnvironment.setFontScale(2f)
        val a = open(mood = Mood.RAIN)
        val before = places(a)
        // 捲到天氣資訊在畫面中間（疊層要跟著捲動走）
        val scroll = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        val sunTop = GlyphInk.rectIn(a.content, a.sun).top
        scroll.scrollTo(0, maxOf(0, sunTop - a.window.decorView.height / 2))
        relayout(a)
        val (x, y) = center(a, a.sun)
        assertTrue("天氣資訊在畫面裡：$y", y < a.window.decorView.height)
        tapSun(a)
        assertEquals(Outcome.PLAY, a.lastWeatherTap)
        for (at in listOf(500L, 2_000, 3_500, 5_000, 7_000)) {
            advance(at - (a.egg.session.t(SystemClock.uptimeMillis())?.toLong() ?: at))
            assertEquals("320dp・200% $at 位置不變", before, places(a))
            assertFalse(a.content.isLayoutRequested)
        }
        advance(1_000)
        assertClean(a, "320dp・200% 播完")
        relayout(a)
        assertEquals(before, places(a))
        assertTrue(x > 0)
    }
}
