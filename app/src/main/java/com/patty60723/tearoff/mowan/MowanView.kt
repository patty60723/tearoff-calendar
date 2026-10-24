package com.patty60723.tearoff.mowan

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

/**
 * 墨丸 V3 的靜態預覽（Phase 2B P1）。還沒有 line boil 動畫，也還沒取代故事卡上的火柴人；
 * 目前只在除錯選單裡用。
 */
class MowanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** 已解鎖的部位，見 [Mowan.partsOf] */
    var parts: Set<String> = emptySet()
        set(value) { field = value; rebuild() }

    /** 筆觸影格 0–4 */
    var variant: Int = 0
        set(value) { field = value.mod(Mowan.FRAMES); rebuild() }

    var hatOn: Boolean = true
        set(value) { field = value; rebuild() }

    private var painter = MowanPainter(Mowan.frame(parts, variant, hatOn))

    private fun rebuild() {
        painter = MowanPainter(Mowan.frame(parts, variant, hatOn))
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        painter.draw(
            canvas, paddingLeft.toFloat(), paddingTop.toFloat(),
            (width - paddingLeft - paddingRight).toFloat(), (height - paddingTop - paddingBottom).toFloat(),
        )
    }
}
