package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * W4：包袱 B1。黃金數值由 docs/prototypes/tools/export-golden-bundle.js 從定稿參考 weather.js 匯出，
 * 只包含包袱自己的筆畫（布包、背帶、皺褶、布角、結、碎花）。
 */
class BundleTest {
    // 單元測試的工作目錄是 app/
    private val golden = JSONObject(File("src/test/resources/mowan/golden-bundle.json").readText())
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))
    private val tol = 0.011
    private val alphaTol = 0.0006

    private val weathers = mapOf(
        "normal" to Mowan.Weather.NONE, "sunny" to Mowan.Weather.SUNNY, "rainy" to Mowan.Weather.RAINY,
        "hot" to Mowan.Weather.HOT, "cloudy" to Mowan.Weather.CLOUDY, "fog" to Mowan.Weather.FOG,
        "thunder" to Mowan.Weather.THUNDER, "cold" to Mowan.Weather.COLD, "snow" to Mowan.Weather.SNOW,
    )

    private fun bundleOps(f: Mowan.Frame) = f.ops.filter { it.part == "bundle" }

    @Test
    fun bundleMatchesReference() {
        val frames = golden.getJSONArray("frames")
        assertEquals(19, frames.length())
        for (i in 0 until frames.length()) {
            val g = frames.getJSONObject(i)
            val t = g.getDouble("t")
            val label = "${g.getString("weather")} ${t.toInt()}ms"
            val frame = Mowan.frame(
                parts(14), g.getInt("variant"), true, weathers.getValue(g.getString("weather")), t, Mowan.rainStepAt(t.toLong()),
                sinceStrikeMs = if (g.isNull("sinceStrike")) null else g.getDouble("sinceStrike"),
            )
            val mine = bundleOps(frame)
            val ops = g.getJSONArray("ops")
            assertEquals("$label 包袱的筆畫數", ops.length(), mine.size)
            for (k in 0 until ops.length()) {
                val o = ops.getJSONObject(k)
                val op = mine[k]
                val at = "$label 包袱第 $k 筆"
                assertEquals("$at 圖層", Mowan.Layer.BUNDLE, op.layer)
                assertEquals("$at 填色", o.getString("fill").uppercase(), "#%06X".format(op.fill!! and 0xFFFFFF))
                assertEquals("$at fill-rule", o.optString("fill-rule") == "evenodd", op.evenOdd)
                assertEquals("$at 不透明度", o.optDouble("opacity", 1.0), op.alpha.toDouble(), alphaTol)
                SvgGolden.assertPathEquals(at, SvgGolden.parse(o.getString("d")), op.path.commands, tol)
            }
        }
    }

    @Test
    fun onlyFromPageFourteenAndOnlyWhenWorn() {
        for (page in 1..13) for (w in Mowan.Weather.entries) {
            assertTrue("第 $page 頁 $w 不該有包袱", bundleOps(Mowan.frame(parts(page), 0, true, w, 1200.0, 9)).isEmpty())
        }
        for (w in Mowan.Weather.entries) for (t in listOf(0.0, 650.0, 1500.0, 2300.0, 3300.0)) {
            assertEquals("第 14 頁 $w ${t.toInt()}ms 背著", 14, bundleOps(Mowan.frame(parts(14), 0, true, w, t, 9)).size)
            assertTrue("第 14 頁 $w 卸下", bundleOps(Mowan.frame(parts(14), 0, true, w, t, 9, bundleOn = false)).isEmpty())
        }
        // 打雷驚嚇、關閉動畫的靜止畫面也不會讓包袱消失
        assertEquals(14, bundleOps(Mowan.frame(parts(14), 0, true, Mowan.Weather.THUNDER, 5150.0, 9, sinceStrikeMs = 150.0)).size)
        assertEquals(14, bundleOps(Mowan.frame(parts(14), 0, true, Mowan.Weather.SNOW, 2000.0, 0, still = true)).size)
    }

    @Test
    fun layersAreBehindTheCharacter() {
        // 包袱在地面之後、傘柄與身體之前；背帶藏在圍巾下面；紙帽、雨傘、頭都畫在包袱前面
        val order = Mowan.Layer.entries
        assertTrue(order.indexOf(Mowan.Layer.GROUND) < order.indexOf(Mowan.Layer.BUNDLE))
        for (front in listOf(Mowan.Layer.SHAFT, Mowan.Layer.BODY, Mowan.Layer.SCARF, Mowan.Layer.HEAD, Mowan.Layer.CANOPY, Mowan.Layer.FRONT)) {
            assertTrue("$front 要在包袱前面", order.indexOf(Mowan.Layer.BUNDLE) < order.indexOf(front))
        }
        val f = Mowan.frame(parts(14), 0, true, Mowan.Weather.RAINY, 1050.0, 8)
        val index = { part: String -> f.ops.indexOfFirst { it.part == part } }
        val lastBundle = f.ops.indexOfLast { it.part == "bundle" }
        for (part in listOf("umbrella", "body", "armL", "scarf", "head", "paperHat", "flick")) {
            assertTrue("$part 要畫在包袱之後", index(part) > lastBundle)
        }
        assertTrue("包袱在地面之後", f.ops.indexOfFirst { it.part == "bundle" } > index("ground"))
    }

    @Test
    fun bundleFollowsTheShoulder() {
        // 一般站姿不動；伸懶腰時左手舉高，包袱被提起（布結往上）
        fun knotY(w: Mowan.Weather, t: Double) =
            Mowan.frame(parts(14), 0, true, w, t).ops.filter { it.part == "bundle" }[9].path.commands
                .filterIsInstance<PathData.Cmd.LineTo>().map { it.y }.average()
        val normal = knotY(Mowan.Weather.NONE, 0.0)
        val stretch = knotY(Mowan.Weather.SUNNY, 2750.0)
        assertTrue("伸懶腰時包袱要被提起（$normal → $stretch）", stretch < normal - 1.0)
    }
}
