package com.patty60723.tearoff.mowan

import kotlin.math.abs

/**
 * 兩個既有姿勢之間的過渡（P7-2，D-48：點故事卡上的墨丸時，經由中性姿勢自然銜接，不突然跳回第一格）。
 * 只在兩個已經存在的姿勢之間插值，不產生新的美術：骨架的點、角度、位移連續變化；
 * 表情、筆的翹法、圍巾拉高這類分段的東西在過渡的一半換過去（原本的天氣動作裡也是這樣分段換的）。
 * 汗滴、呼出的白氣本來就每一輪重新開始一滴／一口：也在過渡的一半換成新的一滴／一口，不倒流。
 */
internal object PoseBlend {
    /** 平順的起步與收尾（smoothstep） */
    fun ease(u: Double): Double {
        val t = Geometry.clamp01(u)
        return t * t * (3 - 2 * t)
    }

    private fun mix(a: List<Pt>, b: List<Pt>, u: Double) = if (a.size == b.size) Keyframes.mix(a, b, u) else if (u < 0.5) a else b

    private fun mix(a: Pt?, b: Pt?, u: Double) = when {
        a != null && b != null -> Pt(Geometry.lerp(a.x, b.x, u), Geometry.lerp(a.y, b.y, u))
        u < 0.5 -> a
        else -> b
    }

    /** [from] 往 [to] 走了 [u]（0–1，已經套用過緩動） */
    fun mix(from: Pose, to: Pose, u: Double): Pose {
        val late = u >= 0.5
        val fe = from.effects
        val te = to.effects
        return Pose(
            body = mix(from.body, to.body, u), armL = mix(from.armL, to.armL, u), armR = mix(from.armR, to.armR, u),
            legL = mix(from.legL, to.legL, u), legR = mix(from.legR, to.legR, u), tail = mix(from.tail, to.tail, u),
            headAngle = Geometry.lerp(from.headAngle, to.headAngle, u),
            headDy = Geometry.lerp(from.headDy, to.headDy, u),
            headDx = Geometry.lerp(from.headDx, to.headDx, u),
            gaze = mix(from.gaze, to.gaze, u)!!,
            face = if (late) to.face else from.face,
            flick = if (late) to.flick else from.flick,
            hand = mix(from.hand, to.hand, u),
            umbrellaAngle = Geometry.lerp(from.umbrellaAngle, to.umbrellaAngle, u),
            effects = Effects(
                sweat = if (late) te.sweat else fe.sweat,
                scarfUp = if (late) te.scarfUp else fe.scarfUp,
                leftOnShaft = if (late) te.leftOnShaft else fe.leftOnShaft,
                breath = if (late) te.breath else fe.breath,
                breathU = if (late) te.breathU else fe.breathU,
                // 閃電照原本的打雷時間（兩邊算的是同一次打雷），不會因為過渡多出一次
                bolt = maxOf(fe.bolt, te.bolt),
            ),
            neckDy = Geometry.lerp(from.neckDy, to.neckDy, u),
        )
    }

    private fun dist(a: Pt, b: Pt) = Math.hypot(a.x - b.x, a.y - b.y)

    /** 兩個姿勢差多遠（viewBox 單位，角色約 120 寬）：骨架點的最大位移，頭與傘的角度換算成弧長 */
    fun distance(a: Pose, b: Pose): Double {
        fun d(x: List<Pt>, y: List<Pt>) = if (x.size != y.size) 20.0 else x.indices.maxOfOrNull { dist(x[it], y[it]) } ?: 0.0
        val joints = listOf(d(a.body, b.body), d(a.armL, b.armL), d(a.armR, b.armR), d(a.legL, b.legL), d(a.legR, b.legR), d(a.tail, b.tail)).max()
        // 頭的半徑約 15、傘柄加傘面約 45：角度差（度）換成最外緣移動的距離
        val head = abs(a.headAngle - b.headAngle) * Math.PI / 180 * 15 + abs(a.headDy - b.headDy) + abs(a.headDx - b.headDx)
        val umbrella = abs(a.umbrellaAngle - b.umbrellaAngle) * Math.PI / 180 * 45
        val hand = if (a.hand != null && b.hand != null) dist(a.hand, b.hand) else 0.0
        return maxOf(joints, head, umbrella, hand)
    }

    /**
     * 過渡要多久：起點參考 0.3 秒，差得越遠越久（手臂、頭、身體不會猛然彈回），最長 0.6 秒。
     * 一律過渡（過渡從目前的姿勢開始，點下去那一刻一定不會跳）；只有剛好在這一輪的起點上才直接開始，見 [Mowan.reactionBlendMs]。
     */
    fun durationMs(distance: Double): Long = (300 + distance * 8).coerceIn(300.0, 600.0).toLong()
}
