package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.patty60723.tearoff.mowan.ArtConfig
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
 * P6-3 的動畫素材：節日主演出、待機、成長階段的降級、撐傘與包袱的動態對照（GIF），
 * 關閉動畫的代表姿勢（PNG）。影格存到 app/build/screenshots/w7/frames/p6-*，CI 用 15 FPS 轉成 GIF。
 * 這是給人看的素材，不是比對用的測試；規則在 FestivalPerformanceTest、FestivalPerformanceGoldenTest。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FestivalAnimShotsTest {
    private val out = File("build/screenshots/w7").apply { mkdirs() }
    private val sheets = File("build/screenshots/p6").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val beats by lazy { StoryBook(context).beats }
    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Mowan.INK; textSize = 18f }
    private val name = mapOf(Festival.SPRING to "春節", Festival.XMAS to "聖誕", Festival.MOON to "中秋")
    private val weatherName = mapOf(Weather.SUNNY to "晴", Weather.RAINY to "雨", Weather.THUNDER to "雷雨", Weather.SNOW to "雪", Weather.NONE to "一般")

    /** 一格：某個節日、天氣、頁數、包袱，演 [action]（null 表示只站著） */
    private class Panel(val title: String, val f: Festival, val w: Weather, val page: Int = 14, val bundle: Boolean = true, val action: Action? = null)

    private fun draw(c: Canvas, x: Float, y: Float, h: Int, p: Panel, t: Double, held: Boolean = false) {
        val parts = parts(p.page)
        val scene = FestivalScene.resolve(parts, p.f, p.w, true, p.bundle)
        val perf = p.action?.let { a ->
            val tt = t.coerceIn(0.0, a.durMs.toDouble())
            if (t < 0 || t > a.durMs) null else FestivalActions.Performance(a, FestivalActions.levelFor(a, scene), tt, held)
        }
        val frame = Mowan.frame(parts, if (held) 0 else Mowan.variantAt(t.toLong().coerceAtLeast(0)), true, p.w, t.coerceAtLeast(0.0), Mowan.rainStepAt(t.toLong().coerceAtLeast(0)),
            still = held, bundleOn = p.bundle, festival = p.f, performance = perf)
        val w = h * 3 / 4
        c.save()
        c.clipRect(x, y, x + w, y + h)
        c.drawColor(Mowan.IVORY)
        MowanPainter(frame).draw(c, x, y, w.toFloat(), h.toFloat())
        c.restore()
    }

    /** 一個 GIF：一列 [panels]，從演出前 400 ms 到演出後 400 ms，15 FPS */
    private fun gif(file: String, panels: List<Panel>, h: Int = 300) {
        val dur = panels.mapNotNull { it.action?.durMs }.maxOrNull() ?: 4000L
        val w = h * 3 / 4
        val dir = File(out, "frames/$file").apply { deleteRecursively(); mkdirs() }
        var i = 0
        var t = -400.0
        while (t <= dur + 400) {
            val bmp = Bitmap.createBitmap(panels.size * (w + 6) + 6, h + 36, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp).apply { drawColor(0xFFEFEAE1.toInt()) }
            panels.forEachIndexed { k, p ->
                c.drawText(p.title, 6f + k * (w + 6) + 4, 24f, label)
                draw(c, 6f + k * (w + 6), 32f, h, p, t)
            }
            c.drawText("${t.toInt().coerceAtLeast(0)} ms", bmp.width - 90f, h + 30f, label)
            File(dir, "%04d.png".format(i++)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            t += 1000.0 / 15
        }
    }

    // ---------- 1. 主演出與待機 ----------

    @Test
    fun spring() {
        gif("p6-spring-main", listOf(
            Panel("作揖遞紅包（晴）", Festival.SPRING, Weather.SUNNY, action = Action.BOW),
            Panel("撐傘拜年（雨）", Festival.SPRING, Weather.RAINY, action = Action.BOW),
            Panel("撐傘拜年（雷雨）", Festival.SPRING, Weather.THUNDER, action = Action.BOW),
        ))
        gif("p6-spring-idle", listOf(
            Panel("看看紅包（晴）", Festival.SPRING, Weather.SUNNY, action = Action.LOOK_ENVELOPE),
            Panel("看看紅包（雨，左手）", Festival.SPRING, Weather.RAINY, action = Action.LOOK_ENVELOPE),
        ))
    }

    @Test
    fun xmas() {
        gif("p6-xmas-main", listOf(
            Panel("扶正聖誕襪、看禮物（晴）", Festival.XMAS, Weather.SUNNY, action = Action.SOCK),
            Panel("扶正聖誕襪（雪）", Festival.XMAS, Weather.SNOW, action = Action.SOCK),
            Panel("扶正聖誕襪（雨，左手）", Festival.XMAS, Weather.RAINY, action = Action.SOCK),
        ))
        gif("p6-xmas-idle", listOf(Panel("偷看禮物", Festival.XMAS, Weather.SUNNY, action = Action.PEEK_GIFT)))
    }

    @Test
    fun moon() {
        gif("p6-moon-main", listOf(
            Panel("舉月餅望月（晴）", Festival.MOON, Weather.SUNNY, action = Action.TOAST),
            Panel("看月餅（雨）", Festival.MOON, Weather.RAINY, action = Action.TOAST),
            Panel("看月餅（雪）", Festival.MOON, Weather.SNOW, action = Action.TOAST),
        ))
        gif("p6-moon-idle", listOf(
            Panel("抬頭望月（晴）", Festival.MOON, Weather.SUNNY, action = Action.GAZE_MOON),
            Panel("看月餅（雷雨）", Festival.MOON, Weather.THUNDER, action = Action.GAZE_MOON),
        ))
    }

    // ---------- 2. 成長階段的降級 ----------

    @Test
    fun growth() {
        val pages = listOf(1 to "線條", 2 to "頭部", 4 to "單手", 5 to "雙手沒腳", 14 to "完整")
        for (f in Festival.entries) {
            val a = FestivalScene.spec(f).main
            gif("p6-${f.name.lowercase()}-growth", pages.map { (page, n) -> Panel("第 $page 頁 $n", f, Weather.SUNNY, page = page, bundle = false, action = a) }, h = 220)
        }
    }

    // ---------- 3. 撐傘與包袱的動態對照 ----------

    @Test
    fun overlap() {
        gif("p6-overlap", listOf(
            Panel("春雨 背包袱", Festival.SPRING, Weather.RAINY, bundle = true, action = Action.BOW),
            Panel("春雨 卸下", Festival.SPRING, Weather.RAINY, bundle = false, action = Action.BOW),
            Panel("中秋 背包袱", Festival.MOON, Weather.SUNNY, bundle = true, action = Action.TOAST),
            Panel("中秋 卸下", Festival.MOON, Weather.SUNNY, bundle = false, action = Action.TOAST),
            Panel("聖誕 背包袱", Festival.XMAS, Weather.SUNNY, bundle = true, action = Action.SOCK),
        ), h = 260)
    }

    // ---------- 4. 關閉動畫的代表姿勢 ----------

    @Test
    fun reducedMotion() {
        val rows = listOf(
            Triple(Festival.SPRING, Weather.SUNNY, Action.BOW), Triple(Festival.SPRING, Weather.RAINY, Action.BOW),
            Triple(Festival.XMAS, Weather.SUNNY, Action.SOCK), Triple(Festival.XMAS, Weather.RAINY, Action.SOCK),
            Triple(Festival.MOON, Weather.SUNNY, Action.TOAST), Triple(Festival.MOON, Weather.RAINY, Action.TOAST),
        )
        val pages = listOf(2, 4, 5, 14)
        val h = 240
        val w = h * 3 / 4
        val bmp = Bitmap.createBitmap(130 + (pages.size + 1) * (w + 8), 40 + rows.size * (h + 10), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp).apply { drawColor(0xFFEFEAE1.toInt()) }
        (pages.map { "第 $it 頁 代表姿勢" } + "第 14 頁 沒有演出").forEachIndexed { i, s -> c.drawText(s, 130f + i * (w + 8), 26f, label) }
        rows.forEachIndexed { r, (f, wthr, a) ->
            val y = 40f + r * (h + 10)
            c.drawText("${name[f]}・${weatherName[wthr]}", 8f, y + h / 2f, label)
            c.drawText("${a.keyMs} ms", 8f, y + h / 2f + 24, label)
            pages.forEachIndexed { i, page -> draw(c, 130f + i * (w + 8), y, h, Panel("", f, wthr, page = page, bundle = page == 14, action = a), a.keyMs.toDouble(), held = true) }
            draw(c, 130f + pages.size * (w + 8), y, h, Panel("", f, wthr), 0.0, held = true)
        }
        File(sheets, "9-reduced-motion.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 5. 修正後的靜態對照：背包袱的月餅、紅包（第 14 頁），48／72 px 的禮物（實際大小與放大）。修正前的圖在 acceptance/mowan-p6-2 */
    @Test
    fun fixes() {
        val h = 600
        val k = h / 160.0
        val cases = listOf(Panel("中秋 晴", Festival.MOON, Weather.SUNNY), Panel("中秋 雷雨", Festival.MOON, Weather.THUNDER), Panel("春節 雨（左手紅包）", Festival.SPRING, Weather.RAINY))
        val cw = (64 * k).toInt()
        val ch = (60 * k).toInt()
        val bmp = Bitmap.createBitmap(cases.size * (cw + 8) + 8, ch + 40, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp).apply { drawColor(0xFFEFEAE1.toInt()) }
        cases.forEachIndexed { i, p ->
            c.drawText(p.title, 8f + i * (cw + 8), 26f, label)
            val full = Bitmap.createBitmap(h * 3 / 4, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(full), 0f, 0f, h, p, 0.0, held = true)
            // viewBox x 28–92、y 60–120：包袱、手、道具
            c.drawBitmap(Bitmap.createBitmap(full, (28 * k).toInt(), (80 * k).toInt(), cw, ch), 8f + i * (cw + 8), 36f, null)
        }
        File(sheets, "10-mooncake-fix.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
