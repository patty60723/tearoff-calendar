package com.patty60723.tearoff.story

import java.time.LocalDate
import kotlin.random.Random

/** 撕下某一頁時，故事需要知道的事。 */
data class StoryContext(
    /** 這是撕下的第幾頁（從 1 開始） */
    val page: Int,
    /** 撕下的那一頁的日期 */
    val date: LocalDate,
    val festivals: List<String> = emptyList(),
    val solarTerm: String? = null,
    val yi: List<String> = emptyList(),
    val ji: List<String> = emptyList(),
    val wearing: Set<String> = emptySet(),
    /** 撕到今天那頁時才有：天氣關鍵字 */
    val weather: String? = null,
    val name: String = StoryEngine.DEFAULT_NAME,
    /** 最近出現過的片段 id，先不重複 */
    val recent: List<String> = emptyList(),
) {
    val chapter get() = StoryEngine.chapterOf(page)
    val season get() = StoryEngine.seasonOf(date)
    val yiCategories get() = yi.mapNotNull(YiJiCategory::of).toSet()
    val jiCategories get() = ji.mapNotNull(YiJiCategory::of).toSet()
}

object StoryEngine {
    const val DEFAULT_NAME = "墨丸"

    fun chapterOf(page: Int) = when {
        page <= 14 -> 1
        page <= 90 -> 2
        page <= 270 -> 3
        else -> 4
    }

    fun seasonOf(date: LocalDate) = when (date.monthValue) {
        3, 4, 5 -> "春"
        6, 7, 8 -> "夏"
        9, 10, 11 -> "秋"
        else -> "冬"
    }

    fun matches(beat: Beat, ctx: StoryContext): Boolean =
        (beat.page == null || beat.page == ctx.page) &&
            (beat.chapters.isEmpty() || ctx.chapter in beat.chapters) &&
            (beat.festival == null || beat.festival in ctx.festivals) &&
            (beat.solarTerm == null || ctx.solarTerm != null && (beat.solarTerm == "*" || beat.solarTerm == ctx.solarTerm)) &&
            (beat.yiCategory == null || beat.yiCategory in ctx.yiCategories) &&
            (beat.jiCategory == null || beat.jiCategory in ctx.jiCategories) &&
            (beat.wearing == null || beat.wearing in ctx.wearing) &&
            (beat.weather == null || beat.weather == ctx.weather) &&
            (beat.seasons.isEmpty() || ctx.season in beat.seasons)

    /**
     * 主線 > 節日 > 節氣 > 宜忌插曲。同一級有好幾段時依權重抽，
     * 用日期當種子，所以同一頁不管看幾次都是同一段。
     */
    fun pick(beats: List<Beat>, ctx: StoryContext): Beat? {
        val eligible = beats.filter { matches(it, ctx) }
        val random = Random(ctx.date.toEpochDay() * 1000 + ctx.page)
        for (tier in Beat.Tier.entries) {
            val candidates = eligible.filter { it.tier == tier }
            if (candidates.isEmpty()) continue
            val fresh = candidates.filter { it.id !in ctx.recent }.ifEmpty { candidates }
            return weighted(fresh, random)
        }
        return null
    }

    private fun weighted(beats: List<Beat>, random: Random): Beat {
        var roll = random.nextInt(beats.sumOf { it.weight.coerceAtLeast(1) })
        for (beat in beats) {
            roll -= beat.weight.coerceAtLeast(1)
            if (roll < 0) return beat
        }
        return beats.last()
    }

    /** 把 {name} {yi} {ji} {day} 填進去。 */
    fun render(beat: Beat, ctx: StoryContext): String {
        val yi = beat.yiCategory?.let { c -> ctx.yi.firstOrNull { YiJiCategory.of(it) == c } } ?: ctx.yi.firstOrNull()
        val ji = beat.jiCategory?.let { c -> ctx.ji.firstOrNull { YiJiCategory.of(it) == c } } ?: ctx.ji.firstOrNull()
        return beat.text
            .replace("{name}", ctx.name)
            .replace("{yi}", yi ?: "")
            .replace("{ji}", ji ?: "")
            .replace("{day}", ctx.date.dayOfMonth.toString())
    }

    /** 撕到第 page 頁為止，主線解鎖了哪些部位和配件。 */
    fun unlocked(beats: List<Beat>, page: Int): Set<String> =
        beats.filter { it.page != null && it.page <= page }.flatMap { it.unlock }.toSet()
}
