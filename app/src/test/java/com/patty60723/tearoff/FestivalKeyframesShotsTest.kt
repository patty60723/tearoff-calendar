package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalActions
import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalScene
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
 * P6-3 動態審核補件：用既有的演出資料輸出關鍵影格拼圖（不改任何動作）。
 * 每張圖上排是全身、下排是上半身放大，每格標時間（ms）。存到 app/build/screenshots/p6/keyframes。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FestivalKeyframesShotsTest {
    private val out = File("build/screenshots/p6/keyframes").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val beats by lazy { StoryBook(context).beats }
    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.INK; textSize = 24f }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.INK; textSize = 18f }
    private val stamp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.TERRA; textSize = 18f; isFakeBoldText = true }

    private class Shot(val label: String, val f: Festival, val w: Weather, val action: Action, val t: Double, val bundle: Boolean = true, val page: Int = 14)

    /** 角色畫成 [h] 高（筆觸固定第一張，免得筆觸抖動被誤看成動作） */
    private fun render(s: Shot, h: Int): Bitmap {
        val parts = parts(s.page)
        val scene = FestivalScene.resolve(parts, s.f, s.w, true, s.bundle)
        val perf = FestivalActions.Performance(s.action, FestivalActions.levelFor(s.action, scene), s.t)
        val frame = Mowan.frame(parts, 0, true, s.w, s.t, Mowan.rainStepAt(s.t.toLong()), still = true, bundleOn = s.bundle, festival = s.f, performance = perf)
        val w = h * 3 / 4
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(Mowan.IVORY); MowanPainter(frame).draw(this, 0f, 0f, w.toFloat(), h.toFloat()) } }
    }

    /** viewBox 範圍 [x0, x1] × [y0, y1] 放大成 [size] × [size] */
    private fun crop(s: Shot, x0: Double, y0: Double, x1: Double, y1: Double, size: Int): Bitmap {
        val h = 1280
        val k = h / 160.0
        val b = render(s, h)
        val c = Bitmap.createBitmap(b, (x0 * k).toInt(), ((y0 + 20) * k).toInt(), ((x1 - x0) * k).toInt(), ((y1 - y0) * k).toInt())
        return Bitmap.createScaledBitmap(c, size, (size * (y1 - y0) / (x1 - x0)).toInt(), true)
    }

    /**
     * 一張拼圖：每格上面是全身（[full] 高），下面是放大（[zoom] 範圍）。
     * [perRow] 格一列，超過換列。
     */
    private fun sheet(file: String, heading: String, shots: List<Shot>, zoom: DoubleArray = doubleArrayOf(24.0, 20.0, 104.0, 110.0), full: Int = 300, perRow: Int = 8) {
        val w = full * 3 / 4
        val zh = (w * (zoom[3] - zoom[1]) / (zoom[2] - zoom[0])).toInt()
        val cellH = 30 + full + 6 + zh + 16
        val rows = (shots.size + perRow - 1) / perRow
        val cols = minOf(shots.size, perRow)
        val bmp = Bitmap.createBitmap(12 + cols * (w + 10), 50 + rows * cellH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp).apply { drawColor(0xFFEFEAE1.toInt()) }
        c.drawText(heading, 12f, 32f, title)
        shots.forEachIndexed { i, s ->
            val x = 12f + (i % perRow) * (w + 10)
            val y = 50f + (i / perRow) * cellH
            c.drawText("${s.t.toInt()} ms", x, y + 20, stamp)
            c.drawText(s.label, x + 90, y + 20, label)
            c.drawBitmap(render(s, full), x, y + 28, null)
            c.drawBitmap(crop(s, zoom[0], zoom[1], zoom[2], zoom[3], w), x, y + 28 + full + 6, null)
        }
        File(out, "$file.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun spring() {
        val times = listOf(0 to "開始", 500 to "抬手看紅包", 1500 to "雙手到胸前", 1750 to "低頭最低", 2300 to "停留", 3050 to "遞出", 3500 to "遞出停住", 4200 to "放回")
        sheet("1-spring-bow", "春節・作揖與遞紅包（晴，第 14 頁，背包袱）", times.map { (t, n) -> Shot(n, Festival.SPRING, Weather.SUNNY, Action.BOW, t.toDouble()) })
        sheet("2-spring-bow-rain", "春節・雨天撐傘拜年（同樣時間點；右手握傘點應該不動）", times.map { (t, n) -> Shot(n, Festival.SPRING, Weather.RAINY, Action.BOW, t.toDouble()) },
            zoom = doubleArrayOf(24.0, 0.0, 104.0, 110.0))
    }

    @Test
    fun xmas() {
        val times = listOf(0 to "開始", 500 to "襪子歪掉", 800 to "抬頭看襪子", 1200 to "手抬起", 1500 to "碰到襪身", 1625 to "扶正中", 1750 to "扶正", 2300 to "確認", 2900 to "手放下", 3600 to "看見禮物", 4400 to "結束")
        sheet("3-xmas-sock", "聖誕・扶正聖誕襪（晴，第 14 頁；D-39 方案 A）", times.map { (t, n) -> Shot(n, Festival.XMAS, Weather.SUNNY, Action.SOCK, t.toDouble()) },
            zoom = doubleArrayOf(14.0, 20.0, 94.0, 110.0), perRow = 11)
    }

    @Test
    fun moon() {
        val toast = listOf(0 to "開始", 700 to "捧到腹前", 1100 to "捧著", 1700 to "舉起中", 2300 to "舉高", 2900 to "停留望月", 3300 to "停留結束", 3800 to "放下中", 4300 to "放回", 4600 to "結束")
        sheet("4-moon-toast", "中秋・晴天舉月餅望月（第 14 頁，背包袱）", toast.map { (t, n) -> Shot(n, Festival.MOON, Weather.SUNNY, Action.TOAST, t.toDouble()) },
            zoom = doubleArrayOf(14.0, 0.0, 104.0, 110.0), perRow = 10)
        val cake = listOf(0 to "開始", 300 to "低頭", 600 to "捧起", 1200 to "看著", 2100 to "微笑", 2900 to "微笑", 3300 to "放回開始", 3800 to "放回中", 4300 to "抬頭", 4600 to "結束")
        sheet("5a-moon-cake-rain", "中秋・雨天看月餅（D-37）", cake.map { (t, n) -> Shot(n, Festival.MOON, Weather.RAINY, Action.TOAST, t.toDouble()) }, perRow = 10)
        sheet("5b-moon-cake-snow", "中秋・雪天看月餅（D-37）", cake.map { (t, n) -> Shot(n, Festival.MOON, Weather.SNOW, Action.TOAST, t.toDouble()) }, perRow = 10)
        // 6. 月餅舉起、放下經過包袱前方：每 100 ms 一格，放大包袱附近
        val pass = (1000..2300 step 100).map { it to "舉起" } + (3300..4400 step 100).map { it to "放下" }
        sheet("6-moon-bundle-pass", "中秋・背包袱時月餅經過包袱前方（每 100 ms；月餅在 HAND 圖層、包袱在 BUNDLE 圖層之下）",
            pass.map { (t, n) -> Shot(n, Festival.MOON, Weather.SUNNY, Action.TOAST, t.toDouble()) }, zoom = doubleArrayOf(26.0, 52.0, 70.0, 104.0), full = 220, perRow = 13)
    }

    @Test
    fun idles() {
        sheet("7a-idle-spring", "春節待機・看看紅包（晴：右手；雨：左手）",
            listOf(0, 500, 900, 1300, 1800, 2200).map { Shot("晴", Festival.SPRING, Weather.SUNNY, Action.LOOK_ENVELOPE, it.toDouble()) } +
                listOf(0, 500, 900, 1300, 1800, 2200).map { Shot("雨", Festival.SPRING, Weather.RAINY, Action.LOOK_ENVELOPE, it.toDouble()) },
            zoom = doubleArrayOf(24.0, 10.0, 104.0, 110.0), perRow = 6)
        sheet("7b-idle-xmas", "聖誕待機・偷看禮物", listOf(0, 400, 900, 1400, 2000).map { Shot("偷看禮物", Festival.XMAS, Weather.SUNNY, Action.PEEK_GIFT, it.toDouble()) },
            zoom = doubleArrayOf(24.0, 10.0, 120.0, 130.0))
        sheet("7c-idle-moon", "中秋待機・抬頭望月（晴）與看月餅（雷雨）",
            listOf(0, 600, 1200, 1800, 2600).map { Shot("望月", Festival.MOON, Weather.SUNNY, Action.GAZE_MOON, it.toDouble()) } +
                listOf(0, 600, 1200, 1600, 2600).map { Shot("看月餅", Festival.MOON, Weather.THUNDER, Action.GAZE_MOON, it.toDouble()) },
            zoom = doubleArrayOf(24.0, 0.0, 104.0, 110.0), perRow = 5)
    }
}
