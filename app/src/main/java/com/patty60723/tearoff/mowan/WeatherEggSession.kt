package com.patty60723.tearoff.mowan

/**
 * 天氣接力彩蛋（P7-9b）的播放狀態與規則。不依賴 Android：現在時間由呼叫的人傳進來，測試可以完全控制。
 *
 * 狀態只有三個：
 * - [State.IDLE]：沒有在播，可以開始。
 * - [State.PLAYING]：正在播；再點忽略（不重來、不疊加）。
 * - [State.COOLDOWN]：剛結束（播完或被取消）[COOLDOWN_MS] 毫秒內；再點忽略，避免連點變成一直重播。
 */
internal class WeatherEggSession {
    enum class State { IDLE, PLAYING, COOLDOWN }

    /** 點天氣資訊會怎樣 */
    enum class Outcome {
        /** 播接力彩蛋 */
        PLAY,
        /** 不播動作，只在故事卡用固定高度換一句天氣的話（節日、Reduced Motion、還沒有彩蛋的天氣、墨丸還沒長齊） */
        TEXT_ONLY,
        /** 完全不反應 */
        IGNORED,
    }

    /** 點下去那一刻的情況 */
    data class Conditions(
        /** 正在看今天那頁，而且有天氣資料（日曆上印著天氣） */
        val weatherShown: Boolean,
        val weather: Mowan.Weather,
        val parts: Set<String>,
        /** 成長故事事件正在演 */
        val storyPlaying: Boolean,
        /** 今天是節日 */
        val festival: Boolean,
        val reducedMotion: Boolean,
        /** 正在撕頁（手指拉著或動畫中） */
        val tearing: Boolean,
    )

    var script: WeatherEgg.Script? = null
        private set
    private var startMs = 0L
    private var endedMs: Long? = null

    fun state(nowMs: Long): State {
        val s = script
        val ended = if (s != null) {
            if (nowMs - startMs < s.durMs) return State.PLAYING
            // 時間到了但還沒有人呼叫 end：一樣從播完那一刻起算
            startMs + s.durMs.toLong()
        } else {
            endedMs ?: return State.IDLE
        }
        return if (nowMs - ended < COOLDOWN_MS) State.COOLDOWN else State.IDLE
    }

    /** 播到第幾毫秒；沒有在播是 null */
    fun t(nowMs: Long): Double? {
        val s = script ?: return null
        val t = (nowMs - startMs).toDouble()
        return if (t < s.durMs) t.coerceAtLeast(0.0) else null
    }

    /**
     * 現在點天氣資訊會怎樣（不改狀態）。順序就是優先順序：
     * 沒有天氣、正在撕頁、成長故事（最優先，完全不打斷）、正在播或剛播完 → 不反應；
     * 節日、Reduced Motion、還沒有彩蛋的天氣、墨丸還沒長齊 → 只換一句話；其他 → 播。
     */
    fun decide(c: Conditions, nowMs: Long): Outcome = when {
        !c.weatherShown || c.weather == Mowan.Weather.NONE -> Outcome.IGNORED
        c.tearing -> Outcome.IGNORED
        c.storyPlaying -> Outcome.IGNORED
        state(nowMs) != State.IDLE -> Outcome.IGNORED
        c.festival || c.reducedMotion -> Outcome.TEXT_ONLY
        WeatherEgg.of(c.weather) == null -> Outcome.TEXT_ONLY
        // 分鏡用到雙手、雙腳、表情：還沒長齊（故事前幾頁）時不播
        Capabilities.of(c.parts).let { it.stage != Capabilities.Stage.FULL || !it.gaze || !it.smile } -> Outcome.TEXT_ONLY
        else -> Outcome.PLAY
    }

    /** 開始播。正在播或剛結束時回傳 false、什麼都不改。 */
    fun start(script: WeatherEgg.Script, nowMs: Long): Boolean {
        if (state(nowMs) != State.IDLE) return false
        this.script = script
        startMs = nowMs
        endedMs = null
        return true
    }

    /** 播完或取消：回到沒有在播；從 [nowMs] 起算 [COOLDOWN_MS] 內不能再開始。沒有在播時什麼都不做。 */
    fun end(nowMs: Long) {
        val s = script ?: return
        // 自然播完的話，從播完那一刻起算
        endedMs = minOf(nowMs, startMs + s.durMs.toLong())
        script = null
    }

    companion object {
        /** 結束後多久內不重播 */
        const val COOLDOWN_MS = 1000L
    }
}
