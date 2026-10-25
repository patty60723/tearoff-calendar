package com.patty60723.tearoff.mowan

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/**
 * 會動的墨丸 V3。時間怎麼走由 [MowanTimeline] 決定，這裡只負責什麼時候重畫。
 *
 * - 一般站姿只有 line boil：5 張影格的 Path 先算好存起來，每 1/6 秒換一張，中間不重畫。
 * - 天氣姿勢一直在變，每 33 毫秒（約 30 FPS，跟原型一樣）重算一張；筆觸還是 6 FPS。
 * - 換頁、換紙帽或包袱不會讓動作重來；只有換天氣會。
 * - 系統關掉動畫（Reduced Motion）時：固定第一張筆觸影格、停在代表姿勢、不打雷，不排任何重畫。
 * - 看不到的時候（App 在背景、被蓋住、離開畫面）不排重畫；回來時錯過的打雷不補播。
 * - App 停在前景時系統動畫被打開（例如分割畫面裡改設定），Android 13 以上會通知，墨丸馬上恢復動作。
 */
class MowanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 已解鎖的部位，見 [Mowan.partsOf] */
    var parts: Set<String> = emptySet()
        set(value) { if (field != value) { field = value; redraw() } }

    var hatOn: Boolean = true
        set(value) { if (field != value) { field = value; redraw() } }

    /** 包袱是否背著（第 14 頁前不管這個值都不畫） */
    var bundleOn: Boolean = true
        set(value) { if (field != value) { field = value; redraw() } }

    /** 換天氣時姿勢從頭開始（伸懶腰從站直開始）、打雷排程重來 */
    var weather: Mowan.Weather
        get() = timeline.weather
        set(value) {
            if (value == timeline.weather) return
            timeline.setWeather(value, now())
            redraw()
        }

    /** 除錯用：固定某一張筆觸影格，null 表示跟著時間跑 */
    var fixedVariant: Int? = null
        set(value) { field = value; invalidate() }

    /** 現在在演什麼；成長故事、節日演出（P6）會從這裡告訴墨丸 */
    val director = MowanDirector()

    /** 時間軸（測試用可以讀）。有更高優先的演出時，雷雨的驚嚇跳過 */
    internal val timeline = MowanTimeline(higherPriorityPlaying = { director.aboveWeather() })
        .also { it.setWeather(Mowan.Weather.NONE, SystemClock.uptimeMillis()) }

    /** 系統的「移除動畫」設定（測試可以換掉） */
    internal var reducedMotion: () -> Boolean = { !ValueAnimator.areAnimatorsEnabled() }

    private fun now() = SystemClock.uptimeMillis()

    private val boil = arrayOfNulls<MowanPainter>(Mowan.FRAMES)
    private val tick = Runnable { isTicking = false; invalidate() }

    /** 現在有沒有排著下一次重畫（測試用） */
    internal var isTicking = false
        private set

    private fun stop() {
        removeCallbacks(tick)
        isTicking = false
    }

    /** 外觀變了：丟掉快取的筆觸，但時鐘不動 */
    private fun redraw() {
        boil.fill(null)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        stop()
        val sample = timeline.sample(now(), reducedMotion())
        val variant = fixedVariant ?: sample.variant
        val painter = if (weather == Mowan.Weather.NONE) {
            boil[variant] ?: MowanPainter(Mowan.frame(parts, variant, hatOn, bundleOn = bundleOn)).also { boil[variant] = it }
        } else {
            MowanPainter(Mowan.frame(
                parts, variant, hatOn, weather, sample.clockMs, sample.rainStep,
                sinceStrikeMs = sample.sinceStrikeMs, still = sample.still, bundleOn = bundleOn,
            ))
        }
        painter.draw(
            canvas, paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingLeft - paddingRight).toFloat(), (height - paddingTop - paddingBottom).toFloat(),
        )
        if (sample.still || !isShown || windowVisibility != VISIBLE) return
        val delay = if (weather == Mowan.Weather.NONE) {
            // 到下一張筆觸影格才重畫
            (Mowan.BOIL_MS - sample.clockMs % Mowan.BOIL_MS).toLong().coerceAtLeast(1)
        } else {
            POSE_FRAME_MS
        }
        isTicking = postDelayed(tick, delay)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) invalidate() else stop()
    }

    /** 系統動畫設定改變：停住的墨丸要重畫一次才知道可以動了（通知可能來自其他執行緒） */
    private val scaleListener = ValueAnimator.DurationScaleChangeListener { post { invalidate() } }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ValueAnimator.registerDurationScaleChangeListener(scaleListener)
    }

    override fun onDetachedFromWindow() {
        stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ValueAnimator.unregisterDurationScaleChangeListener(scaleListener)
        super.onDetachedFromWindow()
    }

    companion object {
        /** 姿勢動畫的重畫間隔，跟原型一樣約 30 FPS */
        const val POSE_FRAME_MS = 33L

        /** 不能動的時候的代表姿勢，見 [MowanTimeline.stillClock] */
        fun stillClock(weather: Mowan.Weather) = MowanTimeline.stillClock(weather)
    }
}
