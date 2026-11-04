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
}
