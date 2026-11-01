package com.patty60723.tearoff.mowan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * P6-2：節日服裝與道具的靜態畫面跟原型逐筆比對。黃金數值由 docs/prototypes/tools/export-golden-festival.js
 * 從原型 core.js 的 Renderer 匯出（3 個節日 × 第 1、2、3、4、5、8、11、14 頁 × 一般、晴、雨，
 * 加上頭飾 B、C、玉兔耳朵 B、雨傘縮小 0.95、節日頭飾關閉，共 83 張）。
 * 原型的節日邏輯沒有包袱，所以比對「卸下包袱」的畫面。
 */
class FestivalGoldenTest {
    private val golden = JSONObject(File("src/test/resources/mowan/golden-festival.json").readText())
    private val tol = 0.011
    private val festivals = mapOf("spring" to Festival.SPRING, "xmas" to Festival.XMAS, "moon" to Festival.MOON)

    @Test
    fun framesMatchPrototype() {
        val frames = golden.getJSONArray("frames")
        assertEquals(83, frames.length())
        for (f in 0 until frames.length()) {
            val g = frames.getJSONObject(f)
            val page = g.getInt("page")
            val festival = festivals.getValue(g.getString("holiday"))
            val weather = Mowan.Weather.valueOf(g.getString("weather").uppercase())
            val head = g.getString("head")
            val art = ArtConfig(
                hat = if (head == "") ArtConfig.HatStyle.A else ArtConfig.HatStyle.valueOf(head),
                ears = if (head == "B") ArtConfig.EarStyle.B else ArtConfig.EarStyle.A,
                umbrellaScale = g.getDouble("umb"),
                festivalHeadwear = g.getBoolean("headwear"),
            )
            val label = "$festival 第 $page 頁 $weather 頭飾${head.ifEmpty { "A" }} 傘${g.getDouble("umb")}${if (art.festivalHeadwear) "" else " 頭飾關"}"
            val frame = Mowan.frame(
                FestivalFixtures.parts(page), g.getInt("variant"), g.getBoolean("hatOn"), weather, g.getDouble("clock"), g.getInt("rainStep"),
                bundleOn = false, festival = festival, art = art,
            )

            val ht = Regex("""translate\(0 (\S+)\) rotate\((\S+) 60\.5 66\)""").find(g.getString("headTransform"))!!
            assertEquals("$label 頭的位移", ht.groupValues[1].toDouble(), frame.head.dy, tol)
            assertEquals("$label 頭的角度", ht.groupValues[2].toDouble(), frame.head.rotDeg, tol)
            val canopy = g.getString("canopyTransform")
            if (canopy.isEmpty()) assertNull("$label 不該有傘", frame.canopy)
            else {
                val m = Regex("""translate\((\S+) (\S+)\) rotate\((\S+)\) scale\((\S+)\)""").find(canopy)!!
                val t = frame.canopy!!
                assertEquals("$label 傘", m.groupValues[1].toDouble(), t.dx, tol)
                assertEquals("$label 傘", m.groupValues[2].toDouble(), t.dy, tol)
                assertEquals("$label 傘的角度", m.groupValues[3].toDouble(), t.rotDeg, tol)
                assertEquals("$label 傘的大小", m.groupValues[4].toDouble(), t.scale, tol)
            }

            val ops = g.getJSONArray("ops")
            assertEquals("$label 的筆畫數", ops.length(), frame.ops.size)
            for (i in 0 until ops.length()) {
                val o = ops.getJSONObject(i)
                val op = frame.ops[i]
                val at = "$label 第 $i 筆（${op.part}）"
                assertEquals(at, o.getString("layer").uppercase(), op.layer.name)
                assertEquals(at, o.optString("fill-rule") == "evenodd", op.evenOdd)
                assertEquals(at, o.optDouble("opacity", 1.0), op.alpha.toDouble(), 1e-6)
                // 紅墨腳尖的漸層：位置已在 MowanGoldenTest 比過，這裡只確認是漸層
                if (o.getString("fill").startsWith("url(")) assertNotNull(at, op.gradient)
                else assertEquals(at, o.getString("fill").uppercase(), op.fill?.let { "#%06X".format(it and 0xFFFFFF) } ?: "NONE")
                if (o.has("stroke")) {
                    assertEquals(at, o.getString("stroke").uppercase(), op.stroke?.let { "#%06X".format(it and 0xFFFFFF) })
                    assertEquals(at, o.getDouble("stroke-width"), op.strokeWidth, 1e-9)
                } else assertNull(at, op.stroke)
                SvgGolden.assertPathEquals(at, SvgGolden.parse(o.getString("d")), op.path.commands, tol)
            }
        }
    }
}
