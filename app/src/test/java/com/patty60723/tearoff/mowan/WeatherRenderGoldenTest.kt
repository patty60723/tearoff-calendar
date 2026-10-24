package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * W3：新天氣的整張畫面跟定稿參考 weather.js 的 drawFrame 逐筆比對。
 * 黃金數值由 docs/prototypes/tools/export-golden-weather-render.js 匯出：
 * 6 種天氣 × 第 1、3、4、5、8、14 頁 × 2–3 個時間點（雪另外有關閉動畫時的靜止畫面）。
 */
class WeatherRenderGoldenTest {
    // 單元測試的工作目錄是 app/
    private val golden = JSONObject(File("src/test/resources/mowan/golden-weather-render.json").readText())
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    /** 座標：原型輸出小數兩位 */
    private val tol = 0.011
    /** 不透明度：原型輸出小數三位 */
    private val alphaTol = 0.0006

    private val numbers = Regex("""-?\d*\.?\d+(?:e-?\d+)?""")
    private fun nums(s: String) = numbers.findAll(s).map { it.value.toDouble() }.toList()

    @Test
    fun newWeathersMatchReference() {
        val frames = golden.getJSONArray("frames")
        assertEquals(96, frames.length())
        for (f in 0 until frames.length()) {
            val g = frames.getJSONObject(f)
            val page = g.getInt("page")
            val weather = Mowan.Weather.valueOf(g.getString("weather").uppercase())
            val t = g.getDouble("t")
            val still = g.getBoolean("still")
            val label = "$weather 第 $page 頁 ${t.toInt()}ms${if (still) "（靜止）" else ""}"
            val frame = Mowan.frame(
                Mowan.partsOf(StoryEngine.unlocked(beats, page)), g.getInt("variant"), true, weather, t, g.getInt("rainStep"),
                sinceStrikeMs = if (g.isNull("sinceStrike")) null else g.getDouble("sinceStrike"), still = still,
            )

            // 頭：translate(dx dy) rotate(a 60.5 66)
            val head = nums(g.getString("headTransform"))
            assertEquals("$label 頭的左右", head[0], frame.head.dx, tol)
            assertEquals("$label 頭的上下", head[1], frame.head.dy, tol)
            assertEquals("$label 頭的角度", head[2], frame.head.rotDeg, tol)
            // 傘面：translate(x y) rotate(a)
            val canopy = g.getString("canopyTransform")
            if (canopy.isEmpty()) {
                assertNull("$label 不該有傘", frame.canopy)
            } else {
                val c = nums(canopy)
                val my = frame.canopy!!
                assertEquals("$label 傘", c[0], my.dx, tol)
                assertEquals("$label 傘", c[1], my.dy, tol)
                assertEquals("$label 傘的角度", c[2], my.rotDeg, tol)
            }

            val ops = g.getJSONArray("ops")
            assertEquals("$label 的筆畫數", ops.length(), frame.ops.size)
            for (i in 0 until ops.length()) {
                val o = ops.getJSONObject(i)
                val op = frame.ops[i]
                val at = "$label 第 $i 筆（${op.part}）"
                val group = when (op.layer) { Mowan.Layer.HEAD -> "head"; Mowan.Layer.CANOPY -> "canopy"; else -> "" }
                assertEquals("$at 圖層", o.getString("group"), group)
                assertEquals("$at fill-rule", o.optString("fill-rule") == "evenodd", op.evenOdd)
                assertEquals("$at 不透明度", o.optDouble("opacity", 1.0), op.alpha.toDouble(), alphaTol)
                if (o.has("gradient")) {
                    val gr = o.getJSONObject("gradient")
                    val my = op.gradient!!
                    assertEquals(at, gr.getDouble("x1"), my.x1, tol)
                    assertEquals(at, gr.getDouble("y1"), my.y1, tol)
                    assertEquals(at, gr.getDouble("x2"), my.x2, tol)
                    assertEquals(at, gr.getDouble("y2"), my.y2, tol)
                } else {
                    assertEquals("$at 填色", o.getString("fill").uppercase(), op.fill?.let { "#%06X".format(it and 0xFFFFFF) } ?: "NONE")
                }
                if (o.has("stroke")) {
                    assertEquals("$at 描邊", o.getString("stroke").uppercase(), op.stroke?.let { "#%06X".format(it and 0xFFFFFF) })
                    assertEquals("$at 描邊寬", o.getDouble("stroke-width"), op.strokeWidth, 1e-9)
                    assertEquals("$at 圓頭", o.optString("stroke-linecap") == "round", op.roundCap)
                } else {
                    assertNull("$at 不該描邊", op.stroke)
                }
                SvgGolden.assertPathEquals(at, SvgGolden.parse(o.getString("d")), op.path.commands, tol)
            }
        }
    }
}
