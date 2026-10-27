package com.patty60723.tearoff

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalSession.Mode
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.stickman.StickmanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate

/**
 * P6-4b：節日接進 App（故事卡、日曆頁、除錯選單、日期改變）。
 * 「看得到」由測試決定（Robolectric 沒有真的捲動與視窗焦點），其他都走 App 真的流程。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FestivalAppTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val eve = LocalDate.of(2027, 2, 5)
    private val spring = LocalDate.of(2027, 2, 6)
    private val plain = LocalDate.of(2027, 3, 1)
    private val log get() = MowanSettings.FestivalLog(context)

    @Before
    fun useV3() {
        MowanSettings.setStoryCard(context, true)
        WeatherService.setFakeMood(context, Mood.SUNNY)
    }

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        MowanSettings.setFestivalHeadwear(context, true)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    /** 假裝今天是 [today]、已經撕到第 [page] 頁；[tornToday] 是今天那頁撕了沒（沒撕的話最上面是昨天那頁） */
    private fun open(today: LocalDate, page: Int = 14, tornToday: Boolean = false): ActivityController<MainActivity> {
        AppClock.setFakeToday(context, today)
        StoryBook(context).apply {
            jumpTo(page - if (tornToday) 1 else 0, today)
            if (tornToday) tear(today)
        }
        return Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    private val MainActivity.mowan get() = findViewById<MowanView>(R.id.story_mowan)

    /** 故事卡捲到畫面上、視窗有焦點 */
    private fun MainActivity.lookAtStoryCard() = mowan.apply {
        visibleFraction = { 1f }
        windowFocused = { true }
        checkVisible()
    }

    private fun mains(a: MainActivity) = a.mowan.session.starts.getValue(Mode.MAIN)

    // ---------- 故事卡：一天一次、重新進入 App ----------

    @Test
    fun storyCardPlaysTodaysFestivalOnce() {
        val c = open(spring)
        val a = c.get()
        assertEquals(spring, a.mowan.today)
        assertEquals(Festival.SPRING, a.mowan.festival)
        a.lookAtStoryCard()
        assertEquals("第一次看到：演主演出", Mode.MAIN, a.mowan.session.mode)
        assertTrue(log.played("2027-02-06:SPRING"))
        // 切到背景再回來：不重播
        c.pause().stop().restart().start().resume()
        a.lookAtStoryCard()
        assertEquals(1, mains(a))
        // 重建 Activity（例如旋轉）、重開 App：同一份紀錄，不重播
        val recreated = c.recreate().get()
        recreated.lookAtStoryCard()
        assertEquals(0, mains(recreated))
        assertEquals(Mode.BASE, recreated.mowan.session.mode)
        c.pause().stop().destroy()
        val again = open(spring).get()
        again.lookAtStoryCard()
        assertEquals("重開 App：不重播", 0, mains(again))
        assertEquals(listOf("2027-02-06:SPRING"), log.keys())
    }

    @Test
    fun nextFestivalDayPlaysAgain() {
        open(spring).get().lookAtStoryCard()
        val next = open(spring.plusDays(1)).get()
        next.lookAtStoryCard()
        assertEquals("初二自己演一次", Mode.MAIN, next.mowan.session.mode)
        val plainDay = open(plain).get()
        plainDay.lookAtStoryCard()
        assertNull(plainDay.mowan.festival)
        assertEquals(Mode.BASE, plainDay.mowan.session.mode)
        assertEquals(listOf("2027-02-06:SPRING", "2027-02-07:SPRING"), log.keys())
    }

    // ---------- V3 開關（D-42、D-47） ----------

    @Test
    fun v3OffHasNoFestivalAndTurningItOnPlaysOnFirstLook() {
        MowanSettings.setStoryCard(context, false)
        val c = open(spring)
        val a = c.get()
        assertEquals(View.GONE, a.mowan.visibility)
        assertNull("V3 關閉：不判斷節日", a.mowan.today)
        assertNull(a.mowan.festival)
        // 就算墨丸那一塊在畫面上、視窗有焦點，V3 關閉（墨丸隱藏）也不觸發
        a.lookAtStoryCard()
        assertTrue("V3 關閉：不記錄", log.keys().isEmpty())
        // 日曆頁是舊火柴人
        val page = a.findViewById<TearPageView>(R.id.page)
        assertEquals(View.VISIBLE, page.findViewById<View>(R.id.stickman).visibility)
        assertEquals(View.GONE, page.findViewById<View>(R.id.mowan_still).visibility)
        // 打開 V3、回到 App：故事卡在畫面上、有焦點，就是打開後第一次看到，這時才演、才記錄
        MowanSettings.setStoryCard(context, true)
        c.pause()
        assertTrue(log.keys().isEmpty())
        c.resume()
        assertEquals(spring, a.mowan.today)
        assertEquals(Mode.MAIN, a.mowan.session.mode)
        assertEquals(listOf("2027-02-06:SPRING"), log.keys())
    }

    @Test
    fun oldStickmanIsTheSameOnFestivalDays() {
        MowanSettings.setStoryCard(context, false)
        // 故事進度固定（撕頁的故事會看日期挑，所以只換「今天」，不重新跳頁）
        StoryBook(context).jumpTo(14, plain)
        fun snapshot(day: LocalDate): Pair<List<Any?>, List<Bitmap>> {
            AppClock.setFakeToday(context, day)
            val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val s = a.findViewById<StickmanView>(R.id.story_stickman)
            val page = a.findViewById<TearPageView>(R.id.page)
            val frames = listOf(R.id.frame0, R.id.frame1, R.id.frame2).map { (page.findViewById<ImageView>(it).drawable as BitmapDrawable).bitmap }
            return listOf(s.visibility, s.parts, s.action, s.props) to frames
        }
        val (springCard, springFrames) = snapshot(spring)
        val (plainCard, plainFrames) = snapshot(plain)
        assertEquals("故事卡的火柴人一樣", plainCard, springCard)
        springFrames.zip(plainFrames).forEachIndexed { i, (x, y) -> assertTrue("日曆頁的火柴人第 $i 張一樣", x.sameAs(y)) }
        assertTrue(log.keys().isEmpty())
    }

    // ---------- 日曆頁：只有今天那頁（D-41） ----------

    /** 日曆頁上 V3 墨丸那張圖 */
    private fun pageStill(a: MainActivity): Bitmap =
        (a.findViewById<TearPageView>(R.id.page).findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap

    /** 跟 App 日曆頁同樣大小的圖：[festivalLook] 開或關 */
    private fun expected(a: MainActivity, day: LocalDate, festivalLook: Boolean): Bitmap {
        val metrics = a.resources.displayMetrics
        val pageWidthPx = metrics.widthPixels - (56 * metrics.density).toInt()
        val stickmanDp = (pageWidthPx * 0.17f).toInt() / metrics.density
        val views = CalendarWidget.build(context, Almanac.page(day), PageLayout.TALL, stickmanDp, festivalLook = festivalLook)
        val v = views.apply(context, FrameLayout(context))
        return (v.findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap
    }

    @Test
    fun calendarPageShowsTheFestivalOnlyOnToday() {
        // 今天那頁撕開了：最上面是今天，畫節日外觀
        val a = open(spring, tornToday = true).get()
        val festive = expected(a, spring, true)
        val usual = expected(a, spring, false)
        assertFalse("節日外觀跟平常不一樣", festive.sameAs(usual))
        assertTrue("今天那頁：節日外觀", pageStill(a).sameAs(festive))
        // 往前翻到昨天：平常的樣子
        a.findViewById<Button>(R.id.prev).performClick()
        assertTrue("昨天那頁：平常的樣子", pageStill(a).sameAs(expected(a, eve, false)))
        // 小工具（P6-4c 才做）：還是平常的樣子
        assertTrue(expected(a, spring, false).sameAs(usual))
    }

    @Test
    fun yesterdayOnTopIsNotFestive() {
        // 今天還沒撕：最上面還是昨天那頁（D-41：不畫節日）
        val a = open(spring).get()
        assertTrue(pageStill(a).sameAs(expected(a, eve, false)))
        // 往後翻到今天：節日外觀
        a.findViewById<Button>(R.id.next).performClick()
        assertTrue(pageStill(a).sameAs(expected(a, spring, true)))
    }

    // ---------- 除錯選單 ----------

    private fun debugItem(a: MainActivity, index: Int) {
        a.findViewById<Button>(R.id.debug).performClick()
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(index)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun debugReplayKeepsTheDailyRecord() {
        val a = open(spring).get()
        a.lookAtStoryCard()
        debugItem(a, 12)
        assertEquals("重播", 2, mains(a))
        assertEquals(Mode.MAIN, a.mowan.session.mode)
        assertEquals("不改紀錄", listOf("2027-02-06:SPRING"), log.keys())
        val p = open(plain).get()
        debugItem(p, 12)
        assertEquals("今天不是節日", ShadowToast.getTextOfLatestToast())
        MowanSettings.setStoryCard(context, false)
        val off = open(spring).get()
        debugItem(off, 12)
        assertEquals("故事卡沒有用墨丸 V3", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun debugHeadwearToggle() {
        val a = open(spring, tornToday = true).get()
        val withHeadwear = pageStill(a)
        assertTrue(a.mowan.art.festivalHeadwear)
        debugItem(a, 13)
        assertFalse(MowanSettings.festivalHeadwear(context))
        assertFalse("故事卡跟著換", a.mowan.art.festivalHeadwear)
        assertFalse("日曆頁跟著換", pageStill(a).sameAs(withHeadwear))
        assertTrue(pageStill(a).sameAs(expected(a, spring, true)))
        debugItem(a, 13)
        assertTrue(MowanSettings.festivalHeadwear(context))
        assertTrue(pageStill(a).sameAs(withHeadwear))
    }

    // ---------- 前景跨午夜（D-44） ----------

    @Test
    fun midnightInTheForegroundUpdatesOnlyTheStoryCard() {
        // 天氣剛抓過（不然 onResume 的背景下載抓到新天氣時會重畫日曆頁，跟這裡要測的事無關）
        context.getSharedPreferences("weather", android.content.Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        val c = open(eve, tornToday = true)
        val a = c.get()
        a.lookAtStoryCard()
        assertNull(a.mowan.festival)
        val pad = a.findViewById<TearPageView>(R.id.page)
        val page = pad.getChildAt(0)
        val tearable = pad.tearable
        val details = a.findViewById<android.widget.TextView>(R.id.details).text.toString()
        AppClock.setFakeToday(context, spring)
        context.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("故事卡換成新的一天", spring, a.mowan.today)
        assertEquals(Festival.SPRING, a.mowan.festival)
        assertEquals("看得到：演今天的主演出", Mode.MAIN, a.mowan.session.mode)
        assertSame("日曆頁沒有重建", page, pad.getChildAt(0))
        assertEquals("還是同一天的頁", details, a.findViewById<android.widget.TextView>(R.id.details).text.toString())
        assertEquals("撕頁狀態不變", tearable, pad.tearable)
        // 回到 App（onResume）時日曆頁照原本的做法重算
        c.pause().resume()
        assertTrue(pad.tearable)
        // 背景時不收廣播
        c.pause().stop()
        AppClock.setFakeToday(context, spring.plusDays(1))
        context.sendBroadcast(Intent(Intent.ACTION_DATE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(spring, a.mowan.today)
    }
}
