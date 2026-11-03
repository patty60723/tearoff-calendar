package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-7 準備（只出候選圖，不改正式版面、不改 golden）：
 * 1. D-54：「回到最上面那頁」在字體 200% 時變成直排。
 * 2. D-54：日曆頁的天氣與日出日落（左下）在字體 200% 時壓到「忌」。
 * 3. WIDE 小工具的英文月份（3.52:1）、8 月的中文月份（4.36:1）對比不足。
 * 候選都在測試裡把畫面組出來（改的是測試裡的 View，不是正式的版面檔或程式），411dp／320dp × 字體 100%／200%。
 * 圖與量測存到 app/build/screenshots/p7-7-candidates。Robolectric 繪圖，不是實機。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P77CandidatesShotsTest {
    private val out = File("build/screenshots/p7-7-candidates").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val desk = 0xFFEDE8E0.toInt()
    private val today = LocalDate.of(2027, 10, 21)

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        RuntimeEnvironment.setFontScale(1f)
    }

    data class Config(val name: String, val qualifiers: String, val fontScale: Float)

    private val configs = listOf(
        Config("411dp・100%", "w411dp-h891dp-xxhdpi", 1f),
        Config("411dp・200%", "w411dp-h891dp-xxhdpi", 2f),
        Config("320dp・100%", "w320dp-h640dp-xhdpi", 1f),
        Config("320dp・200%", "w320dp-h640dp-xhdpi", 2f),
    )

    private fun open(cfg: Config, day: LocalDate = today): MainActivity {
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        RuntimeEnvironment.setFontScale(cfg.fontScale)
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); tear(day) }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val d get() = context.resources.displayMetrics.density

    private fun relayout(v: View) {
        val w = v.width
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(v.left, v.top, v.left + w, v.top + v.measuredHeight)
    }

    private fun shoot(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(desk); v.draw(this) } }

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

    /** 文字實際寫到的範圍（不是整個 View） */
    private fun inkRect(root: View, tv: TextView): Rect {
        val r = rectIn(root, tv)
        val l = tv.layout ?: return r
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (i in 0 until l.lineCount) {
            left = minOf(left, l.getLineLeft(i).toInt()); right = maxOf(right, l.getLineRight(i).toInt())
        }
        return Rect(r.left + tv.totalPaddingLeft + left, r.top + tv.totalPaddingTop, r.left + tv.totalPaddingLeft + right, r.top + tv.totalPaddingTop + l.height)
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 30f }

    /** 每列一種設定，每欄一個方案（縮成同樣寬） */
    private fun sheet(name: String, title: String, cols: List<String>, rows: List<Pair<String, List<Bitmap>>>, colW: Int = 420) {
        val scaled = rows.map { (n, bs) -> n to bs.map { Bitmap.createScaledBitmap(it, colW, maxOf(1, it.height * colW / it.width), true) } }
        val rowH = scaled.map { it.second.maxOf { b -> b.height } + 60 }
        val sheet = Bitmap.createBitmap(cols.size * (colW + 24) + 24, 110 + rowH.sum(), Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawText(title, 24f, 40f, words)
            cols.forEachIndexed { i, c -> drawText(c, 24f + i * (colW + 24), 90f, words) }
            var y = 110f
            scaled.forEachIndexed { r, (n, bs) ->
                drawText(n, 24f, y + 34f, words)
                bs.forEachIndexed { i, b -> drawBitmap(b, 24f + i * (colW + 24), y + 48f, null) }
                y += rowH[r]
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------- 1. 「回到最上面那頁」 ----------

    /** 頁面最上面到城市入口下緣（提示文字、導覽列、「回到最上面那頁」、城市入口） */
    private fun navShot(a: MainActivity): Bitmap {
        val content = (a.findViewById<View>(R.id.prev).parent as View).parent as View
        val full = shoot(content)
        val bottom = minOf(full.height, rectIn(content, a.findViewById(R.id.city)).bottom + (12 * d).toInt())
        return Bitmap.createBitmap(full, 0, 0, full.width, bottom)
    }

    /** 候選 A：「回到最上面那頁」移到導覽列下面自己一列（置中、整列可以點）；看最上面那頁時整列不佔空間（GONE） */
    private fun candidateOwnRow(a: MainActivity) {
        val today = a.findViewById<Button>(R.id.today)
        val nav = today.parent as LinearLayout
        val content = nav.parent as LinearLayout
        val at = nav.indexOfChild(today)
        nav.removeView(today)
        nav.addView(Space(context), at, LinearLayout.LayoutParams(0, 1, 1f))
        content.addView(today, content.indexOfChild(nav) + 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        relayout(content)
    }

    /** 候選 B：同一列、文字縮短成「回最上頁」（文案要另外決定） */
    private fun candidateShort(a: MainActivity) {
        a.findViewById<Button>(R.id.today).text = "回最上頁"
        relayout((a.findViewById<View>(R.id.prev).parent as View).parent as View)
    }

    private fun todayState(a: MainActivity): String {
        val b = a.findViewById<Button>(R.id.today)
        val l = b.layout
        val lines = l?.lineCount ?: 0
        val maxLine = (0 until lines).maxOfOrNull { l!!.getLineEnd(it) - l.getLineStart(it) } ?: 0
        return "%d 行（一行最多 %d 字）、%.0f×%.0fdp".format(lines, maxLine, b.width / d, b.height / d)
    }

    @Test
    fun backToTopButton() {
        val report = StringBuilder("# D-54：「回到最上面那頁」（看昨天那頁時）\n\n| 設定 | 現況 | 候選 A：自己一列 | 候選 B：縮短文字 |\n|---|---|---|---|\n")
        val rows = configs.map { cfg ->
            val shots = mutableListOf<Bitmap>()
            val states = mutableListOf<String>()
            for (variant in 0..2) {
                val a = open(cfg)
                a.findViewById<Button>(R.id.prev).performClick() // 看昨天那頁：「回到最上面那頁」出現
                assertTrue(a.findViewById<View>(R.id.today).visibility == View.VISIBLE)
                when (variant) { 1 -> candidateOwnRow(a); 2 -> candidateShort(a) }
                states += todayState(a)
                val b = a.findViewById<Button>(R.id.today)
                if (variant == 1) assertTrue("${cfg.name} 候選 A 要一行", b.layout.lineCount == 1)
                if (variant > 0) assertTrue("${cfg.name} 觸控範圍 ≥ 48dp", b.height / d >= 47.5f)
                shots += navShot(a)
            }
            report.append("| ${cfg.name} | ${states.joinToString(" | ")} |\n")
            cfg.name to shots
        }
        sheet("1-back-to-top", "D-54：「回到最上面那頁」（看昨天那頁時；導覽列＋城市入口）", listOf("現況", "候選 A：自己一列", "候選 B：縮短文字"), rows)
        File(out, "1-back-to-top.md").writeText(report.toString())
    }

    // ---------- 2. 天氣／日出日落 壓到「忌」 ----------

    private data class PageParts(val page: View, val sun: TextView, val ji: TextView, val lunar: TextView, val spacer: View, val day: TextView)

    private fun pageParts(a: MainActivity): PageParts {
        val page = a.findViewById<ViewGroup>(R.id.page)
        val ji = page.findViewById<TextView>(R.id.ji)
        val column = ji.parent as LinearLayout
        return PageParts(page, page.findViewById(R.id.sun), ji, page.findViewById(R.id.lunar), column.getChildAt(column.childCount - 1), page.findViewById(R.id.day))
    }

    /**
     * 候選 A：左下的天氣＋日出日落佔多高，下面留給山丘的空間就至少留多高（不夠才加大，日期數字自動縮小一點）。
     * 只改 App 的日曆頁（MainActivity.pageView），小工具不動；字體 100% 時不會重疊，所以畫面完全不變。
     */
    private fun candidateReserve(a: MainActivity) {
        val p = pageParts(a)
        val lp = p.sun.layoutParams as ViewGroup.MarginLayoutParams
        val need = p.sun.height + lp.bottomMargin + (4 * d).toInt()
        if (need > p.spacer.height) {
            p.spacer.layoutParams = p.spacer.layoutParams.apply { height = need }
            val pw = p.page.width
            val ph = p.page.height
            p.page.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
            p.page.layout(p.page.left, p.page.top, p.page.left + pw, p.page.top + ph)
        }
    }

    /** 候選 B：天氣跟日出併成一行（「晴 25°・日出 06:00」／「日落 17:14」），三行變兩行 */
    private fun candidateTwoLines(a: MainActivity) {
        val p = pageParts(a)
        val lines = p.sun.text.toString().lines()
        if (lines.size == 3) p.sun.text = "${lines[0]}・${lines[1]}\n${lines[2]}"
        val pw = p.page.width
        val ph = p.page.height
        p.page.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        p.page.layout(p.page.left, p.page.top, p.page.left + pw, p.page.top + ph)
    }

    private fun overlapState(a: MainActivity): String {
        val p = pageParts(a)
        val sun = inkRect(p.page, p.sun)
        val hits = listOf(p.ji to "忌", p.lunar to "農曆").filter { (tv, _) -> Rect.intersects(sun, inkRect(p.page, tv)) }.map { it.second }
        val gap = inkRect(p.page, p.ji).bottom - sun.top
        return (if (hits.isEmpty()) "不重疊" else "**重疊：${hits.joinToString("、")}**") +
            "（日期數字 %.0fdp 高）".format(p.day.textSize / d) +
            if (hits.isEmpty()) "" else "，重疊 %.0fdp".format(gap / d)
    }

    @Test
    fun weatherOverlapsJi() {
        val report = StringBuilder("# D-54：日曆頁左下的天氣＋日出日落 與「忌」\n\n| 設定 | 現況 | 候選 A：留夠空間（只改 App 日曆頁） | 候選 B：天氣併進日出那行 |\n|---|---|---|---|\n")
        val rows = configs.map { cfg ->
            val shots = mutableListOf<Bitmap>()
            val states = mutableListOf<String>()
            var base: Bitmap? = null
            for (variant in 0..2) {
                val a = open(cfg)
                when (variant) { 1 -> candidateReserve(a); 2 -> candidateTwoLines(a) }
                states += overlapState(a)
                val shot = shoot(a.findViewById(R.id.page))
                if (variant == 0) base = shot
                // 候選 A 在不重疊的設定（字體 100%）必須跟現況逐像素相同（golden 的日曆頁不受影響）
                if (variant == 1 && cfg.fontScale == 1f) assertTrue("${cfg.name} 候選 A 在 100% 不改畫面", shot.sameAs(base))
                if (variant == 1) assertTrue("${cfg.name} 候選 A 不重疊：${states.last()}", !states.last().contains("重疊："))
                shots += shot
            }
            report.append("| ${cfg.name} | ${states.joinToString(" | ")} |\n")
            cfg.name to shots
        }
        sheet("2-weather-ji", "D-54：日曆頁左下的天氣＋日出日落 與「忌」（今天那頁、晴）", listOf("現況", "候選 A：留夠空間", "候選 B：兩行"), rows)
        // 小工具 TALL 在字體 200% 會不會也重疊（只記錄；小工具的版面由 launcher 決定大小，這裡用最小尺寸）
        report.append("\n## 小工具（只記錄）\n\n| 版面 | 字體 | 結果 |\n|---|---|---|\n")
        for (scale in listOf(1f, 2f)) {
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
            RuntimeEnvironment.setFontScale(scale)
            AppClock.setFakeToday(context, today)
            WeatherService.setFakeMood(context, Mood.SUNNY)
            for (layout in listOf(PageLayout.TALL, PageLayout.MEDIUM)) {
                val v = CalendarWidget.build(context, Almanac.page(today), layout).apply(context, FrameLayout(context)).apply {
                    measure(View.MeasureSpec.makeMeasureSpec((layout.minWidthDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((layout.minHeightDp * d).toInt(), View.MeasureSpec.EXACTLY))
                    layout(0, 0, measuredWidth, measuredHeight)
                }
                val sun = inkRect(v, v.findViewById(R.id.sun))
                val hit = listOf(R.id.ji to "忌", R.id.lunar to "農曆", R.id.phrase to "宜").filter { (id, _) ->
                    val tv = v.findViewById<TextView>(id)
                    tv.visibility == View.VISIBLE && tv.text.isNotEmpty() && Rect.intersects(sun, inkRect(v, tv))
                }.map { it.second }
                report.append("| ${layout.name}（${layout.minWidthDp.toInt()}×${layout.minHeightDp.toInt()}dp） | ${(scale * 100).toInt()}% | ${if (hit.isEmpty()) "不重疊" else "**重疊：${hit.joinToString("、")}**"} |\n")
            }
        }
        File(out, "2-weather-ji.md").writeText(report.toString())
    }

    // ---------- 3. WIDE 的月份對比 ----------

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /** 文字顏色跟背後最不利像素的對比（同 WideLayoutTest：把字藏起來再畫一次） */
    private fun worstContrast(root: View, tv: TextView): Double? {
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty()) return null
        tv.visibility = View.INVISIBLE
        val bg = shoot(root)
        tv.visibility = View.VISIBLE
        val r = inkRect(root, tv).apply { intersect(0, 0, bg.width, bg.height) }
        if (r.width() <= 0 || r.height() <= 0) return null
        val px = IntArray(r.width() * r.height())
        bg.getPixels(px, 0, r.width(), r.left, r.top, r.width(), r.height())
        return px.minOf { ratio(tv.currentTextColor, it) }
    }

    private fun wide(day: LocalDate, layout: PageLayout = PageLayout.WIDE, wDp: Float = 300f, hDp: Float = 130f): View {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        return CalendarWidget.build(context, Almanac.page(day), layout, wideWidthDp = wDp).apply(context, FrameLayout(context)).apply {
            measure(View.MeasureSpec.makeMeasureSpec((wDp * d).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((hDp * d).toInt(), View.MeasureSpec.EXACTLY))
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    /** 候選：英文月份 #746D64（ink_soft 摻 25% ink，4.82:1）；8 月中文月份 #84693D（沙金加深，4.87:1）；或 WIDE 的中文月份跟星期一樣混 40% ink */
    private val enCandidate = 0xFF746D64.toInt()
    private val augDeepCandidate = 0xFF84693D.toInt()

    @Test
    fun wideMonthContrast() {
        val report = StringBuilder("# WIDE 月份的對比（WCAG，最不利像素；300×130dp）\n\n目標：一般文字 4.5:1（AA）；建議設計值 ≥ 4.8（留給反鋸齒與不同螢幕的餘裕）。\n\n")
        report.append("| 日子 | 英文月份 現況 | 英文月份 候選 #746D64 | 中文月份 現況 | 中文月份 候選 1：8 月沙金加深 #84693D | 中文月份 候選 2：WIDE 混 40% ink |\n|---|---|---|---|---|---|\n")
        val days = (1..12).map { LocalDate.of(2027, it, 10) }
        val shots = mutableListOf<Pair<String, List<Bitmap>>>()
        for (day in days) {
            val v = wide(day)
            val en = v.findViewById<TextView>(R.id.month_en)
            val zh = v.findViewById<TextView>(R.id.month)
            val enNow = worstContrast(v, en)
            val zhNow = worstContrast(v, zh)
            val now = shoot(v)
            en.setTextColor(enCandidate)
            val enNew = worstContrast(v, en)
            val deep = Palettes.forMonth(day.monthValue).deep
            if (day.monthValue == 8) zh.setTextColor(augDeepCandidate)
            val zh1 = worstContrast(v, zh)
            val cand1 = shoot(v)
            zh.setTextColor(Palettes.blend(Palettes.INK, deep, 0.4f))
            val zh2 = worstContrast(v, zh)
            val cand2 = shoot(v)
            report.append("| ${day.monthValue} 月 | %.2f | %.2f | %.2f | %.2f | %.2f |\n".format(enNow, enNew, zhNow, zh1, zh2))
            if (day.monthValue in listOf(8, 10)) shots += "${day.monthValue} 月" to listOf(now, cand1, cand2)
        }
        // 其他版面的英文月份也是 ink_soft（只記錄；範圍是否擴大由審核決定）
        report.append("\n## 其他版面的英文月份（只記錄，現況）\n\n| 版面 | 對比 |\n|---|---|\n")
        for (layout in listOf(PageLayout.COMPACT, PageLayout.MEDIUM, PageLayout.TALL)) {
            val v = wide(LocalDate.of(2027, 10, 10), layout, layout.minWidthDp, layout.minHeightDp)
            val en = v.findViewById<TextView>(R.id.month_en)
            report.append("| ${layout.name} | ${worstContrast(v, en)?.let { "%.2f".format(it) } ?: "不顯示"} |\n")
        }
        sheet("3-wide-month", "WIDE 月份對比：現況／候選 1（英文 #746D64、8 月中文 #84693D）／候選 2（英文 #746D64、中文混 40% ink）", listOf("現況", "候選 1", "候選 2"), shots, colW = 600)
        File(out, "3-wide-month.md").writeText(report.toString())
    }
}
