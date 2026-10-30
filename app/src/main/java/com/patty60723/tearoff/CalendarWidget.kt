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
import com.patty60723.tearoff.mowan.ArtConfig.SmallEnv
import com.patty60723.tearoff.mowan.FestivalTheme
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanBitmap
import com.patty60723.tearoff.mowan.MowanSettings
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
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
                val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 系統依小工具實際大小自動挑版面：挑放得下的裡面最大的那個
                    val wide = wideWidthDp(options, width)
                    RemoteViews(PageLayout.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to build(context, page, it, wideWidthDp = wide) })
                } else {
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
                    build(context, page, PageLayout.fitting(width, height), wideWidthDp = width)
                }
                manager.updateAppWidget(id, views)
            }
        }

        /**
         * WIDE 版面實際有多寬（決定文字要不要精簡，[WideText]）：系統回報的各種大小裡，會用 WIDE 的那些取最窄的；
         * 沒有回報時用最小寬度。比 WIDE 的最小寬度還窄（還沒回報）就當最小寬度，寧可精簡也不要截斷。
         */
        @android.annotation.TargetApi(Build.VERSION_CODES.S)
        private fun wideWidthDp(options: android.os.Bundle, minWidth: Float): Float {
            @Suppress("DEPRECATION")
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
            val widths = sizes.filter { PageLayout.fitting(it.width, it.height) == PageLayout.WIDE }.map { it.width }
            return maxOf(widths.minOrNull() ?: minWidth, PageLayout.WIDE.minWidthDp)
        }

        /**
         * @param characterDp 墨丸畫多大；App 裡的大頁面會傳更大的值，圖才不會糊；0 是不畫
         * @param festivalLook 今天那頁的 V3 墨丸畫節日外觀（D-16、D-41）：小工具（P6-4c）與 App 日曆頁（P6-4b）都是 true；
         *   false 只給候選圖、截圖畫「沒有節日」的對照
         * @param wideWidthDp WIDE 小工具的實際寬度（dp），決定右欄文字放多少（[WideText]）；其他版面不用
         * @param art 美術設定；null 是 [MowanSettings.art] 加上這個版面的小尺寸取捨（[smallEnvFor]）。P6-4c 候選圖用來比較
         */
        fun build(
            context: Context, page: DayPage, layout: PageLayout, characterDp: Float = layout.characterDp,
            festivalLook: Boolean = true, art: ArtConfig? = null, wideWidthDp: Float = PageLayout.WIDE.minWidthDp,
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
                if (layout == PageLayout.WIDE) {
                    // WIDE 放得下多少放多少（P7-1）；星期疊在山丘上，用加深的月份色（4.5:1）
                    val t = WideText.of(page, wideWidthDp, context.resources.configuration.fontScale, layout.yiCount)
                    setTextViewText(R.id.phrase, t.phrase)
                    setTextViewTextSize(R.id.phrase, android.util.TypedValue.COMPLEX_UNIT_SP, t.phraseSp)
                    setTextViewText(R.id.lunar, t.lunar)
                    setTextViewText(R.id.ji, t.ji)
                    setTextColor(R.id.weekday, Palettes.blend(Palettes.INK, palette.deep, 0.4f))
                }

                for (id in intArrayOf(R.id.band, R.id.hills, R.id.rule)) setInt(id, "setColorFilter", palette.main)
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                if (characterDp > 0) {
                    setMowan(context, this, characterDp, page.date, festivalLook, art ?: MowanSettings.art(context).copy(smallEnv = smallEnvFor(layout)))
                }
            }
        }

        /**
         * 小工具上節日背景與天氣環境怎麼取捨（D-16、D-43；2026-10-27 選定方向，待最終圖片驗收）：
         * COMPACT（角色 48dp）只留頭飾與手上道具（C）；其他版面節日背景＋最多一個不衝突的天氣線索（B）。
         * 只影響簡化版（角色 < 72dp）的節日畫法；沒有節日、或完整版時不影響。
         */
        internal fun smallEnvFor(layout: PageLayout): SmallEnv = if (layout == PageLayout.COMPACT) SmallEnv.MINIMAL else SmallEnv.CUE

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

        private val FILLER = WideText.FILLER
        private val MONTHS = listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月")
        private val MONTHS_EN = listOf(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER",
        )

        /**
         * V3 墨丸（P5-3、P5-4）：一張靜態代表姿勢（D-04），跟 App 用同樣的部位、紙帽、包袱、天氣；不播撕頁動作（D-27）。
         * 畫在版面裡的 `mowan_still`（一般的 ImageView，不會動；P7-1 起舊火柴人的 6 張輪播已移除）。
         * 天氣只有今天那頁才畫（跟日出日落旁的天氣文字一樣）；節日外觀也只有今天那頁（D-41）。
         * 節日也是同一張靜態圖：不演出、不輪播（D-16）；背景與天氣的取捨照版面（[smallEnvFor]）。
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
            views.setViewVisibility(R.id.mowan_still, View.VISIBLE)
            views.setImageViewBitmap(R.id.mowan_still, bitmap)
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
