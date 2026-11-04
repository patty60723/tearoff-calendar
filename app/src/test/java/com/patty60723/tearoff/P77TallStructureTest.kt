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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-7（2026-11-04 審核）：最小 TALL 小工具（150×250dp）在字體 200% 時的版面結構研究。只出量測與候選，不改正式版面。
 * - 垂直空間分配：每個區塊在 100%／200% 的實際高度，哪些固定、哪些可以重新分配。
 * - T6：結構重新配置（不以降低字體倍率為主要解法）；T7：有限的字體倍率（找出能完全通過的最大倍率）。
 * - 提高最小高度的替代方案：找出現在的結構與 T6 各自要多高才能在 200% 完全通過。
 * - 判斷「有沒有被切掉」用**字形實際可見的範圍**（getTextBounds 算出的字形 ∩ 自己的元件 ∩ 小工具外框），不用 TextView 邊界。
 * - WIDE 8 月日期數字對比 1.05 的來源。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P77TallStructureTest {
    private val out = File("build/screenshots/p7-7-tall").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val desk = 0xFFEDE8E0.toInt()
    private val normal = LocalDate.of(2027, 10, 21)
    private val spring = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == Festival.SPRING }
    private val d get() = context.resources.displayMetrics.density

    /** 日期數字字形的下限：同一天在字體 100% 時的字形高度（字體放大不能讓日期比預設還小） */
    private val dayMinCache = HashMap<LocalDate, Double>()
    private fun dayMin(day: LocalDate): Double = dayMinCache.getOrPut(day) {
        val v = widget(day, 1f)
        val tv = v.findViewById<TextView>(R.id.day)
        glyphs(v, tv).maxOf { it.height() } / d.toDouble()
    }

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun widget(day: LocalDate, fontScale: Float, hDp: Float = 250f, wDp: Float = 150f): View {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); tear(day) }
        return CalendarWidget.build(context, Almanac.page(day), PageLayout.TALL).apply(context, FrameLayout(context)).also { measure(it, (wDp * d).toInt(), (hDp * d).toInt()) }
    }

    private fun measure(v: View, w: Int, h: Int) {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
    }

    private fun relayout(v: View) = measure(v, v.width, v.height)

    private fun shoot(v: View): Bitmap = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(desk); v.draw(this) } }

    /** 墨丸實際有墨（不透明）的範圍：ImageView 單獨畫出來，找 alpha > 32 的像素外框（不含透明留白） */
    private fun inkRect(root: View, iv: View): Rect {
        val r = rectIn(root, iv)
        if (iv.width <= 0 || iv.height <= 0) return Rect()
        val b = Bitmap.createBitmap(iv.width, iv.height, Bitmap.Config.ARGB_8888).also { iv.draw(Canvas(it)) }
        var l = b.width; var t = b.height; var rr = -1; var bb = -1
        for (y in 0 until b.height) for (x in 0 until b.width) if ((b.getPixel(x, y) ushr 24) > 32) {
            if (x < l) l = x; if (y < t) t = y; if (x > rr) rr = x; if (y > bb) bb = y
        }
        return if (rr < 0) Rect() else Rect(r.left + l, r.top + t, r.left + rr + 1, r.top + bb + 1)
    }

    private fun rectIn(root: View, v: View): Rect {
        var x = 0
        var y = 0
        var cur: View = v
        while (cur !== root) { x += cur.left - cur.scrollX; y += cur.top - cur.scrollY; cur = cur.parent as View }
        return Rect(x, y, x + v.width, y + v.height)
    }

    /** 每一行字形實際的範圍（getTextBounds：不含行距與字距留白），在 root 座標 */
    private fun glyphs(root: View, tv: TextView): List<Rect> {
        val l = tv.layout ?: return emptyList()
        val r = rectIn(root, tv)
        val text = tv.text.toString()
        return (0 until l.lineCount).mapNotNull { i ->
            val s = text.substring(l.getLineStart(i), l.getLineEnd(i)).trimEnd()
            if (s.isEmpty()) return@mapNotNull null
            val b = Rect()
            tv.paint.getTextBounds(s, 0, s.length, b)
            val x = r.left + tv.totalPaddingLeft + l.getLineLeft(i).toInt()
            val y = r.top + tv.totalPaddingTop + l.getLineBaseline(i)
            Rect(x + b.left, y + b.top, x + b.right, y + b.bottom)
        }
    }

    /** 字形有多少被切掉（自己的元件或小工具外框之外，或「…」省略）：回傳被切掉的高度比例最大值（0＝完整） */
    private fun clipped(root: View, tv: TextView): Double {
        val l = tv.layout ?: return 0.0
        if (l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0) return 1.0
        val own = rectIn(root, tv)
        val frame = Rect(0, 0, root.width, root.height)
        val tol = (1 * d).toInt()
        return glyphs(root, tv).maxOfOrNull { g ->
            val vis = Rect(g)
            if (!vis.intersect(own) || !vis.intersect(frame)) return@maxOfOrNull 1.0
            val lostH = (g.height() - vis.height()).coerceAtLeast(0)
            val lostW = (g.width() - vis.width()).coerceAtLeast(0)
            if (lostH <= tol && lostW <= tol) 0.0 else maxOf(lostH.toDouble() / g.height(), lostW.toDouble() / g.width())
        } ?: 0.0
    }

    private val textIds = listOf(
        R.id.month to "月份", R.id.weekday to "星期", R.id.month_en to "英文月份", R.id.day to "日期", R.id.label to "節日",
        R.id.phrase to "宜", R.id.lunar to "農曆", R.id.ji to "忌", R.id.sun to "天氣",
    )

    private fun shown(v: View, id: Int) = v.findViewById<TextView>(id)?.takeIf { it.visibility == View.VISIBLE && it.text.isNotEmpty() && it.width > 0 && it.height > 0 }

    data class Verdict(val pass: Boolean, val dayDp: Double, val problems: List<String>)

    /** 通過＝日期字形完整可見且不小於 100% 時的字形、所有文字字形完整、文字彼此不重疊、不壓到墨丸 */
    private fun verdict(v: View, minDay: Double): Verdict {
        val problems = mutableListOf<String>()
        val texts = textIds.mapNotNull { (id, n) -> shown(v, id)?.let { Triple(id, n, it) } }
        for ((id, n, _) in textIds.map { Triple(it.first, it.second, v.findViewById<TextView>(it.first)) }) {
            val tv = v.findViewById<TextView>(id)
            if (id == R.id.label && (tv.visibility != View.VISIBLE || tv.text.isEmpty())) continue
            if (tv.visibility == View.VISIBLE && tv.text.isNotEmpty() && (tv.width == 0 || tv.height == 0)) problems += "${n}看不到"
        }
        for ((_, n, tv) in texts) clipped(v, tv).let { if (it > 0) problems += "${n}被切 %.0f%%".format(it * 100) }
        val mowan = inkRect(v, v.findViewById<ImageView>(R.id.mowan_still))
        for (i in texts.indices) for (j in i + 1 until texts.size) {
            val a = glyphs(v, texts[i].third)
            val b = glyphs(v, texts[j].third)
            if (a.any { x -> b.any { y -> Rect.intersects(x, y) } }) problems += "${texts[i].second}×${texts[j].second}"
        }
        for ((_, n, tv) in texts) if (glyphs(v, tv).any { Rect.intersects(it, mowan) }) problems += "${n}×墨丸"
        val day = shown(v, R.id.day)
        val dayDp = day?.let { glyphs(v, it).maxOfOrNull { g -> g.height() }?.div(d.toDouble()) } ?: 0.0
        if (dayDp < minDay - 0.5) problems += "日期字形 %.0fdp（< 100%% 時的 %.0f）".format(dayDp, minDay)
        return Verdict(problems.isEmpty(), dayDp, problems.distinct())
    }

    private fun f(v: Verdict) = (if (v.pass) "通過" else "**不通過**") + "（日期 %.0fdp".format(v.dayDp) + (if (v.problems.isEmpty()) "）" else "；${v.problems.joinToString("、")}）")

    // ---------- 1. 垂直空間分配 ----------

    /** 不受高度限制時，每個元件自己要多高（含上下 margin） */
    private fun natural(child: View, width: Int): Int {
        val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
        val w = width - (lp?.leftMargin ?: 0) - (lp?.rightMargin ?: 0)
        val fixed = lp?.height?.takeIf { it > 0 }
        val h = if (fixed != null) fixed else {
            child.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            child.measuredHeight
        }
        return h + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
    }

    private fun name(v: View): String = when (v.id) {
        R.id.band -> "色帶（固定 12dp）"
        R.id.month_en -> "英文月份"
        R.id.day -> "日期數字（weight＝1，自動縮放）"
        R.id.label -> "節日名稱"
        R.id.phrase -> "宜"
        R.id.rule -> "分隔線（1dp＋上下 6dp）"
        R.id.lunar -> "農曆"
        R.id.ji -> "忌"
        else -> if (v is LinearLayout) "月份＋星期（一列）" else "底部保留給山丘與墨丸（固定 84dp）"
    }

    @Test
    fun verticalBudget() {
        val report = StringBuilder("# 最小 TALL 小工具（150×250dp）的垂直空間分配\n\n")
        report.append("單位 dp。文字欄是一個直式 LinearLayout（由上到下），日期數字 `weight=1` 拿剩下的空間；左下的天氣三行、右下的墨丸、山丘是疊在上面的（FrameLayout）。\n\n")
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) {
            report.append("## $dayName\n\n| 區塊 | 100% | 200% | 性質 |\n|---|---|---|---|\n")
            val rows = LinkedHashMap<String, MutableList<Int>>()
            val totals = mutableListOf<String>()
            for (scale in listOf(1f, 2f)) {
                val v = widget(day, scale)
                val column = v.findViewById<View>(R.id.ji).parent as LinearLayout
                val w = column.width
                var sum = 0
                for (i in 0 until column.childCount) {
                    val c = column.getChildAt(i)
                    if (c.visibility == View.GONE) { rows.getOrPut(name(c)) { mutableListOf() } += 0; continue }
                    val h = if (c.id == R.id.day) 0 else natural(c, w)
                    if (c.id != R.id.day) sum += h
                    rows.getOrPut(name(c)) { mutableListOf() } += h
                }
                val root = v as ViewGroup
                val inner = v.findViewById<View>(R.id.hills).parent as View
                val pad = (v.height - inner.height)
                val sun = v.findViewById<TextView>(R.id.sun)
                val sunNeed = natural(sun, inner.width)
                val mowan = v.findViewById<View>(R.id.mowan_still)
                val mowanNeed = natural(mowan, inner.width)
                val left = column.height - sum
                rows.getOrPut("（疊放）左下天氣三行＋下邊距") { mutableListOf() } += sunNeed
                rows.getOrPut("（疊放）右下墨丸＋下邊距") { mutableListOf() } += mowanNeed
                rows.getOrPut("（疊放）山丘") { mutableListOf() } += v.findViewById<View>(R.id.hills).layoutParams.height
                rows.getOrPut("小工具外框的內距（下 5dp）") { mutableListOf() } += pad
                rows.getOrPut("**日期數字可以用的高度**（欄高 − 其他）") { mutableListOf() } += left
                val dayTv = v.findViewById<TextView>(R.id.day)
                totals += "${(scale * 100).toInt()}%%：欄高 %.0f、其他合計 %.0f、日期可用 %.0f（日期字級 %.0f、元件高 %.0f）".format(
                    column.height / d, sum / d, left / d, dayTv.textSize / d, dayTv.height / d)
            }
            for ((k, hs) in rows) {
                val kind = when {
                    k.contains("固定") || k.contains("山丘") || k.contains("內距") || k.contains("墨丸") -> "固定（可以重新配置）"
                    k.contains("日期") -> "剩下的空間"
                    else -> "跟著字體放大"
                }
                report.append("| $k | ${hs.joinToString(" | ") { "%.0f".format(it / d) }} | $kind |\n")
            }
            report.append("\n" + totals.joinToString("；") + "\n\n")
        }
        report.append("天氣三行與墨丸是疊放的：不佔文字欄的高度，但文字欄最下面的 84dp 就是為了讓出它們的位置。\n")
        report.append("日期數字需要的最小可見高度：同一天在字體 100% 時的字形高度（一般日 %.0fdp、春節 %.0fdp）；字形約是字級的 0.7，所以日期元件至少要再高一些。\n".format(dayMin(normal), dayMin(spring)))
        File(out, "1-budget.md").writeText(report.toString())
    }

    // ---------- 2. 候選 ----------

    /**
     * T6：結構重新配置（只在字體放大時用另一個版面；100% 照舊）。都是 RemoteViews 支援的 LinearLayout／FrameLayout／TextView／ImageView。
     * 1. 日期那一列改成橫的：左邊日期數字、右邊天氣三行（天氣離開左下）。日期受限的是高度，讓出寬度不影響它的大小。
     * 2. 左下沒有天氣以後，底部只要讓出墨丸：墨丸縮成 36×48dp、下邊距 12dp，底部保留從 84dp 改成 60dp。
     * 3. 分隔線上下 6dp → 2dp。
     * 4. 有節日名稱時，節日名稱移到英文月份那一行的右邊（兩個合成一行，節日名稱的字級不變；節日當天原本不顯示的英文月份也會顯示）。
     */
    private fun t6(v: View, scale: Float) {
        if (scale <= 1.3f) return
        val column = v.findViewById<View>(R.id.ji).parent as LinearLayout
        val dayTv = v.findViewById<TextView>(R.id.day)
        val sun = v.findViewById<TextView>(R.id.sun)
        (sun.parent as ViewGroup).removeView(sun)
        val at = column.indexOfChild(dayTv)
        column.removeView(dayTv)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(dayTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        sun.gravity = Gravity.START
        row.addView(sun, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = (8 * d).toInt() })
        column.addView(row, at, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // 英文月份與節日名稱同一行
        val en = v.findViewById<TextView>(R.id.month_en)
        val label = v.findViewById<TextView>(R.id.label)
        if (label.visibility == View.VISIBLE && label.text.isNotEmpty()) {
            column.removeView(label)
            en.visibility = View.VISIBLE
            val pair = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val enAt = column.indexOfChild(en)
            column.removeView(en)
            pair.addView(en, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            pair.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = (6 * d).toInt() })
            column.addView(pair, enAt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        v.findViewById<View>(R.id.rule).layoutParams = (v.findViewById<View>(R.id.rule).layoutParams as ViewGroup.MarginLayoutParams).apply { topMargin = (2 * d).toInt(); bottomMargin = (2 * d).toInt() }
        val mowan = v.findViewById<View>(R.id.mowan_still)
        mowan.layoutParams = (mowan.layoutParams as ViewGroup.MarginLayoutParams).apply { width = (36 * d).toInt(); height = (48 * d).toInt(); bottomMargin = (12 * d).toInt() }
        val spacer = column.getChildAt(column.childCount - 1)
        spacer.layoutParams = spacer.layoutParams.apply { height = (60 * d).toInt() }
        relayout(v)
    }

    /** T7：小工具上的字放大到 [cap] 倍為止（setTextViewTextSize；日期數字的縮放下限也用 dp），結構不變；可以疊在 T6 上 */
    private fun cap(v: View, scale: Float, cap: Float) {
        if (scale <= cap) return
        fun walk(x: View) {
            if (x is TextView && x.id != R.id.day) x.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, x.textSize / scale * cap)
            if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
        }
        walk(v)
        v.findViewById<TextView>(R.id.day).setAutoSizeTextTypeUniformWithConfiguration(30, 300, 1, android.util.TypedValue.COMPLEX_UNIT_DIP)
        relayout(v)
    }

    private val caps = listOf(2.0f, 1.8f, 1.6f, 1.5f, 1.4f, 1.3f, 1.2f, 1.15f, 1.1f, 1.05f)

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 26f }

    private fun sheet(name: String, title: String, cols: List<String>, rows: List<Pair<String, List<Bitmap>>>, colW: Int = 260) {
        val scaled = rows.map { (n, bs) -> n to bs.map { Bitmap.createScaledBitmap(it, colW, maxOf(1, it.height * colW / it.width), true) } }
        val rowH = scaled.map { it.second.maxOf { b -> b.height } + 52 }
        val sheet = Bitmap.createBitmap(cols.size * (colW + 20) + 20, 96 + rowH.sum(), Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawText(title, 20f, 34f, words)
            cols.forEachIndexed { i, c -> drawText(c, 20f + i * (colW + 20), 78f, words) }
            var y = 96f
            scaled.forEachIndexed { r, (n, bs) ->
                drawText(n, 20f, y + 30f, words)
                bs.forEachIndexed { i, b -> drawBitmap(b, 20f + i * (colW + 20), y + 40f, null) }
                y += rowH[r]
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun candidates() {
        val report = StringBuilder("# 最小 TALL（150×250dp）：T6（結構重新配置）、T7（有限字體倍率）\n\n")
        report.append("判斷用字形實際可見的範圍：日期字形完整而且不小於同一天 100% 時的字形、所有文字完整、彼此不重疊、不壓到墨丸，才算**通過**。\n\n")
        report.append("| 日子 | 字體 | 現況 | T6 | T7（最大能通過的倍率） | T6＋T7（最大能通過的倍率） |\n|---|---|---|---|---|---|\n")
        val rows = mutableListOf<Pair<String, List<Bitmap>>>()
        val notes = StringBuilder()
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) for (scale in listOf(1f, 2f)) {
            val min = dayMin(day)
            val now = widget(day, scale)
            val vNow = verdict(now, min)
            val s0 = shoot(now)
            val t6v = widget(day, scale).also { t6(it, scale) }
            val v6 = verdict(t6v, min)
            val s6 = shoot(t6v)
            if (scale == 1f && !s6.sameAs(s0)) notes.append("- $dayName 100%：T6 改了畫面（不應該）\n")
            // T7：從大到小找第一個能通過的倍率
            fun search(withT6: Boolean): Triple<Float?, Verdict?, Bitmap?> {
                if (scale <= 1f) return Triple(null, null, null)
                for (c in caps) {
                    val v = widget(day, scale).also { if (withT6) t6(it, scale); cap(it, scale, c) }
                    val r = verdict(v, min)
                    if (r.pass) return Triple(c, r, shoot(v))
                }
                val v = widget(day, scale).also { if (withT6) t6(it, scale); cap(it, scale, caps.last()) }
                return Triple(null, verdict(v, min), shoot(v))
            }
            val (c7, v7, s7) = search(false)
            val (c67, v67, s67) = search(true)
            fun cell(c: Float?, v: Verdict?) = if (v == null) "（100% 不需要）" else if (c == null) "到 ${caps.last()} 倍都**不通過**（${f(v)}）" else "**%.2f 倍**：%s".format(c, f(v))
            report.append("| $dayName | ${(scale * 100).toInt()}% | ${f(vNow)} | ${f(v6)} | ${cell(c7, v7)} | ${cell(c67, v67)} |\n")
            if (scale == 2f) rows += "$dayName 200%" to listOfNotNull(s0, s6, s7, s67)
        }
        report.append("\n" + notes)
        report.append("\nT7 對使用者的影響：系統字體設 200% 時，小工具上的字實際只放大到上表的倍率（App 裡照系統設定）。\n")
        sheet("2-candidates", "最小 TALL 200%：現況／T6（結構）／T7（倍率上限）／T6＋T7", listOf("現況", "T6", "T7", "T6＋T7"), rows)
        File(out, "2-candidates.md").writeText(report.toString())
    }

    /** 提高最小高度：200% 不限制倍率時，要多高才能完全通過（現在的結構、T6） */
    @Test
    fun minimumHeight() {
        val report = StringBuilder("# 提高最小高度的替代方案（寬 150dp、字體 200%、不限制倍率）\n\n| 日子 | 結構 | 最小能通過的高度 | 那個高度的結果 |\n|---|---|---|---|\n")
        val shots = mutableListOf<Bitmap>()
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) for (structure in listOf("現在的結構", "T6")) {
            val min = dayMin(day)
            var found: Pair<Int, Verdict>? = null
            var last: Verdict? = null
            for (h in 250..520 step 10) {
                val v = widget(day, 2f, hDp = h.toFloat()).also { if (structure == "T6") t6(it, 2f) }
                val r = verdict(v, min)
                last = r
                if (r.pass) { found = h to r; if (structure == "T6") shots += shoot(v); break }
            }
            report.append("| $dayName | $structure | ${found?.let { "${it.first}dp" } ?: "> 520dp"} | ${found?.let { f(it.second) } ?: "520dp 時：${last?.let { f(it) }}"} |\n")
        }
        // 只加高解決不了「寬度不夠」：再找寬度（高度 250 與 520）
        report.append("\n## 同時放寬寬度（字體 200%、不限制倍率）\n\n| 日子 | 結構 | 高度 | 最小能通過的寬度 | 那個寬度的結果 |\n|---|---|---|---|---|\n")
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) for (structure in listOf("現在的結構", "T6")) for (h in listOf(250, 520)) {
            val min = dayMin(day)
            var found: Pair<Int, Verdict>? = null
            var last: Verdict? = null
            for (w in 150..330 step 10) {
                val v = widget(day, 2f, hDp = h.toFloat(), wDp = w.toFloat()).also { if (structure == "T6") t6(it, 2f) }
                val r = verdict(v, min)
                last = r
                if (r.pass) { found = w to r; break }
            }
            report.append("| $dayName | $structure | ${h}dp | ${found?.let { "${it.first}dp" } ?: "> 330dp"} | ${found?.let { f(it.second) } ?: "330dp 時：${last?.let { f(it) }}"} |\n")
        }
        report.append("\n目前 TALL 的最小尺寸是 150×250dp（`PageLayout.TALL`、`calendar_widget_info.xml`）。提高最小高度會讓 launcher 不能把 TALL 縮到那麼小，所有使用者（不只大字體）都受影響，PageLayout 的尺寸判斷也要一起改。\n")
        File(out, "3-min-height.md").writeText(report.toString())
    }

    // ---------- 3. WIDE 8 月日期數字的 1.05 ----------

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /** 某個點最上層是哪個元件（不含 [except]） */
    private fun topAt(root: View, x: Int, y: Int, except: View): String {
        var best = "（背景）"
        fun walk(v: View) {
            if (v === except || v.visibility != View.VISIBLE) return
            val r = rectIn(root, v)
            if (!r.contains(x, y)) return
            if (v !is ViewGroup) best = context.resources.getResourceEntryName(v.id.takeIf { it != View.NO_ID } ?: return)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        return best
    }

    @Test
    fun wideDayContrastAnomaly() {
        val day = generateSequence(LocalDate.of(2027, 8, 2)) { it.plusDays(1) }.first { Almanac.page(it).label == null && !Almanac.page(it).isRedDay }
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        val report = StringBuilder("# WIDE 8 月日期數字對比 1.05 的來源（$day）\n\n")
        for ((w, h) in listOf(250 to 110, 300 to 130)) {
            val v = CalendarWidget.build(context, Almanac.page(day), PageLayout.WIDE, wideWidthDp = w.toFloat()).apply(context, FrameLayout(context)).also { measure(it, (w * d).toInt(), (h * d).toInt()) }
            val tv = v.findViewById<TextView>(R.id.day)
            tv.visibility = View.INVISIBLE
            val bg = shoot(v)
            tv.visibility = View.VISIBLE
            fun worstIn(r: Rect): Triple<Double, Int, Pair<Int, Int>> {
                val rr = Rect(r).apply { intersect(0, 0, bg.width, bg.height) }
                var worst = Double.MAX_VALUE
                var at = 0 to 0
                var px = 0
                for (y in rr.top until rr.bottom) for (x in rr.left until rr.right) {
                    val p = bg.getPixel(x, y)
                    val c = ratio(tv.currentTextColor, p)
                    if (c < worst) { worst = c; at = x to y; px = p }
                }
                return Triple(worst, px, at)
            }
            // 上一輪的做法：layout 的行範圍（行高 × 行寬）
            val own = rectIn(v, tv)
            val l = tv.layout
            val lineBox = Rect(own.left + tv.totalPaddingLeft + l.getLineLeft(0).toInt(), own.top + tv.totalPaddingTop, own.left + tv.totalPaddingLeft + l.getLineRight(0).toInt(), own.top + tv.totalPaddingTop + l.height)
            val (w1, p1, a1) = worstIn(lineBox)
            val glyph = glyphs(v, tv).first()
            val (w2, p2, a2) = worstIn(glyph)
            report.append("## ${w}×${h}dp\n\n")
            report.append("- 日期數字：顏色 #%06X、元件 %s、行範圍 %s、字形範圍 %s\n".format(tv.currentTextColor and 0xFFFFFF, own.toShortString(), lineBox.toShortString(), glyph.toShortString()))
            report.append("- 用行範圍量（上一輪）：最差 %.2f，在 (%d,%d)，背景 #%06X，那裡最上層是 `%s`\n".format(w1, a1.first, a1.second, p1 and 0xFFFFFF, topAt(v, a1.first, a1.second, tv)))
            report.append("- 用字形範圍量：最差 %.2f，在 (%d,%d)，背景 #%06X，那裡最上層是 `%s`\n\n".format(w2, a2.first, a2.second, p2 and 0xFFFFFF, topAt(v, a2.first, a2.second, tv)))
        }
        File(out, "4-wide-day-anomaly.md").writeText(report.toString())
    }
}
