package com.patty60723.tearoff

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.text.InputType
import android.widget.EditText
import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.story.StoryBook
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
            "測試閃退紀錄（App 會閃退一次）",
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
                    6 -> throw RuntimeException("除錯選單：測試閃退紀錄")
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

    private fun confirm(message: String, then: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(message)
            .setPositiveButton("確定") { _, _ -> then() }
            .setNegativeButton("取消", null)
            .show()
    }
}
