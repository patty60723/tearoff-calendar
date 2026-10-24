package com.patty60723.tearoff.mowan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** 原型的座標是 SVG 的 viewBox 0 −20 120 160，y 軸朝下。 */
data class Pt(val x: Double, val y: Double)

internal const val TAU = PI * 2

/**
 * 筆觸幾何，照原型 engine.js / core.js 移植。只做純數學，不碰 Android，才能在 JVM 上跟原型比對。
 */
internal object Geometry {
    fun lerp(a: Double, b: Double, u: Double) = a + (b - a) * u
    fun clamp01(u: Double) = if (u < 0) 0.0 else if (u > 1) 1.0 else u

    // ---------- 筆觸抖動 ----------

    /** 開放筆畫的位移：兩端為 0，中間最大；tip 模式只有根部固定 */
    fun openNoise(p: DoubleArray, t: Double, amp: Double, tip: Boolean): Double {
        val env = if (tip) t.pow(1.1) else sin(PI * t).pow(0.8)
        return amp * env * (0.6 * sin(TAU * t + p[0]) + 0.3 * sin(2 * TAU * t + p[1]) + 0.15 * sin(3 * TAU * t + p[2]))
    }

    fun widthVar(p: DoubleArray, t: Double, k: Double) =
        1 + k * (0.7 * sin(TAU * 1.3 * t + p[3]) + 0.3 * sin(TAU * 2.7 * t + p[4]))

    /** 封閉輪廓的位移：只用 2、3、5 次諧波，中心和大小不會跑掉 */
    fun loopNoise(p: DoubleArray, a: Double, amp: Double) =
        amp * (0.55 * sin(2 * a + p[0]) + 0.3 * sin(3 * a + p[1]) + 0.15 * sin(5 * a + p[2]))

    // ---------- 曲線 ----------

    fun catmull(pts: List<Pt>, perSeg: Int): List<Pt> {
        val out = ArrayList<Pt>((pts.size - 1) * perSeg + 1)
        val n = pts.size
        for (i in 0 until n - 1) {
            val p0 = pts[max(i - 1, 0)]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[minOf(i + 2, n - 1)]
            for (s in 0 until perSeg) {
                val t = s.toDouble() / perSeg
                val t2 = t * t
                val t3 = t2 * t
                fun f(a: Double, b: Double, c: Double, d: Double) =
                    0.5 * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3)
                out += Pt(f(p0.x, p1.x, p2.x, p3.x), f(p0.y, p1.y, p2.y, p3.y))
            }
        }
        out += pts[n - 1]
        return out
    }

    fun normalAt(pts: List<Pt>, i: Int, closed: Boolean): Pt {
        val n = pts.size
        val a = if (closed) pts[(i - 1 + n) % n] else pts[max(i - 1, 0)]
        val b = if (closed) pts[(i + 1) % n] else pts[minOf(i + 1, n - 1)]
        val tx = b.x - a.x
        val ty = b.y - a.y
        val l = hypot(tx, ty).takeIf { it != 0.0 } ?: 1.0
        return Pt(-ty / l, tx / l)
    }

    // ---------- 筆刷 ----------

    /** 有粗細的筆畫：中心線、左右兩側、每點半寬 */
    class Stroke(val disp: List<Pt>, val left: List<Pt>, val right: List<Pt>, val half: DoubleArray) {
        val n get() = disp.size
    }

    fun stroke(center: List<Pt>, widthAt: (Double) -> Double, offsetAt: (Double) -> Double): Stroke {
        val n = center.size
        val disp = center.mapIndexed { i, c ->
            val t = i.toDouble() / (n - 1)
            val nm = normalAt(center, i, false)
            val o = offsetAt(t)
            Pt(c.x + nm.x * o, c.y + nm.y * o)
        }
        val left = ArrayList<Pt>(n)
        val right = ArrayList<Pt>(n)
        val half = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / (n - 1)
            val nm = normalAt(disp, i, false)
            val w = widthAt(t) / 2
            half[i] = w
            left += Pt(disp[i].x + nm.x * w, disp[i].y + nm.y * w)
            right += Pt(disp[i].x - nm.x * w, disp[i].y - nm.y * w)
        }
        return Stroke(disp, left, right, half)
    }

    /**
     * 筆畫的外形：左側 → 圓頭 → 右側倒回來 → 圓頭。
     * [from] > 0 時只畫後段（紅墨腳尖），起點不加圓頭；[scale] 把寬度放大（墨水比線稍寬）。
     */
    fun outline(g: Stroke, from: Int = 0, scale: Double = 1.0): PathData {
        fun at(side: List<Pt>, i: Int) =
            if (scale == 1.0) side[i] else Pt(g.disp[i].x + (side[i].x - g.disp[i].x) * scale, g.disp[i].y + (side[i].y - g.disp[i].y) * scale)
        val n = g.n
        val we = max(g.half[n - 1] * scale, 0.05)
        val ws = max(g.half[from] * scale, 0.05)
        val b = PathData.Builder().move(at(g.left, from))
        for (i in from + 1 until n) b.line(at(g.left, i))
        b.arc(we, we, largeArc = false, sweep = false, at(g.right, n - 1))
        for (i in n - 2 downTo from) b.line(at(g.right, i))
        if (from == 0) b.arc(ws, ws, largeArc = false, sweep = false, at(g.left, 0))
        return b.close().build()
    }

    /** 原型的 brush()：一次算完外形 */
    fun brush(center: List<Pt>, widthAt: (Double) -> Double, offsetAt: (Double) -> Double): PathData =
        outline(stroke(center, widthAt, offsetAt))

    /** 封閉輪廓：填色用的中心線、輪廓筆畫（外圈＋反向內圈，用 even-odd 填） */
    class Ring(val fill: PathData, val stroke: PathData, val points: List<Pt>)

    fun ring(loop: List<Pt>, widthAt: (Double) -> Double, offsetAt: (Double) -> Double): Ring {
        val n = loop.size
        val disp = loop.mapIndexed { i, c ->
            val a = i.toDouble() / n * TAU
            val nm = normalAt(loop, i, true)
            val o = offsetAt(a)
            Pt(c.x + nm.x * o, c.y + nm.y * o)
        }
        val outer = ArrayList<Pt>(n)
        val inner = ArrayList<Pt>(n)
        for (i in 0 until n) {
            val a = i.toDouble() / n * TAU
            val nm = normalAt(disp, i, true)
            val w = widthAt(a) / 2
            outer += Pt(disp[i].x + nm.x * w, disp[i].y + nm.y * w)
            inner += Pt(disp[i].x - nm.x * w, disp[i].y - nm.y * w)
        }
        fun loopPath(b: PathData.Builder, pts: List<Pt>) {
            b.move(pts[0])
            for (i in 1 until pts.size) b.line(pts[i])
            b.close()
        }
        val fill = PathData.Builder().also { loopPath(it, disp) }.build()
        val stroke = PathData.Builder().also { loopPath(it, outer); loopPath(it, inner.asReversed()) }.build()
        return Ring(fill, stroke, disp)
    }

    // ---------- 配件形狀 ----------

    /** 沿周長平均取 [count] 個點（封閉多邊形） */
    fun resample(poly: List<Pt>, count: Int): List<Pt> {
        val segs = poly.indices.map { i -> val p = poly[i]; val q = poly[(i + 1) % poly.size]; hypot(q.x - p.x, q.y - p.y) }
        val total = segs.sum()
        val out = ArrayList<Pt>(count)
        var si = 0
        var acc = 0.0
        for (k in 0 until count) {
            val s = k.toDouble() / count * total
            while (acc + segs[si] < s) {
                acc += segs[si]
                si++
            }
            val p = poly[si]
            val q = poly[(si + 1) % poly.size]
            val u = (s - acc) / segs[si]
            out += Pt(p.x + (q.x - p.x) * u, p.y + (q.y - p.y) * u)
        }
        return out
    }

    class Placement(val x: Double, val y: Double, val rot: Double = 0.0, val scale: Double = 1.0)

    fun place(pts: List<Pt>, pl: Placement): List<Pt> {
        val c = cos(pl.rot)
        val s = sin(pl.rot)
        return pts.map { (x, y) -> Pt(pl.x + (x * c - y * s) * pl.scale, pl.y + (x * s + y * c) * pl.scale) }
    }

    fun bend(pts: List<Pt>, r: Double) = pts.map { (x, y) -> Pt(x, y + x * x / (2 * r)) }

    /** 頭頂配件的位置：錨點取這一張影格的頭輪廓（跟一筆根部同一規則） */
    fun headPlace(theta: Double, sink: Double, headPts: List<Pt>, tilt: Double = 0.0): Placement {
        val idx = (((theta + TAU) % TAU) / TAU * Mowan.HEAD_N).roundToInt() % Mowan.HEAD_N
        val p = headPts[idx]
        return Placement(p.x - cos(theta) * sink, p.y - sin(theta) * sink, theta + PI / 2 + tilt)
    }

    /** 貼合：靠近帽緣的點沿著這一格的頭輪廓排列，越往上越接近剛體擺放（避免帽尖被拉歪） */
    fun conform(pts: List<Pt>, theta: Double, sink: Double, headPts: List<Pt>, tilt: Double, bendR: Double): List<Pt> {
        val n = headPts.size
        val i0 = (((theta + TAU) % TAU) / TAU * n).roundToInt() % n
        // 沿輪廓走 s 單位（正＝角度增加方向），回傳位置與切線
        fun at(s: Double): Pair<Pt, Pt> {
            var i = i0
            var left = abs(s)
            val dir = if (s >= 0) 1 else -1
            for (k in 0 until n) {
                val a = headPts[Math.floorMod(i, n)]
                val b = headPts[Math.floorMod(i + dir, n)]
                val d = hypot(b.x - a.x, b.y - a.y)
                if (left <= d) {
                    val u = left / d
                    val t = if (dir > 0) Pt(b.x - a.x, b.y - a.y) else Pt(a.x - b.x, a.y - b.y)
                    return Pt(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u) to t
                }
                left -= d
                i += dir
            }
            return headPts[i0] to Pt(1.0, 0.0)
        }
        val rigid = place(bend(pts, bendR), headPlace(theta, sink, headPts, tilt))
        return pts.mapIndexed { j, (x, y) ->
            val h = -y
            val w = clamp01(1 - h / 9)
            if (w <= 0) return@mapIndexed rigid[j]
            val (p, t) = at(x)
            val l = hypot(t.x, t.y).takeIf { it != 0.0 } ?: 1.0
            val nx = t.y / l
            val ny = -t.x / l
            val c = Pt(p.x + nx * (h - sink), p.y + ny * (h - sink))
            Pt(lerp(rigid[j].x, c.x, w), lerp(rigid[j].y, c.y, w))
        }
    }

    /** 原型 core.js 的 ellipse()：32 個點的橢圓 */
    fun ellipsePts(cx: Double, cy: Double, rx: Double, ry: Double): List<Pt> =
        List(32) { i -> val a = i / 32.0 * TAU; Pt(cx + rx * cos(a), cy + ry * sin(a)) }
}
