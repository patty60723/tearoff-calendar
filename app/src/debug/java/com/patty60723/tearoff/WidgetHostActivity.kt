package com.patty60723.tearoff

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ViewFlipper

/**
 * 模擬器驗收用（P5-4，只在 debug APK）：用系統的 AppWidgetHost 掛 4 個小工具，大小照 4 種版面，
 * 走跟桌面 launcher 一樣的流程（系統綁定 → 廣播更新 → RemoteViews 跨 process 套用）。
 * 要先 `adb shell appwidget grantbind --package com.patty60723.tearoff`。
 * 每個小工具套用好之後把狀態寫到 logcat（tag W7HOST），驗收腳本讀這些來判斷。
 * 每收到一次小工具更新（RemoteViews）也記一筆 `update id=…`：量「靜止」時用來分辨一次性的內容更新（例如背景抓到新天氣）與持續重畫。
 */
class WidgetHostActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private val views = mutableListOf<Pair<String, AppWidgetHostView>>()

    /** (名稱, 寬 dp, 高 dp)：4 種版面的最小大小再大一點，外加一個介於中間的大小 */
    private val sizes = listOf(
        Triple("COMPACT", 115f, 115f), Triple("WIDE", 290f, 125f),
        Triple("MEDIUM", 150f, 195f), Triple("TALL", 175f, 290f), Triple("BETWEEN", 200f, 200f),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = object : AppWidgetHost(applicationContext, HOST_ID) {
            override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
                object : AppWidgetHostView(context) {
                    override fun updateAppWidget(remoteViews: RemoteViews?) {
                        super.updateAppWidget(remoteViews)
                        Log.i(TAG, "update id=$appWidgetId")
                    }
                }
        }
        host.deleteHost() // 上一次留下來的小工具清掉
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, CalendarWidget::class.java)
        val info = manager.installedProviders.first { it.provider == provider }
        val d = resources.displayMetrics.density
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        for ((name, w, h) in sizes) {
            val id = host.allocateAppWidgetId()
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h.toInt())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(w, h)))
                }
            }
            val bound = manager.bindAppWidgetIdIfAllowed(id, provider, options)
            Log.i(TAG, "bind layout=$name id=$id bound=$bound")
            val view = host.createView(applicationContext, id, info)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            column.addView(TextView(this).apply { text = "$name ${w.toInt()}×${h.toInt()} dp" })
            column.addView(view, LinearLayout.LayoutParams((w * d).toInt(), (h * d).toInt()).apply { bottomMargin = (12 * d).toInt() })
            views += name to view
        }
        setContentView(ScrollView(this).apply { addView(column) })
        // 綁定時系統會送更新；保險起見再更新一次，然後回報套用的結果
        CalendarWidget.refreshAll(this)
        column.postDelayed({ report() }, 4000)
    }

    override fun onStart() {
        super.onStart()
        host.startListening()
    }

    override fun onStop() {
        host.stopListening()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CalendarWidget.refreshAll(this)
        window.decorView.postDelayed({ report() }, 3000)
    }

    /** 每個小工具：有沒有套用成功、用哪一種畫法、ViewFlipper 的狀態、內容有沒有超出小工具 */
    private fun report() {
        for ((name, v) in views) {
            val content = (0 until v.childCount).map { v.getChildAt(it) }.firstOrNull()
            val day = v.findViewById<TextView>(R.id.day)
            val flipper = v.findViewById<ViewFlipper>(R.id.stickman)
            val still = v.findViewById<ImageView>(R.id.mowan_still)
            val ok = day != null && content != null
            fun vis(x: View?) = when (x?.visibility) { View.VISIBLE -> "VISIBLE"; View.GONE -> "GONE"; View.INVISIBLE -> "INVISIBLE"; else -> "none" }
            // 內容裡任何看得到的 View 超出小工具的範圍就算截斷
            var clipped = 0
            fun walk(x: View, ox: Int, oy: Int) {
                if (x.visibility != View.VISIBLE) return
                val l = ox + x.left
                val t = oy + x.top
                if (x !== content && (l < 0 || t < 0 || l + x.width > v.width + 1 || t + x.height > v.height + 1)) clipped++
                if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i), l - x.scrollX, t - x.scrollY)
            }
            if (content != null) walk(content, 0, 0)
            // 角色（V3 的 mowan_still 或舊火柴人的 ViewFlipper）整個在小工具裡面
            fun inside(x: View?): Boolean {
                if (x == null || x.visibility != View.VISIBLE) return false
                val loc = IntArray(2).also { x.getLocationInWindow(it) }
                val box = IntArray(2).also { v.getLocationInWindow(it) }
                val l = loc[0] - box[0]
                val t = loc[1] - box[1]
                return x.width > 0 && x.height > 0 && l >= 0 && t >= 0 && l + x.width <= v.width + 1 && t + x.height <= v.height + 1
            }
            val character = if (still?.visibility == View.VISIBLE) still else flipper
            Log.i(
                TAG,
                "report layout=$name ok=$ok size=${v.width}x${v.height} flipper=${vis(flipper)} flipping=${flipper?.isFlipping} " +
                    "interval=${flipper?.flipInterval} still=${vis(still)} stillImage=${still?.drawable != null} characterInside=${inside(character)} clipped=$clipped",
            )
        }
    }

    private companion object {
        const val TAG = "W7HOST"
        const val HOST_ID = 4242
    }
}
