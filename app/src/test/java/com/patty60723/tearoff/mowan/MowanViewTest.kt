package com.patty60723.tearoff.mowan

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** 什麼時候該排下一次重畫、什麼時候不該 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MowanViewTest {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val parent = FrameLayout(activity).also { activity.setContentView(it) }
    private val view = MowanView(activity).apply { parts = setOf("body", "head", "flick", "eyes", "blush", "armR", "legL", "legR") }

    init {
        parent.addView(view, FrameLayout.LayoutParams(240, 320))
        parent.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 480, 640)
    }

    private fun draw() = view.draw(Canvas(Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)))

    private fun setDurationScale(scale: Float) {
        // 系統的「關閉動畫」設定；ValueAnimator.setDurationScale 是隱藏 API，測試裡用反射
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    @After
    fun restoreAnimations() = setDurationScale(1f)

    @Test
    fun keepsBoilingWhileShown() {
        draw()
        assertTrue(view.isTicking)
        view.weather = Mowan.Weather.RAINY
        draw()
        assertTrue(view.isTicking)
    }

    @Test
    fun stopsWhenHidden() {
        draw()
        view.visibility = View.GONE
        assertFalse(view.isTicking)
    }

    @Test
    fun stopsWhenDetached() {
        draw()
        parent.removeView(view)
        assertFalse(view.isTicking)
    }

    @Test
    fun reducedMotionDrawsStillFrame() {
        setDurationScale(0f)
        view.weather = Mowan.Weather.SUNNY
        draw()
        assertFalse(view.isTicking)
    }
}
