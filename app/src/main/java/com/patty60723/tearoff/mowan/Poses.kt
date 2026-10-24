package com.patty60723.tearoff.mowan

import kotlin.math.cos
import kotlin.math.sin

/** (x0, y0, x1, y1, …) → 點列。放在最外層，避免 Flick 和 Poses 互相等對方初始化。 */
internal fun pts(vararg xy: Double) = List(xy.size / 2) { Pt(xy[2 * it], xy[2 * it + 1]) }

/** 臉的表情：一般、晴天（閉眼笑）、雨天（皺眉） */
enum class Face { NORMAL, SUNNY, RAINY }

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
    val face: Face = Face.NORMAL,
    val flick: Flick = Flick.NORMAL,
    /** 撐傘時握傘的手（拳頭的位置）與傘的角度（度） */
    val hand: Pt? = null,
    val umbrellaAngle: Double = 0.0,
) {
    /** 整個角色往下移（沒有腳時落到地面上） */
    fun shifted(dy: Double): Pose {
        fun s(p: List<Pt>) = p.map { Pt(it.x, it.y + dy) }
        return copy(
            body = s(body), armL = s(armL), armR = s(armR), legL = s(legL), legR = s(legR), tail = s(tail),
            hand = hand?.let { Pt(it.x, it.y + dy) }, headDy = headDy + dy,
        )
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
}
