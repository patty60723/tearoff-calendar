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
}
