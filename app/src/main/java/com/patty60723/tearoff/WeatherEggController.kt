package com.patty60723.tearoff

import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import com.patty60723.tearoff.mowan.Capabilities
import com.patty60723.tearoff.mowan.MowanView
import com.patty60723.tearoff.mowan.Pose
import com.patty60723.tearoff.mowan.PoseBlend
import com.patty60723.tearoff.mowan.WeatherEgg
import com.patty60723.tearoff.mowan.WeatherEggSession

/**
 * 天氣接力彩蛋的播放控制（P7-9b）：把 [WeatherEggSession]（狀態）、[WeatherEggOverlay]（小墨丸與素材）、
 * 故事卡的 [MowanView]（大墨丸）接在一起。
 *
 * - 開始：藏起日曆頁的靜態墨丸、疊層開始畫、大墨丸改由分鏡決定姿勢（從此刻的日常姿勢接手）。
 * - 每 [FRAME_MS] 毫秒一格（[step]）：先檢查要不要中斷，再決定這一格小墨丸是畫動畫還是換回靜態圖
 *   （兩件事在同一格切換，所以不會同時出現兩隻，也不會有一格空白）。
 * - 結束（播完或取消）：疊層清空、靜態墨丸顯示回來、大墨丸交還給時間軸、停止排程。取消時大墨丸可以用
 *   [BLEND_OUT_MS] 毫秒從當下的姿勢接回日常，不突然跳。
 *
 * 會自己中斷的情況（每一格檢查）：節日、成長故事、Reduced Motion、大墨丸的天氣換了、日曆頁被換掉。
 * 其他中斷（撕頁、換頁、離開 App）由 [MainActivity] 呼叫 [cancel]。
 */
internal class WeatherEggController(private val overlay: WeatherEggOverlay, private val big: MowanView) {
    /** 現在時間（測試可以換掉；要跟大墨丸的 [MowanView.clock] 一樣） */
    internal var clock: () -> Long = { SystemClock.uptimeMillis() }

    val session = WeatherEggSession()

    /** 除錯版的紀錄（模擬器驗收讀 logcat）：開始、每半秒、結束與原因。正式版是 null */
    internal var trace: ((String) -> Unit)? = null

    /** 最近一次結束的原因（測試用）：done、peel、page、pause、festival、story、reduced、weather、detached */
    internal var lastEnd: String? = null
        private set

    private var frames = 0

    private var still: ImageView? = null

    /** 現在有沒有排著下一格（測試用：結束後不能還在跑） */
    internal var ticking = false
        private set

    private val tick = Runnable {
        ticking = false
        step()
    }

    val playing: Boolean get() = session.script != null

    /**
     * 開始播。[still] 是日曆頁上的靜態墨丸、[sun] 是天氣資訊那幾行。
     * 能不能播由呼叫的人先用 [WeatherEggSession.decide] 判斷；這裡只擋「正在播、剛播完、靜態圖不能照著畫」。
     */
    fun start(script: WeatherEgg.Script, still: ImageView, sun: View, look: WeatherEggOverlay.Look, viewport: View): Boolean {
        if (!overlay.canPlay(still)) return false
        val now = clock()
        if (!session.start(script, now)) return false
        this.still = still
        val caps = Capabilities.of(look.parts)
        overlay.begin(WeatherEggOverlay.Playback(script, still, sun, big, look, viewport))
        big.egg = { daily, at -> session.t(at)?.let { script.big(it, caps, daily) } }
        frames = 0
        trace?.invoke("start weather=${script.weather}")
        step()
        return true
    }

    /** 這次播放被打斷了沒 */
    private fun interrupted(script: WeatherEgg.Script): String? = when {
        big.festival != null -> "festival"
        big.storyPlaying -> "story"
        big.reducedMotion() -> "reduced"
        big.weather != script.weather -> "weather"
        still?.isAttachedToWindow != true -> "detached"
        else -> null
    }

    /** 一格：中斷檢查、切換小墨丸、排下一格 */
    internal fun step() {
        val script = session.script ?: return
        val now = clock()
        val t = session.t(now)
        if (t == null) {
            finish(now, blendBig = false, reason = "done")
            return
        }
        interrupted(script)?.let {
            finish(now, blendBig = false, reason = it)
            return
        }
        val smallOn = t < script.smallEndMs + WeatherEggOverlay.FADE_MS
        still?.visibility = if (smallOn) View.INVISIBLE else View.VISIBLE
        overlay.frame(t, smallOn)
        if (frames++ % 15 == 0) trace?.invoke("frame t=${t.toLong()} small=${if (smallOn) "anim" else "still"}")
        overlay.removeCallbacks(tick)
        ticking = overlay.postDelayed(tick, FRAME_MS)
    }

    /**
     * 取消（沒有在播時什麼都不做）。
     * @param blendBig 大墨丸要不要用一小段時間從現在的姿勢接回日常（撕頁時用；離開 App、換天氣時直接回日常）
     */
    fun cancel(blendBig: Boolean = false, reason: String = "cancel") {
        if (session.script == null) return
        finish(clock(), blendBig, reason)
    }

    private fun finish(now: Long, blendBig: Boolean, reason: String) {
        val script = session.script
        val t = session.t(now)
        overlay.removeCallbacks(tick)
        ticking = false
        overlay.end()
        still?.visibility = View.VISIBLE
        still = null
        val hook = big.egg
        big.egg = null
        if (blendBig && script != null && t != null && hook != null && !interrupted0()) {
            // 從被打斷那一刻的姿勢接回日常；到時間自己交還（回傳 null）
            var from: Pose? = null
            big.egg = { daily, at ->
                val u = (at - now).toDouble() / BLEND_OUT_MS
                if (u >= 1) null
                else {
                    val start = from ?: (script.big(t, Capabilities.of(big.parts), daily)).also { from = it }
                    WeatherEgg.poseLerp(start, daily, PoseBlend.ease(u))
                }
            }
        }
        session.end(now)
        lastEnd = reason
        trace?.invoke("end reason=$reason t=${t?.toLong() ?: -1}")
    }

    /** 大墨丸現在還能不能演過渡（節日、成長故事、Reduced Motion 時直接回日常） */
    private fun interrupted0() = big.festival != null || big.storyPlaying || big.reducedMotion()

    companion object {
        /** 一格多久（跟 [MowanView.POSE_FRAME_MS] 一樣，約 30 FPS；筆觸仍是 6 FPS） */
        const val FRAME_MS = 33L

        /** 取消時大墨丸接回日常的時間 */
        const val BLEND_OUT_MS = 300L
    }
}
