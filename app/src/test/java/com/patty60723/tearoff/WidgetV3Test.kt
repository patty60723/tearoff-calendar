package com.patty60723.tearoff

import android.appwidget.AppWidgetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import com.patty60723.tearoff.mowan.Detail
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanTimeline
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.stickman.StickmanRenderer
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** P5-3：小工具與 App 日曆頁接上 V3 墨丸 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetV3Test {
    private val context get() = RuntimeEnvironment.getApplication()
    private val today get() = AppClock.today(context)
    private val page get() = Almanac.page(today)

    private val frameIds = listOf(R.id.frame0, R.id.frame1, R.id.frame2, R.id.frame3, R.id.frame4, R.id.frame5)

    @Before
    fun setUp() {
        StoryBook(context).jumpTo(14, today)
        MowanSettings.setStoryCard(context, true)
        WeatherService.setFakeMood(context, Mood.RAIN)
    }

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        MowanSettings.resetEquipment(context)
        WeatherService.setFakeMood(context, null)
    }

    private fun applied(layout: PageLayout, date: LocalDate = today, stickmanDp: Float = layout.stickmanDp): View =
        CalendarWidget.build(context, Almanac.page(date), layout, stickmanDp).apply(context, FrameLayout(context))

    private fun View.frames(): List<Bitmap> = frameIds.map { (findViewById<ImageView>(it).drawable as BitmapDrawable).bitmap }

    private val density get() = context.resources.displayMetrics.density.coerceAtMost(3f)

    /** App 那邊應該畫出來的圖：同樣的部位、紙帽、包袱、天氣 */
    private fun expected(widthDp: Float, weather: Mowan.Weather = Mowan.Weather.RAINY) = MowanBitmap.still(
        Mowan.partsOf(StoryBook(context).parts), MowanSettings.hatOn(context), MowanSettings.bundleOn(context), weather,
        (widthDp * density).toInt(), (widthDp * 4 / 3 * density).toInt(), density,
    )

    @Test
    fun oneStillPictureInEveryLayout() {
        for (layout in PageLayout.entries) {
            val frames = applied(layout).frames()
            val at = layout.name
            // 6 格都是同一張靜態圖
            assertTrue(at, frames.all { it.sameAs(frames[0]) })
            assertTrue("$at 跟 App 的狀態一樣", frames[0].sameAs(expected(layout.stickmanDp)))
        }
    }

    /** D-25：小工具（48–64 dp 高）用簡化版；App 日曆頁（約 80 dp 高）用完整版 */
    @Test
    fun simplifiedInWidgetsFullOnTheAppPage() {
        val parts = Mowan.partsOf(StoryBook(context).parts)
        fun direct(widthDp: Float, detail: Detail): Bitmap {
            val w = (widthDp * density).toInt()
            val h = (widthDp * 4 / 3 * density).toInt()
            val frame = Mowan.frame(parts, 0, true, Mowan.Weather.RAINY, MowanTimeline.stillClock(Mowan.Weather.RAINY), still = true, detail = detail)
            return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { MowanPainter(frame).draw(Canvas(it), 0f, 0f, w.toFloat(), h.toFloat()) }
        }
        for (layout in PageLayout.entries) {
            val h = layout.stickmanDp * 4 / 3 * density
            val got = applied(layout).frames()[0]
            assertTrue("${layout.name} 簡化", got.sameAs(direct(layout.stickmanDp, Detail.simplified(h.toDouble()))))
            assertFalse("${layout.name} 不是完整版", got.sameAs(direct(layout.stickmanDp, Detail.FULL)))
        }
        // App 日曆頁：60 dp 寬 → 80 dp 高
        val pageDp = 60f
        val got = applied(PageLayout.TALL, stickmanDp = pageDp).frames()[0]
        assertTrue("日曆頁完整版", got.sameAs(direct(pageDp, Detail.FULL)))
    }

    @Test
    fun followsTheSameStateAsTheApp() {
        fun tall() = applied(PageLayout.TALL).frames()[0]
        val rain = tall()
        // 紙帽、包袱
        MowanSettings.setHatOn(context, false)
        val noHat = tall()
        assertTrue(noHat.sameAs(expected(PageLayout.TALL.stickmanDp)))
        assertFalse(noHat.sameAs(rain))
        MowanSettings.setHatOn(context, true)
        MowanSettings.setBundleOn(context, false)
        val noBundle = tall()
        assertTrue(noBundle.sameAs(expected(PageLayout.TALL.stickmanDp)))
        assertFalse(noBundle.sameAs(rain))
        MowanSettings.setBundleOn(context, true)
        // 天氣
        WeatherService.setFakeMood(context, Mood.SNOW)
        assertTrue(tall().sameAs(expected(PageLayout.TALL.stickmanDp, Mowan.Weather.SNOW)))
        // 頁數
        StoryBook(context).jumpTo(5, today)
        assertTrue(tall().sameAs(expected(PageLayout.TALL.stickmanDp, Mowan.Weather.SNOW)))
        // 不是今天那頁：不畫天氣
        assertTrue(applied(PageLayout.TALL, today.plusDays(1)).frames()[0].sameAs(expected(PageLayout.TALL.stickmanDp, Mowan.Weather.NONE)))
    }

    /** 關掉 V3：跟原本的舊火柴人完全一樣（6 張輪播） */
    @Test
    fun oldStickmanUnchangedWhenSwitchedOff() {
        MowanSettings.setStoryCard(context, false)
        val book = StoryBook(context)
        for (layout in PageLayout.entries) {
            val frames = applied(layout).frames()
            val old = StickmanRenderer.frames(
                book.parts, Actions.get(book.lastAction), 6,
                widthPx = (layout.stickmanDp * density).toInt(), heightPx = (layout.stickmanDp * 4 / 3 * density).toInt(),
                props = book.lastProps,
            )
            frames.zip(old).forEachIndexed { i, (a, b) -> assertTrue("${layout.name} 第 $i 張", a.sameAs(b)) }
        }
    }

    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf("${v.id}:${v.visibility}:${v.text}")
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    /** 日期、宜忌、農曆、節日、天氣、日出日落：V3 開或關都一樣 */
    @Test
    fun textsAndLayoutUnchanged() {
        for (date in listOf(today, today.plusDays(1), LocalDate.of(2027, 2, 6))) for (layout in PageLayout.entries) {
            val v3 = texts(applied(layout, date))
            MowanSettings.setStoryCard(context, false)
            val old = texts(applied(layout, date))
            MowanSettings.setStoryCard(context, true)
            assertEquals("$date ${layout.name}", old, v3)
        }
    }

    private fun estimate(views: RemoteViews) = (RemoteViews::class.java.getMethod("estimateMemoryUsage").invoke(views) as Number).toLong()

    /** 一次更新（Android 12 以上 4 種版面）的 bitmap 總量在 1 MB 以內 */
    @Test
    fun bitmapBudget() {
        val out = StringBuilder("# 小工具 bitmap（P5-3，V3 靜態一張）\n\nRobolectric 量測，`RemoteViews.estimateMemoryUsage()`。\n\n| 密度 | 4 版面合計 | 只送 TALL（Android 8–11） |\n|---|---|---|\n")
        for (q in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-$q")
            val perLayout = PageLayout.entries.associateWith { CalendarWidget.build(context, page, it) }
            val single = estimate(perLayout.getValue(PageLayout.TALL))
            val all = estimate(RemoteViews(perLayout.entries.associate { (l, v) -> SizeF(l.minWidthDp, l.minHeightDp) to v }))
            out.append("| $q | ${"%.1f".format(all / 1024.0)} KB | ${"%.1f".format(single / 1024.0)} KB |\n")
            assertTrue("$q：${all / 1024} KB", all < 1024 * 1024)
        }
        File("build/screenshots/p5").apply { mkdirs() }.resolve("bitmap-v3.md").writeText(out.toString())
        println(out)
    }

    /** 包袱入口切換後，桌面小工具也更新 */
    @Test
    fun bundleToggleUpdatesTheWidget() {
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(CalendarWidget::class.java, R.layout.widget_tall)
        CalendarWidget.refreshAll(context)
        fun widgetFrame() = (manager.getViewFor(id).findViewById<ImageView>(R.id.frame0).drawable as BitmapDrawable).bitmap
        val before = widgetFrame()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        a.findViewById<TextView>(R.id.story_bundle).performClick()
        assertFalse(MowanSettings.bundleOn(context))
        assertFalse("小工具上的包袱要卸下", widgetFrame().sameAs(before))
        // App 日曆頁也換了（最上面那張可能是今天或昨天那頁；不是今天就不畫天氣）
        val pageFrame = (a.findViewById<ImageView>(R.id.frame0).drawable as BitmapDrawable).bitmap
        val parts = Mowan.partsOf(StoryBook(context).parts)
        val candidates = listOf(Mowan.Weather.RAINY, Mowan.Weather.NONE).map { w ->
            MowanBitmap.still(parts, true, false, w, pageFrame.width, pageFrame.height, density)
        }
        assertTrue("日曆頁的包袱要卸下", candidates.any { pageFrame.sameAs(it) })
    }

    // ---------- 截圖（不是比對） ----------

    /** 今天那頁（天氣只畫在今天那頁，跟日出日落旁的天氣文字一樣） */
    private fun shot(layout: PageLayout, date: LocalDate = today): Bitmap {
        val d = context.resources.displayMetrics.density
        val w = (layout.minWidthDp * 1.15f * d).toInt()
        val h = (layout.minHeightDp * 1.15f * d).toInt()
        val v = CalendarWidget.build(context, Almanac.page(date), layout).apply(context, FrameLayout(context))
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFF6B7A8C.toInt()); v.draw(this) } }
    }

    /** 4 種版面：舊火柴人與 V3 並排（xxhdpi）；再加上 mdpi 的 V3（簡化版的真實像素） */
    @Test
    fun widgetScreenshots() {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 36f }
        fun sheet(name: String, rows: List<Pair<String, List<Bitmap>>>) {
            val w = rows.maxOf { (_, bs) -> bs.sumOf { it.width + 24 } } + 260
            val h = rows.sumOf { (_, bs) -> bs.maxOf { it.height } + 24 } + 24
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b).apply { drawColor(0xFF4F5B68.toInt()) }
            var y = 24f
            for ((title, bs) in rows) {
                c.drawText(title, 16f, y + 60, label)
                var x = 260f
                // 不同密度畫出來的圖不要被自動縮放：照實際像素貼上
                for (bm in bs) { bm.density = b.density; c.drawBitmap(bm, x, y, null); x += bm.width + 24 }
                y += bs.maxOf { it.height } + 24
            }
            File("build/screenshots/p5").apply { mkdirs() }.resolve("$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val layouts = listOf(PageLayout.COMPACT, PageLayout.MEDIUM, PageLayout.WIDE, PageLayout.TALL)
        MowanSettings.setStoryCard(context, false)
        val old = layouts.map { shot(it) }
        MowanSettings.setStoryCard(context, true)
        val v3 = layouts.map { shot(it) }
        WeatherService.setFakeMood(context, Mood.SUNNY)
        val sunny = layouts.map { shot(it) }
        sheet("widgets-v3", listOf("舊火柴人" to old, "V3 雨" to v3, "V3 晴" to sunny))

        // 8 種天氣（TALL）
        val moods = Mood.entries
        val all = moods.map { WeatherService.setFakeMood(context, it); shot(PageLayout.TALL) }
        sheet("widgets-v3-weathers", listOf("V3 TALL" to all.take(4), "" to all.drop(4)))

        // 密度：mdpi／hdpi／xhdpi／xxhdpi 的 TALL 與 COMPACT（雨）
        WeatherService.setFakeMood(context, Mood.RAIN)
        val rows = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi").map { q ->
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-$q")
            q to listOf(shot(PageLayout.TALL), shot(PageLayout.COMPACT))
        }
        sheet("widgets-v3-densities", rows)
    }

    /** App 日曆頁（V3，完整版） */
    @Test
    fun appPageScreenshot() {
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val pageView = a.findViewById<View>(R.id.page)
        val b = Bitmap.createBitmap(pageView.width, pageView.height, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { drawColor(0xFFEFEAE1.toInt()); pageView.draw(this) }
        File("build/screenshots/p5").apply { mkdirs() }.resolve("app-page-v3.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
