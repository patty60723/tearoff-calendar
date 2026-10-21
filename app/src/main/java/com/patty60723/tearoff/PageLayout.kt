package com.patty60723.tearoff

/** 小工具的三種版面，依大小自動切換。 */
enum class PageLayout(
    val res: Int,
    /** 小工具至少要這麼大才用這個版面（dp） */
    val minWidthDp: Float,
    val minHeightDp: Float,
    /** 「宜」顯示幾項 */
    val yiCount: Int,
    val stickman: Boolean,
) {
    COMPACT(R.layout.widget_compact, 100f, 100f, 1, false),
    WIDE(R.layout.widget_wide, 220f, 100f, 3, true),
    TALL(R.layout.widget_tall, 130f, 190f, 2, true);

    companion object {
        /** 放得下的版面裡挑面積最大的；Android 12 以上由系統用同樣的規則挑。 */
        fun fitting(widthDp: Float, heightDp: Float): PageLayout =
            entries.filter { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp }
                .maxByOrNull { it.minWidthDp * it.minHeightDp } ?: COMPACT
    }
}
