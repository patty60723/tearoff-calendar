package com.patty60723.tearoff.mowan

/**
 * 能力系統：故事解鎖了什麼，墨丸就只能做什麼。照原型 core.js 的 getCapabilities() 移植。
 * 來源只有已解鎖的部位（[Mowan.partsOf]），不另外存一份頁碼表。
 */
data class Capabilities(
    val body: Boolean,
    /** 有頭：可以轉頭、戴頭飾 */
    val head: Boolean,
    /** 有眼睛：可以看、可以閉眼笑 */
    val gaze: Boolean,
    /** 有嘴巴 */
    val smile: Boolean,
    val holdR: Boolean,
    val holdL: Boolean,
    /** 至少一隻腳：站在腳上；沒有腳時身體直接落在地面 */
    val stand: Boolean,
    /** 兩隻腳 */
    val walk: Boolean,
    /** 第 11 頁拿到紙帽（有沒有戴是另一回事，見 [MowanSettings.hatOn]） */
    val paperHat: Boolean,
) {
    val expression get() = gaze
    val costume get() = head
    val twoHands get() = holdR && holdL
    /** 成長焦點期：還沒有任何一隻手，焦點留給剛長出來的部位 */
    val growthFocus get() = !holdR && !holdL

    enum class Stage { LINE, HEAD, ONE_HAND, TWO_HANDS, FULL }

    val stage: Stage
        get() = when {
            !head -> Stage.LINE
            growthFocus -> Stage.HEAD
            !twoHands -> Stage.ONE_HAND
            !walk -> Stage.TWO_HANDS
            else -> Stage.FULL
        }

    companion object {
        fun of(parts: Set<String>) = Capabilities(
            body = "body" in parts,
            head = "head" in parts,
            gaze = "eyes" in parts,
            smile = "mouth" in parts,
            holdR = "armR" in parts,
            holdL = "armL" in parts,
            stand = "legL" in parts || "legR" in parts,
            walk = "legL" in parts && "legR" in parts,
            paperHat = "paperHat" in parts,
        )
    }
}
