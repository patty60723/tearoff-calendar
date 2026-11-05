package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * P7-7（2026-11-05 審核：使用者不滿意畫面位移）：無位移的日曆互動。**只做候選，不改正式版面、不更新 golden**。
 * 候選都在測試裡用正式畫面組出來（每次換頁、撕頁之後再套一次，跟正式實作會做的事一樣）。
 *
 * 位移的來源（現況 PR #4）：
 * 1. 上方說明文字：不同狀態的文字長短不同，字體 200% 時一行／兩行會變。
 * 2. 「回到最上面那頁」：GONE／VISIBLE 會推動下面一列。
 * 3. 日曆頁高度：只有今天那頁有天氣三行，候選 C 只替今天加高。
 * 4. 「或按這裡撕下」：撕完 GONE，下面的故事卡往上移。
 *
 * 候選：
 * - 全部共用：A1 固定頁面高度（每一頁都照「天氣三行」保留，今天、昨天、節日一樣高）；撕頁按鈕撕完改 INVISIBLE（保留位置）。
 * - X：說明文字固定高度（所有狀態裡最高的）＋「回到最上面那頁」自己一列、固定佔位（最上面那頁時 INVISIBLE）。
 * - Y：說明文字固定高度＋「回到最上面那頁」整合到導覽列中間（「↑」圖示按鈕 48dp，最上面那頁時 INVISIBLE）。
 * - Z：說明文字與「回到最上面那頁」共用同一格固定高度（看別天時這一格顯示按鈕，在最上面時顯示說明）。
 * - Z′：Z ＋ 精簡說明文字（§C 的提案）。
 * 圖與報告：app/build/screenshots/p7-7-stable/。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class P77StableShotsTest {
    private val out = File("build/screenshots/p7-7-stable").apply { mkdirs() }
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

    private fun open(cfg: Cfg, day: LocalDate, torn: Boolean): MainActivity {
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        RuntimeEnvironment.setFontScale(cfg.fontScale)
        AppClock.setFakeToday(context, day)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
        StoryBook(context).apply { jumpTo(13, day); if (torn) tear(day) }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private fun relayout(a: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY))
        v.layout(v.left, v.top, v.right, v.bottom)
    }

    private val MainActivity.content get() = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout
    private fun pad(a: MainActivity) = a.findViewById<TearPageView>(R.id.page)

    // ---------- 候選的組件 ----------

    /** A1：每一頁都照「天氣三行」保留底部空間（今天、昨天、節日一樣高）；100% 時夠用，不加高 */
    private fun fixPageHeight(a: MainActivity) {
        val p = pad(a)
        val page = p.getChildAt(0) ?: return
        val space = page.findViewById<View>(R.id.bottom_space) ?: return
        val sun = page.findViewById<TextView>(R.id.sun) ?: return
        val probe = TextView(a).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, sun.textSize)
            typeface = sun.typeface
            letterSpacing = sun.letterSpacing
            setLineSpacing(sun.lineSpacingExtra, sun.lineSpacingMultiplier)
            includeFontPadding = sun.includeFontPadding
            setPadding(sun.paddingLeft, sun.paddingTop, sun.paddingRight, sun.paddingBottom)
            text = "晴 25°\n日出 06:03\n日落 17:11"
        }
        probe.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val margin = (sun.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
        val base = (84 * d).toInt()
        val h = maxOf(base, probe.measuredHeight + margin + (4 * d).toInt())
        space.layoutParams = space.layoutParams.apply { height = h }
        p.extraHeight = h - base
    }

    private fun hintStrings(a: MainActivity, simple: Boolean) = if (simple) simplified.values.toList() + a.getString(R.string.hint_cover) else listOf(
        a.getString(R.string.hint_tear), a.getString(R.string.hint_more, 9), a.getString(R.string.tear_done),
        a.getString(R.string.hint_browsing, 12, 31), a.getString(R.string.hint_cover),
    )

    /** §C 的精簡提案（原文 → 精簡）；「在看 m/d 的頁面」在 Z 系列由「回到最上面那頁」按鈕取代 */
    private val simplified = linkedMapOf(
        "hint_tear" to "從左下角或右下角往上撕",
        "hint_more" to "還有 9 頁沒撕",
        "tear_done" to "明天再來撕下一頁",
    )

    private fun simplify(a: MainActivity) {
        val hint = a.findViewById<TextView>(R.id.hint)
        val t = hint.text.toString()
        hint.text = when {
            t == a.getString(R.string.hint_tear) -> simplified.getValue("hint_tear")
            t == a.getString(R.string.tear_done) -> simplified.getValue("tear_done")
            t.startsWith("還有") -> t.substringBefore("，")
            else -> t
        }
    }

    /** 說明文字的高度固定成「所有狀態裡最高的」 */
    private fun fixHint(a: MainActivity, strings: List<String>): Int {
        val hint = a.findViewById<TextView>(R.id.hint)
        val w = a.content.width - a.content.paddingLeft - a.content.paddingRight
        val keep = hint.text
        val h = strings.maxOf { hint.text = it; hint.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); hint.measuredHeight }
        hint.text = keep
        hint.layoutParams = hint.layoutParams.apply { height = h }
        hint.gravity = Gravity.CENTER
        return h
    }

    private fun keepTearSlot(a: MainActivity) {
        a.findViewById<Button>(R.id.tear).apply { if (visibility == View.GONE) visibility = View.INVISIBLE }
    }

    private fun todayGoneToInvisible(a: MainActivity) {
        a.findViewById<Button>(R.id.today).apply { if (visibility == View.GONE) visibility = View.INVISIBLE }
    }

    // ---------- 候選 ----------

    data class Cand(val id: String, val name: String, val setup: (MainActivity) -> Unit, val each: (MainActivity) -> Unit)

    private val cands = listOf(
        Cand("now", "現況（PR #4）", {}, {}),
        Cand("X", "X：固定一列", { a -> fixHint(a, hintStrings(a, false)) }, { a -> fixPageHeight(a); todayGoneToInvisible(a); keepTearSlot(a) }),
        Cand("Y", "Y：整合到導覽列（↑）", { a ->
            fixHint(a, hintStrings(a, false))
            val today = a.findViewById<Button>(R.id.today)
            val nav = a.findViewById<View>(R.id.prev).parent as LinearLayout
            (today.parent as ViewGroup).removeView(today)
            today.text = "↑"
            today.contentDescription = a.getString(R.string.today)
            today.minWidth = (48 * d).toInt(); today.minimumWidth = (48 * d).toInt()
            today.minHeight = (48 * d).toInt(); today.minimumHeight = (48 * d).toInt()
            today.setPadding(0, 0, 0, 0)
            // [‹ 前一天][空白][↑][空白][後一天 ›]
            nav.addView(today, 2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            nav.addView(Space(a), 3, LinearLayout.LayoutParams(0, 0, 1f))
        }, { a -> fixPageHeight(a); todayGoneToInvisible(a); keepTearSlot(a) }),
        Cand("Z", "Z：說明與按鈕共用一格", { a -> slot(a, hintStrings(a, false)) }, { a -> fixPageHeight(a); slotEach(a); keepTearSlot(a) }),
        Cand("Z2", "Z′：Z＋精簡文字", { a -> slot(a, hintStrings(a, true)) }, { a -> simplify(a); fixPageHeight(a); slotEach(a); keepTearSlot(a) }),
    )

    /** Z：說明文字與「回到最上面那頁」放進同一格（FrameLayout），格子高度＝max(說明文字最高的狀態, 按鈕) */
    private fun slot(a: MainActivity, strings: List<String>) {
        val hint = a.findViewById<TextView>(R.id.hint)
        val today = a.findViewById<Button>(R.id.today)
        val content = a.content
        val at = content.indexOfChild(hint)
        content.removeView(hint)
        (today.parent as ViewGroup).removeView(today)
        val frame = FrameLayout(a)
        frame.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        frame.addView(today, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        content.addView(frame, at, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        relayout(a)
        val w = content.width - content.paddingLeft - content.paddingRight
        val keep = hint.text
        val hh = strings.maxOf { hint.text = it; hint.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)); hint.measuredHeight }
        hint.text = keep
        val vis = today.visibility
        today.visibility = View.VISIBLE
        today.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val bh = today.measuredHeight
        today.visibility = vis
        frame.layoutParams = frame.layoutParams.apply { height = maxOf(hh, bh) }
    }

    private fun slotEach(a: MainActivity) {
        val hint = a.findViewById<TextView>(R.id.hint)
        val today = a.findViewById<Button>(R.id.today)
        hint.visibility = if (today.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
    }

    // ---------- 量測 ----------

    data class Pos(val pageTop: Int, val pageH: Int, val cityTop: Int, val storyTop: Int, val navTop: Int)

    private fun pos(a: MainActivity): Pos {
        val c = a.content
        val r = GlyphInk.rectIn(c, pad(a))
        val story = a.findViewById<View>(R.id.story_mowan).parent as View
        return Pos(r.top, r.height(), GlyphInk.rectIn(c, a.findViewById(R.id.city)).top, GlyphInk.rectIn(c, story).top, GlyphInk.rectIn(c, a.findViewById(R.id.prev)).top)
    }

    private fun capture(a: MainActivity, h: Int): Bitmap {
        val c = a.content
        val full = Bitmap.createBitmap(c.width, c.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(0xFFEDE8E0.toInt()); c.draw(this) } }
        return Bitmap.createBitmap(full, 0, 0, full.width, minOf(h, full.height))
    }

    /** 按鈕與說明文字：字形完整、按鈕 ≥ 48dp */
    private fun ui(a: MainActivity): List<String> {
        val c = a.content
        val problems = mutableListOf<String>()
        for ((id, n) in listOf(R.id.prev to "前一天", R.id.next to "後一天", R.id.today to "回到最上面那頁", R.id.hint to "說明", R.id.city to "城市")) {
            val v = a.findViewById<TextView>(id)
            if (v.visibility != View.VISIBLE) continue
            val st = GlyphInk.state(c, v)
            if (st != "完整") problems += "$n$st"
            if (v is Button && (v.height / d < 47.5f || v.width / d < 47.5f)) problems += "$n 只有 %.0f×%.0fdp".format(v.width / d, v.height / d)
            if (v is Button && (v.layout?.lineCount ?: 1) > 1) problems += "$n 換成 ${v.layout?.lineCount} 行"
        }
        return problems
    }

    private val states = listOf("撕頁前", "撕頁後", "昨天", "回到今天")

    @Test
    fun stableCandidates() {
        val report = StringBuilder("# 無位移的日曆互動：候選（Robolectric，**只是候選**）\n\n")
        report.append("四個狀態：撕頁前（最上面是昨天那頁，還沒撕）→ 撕頁後（今天那頁）→ 按「前一天」看昨天 → 按「回到最上面那頁」回到今天。\n")
        report.append("位置都是相對於首頁內容的上緣（dp）。「位移」＝四個狀態之間最大的差。\n\n")
        val sheets = LinkedHashMap<String, MutableList<Pair<String, List<Bitmap>>>>()
        for (cfg in cfgs) {
            report.append("## ${cfg.name}\n\n| 候選 | 頁面上緣（四個狀態） | 頁面高 | 故事卡上緣 | 頁面上緣位移 | 頁面高變化 | 故事卡位移 | 按鈕與文字 |\n|---|---|---|---|---|---|---|---|\n")
            val rows = mutableListOf<Pair<String, List<Bitmap>>>()
            val all = mutableListOf<Pair<Cand, List<Pos>>>()
            val shotsAll = mutableListOf<List<Bitmap>>()
            for (cand in cands) {
                val a = open(cfg, normal, torn = false)
                relayout(a)
                cand.setup(a); cand.each(a); relayout(a)
                val ps = mutableListOf<Pos>()
                val shots = mutableListOf<Bitmap>()
                val probs = mutableSetOf<String>()
                fun snap() { relayout(a); ps += pos(a); probs += ui(a); shots += capture(a, 99999) }
                snap()
                pad(a).peelAway(); ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS); cand.each(a); snap()
                a.findViewById<Button>(R.id.prev).performClick(); cand.each(a); snap()
                a.findViewById<Button>(R.id.today).performClick(); cand.each(a); snap()
                fun span(f: (Pos) -> Int) = ps.maxOf(f) - ps.minOf(f)
                report.append("| ${cand.name} | ${ps.joinToString(" → ") { "%.0f".format(it.pageTop / d) }} | ${ps.joinToString(" → ") { "%.0f".format(it.pageH / d) }} | ${ps.joinToString(" → ") { "%.0f".format(it.storyTop / d) }} | ${"%.0f".format(span { it.pageTop } / d)}dp | ${"%.0f".format(span { it.pageH } / d)}dp | ${"%.0f".format(span { it.storyTop } / d)}dp | ${probs.ifEmpty { setOf("完整、≥ 48dp") }.joinToString("、")} |\n")
                all += cand to ps
                shotsAll += shots
            }
            // 截圖：每個候選同樣的高度（到故事卡上緣下面 24dp），看得出位移
            val cropH = all.maxOf { it.second.maxOf { p -> p.storyTop } } + (24 * d).toInt()
            for ((i, cand) in cands.withIndex()) rows += cand.name to shotsAll[i].map { Bitmap.createBitmap(it, 0, 0, it.width, minOf(cropH, it.height)) }
            sheets[cfg.name] = rows
            report.append("\n")
        }
        File(out, "1-candidates.md").writeText(report.toString())
        for ((n, rows) in sheets) sheet("1-${n.replace("・", "-").replace("%", "")}", "${n}：撕頁前｜撕頁後｜昨天｜回到今天", states, rows)
    }

    /** A1：同一尺寸與字體下，今天、昨天、節日（春節）的頁面高度一樣；日期字形不比現況小；資訊完整 */
    @Test
    fun fixedHeightAcrossDays() {
        val report = StringBuilder("# A1 固定頁面高度：今天、昨天、節日\n\n| 設定 | 現況：今天／昨天／春節 | A1：今天／昨天／春節 | A1 日期字形（今天／昨天／春節） | 現況日期字形 | 碰撞與截斷 |\n|---|---|---|---|---|---|\n")
        for (cfg in cfgs) {
            fun measure(day: LocalDate, a1: Boolean, yesterday: Boolean): Triple<Int, Double, List<String>> {
                val a = open(cfg, day, torn = true)
                relayout(a)
                if (yesterday) { a.findViewById<Button>(R.id.prev).performClick(); relayout(a) }
                if (a1) { fixPageHeight(a); relayout(a) }
                val page = pad(a).getChildAt(0)
                val dayTv = page.findViewById<TextView>(R.id.day)
                val dayDp = GlyphInk.glyphs(page, dayTv).maxOf { it.height() } / d.toDouble()
                val texts = listOf(R.id.phrase, R.id.lunar, R.id.ji, R.id.label, R.id.sun, R.id.day, R.id.month, R.id.weekday, R.id.month_en)
                    .mapNotNull { id -> page.findViewById<TextView>(id)?.takeIf { it.visibility == View.VISIBLE && it.text.isNotEmpty() } }
                val probs = mutableListOf<String>()
                texts.forEach { if (GlyphInk.clipped(page, it) > 0) probs += "${it.text.toString().take(4)}被切" }
                val masks = texts.map { GlyphInk.mask(page, it) }
                for (i in masks.indices) for (j in i + 1 until masks.size) if (GlyphInk.overlap(masks[i], masks[j]) > 0) probs += "${texts[i].text.toString().take(3)}×${texts[j].text.toString().take(3)}"
                val m = GlyphInk.mask(page, page.findViewById(R.id.mowan_still))
                masks.forEachIndexed { i, k -> if (GlyphInk.overlap(k, m) > 0) probs += "${texts[i].text.toString().take(3)}×墨丸" }
                return Triple(pad(a).height, dayDp, probs)
            }
            val now = listOf(measure(normal, false, false), measure(normal, false, true), measure(spring, false, false))
            val a1 = listOf(measure(normal, true, false), measure(normal, true, true), measure(spring, true, false))
            report.append("| ${cfg.name} | ${now.joinToString("／") { "%.0f".format(it.first / d) }}dp | ${a1.joinToString("／") { "%.0f".format(it.first / d) }}dp | ${a1.joinToString("／") { "%.0f".format(it.second) }}dp | ${now.joinToString("／") { "%.0f".format(it.second) }}dp | ${a1.flatMap { it.third }.ifEmpty { listOf("無") }.distinct().joinToString("、")} |\n")
        }
        report.append("\n昨天那頁只有日出日落兩行：A1 下面多出一行的留白。春節那頁多一行節日名稱，日期數字照原本的規則讓位（字體 100% 時也是這樣）。\n")
        File(out, "2-fixed-height.md").writeText(report.toString())
    }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 26f }

    private fun sheet(name: String, title: String, cols: List<String>, rows: List<Pair<String, List<Bitmap>>>, colW: Int = 300) {
        val scaled = rows.map { (n, bs) -> n to bs.map { Bitmap.createScaledBitmap(it, colW, maxOf(1, it.height * colW / it.width), true) } }
        val rowH = scaled.map { it.second.maxOf { b -> b.height } + 52 }
        val left = 260
        val sheet = Bitmap.createBitmap(left + cols.size * (colW + 20), 96 + rowH.sum(), Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(0xFFFBF8F2.toInt())
            drawText(title, 20f, 34f, words)
            cols.forEachIndexed { i, c -> drawText(c, left + i * (colW + 20f), 78f, words) }
            var y = 96f
            scaled.forEachIndexed { r, (n, bs) ->
                drawText(n, 20f, y + 40f, words)
                bs.forEachIndexed { i, b -> drawBitmap(b, left + i * (colW + 20f), y + 10f, null) }
                y += rowH[r]
            }
        }
        File(out, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------- 2026-11-05 審核：「回到最上面那頁」的顏色（紅色太突出，不是警告） ----------

    private fun lum(c: Int): Double {
        fun ch(x: Int): Double { val s = x / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }

    private fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    /** 按鈕文字實際畫到的像素位置上，文字色對背景（拿掉這個按鈕的文字後）的最差對比 */
    private fun worst(a: MainActivity, b: TextView): Double {
        val c = a.content
        val ink = GlyphInk.mask(c, b)
        val keep = b.currentTextColor
        b.setTextColor(0x00000000)
        val bg = capture(a, 99999)
        b.setTextColor(keep)
        val px = IntArray(bg.width * bg.height).also { bg.getPixels(it, 0, bg.width, 0, 0, bg.width, bg.height) }
        var w = Double.MAX_VALUE
        for (i in ink.indices) if (ink[i] && i < px.size) w = minOf(w, ratio(keep, px[i]))
        return w
    }

    data class Tone(val id: String, val name: String, val today: Int, val nav: Int?)

    private val tones = listOf(
        Tone("red", "現況：紅（#B8322A）", 0xFFB8322A.toInt(), null),
        Tone("K1", "K1：同前一天／後一天（ink_soft #8C8378）", 0xFF8C8378.toInt(), null),
        Tone("K2", "K2：同城市入口（ink #2E2A26）", 0xFF2E2A26.toInt(), null),
        Tone("K3", "K3：深灰（ink_mid #4A433D）", 0xFF4A433D.toInt(), null),
        Tone("K3n", "K3＋：深灰，前一天／後一天也改深灰", 0xFF4A433D.toInt(), 0xFF4A433D.toInt()),
        Tone("K4", "K4：當月月份色（10 月 deep #8C5530）", 0xFF8C5530.toInt(), null),
    )

    @Test
    fun todayButtonTones() {
        val report = StringBuilder("# 「回到最上面那頁」的顏色候選（版面用 A1＋Z′，看昨天那頁的狀態）\n\n")
        report.append("對比：按鈕文字實際畫到的像素背後的背景（桌面色 #EDE8E0），最差像素。門檻 4.5、設計目標 4.8。\n\n")
        report.append("| 候選 | " + cfgs.joinToString(" | ") { it.name } + " | 前一天／後一天 |\n|---|" + "---|".repeat(cfgs.size) + "---|\n")
        val rows = mutableListOf<Pair<String, List<Bitmap>>>()
        val cells = HashMap<String, MutableList<String>>()
        val navRatio = HashMap<String, String>()
        for (t in tones) {
            val shots = mutableListOf<Bitmap>()
            for (cfg in cfgs) {
                val a = open(cfg, normal, torn = true)
                relayout(a)
                val z = cands.first { it.id == "Z2" }
                z.setup(a); z.each(a); relayout(a)
                a.findViewById<Button>(R.id.prev).performClick(); z.each(a); relayout(a)
                val today = a.findViewById<Button>(R.id.today)
                today.setTextColor(t.today)
                t.nav?.let { c -> a.findViewById<Button>(R.id.prev).setTextColor(c); a.findViewById<Button>(R.id.next).setTextColor(c) }
                relayout(a)
                cells.getOrPut(t.id) { mutableListOf() } += "%.2f".format(worst(a, today))
                if (cfg == cfgs.first()) navRatio[t.id] = "%.2f".format(worst(a, a.findViewById(R.id.prev)))
                val story = a.findViewById<View>(R.id.story_mowan).parent as View
                val pageBottom = GlyphInk.rectIn(a.content, pad(a)).top + (120 * d).toInt()
                shots += capture(a, minOf(pageBottom, GlyphInk.rectIn(a.content, story).top))
            }
            report.append("| ${t.name} | ${cells.getValue(t.id).joinToString(" | ")} | ${navRatio[t.id]} |\n")
            rows += t.name to shots
        }
        // 月份色（K4）每個月對桌面色的對比
        report.append("\n## K4：每個月的月份色（deep）對桌面色 #EDE8E0\n\n| 月 | deep | 對比 |\n|---|---|---|\n")
        for (m in 1..12) { val c = if (m == 8) 0xFF84693D.toInt() else Palettes.forMonth(m).deep; report.append("| $m | #%06X | %.2f |\n".format(c and 0xFFFFFF, ratio(c, 0xFFEDE8E0.toInt()))) }
        File(out, "3-today-tones.md").writeText(report.toString())
        sheet("3-today-tones", "「回到最上面那頁」的顏色（A1＋Z′，看昨天那頁）：" + cfgs.joinToString("｜") { it.name }, cfgs.map { it.name }, rows, colW = 260)
    }
}
