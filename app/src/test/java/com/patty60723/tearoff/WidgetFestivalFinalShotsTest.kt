package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.patty60723.tearoff.mowan.ArtConfig.SmallEnv
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
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
 * P6-4c 最終候選截圖：照 2026-10-27 選定的方向（COMPACT 用 C 極簡、其他版面用 B 節日＋一個天氣線索、小尺寸春聯往角色靠），
 * 用實際的小工具管線畫（`CalendarWidget.build` 不指定美術設定，走 [CalendarWidget.smallEnvFor]）。
 * 存到 app/build/screenshots/p6-4c-final。正式小工具還沒接節日（`festivalLook` 只有這裡和 App 日曆頁傳 true）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WidgetFestivalFinalShotsTest {
    private val out = File("build/screenshots/p6-4c-final").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    private val festivals = listOf(Festival.SPRING to "春節", Festival.MOON to "中秋", Festival.XMAS to "聖誕")
    private val weathers = listOf(
        null to "沒有天氣", Mood.SUNNY to "晴", Mood.HOT to "熱", Mood.CLOUDY to "陰", Mood.FOG to "霧",
        Mood.RAIN to "雨", Mood.THUNDER to "雷雨", Mood.COLD to "冷", Mood.SNOW to "雪",
    )
    /** 小到大 */
    private val layouts = listOf(PageLayout.COMPACT, PageLayout.WIDE, PageLayout.MEDIUM, PageLayout.TALL)

    private fun day(f: Festival) = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == f }

    /** 實際的小工具（最小尺寸，最擠的情況）；[festive] false 是現在的小工具（沒有節日） */
    private fun widget(f: Festival, mood: Mood?, layout: PageLayout, festive: Boolean = true): View {
        MowanSettings.setStoryCard(context, true)
        val today = day(f)
        AppClock.setFakeToday(context, today)
        StoryBook(context).jumpTo(14, today)
        WeatherService.setFakeMood(context, mood)
        val views = CalendarWidget.build(context, Almanac.page(today), layout, festivalLook = festive)
        val d = context.resources.displayMetrics.density
        return views.apply(context, FrameLayout(context)).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec((layout.minWidthDp * d).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((layout.minHeightDp * d).toInt(), View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    private fun still(v: View) = (v.findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap

    private fun draw(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(paper); v.draw(this) } }

    private fun onPaper(b: Bitmap, zoom: Int = 1) = Bitmap.createBitmap(b.width * zoom, b.height * zoom, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawBitmap(if (zoom == 1) b else Bitmap.createScaledBitmap(b, b.width * zoom, b.height * zoom, false), 0f, 0f, null)
        }
    }

    /** [v] 在 [root] 裡的位置 */
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

    /** 春聯在小工具裡的範圍（圖上方 35% 裡最左邊的紅色；帽子、紅包都在右邊或下面）；沒有春聯是 null */
    private fun couplet(root: View): Rect? {
        val img = root.findViewById<ImageView>(R.id.mowan_still)
        val b = still(root)
        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = -1; var y1 = -1
        for (y in 0 until b.height * 35 / 100) for (x in 0 until b.width / 3) {
            val c = b.getPixel(x, y)
            val a = c ushr 24; val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val bl = c and 0xFF
            if (a > 128 && r > 150 && g < 130 && bl < 130) { x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y) }
        }
        if (x1 < 0) return null
        val at = rectIn(root, img)
        val sx = img.width.toFloat() / b.width
        val sy = img.height.toFloat() / b.height
        return Rect(at.left + (x0 * sx).toInt(), at.top + (y0 * sy).toInt(), at.left + (x1 * sx).toInt(), at.top + (y1 * sy).toInt())
    }

    /** 春聯左邊跟文字（日期數字、宜忌…）最近的水平距離（dp）；同一高度沒有文字是 null */
    private fun gapDp(root: View): Float? {
        val c = couplet(root) ?: return null
        var best: Int? = null
        fun visit(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is TextView && v.text.isNotEmpty() && v.layout != null) {
                val r = rectIn(root, v)
                val l = v.layout
                for (i in 0 until l.lineCount) {
                    val top = r.top + v.totalPaddingTop + l.getLineTop(i)
                    val bottom = r.top + v.totalPaddingTop + l.getLineBottom(i)
                    if (bottom < c.top || top > c.bottom) continue
                    val left = r.left + v.totalPaddingLeft + l.getLineLeft(i).toInt()
                    val right = r.left + v.totalPaddingLeft + l.getLineRight(i).toInt()
                    if (left > c.right) continue
                    val gap = c.left - right
                    best = minOf(best ?: gap, gap)
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(root)
        return best?.let { it / context.resources.displayMetrics.density }
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 30f }
    private val small = Paint(words).apply { textSize = 24f; color = 0xFF6B6B6B.toInt() }

    private fun grid(name: String, title: String, note: String, colTitles: List<String>, rowTitles: List<String>, cells: List<List<Bitmap>>) {
        val cw = maxOf(cells.flatten().maxOf { it.width } + 16, 150)
        val ch = cells.flatten().maxOf { it.height } + 16
        val left = 140
        val top = 160
        val sheet = Bitmap.createBitmap(maxOf(left + cw * colTitles.size, 1500), top + ch * rowTitles.size, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(paper)
            drawText(title, 12f, 40f, words)
            drawText(note, 12f, 80f, small)
            colTitles.forEachIndexed { i, t -> t.split('\n').forEachIndexed { k, line -> drawText(line, (left + i * cw + 8).toFloat(), 118f + k * 28, small) } }
            rowTitles.forEachIndexed { r, t ->
                drawText(t, 12f, (top + r * ch + ch / 2).toFloat(), words)
                cells[r].forEachIndexed { c, b -> drawBitmap(b, (left + c * cw + 8).toFloat(), (top + r * ch + 8).toFloat(), null) }
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun label(l: PageLayout) = "${l.name}\n角色 ${(l.stickmanDp * 4 / 3).toInt()}dp・${if (CalendarWidget.smallEnvFor(l) == SmallEnv.MINIMAL) "C 極簡" else "B 節日+線索"}"

    /** 規則：COMPACT 是 C，其他是 B */
    @Test
    fun selectedRule() {
        assertEquals(SmallEnv.MINIMAL, CalendarWidget.smallEnvFor(PageLayout.COMPACT))
        for (l in listOf(PageLayout.WIDE, PageLayout.MEDIUM, PageLayout.TALL)) assertEquals(SmallEnv.CUE, CalendarWidget.smallEnvFor(l))
    }

    /** 每個節日 × 9 種天氣 × 4 種版面（墨丸那張圖，xxhdpi 實際像素）；第一欄是現在的小工具（沒有節日）對照 */
    @Test
    fun weathers() {
        for ((f, fname) in festivals) {
            val cells = weathers.map { (m, _) ->
                listOf(onPaper(still(widget(f, m, PageLayout.TALL, festive = false)))) + layouts.map { onPaper(still(widget(f, m, it))) }
            }
            grid(
                "1-$f-weathers", "$fname：選定方向（xxhdpi 實際像素）", "第一欄是現在的小工具（沒有節日）對照；其他欄是各版面最後會畫的樣子",
                listOf("現在\nTALL 沒有節日") + layouts.map(::label), weathers.map { it.second }, cells,
            )
        }
    }

    /** 整個小工具（實際大小，最小尺寸＝最擠），標出春聯跟文字的距離；COMPACT、MEDIUM 是重點 */
    @Test
    fun spacing() {
        val moods = listOf(Mood.SUNNY to "晴", Mood.RAIN to "雨")
        val gaps = mutableListOf<String>()
        for ((f, fname) in festivals) {
            val cells = moods.map { (m, mname) ->
                layouts.map { l ->
                    val v = widget(f, m, l)
                    val g = gapDp(v)
                    if (f == Festival.SPRING) {
                        gaps += "$mname ${l.name} ${g?.let { "%.1f dp".format(it) } ?: "沒有春聯"}"
                        // 有春聯的版面：跟文字至少 6dp（COMPACT 用極簡，沒有春聯）
                        if (l == PageLayout.COMPACT) assertEquals("COMPACT 沒有春聯", null, couplet(v))
                        else g?.let { assertTrue("${l.name} $mname 春聯跟文字 %.1f dp".format(it), it >= 6f) }
                    }
                    draw(v)
                }
            }
            grid(
                "2-$f-spacing", "$fname：整個小工具（xxhdpi 實際大小，各版面的最小尺寸）",
                if (f == Festival.SPRING) "春聯左緣跟同一高度文字的距離：${gaps.joinToString("　")}" else "COMPACT 用 C 極簡；其他版面用 B",
                layouts.map(::label), moods.map { "$fname${it.second}" }, cells,
            )
        }
    }

    /** 48dp（COMPACT，C 極簡）在 mdpi、xhdpi 的實際像素與放大；左邊是現在的小工具對照 */
    @Test
    fun recognitionAt48dp() {
        val moods = listOf(null to "沒有天氣", Mood.SUNNY to "晴", Mood.RAIN to "雨", Mood.SNOW to "雪")
        for ((q, zoom) in listOf("mdpi" to 4, "xhdpi" to 2)) {
            RuntimeEnvironment.setQualifiers(q)
            val rows = festivals.flatMap { (f, fname) -> moods.map { (m, mname) -> Triple(f, m, "$fname$mname") } }
            val cells = rows.map { (f, m, _) ->
                val now = still(widget(f, m, PageLayout.COMPACT, festive = false))
                val fest = still(widget(f, m, PageLayout.COMPACT))
                listOf(onPaper(now), onPaper(now, zoom), onPaper(fest), onPaper(fest, zoom))
            }
            grid(
                "3-48dp-$q", "48dp（COMPACT・C 極簡）$q：實際像素與 $zoom 倍放大", "節日只靠頭飾與手上道具（春節紅包、中秋月餅、聖誕沒有手持道具）；雨傘照畫",
                listOf("現在\n實際", "現在\n×$zoom", "節日\n實際", "節日\n×$zoom"), rows.map { it.third }, cells,
            )
        }
        RuntimeEnvironment.setQualifiers("xxhdpi")
    }
}
