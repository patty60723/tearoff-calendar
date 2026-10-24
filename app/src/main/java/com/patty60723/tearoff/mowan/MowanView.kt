package com.patty60723.tearoff.mowan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/**
 * 會動的墨丸 V3。
 *
 * - 一般站姿只有 line boil：5 張影格的 Path 先算好存起來，每 1/6 秒換一張，中間不重畫。
 * - 晴天伸懶腰、雨天撐傘：姿勢一直在變，每 33 毫秒（約 30 FPS，跟原型一樣）重算一張；筆觸還是 6 FPS。
 * - 系統關掉動畫（Reduced Motion）時：固定第一張筆觸影格、停在代表姿勢，不排任何重畫。
 * - 看不到的時候（App 在背景、被蓋住、離開畫面）不排重畫。
 */
class MowanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 已解鎖的部位，見 [Mowan.partsOf] */
    var parts: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; reset() } }

    var hatOn: Boolean = true
        set(value) { if (field != value) { field = value; reset() } }

    /** 換天氣時姿勢從頭開始（伸懶腰從站直開始） */
    var weather: Mowan.Weather = Mowan.Weather.NONE
        set(value) { if (field != value) { field = value; reset() } }

    /** 除錯用：固定某一張筆觸影格，null 表示跟著時間跑 */
    var fixedVariant: Int? = null
        set(value) { field = value; invalidate() }

    private var startMs = SystemClock.uptimeMillis()
    private val boil = arrayOfNulls<MowanPainter>(Mowan.FRAMES)
    private val tick = Runnable { isTicking = false; invalidate() }

    /** 現在有沒有排著下一次重畫（測試用） */
    internal var isTicking = false
        private set

    private fun stop() {
        removeCallbacks(tick)
        isTicking = false
    }

    private fun reset() {
        boil.fill(null)
        startMs = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        stop()
        val still = !ValueAnimator.areAnimatorsEnabled()
        val elapsed = SystemClock.uptimeMillis() - startMs
        val variant = fixedVariant ?: if (still) 0 else Mowan.variantAt(elapsed)
        val painter = if (weather == Mowan.Weather.NONE) {
            boil[variant] ?: MowanPainter(Mowan.frame(parts, variant, hatOn)).also { boil[variant] = it }
        } else {
            val clock = if (still) stillClock(weather) else elapsed.toDouble()
            val rain = if (still) 0 else Mowan.rainStepAt(elapsed)
            // 打雷的時間點由排程決定（W5），在那之前雷雨不會嚇一跳
            MowanPainter(Mowan.frame(parts, variant, hatOn, weather, clock, rain, still = still))
        }
        painter.draw(
            canvas, paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingLeft - paddingRight).toFloat(), (height - paddingTop - paddingBottom).toFloat(),
        )
        if (still || !isShown || windowVisibility != VISIBLE) return
        val delay = if (weather == Mowan.Weather.NONE) {
            // 到下一張筆觸影格才重畫
            (Mowan.BOIL_MS - elapsed % Mowan.BOIL_MS).toLong().coerceAtLeast(1)
        } else {
            POSE_FRAME_MS
        }
        isTicking = postDelayed(tick, delay)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate() else stop()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    companion object {
        /** 姿勢動畫的重畫間隔，跟原型一樣約 30 FPS */
        const val POSE_FRAME_MS = 33L

        /** 不能動的時候，用哪一個時間點當代表姿勢（整合規格 §5.4：都選穩定、自然的一格，不停在動作最大的瞬間） */
        fun stillClock(weather: Mowan.Weather) = when (weather) {
            Mowan.Weather.SUNNY -> 2000.0
            Mowan.Weather.RAINY -> 1050.0
            /** 手剛碰到臉頰 */
            Mowan.Weather.HOT -> 650.0
            /** 視線追著雲往右上看 */
            Mowan.Weather.CLOUDY -> 1500.0
            /** 手伸到一半 */
            Mowan.Weather.FOG -> 1250.0
            /** 平常抱著傘；不傳打雷時間，所以沒有閃電、不驚嚇 */
            Mowan.Weather.THUNDER -> 0.0
            /** 兩陣發抖之間，沒有白氣 */
            Mowan.Weather.COLD -> 2300.0
            /** 正抬頭看雪 */
            Mowan.Weather.SNOW -> 2000.0
            Mowan.Weather.NONE -> 0.0
        }
    }
}
