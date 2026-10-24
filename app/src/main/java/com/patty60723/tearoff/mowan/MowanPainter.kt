package com.patty60723.tearoff.mowan

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * 把 [Mowan.Frame] 畫到 Canvas。Path 和 Paint 在建構時就準備好，draw 不再配置物件。
 * 座標系是原型的 viewBox 0 −20 120 160，畫的時候等比例縮放、置中。
 */
class MowanPainter(frame: Mowan.Frame) {
    private class Item(val layer: Mowan.Layer, val path: Path, val fill: Paint?, val stroke: Paint?)

    private val head = frame.head
    private val canopy = frame.canopy
    private val items = frame.ops.map { op ->
        val path = toPath(op.path).apply { fillType = if (op.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING }
        val alpha = (op.alpha * 255).toInt().coerceIn(0, 255)
        val fill = if (op.fill == null && op.gradient == null) null else Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            op.fill?.let { color = it }
            this.alpha = alpha
            op.gradient?.let { g ->
                shader = LinearGradient(
                    g.x1.toFloat(), g.y1.toFloat(), g.x2.toFloat(), g.y2.toFloat(),
                    g.stops.map { (_, c, a) -> (c and 0xFFFFFF) or ((a * 255).toInt() shl 24) }.toIntArray(),
                    g.stops.map { it.first }.toFloatArray(),
                    Shader.TileMode.CLAMP,
                )
            }
        }
        val stroke = op.stroke?.let { c ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = c
                this.alpha = alpha
                strokeWidth = op.strokeWidth.toFloat()
                strokeCap = if (op.roundCap) Paint.Cap.ROUND else Paint.Cap.BUTT
            }
        }
        Item(op.layer, path, fill, stroke)
    }

    /** 畫在 (left, top, width, height) 這一格裡 */
    fun draw(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val s = minOf(width / VIEW_W, height / VIEW_H)
        canvas.save()
        canvas.translate(left + (width - VIEW_W * s) / 2, top + (height - VIEW_H * s) / 2)
        canvas.scale(s, s)
        canvas.translate(0f, -VIEW_TOP)
        var current: Mowan.Layer? = null
        for (item in items) {
            if (item.layer != current) {
                if (transformOf(current) != null) canvas.restore()
                transformOf(item.layer)?.let { t ->
                    canvas.save()
                    canvas.translate(t.dx.toFloat(), t.dy.toFloat())
                    canvas.rotate(t.rotDeg.toFloat(), t.pivotX.toFloat(), t.pivotY.toFloat())
                    canvas.scale(t.scale.toFloat(), t.scale.toFloat())
                }
                current = item.layer
            }
            item.fill?.let { canvas.drawPath(item.path, it) }
            item.stroke?.let { canvas.drawPath(item.path, it) }
        }
        if (transformOf(current) != null) canvas.restore()
        canvas.restore()
    }

    private fun transformOf(layer: Mowan.Layer?) = when (layer) {
        Mowan.Layer.HEAD -> head
        Mowan.Layer.CANOPY -> canopy
        else -> null
    }

    companion object {
        const val VIEW_W = 120f
        const val VIEW_H = 160f
        const val VIEW_TOP = -20f

        fun toPath(data: PathData): Path {
            val path = Path()
            var x = 0.0
            var y = 0.0
            var startX = 0.0
            var startY = 0.0
            val oval = RectF()
            for (c in data.commands) when (c) {
                is PathData.Cmd.MoveTo -> { path.moveTo(c.x.toFloat(), c.y.toFloat()); x = c.x; y = c.y; startX = x; startY = y }
                is PathData.Cmd.LineTo -> { path.lineTo(c.x.toFloat(), c.y.toFloat()); x = c.x; y = c.y }
                is PathData.Cmd.QuadTo -> { path.quadTo(c.x1.toFloat(), c.y1.toFloat(), c.x.toFloat(), c.y.toFloat()); x = c.x; y = c.y }
                is PathData.Cmd.ArcTo -> {
                    val a = ArcCenter.of(x, y, c)
                    if (a == null) {
                        path.lineTo(c.x.toFloat(), c.y.toFloat())
                    } else {
                        oval.set((a.cx - a.rx).toFloat(), (a.cy - a.ry).toFloat(), (a.cx + a.rx).toFloat(), (a.cy + a.ry).toFloat())
                        path.arcTo(oval, a.startDeg.toFloat(), a.sweepDeg.toFloat(), false)
                    }
                    x = c.x; y = c.y
                }
                PathData.Cmd.Close -> { path.close(); x = startX; y = startY }
            }
            return path
        }
    }
}
