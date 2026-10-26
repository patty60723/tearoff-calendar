package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import java.time.LocalDate
import kotlin.random.Random

/**
 * 節日的狀態機（P6-1），照原型 core.js 的 `Controller` + `Session` 移植。只有邏輯：時間、亂數、
 * 「今天演過了沒」的存放位置都由外面給，可以在 JVM 測試；P6-4 才接到 [MowanView] 與 SharedPreferences。
 *
 * 規則（D-33、D-34、D-35、D-36，2026-10-26 確認）：
 * - 主演出：每個「節日日期」最多一次。key 是日期 + 節日（例如春節初一、初二、初三是三個 key）。
 *   **開始播的時候就記下來**，所以中途切到背景、重開 App、天氣更新都不會重播。重播只給除錯選單（[replay]）。
 * - 關閉動畫（Reduced Motion）：停在代表姿勢（[Action.keyMs]），照樣跑完演出時間、照樣記成演過；不排待機。
 * - 演出開始時鎖定降級層級，直到結束（[level]）；天氣環境可以隨時換（[FestivalScene] 每次重算）。
 * - 跨午夜依新的日期判斷：正在播的演出播完，之後才換成新日期（[enter]）。
 * - 沒有節日：不演主演出、不排節日待機。
 * - 優先順序：故事事件 > 主演出 > 節日待機（[MowanDirector]）。故事事件會打斷主演出，結束後回到待機，不重播。
 */
class FestivalSession(
    private val log: MainLog,
    private val random: Random = Random.Default,
    private val idleMinMs: Long = IDLE_MIN_MS,
    private val idleMaxMs: Long = IDLE_MAX_MS,
) {
    /** 「今天演過了沒」存在哪裡（P6-4 用 SharedPreferences；測試用 [MemoryLog]） */
    interface MainLog {
        fun played(key: String): Boolean
        fun markPlayed(key: String)
    }

    class MemoryLog : MainLog {
        val keys = LinkedHashSet<String>()
        override fun played(key: String) = key in keys
        override fun markPlayed(key: String) {
            keys += key
        }
    }

    /** 數字越小越優先（原型 `PRI`） */
    enum class Mode(val activity: MowanDirector.Activity?) {
        STORY(MowanDirector.Activity.GROWTH_STORY),
        MAIN(MowanDirector.Activity.FESTIVAL_MAIN),
        IDLE(MowanDirector.Activity.FESTIVAL_IDLE),
        BASE(null),
    }

    var date: LocalDate? = null
        private set
    var festival: Festival? = null
        private set
    var mode = Mode.BASE
        private set

    /** 正在演的節日動作（故事事件時是 null） */
    var action: Action? = null
        private set

    /** 演出開始時鎖定的降級層級（D-35） */
    var level: Level? = null
        private set
    var startMs = 0L
        private set
    private var durMs = 0L

    /** 關閉動畫：停在代表姿勢 */
    var held = false
        private set
    var nextIdleMs = Long.MAX_VALUE
        private set

    /** 每種模式開始過幾次（測試、除錯用） */
    val starts = mutableMapOf(Mode.STORY to 0, Mode.MAIN to 0, Mode.IDLE to 0)
    val events = ArrayList<String>()

    private var pending: Pair<LocalDate, Festival?>? = null

    /** 演出時間軸上的時間：關閉動畫時固定在代表姿勢 */
    fun actionTime(nowMs: Long): Long? = action?.let { if (held) it.keyMs else nowMs - startMs }

    private fun wait() = random.nextLong(idleMinMs, idleMaxMs + 1)

    private fun note(msg: String) {
        events += msg
    }

    private fun request(mode: Mode, action: Action?, dur: Long, nowMs: Long, scene: FestivalScene.Result?, reduced: Boolean, why: String): Boolean {
        if (this.mode != Mode.BASE && mode > this.mode) {
            note("${mode}被擋下：正在播 ${this.mode}")
            return false
        }
        this.mode = mode
        this.action = action
        this.level = if (action != null && scene != null) FestivalActions.levelFor(action, scene) else null
        this.startMs = nowMs
        this.durMs = dur
        this.held = reduced
        starts[mode] = starts.getValue(mode) + 1
        note(why)
        return true
    }

    private fun toBase(nowMs: Long, reduced: Boolean) {
        mode = Mode.BASE
        action = null
        level = null
        held = false
        nextIdleMs = if (festival != null && !reduced) nowMs + wait() else Long.MAX_VALUE
    }

    /**
     * 故事卡第一次看得到、而且 App 在前景（D-33），或日期換了。
     * 這個日期是節日、而且這個 key 還沒演過，就**先記下來**再開始演主演出。
     * 正在播主演出或故事事件時日期換了：播完才換（D-36）。
     */
    fun enter(date: LocalDate, festival: Festival?, nowMs: Long, scene: FestivalScene.Result?, reduced: Boolean) {
        if (this.date == date && this.festival == festival) {
            // 演出中日期換過去又換回來：之前排的新日期已經過時
            if (pending != null) note("日期換回 $date：取消等待中的 ${pending!!.first}")
            pending = null
            return
        }
        if (this.date != null && (mode == Mode.MAIN || mode == Mode.STORY)) {
            // 演出中多次換日期：只留最新的那一個
            pending = date to festival
            note("日期換成 $date：等目前的演出播完")
            return
        }
        this.date = date
        this.festival = festival
        toBase(nowMs, reduced)
        if (festival == null) {
            note("$date 沒有節日：不演節日演出")
            return
        }
        val k = key(date, festival)
        if (log.played(k)) {
            note("$k 已經演過：直接進入待機")
            return
        }
        log.markPlayed(k)
        request(Mode.MAIN, mainOf(festival), mainOf(festival).durMs, nowMs, scene, reduced, "$k 第一次：演主演出")
    }

    /** 每一幀（或排程到的時間）呼叫：演完回到待機；待機時間到了就演節日待機 */
    fun update(nowMs: Long, scene: FestivalScene.Result?, reduced: Boolean) {
        if (mode != Mode.BASE && nowMs - startMs >= durMs) {
            val ended = mode
            toBase(nowMs, reduced)
            if (ended == Mode.STORY) note("故事事件結束：回到${if (festival != null) "節日待機" else "一般狀態"}（不重播主演出）")
            pending?.let { (d, f) ->
                pending = null
                enter(d, f, nowMs, scene, reduced)
            }
        }
        val f = festival
        if (mode == Mode.BASE && f != null && !reduced && nowMs >= nextIdleMs) {
            val idle = FestivalScene.spec(f).idle
            request(Mode.IDLE, idle, idle.durMs, nowMs, scene, reduced = false, why = "節日待機：${idle.label}")
        }
    }

    /** 天氣更新：只換環境，不重設狀態機、不重播、不改鎖定的層級 */
    fun weatherChanged(from: Mowan.Weather, to: Mowan.Weather) {
        note("天氣 $from → $to：不重播${if (mode != Mode.BASE) "，$mode 繼續" else ""}")
    }

    /** 動畫設定改變：關掉時停掉待機排程；打開時重新排 */
    fun reducedChanged(reduced: Boolean, nowMs: Long) {
        if (mode == Mode.BASE) nextIdleMs = if (festival != null && !reduced) nowMs + wait() else Long.MAX_VALUE
    }

    /** 除錯選單的重播（D-33）：不檢查、也不改「演過了」的紀錄 */
    fun replay(nowMs: Long, scene: FestivalScene.Result?, reduced: Boolean): Boolean {
        val f = festival ?: run { note("沒有節日：忽略重播"); return false }
        if (mode == Mode.STORY) {
            note("故事事件進行中：重播被擋下")
            return false
        }
        mode = Mode.BASE
        val main = mainOf(f)
        return request(Mode.MAIN, main, main.durMs, nowMs, scene, reduced, "除錯重播：${main.label}")
    }

    /** 故事事件（成長故事；V3 目前還沒有故事動作，P6 只接介面） */
    fun story(nowMs: Long, durMs: Long, reduced: Boolean): Boolean =
        request(Mode.STORY, null, durMs, nowMs, null, reduced, "故事事件插入")

    companion object {
        /** 節日待機的間隔（原型：20–40 秒隨機） */
        const val IDLE_MIN_MS = 20_000L
        const val IDLE_MAX_MS = 40_000L

        /** 「今天演過了沒」的 key：日期 + 節日 */
        fun key(date: LocalDate, festival: Festival) = "$date:$festival"

        fun mainOf(festival: Festival) = FestivalScene.spec(festival).main
    }
}
