package com.patty60723.tearoff

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.story.StoryBook
import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.weather.Mood
import com.patty60723.tearoff.weather.WeatherService
import java.time.LocalDate

/** 測試版才有的除錯選單：假裝別天、跳頁、重來、看動作。 */
class DebugMenu(
    private val activity: Activity,
    private val book: StoryBook,
    /** 改完之後讓畫面和小工具重新整理 */
    private val refresh: () -> Unit,
    /** 在故事卡上播某個動作（全身配件都戴上） */
    private val preview: (actionId: String) -> Unit,
) {
    fun show() {
        val fake = AppClock.fakeToday(activity)
        val today = AppClock.today(activity)
        val items = arrayOf(
            "假裝今天是…（現在：${fake?.let { "$it（假的）" } ?: "$today（真的）"}）",
            "假裝的日期 +1 天",
            "回到真實日期",
            "跳到第幾頁…（現在第 ${book.page} 頁）",
            "重設故事，從第 0 頁開始",
            "看火柴人的動作…",
            "假裝天氣是…（現在：${WeatherService.fakeMood(activity)?.keyword ?: "真的天氣"}）",
            "測試閃退紀錄（App 會閃退一次）",
            "看墨丸 V3（靜態預覽）…",
        )
        AlertDialog.Builder(activity)
            .setTitle("除錯")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickDate(today)
                    1 -> setToday(today.plusDays(1))
                    2 -> setToday(null)
                    3 -> askPage()
                    4 -> confirm("確定要從頭開始？名字也會清掉。") { book.reset(); refresh() }
                    5 -> pickAction()
                    6 -> pickWeather()
                    7 -> throw RuntimeException("除錯選單：測試閃退紀錄")
                    8 -> showMowan()
                }
            }
            .show()
    }

    private fun setToday(date: LocalDate?) {
        AppClock.setFakeToday(activity, date)
        refresh()
    }

    private fun pickDate(today: LocalDate) {
        DatePickerDialog(
            activity,
            { _, y, m, d -> setToday(LocalDate.of(y, m + 1, d)) },
            today.year, today.monthValue - 1, today.dayOfMonth,
        ).show()
    }

    private fun askPage() {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "例如 11"
        }
        AlertDialog.Builder(activity)
            .setTitle("跳到已經撕了幾頁")
            .setMessage("最後一頁算昨天撕的，今天可以接著撕下一頁。")
            .setView(input)
            .setPositiveButton("跳過去") { _, _ ->
                input.text.toString().toIntOrNull()?.takeIf { it >= 0 }?.let {
                    book.jumpTo(it, AppClock.today(activity))
                    refresh()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun pickAction() {
        val ids = Actions.all.keys.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("看火柴人的動作")
            .setItems(ids) { _, which -> preview(ids[which]) }
            .show()
    }

    private fun pickWeather() {
        val moods = Mood.entries
        val names = moods.map { "${it.keyword}（${it.line}）" } + "用真的天氣"
        AlertDialog.Builder(activity)
            .setTitle("假裝天氣是")
            .setItems(names.toTypedArray()) { _, which ->
                WeatherService.setFakeMood(activity, moods.getOrNull(which))
                refresh()
            }
            .show()
    }

    /** 墨丸 V3 還沒接到故事卡上，先在這裡看靜態的樣子：可以切頁數、筆觸影格、紙帽。 */
    private fun showMowan() {
        val density = activity.resources.displayMetrics.density
        val view = MowanView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320 * density).toInt())
        }
        val label = TextView(activity)
        var page = book.page.coerceIn(1, 14)
        fun update() {
            view.parts = Mowan.partsOf(StoryEngine.unlocked(book.beats, page))
            label.text = "第 $page 頁・筆觸影格 ${view.variant + 1}/${Mowan.FRAMES}・紙帽${if (view.hatOn) "戴著" else "脫下"}"
        }
        val pages = SeekBar(activity).apply {
            max = 13
            progress = page - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) { page = value + 1; update() }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(view)
            addView(label)
            addView(pages)
        }
        update()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("墨丸 V3（靜態預覽）")
            .setView(layout)
            .setPositiveButton("關閉", null)
            .setNeutralButton("下一張影格", null)
            .setNegativeButton("紙帽", null)
            .show()
        // 這兩個按鈕按了不要關掉對話框
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { view.variant += 1; update() }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { view.hatOn = !view.hatOn; update() }
    }

    private fun confirm(message: String, then: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton("確定") { _, _ -> then() }
            .setNegativeButton("取消", null)
            .show()
    }
}
