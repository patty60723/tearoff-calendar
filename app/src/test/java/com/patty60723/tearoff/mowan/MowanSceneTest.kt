package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import com.patty60723.tearoff.weather.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 場景規則（不用畫面也能檢查的部分）。畫得像不像原型由 [MowanGoldenTest] 負責。 */
class MowanSceneTest {
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    @Test
    fun boilIsSixFramesPerSecondInPrototypeOrder() {
        // 每 1/6 秒換一張，順序 0, 3, 1, 4, 2，一秒多一點循環一次
        val seen = (0 until 12).map { Mowan.variantAt((it * 1000 / 6 + 1).toLong()) }
        assertEquals(listOf(0, 3, 1, 4, 2, 0, 3, 1, 4, 2, 0, 3), seen)
        // 同一個 1/6 秒裡不管畫幾次都是同一張（60 FPS 畫面不會讓筆觸變快）
        assertEquals(1, (0L until 166L).map { Mowan.variantAt(it) }.toSet().size)
    }

    @Test
    fun rainIsEightFramesPerSecond() {
        assertEquals(0, Mowan.rainStepAt(124))
        assertEquals(1, Mowan.rainStepAt(125))
        assertEquals(8, Mowan.rainStepAt(1000))
    }

    @Test
    fun umbrellaNeedsRightHand() {
        // 第 3 頁還沒有右手：下雨不撐傘，但雨和皺眉還在
        val noHand = Mowan.frame(parts(3), 0, weather = Mowan.Weather.RAINY, clockMs = 500.0)
        assertNull(noHand.canopy)
        assertTrue(noHand.ops.none { it.layer == Mowan.Layer.SHAFT || it.layer == Mowan.Layer.CANOPY })
        assertTrue(noHand.ops.any { it.layer == Mowan.Layer.RAIN })
        // 第 4 頁有右手就撐傘
        val withHand = Mowan.frame(parts(4), 0, weather = Mowan.Weather.RAINY, clockMs = 500.0)
        assertNotNull(withHand.canopy)
        assertTrue(withHand.ops.any { it.layer == Mowan.Layer.SHAFT })
    }

    @Test
    fun skyIsQuieterBeforeHandsGrow() {
        // 成長焦點期（第 1–3 頁）太陽淡一點，焦點留給剛長出來的部位
        fun sunAlpha(page: Int) = Mowan.frame(parts(page), 0, weather = Mowan.Weather.SUNNY).ops.first { it.layer == Mowan.Layer.SKY }.alpha
        assertEquals(0.75f * 0.55f, sunAlpha(3), 1e-6f)
        assertEquals(0.75f, sunAlpha(4), 1e-6f)
    }

    @Test
    fun noWeatherHasNoSkyOrRain() {
        val f = Mowan.frame(parts(14), 0)
        assertTrue(f.ops.none { it.layer == Mowan.Layer.SKY || it.layer == Mowan.Layer.RAIN })
        assertNull(f.canopy)
    }

    @Test
    fun sunnyStretchLoops() {
        // 伸懶腰 5.5 秒一個來回：0 秒和 5.5 秒是同一個姿勢
        val a = Mowan.frame(parts(14), 0, weather = Mowan.Weather.SUNNY, clockMs = 0.0)
        val b = Mowan.frame(parts(14), 0, weather = Mowan.Weather.SUNNY, clockMs = 5500.0)
        assertEquals(a.head.rotDeg, b.head.rotDeg, 1e-9)
        val mid = Mowan.frame(parts(14), 0, weather = Mowan.Weather.SUNNY, clockMs = 2750.0)
        assertEquals(-12.0, mid.head.rotDeg, 1e-9)
    }

    @Test
    fun everyMoodHasItsOwnV3Scene() {
        // W6：8 種天氣都接上定稿的 V3 場景，彼此不重複
        val expected = mapOf(
            Mood.SUNNY to Mowan.Weather.SUNNY, Mood.HOT to Mowan.Weather.HOT, Mood.CLOUDY to Mowan.Weather.CLOUDY,
            Mood.FOG to Mowan.Weather.FOG, Mood.RAIN to Mowan.Weather.RAINY, Mood.THUNDER to Mowan.Weather.THUNDER,
            Mood.COLD to Mowan.Weather.COLD, Mood.SNOW to Mowan.Weather.SNOW,
        )
        assertEquals(Mood.entries.toSet(), expected.keys)
        for ((mood, weather) in expected) assertEquals("$mood", weather, MowanSettings.weatherOf(mood))
    }

    @Test
    fun pageFourteenOnlyAddsTheBundle() {
        // 第 14 頁唯一的新東西是包袱（W4）；卸下包袱時跟第 13 頁一樣
        val thirteen = Mowan.frame(parts(13), 0).ops.size
        assertEquals(thirteen + 14, Mowan.frame(parts(14), 0).ops.size)
        assertEquals(thirteen, Mowan.frame(parts(14), 0, bundleOn = false).ops.size)
    }
}
