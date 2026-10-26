package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.FestivalScene.Kind
import com.patty60723.tearoff.mowan.FestivalScene.Slot
import com.patty60723.tearoff.mowan.Mowan.Weather
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * P6-1 規則表：給審查看的，不是比對用的測試。寫到 build/screenshots/p6/festival-rules.md（CI 推到截圖分支）。
 */
class FestivalRulesReport {
    private val name = mapOf(Festival.SPRING to "春節", Festival.XMAS to "聖誕", Festival.MOON to "中秋")
    private fun cell(item: Item?) = item?.label ?: "—"
    private fun level(a: FestivalActions.Action, s: FestivalScene.Result) = FestivalActions.levelFor(a, s).label.substringBefore("（")

    private fun row(s: FestivalScene.Result, f: Festival): String {
        val spec = FestivalScene.spec(f)
        val dim = s.objects.filter { it.shown && it.dim }.joinToString("、") { it.item.label }.ifEmpty { "—" }
        val hidden = s.objects.filter { !it.shown && it.item.kind != Kind.WEATHER_ENV && it.item != Item.BUNDLE }
            .joinToString("；") { "${it.item.label}：${it.reason}" }.ifEmpty { "—" }
        return "| ${cell(s[Slot.HEAD_TOP])} | ${cell(s.hand(Hand.R))} | ${cell(s.hand(Hand.L))} | ${cell(s[Slot.BACKDROP_L])} | ${cell(s[Slot.GROUND_R])} | ${cell(s[Slot.SKY])} | $dim | ${level(spec.main, s)} | ${level(spec.idle, s)} | $hidden |"
    }

    private val header = "| 頭頂 | 右手 | 左手 | 背景 | 地面 | 天空 | 降低對比 | 主演出 | 待機 | 不顯示的東西與原因 |\n|---|---|---|---|---|---|---|---|---|---|"

    @Test
    fun write() {
        val out = StringBuilder()
        out.appendLine("# P6-1 節日規則表\n")
        out.appendLine("由 `FestivalRulesReport` 從 `FestivalTheme`、`FestivalScene`、`FestivalActions` 產生。紙帽戴著、包袱背著、節日頭飾開著。\n")

        out.appendLine("## 1. 日期（D-28）\n")
        out.appendLine("| 年 | 春節（初一到初三） | 聖誕 | 中秋 |\n|---|---|---|---|")
        for (y in 2025..2030) {
            val days = generateSequence(LocalDate.of(y, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == y }.groupBy { FestivalTheme.of(it) }
            fun list(f: Festival) = days[f].orEmpty().joinToString("、") { "${it.monthValue}/${it.dayOfMonth}" }
            out.appendLine("| $y | ${list(Festival.SPRING)} | ${list(Festival.XMAS)} | ${list(Festival.MOON)} |")
        }

        out.appendLine("\n## 2. 第 14 頁 × 8 種天氣（D-31、D-32）\n")
        for (f in Festival.entries) {
            out.appendLine("### ${name[f]}\n")
            out.appendLine("| 天氣 $header".replaceFirst("|---|", "|---|---|"))
            for (w in Weather.entries) out.appendLine("| ${FestivalScene.label(w)} ${row(FestivalFixtures.scene(14, f, w), f)}")
            out.appendLine()
        }

        out.appendLine("## 3. 成長階段 × 晴、雨、雷雨（能力剛解鎖的邊界）\n")
        out.appendLine("第 1 頁身體、2 頭、3 眼睛、4 右手、5 左手、6 第一隻腳、7 雙腳、11 紙帽、14 包袱。\n")
        for (f in Festival.entries) {
            out.appendLine("### ${name[f]}\n")
            out.appendLine("| 頁 | 天氣 $header".replaceFirst("|---|", "|---|---|---|"))
            for (page in listOf(1, 2, 3, 4, 5, 6, 7, 11, 14)) for (w in listOf(Weather.SUNNY, Weather.RAINY, Weather.THUNDER))
                out.appendLine("| $page | ${FestivalScene.label(w)} ${row(FestivalFixtures.scene(page, f, w), f)}")
            out.appendLine()
        }

        out.appendLine("## 4. 紙帽（D-05）\n")
        out.appendLine("| 情況 | 紙帽 | 原因 |\n|---|---|---|")
        for ((label, s) in listOf(
            "第 14 頁、沒有節日、戴著" to FestivalFixtures.scene(14, null),
            "第 14 頁、春節、戴著" to FestivalFixtures.scene(14, Festival.SPRING),
            "第 14 頁、春節、脫下" to FestivalFixtures.scene(14, Festival.SPRING, hatOn = false),
            "第 14 頁、春節、節日頭飾關閉" to FestivalScene.resolve(FestivalFixtures.parts(14), Festival.SPRING, Weather.SUNNY, true, true, ArtConfig(festivalHeadwear = false)),
            "第 3 頁、春節（成長焦點期）" to FestivalFixtures.scene(3, Festival.SPRING),
        )) {
            val o = s.of(Item.PAPER_HAT)!!
            out.appendLine("| $label | ${if (o.shown) "顯示" else "不顯示"} | ${o.reason} |")
        }

        File("build/screenshots/p6").apply { mkdirs() }.resolve("festival-rules.md").writeText(out.toString())
    }
}
