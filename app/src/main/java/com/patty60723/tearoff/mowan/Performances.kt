package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.Keyframes.env
import com.patty60723.tearoff.mowan.Keyframes.mix
import com.patty60723.tearoff.mowan.Keyframes.seg
import com.patty60723.tearoff.mowan.Keyframes.track
import com.patty60723.tearoff.mowan.Keyframes.trackPts
import kotlin.math.exp
import kotlin.math.sin

/**
 * 節日的主演出與待機（P6-3），照原型 core.js 的 `ACTIONS_V1`、Phase 2A.2 的 `ACTIONS`（作揖、掛襪、舉月餅的
 * 完整版與雨天版用「預備 → 動作 → 停留 → 收尾」的 v2）與 `buildFrame` 移植。
 * 演出疊在節日站姿（[Poses.festival]）上；層級由 [FestivalActions.levelFor] 決定、演出開始時鎖定（D-35）。
 *
 * 只動 [FestivalActions.uses] 列出的部位；撐傘的右手與握傘點不動，雨天拜年只讓傘跟著點頭前傾。
 * 「看月餅版」（D-37）是 Android 新加的，原型沒有：中秋看不到月亮時低頭看手上的月餅，不抬頭。
 */
internal object Performances {
    /** 演出帶動的道具：襪子落下與擺動、月光、成長焦點期線索淡入 */
    data class Fx(val sockDy: Double = 0.0, val sockSwing: Double = 0.0, val glow: Double = 0.0, val cueIn: Double = 1.0)

    // ---------- 手臂姿勢（原型 ARM，Phase 2A.2） ----------
    private fun mirror(p: List<Pt>) = p.mapIndexed { i, q -> if (i == 0) Pt(60.8, 77.0) else Pt(121.3 - q.x, q.y) }

    val L_REACH = pts(60.5, 77.0, 52.5, 75.0, 45.0, 71.0, 38.5, 67.0)
    val L_TOAST = pts(60.5, 77.0, 53.5, 73.5, 46.5, 69.5, 40.0, 65.5)
    val R_TOAST = mirror(L_TOAST)
    val L_LIFT = pts(60.5, 77.0, 55.0, 79.0, 51.0, 82.5, 49.0, 85.5)
    val R_LIFT = mirror(L_LIFT)
    /** 拜年：手肘略抬、雙手在胸前偏下 */
    val L_CHEST = pts(60.5, 77.0, 55.4, 80.4, 55.4, 84.4, 58.9, 85.4)
    val R_CHEST = pts(60.8, 77.0, 65.9, 80.4, 65.9, 84.4, 62.4, 85.4)
    /** 舉月餅前先在腹前捧著 */
    val L_CRADLE = pts(60.5, 77.0, 55.6, 81.4, 53.4, 86.0, 52.6, 89.0)
    val R_CRADLE = mirror(L_CRADLE)
    /** 手尖碰到襪子的腳尖 */
    val L_TOUCH = pts(60.5, 77.0, 54.0, 75.2, 47.0, 74.2, 39.6, 73.4)

    /**
     * 背著包袱時左手拿道具的捧、遞（P6-2 審核後的局部修正，跟 [Poses.L_HOLD_BUNDLE] 一起）：
     * 原本的姿勢把月餅、紅包停在包袱前面。這兩個姿勢把手留在身體前方，道具不壓在包袱上。
     */
    val L_CRADLE_BUNDLE = pts(60.5, 77.0, 58.2, 82.4, 57.4, 88.0, 58.0, 93.5)
    val L_LIFT_BUNDLE = pts(60.5, 77.0, 56.2, 80.6, 54.4, 85.6, 54.8, 90.8)
    /** 背著包袱時把月餅舉高：比 [L_TOAST] 再往外一點，月餅不碰到包袱的布角，也不碰到頭 */
    val L_TOAST_BUNDLE = pts(60.5, 77.0, 52.8, 73.4, 44.8, 68.9, 37.0, 64.5)

    private class Out {
        var armL: List<Pt>? = null
        var armR: List<Pt>? = null
        var face: Face? = null
        var flick: Flick? = null
        var headAngle: Double? = null
        var headDy: Double? = null
        var gaze: Pt? = null
        var wiggle = 0.0
        var umbrellaTilt = 0.0
        var sockDy = 0.0
        var sockSwing = 0.0
        var glow = 0.0
        var cueIn = 1.0
        fun arm(h: Hand, p: List<Pt>) = if (h == Hand.L) armL = p else armR = p
    }

    private fun trackPt(t: Double, keys: List<Pair<Double, Pt>>) =
        Pt(track(t, keys.map { it.first to it.second.x }), track(t, keys.map { it.first to it.second.y }))

    private fun sunny(on: Boolean) = if (on) Face.SUNNY else Face.NORMAL
    private fun perked(on: Boolean) = if (on) Flick.PERKED else Flick.NORMAL

    /** 節日站姿 + 演出 → 這一格的姿勢與道具動作量 */
    fun pose(scene: FestivalScene.Result, clockMs: Double, perf: FestivalActions.Performance?): Pair<Pose, Fx> {
        val base = Poses.festival(scene, clockMs)
        if (perf == null) return base to Fx()
        val o = act(perf.action, perf.level, perf.t, scene, base)
        var p = base
        o.armL?.let { p = p.copy(armL = it) }
        o.armR?.let { p = p.copy(armR = it) }
        o.face?.let { p = p.copy(face = it) }
        o.flick?.let { p = p.copy(flick = it) }
        o.headAngle?.let { p = p.copy(headAngle = p.headAngle + it) }
        if (o.umbrellaTilt != 0.0 && p.hand != null) p = p.copy(umbrellaAngle = p.umbrellaAngle + o.umbrellaTilt)
        o.headDy?.let { dy ->
            p = p.copy(headDy = dy)
            // 低頭時脖子跟著下來一點（身體上端、圍巾），避免頭像是從圍巾上滑下來
            if (dy != 0.0) {
                val nk = 0.45 * dy
                val k = doubleArrayOf(1.0, 0.5, 0.15, 0.0)
                p = p.copy(neckDy = nk, body = p.body.mapIndexed { i, q -> Pt(q.x, q.y + nk * k[i]) }, tail = p.tail.map { Pt(it.x, it.y + nk) })
            }
        }
        o.gaze?.let { p = p.copy(gaze = it) }
        if (o.wiggle != 0.0 && !perf.held) {
            val k = doubleArrayOf(0.0, 0.7, 1.0, 0.0)
            p = p.copy(body = p.body.mapIndexed { i, q -> Pt(q.x + o.wiggle * k[i], q.y) })
        }
        val fx = Fx(
            sockDy = if (perf.held) 0.0 else o.sockDy,
            sockSwing = if (perf.held) 0.0 else o.sockSwing,
            glow = o.glow,
            cueIn = if (perf.held) 1.0 else o.cueIn,
        )
        return p to fx
    }

    private fun armOf(p: Pose, h: Hand) = if (h == Hand.L) p.armL else p.armR

    private fun act(action: Action, lv: Level, t: Double, s: FestivalScene.Result, base: Pose): Out {
        val o = Out()
        val bundle = s.shown(Item.BUNDLE)
        fun lift(h: Hand) = if (h == Hand.R) R_LIFT else if (bundle) L_LIFT_BUNDLE else L_LIFT
        fun cradle(h: Hand) = if (h == Hand.R) R_CRADLE else if (bundle) L_CRADLE_BUNDLE else L_CRADLE
        fun chest(h: Hand) = if (h == Hand.R) R_CHEST else L_CHEST
        when (action) {
            Action.BOW -> {
                if (lv == Level.LINE) {
                    o.wiggle = 2.4 * sin(t / 600 * TAU) * env(t, 300.0, 600.0, 1600.0, 2200.0)
                    o.cueIn = seg(t, 0.0, 900.0)
                    return o
                }
                val h = s.handOf(Item.ENVELOPE) ?: Hand.R
                if (lv == Level.FULL || lv == Level.RAIN) {
                    // 預備：看一眼手上的紅包 → 動作：手收到胸前、低頭 → 停留 → 收尾：抬頭、遞出、放回
                    val other = if (h == Hand.L) Hand.R else Hand.L
                    val side = if (h == Hand.L) -1.0 else 1.0
                    val p0 = armOf(base, h)
                    o.arm(h, trackPts(t, listOf(
                        0.0 to p0, 500.0 to mix(p0, lift(h), 0.3), 900.0 to mix(p0, lift(h), 0.3), 1500.0 to chest(h),
                        2500.0 to chest(h), 3050.0 to lift(h), 3500.0 to lift(h), 4200.0 to p0,
                    )))
                    if (lv == Level.FULL) {
                        val q0 = armOf(base, other)
                        o.arm(other, trackPts(t, listOf(0.0 to q0, 900.0 to q0, 1500.0 to chest(other), 2500.0 to chest(other), 3050.0 to q0)))
                    }
                    o.headAngle = track(t, listOf(0.0 to 0.0, 450.0 to 5 * side, 900.0 to 5 * side, 1450.0 to 1.5, 2500.0 to 1.5, 3100.0 to 0.0))
                    o.headDy = track(t, listOf(0.0 to 0.0, 900.0 to 0.0, 1550.0 to 2.8, 1750.0 to 3.1, 2500.0 to 3.1, 3050.0 to -0.3, 3300.0 to 0.0))
                    o.gaze = trackPt(t, listOf(
                        0.0 to Pt(0.0, 0.0), 450.0 to Pt(0.7 * side, 0.8), 900.0 to Pt(0.7 * side, 0.8), 1450.0 to Pt(0.0, 0.6),
                        2500.0 to Pt(0.0, 0.6), 3000.0 to Pt(0.2 * side, 0.0), 4200.0 to Pt(0.0, 0.0),
                    ))
                    o.face = sunny((t > 1550 && t < 2500) || (t > 2900 && t < 3700))
                    o.flick = perked(t > 2950 && t < 3600)
                    // 撐傘拜年：撐傘的手不動，傘跟著點頭微微前傾
                    if (lv == Level.RAIN) o.umbrellaTilt = track(t, listOf(0.0 to 0.0, 900.0 to 0.0, 1550.0 to -3.5, 2500.0 to -3.5, 3100.0 to 0.8, 3400.0 to 0.0))
                    return o
                }
                val nod = env(t, 600.0, 850.0, 900.0, 1250.0) + env(t, 1350.0, 1600.0, 1650.0, 2000.0)
                if (lv == Level.ONE_HAND) {
                    val p0 = armOf(base, h)
                    o.arm(h, mix(mix(p0, lift(h), seg(t, 0.0, 600.0)), p0, seg(t, 2800.0, 3400.0)))
                }
                val toCouplet = if (lv == Level.HEAD) env(t, 0.0, 500.0, 2400.0, 3200.0) else 0.0
                o.headDy = 2.4 * nod
                o.headAngle = 3 * nod - 5 * toCouplet
                o.gaze = Pt(-0.8 * toCouplet, 0.0)
                o.face = sunny(t > 2000 && t < 3000)
                o.flick = perked(t > 600 && t < 3000)
            }
            Action.LOOK_ENVELOPE -> {
                if (lv == Level.LINE) {
                    o.wiggle = 1.2 * sin(t / 700 * TAU) * env(t, 0.0, 400.0, 1400.0, 2200.0)
                    return o
                }
                val e = env(t, 0.0, 500.0, 1300.0, 2200.0)
                if (lv == Level.HEAD) {
                    o.headAngle = -5 * e
                    o.gaze = Pt(-0.8 * e, 0.0)
                    return o
                }
                val h = s.handOf(Item.ENVELOPE)!!
                val side = if (h == Hand.L) -1.0 else 1.0
                o.headAngle = 5 * side * e
                o.gaze = Pt(0.7 * side * e, 0.7 * e)
                o.arm(h, mix(armOf(base, h), lift(h), 0.35 * env(t, 300.0, 700.0, 1200.0, 1800.0)))
            }
            Action.SOCK -> {
                if (lv == Level.FULL) {
                    // 襪子一開始有點歪；伸手把它扶正，放手後只剩很小的擺動
                    val h = if (s.caps.holdL && s.hand(Hand.L) == null) Hand.L else Hand.R
                    o.sockSwing = when {
                        t < 1500 -> 11.0
                        t < 1750 -> track(t, listOf(1500.0 to 11.0, 1750.0 to 0.0))
                        t < 2300 -> 0.0
                        else -> 2.2 * exp(-(t - 2300) / 500) * sin((t - 2300) / 1000 * TAU * 1.6)
                    }
                    val p0 = armOf(base, h)
                    o.arm(h, trackPts(t, listOf(0.0 to p0, 800.0 to p0, 1500.0 to L_TOUCH, 2300.0 to L_TOUCH, 2900.0 to p0)))
                    o.headAngle = track(t, listOf(0.0 to 0.0, 600.0 to -5.0, 1500.0 to -4.5, 2050.0 to -3.5, 2300.0 to -4.0, 2800.0 to 0.0, 3300.0 to 6.5, 3600.0 to 6.0, 4400.0 to 0.0))
                    o.headDy = track(t, listOf(0.0 to 0.0, 1950.0 to 0.0, 2100.0 to 0.9, 2300.0 to 0.0, 3150.0 to 0.0, 3350.0 to -1.1, 3700.0 to 0.0))
                    o.gaze = trackPt(t, listOf(
                        0.0 to Pt(0.0, 0.0), 600.0 to Pt(-0.85, -0.85), 2300.0 to Pt(-0.85, -0.85), 2800.0 to Pt(0.0, 0.0),
                        3250.0 to Pt(0.95, 0.75), 4000.0 to Pt(0.95, 0.75), 4400.0 to Pt(0.0, 0.0),
                    ))
                    o.face = sunny(t > 3350 && t < 4200)
                    o.flick = perked((t > 400 && t < 2300) || (t > 3250 && t < 4000))
                    return o
                }
                // 紙片般逐格落下
                o.sockDy = if (t < 300) -40.0 else if (t < 450) -24.0 else if (t < 600) -9.0 else 0.0
                if (lv == Level.LINE) {
                    o.wiggle = 2.4 * sin(t / 600 * TAU) * env(t, 600.0, 900.0, 1900.0, 2500.0)
                    return o
                }
                val look = env(t, 0.0, 500.0, 1500.0, 2100.0)
                var angle = -4 * look
                var gaze = Pt(-0.8 * look, -0.8 * look)
                val h = when {
                    s.caps.holdL && s.hand(Hand.L) == null -> Hand.L
                    s.caps.holdR && s.hand(Hand.R) == null -> Hand.R
                    else -> null
                }
                if (h != null && lv == Level.POINT) o.arm(h, mix(armOf(base, h), if (h == Hand.L) L_REACH else R_LIFT, env(t, 700.0, 1350.0, 1500.0, 2300.0)))
                if (s[FestivalScene.Slot.GROUND_R] == Item.GIFT) {
                    val gift = env(t, 2200.0, 2700.0, 3000.0, 3600.0)
                    angle += 5 * gift
                    gaze = Pt(gaze.x + 0.8 * gift, gaze.y + 0.7 * gift)
                    o.face = sunny(t > 2500 && t < 3200)
                }
                o.headAngle = angle
                o.gaze = gaze
            }
            Action.PEEK_GIFT -> {
                if (lv == Level.LINE) {
                    o.wiggle = 1.2 * sin(t / 700 * TAU) * env(t, 0.0, 400.0, 1200.0, 2000.0)
                    return o
                }
                val e = env(t, 0.0, 400.0, 1400.0, 2000.0)
                if (lv == Level.FULL) { o.headAngle = 5 * e; o.gaze = Pt(0.8 * e, 0.6 * e) } else { o.headAngle = -4 * e; o.gaze = Pt(-0.8 * e, -0.8 * e) }
            }
            Action.TOAST, Action.GAZE_MOON -> {
                if (lv == Level.CAKE) return cake(action, t, s, base, o, cradle(s.handOf(Item.MOONCAKE)!!))
                if (action == Action.GAZE_MOON) {
                    if (lv == Level.LINE) {
                        o.wiggle = 1.2 * sin(t / 700 * TAU) * env(t, 0.0, 400.0, 1800.0, 2600.0)
                        return o
                    }
                    val e = env(t, 0.0, 600.0, 1800.0, 2600.0)
                    o.headAngle = 4 * e
                    o.gaze = Pt(0.8 * e, -0.9 * e)
                    return o
                }
                if (lv == Level.FULL) {
                    val h = s.handOf(Item.MOONCAKE)!!
                    val side = if (h == Hand.L) -1.0 else 1.0
                    val up = if (h == Hand.R) R_TOAST else if (bundle) L_TOAST_BUNDLE else L_TOAST
                    val p0 = armOf(base, h)
                    o.arm(h, trackPts(t, listOf(0.0 to p0, 700.0 to cradle(h), 1100.0 to cradle(h), 2300.0 to up, 3300.0 to up, 4300.0 to p0)))
                    o.headAngle = track(t, listOf(0.0 to 0.0, 700.0 to 4 * side, 1100.0 to 4 * side, 2300.0 to 2.0, 2700.0 to 5.0, 3300.0 to 5.0, 4300.0 to 0.0))
                    o.headDy = track(t, listOf(0.0 to 0.0, 700.0 to 1.2, 1100.0 to 1.2, 2000.0 to 0.0, 2700.0 to -0.6, 3300.0 to -0.6, 4000.0 to 0.0))
                    o.gaze = trackPt(t, listOf(
                        0.0 to Pt(0.0, 0.0), 700.0 to Pt(0.6 * side, 0.9), 1100.0 to Pt(0.6 * side, 0.9), 2300.0 to Pt(0.4, -0.4),
                        2700.0 to Pt(0.8, -0.9), 3300.0 to Pt(0.8, -0.9), 4300.0 to Pt(0.0, 0.0),
                    ))
                    o.face = sunny(t > 2700 && t < 3500)
                    o.flick = perked(t > 2500 && t < 3500)
                    o.glow = track(t, listOf(0.0 to 0.0, 2500.0 to 0.0, 2900.0 to 1.0, 3300.0 to 1.0, 3900.0 to 0.0))
                    return o
                }
                o.glow = env(t, 1600.0, 2100.0, 2600.0, 3200.0)
                if (lv == Level.LINE) {
                    o.wiggle = 2.2 * sin(t / 650 * TAU) * env(t, 1200.0, 1600.0, 2600.0, 3200.0)
                    // 看不到月亮時沒有月光（D-37）
                    if (!s.shown(Item.MOON)) o.glow = 0.0
                    return o
                }
                val look = env(t, 0.0, 800.0, 2800.0, 3800.0)
                o.headAngle = 4 * look
                o.gaze = Pt(0.8 * look, -0.9 * look)
                o.flick = perked(look > 0.5)
                o.face = sunny(t > 2000 && t < 2800)
            }
        }
        return o
    }

    /**
     * 看月餅版（D-37，Android 新加，Proposed 動作）：把月餅捧到腹前、低頭看著它、笑一下，再放回。
     * 不抬頭、沒有月光。時間配合原本的演出長度。
     */
    private fun cake(action: Action, t: Double, s: FestivalScene.Result, base: Pose, o: Out, cradle: List<Pt>): Out {
        val dur = action.durMs.toDouble()
        val h = s.handOf(Item.MOONCAKE)!!
        val side = if (h == Hand.L) -1.0 else 1.0
        val e = env(t, 0.0, 600.0, dur - 1300, dur - 300)
        o.arm(h, mix(armOf(base, h), cradle, 0.8 * env(t, 200.0, 900.0, dur - 1400, dur - 500)))
        o.headAngle = 5 * side * e
        o.headDy = 1.0 * e
        o.gaze = Pt(0.6 * side * e, 0.9 * e)
        o.face = sunny(t > dur * 0.45 && t < dur * 0.7)
        o.flick = Flick.NORMAL
        return o
    }
}
