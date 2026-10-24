package com.patty60723.tearoff

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.stickman.Parts
import com.patty60723.tearoff.stickman.StickmanView
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.WeatherService
import kotlin.concurrent.thread
import java.time.LocalDate

class MainActivity : Activity() {
    /** 正在看的那一頁；null 是封面（還沒撕過任何一頁） */
    private var date: LocalDate? = null
    private lateinit var book: StoryBook
    private lateinit var pad: TearPageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.read(this)?.let {
            showCrash(it)
            return
        }
        setContentView(R.layout.activity_main)
        book = StoryBook(this)
        date = savedInstanceState?.getString(KEY_DATE)?.let(LocalDate::parse) ?: topDate()

        findViewById<Button>(R.id.prev).setOnClickListener { show(viewing().minusDays(1)) }
        findViewById<Button>(R.id.next).setOnClickListener { show(viewing().plusDays(1)) }
        findViewById<Button>(R.id.today).setOnClickListener { show(topDate()) }

        pad = findViewById(R.id.page)
        pad.pageBelow = { w, h -> snapshot(pageView(date?.plusDays(1) ?: AppClock.today(this)), w, h) }
        pad.onTorn = { onTorn() }
        pad.onBlocked = {
            val message = if (book.nextDate(AppClock.today(this)) == null) R.string.tear_done else R.string.tear_only_top
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.tear).setOnClickListener { pad.peelAway() }
        findViewById<TextView>(R.id.city).setOnClickListener { pickCity() }
        findViewById<TextView>(R.id.weather).setOnClickListener { playWeather() }

        findViewById<Button>(R.id.debug).apply {
            visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
            val menu = DebugMenu(
                this@MainActivity, book,
                refresh = {
                    show(topDate())
                    showStory()
                    showWeather()
                    CalendarWidget.refreshAll(this@MainActivity)
                },
                preview = { id ->
                    // 在 Button.apply 裡，要明確用 Activity 的 findViewById
                    this@MainActivity.findViewById<MowanView>(R.id.story_mowan).visibility = View.GONE
                    this@MainActivity.findViewById<StickmanView>(R.id.story_stickman).apply {
                        visibility = View.VISIBLE
                        parts = Parts.everything
                        action = Actions.get(id)
                        props = emptySet()
                    }
                    this@MainActivity.findViewById<TextView>(R.id.story_title).text = "動作：$id"
                },
            )
            setOnClickListener { menu.show() }
        }

        val manager = AppWidgetManager.getInstance(this)
        val pin = findViewById<Button>(R.id.pin)
        if (manager.isRequestPinAppWidgetSupported) {
            pin.setOnClickListener {
                manager.requestPinAppWidget(ComponentName(this, CalendarWidget::class.java), null, null)
            }
        } else {
            pin.visibility = View.GONE
        }
        show(date)
    }

    /** 上次閃退的錯誤訊息，截圖回報後按「繼續」回到日曆。 */
    private fun showCrash(text: String) {
        val view = TextView(this).apply {
            this.text = getString(R.string.crash_title) + "\n\n" + text
            setTextIsSelectable(true)
            textSize = 12f
            setPadding(32, 32, 32, 32)
        }
        val scroll = android.widget.ScrollView(this).apply {
            fitsSystemWindows = true
            addView(view)
        }
        setContentView(scroll)
        AlertDialog.Builder(this)
            .setMessage(R.string.crash_hint)
            .setPositiveButton(R.string.crash_continue) { _, _ ->
                CrashLog.clear(this)
                recreate()
            }
            .setNegativeButton(R.string.crash_stay, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (!::book.isInitialized) return
        // 打開 App 時順便更新桌面上的小工具，以防午夜那次換頁被系統延誤
        CalendarWidget.refreshAll(this)
        // 可能隔天才回到 App，最上面那張要重新算
        show(topDate())
        showStory()
        refreshWeather()
    }

    /** 先顯示存著的天氣，背景抓到新的再更新畫面和小工具。 */
    private fun refreshWeather() {
        showWeather()
        thread {
            if (WeatherService.refreshIfStale(this)) runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                showWeather()
                show(date)
                CalendarWidget.refreshAll(this)
            }
        }
    }

    private fun showWeather() {
        val weather = WeatherService.current(this)
        findViewById<TextView>(R.id.weather).text =
            if (weather == null) getString(R.string.weather_none) else getString(R.string.weather_row, weather.long)
        // 天氣資料更新：墨丸換成新的天氣；還是同一種天氣就不重來
        if (MowanSettings.storyCard(this)) findViewById<MowanView>(R.id.story_mowan).weather = currentMowanWeather()
    }

    /** 故事卡上墨丸的天氣日常：今天的天氣，沒有資料時是一般站姿 */
    private fun currentMowanWeather(): Mowan.Weather =
        WeatherService.current(this)?.let { MowanSettings.weatherOf(it.mood) } ?: Mowan.Weather.NONE

    private val backToStory = Runnable { showStory() }

    /** 除錯選單打開「故事卡用墨丸 V3」時，用 V3 取代火柴人 */
    private fun showMowan(weather: Mowan.Weather) {
        val v3 = MowanSettings.storyCard(this)
        findViewById<StickmanView>(R.id.story_stickman).visibility = if (v3) View.GONE else View.VISIBLE
        findViewById<MowanView>(R.id.story_mowan).apply {
            visibility = if (v3) View.VISIBLE else View.GONE
            parts = Mowan.partsOf(book.parts)
            hatOn = MowanSettings.hatOn(this@MainActivity)
            bundleOn = MowanSettings.bundleOn(this@MainActivity)
            this.weather = weather
        }
        showBundleToggle(v3)
    }

    /** 包袱的背上／卸下：用 V3 故事卡、而且第 14 頁拿到包袱以後才出現 */
    private fun showBundleToggle(v3: Boolean) {
        val unlocked = "bundle" in Mowan.partsOf(book.parts)
        findViewById<TextView>(R.id.story_bundle).apply {
            visibility = if (v3 && unlocked) View.VISIBLE else View.GONE
            text = getString(if (MowanSettings.bundleOn(this@MainActivity)) R.string.bundle_take_off else R.string.bundle_put_on)
            setOnClickListener {
                if ("bundle" !in Mowan.partsOf(book.parts)) return@setOnClickListener
                MowanSettings.setBundleOn(this@MainActivity, !MowanSettings.bundleOn(this@MainActivity))
                // 只換外觀，墨丸的動作不重來
                this@MainActivity.findViewById<MowanView>(R.id.story_mowan).bundleOn = MowanSettings.bundleOn(this@MainActivity)
                showBundleToggle(MowanSettings.storyCard(this@MainActivity))
            }
        }
    }

    /** 點天氣：故事卡上的火柴人演一段，幾秒後回到故事。 */
    private fun playWeather() {
        val weather = WeatherService.current(this) ?: return refreshWeather()
        val mood = weather.mood
        findViewById<StickmanView>(R.id.story_stickman).apply {
            parts = book.parts
            action = Actions.get(mood.action)
            props = mood.props
        }
        showMowan(MowanSettings.weatherOf(mood))
        findViewById<TextView>(R.id.story_title).text = weather.long
        findViewById<TextView>(R.id.story_text).text = "${book.name}：${mood.line}"
        pad.removeCallbacks(backToStory)
        pad.postDelayed(backToStory, 6000)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        date?.let { outState.putString(KEY_DATE, it.toString()) }
    }

    /**
     * 最上面那張，跟真的手撕日曆一樣：今天的頁撕開後就一直留在最上面；
     * 到了新的一天，上面還是昨天那頁（漏撕的話是更早的），撕掉才看到今天。
     * 還沒撕過任何一頁時是封面（null）。
     */
    private fun topDate(): LocalDate? {
        if (book.page == 0) return null
        val today = AppClock.today(this)
        return book.nextDate(today)?.minusDays(1) ?: today
    }

    /** 封面時當作在看今天 */
    private fun viewing(): LocalDate = date ?: AppClock.today(this)

    private fun show(newDate: LocalDate?) {
        date = newDate
        val today = AppClock.today(this)
        val page = Almanac.page(newDate ?: today)

        pad.removeAllViews()
        pad.addView(if (newDate == null) coverView() else pageView(newDate))
        // 只有最上面那張、而且還有沒撕的頁時才能撕
        pad.tearable = newDate == topDate() && book.nextDate(today) != null

        findViewById<Button>(R.id.today).visibility = if (newDate == topDate()) View.INVISIBLE else View.VISIBLE
        findViewById<TextView>(R.id.yi_all).text = page.yi.joinToString("　")
        findViewById<TextView>(R.id.ji_all).text = page.ji.joinToString("　")
        findViewById<TextView>(R.id.details).text = buildString {
            append("${page.date.monthValue}月${page.date.dayOfMonth}日　${page.chong}　${page.sha}")
            page.solarTerm?.let { append("\n節氣：$it") }
            if (page.festivals.isNotEmpty()) append("\n節日：${page.festivals.joinToString("、")}")
        }
        showHint()
        showCity()
    }

    private fun pickCity() {
        val cities = Cities.all
        val current = cities.indexOf(Cities.selected(this))
        AlertDialog.Builder(this)
            .setTitle(R.string.city_title)
            .setSingleChoiceItems(cities.map { it.name }.toTypedArray(), current) { dialog, which ->
                Cities.select(this, cities[which])
                dialog.dismiss()
                show(date)
                CalendarWidget.refreshAll(this)
            }
            .show()
    }

    private fun showCity() {
        findViewById<TextView>(R.id.city).text = getString(R.string.city_row, Cities.selected(this).name)
    }

    /** 封面：第一次打開時最上面的那張。 */
    private fun coverView(): View {
        val view = layoutInflater.inflate(R.layout.page_cover, null)
        val today = AppClock.today(this)
        val palette = Palettes.forMonth(today.monthValue)
        view.findViewById<android.widget.ImageView>(R.id.band).setColorFilter(palette.main)
        view.findViewById<android.widget.ImageView>(R.id.hills).setColorFilter(palette.main)
        view.findViewById<TextView>(R.id.cover_year).apply {
            text = today.year.toString()
            setTextColor(palette.deep)
        }
        return view
    }

    /** 一頁日曆；還沒到的日子蓋一層薄紙。 */
    private fun pageView(day: LocalDate): View {
        val frame = FrameLayout(this)
        // App 裡的頁面比小工具大很多，火柴人和山丘照頁寬等比例放大
        val metrics = resources.displayMetrics
        val pageWidthPx = metrics.widthPixels - (56 * metrics.density).toInt()
        val stickmanPx = (pageWidthPx * 0.17f).toInt()
        val views = CalendarWidget.build(this, Almanac.page(day), PageLayout.TALL, stickmanPx / metrics.density)
        val page = views.apply(this, frame).apply { isClickable = false }
        page.findViewById<View>(R.id.stickman)?.layoutParams?.let {
            it.width = stickmanPx
            it.height = stickmanPx * 4 / 3
            (it as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin = (pageWidthPx * 0.09f).toInt()
        }
        page.findViewById<View>(R.id.hills)?.layoutParams?.height = (pageWidthPx * 0.26f).toInt()
        page.findViewById<TextView>(R.id.sun)?.apply {
            textSize = 12f
            (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
                it.leftMargin = (pageWidthPx * 0.06f).toInt()
                it.bottomMargin = (pageWidthPx * 0.05f).toInt()
            }
        }
        frame.addView(page)
        val today = AppClock.today(this)
        if (day.isAfter(today)) {
            frame.addView(
                TextView(this).apply {
                    text = if (day == today.plusDays(1)) getString(R.string.veil_tomorrow) else getString(R.string.veil_later, day.monthValue, day.dayOfMonth)
                    gravity = Gravity.CENTER
                    textSize = 18f
                    letterSpacing = 0.2f
                    typeface = android.graphics.Typeface.SERIF
                    setTextColor(getColor(R.color.ink))
                    setBackgroundColor(0xB3FBF8F2.toInt())
                },
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
            )
        }
        return frame
    }

    private fun snapshot(view: View, width: Int, height: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun onTorn() {
        val today = AppClock.today(this)
        val beat = book.peek(today)
        if (beat?.ask == "name") askName { tear(today) } else tear(today)
    }

    private fun askName(then: () -> Unit) {
        val input = EditText(this).apply {
            setText(book.name)
            setSelection(text.length)
            maxLines = 1
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.name_title)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.name_ok) { _, _ ->
                input.text.toString().trim().take(8).takeIf { it.isNotEmpty() }?.let { book.name = it }
                then()
            }
            .show()
    }

    /** 紙已經撕下來了：記下這一頁的故事，換上下一頁。 */
    private fun tear(today: LocalDate) {
        book.tear(today) ?: return
        show(topDate())
        showStory()
        CalendarWidget.refreshAll(this)
    }

    private fun showHint() {
        val today = AppClock.today(this)
        val next = book.nextDate(today)
        val pending = book.pendingPages(today)
        val showing = date
        findViewById<TextView>(R.id.hint).text = when {
            showing != topDate() -> getString(R.string.hint_browsing, viewing().monthValue, viewing().dayOfMonth)
            showing == null -> getString(R.string.hint_cover)
            next == null -> getString(R.string.tear_done)
            pending > 1 -> getString(R.string.hint_more, pending)
            else -> getString(R.string.hint_tear)
        }
        findViewById<Button>(R.id.tear).visibility = if (pad.tearable) View.VISIBLE else View.GONE
    }

    private fun showStory() {
        findViewById<StickmanView>(R.id.story_stickman).apply {
            parts = book.parts
            action = Actions.get(if (book.page == 0) null else book.lastAction)
            props = book.lastProps
        }
        // V3 還沒有故事動作（跳、揮手…）的設計；故事卡平常演今天的天氣（天氣日常），沒有天氣資料時是一般站姿
        showMowan(currentMowanWeather())
        val started = book.page > 0
        findViewById<TextView>(R.id.story_title).text =
            if (started) getString(R.string.story_page, book.page) else getString(R.string.story_not_started)
        findViewById<TextView>(R.id.story_text).text =
            if (started) book.lastText else getString(R.string.story_hint)
    }

    private companion object {
        const val KEY_DATE = "date"
    }
}
