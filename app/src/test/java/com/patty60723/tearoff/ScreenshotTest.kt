package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import com.patty60723.tearoff.story.StoryBook
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
 * 把小工具和 App 畫面畫成圖片，存到 app/build/screenshots，CI 會上傳。
 * 這不是比對用的測試，是讓人不用手機也看得到長相。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    private val out = File("build/screenshots").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()

    private fun save(name: String, view: View, widthDp: Int, heightDp: Int) {
        val density = context.resources.displayMetrics.density
        val w = (widthDp * density).toInt()
        val h = (heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        save(name, view, w, h, background = true)
    }

    private fun save(name: String, view: View, w: Int, h: Int, background: Boolean) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background) canvas.drawColor(0xFF6B7A8C.toInt()) // 像手機桌布
        view.draw(canvas)
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun widget(layout: PageLayout, date: LocalDate): View {
        val parent = FrameLayout(context)
        return CalendarWidget.build(context, Almanac.page(date), layout).apply(context, parent)
    }

    @Test
    fun widgets() {
        StoryBook(context).jumpTo(14, AppClock.today(context))
        val day = LocalDate.of(2026, 10, 21)
        save("widget-tall", widget(PageLayout.TALL, day), 170, 255)
        save("widget-wide", widget(PageLayout.WIDE, day), 349, 170)
        save("widget-compact", widget(PageLayout.COMPACT, day), 110, 110)
        // 你截圖裡那種接近正方形的大小，霜降那天有節氣
        save("widget-medium", widget(PageLayout.MEDIUM, LocalDate.of(2026, 10, 23)), 175, 195)
        save("widget-medium-2x2", widget(PageLayout.MEDIUM, day), 170, 170)
        save("widget-tall-frost", widget(PageLayout.TALL, LocalDate.of(2026, 10, 23)), 170, 255)
        save("widget-tall-new-year", widget(PageLayout.TALL, LocalDate.of(2026, 2, 17)), 170, 255)
        save("widget-tall-june", widget(PageLayout.TALL, LocalDate.of(2026, 6, 19)), 170, 255)
    }

    /** 每種天氣三格：火柴人全身＋道具 */
    @Test
    fun weatherMoods() {
        val moods = com.patty60723.tearoff.weather.Mood.entries
        val cell = 180
        val bitmap = Bitmap.createBitmap(cell * 3, cell * 4 / 3 * moods.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Palettes.PAPER)
        moods.forEachIndexed { row, mood ->
            for (col in 0 until 3) {
                canvas.save()
                canvas.translate(col * cell.toFloat(), row * cell * 4 / 3f)
                com.patty60723.tearoff.stickman.StickmanRenderer.draw(
                    canvas, com.patty60723.tearoff.stickman.Parts.everything,
                    com.patty60723.tearoff.stickman.Actions.get(mood.action), col / 3f,
                    cell.toFloat(), cell * 4 / 3f, mood.props,
                )
                canvas.restore()
            }
        }
        File(out, "weather-moods.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun cover() {
        StoryBook(context).reset()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView
        save("app-cover", root, root.width, root.height, background = false)
    }

    @Test
    fun app() {
        // 第一章走完的樣子，故事卡上有完整的火柴人
        StoryBook(context).jumpTo(14, AppClock.today(context))
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView
        save("app", root, root.width, root.height, background = false)

        // 假裝下雨，點天氣那行：故事卡上的火柴人撐傘
        com.patty60723.tearoff.weather.WeatherService.setFakeMood(context, com.patty60723.tearoff.weather.Mood.RAIN)
        val rainy = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        rainy.findViewById<android.widget.TextView>(R.id.weather).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        rainy.window.decorView.let { save("app-weather", it, it.width, it.height, background = false) }
        com.patty60723.tearoff.weather.WeatherService.setFakeMood(context, null)

        val pad = activity.findViewById<TearPageView>(R.id.page)
        pad.holdPeel(rightCorner = true, x = pad.width * 0.42f, y = pad.height * 0.62f)
        save("app-peel", root, root.width, root.height, background = false)
    }
}
