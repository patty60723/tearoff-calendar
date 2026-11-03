package com.patty60723.tearoff

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import com.patty60723.tearoff.mowan.Festival
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.LocalDate

/**
 * P7-3：故事卡、App 日曆頁、桌面小工具（四種版面）的狀態一致性。
 *
 * 狀態摘要都從「實際畫出來的東西」取：
 * - 故事卡：[MowanView.onFrame] 回報 onDraw 這一格實際用的參數（部位、紙帽、包袱、天氣、節日、頭飾、是否靜態）。
 * - 日曆頁與小工具：找出畫面上 `mowan_still` 那張圖，對到 [MowanBitmap.onRender] 回報的參數，
 *   再用同樣的參數重畫一次、逐像素相同（證明這組參數就是畫面上的那張）；日期與天氣文字讀版面上的文字。
 * - 小工具用 [CalendarWidget.onUpdated] 拿到實際送給系統的 RemoteViews，按四種版面的大小各套用一次。
 * 不是拿設定值自己組出預期的圖來比；底層狀態（StoryBook、MowanSettings、WeatherService、AppClock）只用來判斷規則。
 *
 * 刻意不同的規則（不算不一致）：見 docs/character-p7-3-consistency.md 第 2 節。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ConsistencyMatrixTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val renders = mutableListOf<Pair<MowanBitmap.Spec, Bitmap>>()
    private val sent = LinkedHashMap<Int, RemoteViews>()
    private val report = StringBuilder()

    @Before
    fun setUp() {
        MowanBitmap.onRender = { spec, bitmap -> synchronized(renders) { renders += spec to bitmap } }
        CalendarWidget.onUpdated = { id, views -> sent[id] = views }
    }

    @After
    fun cleanUp() {
        MowanBitmap.onRender = null
        CalendarWidget.onUpdated = null
        MowanSettings.resetEquipment(context)
        WeatherService.setFakeMood(context, null)
        AppClock.setFakeToday(context, null)
        System.clearProperty("https.proxyHost")
        System.clearProperty("https.proxyPort")
    }

    // ---------- 狀態摘要 ----------

    /** 一個顯示位置的狀態摘要（從畫面取得） */
    data class Shown(
        val where: String, val date: LocalDate?, val festival: Festival?, val parts: Set<String>,
        val hat: Boolean, val bundle: Boolean, val weather: Mowan.Weather, val headwear: Boolean,
        val still: Boolean, val weatherText: String?, val page: Int? = null,
    ) {
        /** 共同的底層狀態（不含日期、天氣這些依顯示規則可能不同的部分） */
        val equipment get() = listOf(parts, hat, bundle, headwear)
        override fun toString() =
            "$where：$date ${festival ?: "-"} 部位${parts.size} 帽${if (hat) "✓" else "✗"} 包袱${if (bundle) "✓" else "✗"} " +
                "頭飾${if (headwear) "✓" else "✗"} $weather${weatherText?.let { "「$it」" } ?: ""}${if (still) " 靜態" else ""}${page?.let { " 第${it}頁" } ?: ""}"
    }

    private val months = listOf("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER")

    private fun View.text(id: Int) = findViewById<TextView>(id)?.text?.toString()

    /** 版面上的日期（小工具與日曆頁同一套版面）：日、英文月份＋年 */
    private fun dateOn(v: View): LocalDate {
        val (mon, year) = v.text(R.id.month_en)!!.split(" ")
        return LocalDate.of(year.toInt(), months.indexOf(mon) + 1, v.text(R.id.day)!!.toInt())
    }

    /** 日出日落那一格的第一行若不是「日出」就是天氣文字（只有今天那頁會有） */
    private fun weatherTextOn(v: View) = v.text(R.id.sun)?.lines()?.firstOrNull()?.takeIf { !it.startsWith("日出") }

    /** 畫面上這張圖是哪一次 [MowanBitmap.still] 畫的；重畫一次逐像素相同才算 */
    private fun specOf(bitmap: Bitmap, where: String): MowanBitmap.Spec {
        val list = synchronized(renders) { renders.toList() }
        val byIdentity = list.lastOrNull { it.second === bitmap }
        val matches = byIdentity?.let { listOf(it) } ?: list.filter { it.second.sameAs(bitmap) }
        if (matches.isEmpty()) fail("$where：畫面上的墨丸找不到是哪一次畫的（共 ${list.size} 張）")
        val states = matches.map { it.first.copy(widthPx = 0, heightPx = 0, density = 0f) }.toSet()
        if (states.size > 1) fail("$where：同一張圖對到不同的狀態 $states")
        val spec = matches.last().first
        val hook = MowanBitmap.onRender
        MowanBitmap.onRender = null
        try {
            val again = MowanBitmap.still(spec.parts, spec.hatOn, spec.bundleOn, spec.weather, spec.widthPx, spec.heightPx, spec.density, spec.festival, spec.art)
            assertTrue("$where：用回報的參數重畫要逐像素相同", again.sameAs(bitmap))
        } finally {
            MowanBitmap.onRender = hook
        }
        return spec
    }

    private fun stillOf(v: View): Bitmap = (v.findViewById<ImageView>(R.id.mowan_still).drawable as BitmapDrawable).bitmap

    private fun fromPage(where: String, v: View): Shown {
        val s = specOf(stillOf(v), where)
        return Shown(where, dateOn(v), s.festival, s.parts, s.hatOn, s.bundleOn, s.weather, s.art.festivalHeadwear, true, weatherTextOn(v))
    }

    private fun storyCard(a: MainActivity): Shown {
        val m = a.findViewById<MowanView>(R.id.story_mowan)
        var spec: MowanView.FrameSpec? = null
        m.onFrame = { spec = it }
        val w = m.width.takeIf { it > 0 } ?: 288
        val h = m.height.takeIf { it > 0 } ?: 384
        m.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        m.onFrame = null
        val s = spec ?: error("故事卡沒有畫")
        // 標題平常是「第 N 頁」；點墨丸後 6 秒內換成天氣（那是台詞，不是狀態），那時不讀頁數
        val page = Regex("""第 (\d+) 頁""").find(a.text(R.id.story_title) ?: "")?.groupValues?.get(1)?.toInt()
        return Shown("故事卡", m.today, s.festival, s.parts, s.hatOn, s.bundleOn, s.weather, s.art.festivalHeadwear, s.still, null, page)
    }

    private fun Activity.text(id: Int) = findViewById<TextView>(id)?.text?.toString()

    private fun calendarPage(a: MainActivity): Shown? {
        val pad = a.findViewById<ViewGroup>(R.id.page)
        val page = pad.getChildAt(0) ?: return null
        if (page.findViewById<View>(R.id.mowan_still) == null) return null // 封面
        return fromPage("日曆頁", page)
    }

    /** 送給系統的 RemoteViews 裡，這個版面大小的那一份（Android 12 以上一次送四種版面，系統依大小挑） */
    private fun sized(views: RemoteViews, layout: PageLayout): RemoteViews {
        val list = ReflectionHelpers.getField<List<RemoteViews>?>(views, "mSizedRemoteViews") ?: return views
        val want = SizeF(layout.minWidthDp, layout.minHeightDp)
        return list.firstOrNull { ReflectionHelpers.getField<SizeF>(it, "mIdealSize") == want }
            ?: error("送出的 RemoteViews 裡沒有 ${layout.name}（${list.map { ReflectionHelpers.getField<SizeF>(it, "mIdealSize") }}）")
    }

    private fun widgets(): List<Shown> {
        val views = sent.values.lastOrNull() ?: error("小工具沒有更新過")
        return PageLayout.entries.map { layout ->
            fromPage("小工具 ${layout.name}", sized(views, layout).apply(context, FrameLayout(context)))
        }
    }

    // ---------- 底層狀態與規則 ----------

    data class Truth(
        val today: LocalDate, val top: LocalDate?, val parts: Set<String>, val hat: Boolean, val bundle: Boolean,
        val headwear: Boolean, val weather: Mowan.Weather, val weatherText: String?, val page: Int,
    )

    private fun truth(): Truth {
        val today = AppClock.today(context)
        val book = StoryBook(context)
        val top = if (book.page == 0) null else book.nextDate(today)?.minusDays(1) ?: today
        val w = WeatherService.current(context)
        return Truth(
            today, top, Mowan.partsOf(book.parts), MowanSettings.hatOn(context), MowanSettings.bundleOn(context),
            MowanSettings.festivalHeadwear(context), w?.let { MowanSettings.weatherOf(it.mood) } ?: Mowan.Weather.NONE, w?.short, book.page,
        )
    }

    /**
     * 三處（六個畫面）都對到底層狀態，而且符合刻意不同的規則：
     * - 部位、紙帽、包袱、節日頭飾：六處完全相同。
     * - 故事卡與小工具：一律是「今天」（故事卡不跟著翻頁；小工具畫今天那頁）；節日與天氣都照今天。
     * - 日曆頁：畫最上面那張（可能是昨天還沒撕）。只有那張是今天時才畫節日與天氣（D-41；天氣文字也一樣）。
     * - 日曆頁、小工具一律是靜態代表姿勢；故事卡只有 Reduced Motion 時是靜態。
     * @param pageDate 日曆頁實際應該停在哪一天（D-44：App 停在前景跨午夜時，日曆頁暫時不動）
     */
    private fun check(a: MainActivity, label: String, reduced: Boolean = false, pageDate: LocalDate? = null): List<Shown> {
        val t = truth()
        val shown = listOfNotNull(storyCard(a), calendarPage(a)) + widgets()
        val todayFestival = FestivalTheme.of(t.today)
        for (s in shown) {
            val at = "$label｜$s"
            assertEquals("$at：部位、紙帽、包袱、頭飾跟底層狀態一致", listOf(t.parts, t.hat, t.bundle, t.headwear), s.equipment)
            when {
                s.where == "故事卡" -> {
                    assertEquals("$at：故事卡是今天", t.today, s.date)
                    assertEquals("$at：故事卡的節日照今天", todayFestival, s.festival)
                    assertEquals("$at：故事卡的天氣照今天的天氣資料", t.weather, s.weather)
                    assertEquals("$at：故事卡只有 Reduced Motion 時是靜態", reduced, s.still)
                    if (t.page > 0 && s.page != null) assertEquals("$at：故事卡的頁數", t.page, s.page)
                }
                s.where == "日曆頁" -> {
                    val date = pageDate ?: t.top
                    assertEquals("$at：日曆頁是最上面那張", date, s.date)
                    // 畫的時候那張是不是「今天」：跨午夜時日曆頁暫時不動（D-44），保留畫的時候（那天還是今天）的節日與天氣
                    val isToday = pageDate != null || date == t.today
                    assertEquals("$at：日曆頁只有今天那頁畫節日（D-41）", if (isToday) FestivalTheme.of(date!!) else null, s.festival)
                    assertEquals("$at：日曆頁只有今天那頁畫天氣", if (isToday) t.weather else Mowan.Weather.NONE, s.weather)
                    assertEquals("$at：天氣文字只在今天那頁", if (isToday) t.weatherText else null, s.weatherText)
                    assertTrue("$at：日曆頁是靜態代表姿勢", s.still)
                }
                else -> {
                    assertEquals("$at：小工具畫今天那頁", t.today, s.date)
                    assertEquals("$at：小工具的節日照今天", todayFestival, s.festival)
                    assertEquals("$at：小工具的天氣照今天", t.weather, s.weather)
                    assertEquals("$at：小工具的天氣文字", t.weatherText, s.weatherText)
                    assertTrue("$at：小工具是靜態代表姿勢", s.still)
                }
            }
        }
        // 六處的共同狀態彼此相同（上面逐一對過底層狀態，這裡再直接互相比一次）
        assertEquals("$label：六處的部位、紙帽、包袱、頭飾", 1, shown.map { it.equipment }.toSet().size)
        report.append("| $label | ").append(shown.joinToString("<br>") { it.toString() }).append(" |\n")
        return shown
    }

    // ---------- 開 App ----------

    private fun festivalDay(f: Festival) = generateSequence(LocalDate.of(2026, 10, 1)) { it.plusDays(1) }.first { FestivalTheme.of(it) == f }

    private fun markWeatherFresh(city: String = Cities.selected(context).name) {
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("city", city).putLong("fetchedAt", System.currentTimeMillis()).commit()
    }

    data class Scenario(
        val day: LocalDate, val page: Int, val tornToday: Boolean, val mood: Mood?,
        val hat: Boolean, val bundle: Boolean, val headwear: Boolean, val reduced: Boolean,
    )

    private fun prepare(s: Scenario) {
        synchronized(renders) { renders.clear() }
        sent.clear()
        AppClock.setFakeToday(context, s.day)
        StoryBook(context).apply { if (s.tornToday) { jumpTo(s.page - 1, s.day); tear(s.day) } else jumpTo(s.page, s.day) }
        WeatherService.setFakeMood(context, s.mood)
        MowanSettings.setHatOn(context, s.hat)
        MowanSettings.setBundleOn(context, s.bundle)
        MowanSettings.setFestivalHeadwear(context, s.headwear)
        markWeatherFresh()
    }

    private fun widget() = shadowOf(AppWidgetManager.getInstance(context)).createWidget(CalendarWidget::class.java, R.layout.widget_tall)

    private fun launch(reduced: Boolean = false): ActivityController<MainActivity> {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        c.get().findViewById<MowanView>(R.id.story_mowan).reducedMotion = { reduced }
        shadowOf(Looper.getMainLooper()).idle()
        return c
    }

    private fun save(name: String) {
        File("build/screenshots/p7-3").apply { mkdirs() }.resolve("$name.md").writeText(
            "# P7-3 一致性：$name（Robolectric）\n\n| 情境 | 六個畫面的狀態摘要 |\n|---|---|\n$report",
        )
    }

    // ---------- 統一矩陣 ----------

    private val moods = listOf(Mood.SUNNY, Mood.HOT, Mood.CLOUDY, Mood.FOG, Mood.RAIN, Mood.THUNDER, Mood.COLD, Mood.SNOW, null)
    private val pages = listOf(1, 5, 8, 13, 14)

    /**
     * 4 種日子（一般、春節、中秋、聖誕）× 9 種天氣（8 種＋沒有資料），每一列輪換成長頁數（1、5、8、13、14）、
     * 紙帽、包袱、節日頭飾、今天撕了沒（沒撕時日曆頁停在昨天）、Reduced Motion。共 36 種，每種都開 App 讀六個畫面。
     */
    @Test
    fun matrix() {
        val days = listOf(LocalDate.of(2026, 10, 21), festivalDay(Festival.SPRING), festivalDay(Festival.MOON), festivalDay(Festival.XMAS))
        var n = 0
        days.forEachIndexed { i, day ->
            moods.forEachIndexed { j, mood ->
                val s = Scenario(
                    day, pages[(i + j) % pages.size], tornToday = j % 3 != 2, mood = mood,
                    hat = j % 2 == 0, bundle = (i + j) % 3 != 0, headwear = j % 4 != 1, reduced = j % 5 == 0,
                )
                prepare(s)
                widget()
                val c = launch(s.reduced)
                check(c.get(), "$day ${FestivalTheme.of(day) ?: "一般"} ${mood ?: "沒有天氣"} 第${s.page}頁${if (s.tornToday) "" else "（今天還沒撕）"}", s.reduced)
                c.pause().stop().destroy()
                n++
            }
        }
        assertEquals(36, n)
        save("matrix")
    }

    // ---------- 互動之後 ----------

    private fun prefsDump(): String = listOf("mowan", "story", "weather").joinToString("\n") { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toSortedMap().filterKeys { it != "fetchedAt" }.toString()
    }

    /** 點故事卡上的墨丸：只換台詞與動作，日期、頁數、紙帽、包袱、天氣都不變（一般日子與節日） */
    @Test
    fun tappingTheMowanChangesNoState() {
        for (day in listOf(LocalDate.of(2026, 10, 21), festivalDay(Festival.MOON))) {
            prepare(Scenario(day, 14, true, Mood.RAIN, hat = true, bundle = true, headwear = true, reduced = false))
            widget()
            val c = launch()
            val a = c.get()
            val before = check(a, "$day 點墨丸之前")
            val stored = prefsDump()
            val updates = sent.size
            a.findViewById<MowanView>(R.id.story_mowan).performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val after = check(a, "$day 點墨丸之後")
            assertEquals("$day：點墨丸不改任何存起來的狀態", stored, prefsDump())
            assertEquals("$day：共同狀態不變（不比動畫的中間姿勢）", before.map { it.equipment + it.date + it.festival + it.weather }, after.map { it.equipment + it.date + it.festival + it.weather })
            assertEquals("$day：點墨丸不會更新小工具", updates, sent.size)
            c.pause().stop().destroy()
        }
        save("tap")
    }

    /** 故事卡切換包袱：日曆頁與小工具（四種版面）同步 */
    @Test
    fun bundleToggleSyncsEverywhere() {
        prepare(Scenario(LocalDate.of(2026, 10, 21), 14, true, Mood.SUNNY, hat = true, bundle = true, headwear = true, reduced = false))
        widget()
        val c = launch()
        check(c.get(), "包袱：背著")
        c.get().findViewById<TextView>(R.id.story_bundle).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, MowanSettings.bundleOn(context))
        check(c.get(), "包袱：卸下")
        c.get().findViewById<TextView>(R.id.story_bundle).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        check(c.get(), "包袱：再背上")
        save("bundle")
    }

    private fun debugMenu(a: MainActivity, item: Int) {
        a.findViewById<View>(R.id.debug).performClick()
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(item)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** 紙帽、節日頭飾（除錯選單；正式版沒有這個入口，但走的是同一條「三處一起更新」的路） */
    @Test
    fun hatAndHeadwearSyncEverywhere() {
        val day = festivalDay(Festival.SPRING)
        prepare(Scenario(day, 14, true, Mood.CLOUDY, hat = true, bundle = true, headwear = true, reduced = false))
        widget()
        val c = launch()
        check(c.get(), "紙帽：戴著（春節）")
        debugMenu(c.get(), 8)
        assertEquals(false, MowanSettings.hatOn(context))
        check(c.get(), "紙帽：拿掉（春節）")
        debugMenu(c.get(), 11)
        assertEquals(false, MowanSettings.festivalHeadwear(context))
        check(c.get(), "節日頭飾：不戴")
        debugMenu(c.get(), 8)
        check(c.get(), "紙帽：戴回去、頭飾不戴")
        save("hat")
    }

    /** 真的天氣資料（Open-Meteo 格式，存在 prefs）；用 [WeatherService.current] 讀，跟正式一樣 */
    private fun storeWeather(city: String, code: Int, now: Double) {
        val json = """{"current":{"time":"2026-10-21T13:00","temperature_2m":$now,"weather_code":$code},
            "daily":{"time":["2026-10-21"],"weather_code":[$code],"temperature_2m_max":[${now + 3}],"temperature_2m_min":[${now - 3}]}}"""
        context.getSharedPreferences("weather", Context.MODE_PRIVATE).edit()
            .putString("json", json).putString("city", city).putLong("fetchedAt", System.currentTimeMillis()).commit()
    }

    /** 背景抓到新天氣（[MainActivity.onWeatherRefreshed] 是背景下載成功後走的路）：三處一起換 */
    @Test
    fun weatherUpdateSyncsEverywhere() {
        val day = LocalDate.of(2026, 10, 21)
        prepare(Scenario(day, 14, true, null, hat = true, bundle = true, headwear = true, reduced = false))
        storeWeather(Cities.selected(context).name, 61, 22.0) // 雨
        widget()
        val c = launch()
        assertEquals(Mowan.Weather.RAINY, truth().weather)
        check(c.get(), "天氣：雨")
        storeWeather(Cities.selected(context).name, 71, 1.0) // 雪
        c.get().onWeatherRefreshed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Mowan.Weather.SNOW, truth().weather)
        check(c.get(), "天氣更新：雪")
        save("weather")
    }

    /**
     * 換城市：存著的天氣是舊城市的，換了以後就不能再顯示（[WeatherService.current] 回傳 null），
     * 三處都要一起變成「沒有天氣」，直到新城市的天氣抓到。不連網（把 https 代理指到關著的連接埠，下載馬上失敗）。
     */
    @Test
    fun cityChangeSyncsEverywhere() {
        System.setProperty("https.proxyHost", "127.0.0.1")
        System.setProperty("https.proxyPort", "9")
        val day = LocalDate.of(2026, 10, 21)
        prepare(Scenario(day, 14, true, null, hat = true, bundle = true, headwear = true, reduced = false))
        val city = Cities.selected(context)
        storeWeather(city.name, 61, 22.0)
        widget()
        val c = launch()
        check(c.get(), "換城市之前：${city.name} 雨")
        val other = Cities.all.first { it != city }
        c.get().findViewById<View>(R.id.city).performClick()
        shadowOf(ShadowAlertDialog.getLatestAlertDialog()).clickOnItem(Cities.all.indexOf(other))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(other, Cities.selected(context))
        assertEquals("舊城市的天氣不再顯示", Mowan.Weather.NONE, truth().weather)
        check(c.get(), "換成 ${other.name}（新城市的天氣還沒抓到）")
        // 新城市的天氣抓到了（背景下載成功時走的路）
        storeWeather(other.name, 3, 18.0)
        c.get().onWeatherRefreshed()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Mowan.Weather.CLOUDY, truth().weather)
        check(c.get(), "${other.name} 的天氣抓到：陰")
        Thread.sleep(300) // 等背景下載（已經被擋掉）結束，不要留到下一個測試
        save("city")
    }

    /** 撕頁：成長頁數、部位三處一起換；撕之前日曆頁停在昨天（昨天那頁不畫今天的天氣） */
    @Test
    fun tearingSyncsGrowth() {
        val day = LocalDate.of(2026, 10, 21)
        prepare(Scenario(day, 13, false, Mood.SUNNY, hat = true, bundle = true, headwear = true, reduced = false))
        widget()
        val c = launch()
        assertEquals(day.minusDays(1), truth().top)
        check(c.get(), "撕之前：第 13 頁、日曆頁停在昨天")
        c.get().findViewById<View>(R.id.tear).performClick()
        org.robolectric.shadows.ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(14, StoryBook(context).page)
        assertTrue("第 14 頁拿到包袱", "bundle" in truth().parts)
        check(c.get(), "撕完：第 14 頁、今天")
        save("tear")
    }

    /** 節日當天、重開 App、跨午夜（App 停在前景時日曆頁暫時不動，D-44）、回到 App 之後 */
    @Test
    fun festivalDayAcrossReopenAndMidnight() {
        val moon = festivalDay(Festival.MOON)
        prepare(Scenario(moon, 14, true, Mood.SUNNY, hat = true, bundle = true, headwear = true, reduced = false))
        widget()
        var c = launch()
        check(c.get(), "中秋當天")
        c.pause().stop().destroy()
        c = launch()
        check(c.get(), "中秋：重開 App")
        // 跨午夜：系統送日期改變（小工具換頁）、App 停在前景（故事卡換成新的一天，日曆頁不動）
        val next = moon.plusDays(1)
        assertEquals(null, FestivalTheme.of(next))
        AppClock.setFakeToday(context, next)
        CalendarWidget().onReceive(context, android.content.Intent(android.content.Intent.ACTION_DATE_CHANGED))
        c.get().onDayChanged()
        shadowOf(Looper.getMainLooper()).idle()
        check(c.get(), "跨午夜、App 在前景（日曆頁還是中秋那頁）", pageDate = moon)
        // 回到 App：日曆頁照原本的做法重算（中秋那頁變成昨天那張）
        c.pause().resume()
        shadowOf(Looper.getMainLooper()).idle()
        check(c.get(), "回到 App 之後")
        save("festival")
    }

    /** Reduced Motion：故事卡變成靜態代表姿勢，三處的狀態不變（一般日子與節日） */
    @Test
    fun reducedMotionKeepsTheSameState() {
        for (day in listOf(LocalDate.of(2026, 10, 21), festivalDay(Festival.XMAS))) {
            prepare(Scenario(day, 14, true, Mood.SNOW, hat = false, bundle = true, headwear = true, reduced = false))
            widget()
            val moving = launch(reduced = false)
            val a = check(moving.get(), "$day 動畫開著", reduced = false)
            moving.pause().stop().destroy()
            val still = launch(reduced = true)
            val b = check(still.get(), "$day Reduced Motion", reduced = true)
            still.pause().stop().destroy()
            assertEquals("$day：Reduced Motion 不改變任何共同狀態", a.map { it.copy(still = false) }, b.map { it.copy(still = false) })
        }
        save("reduced")
    }

    /**
     * 小工具的靜態代表姿勢跟故事卡在 Reduced Motion 時畫的是同一組狀態（一般日子，8 種天氣＋沒有天氣，紙帽、包袱輪換）。
     * 小工具那張圖本身由 [specOf] 重畫比對逐像素確認；畫的大小與簡化程度依版面不同（D-25），所以這裡比狀態，不比兩邊的像素。
     */
    @Test
    fun widgetStillIsTheStoryCardsRepresentativePose() {
        for (mood in moods) {
            prepare(Scenario(LocalDate.of(2026, 10, 21), 14, true, mood, hat = mood != Mood.FOG, bundle = mood != Mood.RAIN, headwear = true, reduced = true))
            widget()
            val c = launch(reduced = true)
            val story = storyCard(c.get())
            assertTrue(story.still)
            val views = sent.values.last()
            for (layout in PageLayout.entries) {
                val got = specOf(stillOf(sized(views, layout).apply(context, FrameLayout(context))), layout.name)
                assertEquals("$mood ${layout.name}：小工具的靜態圖跟故事卡的代表姿勢是同一組狀態",
                    listOf(story.parts, story.hat, story.bundle, story.weather, story.festival),
                    listOf(got.parts, got.hatOn, got.bundleOn, got.weather, got.festival))
            }
            c.pause().stop().destroy()
        }
    }
}
