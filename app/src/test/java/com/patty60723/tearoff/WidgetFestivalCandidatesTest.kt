package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.patty60723.tearoff.mowan.ArtConfig
import com.patty60723.tearoff.mowan.ArtConfig.SmallEnv
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P6-4c 美術審核素材：小工具的節日外觀候選（D-16、D-43、48dp）。
 * 每一格都是實際的小工具管線畫出來的：`CalendarWidget.build` → RemoteViews 套到版面 → `MowanBitmap.still`。
 * 存到 app/build/screenshots/p6-4c。這不是比對用的測試（P6-4c 審核用；定案是 COMPACT＝C、其他版面＝B，已接到正式小工具）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class WidgetFestivalCandidatesTest {
    private val out = File("build/screenshots/p6-4c").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    /** 候選：null 是「現在的小工具」（沒有節日外觀，既有規則） */
    private val candidates = listOf(
        "0 現在" to null,
        "A 節日優先" to SmallEnv.FESTIVAL,
        "B 節日+線索" to SmallEnv.CUE,
        "C 極簡" to SmallEnv.MINIMAL,
    )

    private val legend = "0＝現在的小工具（沒有節日，既有）　A＝節日背景優先（現行程式預設，天氣環境讓位）　B＝A＋一個不衝突的天氣線索（D-43 候選）　C＝只有頭飾與手上道具（48dp 極簡候選）"

    private val festivals = listOf(Festival.SPRING to "春節", Festival.MOON to "中秋", Festival.XMAS to "聖誕")

    private val weathers = listOf(
        null to "沒有天氣", Mood.SUNNY to "晴", Mood.HOT to "熱", Mood.CLOUDY to "陰", Mood.FOG to "霧",
        Mood.RAIN to "雨", Mood.THUNDER to "雷雨", Mood.COLD to "冷", Mood.SNOW to "雪",
    )

    private fun day(f: Festival) = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == f }

    /** 實際的小工具版面（RemoteViews 套出來的 View） */
    private fun widget(f: Festival, mood: Mood?, layout: PageLayout, env: SmallEnv?): View {
        MowanSettings.setStoryCard(context, true)
        val today = day(f)
        AppClock.setFakeToday(context, today)
        StoryBook(context).jumpTo(14, today)
        WeatherService.setFakeMood(context, mood)
        val views = CalendarWidget.build(
            context, Almanac.page(today), layout,
            festivalLook = env != null, art = env?.let { ArtConfig.DEFAULT.copy(smallEnv = it) },
        )
        val d = context.resources.displayMetrics.density
        return views.apply(context, FrameLayout(context)).apply {
            measure(
                View.MeasureSpec.makeMeasureSpec((layout.minWidthDp * d).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((layout.minHeightDp * d).toInt(), View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, measuredWidth, measuredHeight)
        }
    }

    /** 小工具上那張墨丸的圖（真正送到小工具的像素） */
    private fun still(v: View) = (v.findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap

    private fun draw(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(paper); v.draw(this) } }

    private fun onPaper(b: Bitmap, zoom: Int = 1) = Bitmap.createBitmap(b.width * zoom, b.height * zoom, Bitmap.Config.ARGB_8888).also {
        Canvas(it).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawBitmap(if (zoom == 1) b else Bitmap.createScaledBitmap(b, b.width * zoom, b.height * zoom, false), 0f, 0f, null)
        }
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 30f }
    private val small = Paint(words).apply { textSize = 24f; color = 0xFF6B6B6B.toInt() }

    /** 表格：rows × cols 張圖，加上欄、列標題（欄標題可以用 \n 分兩行） */
    private fun grid(name: String, title: String, colTitles: List<String>, rowTitles: List<String>, cells: List<List<Bitmap>>) {
        val cw = maxOf(cells.flatten().maxOf { it.width } + 16, 150)
        val ch = cells.flatten().maxOf { it.height } + 16
        val left = 140
        val top = 160
        val sheet = Bitmap.createBitmap(maxOf(left + cw * colTitles.size, 1900), top + ch * rowTitles.size, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(paper)
            drawText(title, 12f, 40f, words)
            drawText(legend, 12f, 80f, small)
            colTitles.forEachIndexed { i, t -> t.split('\n').forEachIndexed { k, line -> drawText(line, (left + i * cw + 8).toFloat(), 118f + k * 28, small) } }
            rowTitles.forEachIndexed { r, t ->
                drawText(t, 12f, (top + r * ch + ch / 2).toFloat(), words)
                cells[r].forEachIndexed { c, b -> drawBitmap(b, (left + c * cw + 8).toFloat(), (top + r * ch + 8).toFloat(), null) }
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 每個節日 × 9 種天氣 × 4 種候選；左邊四欄是 TALL（角色 64dp），右邊四欄是 COMPACT（角色 48dp），都是實際像素 */
    @Test
    fun weathersPerFestival() {
        for ((f, fname) in festivals) {
            val cols = listOf(PageLayout.TALL, PageLayout.COMPACT).flatMap { l -> candidates.map { (c, _) -> "${if (l == PageLayout.TALL) "TALL 64dp" else "COMPACT 48dp"}\n$c" } }
            val cells = weathers.map { (mood, _) ->
                listOf(PageLayout.TALL, PageLayout.COMPACT).flatMap { l -> candidates.map { (_, env) -> onPaper(still(widget(f, mood, l, env))) } }
            }
            grid("1-$f-weathers", "$fname：9 種天氣 × 候選（xxhdpi 實際像素；小工具 V3 一律是簡化版）", cols, weathers.map { it.second }, cells)
        }
    }

    /** 每個節日在 4 種版面（整個小工具，縮成一半），天氣選最容易衝突的那一種 */
    @Test
    fun layoutsPerFestival() {
        val worst = mapOf(Festival.SPRING to (Mood.RAIN to "雨"), Festival.MOON to (Mood.CLOUDY to "陰"), Festival.XMAS to (Mood.SNOW to "雪"))
        for ((f, fname) in festivals) {
            val (mood, wname) = worst.getValue(f)
            val cells = candidates.map { (_, env) ->
                PageLayout.entries.map { l -> draw(widget(f, mood, l, env)).let { Bitmap.createScaledBitmap(it, it.width / 2, it.height / 2, true) } }
            }
            grid("2-$f-layouts", "$fname・$wname：4 種版面（整個小工具，縮成 50%）", PageLayout.entries.map { "${it.name}\n角色 ${(it.stickmanDp * 4 / 3).toInt()}dp" }, candidates.map { it.first.substringBefore(' ') }, cells)
        }
    }

    /** 48dp（COMPACT）在 mdpi、xhdpi 的實際像素，加上 4 倍放大（最近鄰，看得到每個像素） */
    @Test
    fun recognitionAt48dp() {
        val moods = listOf(Mood.SUNNY to "晴", Mood.RAIN to "雨", Mood.CLOUDY to "陰", Mood.SNOW to "雪")
        for ((q, zoom) in listOf("mdpi" to 4, "xhdpi" to 2)) {
            RuntimeEnvironment.setQualifiers(q)
            val rows = festivals.flatMap { (f, fname) -> moods.map { (m, mname) -> Triple(f, m, "$fname$mname") } }
            val cells = rows.map { (f, m, _) ->
                candidates.flatMap { (_, env) ->
                    val b = still(widget(f, m, PageLayout.COMPACT, env))
                    listOf(onPaper(b), onPaper(b, zoom))
                }
            }
            val cols = candidates.flatMap { (c, _) -> listOf("$c\n實際", "$c\n×$zoom") }
            grid("3-48dp-$q", "48dp（COMPACT）$q：實際像素與 $zoom 倍放大", cols, rows.map { it.third }, cells)
        }
        RuntimeEnvironment.setQualifiers("xxhdpi")
    }

    /** 候選只影響有節日的那一張；「現在」那一欄確實沒有節日外觀 */
    @Test
    fun candidatesOnlyChangeFestivalDays() {
        val now = still(widget(Festival.SPRING, Mood.RAIN, PageLayout.TALL, null))
        val a = still(widget(Festival.SPRING, Mood.RAIN, PageLayout.TALL, SmallEnv.FESTIVAL))
        assertEquals(false, now.sameAs(a))
        // 正式的小工具（P6-4c 整合後）：TALL 用定案的 B
        val cue = still(widget(Festival.SPRING, Mood.RAIN, PageLayout.TALL, SmallEnv.CUE))
        val real = still(CalendarWidget.build(context, Almanac.page(day(Festival.SPRING)), PageLayout.TALL).apply(context, FrameLayout(context)))
        assertEquals(true, real.sameAs(cue))
    }
}
