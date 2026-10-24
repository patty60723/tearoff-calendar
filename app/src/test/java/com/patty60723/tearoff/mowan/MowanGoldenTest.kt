package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 跟原型比對：黃金數值由 docs/prototypes/tools/export-golden.js 從原型原始碼匯出。
 * 原型輸出的座標是小數兩位，所以容許 0.011 的誤差。
 */
class MowanGoldenTest {
    // 單元測試的工作目錄是 app/
    private val golden = JSONObject(File("src/test/resources/mowan/golden.json").readText())
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private val tol = 0.011

    @Test
    fun randomMatchesPrototype() {
        val rng = golden.getJSONObject("rng")
        assertEquals(rng.getLong("hashMowan"), Rng.hash("mowan"))
        val r = Mulberry32((Rng.hash("head") + 3 * 7919L).toInt())
        val expected = rng.getJSONArray("mulberry")
        for (i in 0 until expected.length()) assertEquals(expected.getDouble(i), r.next(), 1e-15)
    }

    @Test
    fun headLoopMatchesPrototype() {
        val loop = golden.getJSONArray("headLoop")
        assertEquals(loop.length(), Mowan.HEAD_LOOP.size)
        for (i in 0 until loop.length()) {
            val p = loop.getJSONArray(i)
            assertEquals(p.getDouble(0), Mowan.HEAD_LOOP[i].x, 1e-9)
            assertEquals(p.getDouble(1), Mowan.HEAD_LOOP[i].y, 1e-9)
        }
        assertEquals(golden.getInt("rootIndex"), Mowan.ROOT_INDEX)
    }

    /** 故事頁的解鎖要跟原型的 UNLOCKS 表一樣（原型就是照 chapter1.json 寫的） */
    @Test
    fun partsFollowChapterOne() {
        val expected = mapOf(
            1 to setOf("body"),
            2 to setOf("head", "flick"),
            3 to setOf("eyes", "blush"),
            4 to setOf("armR"),
            5 to setOf("armL"),
            6 to setOf("legL"),
            7 to setOf("legR"),
            8 to setOf("mouth"),
            9 to setOf("redToes"),
            10 to setOf("scarf"),
            11 to setOf("paperHat"),
            12 to emptySet(),
            13 to emptySet(),
            14 to setOf("bundle"),
        )
        var acc = emptySet<String>()
        for (page in 1..14) {
            acc = acc + expected.getValue(page)
            assertEquals("第 $page 頁", acc, Mowan.partsOf(StoryEngine.unlocked(beats, page)))
        }
    }

    @Test
    fun framesMatchPrototype() {
        val frames = golden.getJSONArray("frames")
        for (f in 0 until frames.length()) {
            val g = frames.getJSONObject(f)
            val page = g.getInt("page")
            val variant = g.getInt("variant")
            val hatOn = g.getBoolean("hatOn")
            val weather = Mowan.Weather.valueOf(g.getString("weather").uppercase())
            val clock = g.getDouble("clock")
            val label = "第 $page 頁、$weather ${clock.toInt()}ms、影格 $variant、紙帽${if (hatOn) "戴著" else "脫下"}"
            val frame = Mowan.frame(
                Mowan.partsOf(StoryEngine.unlocked(beats, page)), variant, hatOn, weather, clock, g.getInt("rainStep"),
            )

            val head = Regex("""translate\(0 (\S+)\) rotate\((\S+) 60\.5 66\)""").find(g.getString("headTransform"))!!
            assertEquals("$label 頭的位移", head.groupValues[1].toDouble(), frame.head.dy, tol)
            assertEquals("$label 頭的角度", head.groupValues[2].toDouble(), frame.head.rotDeg, tol)
            assertEquals(60.5, frame.head.pivotX, 0.0)
            assertEquals(66.0, frame.head.pivotY, 0.0)

            val canopy = g.getString("canopyTransform")
            if (canopy.isEmpty()) {
                assertNull("$label 不該有傘", frame.canopy)
            } else {
                val m = Regex("""translate\((\S+) (\S+)\) rotate\((\S+)\) scale\((\S+)\)""").find(canopy)!!
                val t = frame.canopy!!
                assertEquals("$label 傘的位置", m.groupValues[1].toDouble(), t.dx, tol)
                assertEquals("$label 傘的位置", m.groupValues[2].toDouble(), t.dy, tol)
                assertEquals("$label 傘的角度", m.groupValues[3].toDouble(), t.rotDeg, tol)
                assertEquals("$label 傘的大小", m.groupValues[4].toDouble(), t.scale, tol)
            }

            val ops = g.getJSONArray("ops")
            assertEquals("$label 的筆畫數", ops.length(), frame.ops.size)
            for (i in 0 until ops.length()) {
                val o = ops.getJSONObject(i)
                val op = frame.ops[i]
                val at = "$label 第 $i 筆"
                assertEquals(at, o.getString("layer").uppercase(), op.layer.name)
                assertEquals(at, o.optString("fill-rule") == "evenodd", op.evenOdd)
                assertEquals(at, o.optDouble("opacity", 1.0), op.alpha.toDouble(), 1e-6)
                if (o.has("gradient")) {
                    val gr = o.getJSONObject("gradient")
                    val my = op.gradient!!
                    assertEquals(at, gr.getDouble("x1"), my.x1, tol)
                    assertEquals(at, gr.getDouble("y1"), my.y1, tol)
                    assertEquals(at, gr.getDouble("x2"), my.x2, tol)
                    assertEquals(at, gr.getDouble("y2"), my.y2, tol)
                } else {
                    assertEquals(at, o.getString("fill").uppercase(), op.fill?.let { "#%06X".format(it and 0xFFFFFF) } ?: "NONE")
                }
                if (o.has("stroke")) {
                    assertEquals(at, o.getString("stroke").uppercase(), op.stroke?.let { "#%06X".format(it and 0xFFFFFF) })
                    assertEquals(at, o.getDouble("stroke-width"), op.strokeWidth, 1e-9)
                    assertEquals(at, o.optString("stroke-linecap") == "round", op.roundCap)
                } else {
                    assertNull(at, op.stroke)
                }
                assertPathEquals(at, parseSvg(o.getString("d")), op.path.commands)
            }
        }
    }

    @Test
    fun semicircleArcCenter() {
        // 筆畫末端的圓頭：從 (0,0) 到 (2,0)、半徑 1、sweep 0 → 圓心在中點，逆時針畫半圈
        val a = ArcCenter.of(0.0, 0.0, PathData.Cmd.ArcTo(1.0, 1.0, false, false, 2.0, 0.0))!!
        assertEquals(1.0, a.cx, 1e-9)
        assertEquals(0.0, a.cy, 1e-9)
        assertEquals(180.0, a.startDeg, 1e-9)
        assertEquals(-180.0, a.sweepDeg, 1e-9)
    }

    private fun assertPathEquals(at: String, expected: List<PathData.Cmd>, actual: List<PathData.Cmd>) {
        assertEquals("$at 的指令數", expected.size, actual.size)
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            val where = "$at 指令 $i（$e vs $a）"
            assertEquals(where, e::class, a::class)
            when (e) {
                is PathData.Cmd.MoveTo -> { a as PathData.Cmd.MoveTo; near(where, e.x, a.x); near(where, e.y, a.y) }
                is PathData.Cmd.LineTo -> { a as PathData.Cmd.LineTo; near(where, e.x, a.x); near(where, e.y, a.y) }
                is PathData.Cmd.ArcTo -> {
                    a as PathData.Cmd.ArcTo
                    near(where, e.rx, a.rx); near(where, e.ry, a.ry)
                    assertEquals(where, e.largeArc, a.largeArc)
                    assertEquals(where, e.sweep, a.sweep)
                    near(where, e.x, a.x); near(where, e.y, a.y)
                }
                is PathData.Cmd.QuadTo -> {
                    a as PathData.Cmd.QuadTo
                    near(where, e.x1, a.x1); near(where, e.y1, a.y1); near(where, e.x, a.x); near(where, e.y, a.y)
                }
                PathData.Cmd.Close -> Unit
            }
        }
    }

    private fun near(where: String, e: Double, a: Double) = assertTrue("$where：$e ≠ $a", Math.abs(e - a) <= tol)

    /** 原型的 path 只用到 M、L、Q、A、a、Z；全部轉成絕對座標 */
    private fun parseSvg(d: String): List<PathData.Cmd> {
        val tokens = Regex("""[MLQAaZ]|-?\d*\.?\d+(?:e-?\d+)?""").findAll(d).map { it.value }.toList()
        val out = ArrayList<PathData.Cmd>()
        var i = 0
        var x = 0.0
        var y = 0.0
        var sx = 0.0
        var sy = 0.0
        fun num() = tokens[i++].toDouble()
        while (i < tokens.size) {
            when (val c = tokens[i++]) {
                "M" -> { x = num(); y = num(); sx = x; sy = y; out += PathData.Cmd.MoveTo(x, y) }
                "L" -> { x = num(); y = num(); out += PathData.Cmd.LineTo(x, y) }
                "Q" -> { val x1 = num(); val y1 = num(); x = num(); y = num(); out += PathData.Cmd.QuadTo(x1, y1, x, y) }
                "A", "a" -> {
                    val rx = num(); val ry = num(); num()
                    val large = num() != 0.0; val sweep = num() != 0.0
                    var ex = num(); var ey = num()
                    if (c == "a") { ex += x; ey += y }
                    x = ex; y = ey
                    out += PathData.Cmd.ArcTo(rx, ry, large, sweep, ex, ey)
                }
                "Z" -> { x = sx; y = sy; out += PathData.Cmd.Close }
                else -> error("看不懂的指令 $c")
            }
        }
        return out
    }
}
