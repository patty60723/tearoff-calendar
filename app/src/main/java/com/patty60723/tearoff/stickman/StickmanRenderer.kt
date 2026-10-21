package com.patty60723.tearoff.stickman

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** 依解鎖到哪裡，把火柴人畫出來。沒長出來的部位就不畫。 */
object StickmanRenderer {
    private const val W = 48f
    /** 骨架的格子是 48×64，上面多留 8 給紙帽和跳起來的時候 */
    private const val TOP = -8f
    private const val H = 64f - TOP

    private const val INK = 0xFF2B2724.toInt()
    private const val RED = 0xFFC62F2F.toInt()
    private const val PAPER = 0xFFFFFDF7.toInt()
    private const val CLOTH = 0xFFE2B866.toInt()

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = INK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * @param phase 0 到 1，動作播到哪裡；圍巾尾巴之類的小擺動也跟著它。
     */
    fun draw(canvas: Canvas, parts: Set<String>, action: Action, phase: Float, width: Float, height: Float) {
        val scale = min(width / W, height / H)
        canvas.save()
        canvas.translate((width - W * scale) / 2, (height - H * scale) / 2)
        canvas.scale(scale, scale)
        canvas.translate(0f, -TOP)
        drawUnits(canvas, parts, action, phase)
        canvas.restore()
    }

    /** 小工具用：把一個動作切成幾張圖輪播。 */
    fun frames(parts: Set<String>, action: Action, count: Int, widthPx: Int, heightPx: Int): List<Bitmap> =
        List(count) { i ->
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                draw(Canvas(it), parts, action, i / count.toFloat(), widthPx.toFloat(), heightPx.toFloat())
            }
        }

    private fun drawUnits(canvas: Canvas, parts: Set<String>, action: Action, phase: Float) {
        if (parts.isEmpty()) {
            // 還沒撕第一頁：只是一個墨點，輕輕跳
            fill.color = INK
            canvas.drawCircle(24f, Pose.GROUND - 2 - 3 * bounce(phase), 2.5f, fill)
            return
        }

        var pose = action.at(phase)
        if (Parts.LEG_L !in parts && Parts.LEG_R !in parts) {
            // 還沒有腳：身體直接站在地上
            pose = pose.moved(dy = Pose.GROUND - pose.y(Pose.HIP))
        }

        val neckX = pose.x(Pose.NECK)
        val neckY = pose.y(Pose.NECK)
        val hipX = pose.x(Pose.HIP)
        val hipY = pose.y(Pose.HIP)
        val shoulderX = neckX + (hipX - neckX) * 0.2f
        val shoulderY = neckY + (hipY - neckY) * 0.2f

        line.color = INK
        line.strokeWidth = 2.5f

        if (Parts.BUNDLE in parts) {
            // 包袱：扛在背後的小棍子
            canvas.drawLine(shoulderX + 2, shoulderY + 2, shoulderX - 11, shoulderY - 5, line)
            fill.color = CLOTH
            canvas.drawCircle(shoulderX - 13, shoulderY - 7, 4.5f, fill)
            line.strokeWidth = 1.5f
            canvas.drawCircle(shoulderX - 13, shoulderY - 7, 4.5f, line)
            line.strokeWidth = 2.5f
        }

        val torso = Path().apply {
            moveTo(neckX, neckY)
            quadTo((neckX + hipX) / 2 + pose.bend, (neckY + hipY) / 2, hipX, hipY)
        }
        canvas.drawPath(torso, line)

        if (Parts.ARM_L in parts) limb(canvas, shoulderX, shoulderY, pose, Pose.ELBOW_L, Pose.HAND_L)
        if (Parts.ARM_R in parts) limb(canvas, shoulderX, shoulderY, pose, Pose.ELBOW_R, Pose.HAND_R)
        if (Parts.LEG_L in parts) limb(canvas, hipX, hipY, pose, Pose.KNEE_L, Pose.FOOT_L)
        if (Parts.LEG_R in parts) limb(canvas, hipX, hipY, pose, Pose.KNEE_R, Pose.FOOT_R)

        if (Parts.RED_TOES in parts) {
            fill.color = RED
            if (Parts.LEG_L in parts) canvas.drawCircle(pose.x(Pose.FOOT_L), pose.y(Pose.FOOT_L), 1.8f, fill)
            if (Parts.LEG_R in parts) canvas.drawCircle(pose.x(Pose.FOOT_R), pose.y(Pose.FOOT_R), 1.8f, fill)
        }

        if (Parts.HEAD in parts) drawHead(canvas, parts, pose.x(Pose.HEAD), pose.y(Pose.HEAD))

        if (Parts.SCARF in parts) {
            line.color = RED
            line.strokeWidth = 2.8f
            canvas.drawLine(neckX - 3.5f, neckY + 2.5f, neckX + 3.5f, neckY + 2.5f, line)
            val sway = sin(phase * 2 * PI).toFloat() * 2
            val tail = Path().apply {
                moveTo(neckX - 3, neckY + 2.5f)
                quadTo(neckX - 7, neckY + 2.5f + sway, neckX - 10, neckY + 5.5f - sway)
            }
            canvas.drawPath(tail, line)
            line.color = INK
            line.strokeWidth = 2.5f
        }
    }

    private fun limb(canvas: Canvas, fromX: Float, fromY: Float, pose: Pose, mid: Int, end: Int) {
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(pose.x(mid), pose.y(mid))
            lineTo(pose.x(end), pose.y(end))
        }
        canvas.drawPath(path, line)
    }

    private fun drawHead(canvas: Canvas, parts: Set<String>, x: Float, y: Float) {
        val r = Pose.HEAD_RADIUS
        fill.color = PAPER
        canvas.drawCircle(x, y, r, fill)
        canvas.drawCircle(x, y, r, line)

        if (Parts.EYES in parts) {
            fill.color = INK
            canvas.drawCircle(x - 2.5f, y - 0.8f, 1.1f, fill)
            canvas.drawCircle(x + 2.5f, y - 0.8f, 1.1f, fill)
        }
        if (Parts.VOICE in parts) {
            line.strokeWidth = 1.2f
            canvas.drawArc(RectF(x - 2.2f, y + 0.6f, x + 2.2f, y + 3.6f), 0f, 180f, false, line)
            line.strokeWidth = 2.5f
        }
        if (Parts.PAPER_HAT in parts) {
            val hat = Path().apply {
                moveTo(x - 6.5f, y - 4.5f)
                lineTo(x + 6.5f, y - 4.5f)
                lineTo(x + 1f, y - 15f)
                close()
            }
            fill.color = PAPER
            canvas.drawPath(hat, fill)
            line.strokeWidth = 1.5f
            canvas.drawPath(hat, line)
            line.strokeWidth = 2.5f
        }
    }

    private fun bounce(phase: Float) = sin(phase * PI).toFloat()
}
