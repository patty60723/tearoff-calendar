package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/** 14 頁的能力與外觀 */
class CapabilitiesTest {
    private val golden = JSONObject(File("src/test/resources/mowan/golden.json").readText())
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))
    private fun caps(page: Int) = Capabilities.of(parts(page))

    /** 跟原型 getCapabilities() 每一頁、每一項都一樣 */
    @Test
    fun matchesPrototype() {
        val pages = golden.getJSONArray("capabilities")
        assertEquals(14, pages.length())
        for (i in 0 until 14) {
            val g = pages.getJSONObject(i)
            val c = caps(i + 1)
            val at = "第 ${i + 1} 頁"
            val mine = mapOf(
                "body" to c.body, "head" to c.head, "headTurn" to c.head, "gaze" to c.gaze, "expression" to c.expression,
                "smile" to c.smile, "holdR" to c.holdR, "holdL" to c.holdL, "stand" to c.stand, "walk" to c.walk,
                "costume" to c.costume, "paperHat" to c.paperHat, "twoHands" to c.twoHands, "growthFocus" to c.growthFocus,
            )
            for ((k, v) in mine) assertEquals("$at $k", g.getBoolean(k), v)
            val stage = mapOf("line" to Capabilities.Stage.LINE, "head" to Capabilities.Stage.HEAD, "oneHand" to Capabilities.Stage.ONE_HAND,
                "twoHands" to Capabilities.Stage.TWO_HANDS, "full" to Capabilities.Stage.FULL)
            assertEquals(at, stage.getValue(g.getString("stage")), c.stage)
        }
    }

    @Test
    fun stagesAcrossTheChapter() {
        val stages = (1..14).map { caps(it).stage }
        assertEquals(
            listOf(
                Capabilities.Stage.LINE,
                Capabilities.Stage.HEAD, Capabilities.Stage.HEAD,
                Capabilities.Stage.ONE_HAND,
                Capabilities.Stage.TWO_HANDS, Capabilities.Stage.TWO_HANDS,
            ) + List(8) { Capabilities.Stage.FULL },
            stages,
        )
    }

    private fun Mowan.Frame.signature() = ops.map { listOf(it.layer, it.fill, it.alpha, it.path.commands) }

    @Test
    fun pagesTwelveAndThirteenLookLikePageEleven() {
        // 第 12、13 頁沒有新的部位（Confirmed：外觀不變）
        for (v in 0 until Mowan.FRAMES) {
            val eleven = Mowan.frame(parts(11), v).signature()
            assertEquals(eleven, Mowan.frame(parts(12), v).signature())
            assertEquals(eleven, Mowan.frame(parts(13), v).signature())
        }
    }

    @Test
    fun eachPageAddsWhatItUnlocks() {
        // 第 1–11 頁每一頁都會多出東西；第 14 頁多了包袱，卸下包袱時跟第 13 頁一樣
        for (page in 2..11) {
            assertNotEquals("第 $page 頁", Mowan.frame(parts(page - 1), 0).signature(), Mowan.frame(parts(page), 0).signature())
        }
        assertNotEquals(Mowan.frame(parts(13), 0).signature(), Mowan.frame(parts(14), 0).signature())
        assertEquals(Mowan.frame(parts(13), 0).signature(), Mowan.frame(parts(14), 0, bundleOn = false).signature())
    }

    @Test
    fun hatNeedsToBeGrantedAndWorn() {
        // 第 10 頁還沒拿到：不管裝備狀態都不畫
        assertEquals(Mowan.frame(parts(10), 0, hatOn = true).signature(), Mowan.frame(parts(10), 0, hatOn = false).signature())
        // 拿到之後，脫下就少了紙帽（帽身 2 筆＋摺線 2 筆）
        assertEquals(Mowan.frame(parts(11), 0, hatOn = true).ops.size - 4, Mowan.frame(parts(11), 0, hatOn = false).ops.size)
        // 脫下紙帽跟第 10 頁一樣（紙帽是唯一的差別）
        assertEquals(Mowan.frame(parts(10), 0).signature(), Mowan.frame(parts(11), 0, hatOn = false).signature())
    }
}
