package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-2 正式首頁（方案 A，D-53）：411dp／320dp × 字體 100%／200% 的整頁截圖（ScrollView 的完整內容），
 * 城市入口與故事卡墨丸的觸控範圍、文字截斷或換行的量測。存到 app/build/screenshots/p7-home-final。
 * 核准的參考圖在 docs/p7-home/6-a-all.png（候選，測試裡組出來的）。Robolectric 繪圖，不代表實機。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeShotsTest {
    private val out = File("build/screenshots/p7-home-final").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val desk = 0xFFEDE8E0.toInt()
    private val today = LocalDate.of(2027, 10, 21)

    @After
    fun cleanUp() {
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        RuntimeEnvironment.setFontScale(1f)
    }

    data class Config(val name: String, val qualifiers: String, val fontScale: Float)

    private val configs = listOf(
        Config("411dp・字體 100%", "w411dp-h891dp-xxhdpi", 1f),
        Config("411dp・字體 200%", "w411dp-h891dp-xxhdpi", 2f),
        Config("320dp・字體 100%", "w320dp-h640dp-xhdpi", 1f),
        Config("320dp・字體 200%", "w320dp-h640dp-xhdpi", 2f),
    )

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    private fun open(cfg: Config): MainActivity {
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        RuntimeEnvironment.setFontScale(cfg.fontScale)
        AppClock.setFakeToday(context, today)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        // 不要在背景真的下載天氣
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, today); tear(today) }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val MainActivity.content get() = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout

    /** 整個首頁（ScrollView 的完整內容） */
    private fun capture(a: MainActivity): Bitmap {
        val c = a.content
        val w = c.width
        c.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        c.layout(0, 0, w, c.measuredHeight)
        return Bitmap.createBitmap(w, c.measuredHeight, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(desk); c.draw(this) } }
    }

    private fun rectIn(root: View, v: View): Rect {
        var x = 0
        var y = 0
        var cur: View = v
        while (cur !== root) {
            x += cur.left - cur.scrollX
            y += cur.top - cur.scrollY
            cur = cur.parent as View
        }
        return Rect(x, y, x + v.width, y + v.height)
    }

    private fun textState(tv: TextView): String {
        val l = tv.layout ?: return "—"
        return when {
            l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0 -> "「…」截斷"
            l.lineCount > 1 -> "換成 ${l.lineCount} 行"
            tv.paint.measureText(tv.text.toString()) > tv.width - tv.totalPaddingLeft - tv.totalPaddingRight + 1 -> "被切掉"
            else -> "完整"
        }
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 34f }

    private fun sheet(name: String, title: String, shots: List<Pair<String, Bitmap>>) {
        val colW = 620
        val scaled = shots.map { (t, b) -> t to Bitmap.createScaledBitmap(b, colW, b.height * colW / b.width, true) }
        val h = scaled.maxOf { it.second.height } + 130
        val sheet = Bitmap.createBitmap(scaled.size * (colW + 30) + 30, h, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawText(title, 30f, 44f, words)
            scaled.forEachIndexed { i, (t, b) ->
                val x = 30f + i * (colW + 30)
                drawText(t, x, 100f, words)
                drawBitmap(b, x, 120f, null)
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun homeScreens() {
        val report = StringBuilder("# 正式首頁量測（方案 A；Robolectric）\n\n")
        report.append("| 設定 | 城市入口（dp） | ≥ 48dp | 城市名 | 故事卡墨丸（dp） | 包袱入口（dp） | 墨丸與包袱重疊 | 前一天／後一天 | 整頁高（dp） |\n|---|---|---|---|---|---|---|---|---|\n")
        val shots = configs.map { cfg ->
            val a = open(cfg)
            val shot = capture(a)
            val d = context.resources.displayMetrics.density
            val root = a.content
            val city = a.findViewById<TextView>(R.id.city)
            val c = rectIn(root, city)
            val m = rectIn(root, a.findViewById(R.id.story_mowan))
            val b = rectIn(root, a.findViewById(R.id.story_bundle))
            val nav = listOf(R.id.prev, R.id.next).map { a.findViewById<Button>(it) }
            val big = c.width() / d >= 47.5f && c.height() / d >= 47.5f
            report.append(
                "| %s | %.0f×%.0f | %s | %s | %.0f×%.0f | %.0f×%.0f | %s | %s | %.0f |\n".format(
                    cfg.name, c.width() / d, c.height() / d, if (big) "是" else "**否**", textState(city),
                    m.width() / d, m.height() / d, b.width() / d, b.height() / d, if (Rect.intersects(m, b)) "**是**" else "否",
                    nav.joinToString("、") { "${it.text.toString().trim()}：${textState(it)}" }, shot.height / d,
                ),
            )
            assertTrue("${cfg.name} 城市入口 ≥ 48dp", big)
            assertTrue("${cfg.name} 城市名不換行、不截斷", textState(city) == "完整")
            assertTrue("${cfg.name} 墨丸 ≥ 48dp", m.width() / d >= 47.5f && m.height() / d >= 47.5f)
            assertTrue("${cfg.name} 墨丸跟包袱入口不重疊", !Rect.intersects(m, b))
            // 標出可點的範圍（藍：墨丸；橘：包袱；紅：城市入口）
            val marked = shot.copy(Bitmap.Config.ARGB_8888, true)
            Canvas(marked).apply {
                val line = { color: Int -> Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color; strokeWidth = dp(2f); pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f) } }
                drawRect(m, line(0xFF2B5FA8.toInt()))
                drawRect(b, line(0xFFC0701E.toInt()))
                drawRect(c, line(0xFFB8322A.toInt()))
            }
            File(out, "home-${configs.indexOf(cfg) + 1}.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cfg.name to (shot to marked)
        }
        sheet("1-home-final", "正式首頁（方案 A）：411dp／320dp × 字體 100%／200%（縮成同樣寬；Robolectric）", shots.map { it.first to it.second.first })
        sheet("2-home-touch", "可點範圍：紅＝城市入口、藍＝故事卡墨丸（天氣彩蛋）、橘＝包袱入口", shots.map { it.first to it.second.second })
        File(out, "report.md").writeText(report.toString())
    }
}
