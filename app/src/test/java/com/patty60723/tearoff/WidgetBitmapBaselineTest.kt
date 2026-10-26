package com.patty60723.tearoff

import android.os.Parcel
import android.util.SizeF
import android.widget.RemoteViews
import com.patty60723.tearoff.story.StoryBook
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * P5-1：小工具換成 V3 之前，先量現在的舊火柴人小工具一次更新送多少 bitmap。
 * 結果寫到 app/build/screenshots/p5/bitmap-baseline.md（CI 會推到 screenshots 分支）。這是量測，不是預算檢查。
 *
 * 三種數字分開記：
 * - 像素估算：寬 × 高 × 4 bytes（ARGB_8888）× 張數，用公式算的。
 * - RemoteViews 自己的估算：`RemoteViews.estimateMemoryUsage()`（隱藏 API），AppWidgetManager 用它檢查上限。
 * - Parcel 大小：把 RemoteViews 寫進 Parcel 後的 dataSize。在 Robolectric 裡 bitmap 整個寫進 Parcel；
 *   實機上大的 bitmap 可能改走共享記憶體（blob），所以這一欄不能當成實際的 Binder 傳輸量。
 * 這些都是 Robolectric 裡算的，不是實機量測。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetBitmapBaselineTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun estimate(views: RemoteViews): Long? = runCatching {
        (RemoteViews::class.java.getMethod("estimateMemoryUsage").invoke(views) as Number).toLong()
    }.getOrNull()

    private fun parcelSize(views: RemoteViews): Int? = runCatching {
        val p = Parcel.obtain()
        try {
            views.writeToParcel(p, 0)
            p.dataSize()
        } finally {
            p.recycle()
        }
    }.getOrNull()

    private fun kb(bytes: Number?) = bytes?.let { "%.1f KB".format(it.toDouble() / 1024) } ?: "量不到"

    @Test
    fun measureCurrentWidget() {
        StoryBook(context).jumpTo(14, AppClock.today(context))
        val page = Almanac.page(AppClock.today(context))
        val frames = 6 // 版面裡 ViewFlipper 的 6 張 ImageView（舊火柴人的輪播）
        val out = StringBuilder()
        out.appendLine("# 小工具 bitmap 基準（P5-1，舊火柴人，換 V3 之前）")
        out.appendLine()
        out.appendLine("Robolectric 量測（Android ${android.os.Build.VERSION.SDK_INT}），不是實機。密度最多算到 3x（`CalendarWidget.setStickman`），所以 xxxhdpi 跟 xxhdpi 一樣。")
        out.appendLine()
        out.appendLine("- 像素估算：寬 × 高 × 4 bytes × 張數，公式算的。")
        out.appendLine("- RemoteViews 估算：`RemoteViews.estimateMemoryUsage()`，系統檢查上限時用的就是這個數字。")
        out.appendLine("- Parcel 大小：Robolectric 裡把 RemoteViews 寫進 Parcel 的大小；實機上大 bitmap 可能走共享記憶體，不能當成實際傳輸量。")
        out.appendLine()
        out.appendLine("| 密度 | 版面 | 小人 dp | 每張 px | 張數 | 像素估算 | 其中輪播多出來的（5/6） | RemoteViews 估算 | Parcel 大小 |")
        out.appendLine("|---|---|---|---|---|---|---|---|---|")
        val densities = listOf("mdpi" to 1f, "hdpi" to 1.5f, "xhdpi" to 2f, "xxhdpi" to 3f, "xxxhdpi" to 4f)
        val summary = mutableListOf<String>()
        for ((q, d) in densities) {
            RuntimeEnvironment.setQualifiers("w411dp-h891dp-$q")
            val scale = d.coerceAtMost(3f)
            var pixels = 0L
            val perLayout = PageLayout.entries.associateWith { CalendarWidget.build(context, page, it) }
            for ((layout, views) in perLayout) {
                val w = (layout.stickmanDp * scale).toInt()
                val h = (layout.stickmanDp * 4 / 3 * scale).toInt()
                val bytes = w.toLong() * h * 4 * frames
                pixels += bytes
                out.appendLine(
                    "| $q | ${layout.name} | ${layout.stickmanDp.toInt()} | ${w}×$h | $frames | ${kb(bytes)} | ${kb(bytes * 5 / 6)} | " +
                        "${kb(estimate(views))} | ${kb(parcelSize(views))} |",
                )
            }
            // Android 8–11：只送一種版面（這裡量最大的 TALL）。要在組合之前量：組合後 4 個版面共用同一份 bitmap 快取
            val single = estimate(perLayout.getValue(PageLayout.TALL))
            // Android 12 以上：4 種版面放在同一個 RemoteViews 一次送出
            val all = RemoteViews(perLayout.entries.associate { (l, v) -> SizeF(l.minWidthDp, l.minHeightDp) to v })
            summary += "| $q | ${kb(pixels)} | ${kb(pixels / frames)} | ${kb(estimate(all))} | ${kb(parcelSize(all))} | ${kb(single)}（TALL） |"
            assertTrue(pixels > 0)
        }
        out.appendLine()
        out.appendLine("## 一次更新的總量")
        out.appendLine()
        out.appendLine("Android 12 以上一次送 4 種版面；Android 8–11 只送放得下的那一種（表中最後一欄列最大的 TALL）。")
        out.appendLine()
        out.appendLine("| 密度 | Android 12+ 像素估算（4 版面 × 6 張） | 同上但只送 1 張（靜態） | Android 12+ RemoteViews 估算 | Android 12+ Parcel 大小 | Android 8–11 單一版面 RemoteViews 估算 |")
        out.appendLine("|---|---|---|---|---|---|")
        summary.forEach { out.appendLine(it) }
        out.appendLine()
        out.appendLine("## App 日曆頁（同一個 build，TALL 版面放大）")
        out.appendLine()
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        val metrics = context.resources.displayMetrics
        val pageWidthPx = metrics.widthPixels - (56 * metrics.density).toInt()
        val stickmanPx = (pageWidthPx * 0.17f).toInt()
        val pageViews = CalendarWidget.build(context, page, PageLayout.TALL, stickmanPx / metrics.density)
        val pw = (stickmanPx / metrics.density * metrics.density.coerceAtMost(3f)).toInt()
        val ph = (stickmanPx / metrics.density * 4 / 3 * metrics.density.coerceAtMost(3f)).toInt()
        out.appendLine("xxhdpi、411dp 寬：每張 ${pw}×$ph px × $frames 張 = ${kb(pw.toLong() * ph * 4 * frames)}（像素估算），RemoteViews 估算 ${kb(estimate(pageViews))}。只在 App 裡用，不經過小工具的傳送。")
        out.appendLine()
        out.appendLine("## 參考：系統上限")
        out.appendLine()
        out.appendLine("AOSP `AppWidgetServiceImpl` 把一次更新的 bitmap 上限設成螢幕寬 × 高 × 6 bytes（1.5 倍全螢幕 ARGB）；1080×2400 約 ${kb(1080L * 2400 * 6)}。這是讀原始碼得到的規則，沒有在實機上量。")
        val text = out.toString()
        File("build/screenshots/p5").apply { mkdirs() }.resolve("bitmap-baseline.md").writeText(text)
        println(text)
    }
}
