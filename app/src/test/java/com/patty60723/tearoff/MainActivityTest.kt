package com.patty60723.tearoff

import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Assert.assertTrue
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
        val title = activity.findViewById<TextView>(R.id.story_title).text.toString()
        assertTrue(title, title.contains("1"))
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
