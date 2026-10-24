package com.patty60723.tearoff.mowan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MowanSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun paperHatIsWornByDefaultAndRemembered() {
        assertTrue(MowanSettings.hatOn(context))
        MowanSettings.setHatOn(context, false)
        assertFalse(MowanSettings.hatOn(context))
        MowanSettings.resetEquipment(context)
        assertTrue(MowanSettings.hatOn(context))
    }

    @Test
    fun storyCardUsesOldStickmanByDefault() {
        assertFalse(MowanSettings.storyCard(context))
    }

    @Test
    fun bundleIsWornByDefaultAndRemembered() {
        assertTrue(MowanSettings.bundleOn(context))
        MowanSettings.setBundleOn(context, false)
        // 重新開 App：從 SharedPreferences 重新讀
        val reopened = context.getSharedPreferences("mowan", android.content.Context.MODE_PRIVATE)
        assertFalse(reopened.getBoolean("bundleOn", true))
        assertFalse(MowanSettings.bundleOn(context))
        MowanSettings.setBundleOn(context, true)
        assertTrue(MowanSettings.bundleOn(context))
        // 卸下後再從頭開始故事，回到預設的背著
        MowanSettings.setBundleOn(context, false)
        MowanSettings.resetEquipment(context)
        assertTrue(MowanSettings.bundleOn(context))
        // 紙帽和包袱是分開存的
        MowanSettings.setBundleOn(context, false)
        assertTrue(MowanSettings.hatOn(context))
    }
}
