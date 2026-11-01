package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.patty60723.tearoff.mowan.Detail
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.Mowan.Weather
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.mowan.MowanTimeline
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
 * P5-2 對照圖：小尺寸的簡化畫法。存到 app/build/screenshots/p5（CI 推到 screenshots 分支）。不是比對用的測試。
 * 每一格都是「真的畫成那個像素大小」，再用最近鄰放大，看得到每個像素。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SimplifiedShotsTest {
    private val out = File("build/screenshots/p5").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val beats by lazy { StoryBook(context).beats }
    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.INK; textSize = 20f }
    private val small = Paint(label).apply { textSize = 16f }

    private val weathers = listOf(
        "一般" to Weather.NONE, "晴" to Weather.SUNNY, "雨" to Weather.RAINY, "熱" to Weather.HOT, "陰" to Weather.CLOUDY,
        "霧" to Weather.FOG, "雷雨" to Weather.THUNDER, "冷" to Weather.COLD, "雪" to Weather.SNOW,
    )

    /** 角色畫成 [h] px 高（寬 = 高 × 3/4）的點陣圖，用靜止代表姿勢 */
    private fun render(h: Int, detail: Detail, w: Weather = Weather.SUNNY, page: Int = 14, hatOn: Boolean = true, bundleOn: Boolean = true): Bitmap {
        val frame = Mowan.frame(parts(page), 0, hatOn, w, MowanTimeline.stillClock(w), still = true, bundleOn = bundleOn, detail = detail)
        val bw = h * 3 / 4
        return Bitmap.createBitmap(bw, h, Bitmap.Config.ARGB_8888).also {
            Canvas(it).apply { drawColor(Mowan.IVORY); MowanPainter(frame).draw(this, 0f, 0f, bw.toFloat(), h.toFloat()) }
        }
    }

    /** 最近鄰放大到 [h] px 高 */
    private fun zoom(b: Bitmap, h: Int) = Bitmap.createScaledBitmap(b, b.width * h / b.height, h, false)

    private fun save(name: String, b: Bitmap) = File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private class Sheet(val w: Int, val h: Int) {
        val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(0xFFEFEAE1.toInt()) }
    }

    /** 1. 48／72／96／120／160 px：上排完整版、下排簡化版，都放大到 192 px 高 */
    @Test
    fun sizes() {
        val sizes = listOf(48, 72, 96, 120, 160)
        val cell = 192
        val sheet = Sheet(110 + sizes.size * 160, 40 + 2 * (cell + 30))
        sizes.forEachIndexed { i, h -> sheet.canvas.drawText("$h px", 110f + i * 160 + 40, 28f, label) }
        listOf("完整" to { h: Int -> Detail.FULL }, "簡化" to { h: Int -> Detail.simplified(h.toDouble()) }).forEachIndexed { r, (name, d) ->
            val y = 40f + r * (cell + 30)
            sheet.canvas.drawText(name, 16f, y + cell / 2, label)
            sizes.forEachIndexed { i, h -> sheet.canvas.drawBitmap(zoom(render(h, d(h)), cell), 110f + i * 160, y, null) }
        }
        save("sizes", sheet.bitmap)
    }

    /** 2 + 5. 每種天氣：完整版、簡化版並排（48 px 與 72 px，放大 4 倍／約 2.7 倍；最右邊是 160 px 的完整版參考） */
    @Test
    fun weathersBeforeAfter() {
        val cell = 192
        val cols = listOf("48 完整", "48 簡化", "72 完整", "72 簡化", "160 完整")
        val sheet = Sheet(90 + cols.size * 156, 40 + weathers.size * (cell + 8))
        cols.forEachIndexed { i, c -> sheet.canvas.drawText(c, 90f + i * 156 + 20, 28f, label) }
        weathers.forEachIndexed { r, (name, w) ->
            val y = 40f + r * (cell + 8)
            sheet.canvas.drawText(name, 16f, y + cell / 2, label)
            val cells = listOf(
                render(48, Detail.FULL, w), render(48, Detail.simplified(48.0), w),
                render(72, Detail.FULL, w), render(72, Detail.simplified(72.0), w),
                render(160, Detail.FULL, w),
            )
            cells.forEachIndexed { i, b -> sheet.canvas.drawBitmap(zoom(b, cell), 90f + i * 156, y, null) }
        }
        save("weathers-before-after", sheet.bitmap)
    }

    /** 3. 紙帽、圍巾、包袱的有無：第 9、10、11、14 頁，第 14 頁脫下紙帽、卸下包袱（48 px 與 96 px 簡化） */
    @Test
    fun equipment() {
        class Col(val text: String, val page: Int, val hatOn: Boolean = true, val bundleOn: Boolean = true)
        val cols = listOf(
            Col("第 9 頁", 9), Col("第 10 頁 圍巾", 10), Col("第 11 頁 紙帽", 11), Col("第 14 頁 包袱", 14),
            Col("脫下紙帽", 14, hatOn = false), Col("卸下包袱", 14, bundleOn = false),
        )
        val cell = 192
        val sheet = Sheet(90 + cols.size * 156, 40 + 2 * (cell + 8))
        cols.forEachIndexed { i, c -> sheet.canvas.drawText(c.text, 90f + i * 156 + 4, 28f, small) }
        listOf(48, 96).forEachIndexed { r, h ->
            val y = 40f + r * (cell + 8)
            sheet.canvas.drawText("$h px", 16f, y + cell / 2, label)
            cols.forEachIndexed { i, c ->
                sheet.canvas.drawBitmap(zoom(render(h, Detail.simplified(h.toDouble()), Weather.RAINY, c.page, c.hatOn, c.bundleOn), cell), 90f + i * 156, y, null)
            }
        }
        save("equipment", sheet.bitmap)
    }

    /**
     * 4. 同一個視覺大小在不同密度：小工具 TALL（64 dp 高）與 COMPACT（48 dp 高）在 mdpi–xxhdpi。
     * 每一格先畫成真的像素大小，再縮放到同樣的顯示大小（就是在手機上看到的實際大小比例）。
     * 上兩列照舊的暫定值 120 px 判斷（比較用）；下兩列照 D-25 確認的 72 dp。
     */
    @Test
    fun densities() {
        val densities = listOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0)
        val rules = listOf("120 px" to Detail.Rule.ByPx(120.0), "72 dp" to Detail.Rule.ByDp(72.0))
        val dps = listOf(48, 64)
        val cell = 192
        val sheet = Sheet(170 + densities.size * 156, 40 + rules.size * dps.size * (cell + 8))
        densities.forEachIndexed { i, (q, _) -> sheet.canvas.drawText(q, 170f + i * 156 + 40, 28f, label) }
        var r = 0
        for ((ruleName, rule) in rules) for (dp in dps) {
            val y = 40f + r++ * (cell + 8)
            sheet.canvas.drawText("$dp dp", 16f, y + cell / 2 - 12, label)
            sheet.canvas.drawText("門檻 $ruleName", 16f, y + cell / 2 + 16, small)
            densities.forEachIndexed { i, (_, d) ->
                val h = (dp * d).toInt()
                val detail = Detail.of(h.toDouble(), d, rule)
                val b = render(h, detail, Weather.RAINY)
                sheet.canvas.drawBitmap(Bitmap.createScaledBitmap(b, b.width * cell / b.height, cell, true), 170f + i * 156, y, null)
                sheet.canvas.drawText("${h}px ${if (detail.simplified) "簡化" else "完整"}", 170f + i * 156 + 4, y + cell - 4, small)
            }
        }
        save("densities", sheet.bitmap)
    }

    /** 實際大小（不放大）：每種天氣 48／72／96 px 的簡化版，看真實像素的樣子 */
    @Test
    fun actualPixels() {
        val sizes = listOf(48, 72, 96)
        val sheet = Sheet(20 + weathers.size * 80, 40 + sizes.sum() + sizes.size * 10)
        weathers.forEachIndexed { i, (name, _) -> sheet.canvas.drawText(name, 20f + i * 80, 28f, small) }
        var y = 40f
        for (h in sizes) {
            weathers.forEachIndexed { i, (_, w) -> sheet.canvas.drawBitmap(render(h, Detail.simplified(h.toDouble()), w), 20f + i * 80, y, null) }
            y += h + 10
        }
        save("actual-pixels", sheet.bitmap)
    }
}
