package com.patty60723.tearoff

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * P7-7：判斷文字「看得到的部分」用的共用工具（正式實作計畫 §5；研究見 character-p7-7-prep.md §9.1）。
 *
 * 一律用**實際畫出來的像素**，不用 TextView 的邊界，也不用 `Paint.getTextBounds`：
 * - TextView 的邊界含行距與字距留白：上一輪「截斷：天氣」的誤報。
 * - `getTextBounds` 在有 `letterSpacing` 時把字距留白也算進字形：「英文月份被切 2%」「宜被切 1%」的誤報。
 * - 只看 TextView 在不在外框裡、不看字形：日期字形被切掉一半卻判通過的漏報（T3／T4）。
 * - 墨丸用 ImageView 的整個方框（含透明留白）：「忌×墨丸」的誤報。
 *
 * 字形範圍：同一支 Paint 把那一行畫在夠大的透明圖上，alpha > [INK_ALPHA] 的像素外框。
 */
object GlyphInk {
    const val INK_ALPHA = 32

    fun rectIn(root: View, v: View): Rect {
        var x = 0
        var y = 0
        var cur: View = v
        while (cur !== root) { x += cur.left - cur.scrollX; y += cur.top - cur.scrollY; cur = cur.parent as View }
        return Rect(x, y, x + v.width, y + v.height)
    }

    private val cache = HashMap<String, Rect>()

    /** 一行字實際有墨的範圍，相對於起點（x = 0）與基線（y = 0） */
    fun ink(paint: Paint, s: String): Rect =
        cache.getOrPut("$s|${paint.textSize}|${paint.letterSpacing}|${paint.typeface?.hashCode()}|${paint.isFakeBoldText}|${paint.textSkewX}|${paint.textScaleX}") { inkOf(paint, s) }

    private fun inkOf(paint: Paint, s: String): Rect {
        val p = Paint(paint).apply { color = Color.BLACK; shader = null }
        val fm = p.fontMetricsInt
        val pad = (p.textSize / 4).toInt() + 4
        val w = p.measureText(s).toInt() + pad * 2
        val h = (fm.bottom - fm.top) + pad * 2
        val bmp = Bitmap.createBitmap(maxOf(1, w), maxOf(1, h), Bitmap.Config.ARGB_8888)
        val base = pad - fm.top
        Canvas(bmp).drawText(s, pad.toFloat(), base.toFloat(), p)
        var l = w; var t = h; var r = -1; var b = -1
        val row = IntArray(bmp.width)
        for (y in 0 until bmp.height) {
            bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
            for (x in row.indices) if ((row[x] ushr 24) > INK_ALPHA) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        }
        bmp.recycle()
        return if (r < 0) Rect() else Rect(l - pad, t - base, r + 1 - pad, b + 1 - base)
    }

    /** 每一行字形實際的範圍（root 座標）；有「…」時算的是省略後實際畫的字 */
    fun glyphs(root: View, tv: TextView): List<Rect> {
        val l = tv.layout ?: return emptyList()
        val r = rectIn(root, tv)
        val text = tv.text.toString()
        return (0 until l.lineCount).mapNotNull { i ->
            var s = text.substring(l.getLineStart(i), l.getLineEnd(i))
            val e = l.getEllipsisCount(i)
            if (e > 0) { val at = l.getEllipsisStart(i).coerceIn(0, s.length); s = s.substring(0, at) + "…" }
            s = s.trimEnd()
            if (s.isEmpty()) return@mapNotNull null
            val b = ink(tv.paint, s)
            val x = r.left + tv.totalPaddingLeft + l.getLineLeft(i).toInt()
            val y = r.top + tv.totalPaddingTop + l.getLineBaseline(i)
            Rect(x + b.left, y + b.top, x + b.right, y + b.bottom)
        }
    }

    /** 這個元件畫出來看得到的範圍：自己 ∩ 每一層上層（有 clipToPadding 的扣掉內距）∩ root 外框 */
    fun visibleArea(root: View, v: View): Rect {
        val area = rectIn(root, v)
        var cur = v.parent as? ViewGroup
        while (cur != null) {
            val a = if (cur === root) Rect(0, 0, root.width, root.height) else rectIn(root, cur)
            if (cur.clipToPadding) { a.left += cur.paddingLeft; a.top += cur.paddingTop; a.right -= cur.paddingRight; a.bottom -= cur.paddingBottom }
            if (!area.intersect(a)) return Rect()
            if (cur === root) break
            cur = cur.parent as? ViewGroup
        }
        return area
    }

    fun ellipsized(tv: TextView): Boolean {
        val l = tv.layout ?: return false
        return (0 until l.lineCount).any { l.getEllipsisCount(it) > 0 }
    }

    /**
     * 字形有多少被切掉：回傳被切掉的比例（0＝完整；有「…」是 1）。容許 [tolDp]（預設 1dp，抗鋸齒與四捨五入）。
     * 沒有顯示（GONE／INVISIBLE／沒有字）是 0。
     */
    fun clipped(root: View, tv: TextView, tolDp: Float = 1f): Double {
        if (tv.visibility != View.VISIBLE || tv.text.isEmpty()) return 0.0
        if (ellipsized(tv)) return 1.0
        if (tv.width == 0 || tv.height == 0) return 1.0
        val vis = visibleArea(root, tv)
        val tol = (tolDp * tv.resources.displayMetrics.density).toInt()
        return glyphs(root, tv).maxOfOrNull { g ->
            val seen = Rect(g)
            if (!seen.intersect(vis)) return@maxOfOrNull 1.0
            val lostH = (g.height() - seen.height()).coerceAtLeast(0)
            val lostW = (g.width() - seen.width()).coerceAtLeast(0)
            if (lostH <= tol && lostW <= tol) 0.0 else maxOf(lostH.toDouble() / g.height(), lostW.toDouble() / g.width())
        } ?: 0.0
    }

    /** 文字狀態（給報告）：完整／「…」／被切 N% */
    fun state(root: View, tv: TextView): String = when {
        ellipsized(tv) -> "「…」截斷"
        else -> clipped(root, tv).let { if (it > 0) "被切掉 %.0f%%".format(it * 100) else "完整" }
    }

    /** 一個元件單獨畫出來、放回 root 座標的有墨像素（alpha > [INK_ALPHA]；ImageView 的透明留白不算） */
    fun mask(root: View, part: View): BooleanArray {
        val r = rectIn(root, part)
        val vis = visibleArea(root, part)
        val b = Bitmap.createBitmap(maxOf(1, root.width), maxOf(1, root.height), Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            clipRect(vis)
            translate(r.left.toFloat(), r.top.toFloat())
            part.draw(this)
        }
        val px = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }
        b.recycle()
        return BooleanArray(px.size) { (px[it] ushr 24) > INK_ALPHA }
    }

    /** 兩組有墨像素重疊的數量 */
    fun overlap(a: BooleanArray, b: BooleanArray): Int { var n = 0; for (i in a.indices) if (a[i] && b[i]) n++; return n }

    /** 兩個元件實際有墨的像素有沒有碰到（重疊像素數） */
    fun touching(root: View, a: View, b: View): Int = overlap(mask(root, a), mask(root, b))
}
