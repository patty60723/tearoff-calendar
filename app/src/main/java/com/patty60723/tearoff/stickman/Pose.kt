package com.patty60723.tearoff.stickman

/**
 * 火柴人的骨架姿勢：只記關節的位置，線條由 [StickmanRenderer] 畫。
 * 座標在 48×64 的格子裡，地面是 y = [GROUND]。
 */
class Pose(private val p: FloatArray) {
    init {
        require(p.size == SIZE)
    }

    fun x(joint: Int) = p[joint * 2]
    fun y(joint: Int) = p[joint * 2 + 1]

    /** 身體那條線往旁邊彎多少（只有一條線的時候用來扭動）。 */
    val bend get() = p[BEND]

    fun lerp(to: Pose, t: Float) = Pose(FloatArray(SIZE) { p[it] + (to.p[it] - p[it]) * t })

    fun moved(dx: Number = 0, dy: Number = 0) = Pose(
        FloatArray(SIZE) { i ->
            when {
                i == BEND -> p[i]
                i % 2 == 0 -> p[i] + dx.toFloat()
                else -> p[i] + dy.toFloat()
            }
        }
    )

    fun edit(block: Editor.() -> Unit) = Pose(p.copyOf().also { Editor(it).block() })

    class Editor(private val p: FloatArray) {
        fun at(joint: Int, x: Number, y: Number) {
            p[joint * 2] = x.toFloat()
            p[joint * 2 + 1] = y.toFloat()
        }

        fun bend(amount: Number) {
            p[BEND] = amount.toFloat()
        }
    }

    companion object {
        const val HEAD = 0
        const val NECK = 1
        const val HIP = 2
        const val ELBOW_L = 3
        const val HAND_L = 4
        const val ELBOW_R = 5
        const val HAND_R = 6
        const val KNEE_L = 7
        const val FOOT_L = 8
        const val KNEE_R = 9
        const val FOOT_R = 10
        private const val BEND = 22
        private const val SIZE = 23

        const val GROUND = 57f
        const val HEAD_RADIUS = 7f

        fun of(vararg values: Number) = Pose(FloatArray(SIZE) { values.getOrNull(it)?.toFloat() ?: 0f })
    }
}

/** 一個循環播放的動作：幾個關鍵姿勢，中間自動補間。 */
class Action(val id: String, val periodMs: Long, private val frames: List<Pose>) {
    /** t 是 0 到 1 之間的進度，超過會繞回來。 */
    fun at(t: Float): Pose {
        val n = frames.size
        val f = ((t % 1f) + 1f) % 1f * n
        val i = f.toInt() % n
        return frames[i].lerp(frames[(i + 1) % n], f - f.toInt())
    }
}

object Actions {
    private val STAND = Pose.of(
        24, 12, 24, 19, 24, 38,
        19, 29, 16, 35, 29, 29, 32, 35,
        21, 47, 19, 57, 27, 47, 29, 57,
        0,
    )

    val IDLE = Action(
        "idle", 1600,
        listOf(
            STAND,
            STAND.edit {
                at(Pose.HEAD, 24, 13); at(Pose.NECK, 24, 20)
                at(Pose.ELBOW_L, 19, 30); at(Pose.HAND_L, 16, 36)
                at(Pose.ELBOW_R, 29, 30); at(Pose.HAND_R, 32, 36)
            },
        ),
    )

    /** 只有一條線的時候：左右扭。 */
    val WIGGLE = Action("wiggle", 900, listOf(STAND.edit { bend(-4) }, STAND.edit { bend(4) }))

    val LOOK = Action(
        "look", 1400,
        listOf(STAND.edit { at(Pose.HEAD, 22, 12) }, STAND.edit { at(Pose.HEAD, 26, 12) }),
    )

    val WAVE = Action(
        "wave", 700,
        listOf(
            STAND.edit { at(Pose.ELBOW_R, 31, 23); at(Pose.HAND_R, 33, 13) },
            STAND.edit { at(Pose.ELBOW_R, 31, 23); at(Pose.HAND_R, 38, 16) },
        ),
    )

    val CLAP = Action(
        "clap", 600,
        listOf(
            STAND.edit {
                at(Pose.ELBOW_L, 19, 27); at(Pose.HAND_L, 15, 27)
                at(Pose.ELBOW_R, 29, 27); at(Pose.HAND_R, 33, 27)
            },
            STAND.edit {
                at(Pose.ELBOW_L, 19, 28); at(Pose.HAND_L, 23, 25)
                at(Pose.ELBOW_R, 29, 28); at(Pose.HAND_R, 25, 25)
            },
        ),
    )

    /** 單腳跳：右腳縮起來。 */
    val HOP: Action = STAND.edit {
        at(Pose.KNEE_L, 24, 48); at(Pose.FOOT_L, 24, 57)
        at(Pose.KNEE_R, 29, 44); at(Pose.FOOT_R, 32, 49)
        at(Pose.HAND_L, 14, 30); at(Pose.HAND_R, 34, 30)
    }.let { Action("hop", 600, listOf(it, it.moved(dy = -5))) }

    /** 往右走：左腳在前、右腳在前，中間兩個是腳交錯的時候。 */
    val WALK = Action(
        "walk", 900,
        listOf(
            STAND.edit {
                at(Pose.KNEE_L, 27, 47); at(Pose.FOOT_L, 31, 57)
                at(Pose.KNEE_R, 22, 47); at(Pose.FOOT_R, 17, 57)
                at(Pose.ELBOW_L, 21, 29); at(Pose.HAND_L, 17, 34)
                at(Pose.ELBOW_R, 27, 29); at(Pose.HAND_R, 31, 34)
            },
            STAND.edit {
                at(Pose.KNEE_L, 24, 47); at(Pose.FOOT_L, 23, 57)
                at(Pose.KNEE_R, 30, 44); at(Pose.FOOT_R, 27, 52)
                at(Pose.ELBOW_L, 21, 29); at(Pose.HAND_L, 20, 35)
                at(Pose.ELBOW_R, 27, 29); at(Pose.HAND_R, 28, 35)
            }.moved(dy = -1),
            STAND.edit {
                at(Pose.KNEE_L, 22, 47); at(Pose.FOOT_L, 17, 57)
                at(Pose.KNEE_R, 27, 47); at(Pose.FOOT_R, 31, 57)
                at(Pose.ELBOW_L, 27, 29); at(Pose.HAND_L, 31, 34)
                at(Pose.ELBOW_R, 21, 29); at(Pose.HAND_R, 17, 34)
            },
            STAND.edit {
                at(Pose.KNEE_L, 30, 44); at(Pose.FOOT_L, 27, 52)
                at(Pose.KNEE_R, 24, 47); at(Pose.FOOT_R, 23, 57)
                at(Pose.ELBOW_L, 27, 29); at(Pose.HAND_L, 28, 35)
                at(Pose.ELBOW_R, 21, 29); at(Pose.HAND_R, 20, 35)
            }.moved(dy = -1),
        ),
    )

    val BOUNCE = Action(
        "bounce", 600,
        listOf(STAND, STAND.edit { at(Pose.HAND_L, 13, 32); at(Pose.HAND_R, 35, 32) }.moved(dy = -3)),
    )

    val CHEER: Action = STAND.edit {
        at(Pose.ELBOW_L, 17, 21); at(Pose.HAND_L, 12, 12)
        at(Pose.ELBOW_R, 31, 21); at(Pose.HAND_R, 36, 12)
    }.let { up ->
        Action("cheer", 700, listOf(up.moved(dy = -4), up.edit { at(Pose.HAND_L, 10, 15); at(Pose.HAND_R, 38, 15) }))
    }

    /** 坐著喝茶：右手拿杯子慢慢喝。 */
    val SIT: Action = Pose.of(
        22, 26, 22, 33, 22, 50,
        25, 40, 29, 45, 26, 39, 27, 32,
        30, 48, 33, 57, 30, 49, 34, 57,
        0,
    ).let { sit ->
        Action("sit", 1800, listOf(sit, sit.edit { at(Pose.ELBOW_R, 26, 37); at(Pose.HAND_R, 26, 29) }))
    }

    val all: Map<String, Action> =
        listOf(IDLE, WIGGLE, LOOK, WAVE, CLAP, HOP, WALK, BOUNCE, CHEER, SIT).associateBy { it.id }

    fun get(id: String?): Action = all[id] ?: IDLE
}
