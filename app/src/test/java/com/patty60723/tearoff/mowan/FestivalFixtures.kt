package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import java.io.File

/** 節日測試共用：第幾頁解鎖了哪些部位（跟 App 一樣從故事 JSON 算，不另外抄一份頁碼表） */
internal object FestivalFixtures {
    private val beats by lazy {
        File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
            .sorted().flatMap { StoryParser.parse(it.readText()) }
    }

    fun parts(page: Int): Set<String> = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    val PAGES = 1..14
    val WEATHERS = Mowan.Weather.entries
    val FESTIVALS: List<Festival?> = Festival.entries + listOf(null)

    class Combo(val page: Int, val festival: Festival?, val weather: Mowan.Weather, val hatOn: Boolean, val bundleOn: Boolean, val headwear: Boolean) {
        val parts get() = parts(page)
        fun resolve() = FestivalScene.resolve(parts, festival, weather, hatOn, bundleOn, ArtConfig(festivalHeadwear = headwear))
        override fun toString() = "第 $page 頁／${festival ?: "無節日"}／$weather／紙帽${if (hatOn) "戴" else "脫"}／包袱${if (bundleOn) "背" else "卸"}${if (headwear) "" else "／節日頭飾關"}"
    }

    /** 4（3 個節日 + 沒有節日）× 9 種天氣 × 14 頁 × 紙帽 × 包袱 × 節日頭飾開關 = 4032 種 */
    val ALL: List<Combo> by lazy {
        val out = ArrayList<Combo>()
        for (page in PAGES) for (f in FESTIVALS) for (w in WEATHERS) for (hat in listOf(true, false)) for (bundle in listOf(true, false)) for (hw in listOf(true, false))
            out += Combo(page, f, w, hat, bundle, hw)
        out
    }

    fun scene(page: Int = 14, festival: Festival? = Festival.SPRING, weather: Mowan.Weather = Mowan.Weather.SUNNY, hatOn: Boolean = true, bundleOn: Boolean = true) =
        FestivalScene.resolve(parts(page), festival, weather, hatOn, bundleOn)
}
