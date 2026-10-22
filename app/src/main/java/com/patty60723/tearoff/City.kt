package com.patty60723.tearoff

import android.content.Context

/** 台灣 22 個縣市，座標取縣市政府所在地附近。 */
data class City(val name: String, val lat: Double, val lon: Double)

object Cities {
    val all = listOf(
        City("基隆市", 25.128, 121.741),
        City("臺北市", 25.033, 121.565),
        City("新北市", 25.012, 121.465),
        City("桃園市", 24.993, 121.301),
        City("新竹市", 24.804, 120.971),
        City("新竹縣", 24.839, 121.004),
        City("苗栗縣", 24.560, 120.821),
        City("臺中市", 24.148, 120.674),
        City("彰化縣", 24.075, 120.544),
        City("南投縣", 23.910, 120.684),
        City("雲林縣", 23.707, 120.431),
        City("嘉義市", 23.480, 120.449),
        City("嘉義縣", 23.459, 120.333),
        City("臺南市", 22.999, 120.227),
        City("高雄市", 22.627, 120.301),
        City("屏東縣", 22.669, 120.488),
        City("宜蘭縣", 24.757, 121.753),
        City("花蓮縣", 23.977, 121.604),
        City("臺東縣", 22.755, 121.144),
        City("澎湖縣", 23.571, 119.579),
        City("金門縣", 24.437, 118.319),
        City("連江縣", 26.160, 119.951),
    )

    val DEFAULT = all.first { it.name == "臺北市" }

    private const val PREFS = "settings"
    private const val KEY_CITY = "city"

    fun selected(context: Context): City {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CITY, null)
        return all.firstOrNull { it.name == name } ?: DEFAULT
    }

    fun select(context: Context, city: City) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CITY, city.name).apply()
    }
}
