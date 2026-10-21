package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
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
        val day = LocalDate.of(2026, 10, 21)
        save("widget-tall", widget(PageLayout.TALL, day), 170, 255)
        save("widget-wide", widget(PageLayout.WIDE, day), 349, 170)
        save("widget-compact", widget(PageLayout.COMPACT, day), 170, 170)
        save("widget-tall-new-year", widget(PageLayout.TALL, LocalDate.of(2026, 2, 17)), 170, 255)
        save("widget-tall-june", widget(PageLayout.TALL, LocalDate.of(2026, 6, 19)), 170, 255)
    }

    @Test
    fun app() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView
        save("app", root, root.width, root.height, background = false)

        val pad = activity.findViewById<TearPageView>(R.id.page)
        pad.holdPeel(rightCorner = true, x = pad.width * 0.42f, y = pad.height * 0.62f)
        save("app-peel", root, root.width, root.height, background = false)
    }
}
