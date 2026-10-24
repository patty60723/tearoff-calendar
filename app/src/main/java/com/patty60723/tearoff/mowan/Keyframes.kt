package com.patty60723.tearoff.mowan

/**
 * 關鍵影格的內插，照原型 core.js 的 ease / seg / env / track 移植。
 * 段與段之間用 smoothstep（ease in-out）。
 */
internal object Keyframes {
    fun ease(u: Double): Double {
        val c = Geometry.clamp01(u)
        return c * c * (3 - 2 * c)
    }

    private fun seg(t: Double, a: Double, b: Double) = ease((t - a) / (b - a))

    /** 0 → 1 → 0 的包絡：a 開始、b 到頂、c 開始回、d 回到 0 */
    fun env(t: Double, a: Double, b: Double, c: Double, d: Double): Double = when {
        t < a || t > d -> 0.0
        t < b -> seg(t, a, b)
        t < c -> 1.0
        else -> 1 - seg(t, c, d)
    }

    fun mix(a: List<Pt>, b: List<Pt>, u: Double): List<Pt> =
        a.mapIndexed { i, p -> Pt(Geometry.lerp(p.x, b[i].x, u), Geometry.lerp(p.y, b[i].y, u)) }

    /** 數值的關鍵影格：[(時間, 值), …] */
    fun track(t: Double, keys: List<Pair<Double, Double>>): Double {
        if (t <= keys[0].first) return keys[0].second
        for (i in 1 until keys.size) {
            if (t <= keys[i].first) {
                val (t0, a) = keys[i - 1]
                val (t1, b) = keys[i]
                return Geometry.lerp(a, b, ease((t - t0) / (t1 - t0)))
            }
        }
        return keys.last().second
    }

    /** 點列的關鍵影格：每一格的點數要一樣 */
    fun trackPts(t: Double, keys: List<Pair<Double, List<Pt>>>): List<Pt> {
        if (t <= keys[0].first) return keys[0].second
        for (i in 1 until keys.size) {
            if (t <= keys[i].first) {
                val (t0, a) = keys[i - 1]
                val (t1, b) = keys[i]
                return mix(a, b, ease((t - t0) / (t1 - t0)))
            }
        }
        return keys.last().second
    }
}
