package com.patty60723.tearoff

import android.view.View
import android.widget.TextView
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanSettings
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
        a.findViewById<TextView>(R.id.weather).performClick()
        assertEquals(Mowan.Weather.SNOW, a.mowan.weather)
        assertEquals(before - 700, a.mowan.timeline.sample(later, false).clockMs, 0.0)
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
}
