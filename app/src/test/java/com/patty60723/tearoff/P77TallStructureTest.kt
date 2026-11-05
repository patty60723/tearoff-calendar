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
import com.patty60723.tearoff.mowan.Detail
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanTimeline
import com.patty60723.tearoff.mowan.PathData
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

    /**
     * 一行字實際有墨的範圍（相對於起點與基線）：用同一支 Paint 畫在夠大的透明圖上，找 alpha > 32 的像素外框。
     * 不用 getTextBounds：有 letterSpacing 時它會把字距留白也算進去（上一輪「被切 1～2%」的誤報）。
     */
    private val inkCache = HashMap<String, Rect>()

    private fun ink(paint: Paint, s: String): Rect = inkCache.getOrPut("$s|${paint.textSize}|${paint.letterSpacing}|${paint.typeface?.hashCode()}|${paint.isFakeBoldText}") { inkOf(paint, s) }

    private fun inkOf(paint: Paint, s: String): Rect {
        val p = Paint(paint)
        val fm = p.fontMetricsInt
        val pad = (p.textSize / 4).toInt() + 4
        val w = (p.measureText(s)).toInt() + pad * 2
        val h = (fm.bottom - fm.top) + pad * 2
        val bmp = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
        val base = pad - fm.top
        Canvas(bmp).drawText(s, pad.toFloat(), base.toFloat(), p.apply { color = Color.BLACK })
        var l = w; var t = h; var r = -1; var b = -1
        val row = IntArray(bmp.width)
        for (y in 0 until bmp.height) {
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            for (x in row.indices) if ((row[x] ushr 24) > 32) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        }
        bmp.recycle()
        return if (r < 0) Rect() else Rect(l - pad, t - base, r + 1 - pad, b + 1 - base)
    }

    /** 每一行字形實際的範圍（getTextBounds：不含行距與字距留白），在 root 座標 */
    private fun glyphs(root: View, tv: TextView): List<Rect> {
        val l = tv.layout ?: return emptyList()
        val r = rectIn(root, tv)
        val text = tv.text.toString()
        return (0 until l.lineCount).mapNotNull { i ->
            val s = text.substring(l.getLineStart(i), l.getLineEnd(i)).trimEnd()
            if (s.isEmpty()) return@mapNotNull null
            val b = ink(tv.paint, s)
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
        // 碰撞一律用實際有墨的像素（2026-11-04 最後一輪起；之前墨丸用有墨範圍的外框，春節 100% 的「忌×墨丸」就是外框造成的）
        val masks = texts.map { mask(v, it.third) }
        val mowanMask = mask(v, v.findViewById<ImageView>(R.id.mowan_still))
        fun hit(a: BooleanArray, b: BooleanArray): Boolean { for (k in a.indices) if (a[k] && b[k]) return true; return false }
        for (i in texts.indices) for (j in i + 1 until texts.size) if (hit(masks[i], masks[j])) problems += "${texts[i].second}×${texts[j].second}"
        for ((i, t) in texts.withIndex()) if (hit(masks[i], mowanMask)) problems += "${t.second}×墨丸"
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
        report.append("日期數字需要的最小可見高度：同一天在字體 100%% 時的字形高度（一般日 %.0fdp、春節 %.0fdp）；字形約是字級的 0.7，所以日期元件至少要再高一些。\n".format(dayMin(normal), dayMin(spring)))
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
    private fun t6(v: View, scale: Float, force: Boolean = false) {
        if (!force && scale <= 1.3f) return
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

    // ---------- 5. T8：T6 ＋ 宜、忌必要時換兩行（2026-11-04 最後一輪） ----------

    /**
     * T8：字體放大（> 100%）時用另一個版面檔：T6 的結構（日期列右邊放天氣、節日與英文月份同一行、分隔線邊距 2dp、墨丸 36×48），
     * 再讓「宜」「忌」最多兩行（`maxLines=2`，置中）；日期自動縮放改用 dp（30～300dp），字體放大不會把下限也放大。
     * 都是版面檔屬性（RemoteViews 在 `CalendarWidget.build` 依字體倍率選版面檔），100% 不套用。
     */
    private fun t8(v: View, scale: Float) {
        if (scale <= 1f) return
        t6(v, scale, force = true)
        for (id in intArrayOf(R.id.phrase, R.id.ji)) v.findViewById<TextView>(id).apply { maxLines = 2; gravity = Gravity.CENTER_HORIZONTAL }
        v.findViewById<TextView>(R.id.day).setAutoSizeTextTypeUniformWithConfiguration(30, 300, 1, android.util.TypedValue.COMPLEX_UNIT_DIP)
        relayout(v)
    }

    private val t8Caps = listOf(1.9f, 1.8f, 1.7f, 1.6f, 1.5f, 1.45f, 1.4f, 1.35f, 1.3f, 1.25f, 1.2f, 1.15f, 1.1f, 1.05f)

    @Test
    fun t8() {
        val report = StringBuilder("# T8：T6 ＋ 宜、忌換兩行（最小 TALL 150×250dp）\n\n")
        report.append("判斷同 §9（碰撞改用實際有墨的像素）：日期字形完整而且不小於同一天 100% 時的字形；宜、忌、農曆、天氣、節日、月份、星期的字形完整（含「…」省略）；文字彼此不碰；不碰墨丸與裝飾（實際有墨的像素範圍）。\n\n")
        report.append("| 日子 | 字體 | 現況 | T6＋T7（上限 1.2） | T8（不限制倍率） | T8 能通過的最高倍率 |\n|---|---|---|---|---|---|\n")
        val rows = mutableListOf<Pair<String, List<Bitmap>>>()
        val notes = StringBuilder()
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            val min = dayMin(day)
            val now = widget(day, scale)
            val vNow = verdict(now, min)
            val s0 = shoot(now)
            val t67 = widget(day, scale).also { t6(it, scale); cap(it, scale, 1.2f) }
            val v67 = verdict(t67, min)
            val v8w = widget(day, scale).also { t8(it, scale) }
            val v8 = verdict(v8w, min)
            val s8 = shoot(v8w)
            if (scale == 1f && !s8.sameAs(s0)) notes.append("- $dayName 100%：T8 改了畫面（不應該）\n")
            var best: Pair<Float, Verdict>? = null
            var bestShot: Bitmap? = null
            var lastFail: Verdict? = null
            if (scale > 1f && !v8.pass) for (c in t8Caps.filter { it < scale }) {
                val w = widget(day, scale).also { t8(it, scale); cap(it, scale, c) }
                val r = verdict(w, min)
                if (r.pass) { best = c to r; bestShot = shoot(w); break } else lastFail = r
            }
            val cell = when {
                scale == 1f -> "（100% 不套用）"
                v8.pass -> "不需要（${(scale * 100).toInt()}% 就通過）"
                best != null -> "**%.2f 倍**：%s".format(best.first, f(best.second))
                else -> "到 ${t8Caps.last()} 倍都**不通過**（${lastFail?.let { f(it) }}）"
            }
            report.append("| $dayName | ${(scale * 100).toInt()}% | ${f(vNow)} | ${if (scale <= 1.2f) "（不套用）" else f(v67)} | ${if (scale == 1f) "（不套用）" else f(v8)} | $cell |\n")
            rows += "$dayName ${(scale * 100).toInt()}%" to listOfNotNull(s0, shoot(t67), s8, bestShot ?: s8)
        }
        report.append("\n" + notes)
        report.append("\n第 4 欄的圖：能通過的最高倍率；都不通過時是 T8 不限制倍率。\n")
        sheet("5-t8", "最小 TALL：現況／T6＋T7（1.2）／T8（不限制）／T8 最高可通過倍率", listOf("現況", "T6＋T7 1.2", "T8 不限制", "T8 最高倍率"), rows, colW = 220)
        File(out, "5-t8.md").writeText(report.toString())
    }

    // ---------- 6. 春節 100%：墨丸（吊飾）碰到「忌」 ----------

    /** 一個元件單獨畫出來、放回小工具座標的有墨像素（alpha > 32） */
    private fun mask(root: View, part: View): BooleanArray {
        val r = rectIn(root, part)
        val b = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        Canvas(b).apply { translate(r.left.toFloat(), r.top.toFloat()); clipRect(0, 0, part.width, part.height); part.draw(this) }
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        return BooleanArray(px.size) { (px[it] ushr 24) > 32 }
    }

    data class Touch(val overlap: Int, val gapDp: Double, val at: Rect?)

    /** 兩組有墨像素：重疊幾個像素、最近距離（dp，重疊時是 0）、重疊（或最近）的範圍 */
    private fun touch(w: Int, a: BooleanArray, b: BooleanArray): Touch {
        var n = 0
        var box: Rect? = null
        for (i in a.indices) if (a[i] && b[i]) { n++; val x = i % w; val y = i / w; box = (box ?: Rect(x, y, x + 1, y + 1)).apply { union(x, y, x + 1, y + 1) } }
        if (n > 0) return Touch(n, 0.0, box)
        val pa = a.indices.filter { a[it] }
        val pb = b.indices.filter { b[it] }
        var best = Double.MAX_VALUE
        var at: Rect? = null
        for (i in pa) { val ax = i % w; val ay = i / w
            for (j in pb) { val dx = (j % w - ax).toDouble(); val dy = (j / w - ay).toDouble(); val dd = dx * dx + dy * dy
                if (dd < best) { best = dd; at = Rect(minOf(ax, j % w), minOf(ay, j / w), maxOf(ax, j % w) + 1, maxOf(ay, j / w) + 1) } } }
        return Touch(0, Math.sqrt(best) / d, at)
    }

    /** 小工具的墨丸重新畫一次：跟 CalendarWidget.setMowan 一樣的參數，[edit] 可以改節日背景（春聯與吊繩）的路徑 */
    private fun redrawMowan(v: View, day: LocalDate, layout: PageLayout, edit: (Mowan.Op) -> Mowan.Op): Bitmap {
        val book = StoryBook(context)
        val density = context.resources.displayMetrics.density.coerceAtMost(3f)
        val weather = WeatherService.current(context)?.let { MowanSettings.weatherOf(it.mood) } ?: Mowan.Weather.NONE
        val wPx = (layout.characterDp * density).toInt()
        val hPx = (layout.characterDp * 4 / 3 * density).toInt()
        val drawnHeight = minOf(wPx / 120.0, hPx / 160.0) * 160.0
        val frame = Mowan.frame(
            Mowan.partsOf(book.parts), 0, MowanSettings.hatOn(context), weather, MowanTimeline.stillClock(weather),
            still = true, bundleOn = MowanSettings.bundleOn(context), detail = Detail.of(drawnHeight, density.toDouble()),
            festival = FestivalTheme.of(day), art = MowanSettings.art(context).copy(smallEnv = CalendarWidget.smallEnvFor(layout)),
        )
        val edited = Mowan.Frame(frame.ops.map(edit), frame.head, frame.canopy)
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888).also { MowanPainter(edited).draw(Canvas(it), 0f, 0f, wPx.toFloat(), hPx.toFloat()) }
        v.findViewById<ImageView>(R.id.mowan_still).setImageBitmap(bmp)
        return bmp
    }

    private fun mapPath(p: PathData, f: (Double, Double) -> Pair<Double, Double>) = PathData(p.commands.map { c ->
        when (c) {
            is PathData.Cmd.MoveTo -> f(c.x, c.y).let { PathData.Cmd.MoveTo(it.first, it.second) }
            is PathData.Cmd.LineTo -> f(c.x, c.y).let { PathData.Cmd.LineTo(it.first, it.second) }
            is PathData.Cmd.ArcTo -> f(c.x, c.y).let { c.copy(x = it.first, y = it.second) }
            is PathData.Cmd.QuadTo -> { val a = f(c.x1, c.y1); val b = f(c.x, c.y); PathData.Cmd.QuadTo(a.first, a.second, b.first, b.second) }
            PathData.Cmd.Close -> c
        }
    })

    private val hanging = setOf("couplet", "cplString")

    private fun ys(p: PathData) = p.commands.mapNotNull { c -> when (c) { is PathData.Cmd.MoveTo -> c.y; is PathData.Cmd.LineTo -> c.y; is PathData.Cmd.ArcTo -> c.y; is PathData.Cmd.QuadTo -> c.y; else -> null } }

    /** 吊繩縮短 [units]：裝訂環（春聯那組裡、整個在 y < −10 的小圓）往下移，吊繩（cplString）往下端（y 14.5）壓縮；春聯菱形不動 */
    private fun shorten(v: View, units: Double) {
        val k = 1 - units / (14.5 + 17.2)
        redrawMowan(v, spring, PageLayout.TALL) { op ->
            // 裝訂環與吊繩都記在 "couplet" 底下（strokePath 的 part 參數只用來決定筆觸）；春聯菱形的 y 在 13.4～36.6，吊繩與環整個在 y ≤ 14.5
            val hang = op.part == "couplet" && ys(op.path).let { it.isNotEmpty() && it.max() <= 14.6 && it.min() < 0 }
            if (hang) op.copy(path = mapPath(op.path) { x, y -> x to (14.5 - (14.5 - y) * k) }) else op
        }
    }

    data class SpringCand(val id: String, val desc: String, val apply: (View) -> Unit, val goldens: String)

    @Test
    fun springCollision() {
        val layout = PageLayout.TALL
        val report = StringBuilder("# 春節 100%：墨丸與「忌」的碰撞（最小 TALL 150×250dp）\n\n")
        report.append("量法：「忌」與墨丸各自單獨畫出來，取實際有墨的像素（alpha > 32），算重疊的像素數與最近距離。\n\n")
        // 先確認重畫的墨丸跟小工具上的一模一樣
        val base = widget(spring, 1f)
        val real = shoot(base)
        redrawMowan(base, spring, layout) { it }
        val same = shoot(base).sameAs(real)
        report.append("重畫的墨丸跟小工具上的${if (same) "**逐像素相同**" else "**不同（候選圖不可信）**"}。\n\n")
        val units = 0.4 // 48dp 寬畫 120 單位
        val cands = listOf(
            SpringCand("現況", "—", {}, "—"),
            SpringCand("S1 吊繩縮短 6 單位", "裝訂環與吊繩上段往下 6 單位（%.1fdp），春聯本身不動".format(6 * units), { v ->
                shorten(v, 6.0)
            }, "小工具 MEDIUM／WIDE／TALL 的春節（只改小尺寸的春聯時）"),
            SpringCand("S2 吊繩縮短 10 單位", "同上，10 單位（%.1fdp）".format(10 * units), { v ->
                shorten(v, 10.0)
            }, "同 S1"),
            SpringCand("S3 春聯與吊繩右移 4 單位", "整組往墨丸靠 4 單位（%.1fdp）；菱形右角離頭約 6 單位".format(4 * units), { v ->
                redrawMowan(v, spring, layout) { op -> if (op.part in hanging) op.copy(path = mapPath(op.path) { x, y -> (x + 4) to y }) else op }
            }, "同 S1"),
            SpringCand("S4 墨丸往下 2dp", "墨丸的下邊距 24 → 22dp（整個墨丸含吊飾往下 2dp，大小不變；文字不動）", { v ->
                val m = v.findViewById<View>(R.id.mowan_still)
                m.layoutParams = (m.layoutParams as ViewGroup.MarginLayoutParams).apply { bottomMargin = (22 * d).toInt() }; relayout(v)
            }, "TALL 全部（墨丸位置）；App 日曆頁另外設下邊距，不受影響"),
            SpringCand("S5 文字與墨丸之間多 3dp", "底部保留 84 → 87dp（文字往上 3dp，日期少 3dp 高）", { v ->
                val column = v.findViewById<View>(R.id.ji).parent as LinearLayout
                val spacer = column.getChildAt(column.childCount - 1)
                spacer.layoutParams = spacer.layoutParams.apply { height = (87 * d).toInt() }; relayout(v)
            }, "TALL 與 App 日曆頁全部"),
        )
        report.append("| 候選 | 做法 | 重疊像素 | 最近距離 | 日期字形 | 會改的 golden | 判斷 |\n|---|---|---|---|---|---|---|\n")
        val shots = mutableListOf<Bitmap>()
        val zooms = mutableListOf<Bitmap>()
        var zoomBox: Rect? = null
        for (c in cands) {
            val v = widget(spring, 1f)
            c.apply(v)
            val ji = v.findViewById<TextView>(R.id.ji)
            val t = touch(v.width, mask(v, ji), mask(v, v.findViewById<View>(R.id.mowan_still)))
            if (c.id == "現況") zoomBox = t.at
            val r = verdict(v, dayMin(spring))
            val ok = t.overlap == 0 && t.gapDp >= 1.0 && r.pass
            report.append("| ${c.id} | ${c.desc} | ${t.overlap} | ${"%.1f".format(t.gapDp)}dp | ${"%.0f".format(r.dayDp)}dp | ${c.goldens} | ${if (ok) "不碰（≥ 1dp）" else "**仍碰到或太近**"}${if (r.pass) "" else "；${r.problems.joinToString("、")}"} |\n")
            shots += shoot(v)
        }
        // 局部放大：碰撞位置周圍 24dp，放大 8 倍；紅＝「忌」與墨丸重疊的像素
        val box = zoomBox ?: Rect(0, 0, 1, 1)
        val cx = box.centerX(); val cy = box.centerY(); val half = (12 * d).toInt()
        val crop = Rect(cx - half, cy - half, cx + half, cy + half).apply { intersect(0, 0, real.width, real.height) }
        report.append("\n碰撞位置（現況）：小工具座標 (%.1f, %.1f)dp 附近，放大圖範圍 %s px。\n".format(cx / d, cy / d, crop.toShortString()))
        for ((i, c) in cands.withIndex()) {
            val v = widget(spring, 1f).also { c.apply(it) }
            val img = shoot(v)
            val a = mask(v, v.findViewById<TextView>(R.id.ji)); val b = mask(v, v.findViewById<View>(R.id.mowan_still))
            val z = Bitmap.createBitmap(crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
            for (y in 0 until crop.height()) for (x in 0 until crop.width()) {
                val idx = (crop.top + y) * img.width + crop.left + x
                z.setPixel(x, y, if (a[idx] && b[idx]) 0xFFE0301E.toInt() else img.getPixel(crop.left + x, crop.top + y))
            }
            zooms += Bitmap.createScaledBitmap(z, z.width * 8, z.height * 8, false)
            if (i == 0) File(out, "6-spring-zoom.png").outputStream().use { zooms[0].compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        sheet("6-spring-candidates", "春節 100% 最小 TALL：現況與候選（上：整張；下：碰撞處放大 8 倍，紅＝重疊）", cands.map { it.id.substringBefore(" ") }, listOf("整張" to shots, "放大" to zooms), colW = 220)
        File(out, "6-spring-collision.md").writeText(report.toString())
    }

    // ---------- 7. WIDE 250×110dp：12 個月日期數字的實際對比 ----------

    data class Worst(val ratio: Double, val day: LocalDate, val at: Pair<Int, Int>, val bg: Int, val color: Int, val onHills: Boolean)

    /** 日期數字有墨的像素位置上，日期色對「拿掉日期後」那裡的背景的最差對比 */
    private fun dayWorst(day: LocalDate, color: Int? = null): Worst {
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        val v = CalendarWidget.build(context, Almanac.page(day), PageLayout.WIDE, wideWidthDp = 250f).apply(context, FrameLayout(context)).also { measure(it, (250 * d).toInt(), (110 * d).toInt()) }
        val tv = v.findViewById<TextView>(R.id.day)
        color?.let { tv.setTextColor(it) }
        val ink = mask(v, tv)
        val hills = mask(v, v.findViewById<View>(R.id.hills))
        tv.visibility = View.INVISIBLE
        val bg = shoot(v)
        tv.visibility = View.VISIBLE
        val px = IntArray(bg.width * bg.height).also { bg.getPixels(it, 0, bg.width, 0, 0, bg.width, bg.height) }
        var worst = Double.MAX_VALUE; var at = 0; 
        for (i in ink.indices) if (ink[i]) { val r = ratio(tv.currentTextColor, px[i]); if (r < worst) { worst = r; at = i } }
        return Worst(worst, day, (at % bg.width) to (at / bg.width), px[at], tv.currentTextColor, hills[at])
    }

    private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)

    @Test
    fun wideMonths() {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1f)
        val report = StringBuilder("# WIDE 最小尺寸（250×110dp）：12 個月日期數字的實際對比\n\n")
        report.append("量法：2027 年每一天各畫一次；日期數字單獨畫出來取有墨的像素（alpha > 32），每個像素拿日期色跟**拿掉日期後那裡的背景**算對比，取最差。門檻 4.5:1、設計目標 4.8:1。\n\n")
        report.append("| 月 | 日期色 | 最差（一般日） | 哪一天、位置 | 背景 | 在山丘上 | 紅字日最差 | 哪一天、背景 | 判斷 |\n|---|---|---|---|---|---|---|---|---|\n")
        val failing = mutableListOf<Pair<Int, Worst>>()
        for (m in 1..12) {
            val days = generateSequence(LocalDate.of(2027, m, 1)) { it.plusDays(1) }.takeWhile { it.monthValue == m }.toList()
            val all = days.map { it to dayWorst(it) }
            val normalW = all.filter { !Almanac.page(it.first).isRedDay }.minByOrNull { it.second.ratio }?.second
            val redW = all.filter { Almanac.page(it.first).isRedDay }.minByOrNull { it.second.ratio }?.second
            val worst = listOfNotNull(normalW, redW).minOf { it.ratio }
            val verdict = when { worst >= 4.8 -> "達設計目標"; worst >= 4.5 -> "過門檻、未達 4.8"; else -> "**不合格（< 4.5）**" }
            report.append("| $m | ${hex(Palettes.forMonth(m).deep)} | ${normalW?.let { "%.2f".format(it.ratio) } ?: "—"} | ${normalW?.let { "${it.day.dayOfMonth} 日 (%.1f, %.1f)dp".format(it.at.first / d, it.at.second / d) } ?: "—"} | ${normalW?.let { hex(it.bg) } ?: "—"} | ${normalW?.let { if (it.onHills) "是" else "否" } ?: "—"} | ${redW?.let { "%.2f".format(it.ratio) } ?: "—"} | ${redW?.let { "${it.day.dayOfMonth} 日、${hex(it.bg)}${if (it.onHills) "（山丘）" else ""}" } ?: "—"} | $verdict |\n")
            normalW?.takeIf { it.ratio < 4.8 }?.let { failing += m to it }
            redW?.takeIf { it.ratio < 4.8 }?.let { failing += m to it }
        }
        // 8 月已核准的 #84693D
        val aug = generateSequence(LocalDate.of(2027, 8, 1)) { it.plusDays(1) }.takeWhile { it.monthValue == 8 }.filter { !Almanac.page(it).isRedDay }
            .map { dayWorst(it, 0xFF84693D.toInt()) }.minBy { it.ratio }
        report.append("\n8 月改成已核准的 `#84693D` 後：最差 **%.2f**（%d 日、背景 %s%s）。\n".format(aug.ratio, aug.day.dayOfMonth, hex(aug.bg), if (aug.onHills) "，在山丘上" else ""))
        // 候選：只針對未達 4.8 的月份（一般日用 deep、紅字日用 RED）
        report.append("\n## 未達 4.8 的月份：局部修正候選\n\n")
        report.append("- W1：只在 WIDE 的日期數字用加深色（`blend(INK, 原色, s)`，跟 WIDE 星期同一個做法），找最接近原色、而且最差像素達 4.5／4.8 的 s。\n")
        report.append("- W2：日期色不變，WIDE 日期數字下方的山丘變淡：最差背景往紙色混合 t，找達 4.5／4.8 的最小 t（只估算，沒有畫）。\n\n")
        report.append("| 月 | 哪種日子 | 現在 | W1 達 4.5 | W1 達 4.8 | W1 4.8 的實際驗證 | W2 達 4.5 | W2 達 4.8 |\n|---|---|---|---|---|---|---|---|\n")
        val candShots = mutableListOf<Pair<String, List<Bitmap>>>()
        for ((m, w) in failing.distinctBy { it.first to it.second.color }) {
            fun w1(target: Double): Pair<Double, Int>? { for (k in 0..100) { val s = k / 100.0; val c = Palettes.blend(Palettes.INK, w.color, s.toFloat()); if (ratio(c, w.bg) >= target) return s to c }; return null }
            fun w2(target: Double): Pair<Double, Int>? { for (k in 0..100) { val t = k / 100.0; val b = Palettes.blend(Palettes.PAPER, w.bg, t.toFloat()); if (ratio(w.color, b) >= target) return t to b }; return null }
            val a45 = w1(4.5); val a48 = w1(4.8); val b45 = w2(4.5); val b48 = w2(4.8)
            // 實際驗證：整個月用 W1 4.8 的顏色再掃一次
            val red = w.color == Palettes.RED
            val check = a48?.let { (_, c) -> generateSequence(LocalDate.of(2027, m, 1)) { it.plusDays(1) }.takeWhile { it.monthValue == m }.filter { Almanac.page(it).isRedDay == red }.map { dayWorst(it, c) }.minBy { it.ratio } }
            report.append("| $m | ${if (red) "紅字日" else "一般日"} | %.2f（${hex(w.color)}） | ${a45?.let { "s=%.2f → ${hex(it.second)}".format(it.first) } ?: "—"} | ${a48?.let { "s=%.2f → ${hex(it.second)}".format(it.first) } ?: "—"} | ${check?.let { "最差 %.2f".format(it.ratio) } ?: "—"} | ${b45?.let { "t=%.2f".format(it.first) } ?: "—"} | ${b48?.let { "t=%.2f".format(it.first) } ?: "—"} |\n".format(w.ratio))
            // 局部放大：最差那一天，現在／W1 4.8
            fun crop(c: Int?): Bitmap {
                AppClock.setFakeToday(context, w.day)
                val v = CalendarWidget.build(context, Almanac.page(w.day), PageLayout.WIDE, wideWidthDp = 250f).apply(context, FrameLayout(context)).also { measure(it, (250 * d).toInt(), (110 * d).toInt()) }
                c?.let { v.findViewById<TextView>(R.id.day).setTextColor(it) }
                val img = shoot(v)
                val r = rectIn(v, v.findViewById<TextView>(R.id.day)).apply { inset(-(4 * d).toInt(), -(4 * d).toInt()); intersect(0, 0, img.width, img.height) }
                return Bitmap.createBitmap(img, r.left, r.top, r.width(), r.height())
            }
            candShots += "$m 月${if (red) "紅字" else ""} ${w.day.dayOfMonth} 日" to listOfNotNull(crop(null), a48?.let { crop(it.second) })
        }
        if (candShots.isNotEmpty()) sheet("7-wide-months", "WIDE 250×110 未達 4.8 的月份：現在／W1（達 4.8）", listOf("現在", "W1 4.8"), candShots, colW = 300)
        File(out, "7-wide-months.md").writeText(report.toString())
    }

    // ---------- 8. W1（WIDE 日期局部加深）在 4 種 WIDE 大小的驗證（2026-11-05 後續分流） ----------

    /** 日期數字有墨的像素位置與那裡「拿掉日期後」的背景色（同一天、同一個大小只畫一次，任何日期色都能拿來算） */
    private fun inkBackground(day: LocalDate, w: Int, h: Int): Pair<IntArray, IntArray> {
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        val v = CalendarWidget.build(context, Almanac.page(day), PageLayout.WIDE, wideWidthDp = w.toFloat()).apply(context, FrameLayout(context)).also { measure(it, (w * d).toInt(), (h * d).toInt()) }
        val tv = v.findViewById<TextView>(R.id.day)
        val ink = mask(v, tv)
        tv.visibility = View.INVISIBLE
        val bg = shoot(v)
        val px = IntArray(bg.width * bg.height).also { bg.getPixels(it, 0, bg.width, 0, 0, bg.width, bg.height) }
        val idx = ink.indices.filter { ink[it] }.toIntArray()
        return idx to IntArray(idx.size) { px[idx[it]] }
    }

    private fun worstOf(bgs: IntArray, color: Int) = bgs.minOfOrNull { ratio(color, it) } ?: Double.MAX_VALUE

    @Test
    fun wideW1AllSizes() {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1f)
        val sizes = listOf(250 to 110, 300 to 130, 350 to 170, 400 to 200)
        val year = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == 2027 }.toList()
        // 每一天 × 每個大小：日期下面的背景（一次）
        val bg = HashMap<Pair<LocalDate, Int>, IntArray>()
        for (day in year) for ((i, wh) in sizes.withIndex()) bg[day to i] = inkBackground(day, wh.first, wh.second).second
        fun base(m: Int, red: Boolean) = if (red) Palettes.RED else if (m == 8) 0xFF84693D.toInt() else Palettes.forMonth(m).deep
        // W1：在最小尺寸（250×110）把每個月最差的那一天拉到 4.8 的最小加深量；已經 ≥ 4.8 就不改
        fun w1(m: Int, red: Boolean): Pair<Double, Int> {
            val days = year.filter { it.monthValue == m && Almanac.page(it).isRedDay == red }
            val b = base(m, red)
            if (days.isEmpty()) return 0.0 to b
            for (k in 0..100) {
                val c = Palettes.blend(Palettes.INK, b, (k / 100.0).toFloat())
                if (days.all { worstOf(bg.getValue(it to 0), c) >= 4.8 }) return k / 100.0 to c
            }
            return 1.0 to Palettes.INK
        }
        val report = StringBuilder("# W1（WIDE 日期局部加深）：4 種 WIDE 大小 × 12 個月 × 一般日／紅字日\n\n")
        report.append("對比標準：**日期數字一律用 4.5:1 當最低門檻、4.8:1 當設計目標**（不套用 WCAG 大字 3:1 的例外：日期是這個畫面最主要的資訊，而且最差的地方是字形下緣疊在山丘上）。\n")
        report.append("量法：日期數字有墨的像素位置上，日期色對拿掉日期後的背景；2027 年每一天 × 每個大小。8 月以已核准的 #84693D 為基準。\n")
        report.append("W1 的加深量 s 在最小尺寸 250×110 決定（整個月每一天都 ≥ 4.8 的最小 s），其他大小沿用同一個顏色。\n\n")
        report.append("| 月 | 日子 | 現在的顏色 | W1（s） | " + sizes.joinToString(" | ") { "${it.first}×${it.second} 現在 → W1" } + " |\n|---|---|---|---|" + "---|".repeat(sizes.size) + "\n")
        val fails = mutableListOf<String>()
        for (m in 1..12) for (red in listOf(false, true)) {
            val days = year.filter { it.monthValue == m && Almanac.page(it).isRedDay == red }
            if (days.isEmpty()) continue
            val b = base(m, red)
            val (sv, c) = w1(m, red)
            val cells = sizes.indices.map { i ->
                val now = days.minOf { worstOf(bg.getValue(it to i), b) }
                val after = days.minOf { worstOf(bg.getValue(it to i), c) }
                if (after < 4.5) fails += "$m 月${if (red) "紅字日" else "一般日"} ${sizes[i]} W1 後 %.2f".format(after)
                "%.2f → **%.2f**".format(now, after)
            }
            report.append("| $m | ${if (red) "紅字日" else "一般日"} | ${hex(b)} | ${if (sv == 0.0) "不改" else "${hex(c)}（%.2f）".format(sv)} | ${cells.joinToString(" | ")} |\n")
        }
        report.append("\n" + if (fails.isEmpty()) "**W1 之後：4 種大小、12 個月、一般日與紅字日的最差像素都 ≥ 4.5。**\n" else "**W1 之後仍未達 4.5：**\n" + fails.joinToString("\n") { "- $it" } + "\n")
        File(out, "8-wide-w1-sizes.md").writeText(report.toString())
    }
}
