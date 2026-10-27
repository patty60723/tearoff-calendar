package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalScene
import com.patty60723.tearoff.mowan.FestivalSession.Mode
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration
import java.time.LocalDate

/**
 * P6-4b 審核素材：App 實際的故事卡、日曆頁、整個畫面（Robolectric 繪圖，不是模擬器或實機）。
 * 存到 app/build/screenshots/p6-4b。這不是比對用的測試。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FestivalAppShotsTest {
    private val out = File("build/screenshots/p6-4b").apply { mkdirs() }
    private val context get() = RuntimeEnvironment.getApplication()
    private val paper = 0xFFEFEAE1.toInt()

    @After
    fun cleanUp() {
        MowanSettings.setStoryCard(context, false)
        MowanSettings.setFestivalHeadwear(context, true)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
    }

    private fun save(name: String, bitmap: Bitmap) =
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun draw(v: View) = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { Canvas(it).apply { drawColor(paper); v.draw(this) } }

    private fun open(today: LocalDate, mood: Mood, tornToday: Boolean = true, v3: Boolean = true): MainActivity {
        MowanSettings.setStoryCard(context, v3)
        WeatherService.setFakeMood(context, mood)
        AppClock.setFakeToday(context, today)
        StoryBook(context).apply {
            jumpTo(if (tornToday) 13 else 14, today)
            if (tornToday) tear(today)
        }
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val MainActivity.mowan get() = findViewById<MowanView>(R.id.story_mowan)
    private val MainActivity.card get() = mowan.parent as View
    private val MainActivity.page get() = findViewById<View>(R.id.page)

    private fun MainActivity.lookAtStoryCard() = mowan.apply {
        visibleFraction = { 1f }
        windowFocused = { true }
        checkVisible()
    }

    /** 整個畫面；[toCard] 時捲到故事卡 */
    private fun screen(a: MainActivity, toCard: Boolean): Bitmap {
        val scroll = a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ScrollView
        if (toCard) {
            val at = IntArray(2).also { a.card.getLocationInWindow(it) }[1] - IntArray(2).also { scroll.getLocationInWindow(it) }[1]
            scroll.scrollTo(0, at + scroll.scrollY - 40)
        } else {
            scroll.scrollTo(0, 0)
        }
        return draw(a.window.decorView)
    }

    private fun advance(ms: Long) = ShadowSystemClock.advanceBy(Duration.ofMillis(ms))

    private fun firstDay(f: Festival) = generateSequence(LocalDate.of(2027, 1, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == f }

    private val words = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B2B2B.toInt(); textSize = 40f }

    /** 幾張圖排成一列，上面加標題 */
    private fun row(name: String, shots: List<Pair<String, Bitmap>>) {
        val h = shots.maxOf { it.second.height } + 60
        val sheet = Bitmap.createBitmap(shots.sumOf { it.second.width + 20 }, h, Bitmap.Config.ARGB_8888)
        Canvas(sheet).apply {
            drawColor(paper)
            var x = 0f
            for ((label, b) in shots) {
                drawText(label, x + 10, 44f, words)
                drawBitmap(b, x, 60f, null)
                x += b.width + 20
            }
        }
        save(name, sheet)
    }

    @Test
    fun storyCardAndCalendarPage() {
        val cases = listOf(
            "spring" to Mood.SUNNY, "spring-rain" to Mood.RAIN,
            "xmas" to Mood.SUNNY, "xmas-snow" to Mood.SNOW,
            "moon" to Mood.SUNNY, "moon-rain" to Mood.RAIN,
        )
        for ((name, mood) in cases) {
            val f = when (name.substringBefore('-')) { "spring" -> Festival.SPRING; "xmas" -> Festival.XMAS; else -> Festival.MOON }
            val a = open(firstDay(f), mood)
            val main = FestivalScene.spec(f).main
            save("$name-page", draw(a.page))
            a.lookAtStoryCard()
            assertEquals(Mode.MAIN, a.mowan.session.mode)
            advance(main.keyMs)
            val key = draw(a.card)
            advance(main.durMs - main.keyMs + 100)
            val stance = draw(a.card)
            assertEquals("演完回到站姿", Mode.BASE, a.mowan.session.mode)
            save("$name-card-main", key)
            save("$name-card-stance", stance)
            row("$name-sheet", listOf("故事卡：主演出 ${main.keyMs} ms" to key, "故事卡：演完" to stance, "日曆頁（今天）" to draw(a.page)))
            if (name == "spring") {
                save("spring-screen-top", screen(a, toCard = false))
                save("spring-screen-card", screen(a, toCard = true))
            }
        }
    }

    @Test
    fun rulesInTheApp() {
        val spring = firstDay(Festival.SPRING)
        // D-41：今天那頁還沒撕，最上面是昨天那頁（不畫節日）
        val top = open(spring, Mood.SUNNY, tornToday = false)
        val yesterday = draw(top.page)
        top.findViewById<View>(R.id.next).performClick()
        row("rule-1-page-only-today", listOf("最上面：昨天那頁" to yesterday, "往後翻：今天那頁" to draw(top.page)))
        // 節日頭飾關閉（除錯選單）：照紙帽的裝備狀態
        MowanSettings.setFestivalHeadwear(context, false)
        val off = open(spring, Mood.SUNNY)
        row("rule-2-headwear-off", listOf("故事卡" to draw(off.card), "日曆頁" to draw(off.page)))
        MowanSettings.setFestivalHeadwear(context, true)
        // V3 關閉：舊火柴人，沒有任何節日東西
        val old = open(spring, Mood.SUNNY, v3 = false)
        row("rule-3-v3-off", listOf("故事卡（舊火柴人）" to draw(old.card), "日曆頁（舊火柴人）" to draw(old.page)))
    }
}
