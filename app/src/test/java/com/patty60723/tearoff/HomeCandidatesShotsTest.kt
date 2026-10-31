package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * P7-2：首頁的城市入口（2026-10-31 審核選 A：日曆上方右側；B 不再繼續）與天氣彩蛋入口（故事卡上的墨丸）的對照圖。
 * 方案的 View 只在這個測試裡組出來（拿掉天氣列、城市列，加上「⌖ 臺北市 ⌄」），正式首頁沒有改。
 * 存到 app/build/screenshots/p7-home：整個首頁（ScrollView 的完整內容）、一般與 200% 字體、411dp 與 320dp，
 * 以及觸控範圍、截斷的量測報告。Robolectric 繪圖，不代表實機。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeCandidatesShotsTest {
    private val out = File("build/screenshots/p7-home").apply { mkdirs() }
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

    /** 2026-10-31 審核：選 A（日曆上方右側），B 不再繼續 */
    enum class Variant(val title: String) {
        NOW("現在"),
        A("A：日曆上方右側"),
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    // ---------- 圖示：大小跟著字（字體放大時一起放大） ----------

    /** 24×24 的線條圖示，畫成 [sizePx] 大小 */
    private inner class Icon(c: Context, private val sizePx: Int, private val color: Int, private val draw: (Canvas, Paint) -> Unit) : View(c) {
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(sizePx, sizePx)
        override fun onDraw(canvas: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; this.color = this@Icon.color
            }
            canvas.save()
            canvas.scale(sizePx / 24f, sizePx / 24f)
            draw(canvas, p)
            canvas.restore()
        }
    }

    private fun pin(c: Canvas, p: Paint) {
        c.drawPath(Path().apply { moveTo(12f, 21f); cubicTo(5f, 13f, 5.5f, 4f, 12f, 4f); cubicTo(18.5f, 4f, 19f, 13f, 12f, 21f) }, p)
        c.drawCircle(12f, 10f, 2.4f, p)
    }

    private fun chevron(c: Canvas, p: Paint) {
        c.drawPath(Path().apply { moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f) }, p)
    }

    /** 城市入口「⌖ 臺北市 ⌄」：整塊可以點，至少 48dp 高；圖示跟著字的大小 */
    private fun cityChip(c: Context): LinearLayout {
        val city = Cities.selected(c).name
        val text = TextView(c).apply {
            this.text = city
            textSize = 14f
            letterSpacing = 0.1f
            typeface = android.graphics.Typeface.SERIF
            setTextColor(c.getColor(R.color.ink))
        }
        val icon = (text.textSize * 1.15f).toInt()
        return LinearLayout(c).apply {
            id = View.generateViewId()
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(48f).toInt()
            minimumWidth = dp(48f).toInt()
            setPadding(dp(12f).toInt(), 0, dp(12f).toInt(), 0)
            isClickable = true
            isFocusable = true
            contentDescription = "地點：$city。天氣與日出日落依這個城市計算。按兩下可以換城市"
            val soft = c.getColor(R.color.ink_soft)
            addView(Icon(c, icon, soft, ::pin))
            // 間隔要給固定大小：一般 View 用 wrap_content 會把剩下的寬度全部吃掉
            addView(View(c), LinearLayout.LayoutParams(dp(4f).toInt(), 1))
            addView(text)
            addView(Icon(c, (icon * 0.9f).toInt(), soft, ::chevron))
        }
    }

    // ---------- 開 App、套方案 ----------

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

    /** 套用方案，回傳城市入口（現在的版面是城市那一列） */
    private fun apply(a: MainActivity, v: Variant): View {
        if (v == Variant.NOW) return a.findViewById(R.id.city)
        // A：拿掉日曆下方的天氣列與城市列（天氣、溫度、日出日落還在日曆卡片裡），城市入口放在日曆上方右側
        a.findViewById<View>(R.id.weather).visibility = View.GONE
        a.findViewById<View>(R.id.city).visibility = View.GONE
        val chip = cityChip(a)
        val row = LinearLayout(a).apply { gravity = Gravity.END; addView(chip) }
        a.content.addView(row, a.content.indexOfChild(a.findViewById(R.id.page)))
        return chip
    }

    /** 整個首頁（ScrollView 的完整內容，不只畫面那一截） */
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

    /** 文字被截斷或換行（不縮字，放不下就換行；這裡記下來給審核看） */
    private fun textState(tv: TextView): String {
        val l = tv.layout ?: return "—"
        return when {
            l.lineCount > 0 && l.getEllipsisCount(l.lineCount - 1) > 0 -> "「…」截斷"
            l.lineCount > 1 -> "換成 ${l.lineCount} 行"
            tv.paint.measureText(tv.text.toString()) > tv.width - tv.totalPaddingLeft - tv.totalPaddingRight + 1 -> "被切掉"
            else -> "完整"
        }
    }

    private fun texts(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 34f }

    /** 幾張整頁圖排成一列（縮成同樣的寬），上面加標題 */
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
    fun candidates() {
        val report = StringBuilder("# 首頁城市入口候選：量測（Robolectric）\n\n")
        report.append("觸控範圍要 ≥ 48×48dp。文字不縮小；放不下就換行（記在「文字」欄）。\n\n")
        report.append("| 設定 | 方案 | 城市入口（dp） | ≥ 48dp | 城市入口文字 | 前一天／後一天／回到最上面 | 整頁高（dp） |\n|---|---|---|---|---|---|---|\n")
        for ((i, cfg) in configs.withIndex()) {
            val shots = Variant.entries.map { v ->
                val a = open(cfg)
                val entry = apply(a, v)
                val shot = capture(a)
                val d = context.resources.displayMetrics.density
                val r = rectIn(a.content, entry)
                val entryTexts = texts(entry).filter { it.visibility == View.VISIBLE }
                val nav = listOf(R.id.prev, R.id.next, R.id.today).map { a.findViewById<Button>(it) }.filter { it.visibility == View.VISIBLE }
                val visible = entry.visibility == View.VISIBLE && entry.isShown
                report.append(
                    "| ${cfg.name} | ${v.title} | " +
                        (if (visible) "%.0f×%.0f".format(r.width() / d, r.height() / d) else "（收起來）") + " | " +
                        (if (!visible) "—" else if (r.width() / d >= 47.5f && r.height() / d >= 47.5f) "是" else "**否**") + " | " +
                        (if (visible) entryTexts.joinToString("、") { textState(it) } else "—") + " | " +
                        nav.joinToString("、") { "${it.text.toString().trim()}：${textState(it)}" } + " | " +
                        "%.0f |\n".format(shot.height / d),
                )
                v.title to shot
            }
            sheet("${i + 1}-home", "首頁 ${cfg.name}（整頁，縮成同樣寬；Robolectric）", shots)
        }
        report.append("\n「現在」的城市入口是日曆下方那一列文字（約 36dp 高）。\n")
        File(out, "report.md").writeText(report.toString())
        // 方便直接審核：A 在四種設定的整頁並排
        sheet("6-a-all", "方案 A 整頁：411dp／320dp × 字體 100%／200%（縮成同樣寬；Robolectric）", configs.map { cfg -> cfg.name to open(cfg).let { a -> apply(a, Variant.A); capture(a) } })
    }

    /**
     * 天氣彩蛋入口（2026-10-31 審核）：故事卡上的墨丸。量它的可點範圍（要 ≥ 48×48dp）、跟「卸下包袱」入口的距離（不能重疊），
     * 四種設定各一張故事卡的標示圖（藍：墨丸可點範圍；橘：包袱入口）。
     */
    @Test
    fun storyCardEntry() {
        val md = StringBuilder("# 故事卡上的墨丸：可點範圍與包袱入口（方案 A、第 14 頁；Robolectric）\n\n")
        md.append("| 設定 | 墨丸（dp） | ≥ 48dp | 包袱入口（dp） | 包袱入口 ≥ 48dp 高 | 兩者重疊 | 最近距離（dp） | 故事卡上緣在第一個畫面裡 |\n|---|---|---|---|---|---|---|---|\n")
        val crops = configs.map { cfg ->
            val a = open(cfg)
            apply(a, Variant.A)
            val shot = capture(a)
            val root = a.content
            val d = context.resources.displayMetrics.density
            val m = rectIn(root, a.findViewById(R.id.story_mowan))
            val b = rectIn(root, a.findViewById(R.id.story_bundle))
            val card = rectIn(root, a.findViewById<View>(R.id.story_mowan).parent as View)
            val overlap = Rect.intersects(m, b)
            val gapX = maxOf(0, maxOf(b.left - m.right, m.left - b.right))
            val gapY = maxOf(0, maxOf(b.top - m.bottom, m.top - b.bottom))
            val gap = Math.hypot(gapX.toDouble(), gapY.toDouble()) / d
            val screenH = context.resources.displayMetrics.heightPixels
            md.append(
                "| ${cfg.name} | %.0f×%.0f | %s | %.0f×%.0f | %s | %s | %.0f | %s |\n".format(
                    m.width() / d, m.height() / d, if (m.width() / d >= 47.5 && m.height() / d >= 47.5) "是" else "**否**",
                    b.width() / d, b.height() / d, if (b.height() / d >= 47.5) "是" else "**否**",
                    if (overlap) "**是**" else "否", gap, if (card.top < screenH) "是" else "否（要往下捲）",
                ),
            )
            val c = Canvas(shot)
            c.drawRect(m, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF2B5FA8.toInt(); strokeWidth = dp(2f) })
            c.drawRect(b, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFC0701E.toInt(); strokeWidth = dp(2f); pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f) })
            val pad = dp(10f).toInt()
            cfg.name to Bitmap.createBitmap(shot, 0, maxOf(0, card.top - pad), shot.width, minOf(shot.height - maxOf(0, card.top - pad), card.height() + 2 * pad))
        }
        File(out, "7-story-entry.md").writeText(md.toString() + "\n藍框：墨丸（點一下觸發天氣彩蛋）；橘色虛線：「卸下包袱」入口。\n")
        sheet("7-story-entry", "故事卡上的墨丸（藍）與包袱入口（橘）：四種設定", crops)
    }
}
