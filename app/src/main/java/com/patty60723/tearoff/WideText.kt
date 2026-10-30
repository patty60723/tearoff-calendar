package com.patty60723.tearoff

/**
 * WIDE 小工具右欄的文字（P7-1，2026-10-30 審核：候選 B，依實際空間決定）。
 * 放得下多少放多少：放不下的項目整項拿掉，不出現「…」；空間夠時跟原本一樣（宜 2 項、農曆加干支年、忌 3 項）。
 * 連 1 項宜都放不下（四個字的項目，最小尺寸才會發生）時，只有那一行字縮小，最小 [MIN_PHRASE_SP]。
 * 寬度是連續判斷（沒有單一門檻），變寬時項目只會增加、不會先出現再消失。日期資料本身不變。
 */
object WideText {
    data class Lines(val phrase: String, val lunar: String, val ji: String, val phraseSp: Float = PHRASE_SP)

    /** 宜的字級（widget_wide.xml）與放不下時最小可以縮到多少 */
    const val PHRASE_SP = 15f
    const val MIN_PHRASE_SP = 12f

    /** 宜、忌裡不算項目的字 */
    val FILLER = setOf("餘事勿取", "諸事不宜", "無")

    /** widget_wide.xml：外框右邊 3dp、內距左右各 10dp；右欄佔 1.3 / 2.2，自己左 8dp、右 46dp（留給墨丸） */
    fun rightColumnDp(widthDp: Float) = (widthDp - 3f - 20f) * 1.3f / 2.2f - 8f - 46f

    /** 一行字的寬：中文（全形）一個字一個字級、空白算半個，每個字再加字距；乘上字型縮放，多留 4% 餘裕 */
    fun widthDp(text: String, sp: Float, letterSpacing: Float, fontScale: Float) =
        text.sumOf { (if (it == ' ') 0.5 else 1.0) + letterSpacing } * sp * fontScale * 1.04

    private fun fits(text: String, availDp: Float, sp: Float, letterSpacing: Float, fontScale: Float) =
        widthDp(text, sp, letterSpacing, fontScale) <= availDp

    /** 從 1 項開始加，放得下的最多項（至少 1 項：連 1 項都放不下時照樣顯示，交給 TextView 的「…」） */
    private fun most(prefix: String, items: List<String>, max: Int, fit: (String) -> Boolean): String {
        var best = prefix + items.first()
        for (k in 2..minOf(max, items.size)) {
            val t = prefix + items.take(k).joinToString("・")
            if (fit(t)) best = t else break
        }
        return best
    }

    fun of(page: DayPage, widthDp: Float, fontScale: Float, yiMax: Int = 2, jiMax: Int = 3): Lines {
        val avail = rightColumnDp(widthDp)
        // 字級、字距跟 widget_wide.xml 一樣：宜 15sp／0.15，農曆、忌 10sp／0.05
        val big = { t: String -> fits(t, avail, PHRASE_SP, 0.15f, fontScale) }
        val small = { t: String -> fits(t, avail, 10f, 0.05f, fontScale) }
        val yi = page.yi.filter { it !in FILLER }
        val ji = page.ji.filter { it !in FILLER }
        val full = "農曆${page.lunarDate}・${page.ganZhiYear}"
        val phrase = if (yi.isEmpty()) "諸事不宜" else most("宜 ", yi, yiMax, big)
        // 放得下的最大字級（每次少 0.5sp），寬度變大時字只會變大
        var sp = PHRASE_SP
        while (sp > MIN_PHRASE_SP && !fits(phrase, avail, sp, 0.15f, fontScale)) sp -= 0.5f
        return Lines(
            phrase = phrase,
            lunar = if (small(full)) full else "農曆${page.lunarDate}",
            ji = if (ji.isEmpty()) "" else most("忌 ", ji, jiMax, small),
            phraseSp = sp,
        )
    }
}
