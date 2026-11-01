package com.patty60723.tearoff

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.hypot
import kotlin.math.min

/**
 * 可以用手指撕的那一頁：從左下角或右下角往上拖，紙會跟著手指捲起來，
 * 露出底下的下一頁；拖超過一半放手就撕掉，不到一半就彈回去。
 *
 * 做法：手指和角落連線的中垂線就是摺線。角落那一側的紙翻到另一面（對摺線鏡射），
 * 畫成紙的背面；原本那塊地方露出下一頁。
 */
class TearPageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    /** 這一頁現在能不能撕（今天撕過了、或在翻看別天時不能撕，但還是可以掀起來偷看） */
    var tearable = false
        set(value) {
            field = value
            invalidate()
        }

    /** 底下那一頁畫成圖，大小跟這一頁一樣。 */
    var pageBelow: (width: Int, height: Int) -> Bitmap? = { _, _ -> null }
    var onTorn: () -> Unit = {}
    /** 拖得夠遠但這頁不能撕 */
    var onBlocked: () -> Unit = {}

    /** 高 / 寬 */
    var aspect = 1.45f

    private val density = resources.displayMetrics.density
    private var front: Bitmap? = null
    private var below: Bitmap? = null
    private var corner: PointF? = null
    private val touch = PointF()
    private var animator: ValueAnimator? = null

    private val frontPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val belowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palettes.PAPER_BACK }
    private val printPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 26 }
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26000000 }
    private val deskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setWillNotDraw(false)
        deskPaint.color = context.getColor(R.color.desk)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * aspect).toInt()
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
    }

    // ---- 手勢 ----

    private fun cornerAt(x: Float, y: Float): PointF? {
        val zone = width * 0.35f
        if (y < height - zone) return null
        return when {
            x < zone -> PointF(0f, height.toFloat())
            x > width - zone -> PointF(width.toFloat(), height.toFloat())
            else -> null
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        ev.actionMasked == MotionEvent.ACTION_DOWN && animator == null && cornerAt(ev.x, ev.y) != null

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (animator != null) return false
                val c = cornerAt(event.x, event.y) ?: return false
                begin(c)
                touch.set(event.x, event.y)
                // 不要讓外面的捲動把手勢搶走
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (corner == null) return false
                touch.set(event.x, event.y)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    /** 正在撕（手指拉著或動畫中）：這時不能換掉底下的頁面 */
    internal val busy: Boolean get() = corner != null || animator != null

    /** 不用手指，直接把這一頁撕掉（按鈕用）。 */
    fun peelAway() {
        if (animator != null || width == 0) return
        val c = PointF(width.toFloat(), height.toFloat())
        begin(c)
        touch.set(c.x - 1, c.y - 1)
        animateTo(awayPoint(c), 520) { finish(torn = true) }
    }

    private fun begin(c: PointF) {
        val child = getChildAt(0) ?: return
        front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { child.draw(Canvas(it)) }
        below = pageBelow(width, height)
        frontPaint.shader = BitmapShader(front!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        belowPaint.shader = below?.let { BitmapShader(it, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        // 紙背透出的字要另一個 shader，因為它會被鏡射
        printPaint.shader = BitmapShader(front!!, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        corner = c
        child.visibility = View.INVISIBLE
    }

    private fun release() {
        val c = corner ?: return
        val far = hypot(touch.x - c.x, touch.y - c.y) > width * 0.5f
        if (far && tearable) {
            animateTo(awayPoint(c), 320) { finish(torn = true) }
        } else {
            animateTo(PointF(c.x - (if (c.x > 0) 1 else -1), c.y - 1), 240) {
                finish(torn = false)
                if (far) onBlocked()
            }
        }
    }

    /** 撕掉時手指一路拉到對角外面，整張紙翻走。 */
    private fun awayPoint(c: PointF) =
        PointF(if (c.x > 0) -width * 0.7f else width * 1.7f, -height * 0.5f)

    private fun animateTo(target: PointF, duration: Long, end: () -> Unit) {
        val from = PointF(touch.x, touch.y)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                touch.set(from.x + (target.x - from.x) * t, from.y + (target.y - from.y) * t)
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animator = null
                    end()
                }
            })
            start()
        }
    }

    private fun finish(torn: Boolean) {
        corner = null
        frontPaint.shader = null
        belowPaint.shader = null
        printPaint.shader = null
        front = null
        below = null
        if (torn) onTorn()
        getChildAt(0)?.visibility = View.VISIBLE
        invalidate()
    }

    /** 測試截圖用：停在掀到一半的樣子。 */
    internal fun holdPeel(rightCorner: Boolean, x: Float, y: Float) {
        begin(if (rightCorner) PointF(width.toFloat(), height.toFloat()) else PointF(0f, height.toFloat()))
        touch.set(x, y)
        invalidate()
    }

    // ---- 畫 ----

    override fun dispatchDraw(canvas: Canvas) {
        val c = corner
        if (c == null) {
            super.dispatchDraw(canvas)
            if (tearable) drawDogEar(canvas)
        } else {
            drawPeel(canvas, c)
        }
    }

    /** 平常右下角折起一小角，提示可以撕。 */
    private fun drawDogEar(canvas: Canvas) {
        val e = 22 * density
        val w = width.toFloat() - 3 * density  // 對齊最上面那頁紙的邊
        val h = height.toFloat() - 5 * density
        val gone = Path().apply { moveTo(w - e, h); lineTo(w, h - e); lineTo(w, h); close() }
        canvas.drawPath(gone, deskPaint)
        val flap = Path().apply { moveTo(w - e, h); lineTo(w, h - e); lineTo(w - e, h - e); close() }
        canvas.save()
        canvas.translate(1.5f * density, 2f * density)
        canvas.drawPath(flap, dropPaint)
        canvas.restore()
        canvas.drawPath(flap, backPaint)
    }

    private fun drawPeel(canvas: Canvas, c: PointF) {
        val w = width.toFloat()
        val h = height.toFloat()
        var nx = touch.x - c.x
        var ny = touch.y - c.y
        val len = hypot(nx, ny)
        if (len < 1f) {
            front?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            return
        }
        nx /= len
        ny /= len
        val mx = (c.x + touch.x) / 2
        val my = (c.y + touch.y) / 2
        val page = listOf(PointF(0f, 0f), PointF(w, 0f), PointF(w, h), PointF(0f, h))
        // side < 0 是角落那一側（要翻過去的那塊）
        val side = { p: PointF -> (p.x - mx) * nx + (p.y - my) * ny }
        val flap = cut(page, side, keepNegative = true)
        val rest = cut(page, side, keepNegative = false)
        if (flap.size < 3) {
            front?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            return
        }

        // 1. 翻走的地方露出下一頁，靠摺線那邊有一點陰影
        val flapPath = path(flap)
        if (below != null) canvas.drawPath(flapPath, belowPaint) else canvas.drawPath(flapPath, deskPaint)
        val shadow = min(len * 0.5f, 28 * density)
        shadePaint.shader = LinearGradient(mx, my, mx - nx * shadow, my - ny * shadow, 0x38000000, 0, Shader.TileMode.CLAMP)
        canvas.drawPath(flapPath, shadePaint)

        // 2. 還沒翻的這一頁
        if (rest.size >= 3) canvas.drawPath(path(rest), frontPaint)

        // 3. 翻過來的紙背：對摺線鏡射，淡淡透出正面的字
        val mirror = reflection(nx, ny, mx, my)
        val back = path(flap).apply { transform(mirror) }
        canvas.save()
        canvas.translate(2 * density, 3 * density)
        canvas.drawPath(back, dropPaint)
        canvas.restore()
        canvas.drawPath(back, backPaint)
        printPaint.shader?.setLocalMatrix(mirror)
        canvas.drawPath(back, printPaint)
        shadePaint.shader = LinearGradient(mx, my, mx + nx * shadow * 1.6f, my + ny * shadow * 1.6f, 0x22000000, 0, Shader.TileMode.CLAMP)
        canvas.drawPath(back, shadePaint)
    }

    private fun path(points: List<PointF>) = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (p in points.drop(1)) lineTo(p.x, p.y)
        close()
    }

    companion object {
        /** 把多邊形用一條直線切開，留下其中一邊。 */
        internal fun cut(polygon: List<PointF>, side: (PointF) -> Float, keepNegative: Boolean): List<PointF> {
            val keep = { p: PointF -> if (keepNegative) side(p) < 0 else side(p) >= 0 }
            val out = mutableListOf<PointF>()
            for (i in polygon.indices) {
                val a = polygon[i]
                val b = polygon[(i + 1) % polygon.size]
                if (keep(a)) out += a
                if (keep(a) != keep(b)) {
                    val sa = side(a)
                    val t = sa / (sa - side(b))
                    out += PointF(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
            }
            return out
        }

        /** 對「通過 (mx, my)、法向量 (nx, ny)」那條線鏡射。 */
        internal fun reflection(nx: Float, ny: Float, mx: Float, my: Float) = Matrix().apply {
            val d = 2 * (mx * nx + my * ny)
            setValues(
                floatArrayOf(
                    1 - 2 * nx * nx, -2 * nx * ny, d * nx,
                    -2 * nx * ny, 1 - 2 * ny * ny, d * ny,
                    0f, 0f, 1f,
                )
            )
        }
    }
}
