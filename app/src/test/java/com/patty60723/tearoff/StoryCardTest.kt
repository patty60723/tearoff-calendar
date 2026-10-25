package com.patty60723.tearoff

import android.view.View
import android.widget.TextView
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanTimeline
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.stickman.StickmanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** W6：故事卡用墨丸 V3 時，8 種天氣、成長頁數、包袱入口 */
@RunWith(RobolectricTestRunner::class)
class StoryCardTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun useV3() = MowanSettings.setStoryCard(context, true)

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        WeatherService.setFakeMood(context, null)
    }

    private fun open(page: Int): MainActivity {
        StoryBook(context).jumpTo(page, AppClock.today(context))
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val MainActivity.mowan get() = findViewById<MowanView>(R.id.story_mowan)
    private val MainActivity.bundleRow get() = findViewById<TextView>(R.id.story_bundle)

    @Test
    fun everyWeatherShowsOnTheStoryCard() {
        for (mood in Mood.entries) {
            WeatherService.setFakeMood(context, mood)
            val a = open(14)
            assertEquals("$mood", MowanSettings.weatherOf(mood), a.mowan.weather)
            assertEquals(View.VISIBLE, a.mowan.visibility)
            assertEquals(View.GONE, a.findViewById<StickmanView>(R.id.story_stickman).visibility)
        }
    }

    @Test
    fun growthPartsFollowThePage() {
        WeatherService.setFakeMood(context, Mood.RAIN)
        val book = StoryBook(context)
        for (page in listOf(1, 4, 11, 13, 14)) {
            val a = open(page)
            assertEquals("第 $page 頁", Mowan.partsOf(book.parts), a.mowan.parts)
        }
    }

    @Test
    fun sameWeatherAgainDoesNotRestartTheMotion() {
        WeatherService.setFakeMood(context, Mood.SUNNY)
        val a = open(14)
        val later = android.os.SystemClock.uptimeMillis() + 3000
        val before = a.mowan.timeline.sample(later, false).clockMs
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700))
        // 點天氣那一行（還是晴天）
        a.findViewById<TextView>(R.id.weather).performClick()
        assertEquals(before, a.mowan.timeline.sample(later, false).clockMs, 0.0)
        // 換成雪：從頭開始
        WeatherService.setFakeMood(context, Mood.SNOW)
        val clickedFrom = android.os.SystemClock.uptimeMillis()
        a.findViewById<TextView>(R.id.weather).performClick()
        val clickedTo = android.os.SystemClock.uptimeMillis()
        assertEquals(Mowan.Weather.SNOW, a.mowan.weather)
        // 雪的時鐘從點下去的那一刻開始算（點擊時 Robolectric 的時鐘可能會走幾毫秒）
        val snow = a.mowan.timeline.sample(later, false).clockMs
        assertTrue("雪的時鐘 $snow 應該從換天氣時重新開始", snow in (later - clickedTo).toDouble()..(later - clickedFrom).toDouble())
        assertTrue(snow < before)
    }

    @Test
    fun bundleToggleOnlyAfterPageFourteen() {
        MowanSettings.setBundleOn(context, true)
        assertEquals("第 13 頁還沒有包袱", View.GONE, open(13).bundleRow.visibility)

        val a = open(14)
        assertEquals(View.VISIBLE, a.bundleRow.visibility)
        assertEquals(context.getString(R.string.bundle_take_off), a.bundleRow.text.toString())
        assertTrue(a.mowan.bundleOn)

        // 卸下：狀態存起來、墨丸跟著換、動作不重來
        val later = android.os.SystemClock.uptimeMillis() + 3000
        val before = a.mowan.timeline.sample(later, false).clockMs
        a.bundleRow.performClick()
        assertFalse(MowanSettings.bundleOn(context))
        assertFalse(a.mowan.bundleOn)
        assertEquals(context.getString(R.string.bundle_put_on), a.bundleRow.text.toString())
        assertEquals(before, a.mowan.timeline.sample(later, false).clockMs, 0.0)

        // 重新開 App：還是卸下
        val again = open(14)
        assertFalse(again.mowan.bundleOn)
        assertEquals(context.getString(R.string.bundle_put_on), again.bundleRow.text.toString())
        // 再背上
        again.bundleRow.performClick()
        assertTrue(MowanSettings.bundleOn(context))
        assertTrue(again.mowan.bundleOn)
    }

    @Test
    fun oldStickmanCardHasNoBundleToggle() {
        MowanSettings.setStoryCard(context, false)
        assertEquals(View.GONE, open(14).bundleRow.visibility)
    }

    /** W7：App 到背景時錯過的打雷，回到前景不補播；時鐘也不重來 */
    @Test
    fun backgroundDoesNotReplayMissedThunder() {
        WeatherService.setFakeMood(context, Mood.THUNDER)
        StoryBook(context).jumpTo(14, AppClock.today(context))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val view = controller.get().mowan
        // 打雷間隔固定 20 秒，結果才確定（App 裡是隨機 15–40 秒）；換掉亂數後重新進入雷雨，排程才會用新的間隔
        MowanTimeline::class.java.getDeclaredField("random").apply { isAccessible = true }.set(view.timeline, object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int) = 0
            override fun nextLong(from: Long, until: Long) = 20_000L
        })
        view.weather = Mowan.Weather.NONE
        view.weather = Mowan.Weather.THUNDER
        val start = android.os.SystemClock.uptimeMillis()

        controller.pause().stop()
        advance(30_000) // 第 20 秒的雷在背景時錯過了
        controller.restart().start().resume()
        val back = view.timeline.sample(android.os.SystemClock.uptimeMillis(), false)
        assertEquals("回到前景不補播錯過的雷", null, back.sinceStrikeMs)
        assertEquals("時鐘沒有重來", (android.os.SystemClock.uptimeMillis() - start).toDouble(), back.clockMs, 0.0)

        // 下一次（第 40 秒）照常打
        advance(40_100 - (android.os.SystemClock.uptimeMillis() - start))
        assertEquals(100.0, view.timeline.sample(android.os.SystemClock.uptimeMillis(), false).sinceStrikeMs!!, 0.0)
    }

    /** W7：關閉動畫時故事卡上的墨丸停在代表姿勢，不排重畫；打開後繼續動 */
    @Test
    fun reducedMotionOnTheStoryCard() {
        WeatherService.setFakeMood(context, Mood.SUNNY)
        val a = open(14)
        // 讓視窗跑完第一次 traversal（要等一個畫面的時間），View 才知道自己的視窗看得到；
        // 沒跑的話 windowVisibility 還是 GONE，本來就不排重畫
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertEquals(View.VISIBLE, a.mowan.windowVisibility)
        val canvas = android.graphics.Canvas(android.graphics.Bitmap.createBitmap(300, 400, android.graphics.Bitmap.Config.ARGB_8888))
        val scale = android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType)
        try {
            scale.invoke(null, 0f)
            a.mowan.draw(canvas)
            assertFalse("關閉動畫時不排重畫", a.mowan.isTicking)
            val still = a.mowan.timeline.sample(android.os.SystemClock.uptimeMillis(), true)
            assertTrue(still.still)
            assertEquals(MowanTimeline.stillClock(Mowan.Weather.SUNNY), still.clockMs, 0.0)
            // 包袱切換時也一樣不動
            a.bundleRow.performClick()
            a.mowan.draw(canvas)
            assertFalse(a.mowan.isTicking)
        } finally {
            scale.invoke(null, 1f)
        }
        a.mowan.draw(canvas)
        assertTrue(
            "打開動畫後繼續動（isShown=${a.mowan.isShown}、window=${a.mowan.windowVisibility}、" +
                "animators=${android.animation.ValueAnimator.areAnimatorsEnabled()}）",
            a.mowan.isTicking,
        )
    }

    private fun advance(ms: Long) = org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(ms))
}
