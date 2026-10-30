package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.FrameLayout
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalScene
import com.patty60723.tearoff.mowan.FestivalSession.Mode
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.time.LocalDate

/**
 * P7-1：V3 代表組合的 PNG golden（D-51）。故事卡、App 日曆頁、小工具 4 種版面 × 一般日子（晴、雨）、3 個節日（晴）、春節雨、中秋雨、第 1 頁、卸下包袱，
 * 用 Robolectric（NATIVE 繪圖，411dp xxhdpi）畫出來，跟 `src/test/resources/v3-png/` 的基準逐像素比。
 *
 * - 輸出：`build/screenshots/p7-golden/`（actual／diff／overview.png／report.md），CI 會推到截圖分支。
 * - [LOCKED] 是 false 時基準只是「候選」：不一致或缺少只寫進報告、不讓測試失敗。美術審核通過後才改成 true（不自行批准）。
 * - 這是 Robolectric 的繪圖（字型、抗鋸齒跟實機不同），只當回歸基準，不代表實機長相。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class V3PngGoldenTest {
    companion object {
        /** 人工審核通過前是 false（候選）；通過後改成 true，不一致就失敗 */
        const val LOCKED = false

        /** 每個顏色通道容許的差距（壓縮、四捨五入）；超過就算這個像素不同 */
        const val CHANNEL_TOLERANCE = 2
    }

    private val baseline = File("src/test/resources/v3-png")
    private val out = File("build/screenshots/p7-golden")
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        MowanSettings.setBundleOn(context, true)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    /** [page] 撕到第幾頁、[bundle] 包袱背著（預設第 14 頁、背著） */
    data class Condition(
        val id: String, val label: String, val day: LocalDate, val mood: Mood, val festival: Festival?,
        val page: Int = 14, val bundle: Boolean = true,
    )

    private fun firstDay(f: Festival) = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == f }

    /** 一般日子：2027-10-21（沒有節日） */
    private val normal = LocalDate.of(2027, 10, 21)
    private val conditions = listOf(
        Condition("normal-sunny", "一般・晴", normal, Mood.SUNNY, null),
        Condition("normal-rainy", "一般・雨", normal, Mood.RAIN, null),
        Condition("spring", "春節・晴", firstDay(Festival.SPRING), Mood.SUNNY, Festival.SPRING),
        Condition("moon", "中秋・晴", firstDay(Festival.MOON), Mood.SUNNY, Festival.MOON),
        Condition("xmas", "聖誕・晴", firstDay(Festival.XMAS), Mood.SUNNY, Festival.XMAS),
        // 2026-10-30 審核補充（候選）：放在最後，前面 30 張的畫法與順序不變
        Condition("spring-rainy", "春節・雨", firstDay(Festival.SPRING), Mood.RAIN, Festival.SPRING),
        Condition("moon-rainy", "中秋・雨", firstDay(Festival.MOON), Mood.RAIN, Festival.MOON),
        Condition("page1", "第 1 頁・晴", normal, Mood.SUNNY, null, page = 1),
        Condition("bundle-off", "卸下包袱・晴", normal, Mood.SUNNY, null, bundle = false),
    )
    private val surfaces = listOf("story" to "故事卡", "page" to "日曆頁") +
        PageLayout.entries.map { "widget-${it.name.lowercase()}" to "小工具 ${it.name}" }

    private fun shoot(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(paper); v.draw(this) } }

    /** 實際的小工具（版面的最小尺寸），撕到 [Condition.page] 頁 */
    private fun widget(c: Condition, layout: PageLayout): Bitmap {
        MowanSettings.setStoryCard(context, true)
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
        return shoot(v)
    }

    /** App 的日曆頁與故事卡：今天撕完，到 [Condition.page] 頁；故事卡在節日當天是主演出結束後的站姿（同一天第二種天氣用除錯重播） */
    private fun app(c: Condition): Pair<Bitmap, Bitmap> {
        MowanSettings.setStoryCard(context, true)
        WeatherService.setFakeMood(context, c.mood)
        AppClock.setFakeToday(context, c.day)
        MowanSettings.setBundleOn(context, c.bundle)
        StoryBook(context).apply { jumpTo(c.page - 1, c.day); tear(c.day) }
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val page = shoot(a.findViewById(R.id.page))
        val mowan = a.findViewById<MowanView>(R.id.story_mowan)
        mowan.visibleFraction = { 1f }
        mowan.windowFocused = { true }
        mowan.checkVisible()
        if (c.festival != null) {
            if (mowan.session.mode != Mode.MAIN) mowan.replayFestival()
            assertEquals("${c.id} 主演出", Mode.MAIN, mowan.session.mode)
            ShadowSystemClock.advanceBy(Duration.ofMillis(FestivalScene.spec(c.festival).main.durMs + 100L))
        }
        val card = mowan.parent as View
        return page to shoot(card)
    }

    private fun render(): Map<String, Bitmap> {
        val shots = LinkedHashMap<String, Bitmap>()
        for (c in conditions) {
            assertEquals("${c.id} 的日期", c.festival, FestivalTheme.of(c.day))
            val (page, story) = app(c)
            shots["story-${c.id}"] = story
            shots["page-${c.id}"] = page
            for (l in PageLayout.entries) shots["widget-${l.name.lowercase()}-${c.id}"] = widget(c, l)
        }
        return shots
    }

    data class Diff(val pixels: Int, val maxChannel: Int, val image: Bitmap?)

    private fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    private fun diff(e: Bitmap, a: Bitmap): Diff {
        if (e.width != a.width || e.height != a.height) return Diff(-1, -1, null)
        val ep = pixels(e)
        val ap = pixels(a)
        val out = IntArray(ep.size)
        var n = 0
        var max = 0
        for (i in ep.indices) {
            val p = ep[i]
            val q = ap[i]
            var m = 0
            for (s in intArrayOf(0, 8, 16, 24)) m = maxOf(m, Math.abs(((p shr s) and 0xFF) - ((q shr s) and 0xFF)))
            max = maxOf(max, m)
            if (m > CHANNEL_TOLERANCE) n++
            // 不同的像素標紅，一樣的淡淡顯示原圖
            out[i] = if (m > CHANNEL_TOLERANCE) 0xFFE0301E.toInt() else (p and 0x00FFFFFF) or 0x30000000
        }
        return Diff(n, max, Bitmap.createBitmap(out, e.width, e.height, Bitmap.Config.ARGB_8888))
    }

    private fun save(b: Bitmap, f: File) {
        f.parentFile!!.mkdirs()
        f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 跟存下來再讀回來的一樣（PNG 是無損的，但保險起見比的是讀回來的） */
    private fun reloaded(f: File) = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inScaled = false })

    @Test
    fun representativeCombinations() {
        val shots = render()
        assertEquals(conditions.size * surfaces.size, shots.size)
        val report = StringBuilder()
        report.append("# V3 PNG golden 比對報告\n\n")
        report.append("狀態：**${if (LOCKED) "已鎖定（不一致就失敗）" else "候選（人工審核前不鎖定，不一致只記錄）"}**。")
        report.append("Robolectric NATIVE 繪圖、411dp xxhdpi；通道容許差距 $CHANNEL_TOLERANCE。\n\n")
        report.append("| 圖 | 大小（px） | 結果 |\n|---|---|---|\n")
        val problems = mutableListOf<String>()
        for ((name, b) in shots) {
            val actual = File(out, "actual/$name.png").also { save(b, it) }
            val now = reloaded(actual)
            assertTrue("$name 不是空白", pixels(now).any { it != paper })
            val base = File(baseline, "$name.png")
            val result = if (!base.exists()) {
                problems += "$name 沒有基準"
                "沒有基準（新候選）"
            } else {
                val d = diff(reloaded(base), now)
                when {
                    d.pixels < 0 -> { problems += "$name 大小不同"; "**大小不同**" }
                    d.pixels == 0 -> "一致（最大通道差 ${d.maxChannel}）"
                    else -> {
                        save(d.image!!, File(out, "diff/$name.png"))
                        problems += "$name 有 ${d.pixels} 個像素不同"
                        "**${d.pixels} 個像素不同**（最大通道差 ${d.maxChannel}，見 diff/$name.png）"
                    }
                }
            }
            report.append("| $name | ${now.width}×${now.height} | $result |\n")
        }
        report.append("\n${if (problems.isEmpty()) "全部一致。" else "不一致：${problems.size} 張。"}\n")
        File(out, "report.md").writeText(report.toString())
        overview(shots)
        if (LOCKED) assertNull("PNG golden 不一致：$problems", problems.ifEmpty { null })
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 34f }

    /** 總覽：每一列是一種情況，每一欄是一個畫面（縮到同樣高度，只給人看，不拿來比） */
    private fun overview(shots: Map<String, Bitmap>) {
        val cellH = 520
        val scaled = shots.mapValues { (_, b) -> Bitmap.createScaledBitmap(b, b.width * cellH / b.height, cellH, true) }
        val colW = surfaces.map { (s, _) -> conditions.maxOf { scaled.getValue("$s-${it.id}").width } + 24 }
        val left = 200
        val top = 70
        val sheet = Bitmap.createBitmap(left + colW.sum(), top + (cellH + 24) * conditions.size, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            var x = left
            surfaces.forEachIndexed { i, (s, label) ->
                drawText(label, x.toFloat(), 46f, words)
                conditions.forEachIndexed { r, c -> drawBitmap(scaled.getValue("$s-${c.id}"), x.toFloat(), (top + r * (cellH + 24)).toFloat(), null) }
                x += colW[i]
            }
            conditions.forEachIndexed { r, c -> drawText(c.label, 16f, (top + r * (cellH + 24) + cellH / 2).toFloat(), words) }
        }
        save(sheet, File(out, "overview.png"))
    }
}
