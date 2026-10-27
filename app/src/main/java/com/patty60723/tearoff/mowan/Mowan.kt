package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.Geometry.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 墨丸 V3 的造型與一張影格的組成。數值全部來自原型
 * docs/prototypes/src/engine.js、core.js，不在這裡調整比例或五官。
 *
 * 天氣 8 種（P4）、小尺寸簡化（P5）、節日服裝與道具的靜態畫法（P6-2）；節日演出在 P6-3。
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
    // 天氣用的顏色，在五色色票外（D-21 待決定）
    /** 陰天的雲、霧、白氣 */
    const val CLOUD = 0xFFEAE5DC.toInt()
    /** 雷雨的雲 */
    const val STORM = 0xFFCFC7BC.toInt()
    /** 雪花、積雪（紙是米白色，雪要更白、加細墨線） */
    const val SNOW = 0xFFFFFFFF.toInt()

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

    // ---------- 新天氣的環境（docs/prototypes/src/weather.js） ----------

    /** 雲的外形：三個圓弧鼓起的上緣＋平的下緣（原型 core.js 的 cloudLoop） */
    private fun cloudLoop(w: Double, h: Double): List<Pt> {
        val bumps = listOf(Triple(-w * 0.27, -h * 0.32, h * 0.48), Triple(0.0, -h * 0.5, h * 0.62), Triple(w * 0.26, -h * 0.34, h * 0.5))
        val top = (0..24).map { i ->
            val x = -w / 2 + i / 24.0 * w
            var y = 0.0
            for ((cx, cy, r) in bumps) if (abs(x - cx) < r) y = minOf(y, cy - sqrt(r * r - (x - cx) * (x - cx)))
            Pt(x, minOf(y, -0.6))
        }
        return Geometry.resample(top + Pt(w / 2, 0.0) + Pt(-w / 2, 0.0), 60)
    }

    private class Cloud(val x: Double, val y: Double, val speed: Double, val loop: List<Pt>)

    /** 天上的三朵雲（陰天會往右飄，雷雨時不動） */
    private val CLOUDS = listOf(
        Cloud(30.0, 10.0, 1.1, cloudLoop(26.0, 11.0)),
        Cloud(100.0, 16.0, 0.7, cloudLoop(30.0, 12.0)),
        Cloud(70.0, -4.0, 0.9, cloudLoop(21.0, 9.0)),
    )
    private val BREATH = cloudLoop(9.0, 5.0)
    private val BOLT = pts(2.0, 0.0, 8.0, 0.0, 4.2, 8.0, 9.5, 8.0, -1.5, 24.0, 1.6, 12.0, -3.5, 12.0)
    /** 雷雨的雨：比雨天多、斜 */
    private val HEAVY_DROPS = pts(10.0, 0.0, 20.0, 52.0, 8.0, 95.0, 26.0, 30.0, 16.0, 120.0, 112.0, 18.0, 116.0, 70.0, 108.0, 110.0, 28.0, 85.0, 4.0, 60.0, 118.0, 40.0, 100.0, 128.0, 34.0, 10.0)
    /** 雪花：(x, y, 半徑) */
    private val FLAKES = listOf(
        Triple(12.0, 0.0, 2.0), Triple(26.0, 40.0, 1.6), Triple(8.0, 78.0, 2.2), Triple(104.0, 12.0, 1.9), Triple(114.0, 58.0, 1.6),
        Triple(98.0, 96.0, 2.1), Triple(30.0, 104.0, 1.5), Triple(118.0, 120.0, 1.9), Triple(44.0, 20.0, 1.5), Triple(84.0, 30.0, 1.7),
        Triple(20.0, 128.0, 1.7), Triple(92.0, 62.0, 1.4), Triple(36.0, 70.0, 1.8), Triple(110.0, 88.0, 1.5), Triple(70.0, 6.0, 1.6), Triple(4.0, 40.0, 1.4),
    )
    /** 簡化版留下來的環境（P5-2、D-26）：一朵雲（陰天最大的、雷雨左邊那朵）、兩邊各兩條雨絲、四片大雪花 */
    private const val MAIN_CLOUD = 1
    private const val STORM_CLOUD = 0
    private val MAIN_DROPS = setOf(1, 2, 5, 6)
    private val MAIN_FLAKES = setOf(0, 2, 3, 5)

    /** D-43 候選（ArtConfig.SmallEnv.CUE）：節日背景在左邊時，雨絲、雪花只留右邊這兩條／兩片 */
    private val RIGHT_DROPS = setOf(5, 6)
    private val RIGHT_FLAKES = setOf(3, 5)
    private val SNOW_MOUND = Geometry.resample(pts(38.0, 129.5, 50.0, 126.4, 62.0, 125.8, 76.0, 126.4, 86.0, 129.5), 48)

    /** 霧帶：兩頭尖的長條 */
    private fun mistShape(w: Double, h: Double) = Geometry.resample(
        pts(-w / 2, -h / 2, w / 2, -h / 2, w / 2 + h / 2, 0.0, w / 2, h / 2, -w / 2, h / 2, -w / 2 - h / 2, 0.0), 48,
    )

    /** 汗滴：從左邊太陽穴往下滑，[u] 是 0–1 的進度 */
    private fun sweatDrop(u: Double): List<Pt> {
        val y = 38 + 8 * Keyframes.ease(u)
        return List(32) { i ->
            val a = i / 32.0 * TAU
            val pinch = max(0.0, -sin(a)).pow(3)
            Pt(35.4 + 2.3 * cos(a) * (1 - 0.85 * pinch), y + 2.3 * sin(a) - 3.4 * pinch)
        }
    }

    // ---------- 包袱 B1（第 14 頁，docs/prototypes/src/weather.js） ----------
    private object Bundle {
        /** 以布結為支點，手舉高時包袱繞這裡擺 */
        val PIVOT = Pt(48.0, 73.6)
        /** 背帶：從結往脖子後面繞，末端藏在圍巾下面 */
        val STRAP = pts(49.6, 72.6, 52.6, 71.2, 55.6, 70.6, 58.2, 70.8)
        /** 收口的布袋 */
        val SACK = Geometry.resample(pts(
            38.6, 82.6, 38.4, 78.4, 40.6, 75.2, 44.4, 73.8, 47.8, 74.6, 51.4, 73.8,
            54.2, 75.8, 55.2, 79.8, 54.2, 84.4, 50.8, 87.6, 45.4, 88.4, 40.8, 86.8,
        ), 64)
        val FOLD1 = pts(46.6, 75.4, 45.2, 78.6, 44.8, 82.0)
        val FOLD2 = pts(49.2, 75.4, 50.6, 78.4, 50.8, 81.6)
        /** 兩個布角（擺動後才取樣，跟原型一樣） */
        val EAR_L = pts(46.4, 73.4, 43.4, 69.8, 44.4, 68.6, 47.8, 72.2)
        val EAR_R = pts(49.2, 72.4, 52.0, 68.6, 53.4, 69.6, 50.4, 73.6)
        val KNOT = Geometry.ellipsePts(48.0, 73.6, 2.3, 1.7)
        /** 碎花：小、淡 */
        val DOTS = pts(42.4, 81.4, 48.6, 84.4, 51.4, 79.4)
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
        // bundle（第 14 頁）：包袱 B1；有沒有背著另外存（D-05，見 MowanSettings.bundleOn）
        "bundle" to listOf("bundle"),
    )

    fun partsOf(unlocks: Set<String>): Set<String> = unlocks.flatMapTo(HashSet()) { PARTS_OF_UNLOCK[it].orEmpty() }

    // ---------- 一張影格 ----------

    /**
     * 天氣場景。晴、雨已上線；熱、陰、霧、雷雨、冷、雪是 Phase 2B 定稿（W3），
     * 目前只在除錯選單看得到，故事卡還是只用晴、雨（見 [MowanSettings.weatherOf]）。
     */
    enum class Weather { NONE, SUNNY, RAINY, HOT, CLOUDY, FOG, THUNDER, COLD, SNOW }

    /** 原型的圖層順序；同一層裡按加入順序畫 */
    enum class Layer { SKY, CLOUDS, RAIN, BACKDROP, GROUND, BUNDLE, SHAFT, BODY, SCARF, HEAD, HAND, CANOPY, FRONT }

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
        /** 這一筆屬於哪個部位或道具（例如 "armR"、"eyes"、"umbrella"、"ground"），測試用來檢查成長階段 */
        val part: String = "",
    )

    private data class Limb(val name: String, val pts: List<Pt>, val w0: Double, val w1: Double)

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
     * @param sinceStrikeMs 雷雨：打雷後經過的毫秒數，null 表示沒有打雷
     * @param still 關閉動畫時的靜止畫面（雪花不放在頭和臉上）
     * @param bundleOn 包袱是否背著（裝備狀態，見 [MowanSettings.bundleOn]；第 14 頁前不管這個值都不畫）
     * @param detail 小尺寸的簡化畫法（P5-2），預設是完整版
     * @param festival 今天的節日（P6-2）：節日站姿、服裝與道具，見 [FestivalScene]；null 表示沒有節日
     * @param art 還沒定案的美術候選（頭飾、耳朵、雨傘大小、小尺寸的背景），見 [ArtConfig]
     * @param performance 節日演出（P6-3），只在有節日時有用
     */
    fun frame(
        parts: Set<String>,
        variant: Int,
        hatOn: Boolean = true,
        weather: Weather = Weather.NONE,
        clockMs: Double = 0.0,
        rainStep: Int = 0,
        umbrellaScale: Double = 1.0,
        sinceStrikeMs: Double? = null,
        still: Boolean = false,
        bundleOn: Boolean = true,
        detail: Detail = Detail.FULL,
        festival: Festival? = null,
        art: ArtConfig = ArtConfig.DEFAULT,
        performance: FestivalActions.Performance? = null,
    ): Frame {
        if (festival != null) {
            val scene = FestivalScene.resolve(parts, festival, weather, hatOn, bundleOn, art)
            val (pose, fx) = Performances.pose(scene, clockMs, performance)
            return render(parts, variant, hatOn, pose, weather, rainStep, art.umbrellaScale, clockMs, still, bundleOn, detail, scene, art, fx)
        }
        val caps = Capabilities.of(parts)
        val rainy = weather == Weather.RAINY
        // 雨傘是功能道具，要有右手才拿得了（Confirmed）
        val umbrella = rainy && caps.holdR
        var p = when (weather) {
            Weather.SUNNY -> Poses.sunny(clockMs)
            Weather.HOT -> Poses.hot(clockMs, caps)
            Weather.CLOUDY -> Poses.cloudy(clockMs, caps)
            Weather.FOG -> Poses.fog(clockMs, caps)
            Weather.THUNDER -> Poses.thunder(clockMs, caps, sinceStrikeMs)
            Weather.COLD -> Poses.cold(clockMs, caps)
            Weather.SNOW -> Poses.snow(clockMs, caps)
            else -> if (umbrella) Poses.rainy(clockMs) else Poses.NORMAL
        }
        if (rainy && !umbrella) p = p.copy(face = Face.RAINY, flick = Flick.DROOPED)
        return render(parts, variant, hatOn, p, weather, rainStep, umbrellaScale, clockMs, still, bundleOn, detail)
    }

    /**
     * 把一個姿勢畫成一張影格。姿勢裡有、但還沒解鎖的部位一律不畫；雨傘要姿勢有握點而且有右手才畫。
     * 天氣只決定環境（太陽、雨、地面）。
     */
    internal fun render(
        parts: Set<String>,
        variant: Int,
        hatOn: Boolean,
        pose: Pose,
        weather: Weather = Weather.NONE,
        rainStep: Int = 0,
        umbrellaScale: Double = 1.0,
        clockMs: Double = 0.0,
        still: Boolean = false,
        bundleOn: Boolean = true,
        detail: Detail = Detail.FULL,
        fest: FestivalScene.Result? = null,
        art: ArtConfig = ArtConfig.DEFAULT,
        fx: Performances.Fx = Performances.Fx(),
    ): Frame {
        val ops = ArrayList<Op>()
        // 簡化版（P5-2）：拿掉細節、把留下來的線加粗到下限；完整版下面的 floor 都不會改變任何寬度
        val simple = detail.simplified
        val main = detail.mainInk
        val accent = detail.accentInk
        val v = variant
        val caps = Capabilities.of(parts)
        val rainy = weather == Weather.RAINY
        val umbrella = pose.hand != null && caps.holdR
        var p = pose
        // 表情要有眼睛
        if (!caps.expression) p = p.copy(face = Face.NORMAL)
        if (!caps.stand) p = p.shifted(GROUND_DY)
        // 成長焦點期（還沒有手）：天空降到 55%
        val skyOp = if (caps.growthFocus) 0.55f else 1f

        // ---- 節日（P6-2）：節日環境照 FestivalScene 的插槽畫 ----
        // 成長焦點期的節日線索降到 40%
        val envOp = (if (fest != null && fest.low) Props.LOW_ENV else 1f) * fx.cueIn.toFloat()
        // 小尺寸最多一個主要背景裝飾（D-16 方向）：每個節日只留一個背景元素；跟天氣環境怎麼取捨是候選（ArtConfig.smallEnv）
        val festSmall = simple && fest?.festival != null
        val smallKeep = fest?.festival?.let { Props.SMALL_KEEP[it] }
        // 48dp 極簡候選：節日背景與天氣環境都不畫
        val minimal = festSmall && art.smallEnv == ArtConfig.SmallEnv.MINIMAL
        fun festEnv(item: FestivalScene.Item): Boolean {
            if (fest == null || fest[item.slot!!] != item) return false
            if (!festSmall) return true
            return art.smallEnv != ArtConfig.SmallEnv.WEATHER && !minimal && item == smallKeep
        }
        val festBackdropSmall = festSmall && smallKeep != null && festEnv(smallKeep)
        // 天氣環境（太陽、雲、雨、雪、霧、熱氣）；雨傘是功能道具，不受影響
        val weatherEnv = !minimal && !(festBackdropSmall && art.smallEnv == ArtConfig.SmallEnv.FESTIVAL)
        // D-43 候選：節日背景＋一個不衝突的天氣線索
        val cue = festBackdropSmall && art.smallEnv == ArtConfig.SmallEnv.CUE
        val cloudOff = cue && (smallKeep == FestivalScene.Item.MOON || (weather == Weather.THUNDER && smallKeep == FestivalScene.Item.COUPLET))
        // 太陽：天空有月亮時不畫（D-32）
        val sunOn = weatherEnv && (fest == null || fest.shown(FestivalScene.Item.SUN))

        // 接下來畫的筆畫屬於哪個部位
        var owner = ""
        fun add(layer: Layer, path: PathData, fill: Int?, alpha: Float = 1f, evenOdd: Boolean = false, gradient: Gradient? = null) {
            ops += Op(layer, path, fill, alpha, evenOdd, gradient, part = owner)
        }
        fun ringPath(layer: Layer, pts: List<Pt>, fill: Int, part: String, w: Double, amp: Double, alpha: Float = 1f, floor: Double = 0.0): Geometry.Ring {
            val ph = Rng.phases(part, v)
            val r = Geometry.ring(pts, { a -> max(w * (1 + 0.12 * sin(2 * a + ph[3])), floor) }, { a -> Geometry.loopNoise(ph, a, amp) })
            add(layer, r.fill, fill, alpha)
            add(layer, r.stroke, INK, alpha, evenOdd = true)
            return r
        }
        fun strokePath(layer: Layer, pts: List<Pt>, w0: Double, w1: Double, part: String, amp: Double, color: Int = INK, per: Int = 6, alpha: Float = 1f, tip: Boolean = false, floor: Double = 0.0) {
            val ph = Rng.phases(part, v)
            add(layer, Geometry.brush(Geometry.catmull(pts, per), { t -> max(lerp(w0, w1, t) * Geometry.widthVar(ph, t, 0.1), floor) }, { t -> Geometry.openNoise(ph, t, amp, tip) }), color, alpha)
        }
        /** Paint 描邊的寬度加到下限 */
        fun sw(w: Double, floor: Double) = max(w, floor)
        fun mistBand(layer: Layer, y: Double, w: Double, h: Double, dx: Double, part: String, alpha: Float) {
            val ph = Rng.phases(part, v)
            val r = Geometry.ring(Geometry.place(mistShape(w, h), Geometry.Placement(60 + dx, y)), { 0.01 }, { a -> Geometry.loopNoise(ph, a, 0.6) })
            add(layer, r.fill, CLOUD, alpha)
        }
        fun limb(part: String, pts: List<Pt>, w0: Double, w1: Double, amp: Double): Geometry.Stroke {
            val ph = Rng.phases(part, v)
            return Geometry.stroke(Geometry.catmull(pts, 9), { t -> max((w0 + (w1 - w0) * t) * Geometry.widthVar(ph, t, 0.13), main) }, { t -> Geometry.openNoise(ph, t, amp, false) })
        }

        // ---- 天空 ----
        if (weather == Weather.HOT && sunOn) {
            // 熱：太陽比晴天大一圈
            owner = "sun"
            add(Layer.SKY, PathData.ellipse(99.0, 4.0, 10.5, 10.5), PINK, 0.85f * skyOp)
            ops += Op(Layer.SKY, PathData.ellipse(99.0, 4.0, 14.5, 14.5), null, skyOp, stroke = PINK, strokeWidth = sw(1.2, accent), part = owner)
        }
        if ((weather == Weather.CLOUDY || weather == Weather.THUNDER) && weatherEnv && !cloudOff) {
            // 陰天的雲慢慢往右飄；雷雨的雲比較深、不動、高一點
            val storm = weather == Weather.THUNDER
            owner = "clouds"
            CLOUDS.forEachIndexed { i, c ->
                // 簡化：只留一朵。陰天留最大的（右邊）；雷雨右邊那朵會被傘擋住，留左邊露在傘外面的
                if (simple && i != (if (storm) STORM_CLOUD else MAIN_CLOUD)) return@forEachIndexed
                val x = if (storm) c.x else ((c.x + clockMs / 1000 * c.speed + 30) % 170) - 30
                val loop = Geometry.place(c.loop, Geometry.Placement(x, c.y - if (storm) 2 else 0))
                ringPath(Layer.CLOUDS, loop, if (storm) STORM else CLOUD, "cloud$i", 1.5, 0.5, if (caps.growthFocus) 0.7f else 1f, floor = accent)
            }
        }
        if (weather == Weather.THUNDER && p.effects.bolt > 0 && !simple && weatherEnv) {
            owner = "bolt"
            ringPath(Layer.CLOUDS, Geometry.resample(Geometry.place(BOLT, Geometry.Placement(96.0, 14.0)), 40), PINK, "bolt", 1.1, 0.15, p.effects.bolt.toFloat())
        }
        if (weather == Weather.SUNNY && sunOn) {
            owner = "sun"
            add(Layer.SKY, PathData.ellipse(99.0, 4.0, 8.5, 8.5), PINK, 0.75f * skyOp)
            ops += Op(Layer.SKY, PathData.ellipse(99.0, 4.0, 12.0, 12.0), null, skyOp, stroke = PINK, strokeWidth = sw(1.1, accent), part = owner)
        }
        // ---- 月亮（中秋）：不畫外圈光環（那是太陽的語彙）；陰天、霧降低對比 ----
        if (festEnv(FestivalScene.Item.MOON)) {
            owner = "moon"
            val dim = if (weather == Weather.CLOUDY || weather == Weather.FOG) Props.DIM_MOON else 1f
            // 舉月餅時才有一層很淡的月光
            if (fx.glow > 0) add(Layer.SKY, PathData.ellipse(Props.MOON_C.x, Props.MOON_C.y, 12.5, 12.5), Props.MOON, 0.45f * fx.glow.toFloat() * envOp * dim)
            ringPath(Layer.SKY, Props.circlePts(Props.MOON_C.x, Props.MOON_C.y, Props.MOON_R), Props.MOON, "moon", 1.2, 0.3, envOp * dim, floor = accent)
            // 月面的淡影，讓它一看就是月亮
            if (!simple && envOp > 0.5f) for (c in Props.CRATERS) add(Layer.SKY, PathData.ellipse(c[0], c[1], c[2], c[3]), Props.CRATER, c[4].toFloat() * dim)
        }
        // ---- 雨（8 FPS） ----
        if (rainy && weatherEnv) for ((i, d) in DROPS.withIndex()) {
            // 簡化：只留兩邊各兩條
            if (simple && i !in (if (cue) RIGHT_DROPS else MAIN_DROPS)) continue
            val y = ((d.y + rainStep * 7) % 150) - 20
            val path = PathData.Builder().move(Pt(d.x, y)).line(Pt(d.x - 1.6, y + 7)).build()
            ops += Op(Layer.RAIN, path, null, stroke = SAGE, strokeWidth = sw(1.5, accent), roundCap = true, part = "rain")
        }

        // 雷雨：簡化時環境只留烏雲
        if (weather == Weather.THUNDER && !simple && weatherEnv) for (d in HEAVY_DROPS) {
            val y = ((d.y + rainStep * 9) % 150) - 20
            val path = PathData.Builder().move(Pt(d.x, y)).line(Pt(d.x - 2.6, y + 8)).build()
            ops += Op(Layer.RAIN, path, null, stroke = SAGE, strokeWidth = 1.6, roundCap = true, part = "rain")
        }
        if (weather == Weather.SNOW && weatherEnv) FLAKES.forEachIndexed { i, (x0, y0, r0) ->
            // 簡化：只留四片大的，半徑至少 1.2 px
            if (simple && i !in (if (cue) RIGHT_FLAKES else MAIN_FLAKES)) return@forEachIndexed
            val r = max(r0, 0.6 * accent * 2)
            val y = ((y0 + clockMs / 1000 * 7) % 150) - 20
            val x = x0 + 2.6 * sin(clockMs / 1100 + i)
            // 靜止畫面的雪花會一直停在那裡，所以不放在頭和臉的範圍裡
            val dy = if (caps.stand) 0.0 else GROUND_DY
            if (still && x > 32 && x < 90 && y > 18 + dy && y < 72 + dy) return@forEachIndexed
            ops += Op(Layer.RAIN, PathData.ellipse(x, y, r, r), SNOW, stroke = INK, strokeWidth = sw(0.6, 0.6 * accent), part = "snow")
        }
        // ---- 霧（後面兩層） ----
        if (weather == Weather.FOG && weatherEnv) {
            owner = "mist"
            // 簡化：只留腳邊那條最長的
            if (!simple) mistBand(Layer.BACKDROP, 22.0, 70.0, 7.0, 18 * sin(clockMs / 4200), "mist0", 0.8f)
            mistBand(Layer.BACKDROP, 112.0, 120.0, 9.0, 14 * sin(clockMs / 3600 + 1), "mist1", 0.85f)
        }

        // ---- 地面的影子（雨天是一灘水、雪天是積雪） ----
        owner = "ground"
        // 簡化：地面影子、水窪、積雪都不畫
        if (!simple) when (weather) {
            Weather.RAINY -> add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 20.0, 2.6), SAGE, 0.28f)
            Weather.THUNDER -> add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 22.0, 2.8), SAGE, 0.3f)
            Weather.SNOW -> ringPath(Layer.GROUND, SNOW_MOUND, SNOW, "snowMound", 0.9, 0.2)
            Weather.COLD -> add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 18.0, 2.4), INK, 0.07f)
            else -> add(Layer.GROUND, PathData.ellipse(62.0, 128.6, 16.0, 2.2), INK, 0.07f)
        }
        // ---- 節日背景：斗方春聯、襪子用細繩掛在頁面上緣的日曆裝訂環上 ----
        fun hangFrom(x: Double, yTop: Double, part: String) {
            val a = maxOf(envOp, 0.35f)
            ops += Op(Layer.BACKDROP, PathData.ellipse(x, -17.2, 1.5, 1.5), null, a, stroke = INK, strokeWidth = sw(0.9, accent), part = owner)
            strokePath(Layer.BACKDROP, pts(x, -15.7, x + 0.3, (yTop - 15.7) / 2, x, yTop), 0.7, 0.7, part, 0.08, alpha = a, floor = accent)
        }
        if (festEnv(FestivalScene.Item.COUPLET)) {
            owner = "couplet"
            hangFrom(Props.COUPLET_C.x, 14.5, "cplString")
            ringPath(Layer.BACKDROP, Props.COUPLET, Props.RED, "couplet", Props.OW, 0.35, 0.85f * envOp, floor = accent)
            // 「春」字：小尺寸時拿掉（原型 48 px 也不寫字）
            if (!simple) Props.CHUN.forEachIndexed { i, (kind, stroke) ->
                val ph = Rng.phases("chun$i", v)
                val world = stroke.map { Pt(Props.COUPLET_C.x + it.x, Props.COUPLET_C.y + it.y) }
                add(Layer.BACKDROP, Geometry.brush(
                    Geometry.catmull(world, 7),
                    { t -> Props.chunWidth(kind, t) * Geometry.widthVar(ph, t, 0.08) },
                    { t -> Geometry.openNoise(ph, t, 0.12, false) },
                ), INK, maxOf(envOp, 0.3f))
            }
        }
        if (festEnv(FestivalScene.Item.SOCK)) {
            owner = "sock"
            val top = Pt(Props.SOCK_TOP.x, Props.SOCK_TOP.y + fx.sockDy)
            val pl = Geometry.Placement(top.x, top.y, Math.toRadians(fx.sockSwing))
            hangFrom(top.x, top.y, "string")
            ringPath(Layer.BACKDROP, Geometry.place(Props.SOCK_LEG, pl), Props.RED, "sock", Props.OW, 0.3, envOp, floor = accent)
            ringPath(Layer.BACKDROP, Geometry.place(Props.SOCK_CUFF, pl), IVORY, "cuff", Props.OW, 0.25, envOp, floor = accent)
            if (!simple) strokePath(Layer.BACKDROP, Geometry.place(Props.SOCK_TOE, pl), 0.6, 0.45, "sockToe", 0.06, alpha = 0.55f * envOp)
        }
        // ---- 節日的地面：禮物（有影子） ----
        if (festEnv(FestivalScene.Item.GIFT)) {
            owner = "gift"
            val g = Props.GIFT_AT
            fun at(pts: List<Pt>) = pts.map { Pt(g.x + it.x, g.y + it.y) }
            if (!simple) add(Layer.GROUND, PathData.ellipse(g.x, g.y + 0.6, 9.0, 1.6), INK, 0.08f)
            ringPath(Layer.GROUND, at(Props.GIFT), SAGE, "gift", Props.OW, 0.3, floor = accent)
            if (!simple) {
                // 細一點的緞帶、像手綁的蝴蝶結（兩個線圈＋小結），蓋子有一道淡淡的接縫
                strokePath(Layer.GROUND, at(Props.GIFT_LID), 0.55, 0.55, "lid", 0.06, alpha = 0.5f)
                strokePath(Layer.GROUND, at(Props.GIFT_RIB), 0.95, 0.95, "rib1", 0.08, Props.RED)
                strokePath(Layer.GROUND, at(Props.GIFT_LOOP_L), 0.85, 0.7, "loopL", 0.08, Props.RED, per = 5)
                strokePath(Layer.GROUND, at(Props.GIFT_LOOP_R), 0.85, 0.7, "loopR", 0.08, Props.RED, per = 5)
                strokePath(Layer.GROUND, at(Props.GIFT_TAIL), 0.8, 0.35, "tailL", 0.08, Props.RED, per = 5, tip = true)
            } else {
                // 小尺寸（P6-2 審核 D-30）：只留一條紅緞帶和一個小結，不然只剩一個灰綠方塊
                strokePath(Layer.GROUND, at(Props.GIFT_RIB), 0.95, 0.95, "rib1", 0.08, Props.RED, floor = accent)
                add(Layer.GROUND, PathData.ellipse(g.x, g.y - 10.6, max(1.6, 0.8 * accent), max(1.0, 0.6 * accent)), Props.RED)
            }
        }
        if (weather == Weather.HOT && !simple && weatherEnv) {
            // 地面上的熱氣：三條往上飄的波浪線
            owner = "heat"
            listOf(30.0 to 0.0, 92.0 to 0.4, 102.0 to 0.75).forEachIndexed { i, (x, phase) ->
                val u = ((clockMs / 1800) + phase) % 1
                val y0 = 124 - 14 * u
                val wave = List(6) { j -> Pt(x + 1.6 * sin(j * 1.4 + clockMs / 300 + i), y0 - j * 2.2) }
                strokePath(Layer.GROUND, wave, 0.9, 0.5, "heat$i", 0.05, PINK, per = 5, alpha = (sin(PI * u) * 0.9).toFloat())
            }
        }

        // ---- 包袱 B1：背在左肩後面（在身體、圍巾、頭、傘柄之前畫） ----
        if ("bundle" in parts && bundleOn) {
            owner = "bundle"
            // 跟著上半身：身體上段左右移、發抖時跟著頭晃（用還沒落地前的姿勢算，跟原型一樣）
            val bx = (pose.body[1].x - 61.4) * 0.8 + pose.headDx * 0.6
            // 左手舉得越高，肩膀越往上：包袱被背帶提起一點，下緣往身體擺過來
            val raise = if (caps.holdL) Geometry.clamp01((96 - pose.armL.last().y) / 50) else 0.0
            val lift = -1.6 * raise
            val tilt = Math.toRadians(-6 * raise)
            val groundDy = if (caps.stand) 0.0 else GROUND_DY
            fun at(pts: List<Pt>) = pts.map { q ->
                val dx = q.x - Bundle.PIVOT.x
                val dy = q.y - Bundle.PIVOT.y
                Pt(Bundle.PIVOT.x + dx * cos(tilt) - dy * sin(tilt) + bx, Bundle.PIVOT.y + dx * sin(tilt) + dy * cos(tilt) + groundDy + lift)
            }
            // 簡化：只留布包、兩個布角和結（背帶、皺褶、碎花拿掉）
            if (!simple) strokePath(Layer.BUNDLE, at(Bundle.STRAP), 1.3, 1.1, "strap", 0.06)
            ringPath(Layer.BUNDLE, at(Bundle.SACK), SAGE, "bundle", 1.3, 0.3, floor = accent)
            if (!simple) {
                strokePath(Layer.BUNDLE, at(Bundle.FOLD1), 0.7, 0.4, "fold1", 0.05, per = 5, alpha = 0.4f, tip = true)
                strokePath(Layer.BUNDLE, at(Bundle.FOLD2), 0.7, 0.4, "fold2", 0.05, per = 5, alpha = 0.4f, tip = true)
            }
            ringPath(Layer.BUNDLE, Geometry.resample(at(Bundle.EAR_L), 32), SAGE, "earL", 1.0, 0.12, floor = accent)
            ringPath(Layer.BUNDLE, Geometry.resample(at(Bundle.EAR_R), 32), SAGE, "earR", 1.0, 0.12, floor = accent)
            ringPath(Layer.BUNDLE, at(Bundle.KNOT), SAGE, "knot", 1.1, 0.1, floor = accent)
            if (!simple) for (d in at(Bundle.DOTS)) add(Layer.BUNDLE, PathData.ellipse(d.x, d.y, 0.55, 0.55), IVORY, 0.55f)
        }

        // ---- 傘柄（在身體後面） ----
        val hand = p.hand
        if (umbrella && hand != null) {
            val pl = Geometry.Placement(hand.x, hand.y, Math.toRadians(p.umbrellaAngle), umbrellaScale)
            val shaft = Geometry.place(pts(0.0, -64.0, 0.0, -30.0, 0.0, 7.0), pl)
            val crook = Geometry.place(pts(0.0, 7.0, -1.5, 11.4, -5.0, 11.0), pl)
            val path = PathData.Builder()
                .add(Geometry.brush(Geometry.catmull(shaft, 6), { max(2.2, main) }, { 0.0 }))
                .add(Geometry.brush(Geometry.catmull(crook, 6), { max(2.2, main) }, { 0.0 }))
                .build()
            owner = "umbrella"
            add(Layer.SHAFT, path, INK)
        }

        // ---- 身體 ----
        for ((name, w0, w1) in listOf(Triple("legL", 3.1, 2.3), Triple("legR", 3.0, 2.2))) {
            if (name !in parts) continue
            val lg = limb(name, if (name == "legL") p.legL else p.legR, w0, w1, 0.45)
            owner = name
            add(Layer.BODY, Geometry.outline(lg), INK)
            if ("redToes" in parts) {
                // 腳尖沾到紅墨：最後 7 個點，寬度放大一點，越往腳尖越濃
                val j0 = lg.n - 7
                val a = lg.disp[j0]
                val b = lg.disp[lg.n - 1]
                val stops = listOf(Triple(0f, TERRA, 0f), Triple(0.5f, TERRA, 0.7f), Triple(1f, TERRA, 0.95f))
                owner = "redToes"
                add(Layer.BODY, Geometry.outline(lg, j0, 1.12), null, gradient = Gradient(a.x, a.y, b.x, b.y, stops))
            }
        }
        for ((name, pts, w0, w1) in listOf(Limb("body", p.body, 3.1, 2.8), Limb("armL", p.armL, 2.9, 2.1), Limb("armR", p.armR, 2.9, 2.1))) {
            if (name !in parts) continue
            owner = name
            add(Layer.BODY, Geometry.outline(limb(name, pts, w0, w1, if (name == "body") 0.5 else 0.45)), INK)
        }

        // ---- 圍巾 ----
        if ("scarf" in parts) {
            owner = "scarf"
            val pb = Rng.phases("band", v)
            val pt = Rng.phases("tail", v)
            val ps = Rng.phases("split", v)
            // 圍巾帶子跟著頭左右晃；冷、雪時拉高一點、寬一點
            val jx = p.headDx
            // 冷、雪時拉高；節日演出低頭時跟著脖子往下
            val up = (if (p.effects.scarfUp) -1.0 else 0.0) + p.neckDy
            val wide = if (p.effects.scarfUp) 1.12 else 1.0
            add(Layer.SCARF, Geometry.brush(
                Geometry.catmull(pts(51.0 + jx, 68.6 + up, 55.5 + jx, 71.3 + up, 64.0 + jx, 71.4 + up, 70.0 + jx, 67.6 + up), 8),
                { t -> max((4.4 + 0.6 * sin(PI * t)) * wide * Geometry.widthVar(pb, t, 0.12), main) },
                { t -> Geometry.openNoise(pb, t, 0.4, false) },
            ), TERRA)
            add(Layer.SCARF, Geometry.brush(
                Geometry.catmull(p.tail, 9),
                { t -> max((3.9 - 1.9 * t) * Geometry.widthVar(pt, t, 0.14), main) },
                { t -> Geometry.openNoise(pt, t, 0.7, true) },
            ), TERRA)
            val end = p.tail.last()
            val mid = p.tail[2]
            // 尾端分岔是細節，簡化時不畫
            if (!simple) add(Layer.SCARF, Geometry.brush(
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
                { a -> max(2.4 * (1 + 0.13 * (0.7 * sin(2 * a + phd[3]) + 0.3 * sin(3 * a + phd[4]))) + 1.1 * max(0.0, cos(a - heavy)).pow(3), main) },
                { a -> Geometry.loopNoise(phd, a, 0.75) },
            )
            owner = "head"
            add(Layer.HEAD, head.fill, PAPER)
            add(Layer.HEAD, head.stroke, INK, evenOdd = true)

            // 視線：眼睛跟著移，閉眼線、眉毛、嘴巴移一半
            val gz = p.gaze
            fun faceBrush(part: String, pts: List<Pt>, w: Double) {
                val ph = Rng.phases(part, v)
                add(Layer.HEAD, Geometry.brush(
                    Geometry.catmull(pts.map { Pt(it.x + gz.x * 0.5, it.y + gz.y * 0.5) }, 6),
                    { t -> max(w * (0.85 + 0.15 * sin(PI * t)) * Geometry.widthVar(ph, t, 0.08), main) },
                    { t -> Geometry.openNoise(ph, t, 0.12, false) },
                ), INK)
            }
            if ("blush" in parts && !simple) {
                val op = when (p.face) {
                    Face.SUNNY -> 1f
                    Face.RAINY -> 0.55f
                    Face.HOT, Face.COLD -> 0.95f
                    Face.STARTLE, Face.FOG -> 0.7f
                    Face.NORMAL -> 0.85f
                }
                owner = "blush"
                add(Layer.HEAD, PathData.ellipse(47.0, 52.8, 3.2, 1.6), PINK, op)
                add(Layer.HEAD, PathData.ellipse(72.5, 52.3, 3.2, 1.6), PINK, op)
            }
            if ("eyes" in parts) {
                owner = "eyes"
                val pe = Rng.phases("eyes", v)
                // 簡化：眼睛至少跟主要墨線一樣寬
                val r = max(1.95 * (1 + 0.05 * sin(pe[0])), main / 2)
                val eyes = pts(52.0, 46.8, 67.0, 46.3)
                when (p.face) {
                    Face.SUNNY -> {
                        faceBrush("lidL", pts(49.6, 47.1, 52.0, 48.1, 54.4, 47.1), 1.7)
                        faceBrush("lidR", pts(64.6, 46.6, 67.0, 47.6, 69.4, 46.6), 1.7)
                    }
                    Face.HOT -> {
                        // 瞇成兩條線
                        faceBrush("lidL", pts(50.0, 47.6, 52.0, 47.9, 54.2, 47.5), 1.6)
                        faceBrush("lidR", pts(64.9, 47.1, 67.0, 47.4, 69.2, 47.0), 1.6)
                    }
                    Face.COLD -> {
                        // 輕輕閉眼
                        faceBrush("lidL", pts(49.8, 47.4, 52.0, 48.0, 54.2, 47.3), 1.6)
                        faceBrush("lidR", pts(64.8, 46.9, 67.0, 47.5, 69.2, 46.8), 1.6)
                    }
                    Face.FOG -> eyes.forEachIndexed { i, e ->
                        // 瞇眼：扁的橢圓
                        add(Layer.HEAD, PathData.ellipse(e.x + gz.x + 0.1 * sin(pe[1 + i]), e.y + gz.y + 0.4, max(2.3, main / 2), max(1.0, main / 2)), INK)
                    }
                    Face.STARTLE -> eyes.forEachIndexed { i, e ->
                        // 嚇一跳：眼睛變大、往上一點
                        add(Layer.HEAD, PathData.ellipse(e.x + 0.1 * sin(pe[1 + i]), e.y - 0.4, r * 1.3, r * 1.3), INK)
                    }
                    Face.NORMAL, Face.RAINY -> {
                        val at = if (p.face == Face.RAINY) pts(52.4, 47.0, 67.2, 46.6) else eyes
                        at.forEachIndexed { i, e -> add(Layer.HEAD, PathData.ellipse(e.x + gz.x + 0.1 * sin(pe[1 + i]), e.y + gz.y, r, r), INK) }
                    }
                }
                if ((p.face == Face.RAINY || p.face == Face.COLD || p.face == Face.STARTLE) && !simple) {
                    val bw = if (p.face == Face.COLD) 1.2 else 1.5
                    faceBrush("browL", pts(49.5, 43.6, 51.8, 42.9, 54.0, 42.1), bw)
                    faceBrush("browR", pts(69.8, 43.1, 67.6, 42.4, 65.4, 41.6), bw)
                }
            }
            if ("mouth" in parts && detail.mouth) {
                owner = "mouth"
                if (p.face == Face.STARTLE) {
                    // 嚇一跳：張開的「o」嘴
                    val ph = Rng.phases("mouthO", v)
                    val ring = Geometry.ring(Geometry.ellipsePts(60 + gz.x * 0.5, 55.6, 1.3, 1.5), { max(1.3, main) }, { a -> Geometry.loopNoise(ph, a, 0.05) })
                    add(Layer.HEAD, ring.stroke, INK, evenOdd = true)
                } else {
                    val mouth = when (p.face) {
                        Face.RAINY -> pts(57.6, 55.0, 60.0, 54.2, 62.4, 55.0)
                        Face.SUNNY -> pts(57.5, 54.6, 60.0, 56.6, 62.5, 54.4)
                        Face.HOT -> pts(57.8, 55.3, 60.0, 55.9, 62.2, 55.2)
                        Face.COLD -> pts(57.6, 55.2, 58.8, 54.7, 60.0, 55.2, 61.2, 54.7, 62.4, 55.2)
                        Face.FOG -> pts(57.8, 55.5, 60.0, 55.8, 62.2, 55.3)
                        else -> pts(57.4, 54.6, 60.0, 56.2, 62.6, 54.4)
                    }
                    faceBrush("mouth", mouth, if (p.face == Face.COLD) 1.5 else 1.9)
                }
            }

            // 節日頭飾（P6-2）：頭頂今天給節日頭飾時，紙帽暫時不畫（D-05）
            val top = fest?.get(FestivalScene.Slot.HEAD_TOP)
            if (top != null && top != FestivalScene.Item.PAPER_HAT) {
                val hw = Props.headwear(top, art)
                owner = top.name.lowercase()
                var i = 0
                for (spot in hw.spots) {
                    val fit = { pts: List<Pt> ->
                        if (hw.multi) Geometry.place(Geometry.bend(pts, hw.bendR), Geometry.headPlace(spot.theta, hw.sink, head.points, spot.tilt))
                        else Geometry.conform(pts, spot.theta, hw.sink, head.points, spot.tilt, hw.bendR)
                    }
                    for (shape in hw.shapes) {
                        // 耳翼這類細節，簡化時不畫
                        if (!shape.detail || !simple) ringPath(Layer.HEAD, fit(shape.pts), shape.fill, "${hw.id}$i", 1.35, 0.4, floor = accent)
                        i++
                    }
                    for (line in hw.lines) {
                        if (!simple) strokePath(Layer.HEAD, fit(line), 1.0, 0.8, "${hw.id}l$i", 0.12, hw.lineColor)
                        i++
                    }
                }
            }
            // 紙帽：拿到（第 11 頁）而且戴著才畫；有節日時照 FestivalScene（節日頭飾優先）
            val paperHat = if (fest != null) top == FestivalScene.Item.PAPER_HAT else caps.paperHat && hatOn
            if (paperHat) {
                owner = "paperHat"
                val fit = { pts: List<Pt> -> Geometry.conform(pts, PaperHat.THETA, PaperHat.SINK, head.points, 0.0, PaperHat.BEND_R) }
                ringPath(Layer.HEAD, fit(PaperHat.shape), IVORY, "paperHat0", 1.35, 0.4, floor = accent)
                // 摺線是細節，簡化時不畫
                if (!simple) PaperHat.lines.forEachIndexed { i, line -> strokePath(Layer.HEAD, fit(line), 1.0, 0.8, "paperHatl${i + 1}", 0.12) }
            }

            // 一筆：永遠在最前面
            if ("flick" in parts) {
                owner = "flick"
                val root = head.points[ROOT_INDEX]
                val pf = Rng.phases("flick", v)
                add(Layer.HEAD, Geometry.brush(
                    Geometry.catmull(p.flick.shape.map { Pt(root.x + it.x, root.y + it.y + 1.2) }, 9),
                    { t -> max((0.35 + 3.6 * (1 - t).pow(0.85)) * Geometry.widthVar(pf, t, 0.18), main) },
                    { t -> Geometry.openNoise(pf, t, 1.4, true) },
                ), INK)
            }
            // 汗滴：要有眼睛（表情的一部分）
            val sweat = p.effects.sweat
            if (sweat != null && "eyes" in parts && !simple) {
                owner = "sweat"
                val alpha = minOf(1.0, 1.6 * sin(PI * Geometry.clamp01(sweat * 1.1)))
                ringPath(Layer.HEAD, sweatDrop(sweat), SAGE, "sweat", 0.9, 0.08, alpha.toFloat())
            }
        }

        // ---- 手上的節日道具（錨點是手臂末端，左手的角度相反） ----
        if (fest != null) for ((slot, arm) in listOf(FestivalScene.Slot.HAND_R to "armR", FestivalScene.Slot.HAND_L to "armL")) {
            val item = fest[slot] ?: continue
            if (item == FestivalScene.Item.UMBRELLA || arm !in parts) continue
            val h = (if (arm == "armR") p.armR else p.armL).last()
            val sign = if (slot == FestivalScene.Slot.HAND_L) -1 else 1
            if (item == FestivalScene.Item.ENVELOPE) {
                owner = "envelope"
                val pl = Geometry.Placement(h.x, h.y, Props.ENVELOPE_ROT * sign)
                ringPath(Layer.HAND, Geometry.place(Props.ENVELOPE, pl), Props.RED, "env", Props.OW, 0.22, floor = accent)
                if (!simple) {
                    strokePath(Layer.HAND, Geometry.place(Props.ENVELOPE_FLAP, pl), 0.6, 0.55, "envFlap", 0.06, alpha = 0.7f)
                    val seal = Geometry.place(listOf(Props.ENVELOPE_SEAL), pl)[0]
                    add(Layer.HAND, PathData.ellipse(seal.x, seal.y, 0.5, 0.5), IVORY, 0.75f)
                }
            } else if (item == FestivalScene.Item.MOONCAKE) {
                owner = "mooncake"
                val pl = Geometry.Placement(h.x, h.y)
                ringPath(Layer.HAND, Geometry.place(Props.MOONCAKE, pl), Props.CAKE, "cake", Props.OW, 0.2, floor = accent)
                // 只留內圈，避免像鈕扣
                if (!simple) add(Layer.HAND, Geometry.ring(Geometry.place(Props.MOONCAKE_INNER, pl), { 0.55 }, { 0.0 }).stroke, INK, 0.55f, evenOdd = true)
            }
        }

        // ---- 傘面與拳頭 ----
        var canopy: Transform? = null
        if (umbrella && hand != null) {
            canopy = Transform(hand.x, hand.y, p.umbrellaAngle, scale = umbrellaScale)
            owner = "umbrella"
            val pc = Rng.phases("canopy", v)
            val c = Geometry.ring(CANOPY, { a -> max((2.2 / umbrellaScale) * (1 + 0.12 * sin(2 * a + pc[3])), accent / umbrellaScale) }, { a -> Geometry.loopNoise(pc, a, 0.45) })
            add(Layer.CANOPY, c.fill, SAGE)
            add(Layer.CANOPY, c.stroke, INK, evenOdd = true)
            // 傘骨是細節，簡化時不畫
            if (!simple) ops += Op(Layer.CANOPY, RIBS, null, stroke = INK, strokeWidth = 1.5 / umbrellaScale, roundCap = true, part = owner)
            ops += Op(Layer.FRONT, PathData.ellipse(hand.x, hand.y, 3.2, 3.2), PAPER, stroke = INK, strokeWidth = sw(2.2, accent), part = owner)
            // 雷雨：左手也握住傘柄
            if (p.effects.leftOnShaft && caps.holdL) {
                val g = p.armL[3]
                ops += Op(Layer.FRONT, PathData.ellipse(g.x, g.y, 3.0, 3.0), PAPER, stroke = INK, strokeWidth = sw(2.1, accent), part = "umbrellaGrip")
            }
        }
        // ---- 前面那層霧、呼出的白氣 ----
        if (weather == Weather.FOG && !simple) {
            owner = "mist"
            mistBand(Layer.FRONT, 97.0, 92.0, 8.0, -16 * sin(clockMs / 3000), "mist2", 0.62f)
        }
        if (p.effects.breath > 0 && "mouth" in parts && !simple) {
            owner = "breath"
            val u = p.effects.breathU
            val loop = Geometry.place(BREATH, Geometry.Placement(73 + 8 * u + p.headDx, 58 + p.headDy - 3 * u, scale = 0.9 + 0.7 * u))
            ringPath(Layer.FRONT, loop, CLOUD, "breath", 0.7, 0.12, p.effects.breath.toFloat())
        }
        return Frame(
            ops.sortedBy { it.layer.ordinal },
            Transform(p.headDx, p.headDy, p.headAngle, HEAD_PIVOT.x, HEAD_PIVOT.y),
            canopy,
        )
    }
}
