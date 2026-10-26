package com.patty60723.tearoff.mowan

/**
 * 還沒定案的美術選擇（整合規格 §3.6）。全部是 **Proposed**：預設值先跟原型的「目前建議」一致，
 * 定案前不能寫死在畫的程式裡，一律從這裡讀。V3 本體（頭身、五官、一筆、筆觸）是 Confirmed，不放在這裡。
 */
data class ArtConfig(
    /** D-01：財神帽、聖誕帽的戴法（原型候選 C，未批准） */
    val hat: HatStyle = HatStyle.C,
    /** D-02：玉兔耳朵（沒有傾向，先用原型目前的 A） */
    val ears: EarStyle = EarStyle.A,
    /** D-03：雨傘大小（傾向原尺寸 1.0，未批准；縮小候選是 [UMBRELLA_SMALL]） */
    val umbrellaScale: Double = 1.0,
    /** 節日頭飾開關（只在除錯選單，D-05）：關掉時紙帽照裝備狀態顯示 */
    val festivalHeadwear: Boolean = true,
    /** D-16：小尺寸（簡化版）節日背景與天氣環境的取捨（候選，等審核） */
    val smallEnv: SmallEnv = SmallEnv.FESTIVAL,
) {
    /**
     * 小尺寸最多一個主要背景裝飾。雨傘是功能道具，三種都會畫。
     * - FESTIVAL：有節日背景（春聯／禮物／月亮）時，天氣環境不畫
     * - WEATHER：天氣環境照畫，節日背景不畫
     * - BOTH：兩個都畫（超過一個，只當對照）
     */
    enum class SmallEnv { FESTIVAL, WEATHER, BOTH }

    /** A：右斜戴（原型最早的戴法）；B：上一輪的第二種戴法；C：往頭頂、放正、壓低 */
    enum class HatStyle { A, B, C }

    /** A：兩隻都在一筆右側；B：一筆兩側各一 */
    enum class EarStyle { A, B }

    companion object {
        val DEFAULT = ArtConfig()

        /** 雨傘縮小的候選（原型 −5%） */
        const val UMBRELLA_SMALL = 0.95
    }
}
