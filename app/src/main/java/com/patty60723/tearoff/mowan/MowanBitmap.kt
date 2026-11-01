package com.patty60723.tearoff.mowan

import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * 小工具與 App 日曆頁用的 V3 墨丸（P5-3）：永遠一張靜態代表姿勢（D-04），跟 App 關閉動畫時的畫面同一張；
 * 不播天氣動作、不打雷、不播撕頁後的故事動作（D-27）。
 * 角色視覺高度低於 72 dp 用簡化版（D-25，見 [Detail.of]）。背景透明，疊在頁面上。
 * 節日（P6-4b）：傳 [Festival] 時畫節日的靜態外觀（站姿、服裝、道具、背景），不演出。
 */
object MowanBitmap {
    /** 畫了幾張（測試用：確認一次小工具更新沒有重複畫） */
    @Volatile
    internal var renderCount = 0
        private set

    /**
     * @param widthPx、heightPx 圖的大小；角色會等比例放進去（viewBox 120 × 160）
     * @param density 螢幕密度（1 = mdpi），用來算視覺高度
     * @param festival 節日的靜態外觀；null 是一般的樣子（呼叫的人決定哪一頁畫，D-41）
     * @param art 美術候選（節日頭飾開關），見 [MowanSettings.art]
     */
    fun still(
        parts: Set<String>, hatOn: Boolean, bundleOn: Boolean, weather: Mowan.Weather,
        widthPx: Int, heightPx: Int, density: Float,
        festival: Festival? = null, art: ArtConfig = ArtConfig.DEFAULT,
    ): Bitmap {
        renderCount++
        val drawnHeight = minOf(widthPx / VIEW_W, heightPx / VIEW_H) * VIEW_H
        val frame = Mowan.frame(
            parts, 0, hatOn, weather, MowanTimeline.stillClock(weather),
            still = true, bundleOn = bundleOn, detail = Detail.of(drawnHeight, density.toDouble()),
            festival = festival, art = art,
        )
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
            MowanPainter(frame).draw(Canvas(it), 0f, 0f, widthPx.toFloat(), heightPx.toFloat())
        }
    }

    private const val VIEW_W = 120.0
    private const val VIEW_H = 160.0
}
