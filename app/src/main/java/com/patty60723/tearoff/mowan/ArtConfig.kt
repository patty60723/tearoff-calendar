package com.patty60723.tearoff.mowan

/**
 * 節日美術的候選（整合規格 §3.6）。預設值是 2026-10-26 P6-2 視覺審核確認的選擇（D-01、D-02、D-03）；
 * 其他候選保留給除錯與回歸比較，不刪除。V3 本體（頭身、五官、一筆、筆觸）是 Confirmed，不放在這裡。
 */
data class ArtConfig(
    /** D-01（Confirmed）：財神帽、聖誕帽都用 C；A、B 保留比較 */
    val hat: HatStyle = HatStyle.C,
    /** D-02（Confirmed）：玉兔耳朵用 B（一筆兩側各一）；A 保留比較 */
    val ears: EarStyle = EarStyle.B,
    /** D-03（Confirmed）：雨傘維持原尺寸 1.0；縮小候選 [UMBRELLA_SMALL] 不採用，只保留比較 */
    val umbrellaScale: Double = 1.0,
    /** 節日頭飾開關（只在除錯選單，D-05）：關掉時紙帽照裝備狀態顯示 */
    val festivalHeadwear: Boolean = true,
    /** D-16：小尺寸（簡化版）節日背景與天氣環境的取捨。方向已定（A 為基礎＋最多一個簡化天氣線索），實作在 P6-4 */
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
