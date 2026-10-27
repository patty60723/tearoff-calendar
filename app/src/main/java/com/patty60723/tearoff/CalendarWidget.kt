package com.patty60723.tearoff

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.patty60723.tearoff.mowan.ArtConfig
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.stickman.StickmanRenderer
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.weather.WeatherService
import kotlin.concurrent.thread
import java.time.LocalDate

class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        MidnightAlarm.schedule(context)
        refreshWeather(context)
    }

    /** 先用存著的天氣畫，背景抓到新的再畫一次。 */
    private fun refreshWeather(context: Context) {
        val pending = goAsync()
        thread {
            try {
                if (WeatherService.refreshIfStale(context)) refreshAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle,
    ) {
        render(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) = MidnightAlarm.schedule(context)

    override fun onDisabled(context: Context) = MidnightAlarm.cancel(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            MidnightAlarm.ACTION_NEW_DAY,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                refreshAll(context)
                MidnightAlarm.schedule(context)
                refreshWeather(context)
            }
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val page = Almanac.page(AppClock.today(context))
            for (id in ids) {
                val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 系統依小工具實際大小自動挑版面：挑放得下的裡面最大的那個
                    RemoteViews(PageLayout.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to build(context, page, it) })
                } else {
                    val options = manager.getAppWidgetOptions(id)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
                    build(context, page, PageLayout.fitting(width, height))
                }
                manager.updateAppWidget(id, views)
            }
        }

        /**
         * @param stickmanDp 火柴人畫多大；App 裡的大頁面會傳更大的值，圖才不會糊
         * @param festivalLook 今天那頁的 V3 墨丸畫節日外觀（D-41）。App 日曆頁（P6-4b）才傳 true；小工具在 P6-4c
         * @param art 美術設定；null 是 [MowanSettings.art]（P6-4c 候選圖用來比較 D-16／D-43 的小尺寸取捨）
         */
        fun build(
            context: Context, page: DayPage, layout: PageLayout, stickmanDp: Float = layout.stickmanDp,
            festivalLook: Boolean = false, art: ArtConfig? = null,
        ): RemoteViews {
            val palette = Palettes.forMonth(page.date.monthValue)
            return RemoteViews(context.packageName, layout.res).apply {
                setTextViewText(R.id.month, MONTHS[page.date.monthValue - 1])
                setTextViewText(R.id.month_en, "${MONTHS_EN[page.date.monthValue - 1]} ${page.date.year}")
                setTextViewText(R.id.weekday, page.weekday)
                setTextViewText(R.id.day, "%02d".format(page.date.dayOfMonth))
                setTextColor(R.id.day, if (page.isRedDay) Palettes.RED else palette.deep)
                setTextColor(R.id.month, palette.deep)
                setTextColor(R.id.weekday, palette.deep)

                val label = page.label
                setTextViewText(R.id.label, label ?: "")
                val fullPage = layout == PageLayout.TALL || layout == PageLayout.WIDE
                setViewVisibility(R.id.label, if (label != null && fullPage) View.VISIBLE else View.GONE)
                // 直式有節日時，英文月份讓位給節日，日期才不會被擠小
                if (layout == PageLayout.TALL) setViewVisibility(R.id.month_en, if (label == null) View.VISIBLE else View.GONE)
                setTextViewText(R.id.sun, sunText(context, page))
                setTextViewText(R.id.phrase, phrase(page, layout.yiCount))
                setTextViewText(R.id.lunar, "農曆${page.lunarDate}・${page.ganZhiYear}")
                setTextViewText(R.id.ji, avoid(page))

                for (id in intArrayOf(R.id.band, R.id.hills, R.id.rule)) setInt(id, "setColorFilter", palette.main)
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                if (stickmanDp > 0) {
                    // 故事卡用 V3 時，小工具和 App 日曆頁也用 V3（D-23、D-24）
                    if (MowanSettings.storyCard(context)) setMowan(context, this, stickmanDp, page.date, festivalLook, art ?: MowanSettings.art(context)) else setStickman(context, this, stickmanDp)
                }
            }
        }

        /** 「日出 05:53\n日落 17:22」，用設定的城市算。 */
        fun sunText(context: Context, page: DayPage): String {
            val city = Cities.selected(context)
            val times = SunTimes.of(page.date, city.lat, city.lon)
            val sun = "日出 ${times.sunrise ?: "--:--"}\n日落 ${times.sunset ?: "--:--"}"
            // 今天那頁才印天氣
            val weather = if (page.date == AppClock.today(context)) WeatherService.current(context) else null
            return if (weather == null) sun else "${weather.short}\n$sun"
        }

        /** 頁面上最大的那句：「宜 出行・祭祀」。 */
        fun phrase(page: DayPage, count: Int): String {
            val yi = page.yi.filter { it !in FILLER }
            return if (yi.isEmpty()) "諸事不宜" else "宜 " + yi.take(count).joinToString("・")
        }

        private fun avoid(page: DayPage): String {
            val ji = page.ji.filter { it !in FILLER }
            return if (ji.isEmpty()) "" else "忌 " + ji.take(3).joinToString("・")
        }

        private val FILLER = setOf("餘事勿取", "諸事不宜", "無")
        private val MONTHS = listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
        private val MONTHS_EN = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
        )

        private val FRAME_IDS = intArrayOf(
            R.id.frame0, R.id.frame1, R.id.frame2, R.id.frame3, R.id.frame4, R.id.frame5,
        )

        /**
         * V3 墨丸（P5-3、P5-4）：一張靜態代表姿勢（D-04），跟 App 用同樣的部位、紙帽、包袱、天氣；不播撕頁動作（D-27）。
         * 畫在版面裡的 `mowan_still`（一般的 ImageView，不會動）；舊火柴人的 ViewFlipper 隱藏、清掉圖。
         * ViewFlipper 的自動輪播只有 `setFlipInterval` 能透過 RemoteViews 改（`setAutoStart`、`stopFlipping` 不行），
         * 隱藏的 ViewFlipper 仍會照間隔換頁，所以間隔拉到一天、裡面沒有圖。
         * 天氣只有今天那頁才畫（跟日出日落旁的天氣文字一樣）；節日外觀也只有今天那頁（D-41），而且要 [festivalLook]。
         */
        private fun setMowan(context: Context, views: RemoteViews, widthDp: Float, date: LocalDate, festivalLook: Boolean, art: ArtConfig) {
            val book = StoryBook(context)
            val density = context.resources.displayMetrics.density.coerceAtMost(3f)
            val isToday = date == AppClock.today(context)
            val weather = if (isToday) {
                WeatherService.current(context)?.let { MowanSettings.weatherOf(it.mood) } ?: Mowan.Weather.NONE
            } else {
                Mowan.Weather.NONE
            }
            val bitmap = MowanBitmap.still(
                Mowan.partsOf(book.parts), MowanSettings.hatOn(context), MowanSettings.bundleOn(context), weather,
                widthPx = (widthDp * density).toInt(), heightPx = (widthDp * 4 / 3 * density).toInt(), density = density,
                festival = if (festivalLook && isToday) FestivalTheme.of(date) else null, art = art,
            )
            views.setViewVisibility(R.id.stickman, View.GONE)
            for (id in FRAME_IDS) views.setImageViewResource(id, 0)
            views.setDisplayedChild(R.id.stickman, 0)
            views.setInt(R.id.stickman, "setFlipInterval", STILL_FLIP_MS)
            views.setViewVisibility(R.id.mowan_still, View.VISIBLE)
            views.setImageViewBitmap(R.id.mowan_still, bitmap)
        }

        /** V3 時隱藏的 ViewFlipper 的輪播間隔：一天（裡面沒有圖） */
        internal const val STILL_FLIP_MS = 24 * 60 * 60 * 1000

        /** 火柴人目前長到哪裡、最近一次撕頁的動作，畫成幾張圖輪播。 */
        private fun setStickman(context: Context, views: RemoteViews, widthDp: Float) {
            val book = StoryBook(context)
            val action = Actions.get(if (book.page == 0) null else book.lastAction)
            val density = context.resources.displayMetrics.density.coerceAtMost(3f)
            val frames = StickmanRenderer.frames(
                book.parts, action, FRAME_IDS.size,
                widthPx = (widthDp * density).toInt(), heightPx = (widthDp * 4 / 3 * density).toInt(),
                props = if (book.page == 0) emptySet() else book.lastProps,
            )
            frames.forEachIndexed { i, bitmap -> views.setImageViewBitmap(FRAME_IDS[i], bitmap) }
            // 從 V3 切回來時（同一個版面重新套用）要把 V3 的圖藏起來
            views.setViewVisibility(R.id.stickman, View.VISIBLE)
            views.setViewVisibility(R.id.mowan_still, View.GONE)
            views.setImageViewResource(R.id.mowan_still, 0)
            views.setInt(R.id.stickman, "setFlipInterval", (action.periodMs / FRAME_IDS.size).toInt())
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
