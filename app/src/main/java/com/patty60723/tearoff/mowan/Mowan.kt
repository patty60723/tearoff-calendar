package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.Geometry.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 墨丸 V3 的造型與一張靜態影格的組成。數值全部來自原型
 * docs/prototypes/src/engine.js、core.js，不在這裡調整比例或五官。
 *
 * 目前只有一般站姿（Phase 2B 的 P1）；天氣姿勢、節日、48px 規則還沒做。
 */
object Mowan {
    // ---------- 色票（Confirmed） ----------
    const val INK = 0xFF453D38.toInt()
    const val TERRA = 0xFFB96F62.toInt()
    const val IVORY = 0xFFF7F3EB.toInt()
    const val SAGE = 0xFF91A49B.toInt()
    const val PINK = 0xFFD8ADA5.toInt()
    /** 頭的填色，原型用的是比 Ivory 再白一點的紙色 */
    const val PAPER = 0xFFFCFAF5.toInt()

    /** 預先算好的筆觸影格數與播放順序（6 FPS） */
    const val FRAMES = 5
    val SEQUENCE = intArrayOf(0, 3, 1, 4, 2)

    // ---------- 頭 ----------
    internal val HEAD_C = Pt(60.0, 47.5)
    internal const val HEAD_N = 72
    internal val HEAD_PIVOT = Pt(60.5, 66.0)

    /** 不對稱的手繪頭型：左下略寬，右上（一筆的根部附近）微微鼓起 */
    private fun headBase(a: Double): Pt {
        val bump = 1.1 * max(0.0, cos(a + 1.15)).pow(4)
        val rx = 22.5 * (1 + 0.035 * sin(2 * a + 0.5)) + bump
        val ry = 20 * (1 + 0.03 * cos(3 * a + 0.2)) + bump
        return Pt(HEAD_C.x + rx * cos(a), HEAD_C.y + ry * sin(a))
    }

    internal val HEAD_LOOP: List<Pt> = List(HEAD_N) { headBase(it.toDouble() / HEAD_N * TAU) }

    /** 一筆的根部，在頭頂偏左 */
    internal val ROOT_INDEX = (((-1.78 + TAU) % TAU) / TAU * HEAD_N).roundToInt()

    /** 一筆的形狀（以根部為原點） */
    internal val FLICK_NORMAL = listOf(Pt(0.0, 0.0), Pt(-0.8, -6.5), Pt(3.5, -12.0), Pt(11.0, -14.5))

    /** 沒有腳時，身體下端直接落在地面上（第 1–5 頁），不懸空 */
    internal const val GROUND_DY = 127.6 - 101

    // ---------- 一般站姿 ----------
    private fun pts(vararg xy: Double) = List(xy.size / 2) { Pt(xy[2 * it], xy[2 * it + 1]) }

    internal class Pose(
        val body: List<Pt>, val armL: List<Pt>, val armR: List<Pt>,
        val legL: List<Pt>, val legR: List<Pt>, val tail: List<Pt>,
        val headAngle: Double = 0.0, val headDy: Double = 0.0,
    ) {
        fun shifted(dy: Double): Pose {
            fun s(p: List<Pt>) = p.map { Pt(it.x, it.y + dy) }
            return Pose(s(body), s(armL), s(armR), s(legL), s(legR), s(tail), headAngle, headDy + dy)
        }
    }

    internal val NORMAL = Pose(
        body = pts(60.5, 68.6, 61.4, 78.0, 59.6, 90.0, 61.0, 101.0),
        armL = pts(60.5, 77.0, 55.5, 82.5, 52.6, 89.0, 51.6, 96.0),
        armR = pts(60.8, 77.0, 65.8, 80.8, 68.8, 86.2, 67.6, 93.0),
        legL = pts(61.0, 101.0, 59.0, 109.0, 57.6, 118.0, 57.0, 127.0, 52.4, 127.6),
        legR = pts(61.0, 101.0, 64.8, 108.0, 66.8, 114.0, 69.2, 119.6, 70.6, 124.2, 71.0, 126.6, 75.0, 125.6),
        tail = pts(54.0, 70.6, 53.2, 76.0, 53.4, 81.2, 51.8, 87.2),
    )

    // ---------- 紙帽（第 11 頁） ----------
    private object PaperHat {
        const val THETA = -0.98
        const val SINK = 0.8
        const val BEND_R = 21.0
        val shape = Geometry.resample(
            pts(-12.0, 0.0, 12.0, 0.0, 12.0, -3.6, 10.4, -3.6, 0.6, -17.0, -10.4, -3.6, -12.0, -3.6), 60,
        )
        val lines = listOf(pts(-10.4, -3.6, 10.4, -3.6), pts(0.6, -15.6, 0.9, -9.0, 0.4, -3.6))
    }

    // ---------- 故事解鎖 → 畫哪些部位 ----------

    /** chapter1.json 的 unlock 名稱 → 畫面上的部位。「一筆」跟頭一起出現（Q1），腮紅跟眼睛一起出現。 */
    private val PARTS_OF_UNLOCK = mapOf(
        "line" to listOf("body"),
        "head" to listOf("head", "flick"),
        "eyes" to listOf("eyes", "blush"),
        "armR" to listOf("armR"),
        "armL" to listOf("armL"),
        "legL" to listOf("legL"),
        "legR" to listOf("legR"),
        "voice" to listOf("mouth"),
        "redToes" to listOf("redToes"),
        "scarf" to listOf("scarf"),
        "paperHat" to listOf("paperHat"),
        // bundle（第 14 頁）：V3 包袱還沒設計（Q21 TBD），先不畫
        "bundle" to listOf("bundle"),
    )

    fun partsOf(unlocks: Set<String>): Set<String> = unlocks.flatMapTo(HashSet()) { PARTS_OF_UNLOCK[it].orEmpty() }

    // ---------- 一張影格 ----------

    /** 原型的圖層順序；同一層裡按加入順序畫 */
    enum class Layer { SKY, CLOUDS, RAIN, BACKDROP, GROUND, SHAFT, BODY, SCARF, HEAD, HAND, CANOPY, FRONT }

    /** 線性漸層（userSpaceOnUse），stops 是 (位置, 顏色, 不透明度) */
    data class Gradient(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val stops: List<Triple<Float, Int, Float>>)

    data class Op(
        val layer: Layer,
        val path: PathData,
        val color: Int,
        val alpha: Float = 1f,
        val evenOdd: Boolean = false,
        val gradient: Gradient? = null,
    )

    /** HEAD 層要先 translate(0, headDy) 再以 [HEAD_PIVOT] 為中心轉 headAngle 度 */
    class Frame(val ops: List<Op>, val headDy: Double, val headAngle: Double)

    /**
     * 一般站姿的一張靜態影格。
     * @param parts 已解鎖的部位，見 [partsOf]
     * @param variant 筆觸影格 0 到 [FRAMES]−1
     * @param hatOn 紙帽是否戴著（取得後的裝備狀態，細節 TBD）
     */
    fun frame(parts: Set<String>, variant: Int, hatOn: Boolean = true): Frame {
        val ops = ArrayList<Op>()
        val v = variant
        val stand = "legL" in parts || "legR" in parts
        val p = if (stand) NORMAL else NORMAL.shifted(GROUND_DY)

        fun add(layer: Layer, path: PathData, color: Int, alpha: Float = 1f, evenOdd: Boolean = false, gradient: Gradient? = null) {
            ops += Op(layer, path, color, alpha, evenOdd, gradient)
        }
        fun ringPath(layer: Layer, pts: List<Pt>, fill: Int, part: String, w: Double, amp: Double): Geometry.Ring {
            val ph = Rng.phases(part, v)
            val r = Geometry.ring(pts, { a -> w * (1 + 0.12 * sin(2 * a + ph[3])) }, { a -> Geometry.loopNoise(ph, a, amp) })
            add(layer, r.fill, fill)
            add(layer, r.stroke, INK, evenOdd = true)
            return r
        }
        fun strokePath(layer: Layer, pts: List<Pt>, w0: Double, w1: Double, part: String, amp: Double) {
            val ph = Rng.phases(part, v)
            add(layer, Geometry.brush(Geometry.catmull(pts, 6), { t -> lerp(w0, w1, t) * Geometry.widthVar(ph, t, 0.1) }, { t -> Geometry.openNoise(ph, t, amp, false) }), INK)
        }
        fun limb(part: String, pts: List<Pt>, w0: Double, w1: Double, amp: Double): Geometry.Stroke {
            val ph = Rng.phases(part, v)
            return Geometry.stroke(Geometry.catmull(pts, 9), { t -> (w0 + (w1 - w0) * t) * Geometry.widthVar(ph, t, 0.13) }, { t -> Geometry.openNoise(ph, t, amp, false) })
        }

        // ---- 地面的淡影 ----
        add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 16.0, 2.2), INK, 0.07f)

        // ---- 身體 ----
        for ((name, w0, w1) in listOf(Triple("legL", 3.1, 2.3), Triple("legR", 3.0, 2.2))) {
            if (name !in parts) continue
            val lg = limb(name, if (name == "legL") p.legL else p.legR, w0, w1, 0.45)
            add(Layer.BODY, Geometry.outline(lg), INK)
            if ("redToes" in parts) {
                // 腳尖沾到紅墨：最後 7 個點，寬度放大一點，越往腳尖越濃
                val j0 = lg.n - 7
                val a = lg.disp[j0]
                val b = lg.disp[lg.n - 1]
                val stops = listOf(Triple(0f, TERRA, 0f), Triple(0.5f, TERRA, 0.7f), Triple(1f, TERRA, 0.95f))
                add(Layer.BODY, Geometry.outline(lg, j0, 1.12), TERRA, gradient = Gradient(a.x, a.y, b.x, b.y, stops))
            }
        }
        if ("body" in parts) add(Layer.BODY, Geometry.outline(limb("body", p.body, 3.1, 2.8, 0.5)), INK)
        if ("armL" in parts) add(Layer.BODY, Geometry.outline(limb("armL", p.armL, 2.9, 2.1, 0.45)), INK)
        if ("armR" in parts) add(Layer.BODY, Geometry.outline(limb("armR", p.armR, 2.9, 2.1, 0.45)), INK)

        // ---- 圍巾 ----
        if ("scarf" in parts) {
            val pb = Rng.phases("band", v)
            val pt = Rng.phases("tail", v)
            val ps = Rng.phases("split", v)
            add(Layer.SCARF, Geometry.brush(
                Geometry.catmull(pts(51.0, 68.6, 55.5, 71.3, 64.0, 71.4, 70.0, 67.6), 8),
                { t -> (4.4 + 0.6 * sin(PI * t)) * Geometry.widthVar(pb, t, 0.12) },
                { t -> Geometry.openNoise(pb, t, 0.4, false) },
            ), TERRA)
            add(Layer.SCARF, Geometry.brush(
                Geometry.catmull(p.tail, 9),
                { t -> (3.9 - 1.9 * t) * Geometry.widthVar(pt, t, 0.14) },
                { t -> Geometry.openNoise(pt, t, 0.7, true) },
            ), TERRA)
            val end = p.tail.last()
            val mid = p.tail[2]
            add(Layer.SCARF, Geometry.brush(
                Geometry.catmull(listOf(mid, Pt((mid.x + end.x) / 2 + 1.8, (mid.y + end.y) / 2 + 0.6), Pt(end.x + 4, end.y + 1.2)), 6),
                { t -> (1.6 - 1.1 * t) * Geometry.widthVar(ps, t, 0.15) },
                { t -> Geometry.openNoise(ps, t, 0.35, true) },
            ), TERRA)
        }

        // ---- 頭（五官、紙帽、一筆都在頭部座標裡） ----
        if ("head" in parts) {
            val phd = Rng.phases("head", v)
            val heavy = 2.35 + 0.3 * sin(phd[5])
            val head = Geometry.ring(
                HEAD_LOOP,
                { a -> 2.4 * (1 + 0.13 * (0.7 * sin(2 * a + phd[3]) + 0.3 * sin(3 * a + phd[4]))) + 1.1 * max(0.0, cos(a - heavy)).pow(3) },
                { a -> Geometry.loopNoise(phd, a, 0.75) },
            )
            add(Layer.HEAD, head.fill, PAPER)
            add(Layer.HEAD, head.stroke, INK, evenOdd = true)

            fun faceBrush(part: String, pts: List<Pt>, w: Double) {
                val ph = Rng.phases(part, v)
                add(Layer.HEAD, Geometry.brush(
                    Geometry.catmull(pts, 6),
                    { t -> w * (0.85 + 0.15 * sin(PI * t)) * Geometry.widthVar(ph, t, 0.08) },
                    { t -> Geometry.openNoise(ph, t, 0.12, false) },
                ), INK)
            }
            if ("blush" in parts) {
                add(Layer.HEAD, PathData.ellipse(47.0, 52.8, 3.2, 1.6), PINK, 0.85f)
                add(Layer.HEAD, PathData.ellipse(72.5, 52.3, 3.2, 1.6), PINK, 0.85f)
            }
            if ("eyes" in parts) {
                val pe = Rng.phases("eyes", v)
                val r = 1.95 * (1 + 0.05 * sin(pe[0]))
                add(Layer.HEAD, PathData.ellipse(52.0 + 0.1 * sin(pe[1]), 46.8, r, r), INK)
                add(Layer.HEAD, PathData.ellipse(67.0 + 0.1 * sin(pe[2]), 46.3, r, r), INK)
            }
            if ("mouth" in parts) faceBrush("mouth", pts(57.4, 54.6, 60.0, 56.2, 62.6, 54.4), 1.9)

            if ("paperHat" in parts && hatOn) {
                val fit = { pts: List<Pt> -> Geometry.conform(pts, PaperHat.THETA, PaperHat.SINK, head.points, 0.0, PaperHat.BEND_R) }
                ringPath(Layer.HEAD, fit(PaperHat.shape), IVORY, "paperHat0", 1.35, 0.4)
                PaperHat.lines.forEachIndexed { i, line -> strokePath(Layer.HEAD, fit(line), 1.0, 0.8, "paperHatl${i + 1}", 0.12) }
            }

            // 一筆：永遠在最前面
            if ("flick" in parts) {
                val root = head.points[ROOT_INDEX]
                val pf = Rng.phases("flick", v)
                add(Layer.HEAD, Geometry.brush(
                    Geometry.catmull(FLICK_NORMAL.map { Pt(root.x + it.x, root.y + it.y + 1.2) }, 9),
                    { t -> (0.35 + 3.6 * (1 - t).pow(0.85)) * Geometry.widthVar(pf, t, 0.18) },
                    { t -> Geometry.openNoise(pf, t, 1.4, true) },
                ), INK)
            }
        }
        return Frame(ops.sortedBy { it.layer.ordinal }, p.headDy, p.headAngle)
    }
}

