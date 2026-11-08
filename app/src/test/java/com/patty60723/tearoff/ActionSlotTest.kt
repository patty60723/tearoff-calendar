package com.patty60723.tearoff

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * P7-7 V2b＋A1（2026-11-07 核准）：日曆下方的固定高度操作區、首次引導、撕頁後的無障礙焦點。
 * 411dp／320dp × 字體 100%／200%；四個狀態（撕頁前、撕頁後、昨天、回到今天）日曆上緣、日曆高、操作區、故事卡上緣都不動。
 * 報告：app/build/screenshots/p7-7-v2b/report.md。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActionSlotTest {
    private val out = File("build/screenshots/p7-7-v2b").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val today = LocalDate.of(2027, 10, 21)
    private val d get() = context.resources.displayMetrics.density
    private val opened = mutableListOf<ActivityController<MainActivity>>()

    @After
    fun cleanUp() {
        for (c in opened) runCatching { c.pause().stop().destroy() }
        opened.clear()
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        RuntimeEnvironment.setFontScale(1f)
        runCatching { shadowOf(context.getSystemService(AccessibilityManager::class.java)).setEnabled(false) }
    }

    data class Cfg(val name: String, val qualifiers: String, val fontScale: Float)

    private val cfgs = listOf(
        Cfg("411dp・100%", "w411dp-h891dp-xxhdpi", 1f),
        Cfg("411dp・200%", "w411dp-h891dp-xxhdpi", 2f),
        Cfg("320dp・100%", "w320dp-h640dp-xhdpi", 1f),
        Cfg("320dp・200%", "w320dp-h640dp-xhdpi", 2f),
    )

    private fun prepare(cfg: Cfg) {
        RuntimeEnvironment.setQualifiers(cfg.qualifiers)
        RuntimeEnvironment.setFontScale(cfg.fontScale)
        AppClock.setFakeToday(context, today)
        WeatherService.setFakeMood(context, Mood.SUNNY)
        MowanSettings.resetEquipment(context)
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", Cities.selected(context).name).putLong("fetchedAt", System.currentTimeMillis()).commit()
    }

    private fun launch(): MainActivity {
        for (c in opened) runCatching { c.pause().stop().destroy() }
        opened.clear()
        return Robolectric.buildActivity(MainActivity::class.java).setup().also { opened += it }.get().also { relayout(it) }
    }

    private fun relayout(a: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val v = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(v.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(v.height, View.MeasureSpec.EXACTLY))
        v.layout(v.left, v.top, v.right, v.bottom)
    }

    private val MainActivity.content get() = (findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView).getChildAt(0) as LinearLayout

    data class Pos(val pageTop: Int, val pageH: Int, val slotTop: Int, val slotH: Int, val storyTop: Int)

    private fun pos(a: MainActivity): Pos {
        val c = a.content
        val page = GlyphInk.rectIn(c, a.findViewById(R.id.page))
        val slot = GlyphInk.rectIn(c, a.findViewById(R.id.action_slot))
        val story = GlyphInk.rectIn(c, a.findViewById<View>(R.id.story_mowan).parent as View)
        return Pos(page.top, page.height(), slot.top, slot.height(), story.top)
    }

    /** 看得到的按鈕：字形完整、一行、≥ 48dp；看不到或停用的按鈕不能拿到焦點 */
    private fun checkButtons(a: MainActivity, tag: String): List<String> {
        val c = a.content
        val problems = mutableListOf<String>()
        for (id in listOf(R.id.prev, R.id.next, R.id.today, R.id.tear, R.id.city)) {
            val v = a.findViewById<TextView>(id)
            if (!v.isShown) continue
            if (v.width / d < 47.5f || v.height / d < 47.5f) problems += "$tag ${v.text} 只有 %.0f×%.0fdp".format(v.width / d, v.height / d)
            if (GlyphInk.clipped(c, v) > 0) problems += "$tag ${v.text} 被切掉"
            if ((v.layout?.lineCount ?: 1) > 1) problems += "$tag ${v.text} 換行"
            if (!v.isEnabled) problems += "$tag ${v.text} 停用卻看得到"
        }
        for (id in listOf(R.id.hint, R.id.tear_guide)) {
            val v = a.findViewById<TextView>(id)
            if (v.isShown && GlyphInk.clipped(c, v) > 0) problems += "$tag ${v.text} 被切掉"
        }
        // 隱藏的按鈕（不是 VISIBLE）拿不到焦點
        for (id in listOf(R.id.today, R.id.tear)) {
            val v = a.findViewById<View>(id)
            if (!v.isShown && v.visibility == View.INVISIBLE) problems += "$tag ${(v as TextView).text} INVISIBLE（還在無障礙樹上）"
        }
        return problems
    }

    private fun f(v: Int) = "%.0f".format(v / d)

    @Test
    fun noShiftAcrossStates() {
        val report = StringBuilder("# 日曆下方操作區（V2b）＋A1：四個狀態的位置（dp）\n\n")
        report.append("| 設定 | 狀態 | 日曆上緣 | 日曆高 | 操作區上緣 | 操作區高 | 故事卡上緣 | 操作區顯示 |\n|---|---|---|---|---|---|---|---|\n")
        val problems = mutableListOf<String>()
        for (cfg in cfgs) {
            prepare(cfg)
            StoryBook(context).jumpTo(13, today)
            val a = launch()
            val ps = mutableListOf<Pair<String, Pos>>()
            fun snap(state: String) {
                relayout(a)
                val p = pos(a)
                ps += state to p
                problems += checkButtons(a, "${cfg.name} $state")
                val showing = listOf(R.id.tear to "撕下這一頁", R.id.today to "回到最上面那頁", R.id.hint to "明天再來撕下一頁").filter { a.findViewById<View>(it.first).isShown }.joinToString { it.second }
                report.append("| ${cfg.name} | $state | ${f(p.pageTop)} | ${f(p.pageH)} | ${f(p.slotTop)} | ${f(p.slotH)} | ${f(p.storyTop)} | $showing |\n")
            }
            snap("撕頁前")
            assertTrue("${cfg.name} 撕頁前顯示「撕下這一頁」", a.findViewById<View>(R.id.tear).isShown)
            a.findViewById<Button>(R.id.tear).performClick()
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            snap("撕頁後")
            assertTrue("${cfg.name} 撕頁後顯示「明天再來撕下一頁」", a.findViewById<View>(R.id.hint).isShown)
            assertEquals("明天再來撕下一頁", a.findViewById<TextView>(R.id.hint).text.toString())
            a.findViewById<Button>(R.id.prev).performClick()
            snap("昨天")
            val back = a.findViewById<Button>(R.id.today)
            assertTrue("${cfg.name} 昨天顯示「回到最上面那頁」", back.isShown)
            assertEquals("回到最上面那頁，現在在看 10/20", back.contentDescription.toString())
            back.performClick()
            snap("回到今天")
            for ((n, sel) in listOf<Pair<String, (Pos) -> Int>>("日曆上緣" to { it.pageTop }, "日曆高" to { it.pageH }, "操作區上緣" to { it.slotTop }, "操作區高" to { it.slotH }, "故事卡上緣" to { it.storyTop })) {
                val vs = ps.map { sel(it.second) }.distinct()
                if (vs.size > 1) problems += "${cfg.name} $n 會動：${ps.joinToString { "${it.first} ${f(sel(it.second))}" }}"
            }
            // 上方只有前一天／後一天與城市入口（沒有說明文字、沒有回到最上面那頁）
            val pageTop = ps[0].second.pageTop
            for (id in listOf(R.id.hint, R.id.today, R.id.tear)) assertTrue("${cfg.name} 操作元件不在日曆上方", GlyphInk.rectIn(a.content, a.findViewById(id)).top >= pageTop || !a.findViewById<View>(id).isShown)
        }
        File(out, "report.md").writeText(report.toString())
        assertTrue("$problems", problems.isEmpty())
    }

    /** 首次引導：成功撕下第一頁以前顯示；撕下以後不再顯示，而且記住（重開 App 也一樣）；操作區高度從頭到尾不變 */
    @Test
    fun firstUseGuideKeepsTheSlotHeight() {
        val report = StringBuilder("# 首次引導前後、重開 App 的操作區高度（dp）\n\n| 設定 | 首次（封面，有引導） | 撕下第一頁後 | 重開 App | 之後一般使用（重開） |\n|---|---|---|---|---|\n")
        val problems = mutableListOf<String>()
        for (cfg in cfgs) {
            prepare(cfg)
            StoryBook(context).reset()
            context.getSharedPreferences(MainActivity.PREFS_UI, Context.MODE_PRIVATE).edit().clear().commit()
            val a = launch()
            val guide = a.findViewById<TextView>(R.id.tear_guide)
            assertTrue("${cfg.name} 首次：顯示引導", guide.isShown)
            assertEquals("也可以從紙張下方兩角往上撕", guide.text.toString())
            problems += checkButtons(a, "${cfg.name} 首次")
            val h0 = pos(a)
            a.findViewById<Button>(R.id.tear).performClick()
            ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            relayout(a)
            val h1 = pos(a)
            assertFalse("${cfg.name} 撕下第一頁後不再顯示引導", a.findViewById<View>(R.id.tear_guide).isShown)
            assertTrue("${cfg.name} 完成記在設定裡", context.getSharedPreferences(MainActivity.PREFS_UI, Context.MODE_PRIVATE).getBoolean(MainActivity.KEY_GUIDE_DONE, false))
            val b = launch()
            val h2 = pos(b)
            assertFalse("${cfg.name} 重開也不顯示引導", b.findViewById<View>(R.id.tear_guide).isShown)
            // 之後一般使用：又有一頁可以撕（隔天）
            AppClock.setFakeToday(context, today.plusDays(1))
            val c = launch()
            val h3 = pos(c)
            assertTrue("${cfg.name} 隔天可以撕：顯示「撕下這一頁」", c.findViewById<View>(R.id.tear).isShown)
            assertFalse("${cfg.name} 隔天也不顯示引導", c.findViewById<View>(R.id.tear_guide).isShown)
            val hs = listOf(h0, h1, h2, h3).map { it.slotH }
            if (hs.distinct().size > 1) problems += "${cfg.name} 操作區高度變了：${hs.map { f(it) }}"
            // 位置也不能動：封面（首次）跟日曆頁一樣高（emulator #105 抓到封面比較矮，撕下封面後操作區往下 43dp）
            for ((n, sel) in listOf<Pair<String, (Pos) -> Int>>("日曆上緣" to { it.pageTop }, "日曆高" to { it.pageH }, "操作區上緣" to { it.slotTop }, "故事卡上緣" to { it.storyTop })) {
                val vs = listOf(h0, h1, h2, h3).map(sel)
                if (vs.distinct().size > 1) problems += "${cfg.name} $n 變了：${vs.map { f(it) }}"
            }
            report.append("| ${cfg.name} | ${listOf(h0, h1, h2, h3).joinToString(" | ") { "高 ${f(it.slotH)}、上緣 ${f(it.slotTop)}、日曆高 ${f(it.pageH)}" }} |\n")
            AppClock.setFakeToday(context, today)
        }
        File(out, "first-use.md").writeText(report.toString())
        assertTrue("$problems", problems.isEmpty())
    }

    /** TalkBack 開著時撕頁：焦點移到新那一頁的日期，朗讀結果；不停在消失的按鈕 */
    @Test
    fun talkBackFocusMovesToTheNewPage() {
        prepare(cfgs[0])
        StoryBook(context).jumpTo(13, today)
        val a = launch()
        shadowOf(a.getSystemService(AccessibilityManager::class.java)).setEnabled(true)
        val tear = a.findViewById<Button>(R.id.tear)
        tear.performClick()
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        relayout(a)
        val (target, text) = a.lastTornA11y ?: error("撕頁後沒有移動焦點")
        assertEquals("焦點在新那一頁的日期", R.id.day, target.id)
        assertTrue("焦點目標在日曆頁裡", generateSequence(target.parent) { it.parent }.any { it === a.findViewById(R.id.page) })
        assertEquals("已撕下，現在是 10 月 21 日", text)
        assertFalse("撕頁按鈕已經不在畫面上", tear.isShown)
    }

    /** TalkBack 關著時不搶焦點、不朗讀 */
    @Test
    fun noA11yActionWithoutTalkBack() {
        prepare(cfgs[0])
        StoryBook(context).jumpTo(13, today)
        val a = launch()
        a.findViewById<Button>(R.id.tear).performClick()
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        assertEquals(null, a.lastTornA11y)
    }

    /**
     * 操作區裡的內容垂直置中（2026-11-08 審核 Minor：320dp・200% 操作區 123dp）：看得到的內容（按鈕的外框、文字的字形）
     * 上下留白差 ≤ 1dp；首次引導兩行時按鈕與引導不重疊、不被切；內容不超出操作區。
     */
    @Test
    fun contentIsCenteredInTheSlot() {
        val report = StringBuilder("# 操作區內容的垂直位置（dp）：上留白／下留白\n\n| 設定 | 首次（撕下這一頁＋引導） | 撕下這一頁 | 明天再來撕下一頁 | 回到最上面那頁 |\n|---|---|---|---|---|\n")
        val problems = mutableListOf<String>()
        for (cfg in cfgs) {
            prepare(cfg)
            val cells = mutableListOf<String>()
            fun measure(a: MainActivity, state: String) {
                relayout(a)
                val c = a.content
                val slot = GlyphInk.rectIn(c, a.findViewById(R.id.action_slot))
                // 看得到的內容：按鈕用整個按鈕（外框）、文字用實際字形
                val parts = mutableListOf<android.graphics.Rect>()
                for (id in listOf(R.id.tear, R.id.today)) a.findViewById<View>(id).takeIf { it.isShown }?.let { parts += GlyphInk.rectIn(c, it) }
                for (id in listOf(R.id.hint, R.id.tear_guide)) a.findViewById<TextView>(id).takeIf { it.isShown }?.let { parts += GlyphInk.glyphs(c, it) }
                val box = parts.reduce { x, y -> android.graphics.Rect(x).apply { union(y) } }
                val top = box.top - slot.top
                val bottom = slot.bottom - box.bottom
                cells += "${f(top)}／${f(bottom)}"
                if (top < 0 || bottom < 0) problems += "${cfg.name} $state 內容超出操作區（${f(top)}／${f(bottom)}）"
                // 首次引導的按鈕會靠上（下面接兩行引導），整組置中；其他狀態單一內容置中
                if (Math.abs(top - bottom) > d) problems += "${cfg.name} $state 沒有置中：上 ${f(top)}、下 ${f(bottom)}dp"
                val guide = a.findViewById<TextView>(R.id.tear_guide)
                if (guide.isShown) {
                    if (GlyphInk.clipped(c, guide) > 0) problems += "${cfg.name} $state 引導被切"
                    if (GlyphInk.touching(c, a.findViewById(R.id.tear), guide) > 0) problems += "${cfg.name} $state 引導碰到按鈕"
                    if (GlyphInk.rectIn(c, a.findViewById(R.id.tear)).bottom > GlyphInk.glyphs(c, guide).minOf { it.top }) problems += "${cfg.name} $state 引導與按鈕重疊"
                }
            }
            // 首次（封面）
            StoryBook(context).reset()
            context.getSharedPreferences(MainActivity.PREFS_UI, Context.MODE_PRIVATE).edit().clear().commit()
            measure(launch(), "首次")
            // 一般使用：撕頁前 → 撕頁後 → 昨天
            StoryBook(context).jumpTo(13, today)
            val a = launch()
            measure(a, "撕下這一頁")
            a.findViewById<Button>(R.id.tear).performClick(); ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
            measure(a, "明天再來撕下一頁")
            a.findViewById<Button>(R.id.prev).performClick()
            measure(a, "回到最上面那頁")
            report.append("| ${cfg.name} | ${cells.joinToString(" | ")} |\n")
        }
        File(out, "centering.md").writeText(report.toString())
        assertTrue("$problems", problems.isEmpty())
    }
}
