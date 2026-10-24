package com.patty60723.tearoff.mowan

import java.util.concurrent.ConcurrentHashMap

/**
 * 原型用的亂數，照抄成 Kotlin。同一個名字、同一張影格，每次算出來都一樣。
 * JS 的 `Math.imul`、`>>> 0` 對應到 Kotlin 的 Int 乘法（會溢位）和 `ushr`。
 */
internal class Mulberry32(seed: Int) {
    private var a = seed

    fun next(): Double {
        a += 0x6D2B79F5
        var t = (a xor (a ushr 15)) * (1 or a)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
}

internal object Rng {
    /** FNV-1a，回傳 0 到 2³²−1 */
    fun hash(s: String): Long {
        var h = 2166136261L.toInt()
        for (c in s) {
            h = h xor c.code
            h *= 16777619
        }
        return h.toLong() and 0xFFFFFFFFL
    }

    private val cache = ConcurrentHashMap<String, DoubleArray>()

    /** 每個部位、每張筆觸影格固定的 6 個相位 */
    fun phases(part: String, variant: Int): DoubleArray = cache.getOrPut("$part#$variant") {
        val r = Mulberry32((hash(part) + variant * 7919L).toInt())
        DoubleArray(6) { r.next() * Math.PI * 2 }
    }
}
