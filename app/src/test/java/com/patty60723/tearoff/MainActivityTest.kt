package com.patty60723.tearoff

import android.widget.Button
import android.widget.FrameLayout
import com.patty60723.tearoff.story.StoryBook
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate

/** 在模擬的 Android 上實際打開 App、撕一頁、畫小工具。 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun opensAndTearsTheFirstPage() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.tear).performClick()
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(1, StoryBook(activity).page)
        // 撕完再打開一次也不能閃退
        Robolectric.buildActivity(MainActivity::class.java).setup()
    }

    @Test
    fun debugFakeDateAndJump() {
        val app = RuntimeEnvironment.getApplication()
        AppClock.setFakeToday(app, LocalDate.of(2026, 2, 16))
        StoryBook(app).jumpTo(13, AppClock.today(app))
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.tear).performClick()
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        val book = StoryBook(activity)
        assertEquals(14, book.page)
        // 假裝的今天撕過了，下一張是明天
        assertEquals(null, book.nextDate(AppClock.today(activity)))
        AppClock.setFakeToday(app, null)
    }

    @Test
    fun layoutPicksTheBiggestThatFits() {
        assertEquals(PageLayout.COMPACT, PageLayout.fitting(170f, 170f))
        assertEquals(PageLayout.TALL, PageLayout.fitting(170f, 255f))
        assertEquals(PageLayout.WIDE, PageLayout.fitting(349f, 170f))
        assertEquals(PageLayout.TALL, PageLayout.fitting(349f, 340f))
        assertEquals(PageLayout.COMPACT, PageLayout.fitting(80f, 80f))
    }

    @Test
    fun widgetViewsInflate() {
        val context = RuntimeEnvironment.getApplication()
        val page = Almanac.page(LocalDate.of(2026, 10, 21))
        for (layout in PageLayout.entries) {
            CalendarWidget.build(context, page, layout).apply(context, FrameLayout(context))
        }
    }
}
