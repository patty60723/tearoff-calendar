package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalScene.Item
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * 節日服裝與道具的形狀（P6-2），照原型 core.js 的 `HEADWEAR`、`HAND_PROPS`、`PROPS_V2`（手剪紙感）移植；
 * 頭飾 C 來自 mowan-hat-umbrella.html。座標都是 viewBox 單位、原點在錨點、往上是 −y。
 * 怎麼畫（圖層、筆刷）在 [Mowan.render]。
 *
 * 顏色在五色色票外，沿用原型的節日輔助色（跟 D-21 天氣輔助色同一種處理，Proposed）。
 */
internal object Props {
    /** 節日紅（原型 v2 道具的紅，比 v1 的 #B5473A 柔和） */
    const val RED = 0xFFAE4E42.toInt()
    const val MOON = 0xFFEEE2C2.toInt()
    const val CAKE = 0xFFD4AE78.toInt()
    /** 月面的淡影 */
    const val CRATER = 0xFFD8C9A2.toInt()
    /** 手剪紙道具的輪廓寬度 */
    const val OW = 0.9

    fun circlePts(cx: Double, cy: Double, r: Double, n: Int = 36) = List(n) { i -> Pt(cx + r * cos(i.toDouble() / n * TAU), cy + r * sin(i.toDouble() / n * TAU)) }

    /** 原型的 ellipse(cx, cy, rx, ry, rot)：32 個點、可以旋轉 */
    fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double, rot: Double = 0.0) = List(32) { i ->
        val a = i / 32.0 * TAU
        val x = rx * cos(a)
        val y = ry * sin(a)
        Pt(cx + x * cos(rot) - y * sin(rot), cy + x * sin(rot) + y * cos(rot))
    }

    private fun halfDome(w: Double, h: Double, base: Double) = List(17) { i ->
        val a = PI + i / 16.0 * PI
        Pt(w * cos(a), base + h * sin(a))
    }

    private fun r60(pts: List<Pt>) = Geometry.resample(pts, 60)

    /** 手剪紙的不規則輪廓：沿法線加固定的低頻起伏，再做一次圓角（同一個 seed 每次都一樣） */
    fun handCut(pts: List<Pt>, seed: String, amp: Double): List<Pt> {
        val r = Mulberry32(Rng.hash(seed).toInt())
        val ph1 = r.next() * TAU
        val ph2 = r.next() * TAU
        val loop = Geometry.resample(pts, 72)
        val out = loop.mapIndexed { i, p ->
            val n = Geometry.normalAt(loop, i, true)
            val a = i.toDouble() / loop.size * TAU
            val o = amp * (0.6 * sin(3 * a + ph1) + 0.4 * sin(7 * a + ph2))
            Pt(p.x + n.x * o, p.y + n.y * o)
        }
        val sm = ArrayList<Pt>()
        for (i in out.indices) {
            val p = out[i]
            val q = out[(i + 1) % out.size]
            sm += Pt(0.75 * p.x + 0.25 * q.x, 0.75 * p.y + 0.25 * q.y)
            sm += Pt(0.25 * p.x + 0.75 * q.x, 0.25 * p.y + 0.75 * q.y)
        }
        return Geometry.resample(sm, 64)
    }

    // ---------- 頭飾 ----------

    class Shape(val pts: List<Pt>, val fill: Int, val detail: Boolean = false)

    /** 戴在頭輪廓上的哪裡：角度（rad）、往外的傾斜 */
    class Spot(val theta: Double, val tilt: Double = 0.0)

    /**
     * @param id 筆觸相位用的名字（跟原型的頭飾 id 一樣，例如 caishen、caishenB、caishenC）
     * @param spots 多個位置（玉兔耳朵）時各自擺放、不做貼合；一個位置時沿頭輪廓貼合
     */
    class Headwear(
        val id: String,
        val spots: List<Spot>,
        val sink: Double,
        val bendR: Double,
        val shapes: List<Shape>,
        val lines: List<List<Pt>>,
        val lineColor: Int = Mowan.INK,
    ) {
        val multi get() = spots.size > 1
    }

    private val CAISHEN_SHAPES = listOf(
        Shape(r60(pts(-9.6, 0.0, 9.6, 0.0, 9.6, -2.6) + halfDome(8.2, 8.6, -2.4).reversed() + Pt(-9.6, -2.6)), RED),
        Shape(ellipse(11.6, -6.4, 3.3, 1.25, -0.3), Mowan.INK, detail = true),
        Shape(ellipse(-11.6, -6.4, 3.3, 1.25, 0.3), Mowan.INK, detail = true),
    )
    private val CAISHEN_LINES = listOf(pts(-9.2, -2.6, 9.2, -2.6))
    private val SANTA_BRIM = Shape(r60(pts(-11.0, 0.0, 11.0, 0.0, 11.0, -3.8, -11.0, -3.8)), Mowan.IVORY)
    private val SANTA_SHAPES = listOf(
        Shape(r60(pts(-9.2, -3.4, 9.2, -3.4, 9.8, -8.0, 11.8, -12.4, 14.6, -14.8, 12.4, -17.2, 6.0, -17.9, 0.5, -15.6, -4.8, -10.6)), RED),
        SANTA_BRIM,
        Shape(circlePts(15.2, -14.6, 2.7), Mowan.IVORY),
    )
    /** 聖誕帽 B：靠頂、帽尖垂右 */
    private val SANTA_B_SHAPES = listOf(
        Shape(r60(pts(-9.2, -3.4, 9.2, -3.4, 10.4, -7.4, 13.2, -9.6, 16.4, -9.2, 16.8, -11.8, 13.6, -13.6, 7.0, -15.0, 1.0, -13.6, -4.6, -10.0)), RED),
        SANTA_BRIM,
        Shape(circlePts(17.6, -8.2, 2.6), Mowan.IVORY),
    )
    private val EAR = listOf(Shape(ellipse(0.0, -6.9, 2.3, 6.9), Mowan.IVORY))
    private val EAR_LINES = listOf(pts(0.0, -2.6, 0.25, -7.0, 0.0, -11.0))

    /** 節日頭飾的形狀與戴法（D-01、D-02，候選由 [ArtConfig] 選） */
    fun headwear(item: Item, art: ArtConfig): Headwear = when (item) {
        Item.CAISHEN -> when (art.hat) {
            ArtConfig.HatStyle.A -> Headwear("caishen", listOf(Spot(-0.98)), 0.6, 21.0, CAISHEN_SHAPES, CAISHEN_LINES)
            ArtConfig.HatStyle.B -> Headwear("caishenB", listOf(Spot(-PI / 2)), 0.6, 25.0, CAISHEN_SHAPES, CAISHEN_LINES)
            ArtConfig.HatStyle.C -> Headwear("caishenC", listOf(Spot(-1.06, -0.16)), 1.8, 21.0, CAISHEN_SHAPES, CAISHEN_LINES)
        }
        Item.SANTA -> when (art.hat) {
            ArtConfig.HatStyle.A -> Headwear("santa", listOf(Spot(-0.98)), 0.6, 21.0, SANTA_SHAPES, emptyList())
            ArtConfig.HatStyle.B -> Headwear("santaB", listOf(Spot(-1.22)), 0.6, 21.0, SANTA_B_SHAPES, emptyList())
            ArtConfig.HatStyle.C -> Headwear("santaC", listOf(Spot(-1.2, -0.16)), 1.8, 21.0, SANTA_SHAPES, emptyList())
        }
        Item.EARS -> when (art.ears) {
            ArtConfig.EarStyle.A -> Headwear("ears", listOf(Spot(-0.92, 0.12), Spot(-0.56, 0.22)), 0.6, 40.0, EAR, EAR_LINES, Mowan.PINK)
            ArtConfig.EarStyle.B -> Headwear("earsB", listOf(Spot(-2.28, -0.12), Spot(-0.86, 0.12)), 0.6, 40.0, EAR, EAR_LINES, Mowan.PINK)
        }
        else -> error("$item 不是節日頭飾")
    }

    // ---------- 背景：斗方春聯、襪子（細繩掛在日曆的裝訂環上） ----------

    val COUPLET_C = Pt(14.0, 25.0)
    val COUPLET = handCut(pts(14.0, 13.4, 25.6, 25.0, 14.0, 36.6, 2.4, 25.0), "couplet", 0.32)

    enum class ChunStroke { H, V, PIE, NA }

    /** 「春」用墨丸同一套筆刷一筆一筆寫：三橫、撇、捺、日（以字中心為原點，縮成 0.85） */
    val CHUN: List<Pair<ChunStroke, List<Pt>>> = listOf(
        ChunStroke.H to pts(-4.0, -6.4, 0.0, -6.9, 3.9, -7.3),
        ChunStroke.H to pts(-4.4, -4.0, 0.0, -4.5, 4.3, -4.9),
        ChunStroke.H to pts(-6.8, -1.4, 0.0, -2.1, 6.6, -2.7),
        ChunStroke.PIE to pts(0.3, -8.8, 0.1, -4.6, -1.6, -0.6, -4.4, 2.6, -7.6, 4.4),
        ChunStroke.NA to pts(0.4, -1.6, 2.4, 0.6, 5.0, 2.8, 8.2, 4.0),
        ChunStroke.V to pts(-2.4, 2.2, -2.5, 4.9, -2.3, 7.6),
        ChunStroke.V to pts(-2.4, 2.1, 0.0, 1.8, 2.5, 1.6, 2.55, 4.6, 2.4, 7.6),
        ChunStroke.H to pts(-2.1, 4.8, 0.0, 4.6, 2.1, 4.5),
        ChunStroke.H to pts(-2.1, 7.5, 0.0, 7.4, 2.2, 7.3),
    ).map { (k, p) -> k to p.map { Pt(it.x * 0.85, it.y * 0.85) } }

    /** 橫起收筆頓、豎、撇往下漸細、捺漸粗後收尖 */
    fun chunWidth(kind: ChunStroke, t: Double): Double = when (kind) {
        ChunStroke.H -> 1.2 - 0.5 * sin(PI * t) + 0.45 * Math.exp(-((1 - t) / 0.1).pow(2)) + 0.25 * Math.exp(-(t / 0.08).pow(2))
        ChunStroke.V -> 0.95 - 0.2 * sin(PI * t) + 0.25 * Math.exp(-(t / 0.1).pow(2))
        ChunStroke.PIE -> 1.35 - 1.2 * t.pow(0.8) + 0.08
        ChunStroke.NA -> (0.35 + 1.75 * t.pow(1.8)) * (if (t > 0.82) maxOf(0.1, (1 - t) / 0.18) else 1.0)
    }

    val SOCK_TOP = Pt(30.0, 58.0)
    val SOCK_LEG = handCut(pts(-3.4, 4.0, 3.4, 4.0, 3.6, 11.4, 5.2, 13.2, 7.4, 14.4, 7.8, 17.0, 6.0, 18.8, 2.4, 18.8, -1.4, 17.4, -3.4, 14.4), "sock", 0.22)
    val SOCK_CUFF = handCut(pts(-4.3, 0.0, 4.3, 0.0, 4.4, 4.4, 2.2, 4.8, 0.0, 4.4, -2.2, 4.8, -4.4, 4.4), "cuff", 0.22)
    val SOCK_TOE = pts(1.2, 15.6, 3.4, 16.9, 6.2, 16.3)

    // ---------- 地面：禮物 ----------

    val GIFT_AT = Pt(94.0, 127.6)
    val GIFT = handCut(pts(-6.2, 0.0, 6.3, 0.0, 6.1, -10.2, -6.0, -10.4), "gift", 0.25)
    val GIFT_LID = pts(-6.0, -7.7, 0.0, -7.9, 6.1, -7.6)
    val GIFT_RIB = pts(0.2, -0.5, 0.0, -5.0, 0.3, -10.0)
    val GIFT_LOOP_L = pts(0.2, -10.2, -2.4, -13.4, -4.4, -12.2, -2.6, -10.6, 0.1, -10.2)
    val GIFT_LOOP_R = pts(0.3, -10.2, 2.8, -13.0, 4.6, -11.6, 2.8, -10.4, 0.3, -10.2)
    val GIFT_TAIL = pts(0.1, -10.2, -1.2, -8.4, -2.4, -7.0)

    // ---------- 手上的道具（錨點是手臂末端） ----------

    val ENVELOPE = handCut(pts(-3.3, -1.6, 3.4, -1.5, 3.3, 7.9, -3.4, 8.0), "envelope", 0.18)
    val ENVELOPE_FLAP = pts(-3.0, -1.0, -0.2, 1.9, 3.1, -1.0)
    val ENVELOPE_SEAL = Pt(0.0, 2.8)
    const val ENVELOPE_ROT = 0.08
    /** 花邊月餅（8 瓣） */
    val MOONCAKE = List(64) { i ->
        val a = i / 64.0 * TAU
        val r = 4.4 + 0.42 * cos(8 * a)
        Pt(r * cos(a), -3.6 + r * sin(a))
    }
    val MOONCAKE_INNER = circlePts(0.0, -3.6, 2.5, 28)

    // ---------- 天空：月亮 ----------

    val MOON_C = Pt(98.0, 6.0)
    const val MOON_R = 9.5
    /** 月面的淡影：(cx, cy, rx, ry, 不透明度) */
    val CRATERS = listOf(
        doubleArrayOf(95.8, 3.6, 2.6, 1.9, 0.8),
        doubleArrayOf(101.5, 8.6, 2.1, 1.5, 0.7),
        doubleArrayOf(99.7, 1.6, 1.1, 0.8, 0.6),
    )

    /** 成長焦點期的節日環境線索（原型 40%） */
    const val LOW_ENV = 0.4f

    /** 陰天、霧裡的月亮（D-32 低對比；實際值 Proposed，P6-2 美術審查） */
    const val DIM_MOON = 0.55f

    /** 小尺寸只留一個節日背景元素（原型 48 px 的 simple 規則：聖誕留禮物、拿掉掛在頭旁邊的襪子） */
    val SMALL_KEEP = mapOf(Festival.SPRING to Item.COUPLET, Festival.XMAS to Item.GIFT, Festival.MOON to Item.MOON)
}
