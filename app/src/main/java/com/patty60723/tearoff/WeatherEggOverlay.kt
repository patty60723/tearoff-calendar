package com.patty60723.tearoff

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.ImageView
import com.patty60723.tearoff.mowan.Capabilities
import com.patty60723.tearoff.mowan.Detail
import com.patty60723.tearoff.mowan.Mowan
import com.patty60723.tearoff.mowan.MowanPainter
import com.patty60723.tearoff.mowan.MowanTimeline
import com.patty60723.tearoff.mowan.WeatherEgg
import com.patty60723.tearoff.mowan.WeatherEggPaint

/**
 * 天氣接力彩蛋的疊層（P7-9b）：蓋在整個畫面上、不吃觸控、TalkBack 看不到。平常什麼都不畫。
 * 播放時畫三樣東西，位置每一格都從實際的元件算（跟著捲動走），不改任何版面：
 * 1. 天氣資訊後面淡淡亮一下（點擊回饋）。
 * 2. 日曆小墨丸的動畫：畫在日曆頁那張靜態圖（`mowan_still`）的同一個位置、同樣大小、同樣的簡化畫法。
 *    這段時間靜態圖由 [WeatherEggController] 藏起來，所以不會有兩隻。
 * 3. 天氣素材（光點、雨滴、雪花），包含從日曆飄到故事卡的那一段。
 *
 * 小墨丸怎麼跟靜態圖接上：開始與結束各 [FADE_MS] 毫秒，角色就是靜態圖那一格（同一張筆觸、同一個姿勢），
 * 只有雨絲、雪花那一層從靜態的位置淡到會動的位置（結束時反過來）。所以第 0 格跟靜態圖一模一樣，換回靜態圖時也不會跳。
 */
internal class WeatherEggOverlay(context: Context) : View(context) {
    /** 墨丸的外觀（跟日曆頁靜態圖用的一樣） */
    class Look(val parts: Set<String>, val hatOn: Boolean, val bundleOn: Boolean)

    /** 一次播放要用到的東西 */
    /** @param viewport 捲動區域：疊層只畫在它裡面（不畫到狀態列、導覽列底下） */
    class Playback(val script: WeatherEgg.Script, val still: ImageView, val sun: View, val big: View, val look: Look, val viewport: View)

    private var playback: Playback? = null
    private var t = 0.0
    private var smallOn = false
    private var buf: Bitmap? = null
    private var bufCanvas: Canvas? = null
    private var stillPainter: MowanPainter? = null
    private var detail: Detail = Detail.FULL
    private val motes = WeatherEggPaint()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = TAP_COLOR }
    private val rect = RectF()
    private val own = IntArray(2)
    private val loc = IntArray(2)

    /** 正在播（有東西要畫） */
    val active: Boolean get() = playback != null

    /** 測試用：這一格小墨丸有沒有畫、畫了幾個素材 */
    internal var lastSmallDrawn = false
        private set
    internal var lastMotes = 0
        private set

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** 靜態圖是不是一張可以照著畫的圖（不是的話不能播） */
    fun canPlay(still: ImageView): Boolean = (still.drawable as? BitmapDrawable)?.bitmap?.let { it.width > 0 && it.height > 0 } == true

    fun begin(p: Playback) {
        val src = (p.still.drawable as BitmapDrawable).bitmap
        buf = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also { bufCanvas = Canvas(it) }
        // 跟 CalendarWidget 畫靜態圖時一樣的簡化判斷（密度上限 3）
        val density = resources.displayMetrics.density.coerceAtMost(3f)
        detail = Detail.of(minOf(src.width / 120.0, src.height / 160.0) * 160.0, density.toDouble())
        val weather = p.script.weather
        stillPainter = MowanPainter(
            Mowan.frame(p.look.parts, 0, p.look.hatOn, weather, MowanTimeline.stillClock(weather), still = true, bundleOn = p.look.bundleOn, detail = detail),
        )
        playback = p
        t = 0.0
        smallOn = true
        invalidate()
    }

    /** 這一格的時間；[smallOn] 是小墨丸還要不要畫（由控制器決定，跟靜態圖的顯示同一格切換） */
    fun frame(t: Double, smallOn: Boolean) {
        this.t = t
        this.smallOn = smallOn
        invalidate()
    }

    fun end() {
        if (playback == null) return
        playback = null
        stillPainter = null
        bufCanvas = null
        buf?.recycle()
        buf = null
        lastSmallDrawn = false
        lastMotes = 0
        invalidate()
    }

    /** 小墨丸這一格畫到 [buf]（跟靜態圖同樣大小） */
    private fun renderSmall(p: Playback) {
        val b = buf ?: return
        val c = bufCanvas ?: return
        val s = p.script
        val w = b.width.toFloat()
        val h = b.height.toFloat()
        b.eraseColor(0)
        val caps = Capabilities.of(p.look.parts)
        val env = MowanTimeline.stillClock(s.weather) + t
        // 開始、結束的 FADE_MS：角色用靜態那一格，雨雪在兩組位置之間淡入淡出
        val u = when {
            t < FADE_MS -> t / FADE_MS
            t > s.smallEndMs -> 1 - (t - s.smallEndMs) / FADE_MS
            else -> 1.0
        }.coerceIn(0.0, 1.0).toFloat()
        if (u < 1f) {
            if (u > 0f) {
                val moving = MowanPainter(Mowan.render(p.look.parts, 0, p.look.hatOn, s.still(caps), s.weather, Mowan.rainStepAt(t.toLong()), clockMs = env, bundleOn = p.look.bundleOn, detail = detail))
                moving.drawRain(c, 0f, 0f, w, h, u)
            }
            stillPainter?.draw(c, 0f, 0f, w, h, rainAlpha = 1 - u)
        } else {
            val frame = Mowan.render(
                p.look.parts, Mowan.variantAt(t.toLong()), p.look.hatOn, s.small(t, caps), s.weather,
                Mowan.rainStepAt(t.toLong()), clockMs = env, bundleOn = p.look.bundleOn, detail = detail,
            )
            MowanPainter(frame).draw(c, 0f, 0f, w, h)
        }
    }

    /** 測試用：小墨丸這一格的圖（跟靜態圖同樣大小）；沒有在播是 null */
    internal fun smallFrame(): Bitmap? {
        val p = playback ?: return null
        renderSmall(p)
        return buf?.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun place(v: View): Boolean {
        if (!v.isAttachedToWindow) return false
        v.getLocationInWindow(loc)
        loc[0] -= own[0]
        loc[1] -= own[1]
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val p = playback ?: return
        val b = buf ?: return
        getLocationInWindow(own)
        val density = resources.displayMetrics.density
        if (!place(p.viewport)) return
        canvas.save()
        canvas.clipRect(
            (loc[0] + p.viewport.paddingLeft).toFloat(), (loc[1] + p.viewport.paddingTop).toFloat(),
            (loc[0] + p.viewport.width - p.viewport.paddingRight).toFloat(), (loc[1] + p.viewport.height - p.viewport.paddingBottom).toFloat(),
        )
        drawContent(canvas, p, b, density)
        canvas.restore()
    }

    private fun drawContent(canvas: Canvas, p: Playback, b: Bitmap, density: Float) {

        // 1. 點擊回饋
        val tap = p.script.tap(t)
        if (tap > 0 && place(p.sun)) {
            tapPaint.alpha = (40 * tap).toInt()
            rect.set(loc[0] - 4 * density, loc[1] - 2 * density, loc[0] + p.sun.width + 4 * density, loc[1] + p.sun.height + 2 * density)
            canvas.drawRoundRect(rect, 6 * density, 6 * density, tapPaint)
        }

        // 小墨丸在畫面上的位置（靜態圖在 ImageView 裡怎麼擺，就怎麼擺）
        if (!place(p.still)) return
        val sx = (loc[0] + p.still.paddingLeft).toFloat()
        val sy = (loc[1] + p.still.paddingTop).toFloat()
        rect.set(0f, 0f, b.width.toFloat(), b.height.toFloat())
        p.still.imageMatrix.mapRect(rect)
        rect.offset(sx, sy)
        val small = WeatherEggPaint.Box(RectF(rect))

        // 2. 小墨丸
        lastSmallDrawn = smallOn
        if (smallOn) {
            renderSmall(p)
            canvas.save()
            canvas.translate(sx, sy)
            canvas.concat(p.still.imageMatrix)
            canvas.drawBitmap(b, 0f, 0f, bitmapPaint)
            canvas.restore()
        }

        // 3. 天氣素材
        if (!place(p.big)) return
        val big = WeatherEggPaint.Box(
            RectF(
                (loc[0] + p.big.paddingLeft).toFloat(), (loc[1] + p.big.paddingTop).toFloat(),
                (loc[0] + p.big.width - p.big.paddingRight).toFloat(), (loc[1] + p.big.height - p.big.paddingBottom).toFloat(),
            ),
        )
        val list = p.script.motes(t, Capabilities.of(p.look.parts))
        lastMotes = list.size
        for (m in list) motes.draw(canvas, m, small, big, width.toFloat())
    }

    companion object {
        /** 小墨丸開始、結束時跟靜態圖銜接的時間 */
        const val FADE_MS = 300.0
        private const val TAP_COLOR = 0xFF8C8378.toInt()
    }
}
