package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-7：英文月份 #746D64（ink_month_en）在 TALL 小工具與 App 日曆頁的實際對比（WIDE 在 WideLayoutTest）。
 * 量法同 WideLayoutTest：字實際畫到的像素（[GlyphInk.mask]）背後的背景，最差像素 ≥ 4.5。12 個月各取一個沒有節日的日子。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class EnglishMonthContrastTest {
    private val out = File("build/screenshots/p7-7-month-en").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    private fun draw(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEFEAE1.toInt()); v.draw(this) } }

    /** 最差像素的對比；英文月份沒顯示時是 null */
    private fun worst(root: View): Double? {
        val tv = root.findViewById<TextView>(R.id.month_en)
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty()) return null
        val ink = GlyphInk.mask(root, tv)
        tv.visibility = View.INVISIBLE
        val bg = draw(root)
        tv.visibility = View.VISIBLE
        val px = IntArray(bg.width * bg.height).also { bg.getPixels(it, 0, bg.width, 0, 0, bg.width, bg.height) }
        var w = Double.MAX_VALUE
        for (i in px.indices) if (ink[i]) w = minOf(w, ratio(tv.currentTextColor, px[i]))
        return w.takeIf { it != Double.MAX_VALUE }
    }

    private fun plainDay(m: Int) = generateSequence(LocalDate.of(2027, m, 1)) { it.plusDays(1) }.first { Almanac.page(it).label == null }

    private fun prepare(day: LocalDate) {
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
    }

    @Test
    fun tallAndAppPage() {
        val report = StringBuilder("# 英文月份 #746D64：TALL 小工具（150×250dp）與 App 日曆頁的最差像素對比\n\n| 月 | 日子 | TALL | App 日曆頁 |\n|---|---|---|---|\n")
        val below = mutableListOf<String>()
        val d = context.resources.displayMetrics.density
        for (m in 1..12) {
            val day = plainDay(m)
            prepare(day)
            StoryBook(context).apply { jumpTo(13, day); tear(day) }
            val w = CalendarWidget.build(context, Almanac.page(day), PageLayout.TALL).apply(context, FrameLayout(context)).apply {
                measure(View.MeasureSpec.makeMeasureSpec((150 * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((250 * d).toInt(), View.MeasureSpec.EXACTLY))
                layout(0, 0, measuredWidth, measuredHeight)
            }
            val tall = worst(w)
            val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val page = worst(a.findViewById(R.id.page))
            assertEquals("$m 月 TALL 英文月份用 #746D64", 0xFF746D64.toInt(), w.findViewById<TextView>(R.id.month_en).currentTextColor)
            tall?.let { if (it < 4.5) below += "$m 月 TALL %.2f".format(it) }
            page?.let { if (it < 4.5) below += "$m 月 日曆頁 %.2f".format(it) }
            assertTrue("$m 月 TALL 有英文月份", tall != null)
            assertTrue("$m 月 日曆頁有英文月份", page != null)
            report.append("| $m | $day | %.2f | %.2f |\n".format(tall, page))
        }
        File(out, "report.md").writeText(report.toString())
        assertEquals("英文月份未達 4.5：$below", emptyList<String>(), below)
    }
}
