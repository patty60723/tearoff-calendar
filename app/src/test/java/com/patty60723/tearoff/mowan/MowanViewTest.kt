package com.patty60723.tearoff.mowan

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertEquals
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

    private fun advance(ms: Long) = org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(ms))

    @Test
    fun pageHatAndBundleDoNotRestartTheMotion() {
        view.weather = Mowan.Weather.SUNNY
        val later = android.os.SystemClock.uptimeMillis() + 2000
        val before = view.timeline.sample(later, false).clockMs
        advance(500)
        view.parts = view.parts + "armL"
        view.hatOn = false
        view.bundleOn = false
        assertEquals("換頁、紙帽、包袱不會讓伸懶腰重來", before, view.timeline.sample(later, false).clockMs, 0.0)
        // 同一種天氣再設一次（天氣資料更新）也不重來
        view.weather = Mowan.Weather.SUNNY
        assertEquals(before, view.timeline.sample(later, false).clockMs, 0.0)
        // 換天氣才從頭開始
        view.weather = Mowan.Weather.HOT
        assertEquals(before - 500, view.timeline.sample(later, false).clockMs, 0.0)
    }

    @Test
    fun reducedMotionTogglesWithoutGettingStuck() {
        view.weather = Mowan.Weather.THUNDER
        view.reducedMotion = { true }
        draw()
        assertFalse("關閉動畫時不排重畫", view.isTicking)
        view.reducedMotion = { false }
        draw()
        assertTrue("打開動畫後繼續動", view.isTicking)
    }
}
