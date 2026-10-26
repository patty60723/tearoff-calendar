package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.FestivalScene.Slot

/**
 * 節日演出的規格與降級層級（P6-1），照原型 core.js 的 `HOLIDAYS.main／idle`、`ACTION_SPEC`、`levelFor()` 移植。
 * 這裡只決定「演哪一個層級、會動到哪些部位」；每個層級的姿勢在 P6-3。
 *
 * 層級由上往下找第一個能力夠的。能力不夠就換整套動作，不是只把手藏起來；
 * 不用還沒解鎖的部位，同一隻手不拿兩樣（整合規格 §3.4、§3.5，Confirmed）。
 */
object FestivalActions {
    /**
     * @param durMs 演出時間
     * @param keyMs 代表姿勢的時間（關閉動畫時停在這一格，D-34）
     */
    enum class Action(val label: String, val durMs: Long, val keyMs: Long) {
        BOW("拜年：作揖、遞紅包", 4200, 2300),
        LOOK_ENVELOPE("看看紅包", 2200, 900),
        SOCK("掛襪子、看禮物", 4400, 2300),
        PEEK_GIFT("偷看禮物", 2000, 900),
        TOAST("舉月餅望月", 4600, 2900),
        GAZE_MOON("抬頭望月", 2600, 1200),
    }

    enum class Level(val label: String) {
        FULL("完整版"),
        RAIN("雨天版（撐傘拜年）"),
        ONE_HAND("單手版（成長階段只有一隻手）"),
        POINT("指向版（沒有腳，構不到）"),
        HEAD("頭部版"),
        LINE("線條版"),
    }

    /** 能用哪些層級（由上往下找），對應原型 `ACTION_SPEC` */
    val LEVELS: Map<Action, List<Level>> = mapOf(
        Action.BOW to listOf(Level.FULL, Level.RAIN, Level.ONE_HAND, Level.HEAD, Level.LINE),
        Action.LOOK_ENVELOPE to listOf(Level.FULL, Level.HEAD, Level.LINE),
        Action.SOCK to listOf(Level.FULL, Level.POINT, Level.HEAD, Level.LINE),
        Action.PEEK_GIFT to listOf(Level.FULL, Level.HEAD, Level.LINE),
        Action.TOAST to listOf(Level.FULL, Level.HEAD, Level.LINE),
        Action.GAZE_MOON to listOf(Level.FULL, Level.LINE),
    )

    private fun Capabilities.hold(h: Hand) = if (h == Hand.R) holdR else holdL

    /** 已解鎖、手上沒有東西的手（左手優先，跟原型一樣） */
    fun freeHands(scene: FestivalScene.Result): List<Hand> =
        listOf(Hand.L, Hand.R).filter { scene.caps.hold(it) && scene.hand(it) == null }

    fun levelFor(action: Action, scene: FestivalScene.Result): Level {
        val c = scene.caps
        val headOrLine = if (c.head) Level.HEAD else Level.LINE
        return when (action) {
            Action.BOW -> {
                // 「已長出雙手但正在撐傘」跟「成長階段只有一隻手」是兩回事
                val h = scene.handOf(Item.ENVELOPE) ?: return headOrLine
                if (!c.twoHands) return Level.ONE_HAND
                val other = if (h == Hand.R) Hand.L else Hand.R
                if (scene.hand(other) != null) Level.RAIN else Level.FULL
            }
            Action.LOOK_ENVELOPE -> if (scene.handOf(Item.ENVELOPE) != null) Level.FULL else headOrLine
            Action.SOCK -> if (freeHands(scene).isNotEmpty()) (if (c.stand) Level.FULL else Level.POINT) else headOrLine
            Action.PEEK_GIFT -> if (!c.head) Level.LINE else if (scene[Slot.GROUND_R] == Item.GIFT) Level.FULL else Level.HEAD
            Action.TOAST -> if (scene.handOf(Item.MOONCAKE) != null) Level.FULL else headOrLine
            Action.GAZE_MOON -> if (c.head) Level.FULL else Level.LINE
        }
    }

    /** 這個層級會動到的部位（P6-3 的姿勢只能動這些） */
    data class Uses(
        /** 會動的手 */
        val hands: Set<Hand>,
        /** 轉頭、點頭 */
        val head: Boolean,
        /** 雨傘跟著點頭前傾（撐傘的手不動） */
        val umbrellaTilt: Boolean = false,
        /** 會用到腳站著（掛襪子要站得夠高） */
        val stand: Boolean = false,
    )

    fun uses(action: Action, level: Level, scene: FestivalScene.Result): Uses {
        if (level == Level.LINE) return Uses(emptySet(), head = false)
        if (level == Level.HEAD) return Uses(emptySet(), head = true)
        return when (action) {
            Action.BOW -> {
                val h = scene.handOf(Item.ENVELOPE)!!
                when (level) {
                    Level.FULL -> Uses(setOf(Hand.L, Hand.R), head = true)
                    Level.RAIN -> Uses(setOf(h), head = true, umbrellaTilt = true)
                    else -> Uses(setOf(h), head = true)
                }
            }
            Action.LOOK_ENVELOPE -> Uses(setOf(scene.handOf(Item.ENVELOPE)!!), head = true)
            Action.SOCK -> Uses(setOf(freeHands(scene).first()), head = true, stand = level == Level.FULL)
            Action.TOAST -> Uses(setOf(scene.handOf(Item.MOONCAKE)!!), head = true)
            Action.PEEK_GIFT, Action.GAZE_MOON -> Uses(emptySet(), head = true)
        }
    }
}
