package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-1：WIDE 小工具的文字（2026-10-30 審核：候選 B，依實際空間決定；文字對比 4.5:1）。
 * 用正式的 [CalendarWidget.build] 畫出來量：截斷（「…」或被切掉）、內容多寡、文字跟背後最不利像素的對比。
 * 報告與圖存到 app/build/screenshots/p7-wide（Robolectric 繪圖，不代表實機）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WideLayoutTest {
    private val out = File("build/screenshots/p7-wide").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()
    private val dp get() = context.resources.displayMetrics.density

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    /** 審核指定的四種大小（系統選 WIDE 的範圍：寬 ≥ 250、高 110～249） */
    private val sizes = listOf(250 to 110, 300 to 130, 350 to 170, 400 to 200)
    private val year = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == 2027 }.toList()

    private val texts = listOf(
        R.id.month to "中文月份", R.id.month_en to "英文月份", R.id.day to "日期", R.id.weekday to "星期",
        R.id.label to "節日", R.id.phrase to "宜", R.id.lunar to "農曆", R.id.ji to "忌",
    )
    /** 審核要求 4.5:1 的文字 */
    private val contrastTargets = listOf(R.id.weekday to "星期", R.id.phrase to "宜", R.id.lunar to "農曆", R.id.ji to "忌")

    /** [mowan] false 時不畫角色（量截斷用，快很多；文字版面一樣） */
    private fun widget(day: LocalDate, w: Int, h: Int, mowan: Boolean = false): View {
        if (mowan) {
            MowanSettings.setStoryCard(context, true)
            AppClock.setFakeToday(context, day)
            StoryBook(context).jumpTo(14, day)
            WeatherService.setFakeMood(context, Mood.SUNNY)
        }
        val layout = PageLayout.WIDE
        val views = CalendarWidget.build(context, Almanac.page(day), layout, if (mowan) layout.stickmanDp else 0f, wideWidthDp = w.toFloat())
        return views.apply(context, FrameLayout(context)).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec((w * dp).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((h * dp).toInt(), View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun draw(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(paper); v.draw(this) } }

    private fun rectIn(root: View, v: View): Rect {
        var x = 0
        var y = 0
        var cur: View = v
        while (cur !== root) {
            x += cur.left - cur.scrollX
            y += cur.top - cur.scrollY
            cur = cur.parent as View
        }
        return Rect(x, y, x + v.width, y + v.height)
    }

    /** 截斷：有「…」，或文字比可用寬度寬（沒有設 ellipsize 的會被直接切掉）；沒有截斷是 null */
    private fun cut(tv: TextView): String? {
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty() || tv.width == 0) return null
        val l = tv.layout ?: return null
        if (l.lineCount > 0 && l.getEllipsisCount(0) > 0) return "「…」"
        val need = tv.paint.measureText(tv.text.toString())
        val avail = tv.width - tv.totalPaddingLeft - tv.totalPaddingRight
        return if (need > avail + 1) "切掉（需要 %.0f dp，只有 %.0f dp）".format(need / dp, avail / dp) else null
    }

    private fun cuts(v: View) = texts.mapNotNull { (id, name) -> cut(v.findViewById(id))?.let { "$name$it" } }

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /**
     * 文字顏色跟它背後背景的對比（WCAG）。背景是把這行字藏起來再畫一次，看這行字實際寫到的範圍（行高 × 字寬）裡每個像素；
     * 回傳（平均背景的對比, 最不利像素的對比）。
     */
    private fun contrast(root: View, id: Int): Pair<Double, Double>? {
        val tv = root.findViewById<TextView>(id)
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty()) return null
        tv.visibility = View.INVISIBLE
        val bg = draw(root)
        tv.visibility = View.VISIBLE
        val r = rectIn(root, tv)
        val l = tv.layout
        val x0 = r.left + tv.totalPaddingLeft + l.getLineLeft(0).toInt()
        val x1 = minOf(r.right, r.left + tv.totalPaddingLeft + l.getLineRight(0).toInt())
        val w = x1 - x0
        if (w <= 0) return null
        val px = IntArray(w * r.height())
        bg.getPixels(px, 0, w, x0, r.top, w, r.height())
        val text = tv.currentTextColor
        var sr = 0L; var sg = 0L; var sb = 0L
        var worst = Double.MAX_VALUE
        for (p in px) { sr += Color.red(p); sg += Color.green(p); sb += Color.blue(p); worst = minOf(worst, ratio(text, p)) }
        val n = px.size
        return ratio(text, Color.rgb((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())) to worst
    }

    private fun f(p: Pair<Double, Double>?) = p?.let { "%.1f（最差 %.1f）".format(it.first, it.second) } ?: "—"

    private fun texts(v: View) = listOf(R.id.phrase, R.id.lunar, R.id.ji).map { v.findViewById<TextView>(it).text.toString() }

    /** 選出最難放的日子：宜、忌、農曆、節日名稱最長，加上長英文月份與三個節日 */
    private val samples: List<Pair<LocalDate, String>> by lazy {
        val pages = year.map(Almanac::page)
        fun longest(sel: (DayPage) -> String) = pages.maxBy { sel(it).length }.date
        val yi = { p: DayPage -> p.yi.filter { it !in WideText.FILLER }.take(2).joinToString("・") }
        val ji = { p: DayPage -> p.ji.filter { it !in WideText.FILLER }.take(3).joinToString("・") }
        listOf(
            LocalDate.of(2027, 10, 21) to "一般",
            LocalDate.of(2027, 2, 6) to "春節",
            LocalDate.of(2027, 9, 15) to "中秋（SEPTEMBER）",
            LocalDate.of(2027, 12, 25) to "聖誕（行憲紀念日）",
            longest(yi) to "宜最長",
            longest(ji) to "忌最長",
            longest { "${it.lunarDate}・${it.ganZhiYear}" } to "農曆最長",
            longest { it.label.orEmpty() } to "節日名稱最長",
        )
    }

    /** 2027 每一天 × 四種大小：沒有任何文字被截斷；放不下完整內容的日子列出來（400 寬時只有宜的單項很長的日子） */
    @Test
    fun nothingIsCutAndFullContentReturnsWhenThereIsRoom() {
        val problems = mutableListOf<String>()
        val notFull = sizes.associate { it.first to 0 }.toMutableMap()
        val smaller = sizes.associate { it.first to 0 }.toMutableMap()
        val notFullAt400 = mutableListOf<String>()
        for (day in year) for ((w, h) in sizes) {
            val v = widget(day, w, h)
            cuts(v).takeIf { it.isNotEmpty() }?.let { problems += "$day $w×$h：${it.joinToString("；")}" }
            val page = Almanac.page(day)
            val full = listOf(CalendarWidget.phrase(page, PageLayout.WIDE.yiCount), "農曆${page.lunarDate}・${page.ganZhiYear}",
                page.ji.filter { it !in WideText.FILLER }.take(3).let { if (it.isEmpty()) "" else "忌 " + it.joinToString("・") })
            if (texts(v) != full) {
                notFull[w] = notFull.getValue(w) + 1
                if (w == 400) notFullAt400 += "$day：${texts(v).joinToString("｜")}（完整：${full.joinToString("｜")}）"
                // 400 寬時精簡的只能是宜（兩項太長），農曆、忌一定完整
                if (w == 400) { assertEquals("$day 400 農曆", full[1], texts(v)[1]); assertEquals("$day 400 忌", full[2], texts(v)[2]) }
            }
            if (v.findViewById<TextView>(R.id.phrase).textSize < 15 * context.resources.displayMetrics.scaledDensity - 0.5f) smaller[w] = smaller.getValue(w) + 1
        }
        val report = StringBuilder("# WIDE 文字（正式版面，Robolectric xxhdpi，字型縮放 1.0）\n\n## 1. 截斷：2027 年每一天 × 四種大小\n\n")
        report.append("檢查中文月份、英文月份、日期、星期、節日、宜、農曆、忌。")
        report.append(if (problems.isEmpty()) "**${year.size * sizes.size} 次全部沒有截斷。**\n" else "**有 ${problems.size} 次截斷：**\n\n" + problems.joinToString("\n") { "- $it" } + "\n")
        report.append("\n## 2. 精簡了幾天（內容跟原本完整的不一樣）\n\n| 寬度 | 精簡的天數（共 ${year.size} 天） | 宜的字縮小的天數 |\n|---|---|---|\n")
        for ((w, _) in sizes) report.append("| $w dp | ${notFull[w]} | ${smaller[w]} |\n")
        report.append("\n400 寬時沒有完整顯示的日子：\n\n" + notFullAt400.joinToString("\n") { "- $it" }.ifEmpty { "（沒有）" } + "\n")
        File(out, "1-cut.md").writeText(report.toString())
        assertEquals("截斷：$problems", emptyList<String>(), problems)
    }

    /** 變寬時項目只增加、不減少（沒有門檻造成的跳動） */
    @Test
    fun widerNeverShowsLess() {
        for (day in year) {
            val page = Almanac.page(day)
            var last = Triple(0, 0, 0)
            var lastSp = 0f
            for (w in 250..460) {
                val t = WideText.of(page, w.toFloat(), 1f, PageLayout.WIDE.yiCount)
                val now = Triple(t.phrase.split("・").size, t.lunar.length, t.ji.split("・").size)
                assertTrue("$day $w dp：$last → $now", now.first >= last.first && now.second >= last.second && now.third >= last.third)
                // 項目數不變時，宜的字只會變大（項目變多那一下才可能變小，但項目變多前提是 15sp 放得下）
                if (now.first == last.first) assertTrue("$day $w dp：宜的字 $lastSp → ${t.phraseSp}", t.phraseSp >= lastSp)
                last = now
                lastSp = t.phraseSp
            }
        }
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 30f }

    private fun sheet(name: String, title: String, cols: List<String>, rows: List<String>, cells: List<List<Bitmap>>) {
        val cw = cols.indices.map { c -> cells.maxOf { it[c].width } + 24 }
        val ch = cells.map { r -> r.maxOf { it.height } + 24 }
        val left = 280
        val top = 110
        val b = Bitmap.createBitmap(left + cw.sum(), top + ch.sum(), Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawText(title, 12f, 40f, words)
            var x = left
            cols.forEachIndexed { i, t -> drawText(t, x.toFloat(), 90f, words); x += cw[i] }
            var y = top
            rows.forEachIndexed { r, t ->
                drawText(t, 12f, (y + ch[r] / 2).toFloat(), words)
                x = left
                cells[r].forEachIndexed { c, img -> drawBitmap(img, x.toFloat(), y.toFloat(), null); x += cw[c] }
                y += ch[r]
            }
        }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 每個月份色（每月 10 日）＋三個節日 × 四種大小的對比（有墨丸，跟實際一樣）；圖：最難放的日子 × 四種大小 */
    @Test
    fun contrastAndSheets() {
        val days = (1..12).map { LocalDate.of(2027, it, 10) to "${it} 月" } +
            listOf(LocalDate.of(2027, 2, 6) to "春節", LocalDate.of(2027, 9, 15) to "中秋", LocalDate.of(2027, 12, 25) to "聖誕")
        val report = StringBuilder("# WIDE 文字對比（正式版面，Robolectric xxhdpi）\n\n")
        report.append("WCAG 對比，一般文字目標 ≥ 4.5；「最差」是文字範圍（行高 × 字寬）裡最不利的背景像素。\n\n")
        report.append("| 日子 | 大小 | " + contrastTargets.joinToString(" | ") { it.second } + " |\n|---|---|" + "---|".repeat(contrastTargets.size) + "\n")
        val below = mutableListOf<String>()
        for ((day, name) in days) for ((w, h) in sizes) {
            val v = widget(day, w, h, mowan = true)
            val c = contrastTargets.map { (id, label) ->
                contrast(v, id).also { r -> if (r != null && r.second < 4.5) below += "$name $w×$h $label 最差 %.2f".format(r.second) }
            }
            report.append("| $name | $w×$h | ${c.joinToString(" | ") { f(it) }} |\n")
        }
        report.append("\n" + if (below.isEmpty()) "**全部 ≥ 4.5（最不利像素）。**\n" else "**未達 4.5：${below.size} 項**\n\n" + below.joinToString("\n") { "- $it" } + "\n")
        File(out, "2-contrast.md").writeText(report.toString())

        val cells = samples.map { (day, _) -> sizes.map { (w, h) -> draw(widget(day, w, h, mowan = true)) } }
        sheet("1-wide-sizes", "WIDE 正式版面：各大小（xxhdpi 實際像素）", sizes.map { "${it.first}×${it.second}dp" }, samples.map { "${it.second} ${it.first}" }, cells)
        assertEquals("對比未達 4.5：$below", emptyList<String>(), below)
    }
}
