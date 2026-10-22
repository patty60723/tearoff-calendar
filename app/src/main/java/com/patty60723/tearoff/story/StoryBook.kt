package com.patty60723.tearoff.story

import android.content.Context
import com.patty60723.tearoff.Almanac
import com.patty60723.tearoff.AppClock
import com.patty60723.tearoff.weather.WeatherService
import com.patty60723.tearoff.stickman.Parts
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 撕了幾頁、看到哪段故事。存在手機裡。 */
class StoryBook(context: Context) {
    private val prefs = context.getSharedPreferences("story", Context.MODE_PRIVATE)
    private val assets = context.assets
    private val appContext = context.applicationContext

    val beats: List<Beat> by lazy {
        assets.list(DIR).orEmpty().filter { it.endsWith(".json") }.sorted().flatMap { file ->
            StoryParser.parse(assets.open("$DIR/$file").bufferedReader().use { it.readText() })
        }
    }

    /** 已經撕了幾頁 */
    val page: Int get() = prefs.getInt(KEY_PAGE, 0)

    var name: String
        get() = prefs.getString(KEY_NAME, null) ?: StoryEngine.DEFAULT_NAME
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    val lastText: String? get() = prefs.getString(KEY_TEXT, null)
    val lastAction: String? get() = prefs.getString(KEY_ACTION, null)
    /** 天氣片段的道具（傘、草帽……），讓火柴人的動作配得起來 */
    val lastProps: Set<String> get() = prefs.getStringSet(KEY_PROPS, null).orEmpty()

    /** 目前長出來的部位和配件 */
    val parts: Set<String> get() = StoryEngine.unlocked(beats, page)

    private val lastTornDate: LocalDate?
        get() = prefs.getString(KEY_LAST_DATE, null)?.let(LocalDate::parse)

    /**
     * 下一張要撕的是哪一天的頁。漏掉的最多補 [CATCH_UP_DAYS] 天，
     * 再更早的就直接跳過；今天撕過了就是 null。
     */
    fun nextDate(today: LocalDate): LocalDate? {
        val last = lastTornDate ?: return today
        val next = maxOf(last.plusDays(1), today.minusDays(CATCH_UP_DAYS - 1))
        return next.takeIf { !it.isAfter(today) }
    }

    fun pendingPages(today: LocalDate): Int =
        nextDate(today)?.let { ChronoUnit.DAYS.between(it, today).toInt() + 1 } ?: 0

    /** 撕下一頁之前先看會是哪一段（第 12 頁要先問名字）。 */
    fun peek(today: LocalDate): Beat? = nextDate(today)?.let { StoryEngine.pick(beats, context(it, page + 1)) }

    /** 撕下一頁，回傳這一頁的故事。 */
    fun tear(today: LocalDate): TornPage? {
        val date = nextDate(today) ?: return null
        val newPage = page + 1
        val ctx = context(date, newPage)
        val beat = StoryEngine.pick(beats, ctx)
        val text = beat?.let { StoryEngine.render(it, ctx) } ?: ""
        val recent = (listOfNotNull(beat?.id) + recent()).take(RECENT)
        val props = if (beat?.weather != null) WeatherService.current(appContext)?.mood?.props.orEmpty() else emptySet()
        prefs.edit()
            .putStringSet(KEY_PROPS, props)
            .putInt(KEY_PAGE, newPage)
            .putString(KEY_LAST_DATE, date.toString())
            .putString(KEY_TEXT, text)
            .putString(KEY_ACTION, beat?.action)
            .putString(KEY_RECENT, recent.joinToString(","))
            .apply()
        return TornPage(newPage, date, text, beat?.action)
    }

    // ---- 除錯用 ----

    /** 從頭開始：回到第 0 頁，名字也清掉。 */
    fun reset() {
        prefs.edit().clear().apply()
    }

    /**
     * 直接跳到「已經撕了 page 頁」，最後一頁是昨天，所以今天可以接著撕。
     * 故事卡顯示第 page 頁那段。
     */
    fun jumpTo(page: Int, today: LocalDate) {
        val date = today.minusDays(1)
        val ctx = context(date, page)
        val beat = if (page > 0) StoryEngine.pick(beats, ctx) else null
        prefs.edit()
            .putInt(KEY_PAGE, page)
            .putString(KEY_LAST_DATE, date.toString())
            .putString(KEY_TEXT, beat?.let { StoryEngine.render(it, ctx) })
            .putString(KEY_ACTION, beat?.action)
            .remove(KEY_PROPS)
            .remove(KEY_RECENT)
            .apply()
    }

    private fun context(date: LocalDate, page: Int): StoryContext {
        val day = Almanac.page(date)
        return StoryContext(
            page = page,
            date = date,
            festivals = day.festivals,
            solarTerm = day.solarTerm,
            yi = day.yi,
            ji = day.ji,
            wearing = parts intersect Parts.accessories,
            // 只有撕到今天那頁時，今天的天氣才算數
            weather = if (date == AppClock.today(appContext)) WeatherService.current(appContext)?.mood?.keyword else null,
            name = name,
            recent = recent(),
        )
    }

    private fun recent() = prefs.getString(KEY_RECENT, "").orEmpty().split(",").filter { it.isNotEmpty() }

    data class TornPage(val page: Int, val date: LocalDate, val text: String, val action: String?)

    private companion object {
        const val DIR = "story"
        const val CATCH_UP_DAYS = 7L
        const val RECENT = 30
        const val KEY_PAGE = "page"
        const val KEY_NAME = "name"
        const val KEY_TEXT = "lastText"
        const val KEY_ACTION = "lastAction"
        const val KEY_PROPS = "lastProps"
        const val KEY_LAST_DATE = "lastTornDate"
        const val KEY_RECENT = "recent"
    }
}
