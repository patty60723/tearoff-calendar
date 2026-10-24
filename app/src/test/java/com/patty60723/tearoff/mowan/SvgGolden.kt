package com.patty60723.tearoff.mowan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** 黃金數值測試共用：把原型輸出的 SVG path 轉成絕對座標指令，跟 Kotlin 的 [PathData] 逐一比對。 */
object SvgGolden {
    fun assertPathEquals(at: String, expected: List<PathData.Cmd>, actual: List<PathData.Cmd>, tol: Double) {
        assertEquals("$at 的指令數", expected.size, actual.size)
        for (i in expected.indices) {
            val e = expected[i]
            val a = actual[i]
            val where = "$at 指令 $i（$e vs $a）"
            assertEquals(where, e::class, a::class)
            when (e) {
                is PathData.Cmd.MoveTo -> { a as PathData.Cmd.MoveTo; near(tol, where, e.x, a.x); near(tol, where, e.y, a.y) }
                is PathData.Cmd.LineTo -> { a as PathData.Cmd.LineTo; near(tol, where, e.x, a.x); near(tol, where, e.y, a.y) }
                is PathData.Cmd.ArcTo -> {
                    a as PathData.Cmd.ArcTo
                    near(tol, where, e.rx, a.rx); near(tol, where, e.ry, a.ry)
                    assertEquals(where, e.largeArc, a.largeArc)
                    assertEquals(where, e.sweep, a.sweep)
                    near(tol, where, e.x, a.x); near(tol, where, e.y, a.y)
                }
                is PathData.Cmd.QuadTo -> {
                    a as PathData.Cmd.QuadTo
                    near(tol, where, e.x1, a.x1); near(tol, where, e.y1, a.y1); near(tol, where, e.x, a.x); near(tol, where, e.y, a.y)
                }
                PathData.Cmd.Close -> Unit
            }
        }
    }

    private fun near(tol: Double, where: String, e: Double, a: Double) = assertTrue("$where：$e ≠ $a", Math.abs(e - a) <= tol)

    /** 原型的 path 只用到 M、L、Q、A、a、Z；全部轉成絕對座標 */
    fun parse(d: String): List<PathData.Cmd> {
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
