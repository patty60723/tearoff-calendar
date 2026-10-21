package com.patty60723.tearoff

import android.content.Context
import java.time.LocalDate

/**
 * App 裡的「今天」。除錯時可以假裝成別天，App 和小工具都跟著變；
 * 午夜換頁的鬧鐘還是看真的時間。
 */
object AppClock {
    private const val PREFS = "debug"
    private const val KEY_FAKE_TODAY = "fakeToday"

    fun today(context: Context): LocalDate = fakeToday(context) ?: LocalDate.now()

    fun fakeToday(context: Context): LocalDate? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAKE_TODAY, null)?.let(LocalDate::parse)

    fun setFakeToday(context: Context, date: LocalDate?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .apply { if (date == null) remove(KEY_FAKE_TODAY) else putString(KEY_FAKE_TODAY, date.toString()) }
            .apply()
    }
}
