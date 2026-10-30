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
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/**
 * P6-4c：節日接到正式的小工具（D-16、D-43 Confirmed）。
 * 節日當天今天那頁的 V3 墨丸畫節日靜態外觀；COMPACT 用極簡、其他版面用節日＋一個天氣線索；
 * 狀態（日期、成長頁數、紙帽、包袱、天氣、V3 開關）跟 App 同一個來源。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetFestivalTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val eve = LocalDate.of(2027, 2, 5)
    private val spring = LocalDate.of(2027, 2, 6)
    private val fourth = LocalDate.of(2027, 2, 9)

    @Before
    fun setUp() {
        WeatherService.setFakeMood(context, Mood.RAIN)
        today(spring)
        StoryBook(context).jumpTo(14, spring)
    }

    @After
    fun cleanUp() {
        MowanSettings.resetEquipment(context)
        MowanSettings.setFestivalHeadwear(context, true)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    private fun today(d: LocalDate) = AppClock.setFakeToday(context, d)

    private val density get() = context.resources.displayMetrics.density.coerceAtMost(3f)

    private fun applied(layout: PageLayout, date: LocalDate = AppClock.today(context)): View =
        CalendarWidget.build(context, Almanac.page(date), layout).apply(context, FrameLayout(context))

    private fun View.still(): Bitmap = (findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap

    private fun weather() = WeatherService.current(context)?.let { MowanSettings.weatherOf(it.mood) } ?: Mowan.Weather.NONE

    /** 照 App 的共同狀態直接畫：小工具應該就是這張 */
    private fun expected(layout: PageLayout, festival: Festival?) = MowanBitmap.still(
        Mowan.partsOf(StoryBook(context).parts), MowanSettings.hatOn(context), MowanSettings.bundleOn(context), weather(),
        (layout.characterDp * density).toInt(), (layout.characterDp * 4 / 3 * density).toInt(), density,
        festival, MowanSettings.art(context).copy(smallEnv = CalendarWidget.smallEnvFor(layout)),
    )

    // ---------- 日期 ----------

    /** 桌面上的小工具：換到節日當天、節日裡換一天、離開節日當天 */
    @Test
    fun enteringAndLeavingTheFestivalDay() {
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(CalendarWidget::class.java, R.layout.widget_tall)
        for ((d, f) in listOf(eve to null, spring to Festival.SPRING, spring.plusDays(1) to Festival.SPRING, fourth to null)) {
            today(d)
            assertEquals(f, FestivalTheme.of(d))
            CalendarWidget.refreshAll(context)
            val got = manager.getViewFor(id).still()
            // Android 12 以上由系統照大小挑版面：看圖的寬度是哪一個版面的
            val layout = PageLayout.entries.single { (it.characterDp * density).toInt() == got.width }
            assertTrue("$d ${layout.name}", got.sameAs(expected(layout, f)))
            if (f != null) assertFalse("$d 節日外觀", got.sameAs(expected(layout, null)))
        }
    }

    /** 規則：COMPACT 是極簡、其他版面是節日＋一個天氣線索；四種版面都跟 App 的狀態一致 */
    @Test
    fun everyLayoutFollowsTheRule() {
        for (layout in PageLayout.entries) {
            val got = applied(layout).still()
            assertTrue(layout.name, got.sameAs(expected(layout, Festival.SPRING)))
            assertFalse("${layout.name} 跟平常不一樣", got.sameAs(expected(layout, null)))
        }
    }

    // ---------- 共同狀態 ----------

    @Test
    fun followsTheSharedState() {
        fun tall() = applied(PageLayout.TALL).still()
        val base = tall()
        assertTrue(base.sameAs(expected(PageLayout.TALL, Festival.SPRING)))
        // 紙帽：節日頭飾戴著時紙帽讓位（D-05），畫面一樣是對的
        MowanSettings.setHatOn(context, false)
        assertTrue("紙帽", tall().sameAs(expected(PageLayout.TALL, Festival.SPRING)))
        MowanSettings.setHatOn(context, true)
        // 節日頭飾關閉（除錯）：照紙帽畫
        MowanSettings.setFestivalHeadwear(context, false)
        val noHeadwear = tall()
        assertTrue(noHeadwear.sameAs(expected(PageLayout.TALL, Festival.SPRING)))
        assertFalse("節日頭飾開關", noHeadwear.sameAs(base))
        MowanSettings.setFestivalHeadwear(context, true)
        // 包袱
        MowanSettings.setBundleOn(context, false)
        val noBundle = tall()
        assertTrue(noBundle.sameAs(expected(PageLayout.TALL, Festival.SPRING)))
        assertFalse("包袱", noBundle.sameAs(base))
        MowanSettings.setBundleOn(context, true)
        // 天氣
        WeatherService.setFakeMood(context, Mood.SNOW)
        val snow = tall()
        assertTrue(snow.sameAs(expected(PageLayout.TALL, Festival.SPRING)))
        assertFalse("天氣", snow.sameAs(base))
        // 成長頁數
        StoryBook(context).jumpTo(5, spring)
        val page5 = tall()
        assertTrue(page5.sameAs(expected(PageLayout.TALL, Festival.SPRING)))
        assertFalse("頁數", page5.sameAs(snow))
    }

    /** 節日當天也是一張靜態圖（不重新引入輪播；P7-1 起版面裡沒有 ViewFlipper） */
    @Test
    fun staticOnFestivalDays() {
        for (f in listOf(spring, LocalDate.of(2027, 9, 15), LocalDate.of(2027, 12, 25))) {
            today(f)
            for (layout in PageLayout.entries) {
                val v = applied(layout)
                val at = "$f ${layout.name}"
                assertEquals(at, View.VISIBLE, v.findViewById<View>(R.id.mowan_still).visibility)
                fun all(x: View): List<View> = listOf(x) + ((x as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { all(g.getChildAt(it)) } } ?: emptyList())
                assertTrue(at, all(v).none { it is android.widget.ViewFlipper })
            }
        }
    }

    private fun estimate(views: RemoteViews) = (RemoteViews::class.java.getMethod("estimateMemoryUsage").invoke(views) as Number).toLong()

    /** bitmap 總量、畫幾張、故事解析幾次：節日當天跟平常一樣 */
    @Test
    fun budgetAndParsesDoNotGrow() {
        fun update(d: LocalDate): Triple<Long, Int, Int> {
            today(d)
            StoryBook.Assets.clear()
            val parses = StoryBook.Assets.parseCount
            val renders = MowanBitmap.renderCount
            val all = PageLayout.entries.associate { l -> SizeF(l.minWidthDp, l.minHeightDp) to CalendarWidget.build(context, Almanac.page(d), l) }
            return Triple(estimate(RemoteViews(all)), MowanBitmap.renderCount - renders, StoryBook.Assets.parseCount - parses)
        }
        val plain = update(fourth)
        val festive = update(spring)
        assertEquals("bitmap 總量", plain.first, festive.first)
        assertEquals("一次更新畫 4 張", 4, festive.second)
        assertEquals(plain.second, festive.second)
        assertEquals("故事只解析一次", 1, festive.third)
        assertEquals(plain.third, festive.third)
        assertTrue("${festive.first / 1024} KB", festive.first < 1024 * 1024)
    }

    // ---------- App 日曆頁（也用 CalendarWidget.build） ----------

    private fun pageStill(a: MainActivity) = (a.findViewById<TearPageView>(R.id.page).findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap

    /** 窄螢幕、各種密度：今天那頁畫節日外觀；角色小於 72dp 時跟小工具 TALL 一樣簡化 */
    @Test
    fun appPageOnDifferentScreens() {
        val out = File("build/screenshots/p6-4c-widget").apply { mkdirs() }
        val qualifiers = listOf("w320dp-h640dp-mdpi", "w320dp-h640dp-xhdpi", "w360dp-h740dp-xxhdpi", "w411dp-h891dp-xxhdpi", "w600dp-h960dp-xhdpi")
        val shots = mutableListOf<Pair<String, Bitmap>>()
        for (q in qualifiers) {
            RuntimeEnvironment.setQualifiers(q)
            StoryBook(context).apply { jumpTo(13, spring); tear(spring) }
            val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val got = pageStill(a)
            val m = a.resources.displayMetrics
            val pageWidthPx = m.widthPixels - (56 * m.density).toInt()
            val dp = (pageWidthPx * 0.17f).toInt() / m.density
            fun build(festive: Boolean) = (CalendarWidget.build(context, Almanac.page(spring), PageLayout.TALL, dp, festivalLook = festive)
                .apply(context, FrameLayout(context)).findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap
            assertTrue("$q 今天那頁：節日外觀", got.sameAs(build(true)))
            assertFalse("$q 跟平常不一樣", got.sameAs(build(false)))
            val pageView = a.findViewById<View>(R.id.page)
            val b = Bitmap.createBitmap(pageView.width, pageView.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEFEAE1.toInt()); pageView.draw(this) } }
            shots += "$q 角色 ${(dp * 4 / 3).toInt()}dp${if (dp * 4 / 3 < 72) "（簡化）" else ""}" to b
        }
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        // 縮到一樣高排成一列
        val h = 900
        val scaled = shots.map { (t, b) -> t to Bitmap.createScaledBitmap(b, b.width * h / b.height, h, true) }
        // 不同密度畫出來的圖：照實際像素貼上，不要被自動縮放
        scaled.forEach { it.second.density = Bitmap.DENSITY_NONE }
        val sheet = Bitmap.createBitmap(scaled.sumOf { it.second.width + 20 }, h + 70, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 26f }
        Canvas(sheet).apply {
            drawColor(0xFFEFEAE1.toInt())
            var x = 0f
            for ((t, b) in scaled) { drawText(t, x + 6, 40f, label); drawBitmap(b, x, 70f, null); x += b.width + 20 }
        }
        File(out, "2-app-pages.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------- 驗收截圖：3 節日 × 4 版面 × 9 天氣（整個小工具，最小尺寸，縮成 50%） ----------

    @Test
    fun fullWidgetSheets() {
        val out = File("build/screenshots/p6-4c-widget").apply { mkdirs() }
        val weathers = listOf(null to "沒有天氣") + Mood.entries.map { it to it.keyword }
        val layouts = listOf(PageLayout.COMPACT, PageLayout.WIDE, PageLayout.MEDIUM, PageLayout.TALL)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 30f }
        for ((d, name) in listOf(spring to "SPRING", LocalDate.of(2027, 9, 15) to "MOON", LocalDate.of(2027, 12, 25) to "XMAS")) {
            today(d)
            val dens = context.resources.displayMetrics.density
            val rows = weathers.map { (m, _) ->
                WeatherService.setFakeMood(context, m)
                layouts.map { l ->
                    val v = applied(l)
                    val w = (l.minWidthDp * dens).toInt()
                    val hh = (l.minHeightDp * dens).toInt()
                    v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.EXACTLY))
                    v.layout(0, 0, w, hh)
                    val b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEFEAE1.toInt()); v.draw(this) } }
                    Bitmap.createScaledBitmap(b, w / 2, hh / 2, true)
                }
            }
            val colW = layouts.map { l -> (l.minWidthDp * dens).toInt() / 2 + 16 }
            val rowH = rows.map { r -> r.maxOf { it.height } + 16 }
            val left = 140
            val top = 110
            val sheet = Bitmap.createBitmap(left + colW.sum(), top + rowH.sum(), Bitmap.Config.ARGB_8888)
            Canvas(sheet).apply {
                drawColor(0xFFEFEAE1.toInt())
                var x = left.toFloat()
                layouts.forEachIndexed { i, l ->
                    drawText(l.name, x, 40f, label)
                    drawText(if (CalendarWidget.smallEnvFor(l).name == "MINIMAL") "C 極簡" else "B 節日+線索", x, 80f, label)
                    x += colW[i]
                }
                var y = top.toFloat()
                rows.forEachIndexed { r, cells ->
                    drawText(weathers[r].second, 10f, y + rowH[r] / 2, label)
                    var cx = left.toFloat()
                    cells.forEachIndexed { i, b -> drawBitmap(b, cx, y, null); cx += colW[i] }
                    y += rowH[r]
                }
            }
            File(out, "1-$name-widgets.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
