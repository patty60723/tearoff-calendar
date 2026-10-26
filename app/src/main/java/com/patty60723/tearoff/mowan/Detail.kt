package com.patty60723.tearoff.mowan

/**
 * 小尺寸的畫法（P5-2，D-07、D-25、D-26）。[FULL] 是原本的畫法，跟原型逐筆一樣。
 *
 * 簡化版不改任何座標、比例或姿勢，只做兩件事：
 * - **拿掉**看不出來的細節：腮紅、汗滴、白氣、眉毛、帽子摺線、包袱碎花與皺褶、背帶、傘骨、地面影子、
 *   多餘的雨絲、雪花、雲；太小時拿掉嘴巴。天氣環境只留一個主要元素（雨傘是角色道具，不算）。
 * - **加粗**留下來的線：主要墨線（頭、一筆、眼睛、身體、四肢、傘柄、圍巾）畫出來至少 [MAIN_INK_PX]，
 *   配件輪廓（紙帽、包袱、傘面、拳頭）與環境至少 [ACCENT_INK_PX]。只把太細的加到下限，本來就夠粗的不動。
 *
 * 要不要簡化看的是角色**畫出來的高度**（px），由 [of] 決定；筆畫下限一定用 px 換算（像素才會讓線消失）。
 *
 * @param heightPx 角色（viewBox 160 單位高）實際畫出來的高度
 */
class Detail private constructor(val simplified: Boolean, val heightPx: Double) {
    /** 1 px 等於幾個 viewBox 單位 */
    private val unitsPerPx get() = VIEW_H / heightPx

    /** 主要墨線的最小寬度（viewBox 單位）；完整版是 0（不加粗） */
    val mainInk: Double get() = if (simplified) MAIN_INK_PX * unitsPerPx else 0.0

    /** 配件輪廓、環境的最小寬度（viewBox 單位） */
    val accentInk: Double get() = if (simplified) ACCENT_INK_PX * unitsPerPx else 0.0

    /** 嘴巴：簡化版在角色高度小於 [MOUTH_MIN_PX] 時不畫（嘴寬不到 3 px，只會是一個污點） */
    val mouth: Boolean get() = !simplified || heightPx >= MOUTH_MIN_PX

    override fun toString() = if (simplified) "簡化（${heightPx.toInt()} px）" else "完整"

    /** 簡化門檻怎麼算（D-25 還沒定案，兩種都可以測） */
    sealed interface Rule {
        /** 畫出來的像素高度低於 [px] 就簡化（D-25 暫定 120 px） */
        data class ByPx(val px: Double) : Rule

        /** 視覺高度（dp）低於 [dp] 就簡化：同一個實際大小在每種密度看到的細節一樣 */
        data class ByDp(val dp: Double) : Rule
    }

    companion object {
        const val VIEW_H = 160.0
        const val MAIN_INK_PX = 1.5
        const val ACCENT_INK_PX = 1.0
        const val MOUTH_MIN_PX = 96.0

        /** D-25 暫定：120 px */
        val DEFAULT_RULE: Rule = Rule.ByPx(120.0)

        /** 完整版：原本的畫法 */
        val FULL = Detail(false, Double.MAX_VALUE)

        /** 強制用簡化版畫成 [heightPx] 高（對照圖、測試用） */
        fun simplified(heightPx: Double) = Detail(true, heightPx)

        /**
         * 角色畫成 [heightPx] 高、螢幕密度 [density]（1 = mdpi）時用哪一種畫法。
         */
        fun of(heightPx: Double, density: Double, rule: Rule = DEFAULT_RULE): Detail {
            val small = when (rule) {
                is Rule.ByPx -> heightPx < rule.px
                is Rule.ByDp -> heightPx / density < rule.dp
            }
            return if (small) Detail(true, heightPx) else FULL
        }
    }
}
