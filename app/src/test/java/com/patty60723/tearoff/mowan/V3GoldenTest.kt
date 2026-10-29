package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.PageLayout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * P7-1：V3 正式幾何 golden（D-51）。故事卡、App 日曆頁、小工具 4 種版面 × 一般日子（晴、雨）與 3 個節日（晴），
 * 每一筆的圖層、顏色、線寬、透明度、路徑都跟 `golden-v3.json` 比。
 *
 * 這份 golden 是 2026-10-29 V3 的輸出（P6 已驗收的美術），之後是回歸基準：清理舊火柴人（P7-1 第二停點）前後必須完全相同。
 * 重新產生（只有美術審核通過的修改才可以）：`V3_GOLDEN_WRITE=1 ./gradlew testDebugUnitTest --tests '*V3GoldenTest'`。
 */
class V3GoldenTest {
    private val file = File("src/test/resources/mowan/golden-v3.json")
    private val tol = 1e-4

    /** 第 14 頁（全部部位）、紙帽戴著、包袱背著：新安裝撕到最後的樣子 */
    private val parts = FestivalFixtures.parts(14)

    data class Condition(val id: String, val weather: Mowan.Weather, val festival: Festival?)

    data class Surface(val id: String, val still: Boolean, val widthDp: Float, val smallEnv: ArtConfig.SmallEnv)

    companion object {
        val conditions = listOf(
            Condition("normal-sunny", Mowan.Weather.SUNNY, null),
            Condition("normal-rainy", Mowan.Weather.RAINY, null),
            Condition("spring", Mowan.Weather.SUNNY, Festival.SPRING),
            Condition("moon", Mowan.Weather.SUNNY, Festival.MOON),
            Condition("xmas", Mowan.Weather.SUNNY, Festival.XMAS),
        )

        /** 跟正式程式一樣的密度上限（`CalendarWidget.setMowan`） */
        const val DENSITY = 3f

        /** 411dp 寬的手機上，App 日曆頁的角色寬度（`MainActivity` 的算法：頁面寬的 17%） */
        val pageDp = ((411 * 3 - 56 * 3) * 0.17f).toInt() / 3f

        /** 小工具與日曆頁是 [MowanBitmap.still] 的靜態圖；故事卡是 `MowanView` 的完整畫法（時間 0、沒有演出） */
        val surfaces = listOf(Surface("story", still = false, widthDp = 0f, smallEnv = ArtConfig.DEFAULT.smallEnv)) +
            Surface("page", still = true, widthDp = pageDp, smallEnv = ArtConfig.SmallEnv.CUE) +
            PageLayout.entries.map {
                Surface(
                    "widget-${it.name.lowercase()}", still = true, widthDp = it.stickmanDp,
                    smallEnv = if (it == PageLayout.COMPACT) ArtConfig.SmallEnv.MINIMAL else ArtConfig.SmallEnv.CUE,
                )
            }
    }

    private fun frame(s: Surface, c: Condition): Mowan.Frame {
        val art = ArtConfig.DEFAULT.copy(smallEnv = s.smallEnv)
        if (!s.still) return Mowan.frame(parts, 0, hatOn = true, weather = c.weather, bundleOn = true, festival = c.festival, art = art)
        // 跟 MowanBitmap.still 同樣的參數
        val w = (s.widthDp * DENSITY).toInt()
        val h = (s.widthDp * 4 / 3 * DENSITY).toInt()
        val drawnHeight = minOf(w / 120.0, h / 160.0) * 160.0
        return Mowan.frame(
            parts, 0, true, c.weather, MowanTimeline.stillClock(c.weather),
            still = true, bundleOn = true, detail = Detail.of(drawnHeight, DENSITY.toDouble()), festival = c.festival, art = art,
        )
    }

    private fun color(c: Int?) = c?.let { "#%08X".format(it) } ?: "none"

    /** 小數 5 位（比對容許 1e-4） */
    private fun n(v: Double) = String.format(Locale.ROOT, "%.5f", v).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

    private fun path(p: PathData) = p.commands.joinToString(" ") {
        when (it) {
            is PathData.Cmd.MoveTo -> "M ${n(it.x)} ${n(it.y)}"
            is PathData.Cmd.LineTo -> "L ${n(it.x)} ${n(it.y)}"
            is PathData.Cmd.QuadTo -> "Q ${n(it.x1)} ${n(it.y1)} ${n(it.x)} ${n(it.y)}"
            is PathData.Cmd.ArcTo -> "A ${n(it.rx)} ${n(it.ry)} 0 ${if (it.largeArc) 1 else 0} ${if (it.sweep) 1 else 0} ${n(it.x)} ${n(it.y)}"
            PathData.Cmd.Close -> "Z"
        }
    }

    private fun transform(t: Mowan.Transform?) = t?.let { JSONArray(listOf(it.dx, it.dy, it.rotDeg, it.pivotX, it.pivotY, it.scale)) } ?: JSONObject.NULL

    private fun export(f: Mowan.Frame) = JSONObject().apply {
        put("head", transform(f.head))
        put("canopy", transform(f.canopy))
        put("ops", JSONArray(f.ops.map { op ->
            JSONObject().apply {
                put("part", op.part)
                put("layer", op.layer.name)
                put("fill", color(op.fill))
                put("alpha", op.alpha.toDouble())
                put("evenOdd", op.evenOdd)
                put("gradient", op.gradient?.let { g ->
                    JSONArray(listOf(g.x1, g.y1, g.x2, g.y2) + g.stops.flatMap { (at, c, a) -> listOf(at.toDouble(), color(c), a.toDouble()) })
                } ?: JSONObject.NULL)
                put("stroke", color(op.stroke))
                put("strokeWidth", op.strokeWidth)
                put("roundCap", op.roundCap)
                put("d", path(op.path))
            }
        }))
    }

    private fun current() = JSONObject().apply {
        put("note", "V3 正式幾何 golden（P7-1，D-51）。第 14 頁、紙帽戴著、包袱背著；密度 $DENSITY；由 V3GoldenTest 產生")
        for (s in surfaces) for (c in conditions) put("${s.id}/${c.id}", export(frame(s, c)))
    }

    @Test
    fun matchesTheOfficialGolden() {
        val now = current()
        if (System.getenv("V3_GOLDEN_WRITE") == "1") file.writeText(now.toString(1) + "\n")
        val golden = JSONObject(file.readText())
        val keys = surfaces.flatMap { s -> conditions.map { c -> "${s.id}/${c.id}" } }
        assertEquals("golden 的組合", keys.toSet() + "note", golden.keySet())
        for (k in keys) compare(k, golden.getJSONObject(k), now.getJSONObject(k))
    }

    private fun compare(at: String, e: JSONObject, a: JSONObject) {
        compareTransform("$at 頭", e.get("head"), a.get("head"))
        compareTransform("$at 傘", e.get("canopy"), a.get("canopy"))
        val eo = e.getJSONArray("ops")
        val ao = a.getJSONArray("ops")
        val list = { ops: JSONArray -> (0 until ops.length()).joinToString { ops.getJSONObject(it).getString("part") } }
        assertEquals("$at 的筆畫（部位順序）", list(eo), list(ao))
        for (i in 0 until eo.length()) {
            val x = eo.getJSONObject(i)
            val y = ao.getJSONObject(i)
            val where = "$at 第 $i 筆（${x.getString("part")}）"
            for (key in listOf("layer", "fill", "evenOdd", "stroke", "roundCap")) assertEquals("$where $key", x.get(key), y.get(key))
            assertEquals("$where 透明度", x.getDouble("alpha"), y.getDouble("alpha"), tol)
            assertEquals("$where 線寬", x.getDouble("strokeWidth"), y.getDouble("strokeWidth"), tol)
            assertEquals("$where 漸層", x.get("gradient").toString(), y.get("gradient").toString())
            SvgGolden.assertPathEquals(where, SvgGolden.parse(x.getString("d")), SvgGolden.parse(y.getString("d")), tol)
        }
    }

    private fun compareTransform(at: String, e: Any, a: Any) {
        if (e == JSONObject.NULL) { assertEquals("$at 不該有", JSONObject.NULL, a); return }
        assertTrue("$at 應該有", a is JSONArray)
        e as JSONArray; a as JSONArray
        for (i in 0 until e.length()) assertEquals("$at[$i]", e.getDouble(i), a.getDouble(i), tol)
    }

    /** 這份 golden 涵蓋的東西本身要有意義：節日有節日的部位、雨天有傘、COMPACT 是極簡 */
    @Test
    fun theGoldenCoversWhatItClaims() {
        val parts = { s: Surface, c: Condition -> frame(s, c).ops.map { it.part }.toSet() }
        for (s in surfaces) {
            assertTrue("${s.id} 雨天有傘", frame(s, conditions[1]).canopy != null)
            assertEquals("${s.id} 晴天沒有傘", null, frame(s, conditions[0]).canopy)
            for (c in conditions.drop(2)) assertTrue("${s.id} ${c.id} 跟平常不一樣", parts(s, c) != parts(s, conditions[0]))
        }
    }
}
