package com.patty60723.tearoff.mowan

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs

/**
 * 天氣彩蛋的素材（光點、雨滴、水花、雪花、氣、水圈）怎麼畫（P7-9a 預覽與 P7-9b 的 App 疊層共用，畫法沒有改）。
 * 每個素材只是一兩個基本圖形，沒有粒子系統；一格最多十來個。
 */
internal class WeatherEggPaint {
    /** 一個墨丸畫在哪裡：畫布上的範圍；跟 [MowanPainter] 一樣置中、等比例，原型座標 (0, −20)～(120, 140) */
    class Box(val rect: RectF) {
        val s = minOf(rect.width() / 120f, rect.height() / 160f)
        private val left = rect.left + (rect.width() - 120 * s) / 2
        private val top = rect.top + (rect.height() - 160 * s) / 2
        fun x(x: Double) = (left + x * s).toFloat()
        fun y(y: Double) = (top + (y + 20) * s).toFloat()
    }

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    /**
     * @param small、big 兩隻墨丸在畫布上的位置
     * @param stageWidth 接力路徑左右飄的基準寬度（畫面寬）
     */
    fun draw(canvas: Canvas, m: WeatherEgg.Mote, small: Box, big: Box, stageWidth: Float) {
        // 位置：小墨丸、大墨丸各自的座標；接力是兩者之間的曲線（從小墨丸左下出發，落到大墨丸頭頂）
        val x: Float
        val y: Float
        val s: Float
        when (m.space) {
            "small" -> { x = small.x(m.x); y = small.y(m.y); s = small.s }
            "big" -> { x = big.x(m.x); y = big.y(m.y); s = big.s }
            else -> {
                val ax = small.x(44.0); val ay = small.y(72.0)
                val bx = big.x(61.0); val by = big.y(8.0)
                val cx = ax + (bx - ax) * 0.5f + (bx - ax) * 0.35f + (m.x * stageWidth).toFloat()
                val cy = minOf(ay, by) + (by - ay) * 0.15f
                val u = m.u.toFloat()
                x = (1 - u) * (1 - u) * ax + 2 * (1 - u) * u * cx + u * u * bx
                y = (1 - u) * (1 - u) * ay + 2 * (1 - u) * u * cy + u * u * by
                s = small.s + (big.s - small.s) * u
            }
        }
        val a = (255 * m.alpha.coerceIn(0.0, 1.0)).toInt()
        val r = (m.size * s).toFloat()
        ink.shader = null
        when (m.kind) {
            WeatherEgg.Kind.LIGHT -> {
                ink.style = Paint.Style.FILL
                ink.color = Mowan.PINK; ink.alpha = a / 4; canvas.drawCircle(x, y, r * 2.2f, ink)
                ink.alpha = a; canvas.drawCircle(x, y, r, ink)
            }
            WeatherEgg.Kind.DROP -> {
                ink.style = Paint.Style.STROKE; ink.strokeCap = Paint.Cap.ROUND; ink.strokeWidth = 1.5f * s
                ink.color = Mowan.SAGE; ink.alpha = a
                canvas.drawLine(x, y - r, x - 0.5f * r, y + r, ink)
            }
            WeatherEgg.Kind.SPLASH -> {
                ink.style = Paint.Style.STROKE; ink.strokeCap = Paint.Cap.ROUND; ink.strokeWidth = 1.2f * s
                ink.color = Mowan.SAGE; ink.alpha = a
                val d = r * (0.6f + 1.4f * m.u.toFloat())
                for (k in -1..1) canvas.drawLine(x + k * d * 0.6f, y - d * 0.2f, x + k * d * 1.1f, y - d * (0.7f - 0.2f * abs(k)), ink)
            }
            WeatherEgg.Kind.FLAKE -> {
                ink.style = Paint.Style.FILL; ink.color = Mowan.SNOW; ink.alpha = a; canvas.drawCircle(x, y, r * 0.7f, ink)
                ink.style = Paint.Style.STROKE; ink.strokeWidth = 0.6f * s; ink.color = Mowan.INK; ink.alpha = a; canvas.drawCircle(x, y, r * 0.7f, ink)
            }
            WeatherEgg.Kind.PUFF -> {
                ink.style = Paint.Style.FILL; ink.color = Mowan.CLOUD; ink.alpha = (a * 0.85).toInt()
                oval.set(x - r, y - r * 0.6f, x + r, y + r * 0.6f); canvas.drawOval(oval, ink)
            }
            WeatherEgg.Kind.RING -> {
                ink.style = Paint.Style.STROKE; ink.strokeWidth = 1.0f * s; ink.color = Mowan.SAGE; ink.alpha = a
                oval.set(x - r, y - r * 0.16f, x + r, y + r * 0.16f); canvas.drawOval(oval, ink)
            }
        }
    }
}
