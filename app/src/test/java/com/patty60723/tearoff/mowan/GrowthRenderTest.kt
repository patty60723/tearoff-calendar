package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 成長降級：每一頁、每一種天氣姿勢經過 [Mowan.render] 之後，畫出來的部位剛好是已解鎖、看得到的那些。
 * 姿勢本身會帶著完整的骨架（例如第 3 頁的擦汗姿勢也有右手的控制點），要靠 Renderer 濾掉。
 */
class GrowthRenderTest {
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    /** 會畫出來的部位（包袱 V3 在 W4 才畫） */
    private val drawable = setOf("body", "head", "flick", "eyes", "blush", "armL", "armR", "legL", "legR", "mouth", "redToes", "scarf", "paperHat")
    /** 環境，不屬於角色，每一頁都可以有 */
    private val environment = setOf("ground", "sun", "rain", "clouds", "bolt", "snow", "mist", "heat")
    /** 動作附帶的效果 → 需要的部位 */
    private val effects = mapOf(
        "umbrella" to setOf("armR"),
        "umbrellaGrip" to setOf("armR", "armL"),
        "sweat" to setOf("eyes"),
        "breath" to setOf("mouth"),
    )

    private val times = listOf(0.0, 500.0, 900.0, 1500.0, 2300.0, 3300.0)

    /** 每種天氣的姿勢（雷雨另外放一個驚嚇中的） */
    private fun poses(caps: Capabilities): List<Pair<String, Pose>> = buildList {
        add("一般" to Poses.NORMAL)
        for (t in times) {
            add("晴 $t" to Poses.sunny(t))
            add("雨 $t" to Poses.rainy(t))
            add("熱 $t" to Poses.hot(t, caps))
            add("陰 $t" to Poses.cloudy(t, caps))
            add("霧 $t" to Poses.fog(t, caps))
            add("雷雨 $t" to Poses.thunder(t, caps))
            add("雷雨驚嚇 $t" to Poses.thunder(t, caps, 150.0))
            add("冷 $t" to Poses.cold(t, caps))
            add("雪 $t" to Poses.snow(t, caps))
        }
    }

    @Test
    fun onlyUnlockedPartsAreDrawn() {
        for (page in 1..14) {
            val parts = parts(page)
            val caps = Capabilities.of(parts)
            for (hatOn in listOf(true, false)) {
                val expected = (parts intersect drawable).filterTo(HashSet()) { it != "paperHat" || hatOn }
                for ((name, pose) in poses(caps)) {
                    val at = "第 $page 頁 $name（紙帽${if (hatOn) "戴著" else "脫下"}）"
                    val drawn = Mowan.render(parts, 0, hatOn, pose).ops.map { it.part }
                    assertTrue("$at 有沒標部位的筆畫", drawn.none { it.isEmpty() })
                    val character = drawn.filterTo(HashSet()) { it !in environment && it !in effects.keys }
                    assertEquals("$at 畫出的部位", expected, character)
                    // 動作附帶的效果：需要的部位都解鎖了才能出現
                    for (e in drawn.filterTo(HashSet()) { it in effects }) {
                        assertTrue("$at：$e 需要 ${effects[e]}", parts.containsAll(effects.getValue(e)))
                    }
                    // 雨傘：姿勢有握點、而且有右手才畫
                    val umbrella = "umbrella" in drawn
                    assertEquals("$at 雨傘", pose.hand != null && caps.holdR, umbrella)
                    if (umbrella) assertTrue("$at 沒有右手卻撐傘", "armR" in parts)
                }
            }
        }
    }

    @Test
    fun thunderNeverHoldsAnUmbrellaWithoutARightHand() {
        for (page in 1..3) {
            val parts = parts(page)
            val f = Mowan.render(parts, 0, true, Poses.thunder(1000.0, Capabilities.of(parts), 150.0))
            assertTrue(f.ops.none { it.part == "umbrella" })
            assertEquals(null, f.canopy)
        }
    }

    /** 每一頁的每一種新天氣（含環境）都畫得出來，而且環境不會因為成長階段消失 */
    @Test
    fun newWeathersRenderOnEveryPage() {
        val envOf = mapOf(
            Mowan.Weather.HOT to setOf("sun", "heat"), Mowan.Weather.CLOUDY to setOf("clouds"), Mowan.Weather.FOG to setOf("mist"),
            Mowan.Weather.THUNDER to setOf("clouds", "rain"), Mowan.Weather.COLD to emptySet(), Mowan.Weather.SNOW to setOf("snow"),
        )
        for (page in 1..14) for ((w, env) in envOf) {
            val drawn = Mowan.frame(parts(page), 0, true, w, 1200.0, 9).ops.mapTo(HashSet()) { it.part }
            assertTrue("第 $page 頁 $w 缺環境 ${env - drawn}", drawn.containsAll(env))
            assertTrue("第 $page 頁 $w 沒有地面", "ground" in drawn)
        }
    }
}
