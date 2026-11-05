package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * P7-7 日曆頁候選 C（正式）：大字體時左下天氣三行不蓋到「忌」，底部保留加高、頁面跟著加高（日期數字的空間不變）。
 * 411dp／320dp × 字體 100%／200% × 一般日／春節（Robolectric）。字形與碰撞用 [GlyphInk]（實際有墨的像素）。
 * 撕頁、翻頁、返回最上頁的高度；捲到最底點故事卡墨丸不位移由 HomeShotsTest 照舊檢查（同樣 4 種設定，頁面已經是加高後的）。
 * 圖：app/build/screenshots/p7-7-page/。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PageReserveTest {
    private val out = File("build/screenshots/p7-7-page").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val normal = LocalDate.of(2027, 10, 21)
    private val spring = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == Festival.SPRING }
    private val d get() = context.resources.displayMetrics.density

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        RuntimeEnvironment.setFontScale(1f)
    }

    data class Cfg(val name: String, val qualifiers: String, val fontScale: Float)

    private val cfgs = listOf(
        Cfg("411dp・100%", "w411dp-h891dp-xxhdpi", 1f),
        Cfg("411dp・200%", "w411dp-h891dp-xxhdpi", 2f),
        Cfg("320dp・100%", "w320dp-h640dp-xhdpi", 1f),
        Cfg("320dp・200%", "w320dp-h640dp-xhdpi", 2f),
    )

    /** [torn] false：今天這頁還沒撕（最上面是前一天、可以撕） */
    private fun open(cfg: Cfg, day: LocalDate, torn: Boolean = true): MainActivity {
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        RuntimeEnvironment.setFontScale(cfg.fontScale)
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); if (torn) tear(day) }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get().also { relayout(it) }
    }

    private fun relayout(a: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY))
        v.layout(v.left, v.top, v.right, v.bottom)
    }

    private fun pad(a: MainActivity) = a.findViewById<TearPageView>(R.id.page)

    private fun shoot(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEDE8E0.toInt()); v.draw(this) } }

    data class State(val overlaps: List<String>, val cuts: List<String>, val dayDp: Double, val gapDp: Double, val padDp: Double, val extraDp: Double)

    private fun state(a: MainActivity): State {
        val p = pad(a)
        val page = p.getChildAt(0)
        val sun = page.findViewById<TextView>(R.id.sun)
        val texts = listOf(R.id.phrase to "宜", R.id.lunar to "農曆", R.id.ji to "忌", R.id.label to "節日", R.id.day to "日期", R.id.month to "月份", R.id.weekday to "星期")
            .mapNotNull { (id, n) -> page.findViewById<TextView>(id)?.takeIf { it.visibility == View.VISIBLE && it.text.isNotEmpty() }?.let { n to it } }
        val sunMask = GlyphInk.mask(page, sun)
        val mowan = GlyphInk.mask(page, page.findViewById(R.id.mowan_still))
        val overlaps = texts.filter { GlyphInk.overlap(sunMask, GlyphInk.mask(page, it.second)) > 0 }.map { "天氣×${it.first}" } +
            (if (GlyphInk.overlap(sunMask, mowan) > 0) listOf("天氣×墨丸") else emptyList()) +
            texts.filter { GlyphInk.overlap(mowan, GlyphInk.mask(page, it.second)) > 0 }.map { "${it.first}×墨丸" }
        val cuts = (texts + ("天氣" to sun)).filter { GlyphInk.clipped(page, it.second) > 0 }.map { it.first }
        val day = page.findViewById<TextView>(R.id.day)
        val dayDp = GlyphInk.glyphs(page, day).maxOf { it.height() } / d.toDouble()
        // 「忌」可能是空的（那天沒有忌）：間距改量到上面最近一行有字的（農曆）
        val above = listOf(R.id.ji, R.id.lunar, R.id.phrase).firstNotNullOfOrNull { id -> GlyphInk.glyphs(page, page.findViewById(id)).maxOfOrNull { it.bottom } }
        val sunTop = GlyphInk.glyphs(page, sun).minOfOrNull { it.top }
        val gap = if (above != null && sunTop != null) (sunTop - above) / d.toDouble() else Double.NaN
        return State(overlaps, cuts, dayDp, gap, p.height / d.toDouble(), p.extraHeight / d.toDouble())
    }

    /** 模擬實作前（底部保留 84dp、頁面不加高）：拿來比日期字形與 100% 的畫面 */
    private fun before(a: MainActivity) {
        val p = pad(a)
        val space = p.getChildAt(0).findViewById<View>(R.id.bottom_space)
        space.layoutParams = space.layoutParams.apply { height = (84 * d).toInt() }
        p.extraHeight = 0
        relayout(a)
    }

    private fun f(x: Double) = "%.0f".format(x)

    @Test
    fun weatherKeepsClearOfJiAndThePageGrows() {
        val report = StringBuilder("# 日曆頁候選 C（正式）：天氣三行與「忌」\n\n")
        report.append("「間距」＝天氣三行字形上緣到「忌」字形下緣。「實作前」是同一頁把底部保留改回 84dp、頁面不加高。\n\n")
        report.append("| 日子 | 設定 | 碰撞 | 截斷 | 日期字形（實作前→後） | 間距 | 頁面高（加高） |\n|---|---|---|---|---|---|---|\n")
        val failures = mutableListOf<String>()
        val shots = mutableListOf<Pair<String, Bitmap>>()
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) for (cfg in cfgs) {
            val a = open(cfg, day)
            val now = state(a)
            val shot = shoot(pad(a))
            before(a)
            val old = state(a)
            val oldShot = shoot(pad(a))
            val tag = "$dayName ${cfg.name}"
            if (now.overlaps.isNotEmpty()) failures += "$tag 碰撞：${now.overlaps}"
            if (now.cuts.isNotEmpty()) failures += "$tag 截斷：${now.cuts}"
            if (now.dayDp < old.dayDp - 0.5) failures += "$tag 日期變小：${old.dayDp} → ${now.dayDp}"
            if (cfg.fontScale == 1f) {
                if (now.extraDp != 0.0) failures += "$tag 100% 不應該加高"
                if (!shot.sameAs(oldShot)) failures += "$tag 100% 畫面改變"
            }
            report.append("| $dayName | ${cfg.name} | ${now.overlaps.ifEmpty { listOf("無") }.joinToString("、")} | ${now.cuts.ifEmpty { listOf("無") }.joinToString("、")} | ${f(old.dayDp)} → ${f(now.dayDp)}dp | ${f(now.gapDp)}dp（實作前 ${f(old.gapDp)}） | ${f(now.padDp)}dp（+${f(now.extraDp)}） |\n")
            if (cfg.fontScale == 2f) { shots += "$tag 實作前" to oldShot; shots += "$tag 候選 C" to shot }
        }
        File(out, "1-page.md").writeText(report.toString())
        sheet("1-page", shots)
        assertTrue("候選 C：$failures", failures.isEmpty())
    }

    /** 200% 撕頁：撕完換頁、故事卡更新；撕的時候下一頁的截圖跟頁面同大小；撕完頁面高度換成新的那頁 */
    @Test
    fun tearAtLargeFont() {
        val report = StringBuilder("# 字體 200% 撕頁（候選 C）\n\n| 設定 | 撕之前（頁面高、加高） | 下一頁截圖 | 撕完（頁面高、加高） | 故事卡 | 頁數 |\n|---|---|---|---|---|---|\n")
        val shots = mutableListOf<Pair<String, Bitmap>>()
        for (cfg in cfgs.filter { it.fontScale == 2f }) {
            val a = open(cfg, normal, torn = false)
            val p = pad(a)
            val book = StoryBook(context)
            val pageBefore = book.page
            val storyBefore = a.findViewById<TextView>(R.id.story_text).text.toString()
            val h0 = p.height
            val e0 = p.extraHeight
            var below: Bitmap? = null
            val orig = p.pageBelow
            p.pageBelow = { w, h -> orig(w, h).also { below = it } }
            // 撕到一半的樣子（給人看）
            p.holdPeel(true, p.width * 0.35f, p.height * 0.55f)
            shots += "${cfg.name} 撕到一半" to shoot(p)
            ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
            p.peelAway()
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            relayout(a)
            val b = below
            assertTrue("${cfg.name} 有下一頁的截圖", b != null)
            assertEquals("${cfg.name} 下一頁截圖跟頁面同寬", p.width, b!!.width)
            assertEquals("${cfg.name} 下一頁截圖跟撕之前的頁面同高", h0, b.height)
            val pageAfter = StoryBook(context).page
            val storyAfter = a.findViewById<TextView>(R.id.story_text).text.toString()
            assertEquals("${cfg.name} 撕完頁數 +1", pageBefore + 1, pageAfter)
            assertTrue("${cfg.name} 故事卡更新", storyAfter != storyBefore)
            assertEquals("${cfg.name} 撕完的頁面高度＝寬 × 1.45 ＋ 新那頁的加高", (p.width * p.aspect).toInt() + p.extraHeight, p.height)
            val s = state(a)
            assertTrue("${cfg.name} 撕完不碰撞：${s.overlaps}", s.overlaps.isEmpty())
            report.append("| ${cfg.name} | ${f(h0 / d.toDouble())}dp（+${f(e0 / d.toDouble())}） | ${b.width}×${b.height}px（跟撕之前的頁面一樣） | ${f(p.height / d.toDouble())}dp（+${f(p.extraHeight / d.toDouble())}） | 更新 | $pageBefore → $pageAfter |\n")
            shots += "${cfg.name} 撕完" to shoot(p)
        }
        report.append("\n撕之前是前一天那頁（沒有天氣行），撕完是今天那頁（有天氣三行）：頁面在撕完那一刻加高（上表），撕的動畫裡下一頁是用撕之前的大小畫的。\n")
        File(out, "2-tear.md").writeText(report.toString())
        sheet("2-tear", shots)
    }

    /** 翻頁與返回最上頁：每一頁各自計算加高；翻到別天再回來、按「回到最上面那頁」，高度都正確 */
    @Test
    fun flipAndBackToTop() {
        val report = StringBuilder("# 翻頁與返回最上頁的頁面高度（候選 C）\n\n| 日子 | 設定 | 今天 | 前一天 | 後一天回來 | 前一天 → 回到最上面那頁 |\n|---|---|---|---|---|---|\n")
        for ((day, dayName) in listOf(normal to "一般日", spring to "春節")) for (cfg in cfgs) {
            val a = open(cfg, day)
            val p = pad(a)
            fun h() = p.height
            fun expect() = (p.width * p.aspect).toInt() + p.extraHeight
            val today = h()
            assertEquals("$dayName ${cfg.name} 今天", expect(), today)
            a.findViewById<Button>(R.id.prev).performClick(); relayout(a)
            val yesterday = h()
            assertEquals("$dayName ${cfg.name} 前一天", expect(), yesterday)
            assertTrue("$dayName ${cfg.name} 前一天不碰撞", state(a).overlaps.isEmpty())
            a.findViewById<Button>(R.id.next).performClick(); relayout(a)
            val back = h()
            assertEquals("$dayName ${cfg.name} 回到今天高度一樣", today, back)
            a.findViewById<Button>(R.id.prev).performClick(); relayout(a)
            a.findViewById<Button>(R.id.today).performClick(); relayout(a)
            val top = h()
            assertEquals("$dayName ${cfg.name} 回到最上面那頁高度一樣", today, top)
            report.append("| $dayName | ${cfg.name} | ${f(today / d.toDouble())}dp | ${f(yesterday / d.toDouble())}dp | ${f(back / d.toDouble())}dp | ${f(top / d.toDouble())}dp |\n")
        }
        report.append("\n今天那頁有天氣三行、別天只有日出日落兩行：大字體時翻頁頁面高度會變（上表的差）。字體 100% 時都是寬 × 1.45。\n")
        File(out, "3-flip.md").writeText(report.toString())
    }

    private fun sheet(name: String, shots: List<Pair<String, Bitmap>>) {
        val colW = 300
        val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 20f }
        val cells = shots.map { (n, b) -> n to Bitmap.createScaledBitmap(b, colW, b.height * colW / b.width, true) }
        val h = cells.maxOf { it.second.height } + 60
        val sheet = Bitmap.createBitmap(20 + cells.size * (colW + 20), h, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            cells.forEachIndexed { i, (n, b) -> drawText(n, 20f + i * (colW + 20), 32f, words); drawBitmap(b, 20f + i * (colW + 20), 46f, null) }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
