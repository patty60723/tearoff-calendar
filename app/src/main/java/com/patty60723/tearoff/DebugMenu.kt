package com.patty60723.tearoff

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.patty60723.tearoff.mowan.Capabilities
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanSettings
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
            "看墨丸 V3…",
            "故事卡用墨丸 V3（現在：${if (MowanSettings.storyCard(activity)) "開" else "關"}）",
            "墨丸的紙帽（現在：${hatState()}）",
        )
        AlertDialog.Builder(activity)
            .setTitle("除錯")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> pickDate(today)
                    1 -> setToday(today.plusDays(1))
                    2 -> setToday(null)
                    3 -> askPage()
                    4 -> confirm("確定要從頭開始？名字和紙帽也會回到預設。") {
                        book.reset()
                        MowanSettings.resetEquipment(activity)
                        refresh()
                    }
                    5 -> pickAction()
                    6 -> pickWeather()
                    7 -> throw RuntimeException("除錯選單：測試閃退紀錄")
                    8 -> showMowan()
                    9 -> {
                        MowanSettings.setStoryCard(activity, !MowanSettings.storyCard(activity))
                        refresh()
                    }
                    10 -> {
                        MowanSettings.setHatOn(activity, !MowanSettings.hatOn(activity))
                        refresh()
                    }
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

    /** 紙帽要第 11 頁才拿到；拿到之前切換也會記住，拿到時就照這個狀態 */
    private fun hatState(): String {
        val on = if (MowanSettings.hatOn(activity)) "戴著" else "脫下"
        return if (Capabilities.of(Mowan.partsOf(book.parts)).paperHat) on else "還沒拿到，拿到後$on"
    }

    /** 墨丸 V3 的預覽：可以切頁數、天氣、紙帽，line boil 會自己動。 */
    private fun showMowan() {
        val density = activity.resources.displayMetrics.density
        val view = MowanView(activity).apply {
            hatOn = MowanSettings.hatOn(activity)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (320 * density).toInt())
        }
        val label = TextView(activity)
        var page = book.page.coerceIn(1, 14)
        val weatherName = mapOf(
            Mowan.Weather.NONE to "一般", Mowan.Weather.SUNNY to "晴天", Mowan.Weather.RAINY to "雨天",
            Mowan.Weather.HOT to "熱", Mowan.Weather.CLOUDY to "陰", Mowan.Weather.FOG to "霧",
            Mowan.Weather.THUNDER to "雷雨", Mowan.Weather.COLD to "冷", Mowan.Weather.SNOW to "雪",
        )
        fun update() {
            view.parts = Mowan.partsOf(StoryEngine.unlocked(book.beats, page))
            label.text = "第 $page 頁・${weatherName[view.weather]}・紙帽${if (view.hatOn) "戴著" else "脫下"}"
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
            .setTitle("墨丸 V3")
            .setView(layout)
            .setPositiveButton("關閉", null)
            .setNeutralButton("換天氣", null)
            .setNegativeButton("紙帽", null)
            .show()
        // 這兩個按鈕按了不要關掉對話框
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val all = Mowan.Weather.entries
            view.weather = all[(view.weather.ordinal + 1) % all.size]
            update()
        }
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
