package com.patty60723.tearoff

/**
 * 把農民曆函式庫給的簡體字轉成繁體。
 * 函式庫的詞彙是固定的（宜忌、節氣、生肖、方位），所以用對照表就夠了。
 */
object Hant {
    /** 要整個詞一起換的（一個字有好幾種繁體寫法時）。 */
    private val PHRASES = mapOf(
        "理发" to "理髮",
        "启钻" to "啟攢",
        "雇佣" to "僱傭",
        "上梁" to "上樑",
        "作梁" to "作樑",
        "伐木做梁" to "伐木做樑",
        "馀事勿取" to "餘事勿取",
        "平治道涂" to "平治道塗",
        "谷雨" to "穀雨",
    )

    private val CHARS: Map<Char, Char> = buildMap {
        val pairs = "开開绘繪齐齊斋齋庙廟谢謝订訂纳納问問归歸帐帳进進坟墳殓殮门門动動竖豎坏壞补補盖蓋厕廁" +
            "仓倉桥橋筑築扫掃饰飾墙牆马馬挂掛财財买買车車产產货貨机機经經络絡酝醞酿釀铸鑄种種渔漁" +
            "结結网網养養习習艺藝学學发發见見贵貴针針头頭猎獵会會亲親医醫词詞讼訟库庫诸諸丧喪断斷蚁蟻" +
            "无無寿壽疗療惊驚蛰蟄满滿处處龙龍鸡雞猪豬东東闰閏腊臘节節气氣宁寧"
        for (i in pairs.indices step 2) put(pairs[i], pairs[i + 1])
    }

    fun convert(text: String): String {
        PHRASES[text]?.let { return it }
        return buildString(text.length) {
            for (c in text) append(CHARS[c] ?: c)
        }
    }
}
