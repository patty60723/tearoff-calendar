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
                    // 系統依小工具實際大小自動挑版面
                    RemoteViews(
                        mapOf(
                            SizeF(100f, 100f) to build(context, page, compact = true),
                            SizeF(220f, 100f) to build(context, page, compact = false),
                        )
                    )
                } else {
                    val minWidth = manager.getAppWidgetOptions(id)
                        .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    build(context, page, compact = minWidth in 1 until 220)
                }
                manager.updateAppWidget(id, views)
            }
        }

        fun build(context: Context, page: DayPage, compact: Boolean): RemoteViews {
            val layout = if (compact) R.layout.widget_compact else R.layout.widget_wide
            val items = if (compact) 3 else 4
            return RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.month, "${page.date.year}年${page.date.monthValue}月")
                setTextViewText(R.id.weekday, page.weekday)
                setTextViewText(R.id.day, page.date.dayOfMonth.toString())
                setTextColor(
                    R.id.day,
                    context.getColor(if (page.isRedDay) R.color.red else R.color.ink),
                )
                val label = page.label
                setTextViewText(R.id.label, label ?: "")
                setViewVisibility(R.id.label, if (label == null) View.GONE else View.VISIBLE)
                setTextViewText(R.id.lunar, "農曆${page.lunarDate}")
                setTextViewText(R.id.ganzhi, "${page.ganZhiYear}・${page.zodiac}")
                setTextViewText(R.id.yi, page.yi.take(items).joinToString(" "))
                setTextViewText(R.id.ji, page.ji.take(items).joinToString(" "))
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
                if (!compact) setStickman(context, this)
            }
        }

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
