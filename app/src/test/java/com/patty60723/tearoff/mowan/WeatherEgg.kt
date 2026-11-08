package com.patty60723.tearoff.mowan

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * P7-9a 天氣彩蛋（方案 C：日曆小墨丸 → 故事卡大墨丸的接力）的**預覽用**分鏡。只在測試裡，不接正式 App。
 *
 * 每一種天氣是一段時間軸（毫秒，從點擊「天氣資訊」開始）：
 * - [small]：日曆頁上的小墨丸（平常是靜止圖，彩蛋期間動起來，最後回到原本那一格）。
 * - [big]：故事卡上的大墨丸（平常是天氣日常的循環，彩蛋期間接手主演出，最後接回日常循環）。
 * - [motes]：天氣素材（光點、雨滴、雪花）：先在小墨丸那裡進場，再從日曆「接力」飛到故事卡。
 * 姿勢都用正式的骨架（[Pose]）與正式的繪製（[Mowan.render]），所以造型、筆觸、配色都跟現在一樣；
 * 畫面照正式的節奏每 1/6 秒取一次（6 FPS 的手繪感）。
 */
internal object WeatherEgg {
    /** 一個天氣素材：在哪一個墨丸的座標（"small"、"big"）或接力中（"bridge"，用 0–1 的進度） */
    data class Mote(val kind: Kind, val space: String, val x: Double, val y: Double, val size: Double, val alpha: Double, val u: Double = 0.0)

    enum class Kind { LIGHT, DROP, SPLASH, FLAKE, PUFF, RING }

    /** 一段彩蛋：時間軸與每個時間點的姿勢、素材 */
    interface Script {
        val weather: Mowan.Weather
        val durMs: Double
        /** 分鏡的段落（給文件與預覽顯示）：開始時間、名稱 */
        val beats: List<Pair<Double, String>>
        fun small(t: Double, caps: Capabilities): Pose
        fun big(t: Double, caps: Capabilities): Pose
        fun motes(t: Double): List<Mote>
        /** 點擊回饋：天氣資訊那裡淡淡亮一下（0–1） */
        fun tap(t: Double) = Keyframes.env(t, 0.0, 80.0, 220.0, 600.0)
        /** 大墨丸的日常時鐘（接回日常循環用） */
        fun daily(clock: Double, caps: Capabilities): Pose
        /** 小墨丸在日曆上的靜止那一格（跟 MowanBitmap.still 一樣） */
        fun still(caps: Capabilities): Pose
    }

    // ---------- 共用：手臂、混合 ----------

    private const val DEG = PI / 180

    /**
     * 依 [ref] 的節數與每一節長度，做一隻往 [dir] 度方向伸、每節再彎 [bend] 度的手臂（0 度＝垂直往下，正值＝往身體外側）。
     * 長度永遠跟 [ref] 一樣，所以內插角度時手不會變長變短（不會「斷手」）。
     */
    fun arm(ref: List<Pt>, left: Boolean, dir: Double, bend: Double): List<Pt> {
        val out = ArrayList<Pt>(ref.size)
        out += ref[0]
        val side = if (left) -1.0 else 1.0
        var x = ref[0].x
        var y = ref[0].y
        val n = ref.size - 1
        for (i in 1..n) {
            val len = hypot(ref[i].x - ref[i - 1].x, ref[i].y - ref[i - 1].y)
            val a = (dir + bend * (i - 1).toDouble() / maxOf(1, n - 1)) * DEG
            x += side * sin(a) * len
            y += cos(a) * len
            out += Pt(x, y)
        }
        return out
    }

    /** 角度的關鍵影格（時間, 方向, 彎） */
    fun armTrack(ref: List<Pt>, left: Boolean, t: Double, keys: List<Triple<Double, Double, Double>>): List<Pt> {
        val dir = Keyframes.track(t, keys.map { it.first to it.second })
        val bend = Keyframes.track(t, keys.map { it.first to it.third })
        return arm(ref, left, dir, bend)
    }

    /** 從 [from] 過渡到 [to]：[a] 開始、[b] 完成 */
    fun blend(t: Double, a: Double, b: Double, from: Pose, to: Pose) = PoseBlend.mix(from, to, Keyframes.seg(t, a, b))

    /** 一小段上下彈跳（往上是負的） */
    fun hop(t: Double, a: Double, peak: Double, b: Double, h: Double) = when {
        t < a || t > b -> 0.0
        t < peak -> -h * sin((t - a) / (peak - a) * PI / 2)
        else -> -h * cos((t - peak) / (b - peak) * PI / 2)
    }

    /** 接力的路徑：0–1 的進度，帶一點左右飄 */
    fun bridge(kind: Kind, t: Double, a: Double, b: Double, size: Double, sway: Double = 0.0): Mote? {
        if (t < a || t > b) return null
        val u = (t - a) / (b - a)
        return Mote(kind, "bridge", sway * sin(u * PI * 2.2), 0.0, size, Keyframes.env(u, 0.0, 0.1, 0.85, 1.0), Keyframes.ease(u))
    }

    val ALL = listOf(Sunny, Rainy, Snowy)

    // ---------- 晴天：光點 → 小墨丸被太陽照到打噴嚏 → 大墨丸踮腳伸大懶腰 ----------

    object Sunny : Script {
        override val weather = Mowan.Weather.SUNNY
        override val durMs = 7200.0
        override val beats = listOf(
            0.0 to "點擊：天氣資訊亮一下",
            200.0 to "光點從太陽飄下來",
            900.0 to "小墨丸察覺：伸到一半的手放下、睜眼看光點",
            1500.0 to "吸氣（頭往後仰）",
            1750.0 to "哈啾！（被陽光照到打噴嚏）",
            2150.0 to "揉揉鼻子、笑，把光點往下送",
            2500.0 to "接力：光點從日曆飄到故事卡",
            3300.0 to "大墨丸：光點落在頭上，停下來抬頭",
            3700.0 to "蹲低（預備）",
            4100.0 to "踮腳伸一個比平常更大的懶腰",
            5100.0 to "放鬆、開心地彈一下",
            5900.0 to "接回日常：慢慢伸懶腰的循環",
        )

        override fun daily(clock: Double, caps: Capabilities) = Poses.sunny(clock)
        override fun still(caps: Capabilities) = Poses.sunny(2000.0)

        override fun small(t: Double, caps: Capabilities): Pose {
            val s0 = still(caps)
            val normal = Poses.sunny(0.0)
            // 手從伸懶腰放下、睜眼看光點
            var p = blend(t, 900.0, 1350.0, s0, normal.copy(face = Face.NORMAL, flick = Flick.NORMAL, headAngle = -6.0, gaze = Pt(1.2, -1.6)))
            // 吸氣：頭往後仰、身體微微抬
            if (t >= 1500 && t < 2150) {
                val inhale = Keyframes.env(t, 1500.0, 1700.0, 1720.0, 1800.0)
                val sneeze = Keyframes.env(t, 1750.0, 1800.0, 1880.0, 2100.0)
                p = p.copy(headAngle = -6 - 6 * inhale + 14 * sneeze, gaze = Pt(1.2 - 1.2 * sneeze, -1.6 + 2.4 * sneeze), flick = if (sneeze > 0.3) Flick.PERKED else Flick.NORMAL)
                    .shifted(-1.0 * inhale + 0.8 * sneeze).jittered(0.6 * sneeze * sin(t / 18))
            }
            // 揉鼻子、笑；右手往下（故事卡的方向）送一下
            if (t >= 2100) {
                val rub = Keyframes.env(t, 2100.0, 2250.0, 2450.0, 2600.0)
                val point = Keyframes.env(t, 2450.0, 2650.0, 2900.0, 3150.0)
                val l = arm(normal.armL, true, 40 - 70 * rub, -30 * rub)
                val r = arm(normal.armR, false, 30 + 35 * point, -10 * point)
                p = p.copy(armL = Keyframes.mix(p.armL, l, maxOf(rub, 0.0)), armR = Keyframes.mix(p.armR, r, point),
                    face = if (t > 2250) Face.SUNNY else p.face, headAngle = p.headAngle + 4 * point, gaze = Pt(-1.0 * point, 1.4 * point))
            }
            // 回到日曆上原本那一格
            return blend(t, 3050.0, 3700.0, p, s0)
        }

        override fun big(t: Double, caps: Capabilities): Pose {
            val clock = t + 1200
            val d = daily(clock, caps)
            if (t < 3300) return d
            val look = Poses.sunny(0.0).copy(face = Face.NORMAL, flick = Flick.NORMAL, headAngle = -8.0, gaze = Pt(0.2, -2.0))
            val ref = Poses.sunny(0.0)
            // 蹲低預備（手往後下）→ 踮腳大伸展（手直直往上、頭往後仰、閉眼笑）
            val crouch = Keyframes.env(t, 3700.0, 3950.0, 4000.0, 4250.0)
            val stretch = Keyframes.env(t, 4100.0, 4600.0, 5000.0, 5400.0)
            val dir = Keyframes.track(t, listOf(3300.0 to 120.0, 3700.0 to 25.0, 3950.0 to 15.0, 4600.0 to 168.0, 5000.0 to 172.0, 5500.0 to 120.0))
            val bend = Keyframes.track(t, listOf(3300.0 to 8.0, 3950.0 to -10.0, 4600.0 to 4.0, 5500.0 to 8.0))
            var p = look.copy(armL = arm(ref.armL, true, dir, bend), armR = arm(ref.armR, false, dir, bend))
            p = p.copy(headAngle = -8 - 6 * stretch, face = if (stretch > 0.4 || t > 5000) Face.SUNNY else Face.NORMAL, flick = if (stretch > 0.2 || t > 5100) Flick.PERKED else Flick.NORMAL,
                gaze = Pt(0.2, -2.0 * (1 - stretch)))
                .shifted(1.8 * crouch - 1.6 * stretch + hop(t, 5100.0, 5300.0, 5550.0, 2.4))
            // 接手：日常 → 抬頭看光點；最後接回日常
            val into = blend(t, 3300.0, 3700.0, d, p)
            return blend(t, 5900.0, 6900.0, into, d)
        }

        override fun motes(t: Double): List<Mote> {
            val m = ArrayList<Mote>()
            // 三顆光點從太陽（右上）飄到小墨丸鼻子附近
            for (i in 0 until 3) {
                val a = 200.0 + i * 180
                val u = Geometry.clamp01((t - a) / 1100)
                if (t < a || t > 1900 + i * 60) continue
                val x = Geometry.lerp(96.0 - i * 6, 66.0 + i * 5, Keyframes.ease(u)) + 1.5 * sin(t / 160 + i)
                val y = Geometry.lerp(10.0 + i * 3, 50.0 - i * 4, Keyframes.ease(u))
                m += Mote(Kind.LIGHT, "small", x, y, 2.6 - 0.4 * i, Keyframes.env(t, a, a + 200, 1700.0, 1900.0 + i * 60))
            }
            bridge(Kind.LIGHT, t, 2500.0, 3350.0, 3.2, 0.06)?.let { m += it }
            // 大墨丸身邊：光點落在頭上，伸懶腰時亮晶晶
            if (t in 3300.0..5600.0) {
                m += Mote(Kind.LIGHT, "big", 61.0, 14.0, 3.0, Keyframes.env(t, 3300.0, 3400.0, 3650.0, 3900.0))
                for (i in 0 until 4) {
                    val a = 4300.0 + i * 120
                    m += Mote(Kind.LIGHT, "big", 60 + 34 * cos(i * 1.6 + 0.4), 30 - 18 * sin(i * 1.6 + 0.4), 2.2, Keyframes.env(t, a, a + 150, 5000.0, 5500.0))
                }
            }
            return m
        }
    }

    // ---------- 雨天：雨滴打在傘上 → 小墨丸探出手、被淋到縮回 → 大墨丸仰頭被淋、甩傘轉圈、踩水 ----------

    object Rainy : Script {
        override val weather = Mowan.Weather.RAINY
        override val durMs = 7600.0
        override val beats = listOf(
            0.0 to "點擊：天氣資訊亮一下",
            150.0 to "雨開始落下（日曆上的雨滴動起來）",
            700.0 to "一滴大雨打在小墨丸的傘上（啪）",
            900.0 to "小墨丸抬頭看傘",
            1300.0 to "把傘往後斜、伸出左手接雨",
            1900.0 to "雨滴落在手上：縮手、皺眉抖一下",
            2300.0 to "看向故事卡；一滴雨從傘緣滾下去",
            2500.0 to "接力：雨滴從日曆落到故事卡",
            3300.0 to "大墨丸：雨滴打在傘上，抬頭",
            3700.0 to "把傘往後斜、仰頭看天（被淋到臉）",
            4350.0 to "趕快把傘轉回來、甩一甩（水珠飛出去）",
            4950.0 to "踩一下水窪（濺起水花）",
            5700.0 to "滿意地撐好傘",
            6400.0 to "接回日常：撐傘輕輕晃",
        )

        override fun daily(clock: Double, caps: Capabilities): Pose = if (caps.holdR) Poses.rainy(clock) else Poses.NORMAL.copy(face = Face.RAINY, flick = Flick.DROOPED)
        override fun still(caps: Capabilities) = daily(1050.0, caps)

        override fun small(t: Double, caps: Capabilities): Pose {
            val s0 = still(caps)
            // 被打到：頭縮一下、抬頭看傘
            val hit = Keyframes.env(t, 700.0, 760.0, 820.0, 1000.0)
            val look = Keyframes.env(t, 900.0, 1150.0, 2200.0, 2500.0)
            // 傘往後斜、左手伸出去接雨；被淋到縮手
            val tilt = Keyframes.env(t, 1300.0, 1650.0, 1950.0, 2250.0)
            val reach = Keyframes.env(t, 1400.0, 1750.0, 1900.0, 2100.0)
            val flinch = Keyframes.env(t, 1900.0, 1960.0, 2100.0, 2400.0)
            val toward = Keyframes.env(t, 2300.0, 2500.0, 2800.0, 3100.0)
            var p = s0.copy(
                headAngle = s0.headAngle - 8 * look + 3 * hit - 2 * tilt,
                gaze = Pt(0.6 * look - 1.4 * toward, -1.8 * look + 1.6 * toward),
                umbrellaAngle = s0.umbrellaAngle + 16 * tilt,
                face = if (flinch > 0.2) Face.STARTLE else Face.RAINY,
            )
            p = p.copy(armL = Keyframes.mix(s0.armL, arm(s0.armL, true, 75.0, -15.0), reach * (1 - flinch)))
            return p.shifted(0.8 * hit + 0.6 * flinch).jittered(0.5 * flinch * sin(t / 16))
        }

        override fun big(t: Double, caps: Capabilities): Pose {
            val clock = t + 800
            val d = daily(clock, caps)
            if (t < 3300) return d
            val base = Poses.rainy(0.0)
            val hit = Keyframes.env(t, 3300.0, 3360.0, 3420.0, 3600.0)
            val lookUp = Keyframes.env(t, 3400.0, 3650.0, 4300.0, 4500.0)
            val tilt = Keyframes.env(t, 3700.0, 4050.0, 4250.0, 4400.0)
            // 甩傘：角度來回、越來越小
            val shake = if (t in 4350.0..5000.0) 22 * sin((t - 4350) / 650 * PI * 3) * (1 - (t - 4350) / 650) else 0.0
            val hopY = hop(t, 4950.0, 5150.0, 5350.0, 3.0)
            var p = base.copy(
                headAngle = 3 - 12 * lookUp + 2 * hit,
                gaze = Pt(0.4 * lookUp, -2.0 * lookUp),
                umbrellaAngle = -10 + 32 * tilt + shake,
                face = if (t in 4100.0..4500.0) Face.STARTLE else if (t > 5300) Face.NORMAL else Face.RAINY,
                flick = if (t > 5300) Flick.NORMAL else Flick.DROOPED,
            )
            // 踩水：左腳抬一下（整個身體彈起、落下）
            p = p.shifted(hopY + 0.8 * hit)
            val into = blend(t, 3300.0, 3500.0, d, p)
            return blend(t, 6400.0, 7400.0, into, d)
        }

        override fun motes(t: Double): List<Mote> {
            val m = ArrayList<Mote>()
            // 小墨丸：一滴大雨落在傘頂（傘的中心約在握點上方）
            if (t in 300.0..760.0) m += Mote(Kind.DROP, "small", 74.0, Geometry.lerp(-8.0, 30.0, Keyframes.ease((t - 300) / 460)), 3.0, 1.0)
            if (t in 720.0..1100.0) m += Mote(Kind.SPLASH, "small", 74.0, 32.0, 3.0, Keyframes.env(t, 720.0, 760.0, 900.0, 1100.0), (t - 720) / 380)
            // 落在伸出去的左手上
            if (t in 1500.0..1920.0) m += Mote(Kind.DROP, "small", 38.0, Geometry.lerp(20.0, 82.0, Keyframes.ease((t - 1500) / 420)), 2.6, 1.0)
            if (t in 1900.0..2250.0) m += Mote(Kind.SPLASH, "small", 37.0, 84.0, 2.4, Keyframes.env(t, 1900.0, 1940.0, 2050.0, 2250.0), (t - 1900) / 350)
            // 接力：一滴從傘緣滾下去，落到故事卡
            bridge(Kind.DROP, t, 2500.0, 3330.0, 3.4)?.let { m += it }
            // 大墨丸：打在傘上
            if (t in 3300.0..3700.0) m += Mote(Kind.SPLASH, "big", 82.0, 34.0, 4.0, Keyframes.env(t, 3300.0, 3340.0, 3500.0, 3700.0), (t - 3300) / 400)
            // 仰頭時臉被淋到
            if (t in 3950.0..4300.0) m += Mote(Kind.DROP, "big", 58.0, Geometry.lerp(10.0, 44.0, Keyframes.ease((t - 3950) / 350)), 2.6, Keyframes.env(t, 3950.0, 4000.0, 4250.0, 4300.0))
            // 甩傘：水珠從傘緣飛出去
            if (t in 4400.0..5000.0) for (i in 0 until 5) {
                val u = (t - 4400) / 600
                val a = -0.6 + i * 0.5
                m += Mote(Kind.DROP, "big", 82 + cos(a) * (30 + 26 * u), 30 - sin(a) * (14 + 20 * u) + 30 * u * u, 2.0, 1 - u)
            }
            // 踩水：水窪濺起水花與漣漪
            if (t in 5300.0..5900.0) {
                val u = (t - 5300) / 600
                m += Mote(Kind.RING, "big", 62.0, 128.6, 18 + 12 * u, 1 - u)
                for (i in 0 until 4) m += Mote(Kind.DROP, "big", 62 + (i - 1.5) * 10 * (0.4 + u), 126 - 16 * sin(u * PI) * (0.6 + 0.2 * (i % 2)), 1.8, 1 - u)
            }
            return m
        }
    }

    // ---------- 雪天：大雪花慢慢落下 → 小墨丸追著看、落在鼻子上、吹一口氣送走 → 大墨丸伸手接住、湊近看、開心 ----------

    object Snowy : Script {
        override val weather = Mowan.Weather.SNOW
        override val durMs = 8200.0
        override val beats = listOf(
            0.0 to "點擊：天氣資訊亮一下",
            150.0 to "雪開始飄（日曆上的雪花動起來）；一片大雪花慢慢落下",
            800.0 to "小墨丸抬頭、眼睛跟著雪花走",
            2050.0 to "雪花落在鼻子上：縮一下、眨眼",
            2600.0 to "吹一口氣，把雪花往故事卡送",
            3000.0 to "接力：雪花慢慢飄到故事卡（左右晃）",
            3900.0 to "大墨丸：看到雪花飄下來，伸出右手",
            4800.0 to "接住了！",
            5000.0 to "把手湊到臉前、歪頭仔細看",
            5800.0 to "好開心：雙手舉起、小跳一下",
            6700.0 to "手收回來、抬頭看雪",
            7000.0 to "接回日常：抬頭看雪",
        )

        override fun daily(clock: Double, caps: Capabilities) = Poses.snow(clock, caps)
        override fun still(caps: Capabilities) = Poses.snow(2000.0, caps)

        /** 主角雪花在小墨丸座標的位置（落在鼻子上之前） */
        private fun heroSmall(t: Double): Pt {
            val u = Geometry.clamp01((t - 150) / 1900)
            return Pt(80 - 20 * Keyframes.ease(u) + 6 * sin(u * PI * 2), Geometry.lerp(-10.0, 51.0, Keyframes.ease(u)))
        }

        override fun small(t: Double, caps: Capabilities): Pose {
            val s0 = still(caps)
            // 眼睛與頭跟著雪花（從右上慢慢到鼻子前）
            val hero = heroSmall(t)
            val follow = Keyframes.env(t, 800.0, 1200.0, 2000.0, 2200.0)
            val scrunch = Keyframes.env(t, 2050.0, 2120.0, 2350.0, 2550.0)
            val blow = Keyframes.env(t, 2600.0, 2750.0, 2950.0, 3200.0)
            var p = s0.copy(
                headAngle = s0.headAngle + follow * Geometry.lerp(-6.0, 2.0, Geometry.clamp01((hero.y + 10) / 61)) + 4 * scrunch - 3 * blow,
                gaze = Pt(follow * (hero.x - 60) / 14, follow * Geometry.lerp(-2.0, 1.2, Geometry.clamp01((hero.y + 10) / 61)) - 0.8 * blow),
                face = if (scrunch > 0.25) Face.COLD else s0.face,
            )
            p = p.shifted(0.6 * scrunch).jittered(0.35 * scrunch * sin(t / 30))
            return blend(t, 3200.0, 3900.0, p, s0)
        }

        override fun big(t: Double, caps: Capabilities): Pose {
            val clock = t + 1500
            val d = daily(clock, caps)
            if (t < 3800) return d
            val base = Poses.snow(0.0, caps)
            // 右手：往右前方伸出、手心朝上接住 → 湊到臉前 → 舉起歡呼 → 收回
            val dir = Keyframes.track(t, listOf(3900.0 to 10.0, 4500.0 to 80.0, 4800.0 to 85.0, 5300.0 to -20.0, 5700.0 to -25.0, 6000.0 to 160.0, 6400.0 to 165.0, 6900.0 to 10.0))
            val bend = Keyframes.track(t, listOf(3900.0 to 0.0, 4500.0 to -25.0, 4800.0 to -30.0, 5300.0 to -60.0, 5700.0 to -60.0, 6000.0 to 5.0, 6900.0 to 0.0))
            val cheer = Keyframes.env(t, 5800.0, 6050.0, 6300.0, 6650.0)
            val tilt = Keyframes.env(t, 5000.0, 5300.0, 5700.0, 5900.0)
            var p = base.copy(
                armR = arm(base.armR, false, dir, bend),
                armL = Keyframes.mix(base.armL, arm(base.armL, true, 160.0, 5.0), cheer),
                headAngle = Keyframes.track(t, listOf(3800.0 to -4.0, 4300.0 to -2.0, 4800.0 to 4.0, 5300.0 to 8.0, 5800.0 to -6.0, 6600.0 to -4.0)),
                gaze = Pt(Keyframes.track(t, listOf(3800.0 to 0.0, 4400.0 to 1.6, 5300.0 to 1.2, 5800.0 to 0.0)), Keyframes.track(t, listOf(3800.0 to -1.8, 4400.0 to 0.6, 5300.0 to 1.4, 5800.0 to -1.8))),
                face = if (cheer > 0.2) Face.SUNNY else Face.NORMAL,
                flick = if (t in 4800.0..6700.0) Flick.PERKED else Flick.NORMAL,
                effects = Effects(scarfUp = true),
            )
            p = p.copy(headAngle = p.headAngle + 4 * tilt).shifted(hop(t, 5800.0, 6000.0, 6250.0, 2.6))
            val into = blend(t, 3800.0, 4100.0, d, p)
            return blend(t, 6900.0, 8000.0, into, d)
        }

        /** 大墨丸右手手尖（雪花跟著手） */
        fun bigHand(t: Double, caps: Capabilities): Pt = big(t, caps).armR.last()

        override fun motes(t: Double): List<Mote> {
            val m = ArrayList<Mote>()
            if (t in 150.0..2100.0) heroSmall(t).let { m += Mote(Kind.FLAKE, "small", it.x, it.y, 3.2, Keyframes.env(t, 150.0, 400.0, 2000.0, 2100.0)) }
            // 落在鼻子上
            if (t in 2050.0..2750.0) m += Mote(Kind.FLAKE, "small", 60.0, 51.0, 3.2, 1.0)
            // 吹一口氣（小尺寸不畫正式的白氣，用一團淡淡的氣）
            if (t in 2600.0..3200.0) {
                val u = (t - 2600) / 600
                m += Mote(Kind.PUFF, "small", 52 - 16 * u, 56 + 4 * u, 4 + 5 * u, Keyframes.env(t, 2600.0, 2700.0, 2900.0, 3200.0))
            }
            if (t in 2750.0..3050.0) {
                val u = (t - 2750) / 300
                m += Mote(Kind.FLAKE, "small", 60 - 18 * u, 51 + 10 * u, 3.2, 1.0)
            }
            bridge(Kind.FLAKE, t, 3000.0, 4500.0, 4.0, 0.12)?.let { m += it }
            return m
        }
    }
}
