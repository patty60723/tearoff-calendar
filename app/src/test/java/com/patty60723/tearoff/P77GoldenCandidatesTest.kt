package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
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
 * P7-7：英文月份改成 #746D64 後的 golden **候選**與差異圖（不更新 `src/test/resources/v3-png/` 的基準，等人工核准）。
 * 畫法照 [V3PngGoldenTest]（同樣的條件、同樣的尺寸與背景）；先確認不改顏色時跟鎖定的基準逐像素相同，證明畫法一樣，
 * 再把畫面上的英文月份換成候選色（模擬版面檔 textColor 的修改），跟基準比。
 * 輸出：app/build/screenshots/p7-7-golden-candidates/（candidate／diff／report.md）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class P77GoldenCandidatesTest {
    private val baseline = File("src/test/resources/v3-png")
    private val out = File("build/screenshots/p7-7-golden-candidates").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()
    private val candidate = 0xFF746D64.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setBundleOn(context, true)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    data class Condition(val id: String, val day: LocalDate, val mood: Mood, val festival: Festival?, val page: Int = 14, val bundle: Boolean = true)

    private fun firstDay(f: Festival) = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == f }
    private val normal = LocalDate.of(2027, 10, 21)
    private val conditions = listOf(
        Condition("normal-sunny", normal, Mood.SUNNY, null),
        Condition("normal-rainy", normal, Mood.RAIN, null),
        Condition("spring", firstDay(Festival.SPRING), Mood.SUNNY, Festival.SPRING),
        Condition("moon", firstDay(Festival.MOON), Mood.SUNNY, Festival.MOON),
        Condition("xmas", firstDay(Festival.XMAS), Mood.SUNNY, Festival.XMAS),
        Condition("spring-rainy", firstDay(Festival.SPRING), Mood.RAIN, Festival.SPRING),
        Condition("moon-rainy", firstDay(Festival.MOON), Mood.RAIN, Festival.MOON),
        Condition("page1", normal, Mood.SUNNY, null, page = 1),
        Condition("bundle-off", normal, Mood.SUNNY, null, bundle = false),
    )

    private fun shoot(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(paper); v.draw(this) } }

    /** 換成候選色：只動看得到的英文月份（版面檔的 textColor 改了就是這樣） */
    private fun recolor(v: View): Boolean {
        val en = v.findViewById<TextView>(R.id.month_en) ?: return false
        if (en.visibility != View.VISIBLE || en.text.isEmpty()) return false
        en.setTextColor(candidate)
        return true
    }

    private fun widget(c: Condition, layout: PageLayout, recolor: Boolean): Pair<Bitmap, Boolean> {
        AppClock.setFakeToday(context, c.day)
        StoryBook(context).jumpTo(c.page, c.day)
        MowanSettings.setBundleOn(context, c.bundle)
        WeatherService.setFakeMood(context, c.mood)
        val d = context.resources.displayMetrics.density
        val v = CalendarWidget.build(context, Almanac.page(c.day), layout).apply(context, FrameLayout(context)).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec((layout.minWidthDp * d).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((layout.minHeightDp * d).toInt(), View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
        val changed = recolor && recolor(v)
        return shoot(v) to changed
    }

    private fun page(c: Condition, recolor: Boolean): Pair<Bitmap, Boolean> {
        WeatherService.setFakeMood(context, c.mood)
        AppClock.setFakeToday(context, c.day)
        MowanSettings.setBundleOn(context, c.bundle)
        StoryBook(context).apply { jumpTo(c.page - 1, c.day); tear(c.day) }
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val p = a.findViewById<View>(R.id.page)
        val changed = recolor && recolor(p)
        return shoot(p) to changed
    }

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    /** 跟 V3PngGoldenTest 同樣的比法（每個通道容許 2）；回傳（不同的像素數, 差異圖） */
    private fun diff(e: Bitmap, a: Bitmap): Pair<Int, Bitmap?> {
        if (e.width != a.width || e.height != a.height) return -1 to null
        val ep = pixels(e)
        val ap = pixels(a)
        val o = IntArray(ep.size)
        var n = 0
        for (i in ep.indices) {
            var m = 0
            for (s in intArrayOf(0, 8, 16, 24)) m = maxOf(m, Math.abs(((ep[i] shr s) and 0xFF) - ((ap[i] shr s) and 0xFF)))
            if (m > V3PngGoldenTest.CHANNEL_TOLERANCE) n++
            o[i] = if (m > V3PngGoldenTest.CHANNEL_TOLERANCE) 0xFFE0301E.toInt() else (ep[i] and 0x00FFFFFF) or 0x30000000
        }
        return n to Bitmap.createBitmap(o, e.width, e.height, Bitmap.Config.ARGB_8888)
    }

    private fun save(b: Bitmap, f: File) { f.parentFile!!.mkdirs(); f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }

    private fun base(name: String) = BitmapFactory.decodeFile(File(baseline, "$name.png").path, BitmapFactory.Options().apply { inScaled = false })

    @Test
    fun englishMonthCandidates() {
        val report = StringBuilder("# 英文月份 #746D64：golden 候選與差異（**未更新基準**）\n\n")
        report.append("先確認不改顏色時，這裡的畫法跟鎖定的基準逐像素相同；再換成候選色比較。日曆頁與四種小工具共 45 張（故事卡沒有英文月份，不受影響）。\n\n")
        report.append("| 名稱 | 不改顏色時跟基準 | 有英文月份 | 換色後跟基準不同的像素 |\n|---|---|---|---|\n")
        val changed = mutableListOf<String>()
        val unreproduced = mutableListOf<String>()
        val surfaces = listOf("page") + PageLayout.entries.map { "widget-${it.name.lowercase()}" }
        for (c in conditions) for (s in surfaces) {
            val name = "$s-${c.id}"
            val layout = PageLayout.entries.firstOrNull { "widget-${it.name.lowercase()}" == s }
            val (same, _) = if (layout == null) page(c, false) else widget(c, layout, false)
            val (n0, _) = diff(base(name), same)
            if (n0 != 0) unreproduced += "$name（$n0）"
            val (cand, has) = if (layout == null) page(c, true) else widget(c, layout, true)
            val (n, img) = diff(base(name), cand)
            if (n != 0) {
                changed += name
                save(cand, File(out, "candidate/$name.png"))
                img?.let { save(it, File(out, "diff/$name.png")) }
            }
            report.append("| $name | ${if (n0 == 0) "相同" else "**$n0 個像素不同**"} | ${if (has) "是" else "否"} | ${if (n == 0) "0" else "**$n**"} |\n")
        }
        report.append("\n不改顏色時跟基準不同：${if (unreproduced.isEmpty()) "無（畫法跟 V3PngGoldenTest 一樣）" else unreproduced.joinToString("、")}\n")
        report.append("\n**換色後會改的 golden：${changed.size} 張**\n\n" + changed.joinToString("\n") { "- $it" } + "\n")
        File(out, "report.md").writeText(report.toString())
        assertEquals("畫法要先跟基準一樣：$unreproduced", emptyList<String>(), unreproduced)
    }

    // ---------- 2026-11-05 後續分流：春節 S1（小尺寸吊繩縮短 6 單位）實際會改哪些 golden ----------

    private fun ys(p: com.patty60723.tearoff.mowan.PathData) = p.commands.mapNotNull { c -> when (c) {
        is com.patty60723.tearoff.mowan.PathData.Cmd.MoveTo -> c.y; is com.patty60723.tearoff.mowan.PathData.Cmd.LineTo -> c.y
        is com.patty60723.tearoff.mowan.PathData.Cmd.ArcTo -> c.y; is com.patty60723.tearoff.mowan.PathData.Cmd.QuadTo -> c.y; else -> null } }

    private fun mapY(p: com.patty60723.tearoff.mowan.PathData, f: (Double) -> Double) = com.patty60723.tearoff.mowan.PathData(p.commands.map { c ->
        when (c) {
            is com.patty60723.tearoff.mowan.PathData.Cmd.MoveTo -> c.copy(y = f(c.y))
            is com.patty60723.tearoff.mowan.PathData.Cmd.LineTo -> c.copy(y = f(c.y))
            is com.patty60723.tearoff.mowan.PathData.Cmd.ArcTo -> c.copy(y = f(c.y))
            is com.patty60723.tearoff.mowan.PathData.Cmd.QuadTo -> c.copy(y1 = f(c.y1), y = f(c.y))
            else -> c
        }
    })

    /**
     * 把畫面上的墨丸重畫一次（跟 CalendarWidget.setMowan 同樣的參數；大小照現在那張圖），[s1] 時吊繩縮短 6 單位：
     * 裝訂環與吊繩（"couplet" 底下、y 都 ≤ 14.5 的路徑）往下端 y 14.5 壓縮，春聯菱形不動。只有小尺寸（簡化版）才畫這條吊繩的版本受影響。
     */
    private fun redraw(root: View, day: LocalDate, layout: PageLayout, s1: Boolean): Boolean {
        val iv = root.findViewById<android.widget.ImageView>(R.id.mowan_still) ?: return false
        val old = (iv.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return false
        val density = context.resources.displayMetrics.density.coerceAtMost(3f)
        val weather = WeatherService.current(context)?.let { MowanSettings.weatherOf(it.mood) } ?: com.patty60723.tearoff.mowan.Mowan.Weather.NONE
        val drawnHeight = minOf(old.width / 120.0, old.height / 160.0) * 160.0
        val frame = com.patty60723.tearoff.mowan.Mowan.frame(
            com.patty60723.tearoff.mowan.Mowan.partsOf(StoryBook(context).parts), 0, MowanSettings.hatOn(context), weather,
            com.patty60723.tearoff.mowan.MowanTimeline.stillClock(weather), still = true, bundleOn = MowanSettings.bundleOn(context),
            detail = com.patty60723.tearoff.mowan.Detail.of(drawnHeight, density.toDouble()),
            festival = if (day == AppClock.today(context)) FestivalTheme.of(day) else null,
            art = MowanSettings.art(context).copy(smallEnv = CalendarWidget.smallEnvFor(layout)),
        )
        val k = 1 - 6.0 / (14.5 + 17.2)
        var touched = false
        val ops = frame.ops.map { op ->
            val hang = s1 && op.part == "couplet" && ys(op.path).let { it.isNotEmpty() && it.max() <= 14.6 && it.min() < 0 }
            if (hang) { touched = true; op.copy(path = mapY(op.path) { y -> 14.5 - (14.5 - y) * k }) } else op
        }
        val bmp = Bitmap.createBitmap(old.width, old.height, Bitmap.Config.ARGB_8888)
        com.patty60723.tearoff.mowan.MowanPainter(com.patty60723.tearoff.mowan.Mowan.Frame(ops, frame.head, frame.canopy)).draw(Canvas(bmp), 0f, 0f, old.width.toFloat(), old.height.toFloat())
        iv.setImageBitmap(bmp)
        return touched
    }

    private fun scene(c: Condition, s: String): Pair<View, PageLayout> {
        val layout = PageLayout.entries.firstOrNull { "widget-${it.name.lowercase()}" == s }
        if (layout != null) {
            AppClock.setFakeToday(context, c.day)
            StoryBook(context).jumpTo(c.page, c.day)
            MowanSettings.setBundleOn(context, c.bundle)
            WeatherService.setFakeMood(context, c.mood)
            val d = context.resources.displayMetrics.density
            val v = CalendarWidget.build(context, Almanac.page(c.day), layout).apply(context, FrameLayout(context)).apply {
                measure(View.MeasureSpec.makeMeasureSpec((layout.minWidthDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((layout.minHeightDp * d).toInt(), View.MeasureSpec.EXACTLY))
                layout(0, 0, measuredWidth, measuredHeight)
            }
            return v to layout
        }
        WeatherService.setFakeMood(context, c.mood)
        AppClock.setFakeToday(context, c.day)
        MowanSettings.setBundleOn(context, c.bundle)
        StoryBook(context).apply { jumpTo(c.page - 1, c.day); tear(c.day) }
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        return a.findViewById<View>(R.id.page) to PageLayout.TALL
    }

    @Test
    fun springS1Goldens() {
        val out2 = File(out.parentFile, "p7-7-spring-s1").apply { mkdirs() }
        val report = StringBuilder("# 春節 S1（小尺寸吊繩縮短 6 單位）實際會改的 golden\n\n")
        report.append("54 張 golden 的畫法（故事卡沒有小工具的墨丸圖，不受影響：9 張）＋日曆頁與 4 種小工具 × 9 種條件＝45 個畫面。每個畫面：先確認重畫墨丸（不改）跟原本逐像素相同，再套用 S1 比較。\n\n")
        report.append("| 名稱 | 重畫（不改）跟原本 | 有吊繩 | S1 之後不同的像素 | 範圍（dp） |\n|---|---|---|---|---|\n")
        val changed = mutableListOf<Pair<String, Pair<Bitmap, Bitmap>>>()
        val surfaces = listOf("page") + PageLayout.entries.map { "widget-${it.name.lowercase()}" }
        val bad = mutableListOf<String>()
        val dd = context.resources.displayMetrics.density
        for (c in conditions) for (s in surfaces) {
            val name = "$s-${c.id}"
            val (v, layout) = scene(c, s)
            val orig = shoot(v)
            redraw(v, c.day, layout, false)
            val same = shoot(v).sameAs(orig)
            if (!same) bad += name
            val hang = redraw(v, c.day, layout, true)
            val after = shoot(v)
            val (n, _) = diff(orig, after)
            var box: android.graphics.Rect? = null
            if (n > 0) {
                val a = pixels(orig); val b = pixels(after)
                for (i in a.indices) if (a[i] != b[i]) { val x = i % orig.width; val y = i / orig.width; box = (box ?: android.graphics.Rect(x, y, x + 1, y + 1)).apply { union(x, y, x + 1, y + 1) } }
                val bx = box!!
                val pad = (8 * dd).toInt()
                val r = android.graphics.Rect(bx.left - pad, bx.top - pad, bx.right + pad, bx.bottom + pad).apply { intersect(0, 0, orig.width, orig.height) }
                fun crop(b0: Bitmap) = Bitmap.createScaledBitmap(Bitmap.createBitmap(b0, r.left, r.top, r.width(), r.height()), r.width() * 4, r.height() * 4, false)
                changed += name to (crop(orig) to crop(after))
                save(crop(orig), File(out2, "$name-before.png")); save(crop(after), File(out2, "$name-s1.png"))
            }
            report.append("| $name | ${if (same) "相同" else "**不同**"} | ${if (hang) "是" else "否"} | ${if (n == 0) "0" else "**$n**"} | ${box?.let { "%.1f–%.1f × %.1f–%.1f".format(it.left / dd, it.right / dd, it.top / dd, it.bottom / dd) } ?: "—"} |\n")
        }
        report.append("\n重畫不改時跟原本不同：${if (bad.isEmpty()) "無" else bad.joinToString("、")}\n")
        report.append("\n**S1 會改的 golden：${changed.size} 張**\n\n" + changed.joinToString("\n") { "- ${it.first}" } + "\n")
        File(out2, "report.md").writeText(report.toString())
        // 放大總覽：每一列一張（改之前｜S1）
        if (changed.isNotEmpty()) {
            val words = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 28f }
            val rowH = changed.maxOf { it.second.first.height } + 50
            val colW = changed.maxOf { it.second.first.width } + 30
            val sheet = Bitmap.createBitmap(380 + colW * 2, 60 + rowH * changed.size, Bitmap.Config.ARGB_8888)
            Canvas(sheet).apply {
                drawColor(0xFFFBF8F2.toInt())
                drawText("改之前", 380f, 40f, words); drawText("S1", 380f + colW, 40f, words)
                changed.forEachIndexed { i, (n, p) ->
                    val y = 60f + i * rowH
                    drawText(n, 10f, y + rowH / 2, words)
                    drawBitmap(p.first, 380f, y, null); drawBitmap(p.second, 380f + colW, y, null)
                }
            }
            save(sheet, File(out2, "overview.png"))
        }
        assertEquals("重畫墨丸要先跟原本一樣：$bad", emptyList<String>(), bad)
    }
}
