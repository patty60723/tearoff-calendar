package com.patty60723.tearoff.stickman

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

/** App 裡會一直動的火柴人。 */
class StickmanView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    var parts: Set<String> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    var action: Action = Actions.IDLE
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val phase = (SystemClock.uptimeMillis() % action.periodMs) / action.periodMs.toFloat()
        StickmanRenderer.draw(canvas, parts, action, phase, width.toFloat(), height.toFloat())
        if (isShown) postInvalidateOnAnimation()
    }
}
