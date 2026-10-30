package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-1 審核：WIDE 小工具的文字可讀性調查（2026-10-30）。
 * 量各版面、各尺寸哪些文字被截斷或省略，以及底部文字跟背景的對比；
 * 候選修正只在這個測試裡改 View（模擬版面檔的改動），正式版面沒有改。存到 app/build/screenshots/p7-wide。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WideReadabilityShotsTest {
    private val out = File("build/screenshots/p7-wide").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    private val days = listOf(
        LocalDate.of(2027, 10, 21) to "一般",
        LocalDate.of(2027, 2, 6) to "春節",
        LocalDate.of(2027, 9, 15) to "中秋",
        LocalDate.of(2027, 12, 25) to "聖誕（行憲紀念日）",
        LocalDate.of(2027, 11, 3) to "十一月",
    )

    /** 系統選 WIDE 的範圍：寬 ≥ 250、高 110～249（再高就是 TALL） */
    private val wideSizes = listOf(250 to 110, 300 to 130, 350 to 170, 400 to 200)

    private val texts = listOf(R.id.month_en to "英文月份", R.id.phrase to "宜", R.id.lunar to "農曆", R.id.ji to "忌")
    private val bottom = listOf(R.id.weekday to "星期", R.id.lunar to "農曆", R.id.ji to "忌")

    private fun widget(day: LocalDate, layout: PageLayout, wDp: Int, hDp: Int, candidate: Int = 0): View {
        MowanSettings.setStoryCard(context, true)
        AppClock.setFakeToday(context, day)
        StoryBook(context).jumpTo(14, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        val page = Almanac.page(day)
        val v = CalendarWidget.build(context, page, layout).apply(context, FrameLayout(context))
        if (candidate >= 1) candidateA(v)
        if (candidate >= 2) candidateB(v, page)
        val d = context.resources.displayMetrics.density
        v.measure(
            View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY),
        )
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return v
    }

    /** 候選 A（不改內容）：英文月份換到中文月份下面一行（完整顯示）；農曆、忌改用 ink（跟日期同一個深色） */
    private fun candidateA(v: View) {
        val month = v.findViewById<TextView>(R.id.month_en)
        (month.parent as LinearLayout).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        (month.layoutParams as LinearLayout.LayoutParams).apply { leftMargin = 0; gravity = Gravity.CENTER_HORIZONTAL }
        month.letterSpacing = 0.2f
        val ink = context.getColor(R.color.ink)
        for (id in intArrayOf(R.id.lunar, R.id.ji)) v.findViewById<TextView>(id).setTextColor(ink)
    }

    /** 候選 B（A＋WIDE 少放一點）：宜 1 項、農曆不加干支年、忌 2 項，最小尺寸不出現「…」 */
    private fun candidateB(v: View, page: DayPage) {
        v.findViewById<TextView>(R.id.phrase).text = CalendarWidget.phrase(page, 1)
        v.findViewById<TextView>(R.id.lunar).text = "農曆${page.lunarDate}"
        val ji = page.ji.filter { it !in setOf("餘事勿取", "諸事不宜", "無") }
        v.findViewById<TextView>(R.id.ji).text = if (ji.isEmpty()) "" else "忌 " + ji.take(2).joinToString("・")
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

    /** 截斷：有「…」，或文字比可用寬度寬（沒有設 ellipsize 的會被直接切掉） */
    private fun cut(tv: TextView): String? {
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty() || tv.width == 0) return null
        val l = tv.layout ?: return null
        if (l.lineCount > 0 && l.getEllipsisCount(0) > 0) return "省略「…」"
        val need = tv.paint.measureText(tv.text.toString())
        val avail = tv.width - tv.totalPaddingLeft - tv.totalPaddingRight
        return if (need > avail + 1) "切掉（需要 %.0f dp，只有 %.0f dp）".format(need / dp, avail / dp) else null
    }

    private val dp get() = context.resources.displayMetrics.density

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /** 文字顏色跟它背後背景的對比（WCAG）：背景是把這行字藏起來再畫一次；回傳（平均背景的對比, 最差的背景像素的對比） */
    private fun contrast(root: View, id: Int): Pair<Double, Double>? {
        val tv = root.findViewById<TextView>(id)
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty()) return null
        tv.visibility = View.INVISIBLE
        val bg = draw(root)
        tv.visibility = View.VISIBLE
        val r = rectIn(root, tv)
        val l = tv.layout
        // 只看真的有字的範圍
        val x1 = minOf(r.right, r.left + tv.totalPaddingLeft + l.getLineRight(0).toInt())
        val px = IntArray(maxOf(0, x1 - r.left) * r.height())
        if (px.isEmpty()) return null
        bg.getPixels(px, 0, x1 - r.left, r.left, r.top, x1 - r.left, r.height())
        val text = tv.currentTextColor
        var sr = 0L; var sg = 0L; var sb = 0L
        var worst = Double.MAX_VALUE
        for (p in px) { sr += Color.red(p); sg += Color.green(p); sb += Color.blue(p); worst = minOf(worst, ratio(text, p)) }
        val n = px.size
        return ratio(text, Color.rgb((sr / n).toInt(), (sg / n).toInt(), (sb / n).toInt())) to worst
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 30f }

    private fun sheet(name: String, title: String, cols: List<String>, rows: List<String>, cells: List<List<Bitmap>>) {
        val cw = cols.indices.map { c -> cells.maxOf { it[c].width } + 24 }
        val ch = cells.map { r -> r.maxOf { it.height } + 24 }
        val left = 260
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

    private fun f(p: Pair<Double, Double>?) = p?.let { "%.1f（最差 %.1f）".format(it.first, it.second) } ?: "—"

    @Test
    fun investigate() {
        val report = StringBuilder("# WIDE 文字可讀性調查（Robolectric，xxhdpi）\n\n")

        report.append("## 1. 各版面最小尺寸的截斷（現在的版面）\n\n| 版面 | 日子 | 截斷 |\n|---|---|---|\n")
        for (l in PageLayout.entries) for ((day, name) in days) {
            val v = widget(day, l, l.minWidthDp.toInt(), l.minHeightDp.toInt())
            val cuts = texts.mapNotNull { (id, label) -> cut(v.findViewById(id))?.let { "$label $it" } }
            report.append("| ${l.name} ${l.minWidthDp.toInt()}×${l.minHeightDp.toInt()} | $name | ${cuts.joinToString("；").ifEmpty { "沒有" }} |\n")
        }

        report.append("\n## 2. WIDE 各尺寸（現在的版面）\n\n| 尺寸（dp） | 日子 | 截斷 | 對比：星期 | 農曆 | 忌 |\n|---|---|---|---|---|---|\n")
        val sizeCells = days.map { (day, name) ->
            wideSizes.map { (w, h) ->
                val v = widget(day, PageLayout.WIDE, w, h)
                val cuts = texts.mapNotNull { (id, label) -> cut(v.findViewById(id))?.let { "$label $it" } }
                val c = bottom.map { (id, _) -> f(contrast(v, id)) }
                report.append("| $w×$h | $name | ${cuts.joinToString("；").ifEmpty { "沒有" }} | ${c.joinToString(" | ")} |\n")
                draw(v)
            }
        }
        sheet("1-wide-sizes", "WIDE 現在的版面：各尺寸（xxhdpi 實際像素）", wideSizes.map { "${it.first}×${it.second}dp" }, days.map { it.second }, sizeCells)

        report.append("\n## 3. 最小尺寸 250×110 的候選（只在測試裡改）\n\n")
        report.append("- 現在：沒有改。\n- 候選 A（不改內容）：英文月份換到中文月份下一行（字距 0.3 → 0.2）；農曆、忌的顏色 ink_soft → ink。\n")
        report.append("- 候選 B：A＋WIDE 少放一點：宜 1 項、農曆不加干支年、忌 2 項（日期資料不變，只是 WIDE 顯示的項目變少）。\n\n")
        report.append("| 日子 | 方案 | 截斷 | 對比：星期 | 農曆 | 忌 |\n|---|---|---|---|---|---|\n")
        val names = listOf("現在", "候選 A", "候選 B")
        val cells = days.map { (day, name) ->
            names.indices.map { k ->
                val v = widget(day, PageLayout.WIDE, 250, 110, k)
                val cuts = texts.mapNotNull { (id, label) -> cut(v.findViewById(id))?.let { "$label $it" } }
                val c = bottom.map { (id, _) -> f(contrast(v, id)) }
                report.append("| $name | ${names[k]} | ${cuts.joinToString("；").ifEmpty { "沒有" }} | ${c.joinToString(" | ")} |\n")
                draw(v)
            }
        }
        sheet("2-wide-candidates", "WIDE 250×110dp：現在／候選 A／候選 B（xxhdpi 實際像素）", names, days.map { it.second }, cells)
        // 放大兩倍看細節
        sheet("3-wide-candidates-x2", "同上，放大 2 倍", names, days.map { it.second },
            cells.map { r -> r.map { Bitmap.createScaledBitmap(it, it.width * 2, it.height * 2, false) } })

        report.append("\n對比是 WCAG 的對比值（一般文字建議 ≥ 4.5，大字 ≥ 3）；「最差」是背景最不利的那個像素。\n")
        File(out, "report.md").writeText(report.toString())
    }
}
