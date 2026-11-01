package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.Mowan.Weather

/**
 * 節日 × 天氣 × 成長階段 × 裝備狀態：今天每個東西放在哪裡、為什麼放或不放（P6-1）。
 * 照原型 core.js 的 `HOLIDAYS`、`OBJECTS`、`resolve()` 移植，天氣從原型的 3 種擴充到 8 種。
 * 純函式，不畫圖；畫法在 P6-2。
 *
 * 已確認的規則（整合規格 §3.4、決策 D-05、D-31、D-32）：
 * - 天氣環境一直都在。
 * - 雨傘是天氣的功能道具，只拿在右手；要有右手才拿得了（雨、雷雨）。
 * - 節日手持道具只放在已解鎖、空著的手上；同一隻手不拿兩樣。沒有空手就不出現（不放地上）。
 * - 節日頭飾要有頭；成長焦點期（還沒有手）不穿戴節日服飾、不拿道具，節日環境只留一個線索並降低對比。
 * - 節日頭飾優先於紙帽：紙帽暫時不畫，**裝備狀態不變**（D-05）。
 * - 雨、雷雨、雪看不到月亮；陰、霧的月亮降低對比；天空有月亮時晴、熱的太陽不畫（D-32）。
 * - 節日當天用節日站姿與演出，不播天氣日常動作；撐傘照常；雷雨的隨機驚嚇暫時不播（D-31）。
 */
object FestivalScene {
    enum class Hand { L, R }

    enum class Slot { HEAD_TOP, HAND_R, HAND_L, GROUND_R, BACKDROP_L, SKY }

    /** 原型的物件分類：天氣環境、節日環境、角色道具、服裝、個人配件 */
    enum class Kind { WEATHER_ENV, WEATHER_PROP, FESTIVAL_ENV, PROP, COSTUME, PERSONAL }

    enum class Item(val kind: Kind, val label: String, val slot: Slot? = null) {
        SUN(Kind.WEATHER_ENV, "太陽", Slot.SKY),
        HEAT(Kind.WEATHER_ENV, "熱氣"),
        CLOUDS(Kind.WEATHER_ENV, "雲"),
        STORM_CLOUDS(Kind.WEATHER_ENV, "雷雨雲"),
        RAIN(Kind.WEATHER_ENV, "雨"),
        MIST(Kind.WEATHER_ENV, "霧"),
        SNOW(Kind.WEATHER_ENV, "雪"),
        UMBRELLA(Kind.WEATHER_PROP, "雨傘"),
        COUPLET(Kind.FESTIVAL_ENV, "斗方春聯", Slot.BACKDROP_L),
        SOCK(Kind.FESTIVAL_ENV, "襪子", Slot.BACKDROP_L),
        GIFT(Kind.FESTIVAL_ENV, "禮物", Slot.GROUND_R),
        MOON(Kind.FESTIVAL_ENV, "月亮", Slot.SKY),
        ENVELOPE(Kind.PROP, "紅包"),
        MOONCAKE(Kind.PROP, "月餅"),
        CAISHEN(Kind.COSTUME, "財神帽", Slot.HEAD_TOP),
        SANTA(Kind.COSTUME, "聖誕帽", Slot.HEAD_TOP),
        EARS(Kind.COSTUME, "玉兔耳朵", Slot.HEAD_TOP),
        PAPER_HAT(Kind.PERSONAL, "紙帽", Slot.HEAD_TOP),
        BUNDLE(Kind.PERSONAL, "包袱"),
    }

    /** 每個節日用哪些東西、演什麼（原型 `HOLIDAYS`） */
    class Spec(
        val costume: Item,
        val prop: Item?,
        /** 道具先放哪一隻手 */
        val propHand: Hand,
        val env: List<Item>,
        /** 成長焦點期唯一保留的環境線索 */
        val cue: Item,
        val main: FestivalActions.Action,
        val idle: FestivalActions.Action,
    )

    fun spec(festival: Festival): Spec = when (festival) {
        Festival.SPRING -> Spec(Item.CAISHEN, Item.ENVELOPE, Hand.R, listOf(Item.COUPLET), Item.COUPLET, FestivalActions.Action.BOW, FestivalActions.Action.LOOK_ENVELOPE)
        Festival.XMAS -> Spec(Item.SANTA, null, Hand.R, listOf(Item.SOCK, Item.GIFT), Item.SOCK, FestivalActions.Action.SOCK, FestivalActions.Action.PEEK_GIFT)
        Festival.MOON -> Spec(Item.EARS, Item.MOONCAKE, Hand.L, listOf(Item.MOON), Item.MOON, FestivalActions.Action.TOAST, FestivalActions.Action.GAZE_MOON)
    }

    /**
     * 一個物件的結果。
     * @param dim 降低對比（成長焦點期的線索、陰天與霧裡的月亮、成長焦點期的太陽）；實際的透明度是美術，P6-2 決定
     */
    data class Shown(val item: Item, val shown: Boolean, val reason: String, val dim: Boolean = false)

    class Result(
        val festival: Festival?,
        val weather: Weather,
        val caps: Capabilities,
        val slots: Map<Slot, Item>,
        val objects: List<Shown>,
    ) {
        operator fun get(slot: Slot): Item? = slots[slot]
        fun shown(item: Item): Boolean = objects.any { it.item == item && it.shown }
        fun of(item: Item): Shown? = objects.firstOrNull { it.item == item }
        fun hand(h: Hand): Item? = slots[if (h == Hand.R) Slot.HAND_R else Slot.HAND_L]

        /** 哪一隻手拿著 [item] */
        fun handOf(item: Item): Hand? = Hand.entries.firstOrNull { hand(it) == item }

        /** 成長焦點期：節日環境線索降低對比 */
        val low: Boolean get() = caps.growthFocus

        /** D-31：節日當天不播天氣日常動作（伸懶腰、擦汗、發抖、探頭等），角色用節日站姿 */
        val weatherDaily: Boolean get() = festival == null

        /** D-31：節日當天雷雨的隨機驚嚇暫時不播（雨、雷雨雲照常） */
        val thunderStartle: Boolean get() = festival == null && weather == Weather.THUNDER
    }

    private fun Capabilities.hold(h: Hand) = if (h == Hand.R) holdR else holdL
    private fun handSlot(h: Hand) = if (h == Hand.R) Slot.HAND_R else Slot.HAND_L
    private fun side(h: Hand) = if (h == Hand.R) "右" else "左"

    /** 天氣本身的環境（不管有沒有節日都在；太陽另外處理，因為會跟月亮搶天空） */
    private fun weatherEnv(weather: Weather): List<Item> = when (weather) {
        Weather.NONE, Weather.SUNNY, Weather.COLD -> emptyList()
        Weather.HOT -> listOf(Item.HEAT)
        Weather.RAINY -> listOf(Item.RAIN)
        Weather.CLOUDY -> listOf(Item.CLOUDS)
        Weather.FOG -> listOf(Item.MIST)
        Weather.THUNDER -> listOf(Item.STORM_CLOUDS, Item.RAIN)
        Weather.SNOW -> listOf(Item.SNOW)
    }

    /** 看不到月亮的天氣（D-32） */
    private val NO_MOON = setOf(Weather.RAINY, Weather.THUNDER, Weather.SNOW)

    /** 月亮降低對比的天氣（D-32） */
    private val DIM_MOON = setOf(Weather.CLOUDY, Weather.FOG)

    /** 有太陽的天氣 */
    private val SUN = setOf(Weather.SUNNY, Weather.HOT)

    /** 要撐傘的天氣 */
    val UMBRELLA_WEATHER = setOf(Weather.RAINY, Weather.THUNDER)

    /**
     * @param parts 已解鎖的部位（[Mowan.partsOf]）
     * @param hatOn 紙帽的裝備狀態（只讀，不會被改）
     * @param bundleOn 包袱的裝備狀態
     */
    fun resolve(
        parts: Set<String>,
        festival: Festival?,
        weather: Weather,
        hatOn: Boolean,
        bundleOn: Boolean,
        art: ArtConfig = ArtConfig.DEFAULT,
    ): Result {
        val caps = Capabilities.of(parts)
        val slots = LinkedHashMap<Slot, Item>()
        val objects = ArrayList<Shown>()
        fun add(item: Item, shown: Boolean, reason: String, dim: Boolean = false) {
            objects += Shown(item, shown, reason, dim)
        }
        val spec = festival?.let(::spec)

        // (1) 天氣：環境一直都在；雨傘要有右手
        for (item in weatherEnv(weather)) add(item, true, "天氣環境")
        if (weather in UMBRELLA_WEATHER) {
            if (caps.holdR) {
                slots[Slot.HAND_R] = Item.UMBRELLA
                add(Item.UMBRELLA, true, "下雨，右手撐傘（天氣功能道具）")
            } else add(Item.UMBRELLA, false, "還沒有右手，不撐傘")
        }

        // (2) 節日手持道具：只在有空著的手時出現，不改放地上
        if (spec?.prop != null) {
            val prop = spec.prop
            if (caps.growthFocus) add(prop, false, "還沒有手：不出現看起來屬於墨丸的道具")
            else {
                val order = if (spec.propHand == Hand.R) listOf(Hand.R, Hand.L) else listOf(Hand.L, Hand.R)
                val h = order.firstOrNull { caps.hold(it) && handSlot(it) !in slots }
                if (h != null) {
                    slots[handSlot(h)] = prop
                    val first = order[0]
                    val why = if (h == first) "拿在${side(h)}手"
                    else "改拿在${side(h)}手：${side(first)}手${if (slots[handSlot(first)] == Item.UMBRELLA) "在撐傘" else "還沒長出來"}"
                    add(prop, true, why)
                } else add(prop, false, "沒有空著的手（不改放地上，避免像掉落的東西）")
            }
        }

        // (3) 節日環境：成長焦點期只留一個主線索，而且降低對比
        if (spec != null) for (item in spec.env) {
            if (item == Item.MOON && weather in NO_MOON) {
                add(item, false, "${label(weather)}看不到月亮"); continue
            }
            if (caps.growthFocus && item != spec.cue) {
                add(item, false, "成長焦點期：只保留一個環境線索"); continue
            }
            slots[item.slot!!] = item
            val dimMoon = item == Item.MOON && weather in DIM_MOON
            val why = when {
                caps.growthFocus -> "成長焦點期唯一的環境線索，降低對比"
                dimMoon -> "${label(weather)}：月亮降低對比"
                else -> "節日環境"
            }
            add(item, true, why, dim = caps.growthFocus || dimMoon)
        }

        // (4) 天氣的太陽：天空有月亮時不畫
        if (weather in SUN) {
            if (slots[Slot.SKY] == Item.MOON) add(Item.SUN, false, "天空已有月亮（節日主題優先）")
            else {
                slots[Slot.SKY] = Item.SUN
                add(Item.SUN, true, if (caps.growthFocus) "${label(weather)}；成長焦點期降低對比" else label(weather), dim = caps.growthFocus)
            }
        }

        // (5) 節日頭飾：要有頭；成長焦點期不穿戴
        if (spec != null) {
            val c = spec.costume
            when {
                !caps.head -> add(c, false, "還沒有頭")
                caps.growthFocus -> add(c, false, "成長焦點期：焦點留給剛長出來的部位")
                !art.festivalHeadwear -> add(c, false, "節日頭飾開關已關閉")
                else -> {
                    slots[Slot.HEAD_TOP] = c
                    add(c, true, "戴在頭上")
                }
            }
        }

        // (6) 紙帽（個人配件）：節日頭飾優先，紙帽暫時不畫，裝備狀態不變（D-05）
        when {
            !caps.paperHat -> add(Item.PAPER_HAT, false, "第 11 頁才取得")
            !hatOn -> add(Item.PAPER_HAT, false, "使用者已換下")
            slots[Slot.HEAD_TOP] != null -> add(Item.PAPER_HAT, false, "頭頂今天給${slots[Slot.HEAD_TOP]!!.label}，紙帽暫收（裝備狀態不變）")
            else -> {
                slots[Slot.HEAD_TOP] = Item.PAPER_HAT
                add(Item.PAPER_HAT, true, "個人配件，已裝備")
            }
        }

        // (7) 包袱（個人配件）：節日不改
        when {
            "bundle" !in parts -> add(Item.BUNDLE, false, "第 14 頁才取得")
            !bundleOn -> add(Item.BUNDLE, false, "使用者已卸下")
            else -> add(Item.BUNDLE, true, "個人配件，已背著")
        }

        return Result(festival, weather, caps, slots, objects)
    }

    fun label(weather: Weather) = when (weather) {
        Weather.NONE -> "一般"
        Weather.SUNNY -> "晴天"
        Weather.RAINY -> "雨天"
        Weather.HOT -> "熱天"
        Weather.CLOUDY -> "陰天"
        Weather.FOG -> "霧天"
        Weather.THUNDER -> "雷雨"
        Weather.COLD -> "冷天"
        Weather.SNOW -> "雪天"
    }
}
