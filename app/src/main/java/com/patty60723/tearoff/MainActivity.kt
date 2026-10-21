package com.patty60723.tearoff

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import java.time.LocalDate

class MainActivity : Activity() {
    private var date: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState?.getString(KEY_DATE)?.let { date = LocalDate.parse(it) }

        findViewById<Button>(R.id.prev).setOnClickListener { show(date.minusDays(1)) }
        findViewById<Button>(R.id.next).setOnClickListener { show(date.plusDays(1)) }
        findViewById<Button>(R.id.today).setOnClickListener { show(LocalDate.now()) }

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

    override fun onResume() {
        super.onResume()
        // 打開 App 時順便更新桌面上的小工具，以防午夜那次換頁被系統延誤
        CalendarWidget.refreshAll(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_DATE, date.toString())
    }

    private fun show(newDate: LocalDate) {
        date = newDate
        val page = Almanac.page(date)

        val preview = findViewById<FrameLayout>(R.id.preview)
        preview.removeAllViews()
        val views = CalendarWidget.build(this, page, compact = false)
        preview.addView(views.apply(this, preview).apply { isClickable = false })

        findViewById<Button>(R.id.today).visibility =
            if (date == LocalDate.now()) View.INVISIBLE else View.VISIBLE
        findViewById<TextView>(R.id.yi_all).text = page.yi.joinToString("　")
        findViewById<TextView>(R.id.ji_all).text = page.ji.joinToString("　")
        findViewById<TextView>(R.id.details).text = buildString {
            append("${page.chong}　${page.sha}")
            page.solarTerm?.let { append("\n節氣：$it") }
            if (page.festivals.isNotEmpty()) append("\n節日：${page.festivals.joinToString("、")}")
        }
    }

    private companion object {
        const val KEY_DATE = "date"
    }
}
