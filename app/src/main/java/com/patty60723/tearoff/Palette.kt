package com.patty60723.tearoff

/** 每個月一個主色，像一整排不同顏色的手撕日曆。 */
data class Palette(val name: String, val main: Int, val deep: Int)

object Palettes {
    const val INK = 0xFF2E2A26.toInt()
    const val SOFT = 0xFF8C8378.toInt()
    /** 週末和國定假日的日期 */
    const val RED = 0xFFB8322A.toInt()
    const val PAPER = 0xFFFBF8F2.toInt()
    /** 紙的背面 */
    const val PAPER_BACK = 0xFFF1ECE3.toInt()

    private val MONTHS = listOf(
        Palette("暖陽橘", 0xFFD9822B.toInt(), 0xFFA85C17.toInt()),
        Palette("春節紅", 0xFFB5352B.toInt(), 0xFF8C231B.toInt()),
        Palette("赤陶", 0xFFB4694A.toInt(), 0xFF8A4A30.toInt()),
        Palette("嫩芽綠", 0xFF8DA37A.toInt(), 0xFF5F7650.toInt()),
        Palette("鼠尾草", 0xFF7E9F95.toInt(), 0xFF4F7268.toInt()),
        Palette("霧藍", 0xFF7F9DB8.toInt(), 0xFF4D6E8C.toInt()),
        Palette("海藍", 0xFF4F7FA3.toInt(), 0xFF335C7C.toInt()),
        Palette("沙金", 0xFFC4A06A.toInt(), 0xFF8E7040.toInt()),
        Palette("奶茶", 0xFFB39A82.toInt(), 0xFF7D6550.toInt()),
        Palette("楓糖", 0xFFC0814E.toInt(), 0xFF8C5530.toInt()),
        Palette("薰衣灰", 0xFF9A92AE.toInt(), 0xFF675F7E.toInt()),
        Palette("冬青紅", 0xFF9E3A34.toInt(), 0xFF742520.toInt()),
    )

    fun forMonth(month: Int): Palette = MONTHS[(month - 1).coerceIn(0, 11)]
}
