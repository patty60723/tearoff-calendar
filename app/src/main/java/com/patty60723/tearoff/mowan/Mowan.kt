package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.Geometry.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 墨丸 V3 的造型與一張影格的組成。數值全部來自原型
 * docs/prototypes/src/engine.js、core.js，不在這裡調整比例或五官。
 *
 * 目前有一般、晴天、雨天三種場景（Phase 2B 的 P2）；節日、48px 規則還沒做。
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

    // ---------- Line boil（Confirmed：6 FPS、預先決定的影格） ----------
    const val FRAMES = 5
    val SEQUENCE = intArrayOf(0, 3, 1, 4, 2)
    const val BOIL_MS = 1000.0 / 6
    /** 雨絲另外用 8 FPS */
    const val RAIN_MS = 1000.0 / 8

    /** 第 [clockMs] 毫秒該用哪一張筆觸影格。用時間算，不是每畫一次加一，所以螢幕刷新再快都還是 6 FPS。 */
    fun variantAt(clockMs: Long): Int = SEQUENCE[((clockMs / BOIL_MS).toLong() % FRAMES).toInt()]

    fun rainStepAt(clockMs: Long): Int = (clockMs / RAIN_MS).toInt()

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

    /** 沒有腳時，身體下端直接落在地面上（第 1–5 頁），不懸空 */
    internal const val GROUND_DY = 127.6 - 101

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

    // ---------- 雨傘 ----------

    /** 傘面的中心線（以握傘的手為原點） */
    private val CANOPY: List<Pt> = buildList {
        for (i in 0..24) {
            val u = i / 24.0
            add(Pt(-34 * cos(PI * u), -64 - 24 * sin(PI * u).pow(0.85)))
        }
        for (i in 1 until 32) {
            val s = i / 32.0
            add(Pt(34 - 68 * s, -64 - 3.2 * sin(PI * ((4 * s) % 1))))
        }
    }

    /** 傘骨（細線） */
    private val RIBS = PathData.Builder()
        .move(Pt(-17.0, -64.0)).quad(Pt(-13.0, -79.0), Pt(0.0, -88.0))
        .move(Pt(17.0, -64.0)).quad(Pt(13.0, -79.0), Pt(0.0, -88.0))
        .move(Pt(0.0, -88.0)).line(Pt(0.0, -92.0))
        .build()

    /** 雨絲的起點 */
    private val DROPS = pts(10.0, 0.0, 20.0, 52.0, 8.0, 95.0, 26.0, 30.0, 16.0, 120.0, 112.0, 18.0, 116.0, 70.0, 108.0, 110.0, 28.0, 85.0)

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

    /** 天氣場景。其他天氣（熱、陰、霧、冷、雪）的 V3 樣子還沒定（D-10），先用 [NONE]。 */
    enum class Weather { NONE, SUNNY, RAINY }

    /** 原型的圖層順序；同一層裡按加入順序畫 */
    enum class Layer { SKY, CLOUDS, RAIN, BACKDROP, GROUND, SHAFT, BODY, SCARF, HEAD, HAND, CANOPY, FRONT }

    /** 線性漸層（userSpaceOnUse），stops 是 (位置, 顏色, 不透明度) */
    data class Gradient(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val stops: List<Triple<Float, Int, Float>>)

    /** 一筆繪圖：填色（[fill] 或 [gradient]）、描邊（[stroke]，圓頭）可以都有，先填再描 */
    data class Op(
        val layer: Layer,
        val path: PathData,
        val fill: Int?,
        val alpha: Float = 1f,
        val evenOdd: Boolean = false,
        val gradient: Gradient? = null,
        val stroke: Int? = null,
        val strokeWidth: Double = 0.0,
        val roundCap: Boolean = false,
    )

    /** 圖層的變換：先位移，再以 (pivotX, pivotY) 為中心旋轉（度），再縮放 */
    data class Transform(
        val dx: Double, val dy: Double, val rotDeg: Double,
        val pivotX: Double = 0.0, val pivotY: Double = 0.0, val scale: Double = 1.0,
    )

    /** [head] 套在 HEAD 層；[canopy] 套在 CANOPY 層（撐傘時才有） */
    class Frame(val ops: List<Op>, val head: Transform, val canopy: Transform?)

    /**
     * 一張影格。
     * @param parts 已解鎖的部位，見 [partsOf]
     * @param variant 筆觸影格 0 到 [FRAMES]−1，見 [variantAt]
     * @param hatOn 紙帽是否戴著（裝備狀態，見 [MowanSettings.hatOn]；還沒拿到時不管這個值都不畫）
     * @param weather 天氣場景
     * @param clockMs 姿勢的時間（伸懶腰、傘的擺動）
     * @param rainStep 雨絲的位置，見 [rainStepAt]
     * @param umbrellaScale 傘的大小（D-03 未定，傾向原尺寸 1.0）
     */
    fun frame(
        parts: Set<String>,
        variant: Int,
        hatOn: Boolean = true,
        weather: Weather = Weather.NONE,
        clockMs: Double = 0.0,
        rainStep: Int = 0,
        umbrellaScale: Double = 1.0,
    ): Frame {
        val ops = ArrayList<Op>()
        val v = variant
        val caps = Capabilities.of(parts)
        val rainy = weather == Weather.RAINY
        // 雨傘是功能道具，要有右手才拿得了（Confirmed）
        val umbrella = rainy && caps.holdR
        var p = when {
            weather == Weather.SUNNY -> Poses.sunny(clockMs)
            umbrella -> Poses.rainy(clockMs)
            else -> Poses.NORMAL
        }
        if (rainy && !umbrella) p = p.copy(face = Face.RAINY, flick = Flick.DROOPED)
        // 閉眼笑要有眼睛
        if (p.face == Face.SUNNY && !caps.expression) p = p.copy(face = Face.NORMAL)
        if (!caps.stand) p = p.shifted(GROUND_DY)
        // 成長焦點期（還沒有手）：天空降到 55%
        val skyOp = if (caps.growthFocus) 0.55f else 1f

        fun add(layer: Layer, path: PathData, fill: Int?, alpha: Float = 1f, evenOdd: Boolean = false, gradient: Gradient? = null) {
            ops += Op(layer, path, fill, alpha, evenOdd, gradient)
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

        // ---- 天空（晴天的太陽） ----
        if (weather == Weather.SUNNY) {
            add(Layer.SKY, PathData.ellipse(99.0, 4.0, 8.5, 8.5), PINK, 0.75f * skyOp)
            ops += Op(Layer.SKY, PathData.ellipse(99.0, 4.0, 12.0, 12.0), null, skyOp, stroke = PINK, strokeWidth = 1.1)
        }
        // ---- 雨（8 FPS） ----
        if (rainy) for (d in DROPS) {
            val y = ((d.y + rainStep * 7) % 150) - 20
            val path = PathData.Builder().move(Pt(d.x, y)).line(Pt(d.x - 1.6, y + 7)).build()
            ops += Op(Layer.RAIN, path, null, stroke = SAGE, strokeWidth = 1.5, roundCap = true)
        }

        // ---- 地面的影子（雨天是一灘水） ----
        if (rainy) add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 20.0, 2.6), SAGE, 0.28f)
        else add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 16.0, 2.2), INK, 0.07f)

        // ---- 傘柄（在身體後面） ----
        val hand = p.hand
        if (umbrella && hand != null) {
            val pl = Geometry.Placement(hand.x, hand.y, Math.toRadians(p.umbrellaAngle), umbrellaScale)
            val shaft = Geometry.place(pts(0.0, -64.0, 0.0, -30.0, 0.0, 7.0), pl)
            val crook = Geometry.place(pts(0.0, 7.0, -1.5, 11.4, -5.0, 11.0), pl)
            val path = PathData.Builder()
                .add(Geometry.brush(Geometry.catmull(shaft, 6), { 2.2 }, { 0.0 }))
                .add(Geometry.brush(Geometry.catmull(crook, 6), { 2.2 }, { 0.0 }))
                .build()
            add(Layer.SHAFT, path, INK)
        }

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
                add(Layer.BODY, Geometry.outline(lg, j0, 1.12), null, gradient = Gradient(a.x, a.y, b.x, b.y, stops))
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

        // ---- 頭（五官、紙帽、一筆都在頭部座標裡，跟著頭轉） ----
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
                // 新天氣的表情要到 W3 才畫；在那之前 frame() 只會收到一般、晴、雨
                val op = when (p.face) { Face.SUNNY -> 1f; Face.RAINY -> 0.55f; else -> 0.85f }
                add(Layer.HEAD, PathData.ellipse(47.0, 52.8, 3.2, 1.6), PINK, op)
                add(Layer.HEAD, PathData.ellipse(72.5, 52.3, 3.2, 1.6), PINK, op)
            }
            if ("eyes" in parts) {
                if (p.face == Face.SUNNY) {
                    faceBrush("lidL", pts(49.6, 47.1, 52.0, 48.1, 54.4, 47.1), 1.7)
                    faceBrush("lidR", pts(64.6, 46.6, 67.0, 47.6, 69.4, 46.6), 1.7)
                } else {
                    val eyes = if (p.face == Face.RAINY) pts(52.4, 47.0, 67.2, 46.6) else pts(52.0, 46.8, 67.0, 46.3)
                    val pe = Rng.phases("eyes", v)
                    val r = 1.95 * (1 + 0.05 * sin(pe[0]))
                    add(Layer.HEAD, PathData.ellipse(eyes[0].x + 0.1 * sin(pe[1]), eyes[0].y, r, r), INK)
                    add(Layer.HEAD, PathData.ellipse(eyes[1].x + 0.1 * sin(pe[2]), eyes[1].y, r, r), INK)
                }
                if (p.face == Face.RAINY) {
                    faceBrush("browL", pts(49.5, 43.6, 51.8, 42.9, 54.0, 42.1), 1.5)
                    faceBrush("browR", pts(69.8, 43.1, 67.6, 42.4, 65.4, 41.6), 1.5)
                }
            }
            if ("mouth" in parts) {
                val mouth = when (p.face) {
                    Face.RAINY -> pts(57.6, 55.0, 60.0, 54.2, 62.4, 55.0)
                    Face.SUNNY -> pts(57.5, 54.6, 60.0, 56.6, 62.5, 54.4)
                    else -> pts(57.4, 54.6, 60.0, 56.2, 62.6, 54.4)
                }
                faceBrush("mouth", mouth, 1.9)
            }

            // 紙帽：拿到（第 11 頁）而且戴著才畫
            if (caps.paperHat && hatOn) {
                val fit = { pts: List<Pt> -> Geometry.conform(pts, PaperHat.THETA, PaperHat.SINK, head.points, 0.0, PaperHat.BEND_R) }
                ringPath(Layer.HEAD, fit(PaperHat.shape), IVORY, "paperHat0", 1.35, 0.4)
                PaperHat.lines.forEachIndexed { i, line -> strokePath(Layer.HEAD, fit(line), 1.0, 0.8, "paperHatl${i + 1}", 0.12) }
            }

            // 一筆：永遠在最前面
            if ("flick" in parts) {
                val root = head.points[ROOT_INDEX]
                val pf = Rng.phases("flick", v)
                add(Layer.HEAD, Geometry.brush(
                    Geometry.catmull(p.flick.shape.map { Pt(root.x + it.x, root.y + it.y + 1.2) }, 9),
                    { t -> (0.35 + 3.6 * (1 - t).pow(0.85)) * Geometry.widthVar(pf, t, 0.18) },
                    { t -> Geometry.openNoise(pf, t, 1.4, true) },
                ), INK)
            }
        }

        // ---- 傘面與拳頭 ----
        var canopy: Transform? = null
        if (umbrella && hand != null) {
            canopy = Transform(hand.x, hand.y, p.umbrellaAngle, scale = umbrellaScale)
            val pc = Rng.phases("canopy", v)
            val c = Geometry.ring(CANOPY, { a -> (2.2 / umbrellaScale) * (1 + 0.12 * sin(2 * a + pc[3])) }, { a -> Geometry.loopNoise(pc, a, 0.45) })
            add(Layer.CANOPY, c.fill, SAGE)
            add(Layer.CANOPY, c.stroke, INK, evenOdd = true)
            ops += Op(Layer.CANOPY, RIBS, null, stroke = INK, strokeWidth = 1.5 / umbrellaScale, roundCap = true)
            ops += Op(Layer.FRONT, PathData.ellipse(hand.x, hand.y, 3.2, 3.2), PAPER, stroke = INK, strokeWidth = 2.2)
        }
        return Frame(
            ops.sortedBy { it.layer.ordinal },
            Transform(0.0, p.headDy, p.headAngle, HEAD_PIVOT.x, HEAD_PIVOT.y),
            canopy,
        )
    }
}
