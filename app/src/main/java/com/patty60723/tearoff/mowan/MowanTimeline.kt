package com.patty60723.tearoff.mowan

import kotlin.random.Random

/**
 * 墨丸的時間軸（W5）：一個天氣一條時鐘，所有東西（姿勢、筆觸、包袱、圍巾、表情、環境）都從同一個時間取樣。
 * 不依賴 Android：現在時間由呼叫的人傳進來，亂數可以換掉，所以測試可以完全控制。
 *
 * - 換天氣：時鐘從 0 開始（伸懶腰從站直開始），打雷排程也重來。換頁、換紙帽或包袱不影響時鐘。
 * - 同一個天氣一直播：時鐘一直往前走，各天氣的姿勢本身是循環的（見 [Poses]），不會跳回起點。
 * - 打雷是獨立事件：隨機間隔，跟撐傘晃動的週期無關。
 * - Reduced Motion：停在代表格、筆觸固定第一張、不打雷。
 *
 * @param random 打雷間隔用的亂數
 * @param higherPriorityPlaying 現在有沒有比「天氣日常」更優先的演出（成長故事、節日演出）。
 *   有的話，輪到的那次打雷整個跳過、不補播（整合規格 §5.4）。
 */
class MowanTimeline(
    private val random: Random = Random.Default,
    private val higherPriorityPlaying: () -> Boolean = { false },
) {
    /**
     * 今天能不能打雷。節日當天整天不打雷（D-31），不只演出的時候；雨與雷雨雲照常。
     * 不放在建構子：既有的呼叫 `MowanTimeline(random) { … }` 的尾端 lambda 要一直是 [higherPriorityPlaying]。
     */
    var thunderAllowed: () -> Boolean = { true }

    /** 某一刻要畫的東西 */
    data class Sample(
        /** 姿勢與環境的時間 */
        val clockMs: Double,
        /** 筆觸影格 */
        val variant: Int,
        /** 雨絲的位置 */
        val rainStep: Int,
        /** 打雷後經過的毫秒數；null 表示現在沒有在打雷 */
        val sinceStrikeMs: Double?,
        /** 關閉動畫時的靜止畫面 */
        val still: Boolean,
        /** 天氣姿勢的時間（P7-2）：平常跟 [clockMs] 一樣；點擊重播後從這一輪的起點重新算，環境仍照 [clockMs] */
        val poseClockMs: Double = clockMs,
        /** 點擊後的過渡（D-48）：從這個時間的姿勢過渡到 [poseClockMs]；null 表示沒有在過渡 */
        val blendFromPoseMs: Double? = null,
        /** 過渡的進度 0–1 */
        val blendU: Double = 1.0,
    )

    var weather: Mowan.Weather = Mowan.Weather.NONE
        private set

    private var startMs = 0L
    private var started = false
    /** 下一次打雷的時間（只有雷雨才有） */
    private var nextStrikeMs: Long? = null
    /** 最近一次真的打了的雷 */
    private var lastStrikeMs: Long? = null
    /** 點擊重播（D-48）：點的時間、那一刻的姿勢時間、過渡多久；沒有重播是 null */
    private var replayAtMs: Long? = null
    private var replayFromPoseMs = 0.0
    private var replayBlendMs = 0L

    /** 換天氣。跟現在一樣就什麼都不做（天氣資料更新不會讓姿勢重來）。 */
    fun setWeather(weather: Mowan.Weather, nowMs: Long) {
        if (started && weather == this.weather) return
        this.weather = weather
        startMs = nowMs
        started = true
        lastStrikeMs = null
        replayAtMs = null
        nextStrikeMs = if (weather == Mowan.Weather.THUNDER) nowMs + nextInterval() else null
    }

    /** 在 [nowMs] 這一刻要畫什麼；[reducedMotion] 是系統的「移除動畫」設定 */
    fun sample(nowMs: Long, reducedMotion: Boolean): Sample {
        if (!started) setWeather(weather, nowMs)
        val elapsed = (nowMs - startMs).coerceAtLeast(0)
        advanceThunder(nowMs, reducedMotion)
        if (reducedMotion) return Sample(stillClock(weather), 0, 0, null, still = true)
        val since = lastStrikeMs?.let { (nowMs - it).toDouble() }?.takeIf { it < STRIKE_MS }
        val sample = Sample(elapsed.toDouble(), Mowan.variantAt(elapsed), Mowan.rainStepAt(elapsed), since, still = false)
        val at = replayAtMs ?: return sample
        val t = (nowMs - at).coerceAtLeast(0)
        return if (t < replayBlendMs) {
            // 過渡中：原本的動作照樣往前走，一邊往這一輪的起點（中性姿勢）靠過去
            sample.copy(poseClockMs = 0.0, blendFromPoseMs = replayFromPoseMs + t, blendU = t.toDouble() / replayBlendMs)
        } else {
            sample.copy(poseClockMs = (t - replayBlendMs).toDouble())
        }
    }

    /** 現在的姿勢時間（還沒點過就是 [sample] 的 clockMs） */
    fun poseClockAt(nowMs: Long): Double {
        val s = sample(nowMs, reducedMotion = false)
        return s.blendFromPoseMs ?: s.poseClockMs
    }

    /**
     * 點故事卡上的墨丸（P7-2，D-48）：天氣不變，姿勢從這一輪的起點重新播；
     * 用 [blendMs] 從目前的姿勢過渡過去（0 表示已經在起點附近，直接開始）。環境（雲、雨、霧）與打雷排程不受影響，也不會多打一次雷。
     */
    fun replay(nowMs: Long, blendMs: Long) {
        if (!started) setWeather(weather, nowMs)
        replayFromPoseMs = poseClockAt(nowMs)
        replayAtMs = nowMs
        replayBlendMs = blendMs.coerceAtLeast(0)
    }

    /** 點擊後的過渡與重播的那一輪還沒播完（D-48：這段時間再點只延長台詞，不重啟動作） */
    fun replaying(nowMs: Long): Boolean {
        val at = replayAtMs ?: return false
        return nowMs < at + replayBlendMs + Mowan.weatherCycleMs(weather).toLong()
    }

    /**
     * 打雷排程往前推到 [nowMs]。錯過的（例如 App 在背景、正在播更高優先的演出、節日當天、或關閉動畫）一律跳過，不補播；
     * 只有「剛好輪到、而且還在驚嚇時間內」的那一次會真的打。
     */
    private fun advanceThunder(nowMs: Long, reducedMotion: Boolean) {
        while (true) {
            val at = nextStrikeMs ?: return
            if (nowMs < at) return
            if (!reducedMotion && nowMs - at < STRIKE_MS && !higherPriorityPlaying() && thunderAllowed()) lastStrikeMs = at
            nextStrikeMs = at + nextInterval()
        }
    }

    private fun nextInterval(): Long = random.nextLong(THUNDER_MIN_MS, THUNDER_MAX_MS + 1)

    companion object {
        /**
         * 打雷間隔：隨機 15–40 秒（D-19，已確認）。
         * 改這兩個數字就好，排程邏輯不用動。
         */
        const val THUNDER_MIN_MS = 15_000L
        const val THUNDER_MAX_MS = 40_000L

        /** 一次打雷從閃電出現到驚嚇完全結束（跟 Poses.thunder 的包絡一致：閃電 0–320 ms、驚嚇 20–800 ms） */
        const val STRIKE_MS = 800L

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
            /** 平常抱著傘；不打雷，所以沒有閃電、不驚嚇 */
            Mowan.Weather.THUNDER -> 0.0
            /** 兩陣發抖之間，沒有白氣 */
            Mowan.Weather.COLD -> 2300.0
            /** 正抬頭看雪 */
            Mowan.Weather.SNOW -> 2000.0
            Mowan.Weather.NONE -> 0.0
        }
    }
}
