package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
import java.io.File
import java.time.LocalDate

/**
 * P7-7 導覽列候選 A：「回到最上面那頁」自己一列（D-54）。411dp／320dp × 字體 100%／200%（Robolectric）。
 * 字形完整用 [GlyphInk]（實際有墨的像素）。圖：app/build/screenshots/p7-7-nav/。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavRowTest {
    private val out = File("build/screenshots/p7-7-nav").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.of(2027, 10, 21)

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

    private fun open(cfg: Cfg): MainActivity {
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        RuntimeEnvironment.setFontScale(cfg.fontScale)
        AppClock.setFakeToday(context, today)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, today); tear(today) }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private fun relayout(a: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val d = a.window.decorView
        d.measure(View.MeasureSpec.makeMeasureSpec(d.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(d.height, View.MeasureSpec.EXACTLY))
        d.layout(d.left, d.top, d.right, d.bottom)
    }

    private val MainActivity.content get() = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout

    /** 一個按鈕：一行、字形完整、高 ≥ 48dp */
    private fun check(a: MainActivity, b: Button, what: String, d: Float): String {
        val root = a.content
        val lines = b.layout?.lineCount ?: 0
        val state = GlyphInk.state(root, b)
        val h = b.height / d
        assertEquals("$what 一行", 1, lines)
        assertEquals("$what 字形完整", "完整", state)
        assertTrue("$what 高 ≥ 48dp（${h}）", h >= 47.5f)
        return "%s：%.0f×%.0fdp、%d 行、%s".format(b.text.toString().trim(), b.width / d, h, lines, state)
    }

    private fun shoot(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEDE8E0.toInt()); v.draw(this) } }

    @Test
    fun todayButtonHasItsOwnRow() {
        val report = StringBuilder("# 導覽列候選 A（正式）：「回到最上面那頁」自己一列\n\n")
        report.append("| 設定 | 看最上面那頁 | 看昨天那頁：前一天 | 後一天 | 回到最上面那頁 | 點了以後 |\n|---|---|---|---|---|---|\n")
        val shots = mutableListOf<Pair<String, Bitmap>>()
        for (cfg in cfgs) {
            val a = open(cfg)
            relayout(a)
            val d = context.resources.displayMetrics.density
            val prev = a.findViewById<Button>(R.id.prev)
            val next = a.findViewById<Button>(R.id.next)
            val todayBtn = a.findViewById<Button>(R.id.today)
            val navRow = prev.parent as View
            // 最上面那頁：按鈕 GONE，不佔高度；城市入口緊接在導覽列下面
            assertEquals("${cfg.name} 最上面那頁時 GONE", View.GONE, todayBtn.visibility)
            val cityTop = GlobalRect.top(a.content, a.findViewById(R.id.city))
            assertEquals("${cfg.name} 最上面那頁時不佔高度", navRow.bottom, cityTop)
            val atTop = "GONE（導覽列下緣 = 城市入口上緣）"
            val topShot = shoot(a.content)
            // 看昨天那頁
            prev.performClick()
            relayout(a)
            assertEquals("${cfg.name} 看昨天時出現", View.VISIBLE, todayBtn.visibility)
            assertEquals("${cfg.name} 導覽列在同一列", prev.top, next.top)
            val p = check(a, prev, "${cfg.name} 前一天", d)
            val n = check(a, next, "${cfg.name} 後一天", d)
            val t = check(a, todayBtn, "${cfg.name} 回到最上面那頁", d)
            assertTrue("${cfg.name} 回到最上面那頁在導覽列下面", todayBtn.top >= navRow.bottom)
            val yesterdayShot = shoot(a.content)
            // 點了回到最上面那頁
            todayBtn.performClick()
            relayout(a)
            val day = a.findViewById<View>(R.id.page).findViewById<TextView>(R.id.day).text.toString()
            assertEquals("${cfg.name} 回到最上面那頁（今天 21 日）", "21", day)
            assertEquals("${cfg.name} 回到最上面以後又 GONE", View.GONE, todayBtn.visibility)
            report.append("| ${cfg.name} | $atTop | $p | $n | $t | 回到 21 日、按鈕 GONE |\n")
            shots += "${cfg.name} 最上面那頁" to topShot
            shots += "${cfg.name} 昨天那頁" to yesterdayShot
        }
        // 焦點順序（TalkBack）：依版面順序
        report.append("\nTalkBack 的焦點順序（依版面檔的順序）：前一天 → 後一天 → 回到最上面那頁 → 城市入口。實機朗讀 **Not tested**。\n")
        File(out, "report.md").writeText(report.toString())
        sheet(shots)
    }

    private fun sheet(shots: List<Pair<String, Bitmap>>) {
        val colW = 360
        val cropH = 520
        val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 22f }
        val cells = shots.map { (n, b) -> n to Bitmap.createScaledBitmap(b, colW, b.height * colW / b.width, true).let { Bitmap.createBitmap(it, 0, 0, colW, minOf(cropH, it.height)) } }
        val sheet = Bitmap.createBitmap(20 + cells.size * (colW + 20), cropH + 70, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            cells.forEachIndexed { i, (n, b) -> drawText(n, 20f + i * (colW + 20), 36f, words); drawBitmap(b, 20f + i * (colW + 20), 50f, null) }
        }
        File(out, "nav.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private object GlobalRect {
        fun top(root: View, v: View) = GlyphInk.rectIn(root, v).top
    }
}
