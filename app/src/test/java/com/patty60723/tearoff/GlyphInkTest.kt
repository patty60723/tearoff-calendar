package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [GlyphInk] 的回歸測試：重現過去的誤報與漏報（P7-7），確認新的量法分得出來。
 * 每一項都同時算「舊的量法」，證明舊量法真的會錯（不是只測新量法自己）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class GlyphInkTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val d get() = context.resources.displayMetrics.density

    private fun text(s: String, sp: Float, spacing: Float = 0f) = TextView(context).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        letterSpacing = spacing
        setTextColor(Color.BLACK)
        includeFontPadding = false
        maxLines = 1
    }

    private fun root(w: Int, h: Int, vararg kids: Pair<View, FrameLayout.LayoutParams>): FrameLayout = FrameLayout(context).apply {
        for ((v, lp) in kids) addView(v, lp)
        measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        layout(0, 0, w, h)
    }

    private fun natural(v: View): Pair<Int, Int> {
        v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return v.measuredWidth to v.measuredHeight
    }

    /** 舊量法 1（上一輪）：getTextBounds 的字形範圍 */
    private fun boundsGlyph(root: View, tv: TextView): Rect {
        val l = tv.layout
        val r = GlyphInk.rectIn(root, tv)
        val b = Rect().also { tv.paint.getTextBounds(tv.text.toString(), 0, tv.text.length, it) }
        val x = r.left + tv.totalPaddingLeft + l.getLineLeft(0).toInt()
        val y = r.top + tv.totalPaddingTop + l.getLineBaseline(0)
        return Rect(x + b.left, y + b.top, x + b.right, y + b.bottom)
    }

    /** 誤報（英文月份被切 2%、宜被切 1%）：有字距時 getTextBounds 把最後一個字後面的留白也算進去 */
    @Test
    fun letterSpacingIsNotClipping() {
        val tv = text("OCTOBER 2027", 20f, 0.3f)
        val (w, h) = natural(tv)
        // 外框切在「實際字形右緣 + 0.5dp」：字一點都沒被切
        val probe = root(w * 2, h, tv to FrameLayout.LayoutParams(w, h))
        val inkRight = GlyphInk.glyphs(probe, tv).single().right
        val frameW = inkRight + (0.5f * d).toInt()
        val r = root(frameW, h, text("OCTOBER 2027", 20f, 0.3f).also { it.tag = "t" } to FrameLayout.LayoutParams(w, h))
        val t = r.findViewWithTag<TextView>("t")
        val old = boundsGlyph(r, t)
        assertTrue("舊量法把字距留白算進字形（右緣 ${old.right} > 外框 $frameW + 1dp）", old.right > frameW + d)
        assertEquals("新量法：字沒有被切", 0.0, GlyphInk.clipped(r, t), 0.0)
    }

    /** 漏報（T3／T4 日期被切一半卻判通過）：TextView 在外框裡，但字形比 TextView 高，下半部被切掉 */
    @Test
    fun glyphTallerThanItsViewIsClipping() {
        val tv = text("21", 90f).apply { gravity = Gravity.TOP }
        val (w, _) = natural(tv)
        val h = (tv.textSize * 0.45f).toInt()
        val r = root(w + 20, h + 20, tv to FrameLayout.LayoutParams(w, h))
        val box = GlyphInk.rectIn(r, tv)
        assertTrue("舊量法：TextView 完整在外框裡", Rect(0, 0, r.width, r.height).contains(box) && !GlyphInk.ellipsized(tv))
        assertTrue("新量法：字形被切掉（${GlyphInk.clipped(r, tv)}）", GlyphInk.clipped(r, tv) > 0.3)
    }

    /** 外框（上層）切掉的部分也算 */
    @Test
    fun parentClipsTheGlyph() {
        val tv = text("農曆九月廿二", 14f)
        val (w, h) = natural(tv)
        val r = root(w / 2, h, tv to FrameLayout.LayoutParams(w, h))
        assertTrue(GlyphInk.clipped(r, tv) > 0.3)
    }

    /** 「…」省略：字形都看得到，但內容被省略，算截斷 */
    @Test
    fun ellipsisIsTruncation() {
        val tv = text("宜 嫁娶・裁衣・納采・訂盟・出行", 14f).apply { ellipsize = TextUtils.TruncateAt.END }
        val r = root((80 * d).toInt(), (40 * d).toInt(), tv to FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        assertTrue(GlyphInk.ellipsized(tv))
        assertEquals(1.0, GlyphInk.clipped(r, tv), 0.0)
        assertEquals("「…」截斷", GlyphInk.state(r, tv))
    }

    /** 文字彼此：TextView 的方框重疊（行距留白）但字形沒碰到 → 不算；字形真的疊在一起 → 算 */
    @Test
    fun textBoxesOverlapButInkDoesNot() {
        val a = text("農曆九月", 14f).apply { includeFontPadding = true }
        val b = text("忌 開市", 14f).apply { includeFontPadding = true }
        val (wa, ha) = natural(a)
        val (wb, hb) = natural(b)
        // b 往上疊到 a 的方框裡：只疊進 a 字形下方與 b 字形上方的留白
        val aBottomInk = run { val r = root(wa, ha, a to FrameLayout.LayoutParams(wa, ha)); GlyphInk.glyphs(r, a).single().bottom }
        val bTopInk = run { val r = root(wb, hb, b to FrameLayout.LayoutParams(wb, hb)); GlyphInk.glyphs(r, b).single().top }
        val overlapBox = (ha - aBottomInk) + bTopInk - 2 // 兩邊留白加起來少 2px：方框重疊、字形還差 2px
        assertTrue("這組字形上下有留白（$overlapBox px）", overlapBox > 4)
        val a2 = text("農曆九月", 14f).apply { includeFontPadding = true }
        val b2 = text("忌 開市", 14f).apply { includeFontPadding = true }
        val r = root(maxOf(wa, wb), ha + hb, a2 to FrameLayout.LayoutParams(wa, ha), b2 to FrameLayout.LayoutParams(wb, hb).apply { topMargin = ha - overlapBox })
        assertTrue("舊量法：方框重疊", Rect.intersects(GlyphInk.rectIn(r, a2), GlyphInk.rectIn(r, b2)))
        assertEquals("新量法：字形沒碰到", 0, GlyphInk.touching(r, a2, b2))
        val a3 = text("農曆九月", 14f)
        val b3 = text("忌 開市", 14f)
        val r3 = root(maxOf(wa, wb), ha + hb, a3 to FrameLayout.LayoutParams(wa, ha), b3 to FrameLayout.LayoutParams(wb, hb).apply { topMargin = ha / 3 })
        assertTrue("字形真的疊在一起要抓到", GlyphInk.touching(r3, a3, b3) > 0)
    }

    /** 墨丸（「忌×墨丸」誤報）：ImageView 的方框碰到字，但碰到的地方是透明留白 → 不算；碰到有墨的地方 → 算 */
    @Test
    fun imageTransparentMarginIsNotCollision() {
        val size = (100 * d).toInt()
        // 左半邊透明、右半邊實心
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { Canvas(it).drawRect(size / 2f, 0f, size.toFloat(), size.toFloat(), android.graphics.Paint().apply { color = Color.BLACK }) }
        fun scene(textLeft: Int): Triple<FrameLayout, TextView, ImageView> {
            val tv = text("置產", 14f)
            val iv = ImageView(context).apply { setImageBitmap(bmp); scaleType = ImageView.ScaleType.FIT_XY }
            val (w, h) = natural(tv)
            val r = root(size * 3, size, iv to FrameLayout.LayoutParams(size, size).apply { leftMargin = size }, tv to FrameLayout.LayoutParams(w, h).apply { leftMargin = textLeft; topMargin = size / 3 })
            return Triple(r, tv, iv)
        }
        val (r1, t1, i1) = scene(size + (4 * d).toInt())
        val inkRight = GlyphInk.glyphs(r1, t1).single().right
        assertTrue("這個位置字形在透明的左半邊裡", inkRight < size + size / 2)
        assertTrue("舊量法：方框重疊", Rect.intersects(GlyphInk.rectIn(r1, t1), GlyphInk.rectIn(r1, i1)))
        assertEquals("新量法：沒碰到有墨的地方", 0, GlyphInk.touching(r1, t1, i1))
        val (r2, t2, i2) = scene(size + size / 2 - (6 * d).toInt())
        assertTrue("字形伸進實心的右半邊要抓到", GlyphInk.touching(r2, t2, i2) > 0)
    }
}
