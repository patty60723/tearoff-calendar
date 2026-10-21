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
    fun widgetViewsInflate() {
        val context = RuntimeEnvironment.getApplication()
        val page = Almanac.page(LocalDate.of(2026, 10, 21))
        for (compact in listOf(false, true)) {
            CalendarWidget.build(context, page, compact).apply(context, FrameLayout(context))
        }
    }
}
