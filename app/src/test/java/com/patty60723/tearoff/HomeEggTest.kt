package com.patty60723.tearoff

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ScrollView
import android.widget.TextView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalSession.Mode
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.MowanDirector
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.time.LocalDate

/**
 * P7-2：首頁的互動（方案 A，D-48、D-53）。
 * - 天氣彩蛋：點故事卡上的墨丸；一般日子重播（經由中性姿勢）、節日／Reduced Motion 只換台詞、成長故事事件完全不反應、
 *   連續點擊只延長台詞；點擊跟捲動分得開；跟包袱入口互不干擾；不自動捲動。
 * - 城市入口：日曆上方的「⌖ 臺北市 ⌄」，≥ 48dp，點了開城市選擇。
 * - TalkBack：描述、點擊動作的說明、台詞是禮貌的動態區域。
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HomeEggTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val plain = LocalDate.of(2027, 10, 21)

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        MowanSettings.resetEquipment(context)
    }

    private fun open(day: LocalDate = plain, mood: Mood? = Mood.SUNNY): MainActivity {
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, mood)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); tear(day) }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val MainActivity.mowan get() = findViewById<MowanView>(R.id.story_mowan)
    private val MainActivity.text get() = findViewById<TextView>(R.id.story_text).text.toString()
    private fun advance(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))
    private fun line(mood: Mood) = "${StoryBook(context).name}：${mood.line}"

    @Test
    fun weatherRowAndCityRowAreGoneAndTheCityEntryIsAboveTheCalendar() {
        val a = open()
        val content = (a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as ViewGroup
        fun texts(v: View): List<String> = when (v) {
            is TextView -> listOf(v.text.toString())
            is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
            else -> emptyList()
        }
        assertTrue("沒有「點我看看」那一列", texts(content).none { "點我看看" in it })
        assertTrue("沒有「換城市 ›」那一列", texts(content).none { "換城市" in it })
        val city = a.findViewById<TextView>(R.id.city)
        assertEquals(Cities.selected(context).name, city.text.toString())
        assertTrue("城市入口在日曆上面", content.indexOfChild(city) == content.indexOfChild(a.findViewById(R.id.page)) - 1)
        val d = context.resources.displayMetrics.density
        assertTrue("城市入口 ≥ 48dp：${city.width / d}×${city.height / d}", city.width / d >= 47.5f && city.height / d >= 47.5f)
        assertNotNull("有定位圖示", city.compoundDrawablesRelative[0])
        assertNotNull("有下拉符號", city.compoundDrawablesRelative[2])
        assertNull("沒有實心底色（只有系統漣漪）", (city.background as? android.graphics.drawable.ColorDrawable))
        // 點了開城市選擇
        city.performClick()
        assertNotNull(ShadowAlertDialog.getLatestAlertDialog())
    }

    @Test
    fun tapOnANormalDayShowsTheLineAndReplaysFromTheNeutralPose() {
        val a = open()
        val story = a.text
        advance(2_700)
        assertTrue(a.mowan.performClick())
        assertEquals(line(Mood.SUNNY), a.text)
        val now = SystemClock.uptimeMillis()
        assertTrue("重播中", a.mowan.timeline.replaying(now))
        assertNotNull("先從目前的姿勢過渡", a.mowan.timeline.sample(now + 10, false).blendFromPoseMs)
        // 6 秒後回到故事
        advance(6_100)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(story, a.text)
    }

    @Test
    fun tappingAgainOnlyExtendsTheLine() {
        val a = open()
        val story = a.text
        advance(2_000)
        a.mowan.performClick()
        val first = a.mowan.timeline.sample(SystemClock.uptimeMillis() + 1_000, false).poseClockMs
        advance(3_000)
        a.mowan.performClick()
        // 動作沒有重來：同一個時間點的姿勢時間一樣
        assertEquals(first, a.mowan.timeline.sample(SystemClock.uptimeMillis() - 2_000, false).poseClockMs, 0.0)
        // 第一次點的 6 秒到了，台詞還在（第二次點重新計時）
        advance(3_500)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(line(Mood.SUNNY), a.text)
        advance(3_000)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(story, a.text)
    }

    @Test
    fun festivalDayOnlyChangesTheLine() {
        val spring = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == Festival.SPRING }
        val a = open(spring)
        a.mowan.visibleFraction = { 1f }
        a.mowan.windowFocused = { true }
        a.mowan.checkVisible()
        // 第一次看到沒開始的話（Robolectric 的可見度判斷），用除錯重播把主演出放起來；要測的是點擊不打斷它
        if (a.mowan.session.mode != Mode.MAIN) a.mowan.replayFestival()
        assertEquals(Mode.MAIN, a.mowan.session.mode)
        val startedAt = a.mowan.session.startMs
        assertEquals(MowanView.Reaction.LINE_ONLY, a.mowan.reactionNow())
        a.mowan.performClick()
        assertEquals(line(Mood.SUNNY), a.text)
        assertEquals("主演出沒有被打斷", Mode.MAIN, a.mowan.session.mode)
        assertEquals(startedAt, a.mowan.session.startMs)
        assertFalse(a.mowan.timeline.replaying(SystemClock.uptimeMillis()))
    }

    @Test
    fun reducedMotionOnlyChangesTheLine() {
        val a = open()
        a.mowan.reducedMotion = { true }
        assertEquals(MowanView.Reaction.LINE_ONLY, a.mowan.reactionNow())
        a.mowan.performClick()
        assertEquals(line(Mood.SUNNY), a.text)
        assertFalse(a.mowan.timeline.replaying(SystemClock.uptimeMillis()))
        assertTrue(a.mowan.timeline.sample(SystemClock.uptimeMillis(), true).still)
    }

    @Test
    fun growthStoryEventIgnoresTapsAndOffersNoClickToTalkBack() {
        val a = open()
        val story = a.text
        a.mowan.director.start(MowanDirector.Activity.GROWTH_STORY)
        assertEquals(MowanView.Reaction.NONE, a.mowan.reactionNow())
        assertFalse("不呼叫點擊", a.mowan.performClick())
        assertEquals("台詞不變", story, a.text)
        assertFalse(a.mowan.timeline.replaying(SystemClock.uptimeMillis()))
        val info = a.mowan.createAccessibilityNodeInfo()
        assertFalse(info.isClickable)
        assertTrue(info.actionList.none { it.id == AccessibilityNodeInfo.ACTION_CLICK })
        // 故事結束後又可以點
        a.mowan.director.finish(MowanDirector.Activity.GROWTH_STORY)
        assertTrue(a.mowan.performClick())
        assertEquals(line(Mood.SUNNY), a.text)
    }

    @Test
    fun noWeatherDataRefreshesAndSaysLoading() {
        val a = open(mood = null)
        // 沒有網路也沒有存過天氣：墨丸是一般站姿
        a.mowan.performClick()
        assertEquals(context.getString(R.string.weather_none), a.text)
    }

    /** 在 ScrollView 裡：手指移動就是捲動，不觸發彩蛋；沒移動就是點擊 */
    @Test
    fun scrollingIsNotATap() {
        val a = open()
        val story = a.text
        val root = a.window.decorView
        // 先捲到故事卡在畫面中間（點擊要落在視窗裡）
        val scroll = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        val top = IntArray(2).also { a.mowan.getLocationInWindow(it) }[1]
        scroll.scrollTo(0, maxOf(0, top - (300 * context.resources.displayMetrics.density).toInt()))
        val loc = IntArray(2).also { a.mowan.getLocationInWindow(it) }
        val x = loc[0] + a.mowan.width / 2f
        val y = loc[1] + a.mowan.height / 2f
        val t = SystemClock.uptimeMillis()
        fun ev(action: Int, dt: Long, yy: Float) = MotionEvent.obtain(t, t + dt, action, x, yy, 0)
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 0, y))
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_MOVE, 30, y - 60 * context.resources.displayMetrics.density))
        root.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 60, y - 60 * context.resources.displayMetrics.density))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("捲動不算點擊", story, a.text)
        val loc2 = IntArray(2).also { a.mowan.getLocationInWindow(it) }
        val x2 = loc2[0] + a.mowan.width / 2f
        val y2 = loc2[1] + a.mowan.height / 2f
        val t2 = SystemClock.uptimeMillis()
        root.dispatchTouchEvent(MotionEvent.obtain(t2, t2, MotionEvent.ACTION_DOWN, x2, y2, 0))
        root.dispatchTouchEvent(MotionEvent.obtain(t2, t2 + 50, MotionEvent.ACTION_UP, x2, y2, 0))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("沒移動就是點擊", line(Mood.SUNNY), a.text)
    }

    @Test
    fun bundleEntryAndTheMowanDoNotInterfere() {
        val a = open()
        val story = a.text
        val bundle = a.findViewById<TextView>(R.id.story_bundle)
        assertTrue(MowanSettings.bundleOn(context))
        bundle.performClick()
        assertFalse("包袱卸下", MowanSettings.bundleOn(context))
        assertEquals("點包袱不會觸發彩蛋", story, a.text)
        assertFalse(a.mowan.timeline.replaying(SystemClock.uptimeMillis()))
        a.mowan.performClick()
        assertFalse("點墨丸不會切換包袱", MowanSettings.bundleOn(context))
        // 兩者的位置不重疊
        val m = IntArray(2).also { a.mowan.getLocationInWindow(it) }
        val b = IntArray(2).also { bundle.getLocationInWindow(it) }
        val mr = android.graphics.Rect(m[0], m[1], m[0] + a.mowan.width, m[1] + a.mowan.height)
        val br = android.graphics.Rect(b[0], b[1], b[0] + bundle.width, b[1] + bundle.height)
        assertFalse(android.graphics.Rect.intersects(mr, br))
    }

    @Test
    fun tapDoesNotScrollThePage() {
        val a = open()
        val scroll = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        scroll.scrollTo(0, 120)
        a.mowan.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("不自動捲動", 120, scroll.scrollY)
    }

    @Test
    fun talkBackDescriptionsAndActions() {
        val a = open()
        val info = a.mowan.createAccessibilityNodeInfo()
        assertTrue(info.isClickable)
        assertEquals("墨丸，今天${WeatherService.current(context)!!.short}", a.mowan.contentDescription.toString())
        assertEquals(
            context.getString(R.string.mowan_action_weather),
            info.actionList.first { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label != null }.label?.toString(),
        )
        assertEquals("台詞換了 TalkBack 會唸", View.ACCESSIBILITY_LIVE_REGION_POLITE, a.findViewById<View>(R.id.story_text).accessibilityLiveRegion)
        assertFalse("不震動", a.mowan.isHapticFeedbackEnabled)
        assertNull("沒有漣漪（沒有背景）", a.mowan.background)
        val city = a.findViewById<TextView>(R.id.city).createAccessibilityNodeInfo()
        assertEquals(context.getString(R.string.city_desc, Cities.selected(context).name), city.contentDescription.toString())
        assertEquals(context.getString(R.string.city_action), city.actionList.first { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label != null }.label.toString())
        // 節日當天：點擊動作是「聽墨丸說一句話」
        val xmas = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == Festival.XMAS }
        val x = open(xmas)
        assertEquals("墨丸，今天是聖誕", x.mowan.contentDescription.toString())
        assertEquals(
            context.getString(R.string.mowan_action_festival),
            x.mowan.createAccessibilityNodeInfo().actionList.first { it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label != null }.label?.toString(),
        )
    }

    /** 日曆頁上的墨丸維持靜態、沒有點擊（撕頁手勢不變） */
    @Test
    fun calendarPageMowanStaysStatic() {
        val a = open()
        val still = a.findViewById<View>(R.id.page).findViewById<View>(R.id.mowan_still)
        assertFalse(still.isClickable)
        assertFalse(still.hasOnClickListeners())
    }
}
