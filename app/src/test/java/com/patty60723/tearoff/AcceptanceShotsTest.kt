package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.widget.TextView
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.Mowan.Weather
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanTimeline
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * W7 驗收素材：小尺寸對照、成長頁 × 天氣、圖層放大、不同密度與字體大小的故事卡、動畫影格。
 * 存到 app/build/screenshots/w7，CI 會把 frames/ 裡的影格轉成 GIF。這不是比對用的測試。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AcceptanceShotsTest {
    private val out = File("build/screenshots/w7").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val beats by lazy { StoryBook(context).beats }

    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    private val weathers = listOf(
        "晴" to Weather.SUNNY, "雨" to Weather.RAINY, "熱" to Weather.HOT, "陰" to Weather.CLOUDY,
        "霧" to Weather.FOG, "雷雨" to Weather.THUNDER, "冷" to Weather.COLD, "雪" to Weather.SNOW,
    )

    /** 每種天氣動作中的代表時間（晴是伸懶腰最高、冷是抱身發抖時） */
    private fun busyClock(w: Weather) = when (w) {
        Weather.SUNNY -> 2750.0
        Weather.RAINY -> 1050.0
        Weather.HOT -> 1050.0
        Weather.CLOUDY -> 1500.0
        Weather.FOG -> 2500.0
        Weather.THUNDER -> 2100.0
        Weather.COLD -> 1150.0
        Weather.SNOW -> 2000.0
        Weather.NONE -> 0.0
    }

    private fun painter(page: Int, w: Weather, t: Double, still: Boolean = false, since: Double? = null, bundleOn: Boolean = true, hatOn: Boolean = true) =
        MowanPainter(Mowan.frame(parts(page), 0, hatOn, w, t, Mowan.rainStepAt(t.toLong()), sinceStrikeMs = since, still = still, bundleOn = bundleOn))

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.INK; textSize = 18f }

    private fun save(name: String, bitmap: Bitmap) =
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        MowanSettings.setBundleOn(context, true)
        WeatherService.setFakeMood(context, null)
        RuntimeEnvironment.setFontScale(1f)
    }

    /**
     * 8 種天氣在角色高度 160、96、48 px 的樣子（真實像素，沒有縮放）。
     * 48 px 另外用最近鄰放大 4 倍，方便看像素；最後兩欄是 96 px 的靜止畫面（關閉動畫）與卸下包袱。
     */
    @Test
    fun sizes() {
        val sizes = listOf(160, 96, 48)
        val colW = listOf(130, 90, 60, 160, 90, 90)
        val rowH = 220
        val top = 40
        val bitmap = Bitmap.createBitmap(110 + colW.sum(), top + rowH * weathers.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Mowan.IVORY)
        val heads = listOf("160 px", "96 px", "48 px", "48 px ×4", "96 靜止", "96 卸包袱")
        var x = 110f
        heads.forEachIndexed { i, h -> canvas.drawText(h, x + 4, 26f, label); x += colW[i] }
        weathers.forEachIndexed { r, (name, w) ->
            val y = top + r * rowH.toFloat()
            canvas.drawText(name, 12f, y + 100, label)
            var cx = 110f
            sizes.forEachIndexed { i, s ->
                painter(14, w, busyClock(w)).draw(canvas, cx, y, s * 0.75f, s.toFloat())
                cx += colW[i]
            }
            // 48 px 畫成真的 36×48 點陣圖，再最近鄰放大
            val small = Bitmap.createBitmap(36, 48, Bitmap.Config.ARGB_8888)
            Canvas(small).apply { drawColor(Mowan.IVORY); painter(14, w, busyClock(w)).draw(this, 0f, 0f, 36f, 48f) }
            canvas.drawBitmap(Bitmap.createScaledBitmap(small, 144, 192, false), cx, y, null)
            cx += colW[3]
            painter(14, w, MowanTimeline.stillClock(w), still = true).draw(canvas, cx, y, 72f, 96f)
            cx += colW[4]
            painter(14, w, busyClock(w), bundleOn = false).draw(canvas, cx, y, 72f, 96f)
        }
        save("sizes", bitmap)
    }

    /** 成長限制：第 1、3、4、5、8、13、14 頁 × 8 種天氣（角色高 160 px） */
    @Test
    fun growthByWeather() {
        val pages = listOf(1, 3, 4, 5, 8, 13, 14)
        val cw = 130
        val ch = 190
        val bitmap = Bitmap.createBitmap(70 + cw * pages.size, 36 + ch * weathers.size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Mowan.IVORY)
        pages.forEachIndexed { c, p -> canvas.drawText("第 $p 頁", 70f + c * cw + 30, 26f, label) }
        weathers.forEachIndexed { r, (name, w) ->
            val y = 36f + r * ch
            canvas.drawText(name, 12f, y + 90, label)
            pages.forEachIndexed { c, p -> painter(p, w, busyClock(w)).draw(canvas, 70f + c * cw, y + 10, 120f, 160f) }
        }
        save("growth", bitmap)
    }

    /**
     * 雨傘、紙帽、圍巾、包袱重疊的地方放大 4 倍。包含三項 W7 Minor：
     * 冷天抱身、雷雨紙帽與傘的間距、晴天伸懶腰時包袱靠近腋下。
     */
    @Test
    fun layersZoomed() {
        class Pose(val text: String, val w: Weather, val t: Double, val since: Double? = null, val hatOn: Boolean = true)
        val poses = listOf(
            Pose("晴：開始伸", Weather.SUNNY, 1500.0), Pose("晴：伸到最高", Weather.SUNNY, 2750.0), Pose("晴：放下", Weather.SUNNY, 4000.0),
            Pose("雨：撐傘", Weather.RAINY, 1050.0), Pose("雷雨：抱傘", Weather.THUNDER, 2100.0), Pose("雷雨：嚇一跳", Weather.THUNDER, 5150.0, since = 150.0),
            Pose("冷：抱身", Weather.COLD, 1150.0), Pose("冷：靜止", Weather.COLD, 2300.0), Pose("雪", Weather.SNOW, 2000.0),
        )
        val cell = 480
        val cols = 3
        val bitmap = Bitmap.createBitmap(cell * cols, cell * 4 / 3 * ((poses.size + cols - 1) / cols), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Mowan.IVORY)
        val big = Paint(label).apply { textSize = 28f }
        poses.forEachIndexed { i, p ->
            val x = (i % cols) * cell.toFloat()
            val y = (i / cols) * cell * 4 / 3f
            painter(14, p.w, p.t, since = p.since, hatOn = p.hatOn).draw(canvas, x, y, cell.toFloat(), cell * 4 / 3f)
            canvas.drawText(p.text, x + 12, y + 36, big)
        }
        save("layers-zoom", bitmap)
    }

    /** 故事卡在不同螢幕密度、字體大小、窄螢幕下（第 14 頁、晴天）。包袱入口要完整顯示、按得到，這部分是真的檢查 */
    @Test
    fun storyCardDensities() {
        MowanSettings.setStoryCard(context, true)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        StoryBook(context).jumpTo(14, AppClock.today(context))
        val configs = listOf(
            "w411dp-h891dp-mdpi" to 1f, "w411dp-h891dp-hdpi" to 1f, "w411dp-h891dp-xxhdpi" to 1f,
            "w411dp-h891dp-xxxhdpi" to 1f, "w411dp-h891dp-xxhdpi" to 1.3f, "w411dp-h891dp-xxhdpi" to 2f,
            "w320dp-h640dp-xhdpi" to 1.3f,
        )
        for ((q, font) in configs) {
            RuntimeEnvironment.setQualifiers(q)
            RuntimeEnvironment.setFontScale(font)
            val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
            val card = a.findViewById<View>(R.id.story_mowan).parent as View
            // 包袱入口：一行、沒有被截斷、不跟故事文字重疊、在卡片裡、按得到（至少 48dp 高）
            val bundle = a.findViewById<TextView>(R.id.story_bundle)
            val text = a.findViewById<TextView>(R.id.story_text)
            val density = a.resources.displayMetrics.density
            val at = "$q 字體 $font"
            assertEquals(at, View.VISIBLE, bundle.visibility)
            assertEquals(at, 1, bundle.lineCount)
            assertEquals(at, 0, bundle.layout.getEllipsisCount(0))
            assertTrue(at, bundle.layout.getLineWidth(0) <= bundle.width - bundle.totalPaddingLeft - bundle.totalPaddingRight + 0.5f)
            assertTrue(at, bundle.top >= text.bottom)
            assertTrue(at, bundle.height >= (48 * density).toInt() - 1)
            val inCard = IntArray(2).also { bundle.getLocationInWindow(it) }[0] + bundle.width <=
                IntArray(2).also { card.getLocationInWindow(it) }[0] + card.width
            assertTrue(at, inCard)
            val bitmap = Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply { drawColor(0xFFEFEAE1.toInt()); card.draw(this) }
            val dpi = q.substringAfterLast('-')
            val width = q.substringBefore('-')
            save("story-card-$width-$dpi-font${(font * 100).toInt()}", bitmap)
        }
    }

    /** 故事卡上的包袱入口：第 13 頁、第 14 頁背著、點一下卸下、重開 App */
    @Test
    fun storyCardBundle() {
        MowanSettings.setStoryCard(context, true)
        MowanSettings.setBundleOn(context, true)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        fun shot(a: MainActivity): Bitmap {
            val card = a.findViewById<View>(R.id.story_mowan).parent as View
            return Bitmap.createBitmap(card.width, card.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEFEAE1.toInt()); card.draw(this) } }
        }
        fun open(page: Int): MainActivity {
            StoryBook(context).jumpTo(page, AppClock.today(context))
            return Robolectric.buildActivity(MainActivity::class.java).setup().get()
        }
        val shots = mutableListOf<Bitmap>()
        shots += shot(open(13))
        val a = open(14)
        shots += shot(a)
        a.findViewById<TextView>(R.id.story_bundle).performClick()
        shots += shot(a)
        shots += shot(open(14))
        val w = shots.maxOf { it.width }
        val h = shots.maxOf { it.height }
        val sheet = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFEFEAE1.toInt())
            shots.forEachIndexed { i, b -> drawBitmap(b, (i % 2) * w.toFloat(), (i / 2) * h.toFloat(), null) }
        }
        save("story-card-bundle", sheet)
    }

    /**
     * 動畫影格（15 FPS、240×320），跟 MowanView.onDraw 一樣從 [MowanTimeline] 取樣。CI 會把每個資料夾轉成 GIF。
     * 雷雨為了示範每 2 秒打一次雷（App 裡是隨機 15–40 秒）；包袱那段在 2 秒卸下、4 秒背上，時鐘不重來。
     */
    @Test
    fun animationFrames() {
        val fps = 15
        fun record(name: String, w: Weather, seconds: Double, firstStrikeMs: Long? = null, bundleAt: (Double) -> Boolean = { true }) {
            val random = object : Random() {
                override fun nextBits(bitCount: Int) = 0
                override fun nextLong(from: Long, until: Long) = firstStrikeMs ?: from
            }
            val timeline = MowanTimeline(random).also { it.setWeather(w, 0) }
            val dir = File(out, "frames/$name").apply { deleteRecursively(); mkdirs() }
            val count = (seconds * fps).toInt()
            for (i in 0 until count) {
                val now = i * 1000L / fps
                val s = timeline.sample(now, false)
                val bundleOn = bundleAt(now.toDouble())
                val frame = Mowan.frame(parts(14), s.variant, true, w, s.clockMs, s.rainStep, sinceStrikeMs = s.sinceStrikeMs, bundleOn = bundleOn)
                val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Mowan.IVORY)
                MowanPainter(frame).draw(canvas, 0f, 0f, 240f, 320f)
                val tag = "%.1f s".format(now / 1000.0) + if (name == "bundle") (if (bundleOn) " 背著" else " 卸下") else ""
                canvas.drawText(tag, 8f, 312f, label)
                File(dir, "%04d.png".format(i)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        record("sunny", Weather.SUNNY, 5.5 * 2)
        record("rainy", Weather.RAINY, 4.2 * 2)
        record("hot", Weather.HOT, 3.2 * 2)
        record("cloudy", Weather.CLOUDY, 6.0 * 2)
        record("fog", Weather.FOG, 5.0 * 2)
        record("thunder", Weather.THUNDER, 5.0, firstStrikeMs = 2000)
        record("cold", Weather.COLD, 2.8 * 3)
        record("snow", Weather.SNOW, 4.0 * 2)
        record("bundle", Weather.SUNNY, 6.0, bundleAt = { it < 2000 || it >= 4000 })
    }
}
