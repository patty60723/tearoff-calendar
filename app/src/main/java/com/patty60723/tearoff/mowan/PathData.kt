package com.patty60723.tearoff.mowan

/** 不依賴 Android 的路徑：只有原型用到的四種指令，座標都是絕對座標。 */
class PathData(val commands: List<Cmd>) {
    sealed interface Cmd {
        data class MoveTo(val x: Double, val y: Double) : Cmd
        data class LineTo(val x: Double, val y: Double) : Cmd
        /** SVG 的橢圓弧（不旋轉），終點 (x, y) */
        data class ArcTo(val rx: Double, val ry: Double, val largeArc: Boolean, val sweep: Boolean, val x: Double, val y: Double) : Cmd
        data object Close : Cmd
    }

    class Builder {
        private val cmds = ArrayList<Cmd>()
        fun move(p: Pt) = apply { cmds += Cmd.MoveTo(p.x, p.y) }
        fun line(p: Pt) = apply { cmds += Cmd.LineTo(p.x, p.y) }
        fun arc(rx: Double, ry: Double, largeArc: Boolean, sweep: Boolean, p: Pt) = apply { cmds += Cmd.ArcTo(rx, ry, largeArc, sweep, p.x, p.y) }
        fun close() = apply { cmds += Cmd.Close }
        fun add(other: PathData) = apply { cmds += other.commands }
        fun build() = PathData(cmds)
    }

    companion object {
        /** 原型的 circleD：兩個半圓弧接成橢圓 */
        fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double): PathData = Builder()
            .move(Pt(cx - rx, cy))
            .arc(rx, ry, largeArc = true, sweep = false, Pt(cx + rx, cy))
            .arc(rx, ry, largeArc = true, sweep = false, Pt(cx - rx, cy))
            .close().build()
    }
}

/**
 * SVG 弧換成圓心表示法（SVG 規格 F.6.5，不旋轉）。角度單位是度，跟 Android `Path.arcTo` 一樣：
 * y 軸朝下，正的角度是順時針。
 */
data class ArcCenter(val cx: Double, val cy: Double, val rx: Double, val ry: Double, val startDeg: Double, val sweepDeg: Double) {
    companion object {
        fun of(x0: Double, y0: Double, arc: PathData.Cmd.ArcTo): ArcCenter? {
            if (x0 == arc.x && y0 == arc.y) return null
            var rx = Math.abs(arc.rx)
            var ry = Math.abs(arc.ry)
            if (rx == 0.0 || ry == 0.0) return null
            val x1 = (x0 - arc.x) / 2
            val y1 = (y0 - arc.y) / 2
            val lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
            if (lambda > 1) {
                rx *= Math.sqrt(lambda)
                ry *= Math.sqrt(lambda)
            }
            val num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val den = rx * rx * y1 * y1 + ry * ry * x1 * x1
            val coef = (if (arc.largeArc == arc.sweep) -1 else 1) * Math.sqrt(Math.max(0.0, num / den))
            val cxp = coef * rx * y1 / ry
            val cyp = -coef * ry * x1 / rx
            val start = Math.atan2((y1 - cyp) / ry, (x1 - cxp) / rx)
            val end = Math.atan2((-y1 - cyp) / ry, (-x1 - cxp) / rx)
            var sweep = end - start
            if (!arc.sweep && sweep > 0) sweep -= 2 * Math.PI
            if (arc.sweep && sweep < 0) sweep += 2 * Math.PI
            return ArcCenter(
                cxp + (x0 + arc.x) / 2, cyp + (y0 + arc.y) / 2, rx, ry,
                Math.toDegrees(start), Math.toDegrees(sweep),
            )
        }
    }
}
