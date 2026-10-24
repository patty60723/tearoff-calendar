package com.patty60723.tearoff.mowan

import kotlin.math.cos
import kotlin.math.sin

/** (x0, y0, x1, y1, …) → 點列。放在最外層，避免 Flick 和 Poses 互相等對方初始化。 */
internal fun pts(vararg xy: Double) = List(xy.size / 2) { Pt(xy[2 * it], xy[2 * it + 1]) }

/** 臉的表情 */
enum class Face {
    NORMAL,
    /** 晴天：閉眼笑 */
    SUNNY,
    /** 雨天：皺眉 */
    RAINY,
    /** 熱：瞇成兩條線、平嘴 */
    HOT,
    /** 霧：瞇眼（扁橢圓）、抿嘴 */
    FOG,
    /** 冷：輕輕閉眼、細眉、打顫的小波浪嘴 */
    COLD,
    /** 雷雨的驚嚇：眼睛變大、「o」嘴、皺眉 */
    STARTLE,
}

/** 姿勢附帶的動作量，給繪製用（環境與這些效果在 W3 才畫） */
internal data class Effects(
    /** 熱：汗滴往下滑的進度 0–1；null 表示沒有汗滴 */
    val sweat: Double? = null,
    /** 冷、雪：圍巾拉高一點 */
    val scarfUp: Boolean = false,
    /** 雷雨：左手也握住傘柄 */
    val leftOnShaft: Boolean = false,
    /** 冷：呼出白氣的不透明度與飄出去的進度 */
    val breath: Double = 0.0,
    val breathU: Double = 0.0,
    /** 雷雨：閃電的不透明度 */
    val bolt: Double = 0.0,
)

/** 一筆的樣子：一般、晴天翹起來、雨天垂下來 */
internal enum class Flick(val shape: List<Pt>) {
    NORMAL(pts(0.0, 0.0, -0.8, -6.5, 3.5, -12.0, 11.0, -14.5)),
    PERKED(pts(0.0, 0.0, -0.5, -8.0, 4.0, -14.0, 11.5, -16.5)),
    DROOPED(pts(0.0, 0.0, -2.0, -4.0, -7.0, -5.5, -12.5, -3.0)),
}

/** 一張影格的骨架控制點 */
internal data class Pose(
    val body: List<Pt>, val armL: List<Pt>, val armR: List<Pt>,
    val legL: List<Pt>, val legR: List<Pt>, val tail: List<Pt>,
    val headAngle: Double = 0.0,
    val headDy: Double = 0.0,
    /** 發抖時頭跟著左右晃 */
    val headDx: Double = 0.0,
    /** 視線（眼睛的位移） */
    val gaze: Pt = Pt(0.0, 0.0),
    val face: Face = Face.NORMAL,
    val flick: Flick = Flick.NORMAL,
    /** 撐傘時握傘的手（拳頭的位置）與傘的角度（度） */
    val hand: Pt? = null,
    val umbrellaAngle: Double = 0.0,
    val effects: Effects = Effects(),
) {
    /** 整個角色往下移（沒有腳時落到地面上） */
    fun shifted(dy: Double): Pose {
        fun s(p: List<Pt>) = p.map { Pt(it.x, it.y + dy) }
        return copy(
            body = s(body), armL = s(armL), armR = s(armR), legL = s(legL), legR = s(legR), tail = s(tail),
            hand = hand?.let { Pt(it.x, it.y + dy) }, headDy = headDy + dy,
        )
    }

    /** 上半身左右抖 [dx]：越往上抖越多，腳不動；頭跟著抖 */
    fun jittered(dx: Double): Pose {
        if (dx == 0.0) return this
        fun s(p: List<Pt>) = p.map { Pt(it.x + dx * Geometry.clamp01((101 - it.y) / 33), it.y) }
        return copy(body = s(body), armL = s(armL), armR = s(armR), tail = s(tail), headDx = headDx + dx)
    }
}

/**
 * V3 的三個天氣姿勢，照原型 engine.js 的 pose() 移植。時間單位是毫秒，姿勢會循環。
 */
internal object Poses {
    private val LEG_L = pts(61.0, 101.0, 59.0, 109.0, 57.6, 118.0, 57.0, 127.0, 52.4, 127.6)
    private val LEG_R = pts(61.0, 101.0, 64.8, 108.0, 66.8, 114.0, 69.2, 119.6, 70.6, 124.2, 71.0, 126.6, 75.0, 125.6)
    private val TAIL = pts(54.0, 70.6, 53.2, 76.0, 53.4, 81.2, 51.8, 87.2)

    /** 慢慢伸懶腰的週期（Confirmed：約 5.5 秒） */
    const val SUNNY_MS = 5500.0
    /** 撐傘輕輕晃的週期（Confirmed：約 4.2 秒） */
    const val RAINY_MS = 4200.0

    val NORMAL = Pose(
        body = pts(60.5, 68.6, 61.4, 78.0, 59.6, 90.0, 61.0, 101.0),
        armL = pts(60.5, 77.0, 55.5, 82.5, 52.6, 89.0, 51.6, 96.0),
        armR = pts(60.8, 77.0, 65.8, 80.8, 68.8, 86.2, 67.6, 93.0),
        legL = LEG_L, legR = LEG_R, tail = TAIL,
    )

    fun sunny(time: Double): Pose {
        val s = (1 - cos(TAU * time / SUNNY_MS)) / 2
        // 雙手往斜上方打開伸展，手一直在頭的外側
        val armL = pts(60.5, 77.0, 53.0, 72 - 1.5 * s, 44.5 - 1.5 * s, 65 - 3 * s, 36.5 - 2.5 * s, 57 - 5 * s, 31 - 3 * s, 52 - 6 * s)
        val armR = armL.mapIndexed { i, (x, y) -> if (i == 0) Pt(60.8, 77.0) else Pt(121.2 - x, y) }
        // 圍巾尾巴被風吹起來
        val sway = Math.toRadians(30 + 6 * sin(TAU * time / 2600))
        val tail = TAIL.map { (x, y) ->
            val dx = x - 54
            val dy = y - 70.6
            Pt(54 + dx * cos(sway) + dy * sin(sway), 70.6 - dx * sin(sway) + dy * cos(sway))
        }
        return Pose(
            body = pts(60.5, 68.6, 62.6 + 1.2 * s, 78.0, 61.6 + 0.6 * s, 90.0, 61.0, 101.0),
            armL = armL, armR = armR, legL = LEG_L, legR = LEG_R, tail = tail,
            headAngle = -4 - 8 * s, face = Face.SUNNY, flick = Flick.PERKED,
        )
    }

    fun rainy(time: Double) = Pose(
        body = pts(60.5, 68.6, 59.1, 79.0, 59.6, 90.0, 61.0, 101.0),
        armL = pts(60.5, 77.0, 55.0, 80.2, 53.6, 85.6, 57.6, 87.6),
        armR = pts(60.8, 77.0, 67.0, 80.6, 75.0, 81.4, 80.5, 79.0),
        legL = pts(61.0, 101.0, 59.6, 109.0, 58.6, 118.0, 58.0, 127.0, 53.5, 127.5),
        legR = pts(61.0, 101.0, 63.0, 109.0, 63.8, 118.0, 64.5, 127.0, 69.0, 127.0),
        tail = pts(54.0, 70.6, 53.4, 76.0, 53.8, 81.6, 53.6, 88.0),
        headAngle = 3.0, face = Face.RAINY, flick = Flick.DROOPED,
        // 以手為支點輕輕晃
        hand = Pt(82.0, 78.0), umbrellaAngle = -10 + 1.8 * sin(TAU * time / RAINY_MS),
    )

    // ---------- Phase 2B 定稿的天氣姿勢（docs/prototypes/src/weather.js） ----------
    // 手臂都是 5 個控制點，關鍵影格才能互相內插。放下的手跟一般站姿同長、同一個手尖位置。

    private object Arm {
        val L_DEF = pts(60.5, 77.0, 56.8, 80.6, 54.0, 85.0, 52.6, 90.2, 51.6, 96.0)
        val R_DEF = pts(60.8, 77.0, 64.3, 79.6, 67.4, 83.2, 69.1, 87.8, 67.6, 93.0)
        // 熱：手沿著右臉頰外側上下擦（太陽穴在頭的後面，手伸過去會被頭擋住）
        val R_WIPE_LO = pts(60.8, 77.0, 67.5, 74.5, 74.0, 70.5, 79.0, 65.0, 82.4, 59.6)
        val R_WIPE_HI = pts(60.8, 77.0, 68.5, 73.0, 76.0, 67.0, 82.0, 59.5, 85.2, 51.6)
        // 抬手、放手的中間姿勢：手臂在過程中維持長度
        val R_WIPE_SIDE = pts(60.8, 77.0, 65.6, 80.6, 70.8, 82.6, 76.2, 82.2, 80.8, 79.6)
        val R_WIPE_MID = pts(60.8, 77.0, 66.6, 78.8, 72.2, 77.6, 77.0, 74.4, 80.6, 69.6)
        // 霧：左手低低地往前伸，慢慢伸出、收回
        val L_PROBE_IN = pts(60.5, 77.0, 55.2, 80.6, 49.6, 83.0, 44.0, 84.4, 39.6, 84.6)
        val L_PROBE_OUT = pts(60.5, 77.0, 54.6, 80.0, 48.2, 82.2, 41.8, 83.4, 36.2, 83.4)
        // 冷：雙手從兩側繞到身體前面，手尖碰在一起，不交叉
        val L_HUG = pts(60.5, 77.0, 55.2, 80.6, 54.8, 85.4, 58.2, 88.0, 62.6, 87.6)
        val R_HUG = pts(60.8, 77.0, 66.2, 80.6, 66.6, 85.4, 63.2, 88.2, 58.8, 87.8)
        // 雪：手臂收在身體兩側，微微夾緊
        val L_TUCK = pts(60.5, 77.0, 58.4, 81.2, 57.4, 86.0, 57.2, 91.0, 57.0, 95.6)
        val R_TUCK = pts(60.8, 77.0, 63.0, 81.0, 64.0, 85.8, 64.2, 90.8, 64.4, 95.4)
        // 雷雨：右手撐傘
        val R_UMBRELLA = pts(60.8, 77.0, 66.5, 80.6, 73.5, 81.4, 79.0, 79.6)
    }

    const val HOT_MS = 3200.0
    const val CLOUDY_MS = 6000.0
    const val FOG_MS = 5000.0
    const val COLD_MS = 2800.0
    const val SNOW_MS = 4000.0

    /** 新天氣的基本站姿：一般站姿換成 5 點的手臂 */
    private fun base() = NORMAL.copy(armL = Arm.L_DEF, armR = Arm.R_DEF)

    /** 熱：右手擦汗兩下再放下，汗滴往下滑，一筆被曬軟。沒有右手時不擦。 */
    fun hot(time: Double, caps: Capabilities): Pose {
        val u = time % HOT_MS
        var p = base().copy(face = Face.HOT, flick = Flick.DROOPED, effects = Effects(sweat = u / HOT_MS))
        if (caps.holdR) {
            // D-22：抬手 0–650 ms、擦三下 650–1850 ms（每下 400 ms）、放手 1850–2500 ms、休息到 3200 ms
            val arm = when {
                u <= 650 -> Keyframes.path(u, 0.0, 650.0, listOf(Arm.R_DEF, Arm.R_WIPE_SIDE, Arm.R_WIPE_MID, Arm.R_WIPE_LO))
                u <= 1850 -> Keyframes.trackPts(u, listOf(650.0 to Arm.R_WIPE_LO, 1050.0 to Arm.R_WIPE_HI, 1450.0 to Arm.R_WIPE_LO, 1850.0 to Arm.R_WIPE_HI))
                u <= 2500 -> Keyframes.path(u, 1850.0, 2500.0, listOf(Arm.R_WIPE_HI, Arm.R_WIPE_MID, Arm.R_WIPE_SIDE, Arm.R_DEF))
                else -> Arm.R_DEF
            }
            p = p.copy(armR = arm, headAngle = Keyframes.track(u, listOf(0.0 to 0.0, 650.0 to 4.0, 1850.0 to 4.0, 2500.0 to 0.0)))
        }
        return p
    }

    /** 陰：抬頭，視線跟著雲慢慢左右看。沒有眼睛時只轉頭。 */
    fun cloudy(time: Double, caps: Capabilities): Pose {
        val s = sin(TAU * time / CLOUDY_MS)
        return base().copy(
            headAngle = -3 + 3 * s, headDy = -0.6,
            gaze = if (caps.gaze) Pt(1.4 * s, -1.9) else Pt(0.0, 0.0),
        )
    }

    /** 霧：探頭、瞇眼，左手往前試探。沒有左手時不伸手。 */
    fun fog(time: Double, caps: Capabilities): Pose {
        val lean = doubleArrayOf(1.2, 0.8, 0.3, 0.0)
        var p = base().copy(
            face = Face.FOG, headAngle = -4.0, headDy = 0.8,
            body = NORMAL.body.mapIndexed { i, (x, y) -> Pt(x - lean[i], y) },
        )
        if (caps.holdL) p = p.copy(armL = Keyframes.mix(Arm.L_PROBE_IN, Arm.L_PROBE_OUT, (1 - cos(TAU * time / FOG_MS)) / 2))
        return p
    }

    /**
     * 雷雨：撐傘為主，左手有空就一起握住傘柄；打雷時短暫嚇一跳。
     * @param sinceStrikeMs 打雷後經過的毫秒數；null 表示現在沒有打雷。什麼時候打雷由排程決定（W5）。
     */
    fun thunder(time: Double, caps: Capabilities, sinceStrikeMs: Double? = null): Pose {
        val since = sinceStrikeMs
        val startle = if (since == null) 0.0 else Keyframes.env(since, 20.0, 120.0, 360.0, 800.0)
        val bolt = if (since == null) 0.0 else Keyframes.env(since, 0.0, 40.0, 220.0, 320.0)
        var p = if (!caps.holdR) {
            base()
        } else {
            val angle = -6 + 0.8 * sin(TAU * time / RAINY_MS)
            val hand = Pt(80.5, 79.0)
            var q = rainy(0.0).copy(armL = Arm.L_DEF, armR = Arm.R_UMBRELLA, hand = hand, umbrellaAngle = angle)
            if (caps.holdL) {
                // 左手握在右手下面 6 個單位的傘柄上
                val grip = Geometry.place(listOf(Pt(0.0, 6.0)), Geometry.Placement(hand.x, hand.y, Math.toRadians(angle)))[0]
                q = q.copy(armL = pts(60.5, 77.0, 66.0, 82.0, 73.5, 85.4) + grip, effects = q.effects.copy(leftOnShaft = true))
            }
            q
        }
        val scared = startle > 0.5
        p = p.copy(
            face = if (scared) Face.STARTLE else Face.RAINY,
            flick = if (scared) Flick.PERKED else Flick.DROOPED,
            headAngle = p.headAngle + 1.2 * startle,
        )
        p = p.jittered(if (since == null) 0.0 else 0.35 * startle * sin(TAU * since / 90))
        return p.copy(effects = p.effects.copy(bolt = bolt))
    }

    /** 冷：輕抱自己、縮脖子，一陣一陣微微發抖，有嘴巴以後呼白氣。 */
    fun cold(time: Double, caps: Capabilities): Pose {
        val u = time % COLD_MS
        var p = base().copy(face = Face.COLD, flick = Flick.DROOPED, headDy = 0.8)
        if (caps.holdL) p = p.copy(armL = Arm.L_HUG)
        if (caps.holdR) p = p.copy(armR = Arm.R_HUG)
        val burst = maxOf(Keyframes.env(u, 0.0, 60.0, 600.0, 700.0), Keyframes.env(u, 1400.0, 1460.0, 2000.0, 2100.0))
        p = p.jittered(0.45 * burst * sin(TAU * u / 85))
        return p.copy(effects = Effects(
            scarfUp = true,
            breath = if (caps.smile) Keyframes.env(u, 800.0, 1000.0, 1300.0, 1700.0) else 0.0,
            breathU = Geometry.clamp01((u - 800) / 900),
        ))
    }

    /** 雪（B）：抬頭看飄下來的雪，手收在身體兩側，每 4 秒輕輕縮一下。 */
    fun snow(time: Double, caps: Capabilities): Pose {
        val w = sin(TAU * time / SNOW_MS)
        var p = base().copy(
            headAngle = -4 + 1.5 * w, headDy = 0.5,
            gaze = if (caps.gaze) Pt(0.9 * w, -1.8) else Pt(0.0, 0.0),
            effects = Effects(scarfUp = true),
        )
        if (caps.holdL) p = p.copy(armL = Arm.L_TUCK)
        if (caps.holdR) p = p.copy(armR = Arm.R_TUCK)
        val u = time % SNOW_MS
        return p.jittered(0.15 * Keyframes.env(u, 3000.0, 3060.0, 3500.0, 3600.0) * sin(TAU * u / 85))
    }
}
