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
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.stickman.StickmanRenderer
import com.patty60723.tearoff.story.StoryBook
import java.time.LocalDate

class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
        MidnightAlarm.schedule(context)
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
            val page = Almanac.page(LocalDate.now())
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

        fun build(context: Context, page: DayPage, layout: PageLayout): RemoteViews {
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
                setViewVisibility(R.id.label, if (label == null || layout == PageLayout.COMPACT) View.GONE else View.VISIBLE)
                setTextViewText(R.id.phrase, phrase(page, layout.yiCount))
                setTextViewText(R.id.lunar, "農曆${page.lunarDate}・${page.ganZhiYear}")
                setTextViewText(R.id.ji, avoid(page))

                for (id in intArrayOf(R.id.band, R.id.hills, R.id.rule)) setInt(id, "setColorFilter", palette.main)
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                if (layout.stickman) setStickman(context, this)
            }
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

        /** 火柴人目前長到哪裡、最近一次撕頁的動作，畫成幾張圖輪播。 */
        private fun setStickman(context: Context, views: RemoteViews) {
            val book = StoryBook(context)
            val action = Actions.get(if (book.page == 0) null else book.lastAction)
            val density = context.resources.displayMetrics.density.coerceAtMost(3f)
            val frames = StickmanRenderer.frames(
                book.parts, action, FRAME_IDS.size,
                widthPx = (36 * density).toInt(), heightPx = (48 * density).toInt(),
            )
            frames.forEachIndexed { i, bitmap -> views.setImageViewBitmap(FRAME_IDS[i], bitmap) }
            views.setInt(R.id.stickman, "setFlipInterval", (action.periodMs / FRAME_IDS.size).toInt())
        }

        private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
