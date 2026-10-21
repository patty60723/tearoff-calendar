package com.patty60723.tearoff.story

/** 把農民曆上約 150 個宜忌詞分成幾大類，故事依類別挑插曲。 */
object YiJiCategory {
    private val GROUPS = mapOf(
        "出門" to "出行 移徙 乘船 渡水 赴任",
        "會友" to "會親友 會友 嫁娶 訂盟 訂婚 納采 問名 納婿 歸寧 冠笄 進人口",
        "祈福" to "祭祀 祈福 求嗣 開光 齋醮 齊醮 酬神 焚香 謝土 祀灶 塑繪 出火",
        "動工" to "動土 修造 上樑 豎柱 入宅 安門 安床 安香 起基 起基動土 蓋屋 蓋屋合脊 造屋 拆卸 " +
            "作灶 造倉 造倉庫 修門 掘井 開池 開井開池 開渠 造橋 築堤 平治道塗 架馬 定磉 作樑 合脊 " +
            "修飾垣牆 補垣 破屋 壞垣 破屋壞垣 伐木 伐木做樑 開柱眼 穿屏扇架 開廁 作廁 塞穴 造畜稠 造車器 安機械 安機",
        "生意" to "開市 交易 立券 立券交易 納財 求財 開倉 掛匾 置產 買車 出貨財 僱傭",
        "打理" to "沐浴 理髮 剃頭 整手足甲 掃舍 解除 求醫 治病 求醫療病 針灸 探病",
        "種植" to "栽種 納畜 牧養 取漁 結網 畋獵 捕捉 教牛馬 割蜜 安碓磑",
        "學習" to "入學 習藝 裁衣 作染 經絡 醞釀 鼓鑄 造船 雕刻",
        "處理舊事" to "安葬 入殮 破土 修墳 啟攢 立碑 成服 除服 移柩 開生墳 合壽木 行喪 普渡",
        "諸事不宜" to "諸事不宜",
    )

    private val byTerm: Map<String, String> = buildMap {
        for ((category, terms) in GROUPS) for (term in terms.split(" ")) put(term, category)
    }

    fun of(term: String): String? = byTerm[term]
}
