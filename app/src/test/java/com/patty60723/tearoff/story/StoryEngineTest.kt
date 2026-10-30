package com.patty60723.tearoff.story

import com.patty60723.tearoff.Almanac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class StoryEngineTest {
    // 單元測試的工作目錄是 app/
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun context(page: Int, date: LocalDate, name: String = "小筆", recent: List<String> = emptyList()): StoryContext {
        val day = Almanac.page(date)
        return StoryContext(
            page = page, date = date, festivals = day.festivals, solarTerm = day.solarTerm,
            yi = day.yi, ji = day.ji, name = name, recent = recent,
        )
    }

    @Test
    fun idsAreUnique() {
        assertEquals(beats.size, beats.map { it.id }.toSet().size)
    }

    /** D-50：故事 JSON 的 action 欄位保留（之後設計成長故事動作時可能用），但不再要求是舊火柴人的動作 */
    @Test
    fun actionFieldIsKept() {
        assertTrue(beats.any { !it.action.isNullOrEmpty() })
    }

    @Test
    fun chapterOneIsTheFirstFourteenPages() {
        val start = LocalDate.of(2026, 10, 21)
        for (page in 1..14) {
            val beat = StoryEngine.pick(beats, context(page, start.plusDays(page - 1L)))
            assertEquals("ch1-%02d".format(page), beat?.id)
        }
    }

    @Test
    fun bodyGrowsInOrder() {
        assertEquals(emptySet<String>(), StoryEngine.unlocked(beats, 0))
        assertEquals(setOf(Parts.LINE), StoryEngine.unlocked(beats, 1))
        val week = StoryEngine.unlocked(beats, 7)
        assertTrue(Parts.LEG_R in week)
        assertFalse(Parts.VOICE in week)
        assertTrue(Parts.BUNDLE in StoryEngine.unlocked(beats, 14))
    }

    @Test
    fun nameIsAskedOnPageTwelve() {
        val beat = beats.single { it.ask == "name" }
        assertEquals(12, beat.page)
        val text = StoryEngine.render(beat, context(12, LocalDate.of(2026, 11, 1)))
        assertTrue(text.contains("我叫小筆"))
    }

    /** 第一章之後的每一天都要有故事，而且空格都填好了。 */
    @Test
    fun everyDayAfterChapterOneHasAStory() {
        var date = LocalDate.of(2026, 1, 1)
        repeat(366) { i ->
            val ctx = context(15 + i, date)
            val beat = StoryEngine.pick(beats, ctx)
            assertNotNull("$date", beat)
            val text = StoryEngine.render(beat!!, ctx)
            assertFalse("$date: $text", text.contains("{") || text.contains("}"))
            // {yi} {ji} 沒填到的話會變成「今天宜。」
            assertFalse("$date: $text", Regex("今天[宜忌]。").containsMatchIn(text))
            date = date.plusDays(1)
        }
    }

    @Test
    fun samePageGivesTheSameStory() {
        val ctx = context(40, LocalDate.of(2026, 12, 3))
        assertEquals(StoryEngine.pick(beats, ctx), StoryEngine.pick(beats, ctx))
    }

    @Test
    fun recentBeatsAreSkippedWhenThereIsAnother() {
        val ctx = context(40, LocalDate.of(2026, 12, 3))
        val first = StoryEngine.pick(beats, ctx)!!
        val second = StoryEngine.pick(beats, ctx.copy(recent = listOf(first.id)))
        assertTrue(second != first)
    }

    @Test
    fun weatherBeatsNeedWeather() {
        val date = LocalDate.of(2026, 12, 3)
        val weatherBeats = beats.filter { it.weather != null }
        assertTrue(weatherBeats.isNotEmpty())
        for (beat in weatherBeats) {
            assertFalse(beat.id, StoryEngine.matches(beat, context(40, date)))
            assertTrue(beat.id, StoryEngine.matches(beat, context(40, date).copy(weather = beat.weather)))
        }
        // 每種天氣都有片段
        for (keyword in listOf("晴", "熱", "陰", "霧", "雨", "雷雨", "冷", "雪")) {
            assertTrue(keyword, weatherBeats.any { it.weather == keyword })
        }
    }

    @Test
    fun categories() {
        assertEquals("出門", YiJiCategory.of("出行"))
        assertEquals("動工", YiJiCategory.of("動土"))
        assertEquals("處理舊事", YiJiCategory.of("安葬"))
        assertEquals(null, YiJiCategory.of("不存在"))
    }
}
