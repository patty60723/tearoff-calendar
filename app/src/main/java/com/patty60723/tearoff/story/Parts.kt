package com.patty60723.tearoff.story

/** 故事解鎖的部位與配件。名稱跟故事檔裡的 `unlock` 一樣（P7-1 從舊火柴人的套件搬過來；墨丸用 [com.patty60723.tearoff.mowan.Mowan.partsOf] 換算）。 */
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

    /** 除錯用：全部長齊、配件全戴 */
    val everything = setOf(LINE, HEAD, EYES, ARM_R, ARM_L, LEG_L, LEG_R, VOICE, RED_TOES) + accessories
}
