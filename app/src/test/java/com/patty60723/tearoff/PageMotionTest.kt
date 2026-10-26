package com.patty60723.tearoff

import android.animation.ValueAnimator
import android.widget.ViewFlipper
import com.patty60723.tearoff.mowan.MowanSettings
import com.patty60723.tearoff.story.StoryBook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * P5-1（W7 M7）：App 日曆頁上的小人是小工具的 ViewFlipper，系統關閉動畫時不能一直輪播。
 * 舊火柴人和 V3 故事卡兩種模式下，日曆頁都是同一個 ViewFlipper。
 */
@RunWith(RobolectricTestRunner::class)
class PageMotionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun setDurationScale(scale: Float) {
        ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, scale)
    }

    @After
    fun restore() {
        setDurationScale(1f)
        MowanSettings.setStoryCard(context, false)
    }

    private fun open(): MainActivity {
        StoryBook(context).jumpTo(5, AppClock.today(context))
        return Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }

    private val MainActivity.flipper get() = findViewById<ViewFlipper>(R.id.stickman)

    @Test
    fun pageFlipsWhenAnimationsAreOn() {
        assertTrue(open().flipper.isFlipping)
    }

    @Test
    fun pageStaysStillWhenAnimationsAreOff() {
        for (v3 in listOf(false, true)) {
            MowanSettings.setStoryCard(context, v3)
            setDurationScale(0f)
            val a = open()
            assertFalse("故事卡用 V3：$v3", a.flipper.isFlipping)
            assertEquals(0, a.flipper.displayedChild)
            setDurationScale(1f)
        }
    }

    @Test
    fun followsTheSettingWithoutGettingStuck() {
        val a = open()
        // Robolectric 改設定不會發通知，直接叫 App 收到通知時會叫的那個方法
        setDurationScale(0f)
        a.updatePageMotion()
        assertFalse(a.flipper.isFlipping)
        assertEquals(0, a.flipper.displayedChild)
        setDurationScale(1f)
        a.updatePageMotion()
        assertTrue(a.flipper.isFlipping)
    }
}
