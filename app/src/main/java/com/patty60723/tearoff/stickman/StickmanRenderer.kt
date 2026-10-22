package com.patty60723.tearoff.stickman

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.patty60723.tearoff.weather.Props
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
    private const val SKY = 0xFF7F9DB8.toInt()
    private const val SUN = 0xFFE8A23A.toInt()
    private const val CLOUD = 0xFFD9D4CC.toInt()

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
     * @param props 天氣道具（傘、草帽、雨……），見 [Props]
     */
    fun draw(
        canvas: Canvas, parts: Set<String>, action: Action, phase: Float, width: Float, height: Float,
        props: Set<String> = emptySet(),
    ) {
        val scale = min(width / W, height / H)
        canvas.save()
        canvas.translate((width - W * scale) / 2, (height - H * scale) / 2)
        canvas.scale(scale, scale)
        canvas.translate(0f, -TOP)
        drawUnits(canvas, parts, action, phase, props)
        canvas.restore()
    }

    /** 小工具用：把一個動作切成幾張圖輪播。 */
    fun frames(
        parts: Set<String>, action: Action, count: Int, widthPx: Int, heightPx: Int,
        props: Set<String> = emptySet(),
    ): List<Bitmap> =
        List(count) { i ->
            Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888).also {
                draw(Canvas(it), parts, action, i / count.toFloat(), widthPx.toFloat(), heightPx.toFloat(), props)
            }
        }

    private fun drawUnits(canvas: Canvas, parts: Set<String>, action: Action, phase: Float, props: Set<String>) {
        drawSky(canvas, props, phase)
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
            canvas.drawLine(shoulderX + 2, shoulderY + 2, shoulderX - 9, shoulderY - 1, line)
            fill.color = CLOTH
            canvas.drawCircle(shoulderX - 12, shoulderY - 1, 4.5f, fill)
            line.strokeWidth = 1.5f
            canvas.drawCircle(shoulderX - 12, shoulderY - 1, 4.5f, line)
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

        // 戴草帽時紙帽先收起來
        val headParts = if (Props.STRAW_HAT in props) parts - Parts.PAPER_HAT else parts
        if (Parts.HEAD in parts) drawHead(canvas, headParts, pose.x(Pose.HEAD), pose.y(Pose.HEAD))

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

        drawProps(canvas, props, pose, phase)
    }

    // ---- 天氣 ----

    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** 火柴人後面的天空：太陽、雲、閃電、雨、雪、霧。 */
    private fun drawSky(canvas: Canvas, props: Set<String>, phase: Float) {
        val wave = sin(phase * 2 * PI).toFloat()
        if (Props.SUN in props) {
            fill.color = SUN
            canvas.drawCircle(7f, -1f, 3.5f, fill)
            thin.color = SUN
            thin.strokeWidth = 1.2f
            for (i in 0 until 8) {
                val a = (i / 8f + phase / 8) * 2 * PI
                val c = kotlin.math.cos(a).toFloat()
                val sn = sin(a).toFloat()
                canvas.drawLine(7 + c * 5, -1 + sn * 5, 7 + c * 6.8f, -1 + sn * 6.8f, thin)
            }
        }
        if (Props.CLOUD in props) {
            val cx = 37 + wave * 2
            fill.color = CLOUD
            canvas.drawCircle(cx - 4.5f, 1.5f, 3.5f, fill)
            canvas.drawCircle(cx, -0.5f, 4.5f, fill)
            canvas.drawCircle(cx + 4.5f, 1.5f, 3.5f, fill)
            canvas.drawRect(cx - 4.5f, 1.5f, cx + 4.5f, 5f, fill)
        }
        if (Props.LIGHTNING in props && phase % 0.5f < 0.15f) {
            thin.color = SUN
            thin.strokeWidth = 1.8f
            val bolt = Path().apply { moveTo(38f, 5f); lineTo(35f, 11f); lineTo(39f, 11f); lineTo(35.5f, 18f) }
            canvas.drawPath(bolt, thin)
        }
        if (Props.RAIN in props) {
            thin.color = SKY
            thin.strokeWidth = 1.2f
            for (i in 0 until 8) {
                val x = 3f + i * 6
                val y = ((phase + i * 0.37f) % 1f) * 66 - 6
                canvas.drawLine(x, y, x - 1, y + 3.5f, thin)
            }
        }
        if (Props.SNOW in props) {
            fill.color = SKY
            for (i in 0 until 9) {
                val x = 3f + i * 5.3f + sin((phase + i * 0.2f) * 2 * PI).toFloat()
                val y = ((phase * 0.5f + i * 0.29f) % 1f) * 66 - 6
                canvas.drawCircle(x, y, 0.9f, fill)
            }
        }
        if (Props.MIST in props) {
            thin.color = CLOUD
            thin.strokeWidth = 2f
            for ((i, y) in listOf(40f, 47f, 54f).withIndex()) {
                val dx = wave * (3 + i)
                val mist = Path().apply {
                    moveTo(2 + dx, y)
                    quadTo(14 + dx, y - 2, 24 + dx, y)
                    quadTo(34 + dx, y + 2, 46 + dx, y)
                }
                canvas.drawPath(mist, thin)
            }
        }
    }

    /** 拿在身上的：傘、草帽、汗滴、發抖的線。 */
    private fun drawProps(canvas: Canvas, props: Set<String>, pose: Pose, phase: Float) {
        val hx = pose.x(Pose.HEAD)
        val hy = pose.y(Pose.HEAD)
        if (Props.UMBRELLA in props) {
            val x = pose.x(Pose.HAND_R)
            val y = pose.y(Pose.HAND_R)
            line.strokeWidth = 1.6f
            canvas.drawLine(x, y + 3, x, y - 11, line)
            canvas.drawArc(RectF(x - 2.5f, y + 1.5f, x, y + 4.5f), 0f, 180f, false, line)
            val top = RectF(x - 12, y - 23, x + 12, y + 1)
            fill.color = RED
            canvas.drawArc(top, 180f, 180f, true, fill)
            line.strokeWidth = 1.2f
            canvas.drawArc(top, 180f, 180f, true, line)
            line.strokeWidth = 2.5f
        }
        if (Props.STRAW_HAT in props) {
            fill.color = CLOTH
            canvas.drawArc(RectF(hx - 6, hy - 12, hx + 6, hy), 180f, 180f, true, fill)
            thin.color = CLOTH
            thin.strokeWidth = 2.6f
            canvas.drawLine(hx - 10, hy - 6, hx + 10, hy - 6, thin)
            thin.color = RED
            thin.strokeWidth = 1f
            canvas.drawLine(hx - 5.5f, hy - 7.5f, hx + 5.5f, hy - 7.5f, thin)
        }
        if (Props.SWEAT in props) {
            fill.color = SKY
            canvas.drawCircle(hx + 9, hy - 1 + 4 * phase, 1.3f, fill)
        }
        if (Props.SHIVER in props) {
            thin.color = SKY
            thin.strokeWidth = 1.2f
            val y = pose.y(Pose.NECK) + 6
            for (side in listOf(-1f, 1f)) {
                val x = pose.x(Pose.NECK) + side * 12
                val zig = Path().apply {
                    moveTo(x, y); lineTo(x + side * 2, y + 2); lineTo(x, y + 4); lineTo(x + side * 2, y + 6)
                }
                canvas.drawPath(zig, thin)
            }
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
