package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.patty60723.tearoff.mowan.ArtConfig
import com.patty60723.tearoff.mowan.Detail
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.Mowan.Weather
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.story.StoryEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * P6-2 對照圖：節日服裝與道具的靜態畫法，給美術審查。存到 app/build/screenshots/p6（CI 推到截圖分支）。
 * 不是比對用的測試；規則與原型比對在 FestivalRenderTest、FestivalGoldenTest。
 * 角色都是靜止站姿（沒有演出，演出在 P6-3），筆觸影格 0。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FestivalShotsTest {
    private val out = File("build/screenshots/p6").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val beats by lazy { StoryBook(context).beats }
    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.INK; textSize = 22f }
    private val small = Paint(label).apply { textSize = 17f }
    private val name = mapOf(Festival.SPRING to "春節", Festival.XMAS to "聖誕", Festival.MOON to "中秋")
    private val weatherName = mapOf(
        Weather.NONE to "一般", Weather.SUNNY to "晴", Weather.RAINY to "雨", Weather.HOT to "熱", Weather.CLOUDY to "陰",
        Weather.FOG to "霧", Weather.THUNDER to "雷雨", Weather.COLD to "冷", Weather.SNOW to "雪",
    )
    private val clockOf = mapOf(Weather.RAINY to 1050.0, Weather.THUNDER to 1050.0)

    /** 角色畫成 [h] px 高（寬 = 高 × 3/4） */
    private fun render(
        h: Int, festival: Festival?, weather: Weather = Weather.NONE, page: Int = 14, hatOn: Boolean = true, bundleOn: Boolean = true,
        art: ArtConfig = ArtConfig.DEFAULT, detail: Detail = Detail.FULL, bg: Int = Mowan.IVORY,
    ): Bitmap {
        val frame = Mowan.frame(parts(page), 0, hatOn, weather, clockOf[weather] ?: 0.0, 8, still = true, bundleOn = bundleOn, detail = detail, festival = festival, art = art)
        val bw = h * 3 / 4
        return Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(bg); MowanPainter(frame).draw(this, 0f, 0f, bw.toFloat(), h.toFloat()) }
        }
    }

    private fun zoom(b: Bitmap, h: Int) = Bitmap.createScaledBitmap(b, b.width * h / b.height, h, false)

    private class Sheet(val w: Int, val h: Int) {
        val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(0xFFEFEAE1.toInt()) }
    }

    private fun save(name: String, b: Bitmap) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    /** 一張表：列 × 欄，每格一張圖，上面有欄名、左邊有列名 */
    private fun grid(file: String, cols: List<String>, rows: List<String>, cellW: Int, cellH: Int, left: Int = 120, cell: (Int, Int) -> Bitmap?) {
        val sheet = Sheet(left + cols.size * (cellW + 8), 44 + rows.size * (cellH + 10))
        cols.forEachIndexed { i, c -> sheet.canvas.drawText(c, left + i * (cellW + 8) + 4f, 30f, small) }
        rows.forEachIndexed { r, rowName ->
            val y = 44 + r * (cellH + 10)
            rowName.split("\n").forEachIndexed { k, line -> sheet.canvas.drawText(line, 10f, y + cellH / 2f + k * 22, label) }
            cols.indices.forEach { i -> cell(r, i)?.let { sheet.canvas.drawBitmap(it, left + i * (cellW + 8f), y.toFloat(), null) } }
        }
        save(file, sheet.bitmap)
    }

    /** 1. 三個節日的完整角色（第 14 頁，一般天氣），左邊是沒有節日的對照 */
    @Test
    fun festivals() {
        val list = listOf<Festival?>(null) + Festival.entries
        grid("1-festivals", list.map { if (it == null) "沒有節日" else name.getValue(it) }, listOf("第 14 頁"), 360, 480) { _, i -> render(480, list[i]) }
    }

    /** 2. 頭飾 A／B／C 與玉兔耳朵 A／B（第 14 頁，放大到頭部），下面一列是撐傘時（看頭飾與傘的間距） */
    @Test
    fun headwear() {
        class Col(val text: String, val f: Festival, val art: ArtConfig)
        val cols = ArtConfig.HatStyle.entries.map { Col("財神帽 $it", Festival.SPRING, ArtConfig(hat = it)) } +
            ArtConfig.HatStyle.entries.map { Col("聖誕帽 $it", Festival.XMAS, ArtConfig(hat = it)) } +
            ArtConfig.EarStyle.entries.map { Col("玉兔耳朵 $it", Festival.MOON, ArtConfig(ears = it)) }
        val h = 960
        fun head(c: Col, w: Weather): Bitmap {
            val b = render(h, c.f, w, art = c.art)
            // 頭部範圍：viewBox x 20–100、y −16–76（加上傘時 y 從更上面開始）
            val k = h / 160.0
            val top = if (w == Weather.RAINY) -20.0 else -16.0
            val rect = Rect((20 * k).toInt(), ((top + 20) * k).toInt(), (100 * k).toInt(), ((76 + 20) * k).toInt())
            return Bitmap.createBitmap(b, rect.left, rect.top, rect.width(), rect.height()).let { Bitmap.createScaledBitmap(it, 240, 276, true) }
        }
        grid("2-headwear", cols.map { it.text }, listOf("一般", "雨（撐傘）"), 240, 276) { r, i -> head(cols[i], if (r == 0) Weather.NONE else Weather.RAINY) }
    }

    /** 3. 三個節日 × 8 種天氣（第 14 頁） */
    @Test
    fun weathers() {
        val ws = Weather.entries
        grid("3-weathers", ws.map { weatherName.getValue(it) }, Festival.entries.map { name.getValue(it) }, 180, 240, left = 70) { r, i -> render(240, Festival.entries[r], ws[i]) }
    }

    /** 4. 成長階段與道具降級：第 1、2、3、4、5、6、8、11、14 頁 × 晴、雨 */
    @Test
    fun growth() {
        val pages = listOf(1, 2, 3, 4, 5, 6, 8, 11, 14)
        val rows = Festival.entries.flatMap { f -> listOf(f to Weather.SUNNY, f to Weather.RAINY) }
        grid("4-growth", pages.map { "第 $it 頁" }, rows.map { (f, w) -> "${name[f]}\n${weatherName[w]}" }, 150, 200, left = 80) { r, i ->
            render(200, rows[r].first, rows[r].second, pages[i])
        }
    }

    /** 5. 包袱、雨傘、節日道具的重疊：背包袱／卸下包袱 × 晴、雨、雷雨（放大到上半身） */
    @Test
    fun overlap() {
        class Col(val text: String, val w: Weather, val bundle: Boolean, val umb: Double = 1.0)
        val cols = listOf(
            Col("晴 背包袱", Weather.SUNNY, true), Col("晴 卸下", Weather.SUNNY, false),
            Col("雨 背包袱", Weather.RAINY, true), Col("雨 卸下", Weather.RAINY, false),
            Col("雷雨 背包袱", Weather.THUNDER, true), Col("雨 傘縮小 0.95", Weather.RAINY, true, ArtConfig.UMBRELLA_SMALL),
        )
        val h = 800
        grid("5-overlap", cols.map { it.text }, Festival.entries.map { name.getValue(it) }, 270, 330, left = 70) { r, i ->
            val c = cols[i]
            val b = render(h, Festival.entries[r], c.w, bundleOn = c.bundle, art = ArtConfig(umbrellaScale = c.umb))
            val k = h / 160.0
            // viewBox x 10–118、y 10–104：頭、包袱、手、道具、襪子
            val rect = Rect((10 * k).toInt(), (30 * k).toInt(), (118 * k).toInt(), (124 * k).toInt())
            Bitmap.createScaledBitmap(Bitmap.createBitmap(b, rect.left, rect.top, rect.width(), rect.height()), 270, 235, true)
        }
    }

    /**
     * 6. 48／72／96 px 的實際大小：上面是真的像素大小（不放大），下面是同一張最近鄰放大到 240 px 高。
     * 小工具裡角色都低於 72 dp，所以三種都用簡化版（D-25）；最右邊是 160 px 完整版參考。
     */
    @Test
    fun sizes() {
        val sizes = listOf(48, 72, 96)
        val rows = Festival.entries.flatMap { f -> listOf(f to Weather.SUNNY, f to Weather.RAINY) }
        val sheet = Sheet(110 + (sizes.size * 2 + 1) * 190, 44 + rows.size * 250)
        val heads = sizes.map { "$it px" } + sizes.map { "$it px 放大" } + "160 px 完整"
        heads.forEachIndexed { i, t -> sheet.canvas.drawText(t, 110 + i * 190 + 4f, 30f, small) }
        rows.forEachIndexed { r, (f, w) ->
            val y = 44 + r * 250
            sheet.canvas.drawText("${name[f]}", 10f, y + 110f, label)
            sheet.canvas.drawText("${weatherName[w]}", 10f, y + 134f, label)
            sizes.forEachIndexed { i, s ->
                val b = render(s, f, w, detail = Detail.simplified(s.toDouble()))
                sheet.canvas.drawBitmap(b, 110 + i * 190f, y.toFloat(), null)
                sheet.canvas.drawBitmap(zoom(b, 240), 110 + (sizes.size + i) * 190f, y.toFloat(), null)
            }
            sheet.canvas.drawBitmap(render(240, f, w), 110 + sizes.size * 2 * 190f, y.toFloat(), null)
        }
        save("6-sizes", sheet.bitmap)
    }

    /**
     * 7. 小工具小尺寸的背景候選（D-16，未定案）：天氣與節日背景衝突的組合 × 三種取捨。
     * 角色 64 dp 高（小工具 TALL，xxhdpi 192 px）、簡化版，放大 1.5 倍。
     */
    @Test
    fun widgetCandidates() {
        val cases = listOf(
            Festival.XMAS to Weather.RAINY, Festival.XMAS to Weather.SNOW, Festival.SPRING to Weather.THUNDER, Festival.SPRING to Weather.FOG,
            Festival.MOON to Weather.CLOUDY, Festival.MOON to Weather.FOG, Festival.MOON to Weather.SUNNY, Festival.MOON to Weather.RAINY,
        )
        val envs = listOf(ArtConfig.SmallEnv.FESTIVAL to "A 節日背景優先", ArtConfig.SmallEnv.WEATHER to "B 天氣環境優先", ArtConfig.SmallEnv.BOTH to "C 兩個都留 （對照）")
        grid("7-widget-candidates", cases.map { (f, w) -> "${name[f]}・${weatherName[w]}" }, envs.map { it.second.replace(" ", "\n") }, 216, 288, left = 140) { r, i ->
            val (f, w) = cases[i]
            zoom(render(192, f, w, detail = Detail.simplified(192.0), art = ArtConfig(smallEnv = envs[r].first), bg = 0xFFFAF7F0.toInt()), 288)
        }
    }

    /** 8. 道具的手剪紙質感（放大）：春聯「春」、紅包、月餅、襪子、禮物、月亮 */
    @Test
    fun props() {
        class Crop(val text: String, val f: Festival, val w: Weather, val x0: Double, val y0: Double, val x1: Double, val y1: Double)
        val crops = listOf(
            Crop("斗方春聯「春」", Festival.SPRING, Weather.NONE, 0.0, 10.0, 28.0, 40.0),
            Crop("紅包（右手）", Festival.SPRING, Weather.NONE, 64.0, 82.0, 82.0, 104.0),
            Crop("月餅（左手）", Festival.MOON, Weather.NONE, 38.0, 80.0, 58.0, 98.0),
            Crop("襪子", Festival.XMAS, Weather.NONE, 20.0, 54.0, 42.0, 80.0),
            Crop("禮物", Festival.XMAS, Weather.NONE, 82.0, 110.0, 106.0, 132.0),
            Crop("月亮（晴）", Festival.MOON, Weather.SUNNY, 84.0, -8.0, 112.0, 20.0),
            Crop("月亮（陰）", Festival.MOON, Weather.CLOUDY, 76.0, -10.0, 118.0, 30.0),
            Crop("月亮（霧）", Festival.MOON, Weather.FOG, 76.0, -10.0, 118.0, 30.0),
        )
        val h = 1600
        val k = h / 160.0
        grid("8-props", crops.map { it.text }, listOf("放大"), 260, 260, left = 70) { _, i ->
            val c = crops[i]
            val b = render(h, c.f, c.w)
            val rect = Rect((c.x0 * k).toInt(), ((c.y0 + 20) * k).toInt(), (c.x1 * k).toInt(), ((c.y1 + 20) * k).toInt())
            Bitmap.createScaledBitmap(Bitmap.createBitmap(b, rect.left, rect.top, rect.width(), rect.height()), 260, 260 * rect.height() / rect.width(), true)
        }
    }
}
