package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
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
 * P7 規劃用的視覺對照（不是正式 UI）：首頁日曆下方「天氣」與「城市」兩列，現在的樣子與改版方案。
 * 方案的 View 都在這個測試裡用程式組出來，只用 App 現有的顏色與襯線字，正式程式完全沒改。
 * 存到 app/build/screenshots/p7-home。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRowsMockShotsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val out = File("build/screenshots/p7-home").apply { mkdirs() }

    private val desk = 0xFFEDE8E0.toInt()
    private val ink = 0xFF2E2A26.toInt()
    private val inkSoft = 0xFF8C8378.toInt()
    private val edge = 0xFFE3DACA.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        Cities.select(context, Cities.all.first { it.name == "臺北市" })
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun dp(v: Float) = v * context.resources.displayMetrics.density

    // ---------- 小圖示（細線、手繪感，跟日曆的線條一樣粗細） ----------

    /** 線條小圖示：[draw] 在 24×24 的座標裡畫 */
    private inner class Glyph(c: Context, private val sizeDp: Float, private val color: Int, private val alphaF: Float = 1f, private val draw: (Canvas, Paint) -> Unit) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(sizeDp).toInt(), dp(sizeDp).toInt())
        override fun onDraw(canvas: Canvas) {
            paint.color = color
            paint.alpha = (255 * alphaF).toInt()
            paint.strokeWidth = 1.6f * 24 / width
            canvas.save()
            canvas.scale(width / 24f, height / 24f)
            draw(canvas, paint)
            canvas.restore()
        }
    }

    private fun cloud(c: Canvas, p: Paint) {
        val path = Path().apply {
            moveTo(6f, 17f); cubicTo(2.5f, 17f, 2.5f, 11.5f, 6.5f, 11.8f)
            cubicTo(7f, 7.5f, 13f, 6.5f, 14.5f, 10.2f); cubicTo(17.5f, 8.8f, 21.5f, 11.5f, 19.5f, 14.6f)
            cubicTo(21f, 17.4f, 18f, 17.2f, 17.6f, 17f); close()
        }
        c.drawPath(path, p)
    }

    private fun rain(c: Canvas, p: Paint) {
        c.save(); c.translate(0f, -3f); cloud(c, p); c.restore()
        for (x in listOf(8f, 12.5f, 17f)) c.drawLine(x, 17f, x - 1.2f, 21f, p)
    }

    /** 地點：手繪的定位針 */
    private fun pin(c: Canvas, p: Paint) {
        c.drawPath(Path().apply { moveTo(12f, 21f); cubicTo(5f, 13f, 5.5f, 4f, 12f, 4f); cubicTo(18.5f, 4f, 19f, 13f, 12f, 21f) }, p)
        c.drawCircle(12f, 10.3f, 2.2f, p)
    }

    /** 向下箭頭 ⌄ */
    private fun chevron(c: Canvas, p: Paint) {
        c.drawPath(Path().apply { moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f) }, p)
    }

    /** 手繪星芒 ✦：四個尖角、邊有一點點彎 */
    private fun sparkle(c: Canvas, p: Paint) {
        val f = Paint(p).apply { style = Paint.Style.FILL }
        c.drawPath(Path().apply {
            moveTo(12f, 2.5f); quadTo(13.2f, 10.6f, 21.5f, 12f); quadTo(13.2f, 13.4f, 12f, 21.5f)
            quadTo(10.8f, 13.4f, 2.5f, 12f); quadTo(10.8f, 10.6f, 12f, 2.5f); close()
        }, f)
    }

    private fun text(c: Context, s: String, sp: Float, color: Int, spacing: Float = 0.1f) = TextView(c).apply {
        text = s; textSize = sp; setTextColor(color); typeface = Typeface.SERIF; letterSpacing = spacing; includeFontPadding = false
    }

    private fun row(c: Context, vararg views: View, gravity: Int = Gravity.CENTER) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity or Gravity.CENTER_VERTICAL
        minimumHeight = dp(48f).toInt()
        val pad = dp(12f).toInt()
        setPadding(pad, 0, pad, 0)
        for (v in views) addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4f).toInt() })
    }

    private fun gap(c: Context, w: Float) = View(c).apply { minimumWidth = dp(w).toInt() }

    private fun divider(c: Context) = View(c).apply { setBackgroundColor(edge) }

    // ---------- 方案 ----------

    /** 天氣：圖示＋溫度＋範圍，後面一個很小、很淡的星芒（整列都可以點） */
    private fun weatherRow(c: Context, mood: Mood, now: String, range: String, sparkleAt: String) = row(
        c,
        Glyph(c, 18f, inkSoft) { cv, p -> if (mood == Mood.RAIN) rain(cv, p) else cloud(cv, p) },
        text(c, "${mood.keyword} $now", 15f, ink),
        text(c, range, 13f, inkSoft, 0.05f),
        *if (sparkleAt == "end") arrayOf<View>(Glyph(c, 7f, inkSoft, 0.55f, ::sparkle)) else emptyArray(),
    ).apply {
        contentDescription = "今天${mood.keyword}，$now，$range。點一下，墨丸會對天氣做出反應"
        if (sparkleAt == "corner") {
            // 星芒放在圖示右上角（疊上去，不佔寬度）
            val frame = FrameLayout(c)
            val icon = getChildAt(0)
            removeViewAt(0)
            frame.addView(icon)
            frame.addView(Glyph(c, 6f, inkSoft, 0.55f, ::sparkle), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { topMargin = -dp(3f).toInt(); marginEnd = -dp(4f).toInt() })
            frame.clipChildren = false
            addView(frame, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6f).toInt() })
            clipChildren = false
        }
    }

    /** 城市：⌖ 臺北市 ⌄，右邊低對比的「天氣・日出日落」 */
    private fun cityRowSplit(c: Context, city: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48f).toInt()
        val pad = dp(24f).toInt()
        setPadding(pad, 0, pad, 0)
        addView(Glyph(c, 16f, ink, 0.8f, ::pin))
        addView(text(c, city, 14f, ink), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(4f).toInt() })
        addView(Glyph(c, 14f, ink, 0.8f, ::chevron))
        addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        addView(text(c, "天氣・日出日落", 11f, inkSoft, 0.05f))
        contentDescription = "地點：$city。天氣與日出日落依這個城市。點兩下可以換城市"
    }

    /** 城市：置中，⌖ 臺北市 ⌄ 下面一行小字 */
    private fun cityRowStacked(c: Context, city: String) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        minimumHeight = dp(48f).toInt()
        addView(row(c, Glyph(c, 16f, ink, 0.8f, ::pin), text(c, city, 14f, ink), Glyph(c, 14f, ink, 0.8f, ::chevron)).apply { minimumHeight = 0 })
        addView(text(c, "天氣・日出日落", 11f, inkSoft, 0.05f).apply { gravity = Gravity.CENTER })
        contentDescription = "地點：$city。天氣與日出日落依這個城市。點兩下可以換城市"
    }

    private fun block(c: Context, vararg rows: View, dividerWidthFraction: Float = 0.36f) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(desk)
        val w = c.resources.displayMetrics.widthPixels
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(divider(c), LinearLayout.LayoutParams((w * dividerWidthFraction).toInt(), 1).apply { gravity = Gravity.CENTER_HORIZONTAL })
            addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun render(v: View, w: Int): Bitmap {
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        return Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(desk); v.draw(this) } }
    }

    /** 觸控範圍標示：每個可以點的列畫一個虛線框（≥ 48dp） */
    private fun withTargets(v: ViewGroup, w: Int): Bitmap {
        val b = render(v, w)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFB8322A.toInt(); strokeWidth = dp(1f); pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(3f)), 0f) }
        val c = Canvas(b)
        for (i in 0 until v.childCount) {
            val child = v.getChildAt(i)
            if (child.contentDescription == null) continue
            c.drawRect(RectF(child.left + 2f, child.top + 2f, child.right - 2f, child.bottom - 2f), p)
        }
        return b
    }

    /** 現在的 App：日曆下方的兩列（實際的 MainActivity） */
    private fun current(mood: Mood): Bitmap {
        MowanSettings.setStoryCard(context, true)
        WeatherService.setFakeMood(context, mood)
        AppClock.setFakeToday(context, LocalDate.of(2026, 10, 21))
        StoryBook(context).jumpTo(14, LocalDate.of(2026, 10, 21))
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val weather = a.findViewById<View>(R.id.weather)
        val city = a.findViewById<View>(R.id.city)
        // 小螢幕、大字體時這兩列在畫面外：畫 ScrollView 的整個內容
        val scroll = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val content = scroll.getChildAt(0)
        val full = Bitmap.createBitmap(content.width, content.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(desk); content.draw(this) } }
        fun top(v: View): Int {
            var y = 0
            var cur = v
            while (cur !== content) { y += cur.top; cur = cur.parent as View }
            return y
        }
        val y0 = (top(weather) - dp(6f)).toInt().coerceAtLeast(0)
        val y1 = (top(city) + city.height + dp(6f)).toInt().coerceAtMost(full.height)
        return Bitmap.createBitmap(full, 0, y0, full.width, y1 - y0)
    }

    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 34f }
    private val note = Paint(label).apply { textSize = 26f; color = 0xFF6B6B6B.toInt() }

    private fun sheet(name: String, title: String, items: List<Triple<String, String, Bitmap>>) {
        val w = items.maxOf { it.third.width } + 40
        val h = items.sumOf { it.third.height + 110 } + 70
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(0xFFFFFFFF.toInt())
            drawText(title, 20f, 46f, label)
            var y = 80f
            for ((head, sub, bm) in items) {
                drawText(head, 20f, y + 34f, label)
                drawText(sub, 20f, y + 70f, note)
                bm.density = b.density
                drawBitmap(bm, 20f, y + 90f, null)
                y += bm.height + 110
            }
        }
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun variants(mood: Mood, city: String): List<Triple<String, String, Bitmap>> {
        val c = context
        val w = c.resources.displayMetrics.widthPixels
        Cities.select(c, Cities.all.first { it.name == city })
        val now = current(mood)
        // 跟現在那張同一份天氣資料
        val weather = WeatherService.current(c)!!
        val temp = "${weather.now}°"
        val range = "${weather.min}–${weather.max}°"
        return listOf(
            Triple("現在", "「${weather.long}　點我看看 ›」與「天氣和日出日落依$city　換城市 ›」", now),
            Triple("方案 1（建議）", "天氣：圖示＋溫度＋範圍，尾端極小星芒；城市：⌖ 城市 ⌄ 靠左、右邊小字；中間極淡分隔線", render(block(c, weatherRow(c, mood, temp, range, "end"), cityRowSplit(c, city)), w)),
            Triple("方案 2", "天氣：星芒疊在圖示右上角；城市：置中兩行", render(block(c, weatherRow(c, mood, temp, range, "corner"), cityRowStacked(c, city)), w)),
            Triple("方案 1 的觸控範圍（紅色虛線，每一列 ≥ 48dp）", "TalkBack：天氣列「今天${mood.keyword}，$temp，$range。點一下，墨丸會對天氣做出反應」；城市列「地點：$city…點兩下可以換城市」",
                withTargets(block(c, weatherRow(c, mood, temp, range, "end"), cityRowSplit(c, city)), w)),
        )
    }

    @Test
    fun phone() {
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        sheet("1-phone-411dp", "首頁日曆下方：現在 vs 方案（w411dp xxhdpi，字體 100%）", variants(Mood.CLOUDY, "臺北市"))
    }

    @Test
    fun smallScreenAndLargeFont() {
        RuntimeEnvironment.setQualifiers("w320dp-h640dp-xhdpi")
        RuntimeEnvironment.setFontScale(1.3f)
        sheet("2-small-320dp-font130", "小螢幕 w320dp、字體 130%（雷雨、長字串）", variants(Mood.THUNDER, "連江縣"))
        RuntimeEnvironment.setQualifiers("w360dp-h740dp-xxhdpi")
        RuntimeEnvironment.setFontScale(2f)
        sheet("3-360dp-font200", "w360dp、字體 200%（雨）", variants(Mood.RAIN, "臺北市"))
    }
}
