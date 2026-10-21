package com.patty60723.tearoff

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.stickman.StickmanView
import com.patty60723.tearoff.story.StoryBook
import java.time.LocalDate

class MainActivity : Activity() {
    private var date: LocalDate = LocalDate.now()
    private lateinit var book: StoryBook

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        savedInstanceState?.getString(KEY_DATE)?.let { date = LocalDate.parse(it) }

        findViewById<Button>(R.id.prev).setOnClickListener { show(date.minusDays(1)) }
        findViewById<Button>(R.id.next).setOnClickListener { show(date.plusDays(1)) }
        findViewById<Button>(R.id.today).setOnClickListener { show(LocalDate.now()) }

        book = StoryBook(this)
        findViewById<Button>(R.id.tear).setOnClickListener { onTear() }

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
        showStory()
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

    private fun onTear() {
        val today = LocalDate.now()
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

    /** 那一頁往上捲起飛走，再換成新的一頁。 */
    private fun tear(today: LocalDate) {
        val torn = book.tear(today) ?: return
        val preview = findViewById<FrameLayout>(R.id.preview)
        findViewById<Button>(R.id.tear).isEnabled = false
        preview.pivotX = 0f
        preview.pivotY = 0f
        preview.animate()
            .translationY(-preview.height * 0.7f)
            .rotation(-12f)
            .alpha(0f)
            .setDuration(450)
            .withEndAction {
                preview.translationY = 0f
                preview.rotation = 0f
                preview.animate().alpha(1f).setDuration(250).start()
                show(if (torn.date == today) today else torn.date.plusDays(1))
                showStory()
                CalendarWidget.refreshAll(this)
            }
            .start()
    }

    private fun showStory() {
        val today = LocalDate.now()
        val pending = book.pendingPages(today)
        val next = book.nextDate(today)
        findViewById<Button>(R.id.tear).apply {
            isEnabled = next != null
            text = when {
                next == null -> getString(R.string.tear_done)
                pending > 1 -> getString(R.string.tear_more, next.monthValue, next.dayOfMonth, pending - 1)
                else -> getString(R.string.tear_one, next.monthValue, next.dayOfMonth)
            }
        }

        findViewById<StickmanView>(R.id.stickman).apply {
            parts = book.parts
            action = Actions.get(if (book.page == 0) null else book.lastAction)
        }
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
