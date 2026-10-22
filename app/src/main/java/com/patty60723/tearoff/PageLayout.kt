package com.patty60723.tearoff

/**
 * 小工具的四種版面，依大小自動切換。越小的版面拿掉越多東西，
 * 但日期和火柴人一定在（最小的版面也有）。
 */
enum class PageLayout(
    val res: Int,
    /** 小工具至少要這麼大才用這個版面（dp） */
    val minWidthDp: Float,
    val minHeightDp: Float,
    /** 「宜」顯示幾項 */
    val yiCount: Int,
    /** 火柴人的寬（dp），跟版面裡 ViewFlipper 一樣 */
    val stickmanDp: Float,
) {
    /** 日期＋火柴人 */
    COMPACT(R.layout.widget_compact, 100f, 100f, 1, 36f),
    /** 日期＋火柴人＋一句宜 */
    MEDIUM(R.layout.widget_medium, 130f, 170f, 2, 44f),
    WIDE(R.layout.widget_wide, 250f, 110f, 2, 42f),
    /** 全部：農曆、忌、節日、日出日落 */
    TALL(R.layout.widget_tall, 150f, 250f, 2, 48f);

    companion object {
        /** 放得下的版面裡挑面積最大的；Android 12 以上由系統用同樣的規則挑。 */
        fun fitting(widthDp: Float, heightDp: Float): PageLayout =
            entries.filter { widthDp >= it.minWidthDp && heightDp >= it.minHeightDp }
                .maxByOrNull { it.minWidthDp * it.minHeightDp } ?: COMPACT
    }
}
