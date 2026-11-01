package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import com.patty60723.tearoff.mowan.FestivalFixtures.ALL
import com.patty60723.tearoff.mowan.FestivalFixtures.scene
import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.Mowan.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 降級層級與會動到的部位（P6-1 審核強化）。4032 種組合 × 每個節日的主演出與待機。
 * 這裡只驗證規則；握傘點、手的座標要等 P6-3 有演出姿勢才能測（見 docs/character-p6-plan.md「P6-3 要補的測試」）。
 */
class FestivalActionsTest {
    private fun each(block: (FestivalFixtures.Combo, FestivalScene.Result, Action, Level, FestivalActions.Uses) -> Unit) {
        for (c in ALL) {
            val f = c.festival ?: continue
            val s = c.resolve()
            val spec = FestivalScene.spec(f)
            for (a in listOf(spec.main, spec.idle)) {
                val lv = FestivalActions.levelFor(a, s)
                block(c, s, a, lv, FestivalActions.uses(a, lv, s))
            }
        }
    }

    private fun FestivalScene.Result.unlocked(h: Hand) = if (h == Hand.R) caps.holdR else caps.holdL

    /** 不動用還沒解鎖的手、頭、腳 */
    @Test
    fun onlyUnlockedParts() = each { c, s, a, lv, u ->
        for (h in u.hands) assertTrue("$c $a=$lv 用了沒解鎖的${h}手", s.unlocked(h))
        if (u.head) assertTrue("$c $a=$lv 沒有頭", s.caps.head)
        if (u.stand) assertTrue("$c $a=$lv 沒有腳", s.caps.stand)
        if (lv != Level.LINE) assertTrue("$c $a=$lv 沒有頭只能是線條版", s.caps.head)
        assertTrue("$c $a=$lv 不在這個演出的層級裡", lv in FestivalActions.LEVELS.getValue(a))
    }

    /** 同一隻手不操作兩樣：動到的手要嘛空著，要嘛拿著這個節日自己的道具；撐傘的右手不動 */
    @Test
    fun oneThingPerHandAndTheUmbrellaHandStays() = each { c, s, a, lv, u ->
        val prop = FestivalScene.spec(c.festival!!).prop
        for (h in u.hands) {
            val held = s.hand(h)
            assertTrue("$c $a=$lv 的${h}手拿著 $held", held == null || held == prop)
        }
        if (s.hand(Hand.R) == Item.UMBRELLA) assertFalse("$c $a=$lv 動到撐傘的右手", Hand.R in u.hands)
        if (u.umbrellaTilt) assertEquals("$c 傘前傾要有傘", Item.UMBRELLA, s.hand(Hand.R))
    }

    /** 道具換手時，演出用的是實際拿著道具的那隻手 */
    @Test
    fun propActionsUseTheHandThatHoldsTheProp() = each { c, s, a, lv, u ->
        val prop = FestivalScene.spec(c.festival!!).prop ?: return@each
        val holder = s.handOf(prop)
        val propAction = a == Action.BOW || a == Action.LOOK_ENVELOPE || a == Action.TOAST || (a == Action.GAZE_MOON && lv == Level.CAKE)
        if (propAction && u.hands.isNotEmpty()) {
            assertTrue("$c $a=$lv 沒有拿著$prop 卻用手", holder != null)
            assertTrue("$c $a=$lv 拿著$prop 的是${holder}手，動作用 ${u.hands}", holder in u.hands)
        }
        if (holder == null && propAction) assertTrue("$c $a=$lv 沒有道具就不能用手", u.hands.isEmpty() || a == Action.BOW && lv == Level.FULL)
    }

    @Test
    fun handSwapCases() {
        // 中秋第 4 頁：只有右手，月餅改拿右手，舉月餅用右手
        scene(4, Festival.MOON).let { assertEquals(setOf(Hand.R), FestivalActions.uses(Action.TOAST, FestivalActions.levelFor(Action.TOAST, it), it).hands) }
        // 中秋第 14 頁：月餅在左手
        scene(14, Festival.MOON).let { assertEquals(setOf(Hand.L), FestivalActions.uses(Action.TOAST, Level.FULL, it).hands) }
        // 春節雨天：紅包換到左手，撐傘拜年與看紅包都用左手
        scene(14, Festival.SPRING, Weather.RAINY).let {
            assertEquals(setOf(Hand.L), FestivalActions.uses(Action.BOW, FestivalActions.levelFor(Action.BOW, it), it).hands)
            assertEquals(setOf(Hand.L), FestivalActions.uses(Action.LOOK_ENVELOPE, FestivalActions.levelFor(Action.LOOK_ENVELOPE, it), it).hands)
        }
        // 聖誕雷雨：右手撐傘，扶正聖誕襪用左手
        scene(14, Festival.XMAS, Weather.THUNDER).let { assertEquals(setOf(Hand.L), FestivalActions.uses(Action.SOCK, Level.FULL, it).hands) }
        // 聖誕第 4 頁雨天：唯一的右手撐傘 → 不能伸手，抬頭看襪子
        scene(4, Festival.XMAS, Weather.RAINY).let { assertEquals(Level.HEAD, FestivalActions.levelFor(Action.SOCK, it)) }
    }

    /** 紙帽、包袱、節日頭飾開關不影響能力與層級 */
    @Test
    fun equipmentDoesNotChangeAbilities() {
        val groups = ALL.filter { it.festival != null }.groupBy { Triple(it.page, it.festival, it.weather) }
        for ((k, combos) in groups) {
            val f = k.second!!
            val spec = FestivalScene.spec(f)
            val results = combos.map { c ->
                val s = c.resolve()
                listOf(spec.main, spec.idle).map { a -> FestivalActions.levelFor(a, s).let { lv -> lv to FestivalActions.uses(a, lv, s) } } to s.caps
            }
            assertEquals("$k 裝備改變了層級或能力", 1, results.toSet().size)
        }
    }

    // ---------- D-37：看不到月亮就看月餅 ----------

    @Test
    fun noMoonMeansLookAtTheMooncake() {
        for (page in FestivalFixtures.PAGES) for (w in Weather.entries) {
            val s = scene(page, Festival.MOON, w)
            val moon = s.shown(Item.MOON)
            for (a in listOf(Action.TOAST, Action.GAZE_MOON)) {
                val lv = FestivalActions.levelFor(a, s)
                if (!moon) {
                    assertFalse("第 $page 頁 $w $a=$lv 抬頭看空的天空", FestivalActions.looksAtSky(a, lv))
                    assertEquals("第 $page 頁 $w $a", if (s.handOf(Item.MOONCAKE) != null) Level.CAKE else Level.LINE, lv)
                } else assertTrue("第 $page 頁 $w $a 有月亮時不用看月餅版", lv != Level.CAKE)
            }
        }
        // 第 14 頁：雨、雷雨、雪看月餅；陰、霧看淡月亮
        for (w in listOf(Weather.RAINY, Weather.THUNDER, Weather.SNOW)) assertEquals("$w", Level.CAKE, FestivalActions.levelFor(Action.TOAST, scene(14, Festival.MOON, w)))
        for (w in listOf(Weather.CLOUDY, Weather.FOG)) assertEquals("$w", Level.FULL, FestivalActions.levelFor(Action.TOAST, scene(14, Festival.MOON, w)))
    }
}
