package com.patty60723.tearoff.stickman

/** 故事解鎖的部位與配件。名稱跟故事檔裡的 `unlock` 一樣。 */
object Parts {
    const val LINE = "line"
    const val HEAD = "head"
    const val EYES = "eyes"
    const val ARM_R = "armR"
    const val ARM_L = "armL"
    const val LEG_L = "legL"
    const val LEG_R = "legR"
    const val VOICE = "voice"
    const val RED_TOES = "redToes"
    const val SCARF = "scarf"
    const val PAPER_HAT = "paperHat"
    const val BUNDLE = "bundle"

    val accessories = setOf(SCARF, PAPER_HAT, BUNDLE)
}
