package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import com.patty60723.tearoff.mowan.FestivalActions.Performance
import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.Mowan.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * P6-3：節日演出的規則（姿勢座標層級）。跟原型的比對在 FestivalPerformanceGoldenTest；這裡驗證：
 * 握傘點、道具跟著手、只動能力允許的部位、包袱與道具、頭飾跟著頭、開始與結束接回站姿、關閉動畫、看月餅版。
 */
class FestivalPerformanceTest {
    private val combos = FestivalFixtures.ALL.filter { it.festival != null && it.hatOn && it.headwear }

    private fun actions(f: Festival) = FestivalScene.spec(f).let { listOf(it.main, it.idle) }

    private fun times(a: Action) = (0..a.durMs.toInt() step 50).map { it.toDouble() }

    private fun arm(p: Pose, h: Hand) = if (h == Hand.L) p.armL else p.armR

    private fun bounds(ops: List<Mowan.Op>): DoubleArray {
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        for (op in ops) for (c in op.path.commands) {
            val (x, y) = when (c) {
                is PathData.Cmd.MoveTo -> c.x to c.y
                is PathData.Cmd.LineTo -> c.x to c.y
                is PathData.Cmd.QuadTo -> c.x to c.y
                is PathData.Cmd.ArcTo -> c.x to c.y
                PathData.Cmd.Close -> continue
            }
            x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y)
        }
        return doubleArrayOf(x0, y0, x1, y1)
    }

    private fun overlap(a: DoubleArray, b: DoubleArray) = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    private fun polygon(op: Mowan.Op) = op.path.commands.mapNotNull { c ->
        when (c) {
            is PathData.Cmd.MoveTo -> Pt(c.x, c.y)
            is PathData.Cmd.LineTo -> Pt(c.x, c.y)
            is PathData.Cmd.QuadTo -> Pt(c.x, c.y)
            is PathData.Cmd.ArcTo -> Pt(c.x, c.y)
            PathData.Cmd.Close -> null
        }
    }

    private fun inside(p: Pt, poly: List<Pt>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    /** 包袱布袋（第一個 Sage 填色）跟道具（第一個填色）的外形有沒有交疊 */
    private fun propOnSack(frame: Mowan.Frame, part: String): Boolean {
        val sack = polygon(frame.ops.first { it.part == "bundle" && it.fill == Mowan.SAGE })
        val prop = polygon(frame.ops.first { it.part == part && it.fill != null })
        return prop.any { inside(it, sack) } || sack.any { inside(it, prop) }
    }

    /** T15：撐傘拜年時握傘點全程不動、撐傘的手臂跟雨天姿勢一樣、傘角在 −17°～−5°、拿紅包的手真的有動 */
    @Test
    fun t15_rainBowKeepsTheUmbrellaGrip() {
        for (page in listOf(5, 6, 14)) for (w in listOf(Weather.RAINY, Weather.THUNDER)) for (bundle in listOf(true, false)) {
            val s = FestivalScene.resolve(FestivalFixtures.parts(page), Festival.SPRING, w, true, bundle)
            val lv = FestivalActions.levelFor(Action.BOW, s)
            assertEquals(Level.RAIN, lv)
            val base = Performances.pose(s, 0.0, null).first
            var moved = 0.0
            var minA = 99.0; var maxA = -99.0
            for (t in times(Action.BOW)) {
                val p = Performances.pose(s, t, Performance(Action.BOW, lv, t)).first
                val free = Poses.rainy(t)
                assertEquals("第 $page 頁 $w ${t}ms 握傘點", Pt(82.0, 78.0), p.hand)
                assertEquals("第 $page 頁 $w ${t}ms 撐傘的手臂", free.armR, p.armR)
                minA = minOf(minA, p.umbrellaAngle); maxA = maxOf(maxA, p.umbrellaAngle)
                moved = maxOf(moved, hypot(p.armL.last().x - base.armL.last().x, p.armL.last().y - base.armL.last().y))
            }
            assertTrue("傘角 $minA～$maxA", minA >= -17 && maxA <= -5)
            assertTrue("拿紅包的手移動 $moved", moved > 3)
        }
    }

    /** 撐傘的右手在任何節日演出都不動，握傘點只跟著雨天的晃動 */
    @Test
    fun umbrellaHandNeverMoves() {
        for (c in combos.filter { it.weather in FestivalScene.UMBRELLA_WEATHER }) {
            val s = c.resolve()
            if (s.hand(Hand.R) != Item.UMBRELLA) continue
            for (a in actions(c.festival!!)) {
                val lv = FestivalActions.levelFor(a, s)
                for (t in times(a)) {
                    val p = Performances.pose(s, t, Performance(a, lv, t)).first
                    assertEquals("$c $a ${t}ms", Poses.rainy(t).armR, p.armR)
                    assertEquals("$c $a ${t}ms", Pt(82.0, 78.0), p.hand)
                }
            }
        }
    }

    /** 紅包、月餅每一格都畫在實際拿著它的那隻手的末端 */
    @Test
    fun propsFollowTheHoldingHand() {
        for (c in combos.filter { it.page in listOf(4, 5, 6, 14) }) {
            val s = c.resolve()
            for (a in actions(c.festival!!)) {
                val lv = FestivalActions.levelFor(a, s)
                for (t in times(a).filterIndexed { i, _ -> i % 4 == 0 }) {
                    val perf = Performance(a, lv, t)
                    var p = Performances.pose(s, t, perf).first
                    if (!s.caps.stand) p = p.shifted(Mowan.GROUND_DY)
                    val frame = Mowan.frame(c.parts, 0, true, c.weather, t, 0, bundleOn = c.bundleOn, festival = c.festival, performance = perf)
                    for (h in Hand.entries) {
                        val item = s.hand(h) ?: continue
                        if (item == Item.UMBRELLA) continue
                        val end = arm(p, h).last()
                        val box = bounds(frame.ops.filter { it.part == if (item == Item.ENVELOPE) "envelope" else "mooncake" })
                        assertTrue("$c $a=$lv ${t}ms $item 不在${h}手：$end", end.x in box[0]..box[2] && end.y in box[1]..box[3])
                    }
                }
            }
        }
    }

    /** 只動 FestivalActions.uses() 列出的部位：沒列的手、頭整段不動；沒解鎖的手不畫也不動 */
    @Test
    fun onlyAllowedPartsMove() {
        for (c in combos.filter { it.bundleOn }) {
            val s = c.resolve()
            for (a in actions(c.festival!!)) {
                val lv = FestivalActions.levelFor(a, s)
                val u = FestivalActions.uses(a, lv, s)
                for (t in times(a).filterIndexed { i, _ -> i % 2 == 0 }) {
                    val base = Performances.pose(s, t, null).first
                    val p = Performances.pose(s, t, Performance(a, lv, t)).first
                    for (h in Hand.entries) if (h !in u.hands) assertEquals("$c $a=$lv ${t}ms ${h}手不該動", arm(base, h), arm(p, h))
                    if (!u.head) {
                        assertEquals("$c $a=$lv ${t}ms 頭不該動", base.headAngle, p.headAngle, 0.0)
                        assertEquals("$c $a=$lv ${t}ms 頭不該動", base.headDy, p.headDy, 0.0)
                    }
                    assertEquals("$c 腳不動", base.legL, p.legL)
                    assertEquals("$c 腳不動", base.legR, p.legR)
                }
            }
        }
    }

    /** 開始與結束都接回節日站姿；每 33 ms 手尖移動不超過 4 個單位（沒有瞬移） */
    @Test
    fun startsAndEndsAtTheStanceWithoutJumps() {
        for (c in combos.filter { it.bundleOn && it.page in listOf(2, 4, 5, 6, 14) }) {
            val s = c.resolve()
            for (a in actions(c.festival!!)) {
                val lv = FestivalActions.levelFor(a, s)
                for (t in listOf(0.0, a.durMs.toDouble())) {
                    val base = Performances.pose(s, t, null).first
                    val p = Performances.pose(s, t, Performance(a, lv, t)).first
                    assertEquals("$c $a=$lv ${t}ms 左手", base.armL, p.armL)
                    assertEquals("$c $a=$lv ${t}ms 右手", base.armR, p.armR)
                    assertEquals("$c $a=$lv ${t}ms 頭", base.headAngle, p.headAngle, 1e-9)
                    assertEquals("$c $a=$lv ${t}ms 頭", base.headDy, p.headDy, 1e-9)
                    assertEquals("$c $a=$lv ${t}ms 身體", base.body, p.body)
                }
                var prev: Pose? = null
                var t = 0.0
                while (t <= a.durMs) {
                    val p = Performances.pose(s, t, Performance(a, lv, t)).first
                    prev?.let { q ->
                        for (h in Hand.entries) {
                            val d = hypot(arm(p, h).last().x - arm(q, h).last().x, arm(p, h).last().y - arm(q, h).last().y)
                            assertTrue("$c $a=$lv ${t}ms ${h}手一格移動 $d", d < 4)
                        }
                        assertTrue("$c $a=$lv ${t}ms 頭一格轉 ${p.headAngle - q.headAngle}", kotlin.math.abs(p.headAngle - q.headAngle) < 2)
                    }
                    prev = p
                    t += 33.0
                }
            }
        }
    }

    /**
     * 包袱與節日道具：道具畫在包袱前面（手在身體前面）；背著包袱時，靜止站姿、演出開始與結束、
     * 關閉動畫的代表姿勢，左手的月餅、紅包都不壓在包袱上（P6-2 審核的修正）。
     */
    @Test
    fun bundleAndHandProps() {
        assertTrue(Mowan.Layer.HAND.ordinal > Mowan.Layer.BUNDLE.ordinal)
        for (c in combos.filter { it.bundleOn && it.page == 14 }) {
            val s = c.resolve()
            val left = s.hand(Hand.L) ?: continue
            val part = if (left == Item.ENVELOPE) "envelope" else "mooncake"
            for (a in actions(c.festival!!)) {
                val lv = FestivalActions.levelFor(a, s)
                val still = listOf(Performance(a, lv, 0.0), Performance(a, lv, a.durMs.toDouble()), Performance(a, lv, 0.0, held = true))
                for (perf in still) {
                    val frame = Mowan.frame(c.parts, 0, true, c.weather, perf.t, 0, bundleOn = true, festival = c.festival, performance = perf)
                    assertFalse("$c $a=$lv ${perf.t}ms${if (perf.held) "（代表姿勢）" else ""} $left 壓在包袱上", propOnSack(frame, part))
                }
            }
            // 沒有演出的靜止站姿
            val frame = Mowan.frame(c.parts, 0, true, c.weather, 0.0, 0, bundleOn = true, festival = c.festival)
            assertFalse("$c 站姿", propOnSack(frame, part))
        }
    }

    /** 節日頭飾與兔耳在頭部座標裡畫，頭轉動時整個跟著頭轉（同一張筆觸影格，形狀不變） */
    @Test
    fun headwearStaysOnTheHead() {
        for (f in Festival.entries) {
            val parts = FestivalFixtures.parts(14)
            val s = FestivalScene.resolve(parts, f, Weather.NONE, true, true)
            val costume = FestivalScene.spec(f).costume.name.lowercase()
            val a = FestivalScene.spec(f).main
            val lv = FestivalActions.levelFor(a, s)
            val shapes = HashSet<List<PathData.Cmd>>()
            val angles = HashSet<Double>()
            for (t in times(a).filterIndexed { i, _ -> i % 5 == 0 }) {
                val frame = Mowan.frame(parts, 0, festival = f, performance = Performance(a, lv, t))
                val ops = frame.ops.filter { it.part == costume }
                assertTrue(ops.all { it.layer == Mowan.Layer.HEAD })
                shapes += ops.flatMap { it.path.commands }
                angles += frame.head.rotDeg
            }
            assertEquals("$f 頭飾在頭部座標裡形狀不變", 1, shapes.size)
            assertTrue("$f 頭有轉動", angles.size > 3)
        }
    }

    /** 關閉動畫：不管時間，都停在代表姿勢；襪子不落下、不擺，線索不淡入 */
    @Test
    fun reducedMotionHoldsTheKeyPose() {
        for (c in combos.filter { it.bundleOn && it.page in listOf(2, 4, 14) }) {
            val s = c.resolve()
            for (a in actions(c.festival!!)) {
                val lv = FestivalActions.levelFor(a, s)
                val key = Performances.pose(s, a.keyMs.toDouble(), Performance(a, lv, a.keyMs.toDouble(), held = true))
                for (t in listOf(0.0, 500.0, a.keyMs.toDouble(), a.durMs.toDouble())) {
                    val (p, fx) = Performances.pose(s, a.keyMs.toDouble(), Performance(a, lv, t, held = true))
                    assertEquals("$c $a ${t}ms", key.first, p)
                    assertEquals("$c $a", 0.0, fx.sockDy, 0.0)
                    assertEquals("$c $a", 0.0, fx.sockSwing, 0.0)
                    assertEquals("$c $a", 1.0, fx.cueIn, 0.0)
                }
            }
        }
    }

    /** D-37 看月餅版：不抬頭（視線、頭都往下或水平）、沒有月光、月餅的手有動 */
    @Test
    fun cakeLevelLooksDown() {
        for (w in listOf(Weather.RAINY, Weather.THUNDER, Weather.SNOW)) for (bundle in listOf(true, false)) {
            val s = FestivalScene.resolve(FestivalFixtures.parts(14), Festival.MOON, w, true, bundle)
            for (a in listOf(Action.TOAST, Action.GAZE_MOON)) {
                val lv = FestivalActions.levelFor(a, s)
                assertEquals(Level.CAKE, lv)
                val h = s.handOf(Item.MOONCAKE)!!
                var moved = 0.0
                for (t in times(a)) {
                    val (p, fx) = Performances.pose(s, t, Performance(a, lv, t))
                    assertTrue("$w $a ${t}ms 視線往上 ${p.gaze}", p.gaze.y >= 0)
                    assertTrue("$w $a ${t}ms 頭往上 ${p.headDy}", p.headDy >= 0)
                    assertEquals("$w $a 沒有月光", 0.0, fx.glow, 0.0)
                    val base = Performances.pose(s, t, null).first
                    moved = maxOf(moved, hypot(arm(p, h).last().x - arm(base, h).last().x, arm(p, h).last().y - arm(base, h).last().y))
                }
                assertTrue("$w $a 月餅的手有動 $moved", moved > 2)
            }
        }
    }
}
