package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.patty60723.tearoff.mowan.Capabilities
import com.patty60723.tearoff.mowan.Detail
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanTimeline
import com.patty60723.tearoff.mowan.Pt
import com.patty60723.tearoff.mowan.WeatherEgg
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import kotlin.math.hypot

/**
 * P7-9a 天氣彩蛋（方案 C）的可播放預覽：用正式的首頁（Robolectric 畫出來）當舞台，把日曆小墨丸與故事卡大墨丸
 * 換成 [WeatherEgg] 分鏡每 1/6 秒的姿勢（正式的 [Mowan.render]、[MowanPainter]），加上天氣素材與接力路徑。
 * **只是預覽**：不改正式 App。
 *
 * 輸出 app/build/screenshots/p7-9a/：每種天氣一個資料夾（frame-NNNN.jpg、frames.json），以及 report.md（自動檢查）。
 * 前後各留一段：點擊前 0.5 秒（日常）、結束後 0.7 秒（恢復）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-hdpi")
class WeatherEggPreviewTest {
    private val out = File("build/screenshots/p7-9a").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val day = LocalDate.of(2027, 10, 21)
    private val boil = Mowan.BOIL_MS
    private val pre = 500.0
    private val post = 700.0

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    private val MainActivity.content get() = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout

    private fun relayout(a: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY))
        v.layout(v.left, v.top, v.right, v.bottom)
        val c = a.content
        c.measure(View.MeasureSpec.makeMeasureSpec(c.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        c.layout(0, 0, c.width, c.measuredHeight)
    }

    /** 一個墨丸畫在哪裡：畫布上的範圍；跟 MowanPainter 一樣置中、等比例，原型座標 (0, −20)～(120, 140) */
    data class Box(val rect: RectF) {
        val s = minOf(rect.width() / 120f, rect.height() / 160f)
        val left = rect.left + (rect.width() - 120 * s) / 2
        val top = rect.top + (rect.height() - 160 * s) / 2
        fun map(x: Double, y: Double) = Pt((left + x * s).toDouble(), (top + (y + 20) * s).toDouble())
    }

    data class Stage(val bg: Bitmap, val small: Box, val big: Box, val sun: RectF, val density: Float, val bigPad: RectF)

    private fun stage(mood: Mood): Stage {
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, mood)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); tear(day) }
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        relayout(a)
        val c = a.content
        val page = a.findViewById<View>(R.id.page)
        val still = page.findViewById<View>(R.id.mowan_still)
        val sun = page.findViewById<View>(R.id.sun)
        val big = a.findViewById<View>(R.id.story_mowan)
        val card = big.parent as View
        fun r(v: View) = GlyphInk.rectIn(c, v).let { RectF(it) }
        val top = r(page).top - 12 * a.resources.displayMetrics.density
        val bottom = r(card).bottom + 12 * a.resources.displayMetrics.density
        val smallR = r(still); val bigR = r(big); val sunR = r(sun)
        still.visibility = View.INVISIBLE
        big.visibility = View.INVISIBLE
        val full = Bitmap.createBitmap(c.width, c.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEDE8E0.toInt()); c.draw(this) } }
        val bg = Bitmap.createBitmap(full, 0, top.toInt(), full.width, (bottom - top).toInt())
        fun off(f: RectF) = RectF(f.left, f.top - top, f.right, f.bottom - top)
        val bigInner = off(bigR).let { RectF(it.left + big.paddingLeft, it.top + big.paddingTop, it.right - big.paddingRight, it.bottom - big.paddingBottom) }
        return Stage(bg, Box(off(smallR)), Box(bigInner), off(sunR), a.resources.displayMetrics.density, off(bigR))
    }

    private val parts by lazy { Mowan.partsOf(StoryBook(context).parts) }

    private fun drawMowan(canvas: Canvas, box: Box, pose: com.patty60723.tearoff.mowan.Pose, weather: Mowan.Weather, variant: Int, clock: Double, still: Boolean, simple: Boolean) {
        val w = box.rect.width().toInt(); val h = box.rect.height().toInt()
        val density = context.resources.displayMetrics.density
        val detail = if (simple) Detail.of(minOf(w / 120.0, h / 160.0) * 160, density.toDouble()) else Detail.FULL
        val frame = Mowan.render(parts, variant, true, pose, weather, Mowan.rainStepAt(clock.toLong().coerceAtLeast(0)), clockMs = clock, still = still, bundleOn = true, detail = detail)
        MowanPainter(frame).draw(canvas, box.rect.left, box.rect.top, box.rect.width(), box.rect.height())
    }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun drawMote(canvas: Canvas, m: WeatherEgg.Mote, st: Stage) {
        // 位置：小墨丸、大墨丸各自的座標；接力是兩者之間的曲線（從小墨丸左下出發，落到大墨丸頭頂）
        val (p, s) = when (m.space) {
            "small" -> st.small.map(m.x, m.y) to st.small.s
            "big" -> st.big.map(m.x, m.y) to st.big.s
            else -> {
                val a = st.small.map(44.0, 72.0); val b = st.big.map(61.0, 8.0)
                val ctrl = Pt(Geometry.lerp(a.x, b.x, 0.5) + (b.x - a.x) * 0.35 + m.x * st.bg.width, minOf(a.y, b.y) + (b.y - a.y) * 0.15)
                val u = m.u
                val x = (1 - u) * (1 - u) * a.x + 2 * (1 - u) * u * ctrl.x + u * u * b.x
                val y = (1 - u) * (1 - u) * a.y + 2 * (1 - u) * u * ctrl.y + u * u * b.y
                Pt(x, y) to Geometry.lerp(st.small.s.toDouble(), st.big.s.toDouble(), u).toFloat()
            }
        }
        val a = (255 * m.alpha.coerceIn(0.0, 1.0)).toInt()
        val x = p.x.toFloat(); val y = p.y.toFloat(); val r = (m.size * s).toFloat()
        when (m.kind) {
            WeatherEgg.Kind.LIGHT -> {
                ink.style = Paint.Style.FILL
                ink.color = Mowan.PINK; ink.alpha = a / 4; canvas.drawCircle(x, y, r * 2.2f, ink)
                ink.alpha = a; canvas.drawCircle(x, y, r, ink)
            }
            WeatherEgg.Kind.DROP -> {
                ink.style = Paint.Style.STROKE; ink.strokeCap = Paint.Cap.ROUND; ink.strokeWidth = 1.5f * s
                ink.color = Mowan.SAGE; ink.alpha = a
                canvas.drawLine(x, y - r, x - 0.5f * r, y + r, ink)
            }
            WeatherEgg.Kind.SPLASH -> {
                ink.style = Paint.Style.STROKE; ink.strokeCap = Paint.Cap.ROUND; ink.strokeWidth = 1.2f * s
                ink.color = Mowan.SAGE; ink.alpha = a
                val d = r * (0.6f + 1.4f * m.u.toFloat())
                for (k in -1..1) canvas.drawLine(x + k * d * 0.6f, y - d * 0.2f, x + k * d * 1.1f, y - d * (0.7f - 0.2f * kotlin.math.abs(k)), ink)
            }
            WeatherEgg.Kind.FLAKE -> {
                ink.style = Paint.Style.FILL; ink.color = Mowan.SNOW; ink.alpha = a; canvas.drawCircle(x, y, r * 0.7f, ink)
                ink.style = Paint.Style.STROKE; ink.strokeWidth = 0.6f * s; ink.color = Mowan.INK; ink.alpha = a; canvas.drawCircle(x, y, r * 0.7f, ink)
            }
            WeatherEgg.Kind.PUFF -> {
                ink.style = Paint.Style.FILL; ink.color = Mowan.CLOUD; ink.alpha = (a * 0.85).toInt()
                canvas.drawOval(RectF(x - r, y - r * 0.6f, x + r, y + r * 0.6f), ink)
            }
            WeatherEgg.Kind.RING -> {
                ink.style = Paint.Style.STROKE; ink.strokeWidth = 1.0f * s; ink.color = Mowan.SAGE; ink.alpha = a
                canvas.drawOval(RectF(x - r, y - r * 0.16f, x + r, y + r * 0.16f), ink)
            }
        }
    }

    private object Geometry {
        fun lerp(a: Double, b: Double, u: Double) = a + (b - a) * u
    }

    /** 一個姿勢所有骨架點（給跳格、斷肢檢查用） */
    private fun joints(p: com.patty60723.tearoff.mowan.Pose) = p.body + p.armL + p.armR + p.legL + p.legR + p.tail

    private fun armLen(a: List<Pt>) = a.zipWithNext { x, y -> hypot(y.x - x.x, y.y - x.y) }.sum()

    data class Check(val maxJump: Double, val jumpAt: Double, val armMin: Double, val armMax: Double, val sizeChanges: Int)

    private fun check(script: WeatherEgg.Script, caps: Capabilities, sel: (Double) -> com.patty60723.tearoff.mowan.Pose, ref: com.patty60723.tearoff.mowan.Pose): Check {
        var prev: com.patty60723.tearoff.mowan.Pose? = null
        var maxJump = 0.0; var at = 0.0; var amin = 9.0; var amax = 0.0; var sizes = 0
        var t = -pre
        while (t <= script.durMs + post) {
            val p = sel(t)
            for ((a, r) in listOf(p.armL to ref.armL, p.armR to ref.armR)) {
                val ratio = armLen(a) / armLen(r)
                amin = minOf(amin, ratio); amax = maxOf(amax, ratio)
            }
            prev?.let { q ->
                if (q.armL.size != p.armL.size || q.armR.size != p.armR.size || q.body.size != p.body.size) sizes++
                else {
                    val j = joints(p).zip(joints(q)).maxOf { (x, y) -> hypot(x.x - y.x, x.y - y.y) }
                    if (j > maxJump) { maxJump = j; at = t }
                }
            }
            prev = p
            t += boil
        }
        return Check(maxJump, at, amin, amax, sizes)
    }

    @Test
    fun renderPreviews() {
        val report = StringBuilder("# P7-9a 天氣彩蛋預覽：自動檢查\n\n")
        report.append("每 1/6 秒取一格（正式的 6 FPS）。跳格＝相鄰兩格之間骨架點移動的最大距離（原型單位，墨丸身高約 75）；")
        report.append("手臂長度＝跟日常姿勢的手臂比（< 0.85 或 > 1.2 像斷手或拉長）；點數改變＝相鄰兩格的手臂點數不同（會造成跳格）。\n\n")
        report.append("| 天氣 | 墨丸 | 最大跳格 | 發生在 | 手臂長度比 | 點數改變 |\n|---|---|---|---|---|---|\n")
        val problems = mutableListOf<String>()
        for ((script, mood) in listOf(WeatherEgg.Sunny to Mood.SUNNY, WeatherEgg.Rainy to Mood.RAIN, WeatherEgg.Snowy to Mood.SNOW)) {
            val st = stage(mood)
            val caps = Capabilities.of(parts)
            val id = script.weather.name.lowercase()
            val dir = File(out, id).apply { mkdirs() }
            val frames = StringBuilder("[")
            var t = -pre
            var n = 0
            while (t <= script.durMs + post) {
                val inEgg = t >= 0 && t <= script.durMs
                val b = st.bg.copy(Bitmap.Config.ARGB_8888, true)
                val cv = Canvas(b)
                // 點擊回饋：天氣資訊後面淡淡亮一下
                val tap = if (t >= 0) script.tap(t) else 0.0
                if (tap > 0) {
                    ink.style = Paint.Style.FILL; ink.color = 0xFF8C8378.toInt(); ink.alpha = (40 * tap).toInt()
                    val r = RectF(st.sun); r.inset(-4 * st.density, -2 * st.density)
                    cv.drawRoundRect(r, 6 * st.density, 6 * st.density, ink)
                }
                // 小墨丸：彩蛋以外是日曆上的靜止圖（筆觸固定第一張）
                if (inEgg) drawMowan(cv, st.small, script.small(t, caps), script.weather, Mowan.variantAt(t.toLong()), MowanTimeline.stillClock(script.weather) + t, false, true)
                else drawMowan(cv, st.small, script.still(caps), script.weather, 0, MowanTimeline.stillClock(script.weather), true, true)
                // 大墨丸：一直在動（日常 → 彩蛋 → 日常）
                val bigClock = t + 10_000
                drawMowan(cv, st.big, script.big(t, caps), script.weather, Mowan.variantAt(bigClock.toLong()), bigClock, false, false)
                if (inEgg) {
                    for (m in script.motes(t)) drawMote(cv, m, st)
                    // 雪：大墨丸接住以後，雪花跟著右手
                    if (script === WeatherEgg.Snowy && t in 4450.0..7100.0) {
                        val hand = WeatherEgg.Snowy.bigHand(t, caps)
                        val from = Pt(61.0, 8.0)
                        val u = ((t - 4450) / 350).coerceIn(0.0, 1.0)
                        val at = if (u < 1) Pt(Geometry.lerp(from.x, hand.x, u), Geometry.lerp(from.y, hand.y - 3, u)) else Pt(hand.x, hand.y - 3)
                        drawMote(cv, WeatherEgg.Mote(WeatherEgg.Kind.FLAKE, "big", at.x, at.y, 3.6, if (t > 6800) 1 - (t - 6800) / 300 else 1.0), st)
                    }
                }
                File(dir, "frame-%04d.jpg".format(n)).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                val beat = if (t < -0.5) "點擊前（日常）" else if (t > script.durMs + 0.5) "結束（恢復日常）" else script.beats.last { it.first <= t + 0.5 }.second
                frames.append(if (n > 0) "," else "").append("{\"t\":${t.toInt()},\"beat\":\"$beat\"}")
                b.recycle()
                n++
                t += boil
            }
            File(dir, "frames.json").writeText(frames.append("]").toString())
            File(dir, "meta.json").writeText("{\"id\":\"$id\",\"fps\":6,\"frames\":$n,\"preMs\":${pre.toInt()},\"durMs\":${script.durMs.toInt()},\"width\":${st.bg.width},\"height\":${st.bg.height}," +
                "\"small\":[${st.small.rect.left.toInt()},${st.small.rect.top.toInt()},${st.small.rect.right.toInt()},${st.small.rect.bottom.toInt()}]," +
                "\"big\":[${st.bigPad.left.toInt()},${st.bigPad.top.toInt()},${st.bigPad.right.toInt()},${st.bigPad.bottom.toInt()}]}")
            for ((who, sel, ref) in listOf(
                Triple("小墨丸（日曆）", { x: Double -> if (x in 0.0..script.durMs) script.small(x, caps) else script.still(caps) }, script.still(caps)),
                Triple("大墨丸（故事卡）", { x: Double -> script.big(x, caps) }, script.daily(0.0, caps)),
            )) {
                val c = check(script, caps, sel, ref)
                report.append("| ${script.weather} | $who | %.1f | %.0f ms | %.2f～%.2f | ${c.sizeChanges} |\n".format(c.maxJump, c.jumpAt, c.armMin, c.armMax))
                if (c.sizeChanges > 0) problems += "${script.weather} $who 手臂點數改變 ${c.sizeChanges} 次"
                if (c.armMin < 0.85 || c.armMax > 1.2) problems += "${script.weather} $who 手臂長度 %.2f～%.2f".format(c.armMin, c.armMax)
                if (c.maxJump > 14) problems += "${script.weather} $who 跳格 %.1f（%.0f ms）".format(c.maxJump, c.jumpAt)
            }
            st.bg.recycle()
        }
        report.append("\n" + if (problems.isEmpty()) "**沒有斷手、拉長或點數改變；跳格都在 14 單位以內。**\n" else "**問題：**\n" + problems.joinToString("\n") { "- $it" } + "\n")
        File(out, "report.md").writeText(report.toString())
        assertTrue("$problems", problems.isEmpty())
    }
}
