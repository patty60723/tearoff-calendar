package com.patty60723.tearoff

import android.content.Context
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
 * P7-7 補充候選（2026-11-04 審核要求；只出候選圖與量測，不改正式版面、程式、golden）：
 * 1. 英文月份 #746D64：其他小工具版面與 App 日曆頁的現況與候選對比。
 * 2. 8 月 deep 加深（#8E7040 → #84693D）：App 日曆頁與四種小工具裡用到 deep 的文字（中文月份、星期、日期數字）。
 * 3. 日曆頁天氣與「忌」：候選 C（留夠空間、頁面跟著加高，日期不縮）跟候選 A 比。
 * 4. TALL 小工具字體 200% 的重疊：兩種 RemoteViews 相容的排版（T1、T2）。
 * 候選都在測試裡改 View 組出來；T1、T2 用的都是 RemoteViews 支援的 LinearLayout／FrameLayout／TextView。
 * 圖與量測存到 app/build/screenshots/p7-7-supplement。Robolectric 繪圖，不是實機。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P77SupplementShotsTest {
    private val out = File("build/screenshots/p7-7-supplement").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val desk = 0xFFEDE8E0.toInt()
    private val normal = LocalDate.of(2027, 10, 21)
    private val spring = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { com.patty60723.tearoff.mowan.FestivalTheme.of(it) == com.patty60723.tearoff.mowan.Festival.SPRING }

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        RuntimeEnvironment.setFontScale(1f)
    }

    private val d get() = context.resources.displayMetrics.density

    private fun setup(qualifiers: String, fontScale: Float, day: LocalDate) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        RuntimeEnvironment.setFontScale(fontScale)
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); tear(day) }
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

    private fun inkRect(root: View, tv: TextView): Rect {
        val r = rectIn(root, tv)
        val l = tv.layout ?: return r
        var left = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        for (i in 0 until l.lineCount) { left = minOf(left, l.getLineLeft(i).toInt()); right = maxOf(right, l.getLineRight(i).toInt()) }
        return Rect(r.left + tv.totalPaddingLeft + left, r.top + tv.totalPaddingTop, r.left + tv.totalPaddingLeft + right, r.top + tv.totalPaddingTop + l.height)
    }

    /** 文字被「…」截斷或寫到 View 外面 */
    private fun cut(tv: TextView): Boolean {
        val l = tv.layout ?: return false
        if (l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0) return true
        val avail = tv.width - tv.totalPaddingLeft - tv.totalPaddingRight
        return (0 until l.lineCount).any { l.getLineWidth(it) > avail + 1 } || l.height > tv.height - tv.totalPaddingTop - tv.totalPaddingBottom + 1
    }

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

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

    private fun widget(day: LocalDate, layout: PageLayout, fontScale: Float = 1f, wDp: Float = layout.minWidthDp, hDp: Float = layout.minHeightDp): View {
        setup("w411dp-h891dp-xxhdpi", fontScale, day)
        return CalendarWidget.build(context, Almanac.page(day), layout, wideWidthDp = wDp).apply(context, FrameLayout(context)).also { measure(it, (wDp * d).toInt(), (hDp * d).toInt()) }
    }

    private fun measure(v: View, w: Int, h: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
    }

    private fun relayout(v: View) = measure(v, v.width, v.height)

    private fun appPage(qualifiers: String, fontScale: Float, day: LocalDate): MainActivity {
        setup(qualifiers, fontScale, day)
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 28f }

    private fun sheet(name: String, title: String, cols: List<String>, rows: List<Pair<String, List<Bitmap>>>, colW: Int = 360) {
        val scaled = rows.map { (n, bs) -> n to bs.map { Bitmap.createScaledBitmap(it, colW, maxOf(1, it.height * colW / it.width), true) } }
        val rowH = scaled.map { it.second.maxOf { b -> b.height } + 56 }
        val sheet = Bitmap.createBitmap(cols.size * (colW + 24) + 24, 104 + rowH.sum(), Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawText(title, 24f, 38f, words)
            cols.forEachIndexed { i, c -> drawText(c, 24f + i * (colW + 24), 84f, words) }
            var y = 104f
            scaled.forEachIndexed { r, (n, bs) ->
                drawText(n, 24f, y + 32f, words)
                bs.forEachIndexed { i, b -> drawBitmap(b, 24f + i * (colW + 24), y + 44f, null) }
                y += rowH[r]
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun f(x: Double?) = x?.let { "%.2f".format(it) } ?: "不顯示"

    // ---------- 1. 英文月份 ----------

    private val enCandidate = 0xFF746D64.toInt()

    @Test
    fun englishMonthEverywhere() {
        val report = StringBuilder("# 英文月份 #746D64：各版面的現況與候選（WCAG，最不利像素）\n\n")
        report.append("目標：4.5:1（AA）最低、4.8:1 設計目標。日子：2027-10-21（沒有節日；節日當天 TALL 與日曆頁不顯示英文月份）。\n\n")
        report.append("| 位置 | 顯示 | 現況 | 候選 #746D64 |\n|---|---|---|---|\n")
        for (layout in PageLayout.entries) {
            val v = widget(normal, layout)
            val en = v.findViewById<TextView>(R.id.month_en)
            val now = worstContrast(v, en)
            en.setTextColor(enCandidate)
            report.append("| 小工具 ${layout.name}（${layout.minWidthDp.toInt()}×${layout.minHeightDp.toInt()}dp） | ${if (en.visibility == View.VISIBLE) "是" else "否（版面設定 gone）"} | ${f(now)} | ${f(worstContrast(v, en))} |\n")
        }
        val a = appPage("w411dp-h891dp-xxhdpi", 1f, normal)
        val page = a.findViewById<View>(R.id.page)
        val en = page.findViewById<TextView>(R.id.month_en)
        val now = worstContrast(page, en)
        en.setTextColor(enCandidate)
        report.append("| App 日曆頁（411dp） | 是 | ${f(now)} | ${f(worstContrast(page, en))} |\n")
        val s = appPage("w411dp-h891dp-xxhdpi", 1f, spring).findViewById<View>(R.id.page).findViewById<TextView>(R.id.month_en)
        report.append("\n節日當天（春節）App 日曆頁的英文月份：${if (s.visibility == View.VISIBLE) "顯示" else "不顯示（讓位給節日名稱）"}。\n")
        File(out, "1-english-month.md").writeText(report.toString())
    }

    // ---------- 2. 8 月 deep ----------

    private val augDeep = Palettes.forMonth(8).deep
    private val augCandidate = 0xFF84693D.toInt()

    /** 把畫面上用 deep（或 WIDE 星期的混色）的文字換成候選色 */
    private fun applyAugCandidate(v: View) {
        val wideWeekday = Palettes.blend(Palettes.INK, augDeep, 0.4f)
        for (id in listOf(R.id.month, R.id.weekday, R.id.day)) {
            val tv = v.findViewById<TextView>(id) ?: continue
            when (tv.currentTextColor) {
                augDeep -> tv.setTextColor(augCandidate)
                wideWeekday -> tv.setTextColor(Palettes.blend(Palettes.INK, augCandidate, 0.4f))
            }
        }
    }

    @Test
    fun augustDeepImpact() {
        // 8 月沒有節日名稱、不是紅字的日子（日期數字才會用 deep）
        val day = generateSequence(LocalDate.of(2027, 8, 2)) { it.plusDays(1) }.first { Almanac.page(it).label == null && !Almanac.page(it).isRedDay }
        val red = generateSequence(LocalDate.of(2027, 8, 1)) { it.plusDays(1) }.first { Almanac.page(it).isRedDay }
        val report = StringBuilder("# 8 月 deep 加深（#8E7040 → #84693D）的影響（${day}，晴）\n\n")
        report.append("用到 deep 的文字：中文月份、星期（WIDE 的星期是混 40% ink）、日期數字（不是紅字日時）。日期數字是大字（≥ 24sp），目標 3:1；其他 4.5:1。\n\n")
        report.append("| 位置 | 中文月份 現況→候選 | 星期 現況→候選 | 日期數字 現況→候選 |\n|---|---|---|---|\n")
        val shots = mutableListOf<Pair<String, List<Bitmap>>>()
        fun row(name: String, v: View) {
            val ids = listOf(R.id.month, R.id.weekday, R.id.day)
            val before = ids.map { v.findViewById<TextView>(it)?.let { tv -> worstContrast(v, tv) } }
            val shotBefore = shoot(v)
            applyAugCandidate(v)
            val after = ids.map { v.findViewById<TextView>(it)?.let { tv -> worstContrast(v, tv) } }
            report.append("| $name | ${before.zip(after).joinToString(" | ") { (x, y) -> "${f(x)} → ${f(y)}" }} |\n")
            shots += name to listOf(shotBefore, shoot(v))
        }
        for (layout in PageLayout.entries) row("小工具 ${layout.name}", widget(day, layout))
        row("App 日曆頁", appPage("w411dp-h891dp-xxhdpi", 1f, day).findViewById(R.id.page))
        report.append("\n紅字日（${red}）的日期數字用紅色，不受影響。golden 沒有 8 月的日子：**0 張受影響**。\n")
        report.append("封面頁的年份（App 還沒撕過任何一頁時）也用 deep：8 月第一次打開 App 的封面會跟著變深（不在 golden）。\n")
        sheet("2-august-deep", "8 月 deep：現況（左）／候選 #84693D（右）", listOf("現況", "候選"), shots, colW = 420)
        File(out, "2-august-deep.md").writeText(report.toString())
    }

    // ---------- 3. 日曆頁：候選 C ----------

    data class Cfg(val name: String, val qualifiers: String, val fontScale: Float)

    private val cfgs = listOf(
        Cfg("411dp・100%", "w411dp-h891dp-xxhdpi", 1f),
        Cfg("411dp・200%", "w411dp-h891dp-xxhdpi", 2f),
        Cfg("320dp・100%", "w320dp-h640dp-xhdpi", 1f),
        Cfg("320dp・200%", "w320dp-h640dp-xhdpi", 2f),
    )

    private class Parts(val pad: TearPageView, val page: View, val sun: TextView, val column: LinearLayout, val spacer: View, val day: TextView, val mowan: View)

    private fun parts(a: MainActivity): Parts {
        val pad = a.findViewById<TearPageView>(R.id.page)
        val ji = pad.findViewById<TextView>(R.id.ji)
        val column = ji.parent as LinearLayout
        return Parts(pad, pad.getChildAt(0), pad.findViewById(R.id.sun), column, column.getChildAt(column.childCount - 1), pad.findViewById(R.id.day), pad.findViewById(R.id.mowan_still))
    }

    /** 左下三行需要多少空間、現在的空間差多少（不夠時 > 0） */
    private fun shortfall(p: Parts): Int {
        val lp = p.sun.layoutParams as ViewGroup.MarginLayoutParams
        return p.sun.height + lp.bottomMargin + (4 * d).toInt() - p.spacer.height
    }

    private fun relayoutActivity(a: MainActivity) {
        val content = (a.findViewById<View>(R.id.prev).parent as View).parent as View
        content.measure(View.MeasureSpec.makeMeasureSpec(content.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        content.layout(content.left, content.top, content.left + content.width, content.top + content.measuredHeight)
    }

    /** 候選 A（上一輪）：留夠空間，頁面大小不變（日期數字縮小） */
    private fun candidateA(a: MainActivity) {
        val p = parts(a)
        val s = shortfall(p)
        if (s > 0) { p.spacer.layoutParams = p.spacer.layoutParams.apply { height = p.spacer.height + s }; relayoutActivity(a) }
    }

    /** 候選 C：留夠空間，**頁面跟著加高同樣的高度**（日期數字的空間不變）；字體 100% 時不需要，所以完全不變 */
    private fun candidateC(a: MainActivity) {
        val p = parts(a)
        val s = shortfall(p)
        if (s > 0) {
            p.spacer.layoutParams = p.spacer.layoutParams.apply { height = p.spacer.height + s }
            p.pad.aspect = (p.pad.height + s).toFloat() / p.pad.width
            relayoutActivity(a)
        }
    }

    private fun pageState(p: Parts): List<String> {
        val sun = inkRect(p.page, p.sun)
        val hits = listOf(R.id.ji to "忌", R.id.lunar to "農曆", R.id.phrase to "宜", R.id.label to "節日").mapNotNull { (id, n) ->
            val tv = p.page.findViewById<TextView>(id)
            if (tv.visibility == View.VISIBLE && tv.text.isNotEmpty() && Rect.intersects(sun, inkRect(p.page, tv))) n else null
        } + (if (Rect.intersects(sun, rectIn(p.page, p.mowan))) listOf("墨丸") else emptyList())
        val ji = inkRect(p.page, p.page.findViewById(R.id.ji))
        val cuts = listOf(R.id.ji to "忌", R.id.lunar to "農曆", R.id.phrase to "宜", R.id.sun to "天氣").filter { (id, _) -> cut(p.page.findViewById(id)) }.map { it.second }
        return listOf(
            if (hits.isEmpty()) "不重疊" else "**重疊：${hits.joinToString("、")}**",
            "%.0fdp".format(p.day.textSize / d),
            "%.0fdp".format((sun.top - ji.bottom) / d),
            "%.0fdp".format(p.pad.height / d),
            if (cuts.isEmpty()) "完整" else "**截斷：${cuts.joinToString("、")}**",
        )
    }

    @Test
    fun pageCandidateC() {
        val report = StringBuilder("# 日曆頁的天氣與「忌」：候選 A 與候選 C\n\n")
        report.append("候選 A：留夠空間、頁面大小不變。候選 C：留夠空間、頁面加高同樣的高度。字體 100% 時兩者都不需要調整（畫面跟現況逐像素相同）。\n\n")
        report.append("「間距」＝左下三行的上緣到「忌」的下緣（負數＝重疊）。\n\n")
        report.append("| 日子 | 設定 | 方案 | 重疊 | 日期數字（字高） | 間距 | 頁面高 | 文字 |\n|---|---|---|---|---|---|---|---|\n")
        val rows = mutableListOf<Pair<String, List<Bitmap>>>()
        val failures = mutableListOf<String>()
        for ((day, dayName) in listOf(normal to "一般", spring to "春節")) for (cfg in cfgs) {
            val shots = mutableListOf<Bitmap>()
            var base: Bitmap? = null
            for ((i, name) in listOf("現況", "候選 A", "候選 C").withIndex()) {
                val a = appPage(cfg.qualifiers, cfg.fontScale, day)
                when (i) { 1 -> candidateA(a); 2 -> candidateC(a) }
                val p = parts(a)
                val st = pageState(p)
                report.append("| $dayName | ${cfg.name} | $name | ${st.joinToString(" | ")} |\n")
                val shot = shoot(p.pad)
                if (i == 0) base = shot
                if (i > 0 && cfg.fontScale == 1f && !shot.sameAs(base)) failures += "$dayName ${cfg.name} $name 在 100% 改了畫面"
                if (i > 0 && st[0] != "不重疊") failures += "$dayName ${cfg.name} $name 還是重疊：$st"
                shots += shot
            }
            if (cfg.fontScale == 2f) rows += "$dayName ${cfg.name}" to shots
        }
        sheet("3-page-a-vs-c", "日曆頁（字體 200%）：現況／候選 A（頁面不變）／候選 C（頁面加高）", listOf("現況", "候選 A", "候選 C"), rows)
        File(out, "3-page-a-vs-c.md").writeText(report.toString())
        assertTrue("候選 A／C：$failures", failures.isEmpty())
    }

    // ---------- 4. TALL 小工具 ----------

    /**
     * T1：左下三行搬進文字欄的最下面（原本固定 84dp 的空白改成「最少 84dp、可以長高」的直式 LinearLayout，三行靠左下），
     * 所有字體都用同一個版面。字變大時欄位自己長高，日期數字（weight=1、自動縮放）讓出空間。
     */
    private fun t1(v: View) {
        val sun = v.findViewById<TextView>(R.id.sun)
        val lp = sun.layoutParams as FrameLayout.LayoutParams
        (sun.parent as ViewGroup).removeView(sun)
        val column = v.findViewById<View>(R.id.ji).parent as LinearLayout
        val spacer = column.getChildAt(column.childCount - 1)
        val h = spacer.layoutParams.height
        column.removeView(spacer)
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM or Gravity.START; minimumHeight = h }
        box.addView(sun, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = lp.leftMargin; bottomMargin = lp.bottomMargin; topMargin = (2 * d).toInt() })
        column.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        relayout(v)
    }

    /**
     * T2：字體放大時（程式依 fontScale 選另一個版面，100% 照舊）：天氣與日出日落改放在「忌」的下面、置中、可以換行，
     * 不再疊在左下的山丘上；左下留給山丘與墨丸。
     */
    private fun t2(v: View) {
        val sun = v.findViewById<TextView>(R.id.sun)
        (sun.parent as ViewGroup).removeView(sun)
        sun.text = sun.text.toString().replace("\n", "　")
        sun.gravity = Gravity.CENTER_HORIZONTAL
        sun.maxLines = 4
        val column = v.findViewById<View>(R.id.ji).parent as LinearLayout
        val at = column.indexOfChild(v.findViewById(R.id.ji)) + 1
        column.addView(sun, at, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (2 * d).toInt(); leftMargin = (8 * d).toInt(); rightMargin = (8 * d).toInt() })
        relayout(v)
    }

    private fun tallState(v: View): List<String> {
        val sun = inkRect(v, v.findViewById(R.id.sun))
        val hits = listOf(R.id.ji to "忌", R.id.lunar to "農曆", R.id.phrase to "宜", R.id.label to "節日", R.id.day to "日期").mapNotNull { (id, n) ->
            val tv = v.findViewById<TextView>(id)
            if (tv.visibility == View.VISIBLE && tv.text.isNotEmpty() && Rect.intersects(sun, inkRect(v, tv))) n else null
        } + (if (Rect.intersects(sun, rectIn(v, v.findViewById(R.id.mowan_still)))) listOf("墨丸") else emptyList())
        val cuts = listOf(R.id.ji to "忌", R.id.lunar to "農曆", R.id.phrase to "宜", R.id.label to "節日", R.id.sun to "天氣", R.id.month to "月份", R.id.weekday to "星期").filter { (id, _) ->
            val tv = v.findViewById<TextView>(id); tv.visibility == View.VISIBLE && cut(tv)
        }.map { it.second }
        val day = v.findViewById<TextView>(R.id.day)
        return listOf(
            if (hits.isEmpty()) "不重疊" else "**重疊：${hits.joinToString("、")}**",
            "%.0fdp".format(day.textSize / d),
            if (cuts.isEmpty()) "完整" else "**截斷：${cuts.joinToString("、")}**",
        )
    }

    @Test
    fun tallWidgetCandidates() {
        val report = StringBuilder("# TALL 小工具（最小 150×250dp）：字體 200% 的重疊\n\n")
        report.append("T1：三行搬進文字欄最下面（所有字體同一個版面）。T2：字體放大時換一個版面，三行改成「忌」下面置中一段（100% 照舊）。\n")
        report.append("兩者都只用 RemoteViews 支援的 LinearLayout／TextView；不隱藏任何資訊。\n\n")
        report.append("| 日子 | 字體 | 方案 | 重疊 | 日期數字（字高） | 文字 |\n|---|---|---|---|---|---|\n")
        val rows = mutableListOf<Pair<String, List<Bitmap>>>()
        for ((day, dayName) in listOf(normal to "一般", spring to "春節")) for (scale in listOf(1f, 2f)) {
            val shots = mutableListOf<Bitmap>()
            var base: Bitmap? = null
            for ((i, name) in listOf("現況", "T1", "T2").withIndex()) {
                val v = widget(day, PageLayout.TALL, scale)
                // T2 只在字體放大時換版面：100% 時就是現況
                when (i) { 1 -> t1(v); 2 -> if (scale > 1.3f) t2(v) }
                val st = tallState(v)
                report.append("| $dayName | ${(scale * 100).toInt()}% | $name | ${st.joinToString(" | ")} |\n")
                val shot = shoot(v)
                if (i == 0) base = shot
                if (i == 1 && scale == 1f) report.append("|  |  | T1 在 100% 跟現況 ${if (shot.sameAs(base)) "逐像素相同" else "**不同**（會改 golden）"} |  |  |  |\n")
                shots += shot
            }
            rows += "$dayName ${(scale * 100).toInt()}%" to shots
        }
        sheet("4-tall-widget", "TALL 小工具 150×250dp：現況／T1（三行進文字欄）／T2（放大時改版面）", listOf("現況", "T1", "T2"), rows, colW = 300)
        File(out, "4-tall-widget.md").writeText(report.toString())
    }
}
