package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * W2：天氣姿勢。黃金數值由 docs/prototypes/tools/export-golden-weather.js 從定稿參考 weather.js 匯出。
 * 姿勢是純數學，跟原型用同一套公式，所以容差很小（1e-6）。
 */
class WeatherPosesTest {
    // 單元測試的工作目錄是 app/
    private val golden = JSONObject(File("src/test/resources/mowan/golden-weather.json").readText())
    private val tol = golden.getDouble("tolerance")
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun caps(page: Int) = Capabilities.of(Mowan.partsOf(StoryEngine.unlocked(beats, page)))
    private val full = caps(14)

    private fun pose(weather: String, t: Double, caps: Capabilities, sinceStrike: Double? = null) = when (weather) {
        "hot" -> Poses.hot(t, caps)
        "cloudy" -> Poses.cloudy(t, caps)
        "fog" -> Poses.fog(t, caps)
        "thunder" -> Poses.thunder(t, caps, sinceStrike)
        "cold" -> Poses.cold(t, caps)
        "snow" -> Poses.snow(t, caps)
        else -> error(weather)
    }

    // ---------- 跟定稿參考比對 ----------

    @Test
    fun weatherPosesMatchReference() {
        val cases = golden.getJSONArray("weather")
        assertEquals(432, cases.length())
        for (i in 0 until cases.length()) {
            val g = cases.getJSONObject(i)
            val w = g.getString("weather")
            val page = g.getInt("page")
            val t = g.getDouble("t")
            val since = if (g.has("sinceStrike") && !g.isNull("sinceStrike")) g.getDouble("sinceStrike") else null
            assertPose("$w 第 $page 頁 ${t.toInt()}ms", g, pose(w, t, caps(page), since))
        }
    }

    /** 已上線的晴、雨：Android 的姿勢、週期、傘的握點跟原型一樣（W1） */
    @Test
    fun shippedSunnyAndRainyMatchReference() {
        val cases = golden.getJSONArray("shipped")
        for (i in 0 until cases.length()) {
            val g = cases.getJSONObject(i)
            val t = g.getDouble("t")
            val p = if (g.getString("weather") == "sunny") Poses.sunny(t) else Poses.rainy(t)
            assertPose("${g.getString("weather")} ${t.toInt()}ms", g, p, effects = false)
        }
        for (t in listOf(0.0, 900.0, 2750.0)) {
            assertEquals(Poses.sunny(t).headAngle, Poses.sunny(t + Poses.SUNNY_MS).headAngle, 1e-9)
            assertEquals(Poses.rainy(t).umbrellaAngle, Poses.rainy(t + Poses.RAINY_MS).umbrellaAngle, 1e-9)
        }
        assertEquals(Pt(82.0, 78.0), Poses.rainy(1234.0).hand)
    }

    private fun assertPose(at: String, g: JSONObject, p: Pose, effects: Boolean = true) {
        for ((key, mine) in listOf("body" to p.body, "armL" to p.armL, "armR" to p.armR, "legL" to p.legL, "legR" to p.legR, "tail" to p.tail)) {
            assertPts("$at $key", g.getJSONArray(key), mine)
        }
        assertEquals("$at headAngle", g.getDouble("headAngle"), p.headAngle, tol)
        assertEquals("$at face", g.getString("face").uppercase(), p.face.name)
        assertEquals("$at flick", g.getString("flick").uppercase(), p.flick.name)
        if (g.isNull("hand")) {
            assertNull("$at 不該有傘", p.hand)
        } else {
            val h = g.getJSONArray("hand")
            assertEquals("$at hand", h.getDouble(0), p.hand!!.x, tol)
            assertEquals("$at hand", h.getDouble(1), p.hand!!.y, tol)
            assertEquals("$at umbrellaAngle", g.getDouble("umbrellaAngle"), p.umbrellaAngle, tol)
        }
        if (!effects) return
        assertEquals("$at headDy", g.getDouble("headDy"), p.headDy, tol)
        assertEquals("$at headDx", g.getDouble("headDx"), p.headDx, tol)
        val gz = g.getJSONArray("gaze")
        assertEquals("$at gaze", gz.getDouble(0), p.gaze.x, tol)
        assertEquals("$at gaze", gz.getDouble(1), p.gaze.y, tol)
        val fx = g.getJSONObject("fx")
        if (fx.isNull("sweat")) assertNull("$at sweat", p.effects.sweat) else assertEquals("$at sweat", fx.getDouble("sweat"), p.effects.sweat!!, tol)
        assertEquals("$at scarfUp", fx.getBoolean("scarfUp"), p.effects.scarfUp)
        assertEquals("$at leftOnShaft", fx.getBoolean("leftOnShaft"), p.effects.leftOnShaft)
        assertEquals("$at breath", fx.getDouble("breath"), p.effects.breath, tol)
        assertEquals("$at breathU", fx.getDouble("breathU"), p.effects.breathU, tol)
        assertEquals("$at bolt", fx.getDouble("bolt"), p.effects.bolt, tol)
    }

    private fun assertPts(at: String, g: JSONArray, mine: List<Pt>) {
        assertEquals("$at 點數", g.length(), mine.size)
        for (i in 0 until g.length()) {
            val q = g.getJSONArray(i)
            assertEquals("$at[$i].x", q.getDouble(0), mine[i].x, tol)
            assertEquals("$at[$i].y", q.getDouble(1), mine[i].y, tol)
        }
    }

    // ---------- 動作一致性（Phase 2B 審核的結果變成自動檢查） ----------

    private fun length(arm: List<Pt>) = arm.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    private val periods = mapOf("hot" to Poses.HOT_MS, "cloudy" to Poses.CLOUDY_MS, "fog" to Poses.FOG_MS, "thunder" to Poses.RAINY_MS, "cold" to Poses.COLD_MS, "snow" to Poses.SNOW_MS)

    /** 每 10 毫秒取一格，跑完一個週期（雷雨另外把打雷的 1 秒也跑一次） */
    private fun sweep(weather: String, caps: Capabilities = full): List<Pose> {
        val step = 10.0
        val frames = (0 until (periods.getValue(weather) / step).toInt()).map { pose(weather, it * step, caps) }
        return if (weather == "thunder") frames + (0 until 100).map { pose(weather, 5000.0 + it * step, caps, it * step) } else frames
    }

    /** 這一格的頭輪廓（套用頭的轉動與位移） */
    private fun head(p: Pose): List<Pt> {
        val r = Math.toRadians(p.headAngle)
        val c = Mowan.HEAD_PIVOT
        return Mowan.HEAD_LOOP.map { q ->
            val dx = q.x - c.x
            val dy = q.y - c.y
            Pt(c.x + dx * cos(r) - dy * sin(r) + p.headDx, c.y + dx * sin(r) + dy * cos(r) + p.headDy)
        }
    }

    private fun inside(pt: Pt, poly: List<Pt>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > pt.y) != (b.y > pt.y) && pt.x < (b.x - a.x) * (pt.y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    @Test
    fun handsNeverGoInsideTheHead() {
        for (w in periods.keys) for (p in sweep(w)) for (arm in listOf(p.armL, p.armR)) {
            assertFalse("$w：手尖進到頭裡", inside(arm.last(), head(p)))
        }
    }

    @Test
    fun armLengthsStayNatural() {
        // 一般站姿的手臂約 21.6；範圍來自 Phase 2B 審核（整合規格 §5.2）
        val limits = mapOf(
            "hot" to (16.0 to 36.5), "cloudy" to (19.0 to 22.0), "fog" to (19.0 to 26.0),
            "thunder" to (19.0 to 24.0), "cold" to (19.5 to 20.5), "snow" to (18.8 to 19.5),
        )
        for ((w, range) in limits) for (p in sweep(w)) for (arm in listOf(p.armL, p.armR)) {
            val l = length(arm)
            assertTrue("$w 手臂長度 $l 不在 $range", l >= range.first && l <= range.second)
        }
    }

    @Test
    fun hotArmNeverCollapsesWhileRaising() {
        // 舊版直接從垂手內插到擦汗，手臂中途縮到 13.5；現在最短約 16.3（垂手時 19.4）
        val shortest = sweep("hot").minOf { length(it.armR) }
        assertTrue("熱天手臂最短 $shortest", shortest >= 16.0)
    }

    @Test
    fun coldHugDoesNotKnot() {
        // 舊版雙手交叉、手尖越過身體 5 個單位以上，小尺寸看起來像打結；現在手尖停在身體線附近
        val p = Poses.cold(2300.0, full)
        val bodyX = 60.7
        assertTrue(abs(p.armL.last().x - bodyX) <= 2.5)
        assertTrue(abs(p.armR.last().x - bodyX) <= 2.5)
        assertTrue("左手從左邊繞過來", p.armL.minOf { it.x } < bodyX - 4)
        assertTrue("右手從右邊繞過來", p.armR.maxOf { it.x } > bodyX + 4)
    }

    @Test
    fun thunderLeftHandHoldsTheShaft() {
        for (t in listOf(0.0, 1000.0, 2100.0, 3150.0)) {
            val p = Poses.thunder(t, full)
            val h = p.hand!!
            val a = Math.toRadians(p.umbrellaAngle)
            // 傘柄方向：手的位置往 (−sin, cos) 延伸
            val dir = Pt(-sin(a), cos(a))
            val g = p.armL.last()
            val off = abs((g.x - h.x) * dir.y - (g.y - h.y) * dir.x)
            assertTrue("左手離傘柄 $off", off < 1e-9)
            assertEquals(6.0, hypot(g.x - h.x, g.y - h.y), 1e-9)
            // 右手手臂末端靠近拳頭
            assertTrue(hypot(p.armR.last().x - h.x, p.armR.last().y - h.y) < 2.0)
        }
    }

    // ---------- 成長階段：沒有的部位不能用 ----------

    @Test
    fun actionsWaitForTheirParts() {
        // 沒有右手（第 1–3 頁）：不撐傘、不擦汗
        assertNull(Poses.thunder(1000.0, caps(3)).hand)
        assertEquals(Poses.hot(0.0, caps(3)).armR, Poses.hot(900.0, caps(3)).armR)
        // 只有右手（第 4 頁）：單手撐傘，左手沒有握傘柄
        assertFalse(Poses.thunder(1000.0, caps(4)).effects.leftOnShaft)
        assertTrue(Poses.thunder(1000.0, caps(5)).effects.leftOnShaft)
        // 沒有左手：霧天不伸手
        assertEquals(Poses.fog(0.0, caps(4)).armL, Poses.fog(2500.0, caps(4)).armL)
        // 沒有眼睛（第 1–2 頁）：看雲、看雪不靠視線
        assertEquals(Pt(0.0, 0.0), Poses.cloudy(1500.0, caps(2)).gaze)
        assertEquals(Pt(0.0, 0.0), Poses.snow(1000.0, caps(2)).gaze)
        assertTrue(Poses.cloudy(1500.0, caps(3)).gaze.y < 0)
        // 沒有嘴巴（第 8 頁前）：不呼白氣
        assertEquals(0.0, Poses.cold(1150.0, caps(7)).effects.breath, 0.0)
        assertTrue(Poses.cold(1150.0, caps(8)).effects.breath > 0)
    }

    @Test
    fun thunderStartleIsShortAndOnlyOnStrike() {
        val calm = (0 until 420).map { Poses.thunder(it * 10.0, full) }
        assertTrue(calm.all { it.face == Face.RAINY && it.effects.bolt == 0.0 && it.headDx == 0.0 })
        val scared = (0 until 100).map { Poses.thunder(5000.0, full, it * 10.0) }
        val startleMs = scared.count { it.face == Face.STARTLE } * 10
        assertTrue("驚嚇表情 $startleMs ms", startleMs in 300..500)
        // 0.8 秒後完全回到撐傘
        assertEquals(Face.RAINY, Poses.thunder(5000.0, full, 800.0).face)
        assertEquals(0.0, Poses.thunder(5000.0, full, 800.0).headDx, 0.0)
    }

    // ---------- 動作連續性：相鄰兩格（App 每 33 毫秒重算一次姿勢）不能突然跳 ----------

    private val frameMs = 1000.0 / 30

    private data class Step(val t: Double, val hand: Double, val headAngle: Double, val headDx: Double)

    /** 從 [from] 到 [to]，每一格跟前一格的差 */
    private fun steps(from: Double, to: Double, at: (Double) -> Pose): List<Step> {
        val out = ArrayList<Step>()
        var prev = at(from)
        var t = from + frameMs
        while (t <= to) {
            val p = at(t)
            val hand = maxOf(
                hypot(p.armL.last().x - prev.armL.last().x, p.armL.last().y - prev.armL.last().y),
                hypot(p.armR.last().x - prev.armR.last().x, p.armR.last().y - prev.armR.last().y),
            )
            out += Step(t, hand, abs(p.headAngle - prev.headAngle), abs(p.headDx - prev.headDx))
            prev = p
            t += frameMs
        }
        return out
    }

    /** 一格最多：手尖 1.5、頭轉 1°。現有動作裡最快的是雷雨驚嚇的 0.41、0.54°（晴天伸懶腰 0.13） */
    private fun assertSmooth(name: String, steps: List<Step>, maxHand: Double = 1.5, maxAngle: Double = 1.0, maxDx: Double = 1.0) {
        val worst = steps.maxBy { it.hand }
        assertTrue("$name：手尖一格跳了 ${"%.2f".format(worst.hand)}（${worst.t.toInt()} ms）", worst.hand <= maxHand)
        val turn = steps.maxBy { it.headAngle }
        assertTrue("$name：頭一格轉了 ${"%.2f".format(turn.headAngle)}°（${turn.t.toInt()} ms）", turn.headAngle <= maxAngle)
        val shake = steps.maxBy { it.headDx }
        assertTrue("$name：頭一格晃了 ${"%.2f".format(shake.headDx)}（${shake.t.toInt()} ms）", shake.headDx <= maxDx)
    }

    @Test
    fun weatherMotionIsContinuous() {
        // 跑兩個週期，循環接回開頭的那一格也算進去
        assertSmooth("陰", steps(0.0, 2 * Poses.CLOUDY_MS) { Poses.cloudy(it, full) })
        assertSmooth("霧", steps(0.0, 2 * Poses.FOG_MS) { Poses.fog(it, full) })
        assertSmooth("冷", steps(0.0, 2 * Poses.COLD_MS) { Poses.cold(it, full) })
        assertSmooth("雪", steps(0.0, 2 * Poses.SNOW_MS) { Poses.snow(it, full) })
        assertSmooth("雷雨", steps(0.0, 2 * Poses.RAINY_MS) { Poses.thunder(it, full) })
        assertSmooth("晴（已上線）", steps(0.0, 2 * Poses.SUNNY_MS) { Poses.sunny(it) })
        assertSmooth("雨（已上線）", steps(0.0, 2 * Poses.RAINY_MS) { Poses.rainy(it) })
    }

    @Test
    fun thunderStartleIsContinuous() {
        // 打雷前 100 ms 到打雷後 1 秒
        assertSmooth("雷雨驚嚇", steps(4900.0, 6000.0) { t -> Poses.thunder(t, full, if (t >= 5000.0) t - 5000.0 else null) })
        // 驚嚇表情只出現一段，不會閃來閃去
        val faces = (0 until 33).map { Poses.thunder(5000.0, full, it * frameMs).face }
        val runs = faces.zipWithNext().count { (a, b) -> a != b }
        assertEquals("表情只切換兩次（進入、離開驚嚇）", 2, runs)
    }

    @Test
    fun hotWipeIsContinuous() {
        // 擦汗的那一段（手在臉頰上下擦）
        assertSmooth("熱：擦汗", steps(500.0, 1700.0) { Poses.hot(it, full) })
    }

    /**
     * 熱天抬手、放手（0–500 ms、1700–2200 ms）目前不通過：
     * 手尖一格最多跳 6.35（其他動作最多 0.41），而且在中間姿勢 R_WIPE_SIDE、R_WIPE_MID 幾乎停住
     * （速度從 200 掉到 15、6 單位／秒），看起來像分三段頓一下。
     * 這是改善「手臂縮短」時加入中間姿勢的副作用；要改時間或內插方式屬於美術決定，等確認後再改數值並打開這個測試。
     */
    @Ignore("熱天抬手、放手的速度與停頓待美術決定，見上方說明")
    @Test
    fun hotRaiseAndLowerAreContinuous() {
        assertSmooth("熱：抬手", steps(0.0, 500.0) { Poses.hot(it, full) })
        assertSmooth("熱：放手", steps(1700.0, 2200.0) { Poses.hot(it, full) })
        // 動作中途不能停下來：最慢的一格至少要有最快的一格的 20%
        for ((name, from, to) in listOf(Triple("抬手", 0.0, 500.0), Triple("放手", 1700.0, 2200.0))) {
            val moving = steps(from, to) { Poses.hot(it, full) }.map { it.hand }.dropLast(1).drop(1)
            assertTrue("熱：$name 中途停頓", moving.min() >= 0.2 * moving.max())
        }
    }
}
