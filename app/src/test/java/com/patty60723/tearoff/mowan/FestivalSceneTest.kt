package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import com.patty60723.tearoff.mowan.FestivalFixtures.ALL
import com.patty60723.tearoff.mowan.FestivalFixtures.scene
import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.FestivalScene.Kind
import com.patty60723.tearoff.mowan.FestivalScene.Slot
import com.patty60723.tearoff.mowan.Mowan.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 節日的插槽與降級層級（P6-1）。原型 mowan-festival.html 的 T10–T14、T16、T18 移植，
 * 再擴充到 8 種天氣、14 頁、紙帽、包袱、節日頭飾開關（[FestivalFixtures.ALL]，4032 種組合）。
 */
class FestivalSceneTest {
    private fun bow(page: Int, weather: Weather = Weather.SUNNY) = scene(page, Festival.SPRING, weather).let { it to FestivalActions.levelFor(Action.BOW, it) }

    // ---------- 原型 P1-02 ----------

    @Test
    fun t10_page14SunnyIsFull() = assertEquals(Level.FULL, bow(14).second)

    @Test
    fun t11_page14RainIsRainLevel() {
        val (s, lv) = bow(14, Weather.RAINY)
        assertEquals("不是單手版", Level.RAIN, lv)
        assertEquals(Item.UMBRELLA, s.hand(Hand.R))
        assertEquals(Item.ENVELOPE, s.hand(Hand.L))
    }

    @Test
    fun t12_page5RainIsRainLevel() = assertEquals("雙手、還沒有腳", Level.RAIN, bow(5, Weather.RAINY).second)

    @Test
    fun t13_page4() {
        assertEquals("晴：單手版", Level.ONE_HAND, bow(4).second)
        val (s, lv) = bow(4, Weather.RAINY)
        assertEquals("雨：唯一的手撐傘 → 頭部版", Level.HEAD, lv)
        assertEquals(Item.UMBRELLA, s.hand(Hand.R))
        assertFalse("沒有空手就不拿紅包", s.shown(Item.ENVELOPE))
    }

    @Test
    fun t14_page2HeadPage1Line() {
        assertEquals(Level.HEAD, bow(2).second)
        assertEquals(Level.LINE, bow(1).second)
    }

    // ---------- 原型回歸 T16、T18（擴充） ----------

    /** T16：第 1–3 頁沒有任何手持道具與服裝（原型 36 種組合；這裡 864 種） */
    @Test
    fun t16_noPropsOrCostumesOnPagesOneToThree() {
        val bad = ALL.filter { it.page <= 3 }.filter { c ->
            c.resolve().objects.any { it.shown && it.item.kind in setOf(Kind.PROP, Kind.COSTUME, Kind.PERSONAL, Kind.WEATHER_PROP) }
        }
        assertEquals(bad.take(3).joinToString(), 0, bad.size)
    }

    /** T18：有手部動作的層級，所需的手都已解鎖（主演出與待機 × 4032 種組合） */
    @Test
    fun t18_handsNeededAreUnlocked() {
        for (c in ALL) {
            val f = c.festival ?: continue
            val s = c.resolve()
            val spec = FestivalScene.spec(f)
            for (a in listOf(spec.main, spec.idle)) {
                val lv = FestivalActions.levelFor(a, s)
                val caps = s.caps
                val need = when (lv) {
                    Level.FULL -> if (a == Action.BOW) caps.twoHands else if (a in setOf(Action.PEEK_GIFT, Action.GAZE_MOON)) caps.head else caps.holdL || caps.holdR
                    Level.RAIN -> caps.twoHands
                    Level.ONE_HAND -> caps.holdL != caps.holdR
                    Level.POINT -> caps.holdL || caps.holdR
                    Level.CAKE -> (caps.holdL || caps.holdR) && !s.shown(Item.MOON)
                    Level.HEAD -> caps.head
                    Level.LINE -> true
                }
                assertTrue("$c $a=$lv", need)
                assertTrue("$c $a=$lv 不在原型的層級裡", lv in FestivalActions.LEVELS.getValue(a))
            }
        }
    }

    // ---------- 擴充：手、道具、能力 ----------

    /** 每個層級動到的部位：只用已解鎖的；動到的手要嘛空著、要嘛拿著這個演出自己的道具 */
    @Test
    fun levelsOnlyUseUnlockedParts() {
        for (c in ALL) {
            val f = c.festival ?: continue
            val s = c.resolve()
            val spec = FestivalScene.spec(f)
            for (a in listOf(spec.main, spec.idle)) {
                val lv = FestivalActions.levelFor(a, s)
                val u = FestivalActions.uses(a, lv, s)
                for (h in u.hands) {
                    assertTrue("$c $a=$lv 用了還沒解鎖的${h}手", if (h == Hand.R) s.caps.holdR else s.caps.holdL)
                    val held = s.hand(h)
                    assertTrue("$c $a=$lv 的${h}手已經拿著 $held", held == null || held == spec.prop)
                }
                if (u.head) assertTrue("$c $a=$lv 沒有頭", s.caps.head)
                if (u.stand) assertTrue("$c $a=$lv 沒有腳", s.caps.stand)
                if (u.umbrellaTilt) assertEquals("$c 撐傘拜年要有傘", Item.UMBRELLA, s.hand(Hand.R))
                if (!s.caps.head) assertEquals("$c 沒有頭只能線條版", Level.LINE, lv)
            }
        }
    }

    /** 同一隻手不拿兩樣；雨傘只在右手；沒解鎖的手不拿東西；手持道具不放地上 */
    @Test
    fun handsHoldAtMostOneThing() {
        for (c in ALL) {
            val s = c.resolve()
            val held = listOf(s.hand(Hand.L), s.hand(Hand.R)).filterNotNull()
            assertEquals("$c 同一樣東西拿在兩隻手", held.size, held.toSet().size)
            if (s.hand(Hand.L) != null) assertTrue("$c 左手沒解鎖", s.caps.holdL)
            if (s.hand(Hand.R) != null) assertTrue("$c 右手沒解鎖", s.caps.holdR)
            assertTrue("$c 雨傘不在左手", s.hand(Hand.L) != Item.UMBRELLA)
            for (o in s.objects.filter { it.item.kind == Kind.PROP && it.shown }) assertTrue("$c ${o.item} 顯示但不在手上", s.handOf(o.item) != null)
            assertTrue("$c 地面只放禮物", s[Slot.GROUND_R] == null || s[Slot.GROUND_R] == Item.GIFT)
        }
    }

    /** 春雨：依能力，不依頁數。雙手 → 右手撐傘、左手紅包；只有右手 → 撐傘、沒有紅包；沒有手 → 都沒有 */
    @Test
    fun springRainFollowsCapabilities() {
        for (page in FestivalFixtures.PAGES) for (w in listOf(Weather.RAINY, Weather.THUNDER)) {
            val s = scene(page, Festival.SPRING, w)
            val c = s.caps
            when {
                c.twoHands -> {
                    assertEquals("第 $page 頁 $w", Item.UMBRELLA, s.hand(Hand.R))
                    assertEquals("第 $page 頁 $w", Item.ENVELOPE, s.hand(Hand.L))
                    assertEquals("第 $page 頁 $w", Level.RAIN, FestivalActions.levelFor(Action.BOW, s))
                }
                c.holdR -> {
                    assertEquals("第 $page 頁 $w", Item.UMBRELLA, s.hand(Hand.R))
                    assertNull("第 $page 頁 $w", s.hand(Hand.L))
                    assertFalse("第 $page 頁 $w", s.shown(Item.ENVELOPE))
                }
                else -> {
                    assertFalse("第 $page 頁 $w", s.shown(Item.UMBRELLA))
                    assertFalse("第 $page 頁 $w", s.shown(Item.ENVELOPE))
                }
            }
        }
    }

    /** 能力剛解鎖的邊界頁：第 3（沒有手）、4（右手）、5（雙手）、6（第一隻腳）、7（雙腳）、11（紙帽）、14（包袱） */
    @Test
    fun capabilityBoundaries() {
        // 第 3 頁：還沒有手 → 成長焦點期，不拿道具、不戴頭飾
        scene(3, Festival.SPRING).let {
            assertTrue(it.caps.growthFocus)
            assertFalse(it.shown(Item.ENVELOPE)); assertFalse(it.shown(Item.CAISHEN))
            assertTrue("只留春聯當線索，降低對比", it.of(Item.COUPLET)!!.let { o -> o.shown && o.dim })
        }
        // 第 4 頁：只有右手
        scene(4, Festival.SPRING).let { assertEquals(Item.ENVELOPE, it.hand(Hand.R)); assertTrue(it.shown(Item.CAISHEN)) }
        scene(4, Festival.MOON).let { assertEquals("月餅先放左手，沒有左手改右手", Item.MOONCAKE, it.hand(Hand.R)) }
        scene(4, Festival.MOON, Weather.RAINY).let { assertFalse("右手撐傘，沒有空手", it.shown(Item.MOONCAKE)) }
        assertEquals("第 4 頁晴天扶正聖誕襪：沒有腳 → 指向版", Level.POINT, FestivalActions.levelFor(Action.SOCK, scene(4, Festival.XMAS)))
        // 第 5 頁：雙手
        scene(5, Festival.MOON).let { assertEquals(Item.MOONCAKE, it.hand(Hand.L)) }
        assertEquals(Level.POINT, FestivalActions.levelFor(Action.SOCK, scene(5, Festival.XMAS)))
        // 第 6 頁：一隻腳就能站
        assertEquals(Level.FULL, FestivalActions.levelFor(Action.SOCK, scene(6, Festival.XMAS)))
        // 第 5 頁雷雨：雙手 → 撐傘＋紅包（節日當天不用雙手握傘，D-31）
        scene(5, Festival.SPRING, Weather.THUNDER).let { assertEquals(Item.ENVELOPE, it.hand(Hand.L)) }
        // 第 10、11 頁：紙帽取得
        assertFalse(scene(10, null).shown(Item.PAPER_HAT))
        assertTrue(scene(11, null).shown(Item.PAPER_HAT))
        // 第 13、14 頁：包袱取得
        assertFalse(scene(13, null).shown(Item.BUNDLE))
        assertTrue(scene(14, null).shown(Item.BUNDLE))
    }

    // ---------- 紙帽、頭飾、包袱 ----------

    /** D-05：節日頭飾優先，紙帽暫時不畫；紙帽的裝備狀態不被改（resolve 只讀，隔天照裝備狀態回來） */
    @Test
    fun festivalHeadwearHidesPaperHatWithoutChangingIt() {
        for (c in ALL) {
            val s = c.resolve()
            val costume = c.festival?.let { FestivalScene.spec(it).costume }
            val both = s.shown(Item.PAPER_HAT) && costume != null && s.shown(costume)
            assertFalse("$c 紙帽與節日頭飾同時出現", both)
            // 沒有節日、同樣的裝備狀態：紙帽只看「拿到了沒」與「戴著沒」
            val plain = FestivalScene.resolve(c.parts, null, c.weather, c.hatOn, c.bundleOn)
            assertEquals("$c", s.caps.paperHat && c.hatOn, plain.shown(Item.PAPER_HAT))
            if (costume != null && s.shown(costume) && s.caps.paperHat && c.hatOn)
                assertTrue("$c 原因要說明裝備狀態不變", s.of(Item.PAPER_HAT)!!.reason.contains("裝備狀態不變"))
            if (!c.headwear && s.caps.paperHat && c.hatOn) assertTrue("$c 節日頭飾關掉時紙帽照戴", s.shown(Item.PAPER_HAT))
        }
    }

    @Test
    fun bundleFollowsEquipmentOnly() {
        for (c in ALL) assertEquals("$c", "bundle" in c.parts && c.bundleOn, c.resolve().shown(Item.BUNDLE))
    }

    // ---------- 環境 ----------

    /** D-32：雨、雷雨、雪看不到月亮；陰、霧降低對比；有月亮時晴、熱不畫太陽 */
    @Test
    fun moonAndSun() {
        for (page in FestivalFixtures.PAGES) for (w in Weather.entries) {
            val s = scene(page, Festival.MOON, w)
            val moon = s.of(Item.MOON)!!
            when (w) {
                Weather.RAINY, Weather.THUNDER, Weather.SNOW -> assertFalse("$page $w", moon.shown)
                Weather.CLOUDY, Weather.FOG -> assertTrue("$page $w", moon.shown && moon.dim)
                else -> assertTrue("$page $w", moon.shown && moon.dim == s.caps.growthFocus)
            }
            if (w == Weather.SUNNY || w == Weather.HOT) assertFalse("$page $w 有月亮不畫太陽", s.shown(Item.SUN))
        }
        for (f in listOf(Festival.SPRING, Festival.XMAS, null)) for (w in listOf(Weather.SUNNY, Weather.HOT))
            assertTrue("$f $w 沒有月亮照畫太陽", scene(14, f, w).shown(Item.SUN))
    }

    /** 天氣環境一直都在：有節日或沒有，天氣環境物件都一樣 */
    @Test
    fun weatherEnvironmentAlwaysStays() {
        for (c in ALL) {
            val s = c.resolve()
            val plain = FestivalScene.resolve(c.parts, null, c.weather, c.hatOn, c.bundleOn)
            val env = { r: FestivalScene.Result -> r.objects.filter { it.item.kind == Kind.WEATHER_ENV && it.item != Item.SUN && it.shown }.map { it.item }.toSet() }
            assertEquals("$c", env(plain), env(s))
            assertEquals("$c 雨傘只看天氣與右手", plain.shown(Item.UMBRELLA), s.shown(Item.UMBRELLA))
        }
    }

    /** 成長焦點期：節日環境只留一個線索，降低對比 */
    @Test
    fun growthFocusKeepsOneCue() {
        for (c in ALL.filter { it.festival != null }) {
            val s = c.resolve()
            if (!s.caps.growthFocus) continue
            val spec = FestivalScene.spec(c.festival!!)
            val env = s.objects.filter { it.item.kind == Kind.FESTIVAL_ENV && it.shown }
            assertTrue("$c 最多一個線索", env.size <= 1)
            env.forEach { assertTrue("$c", it.item == spec.cue && it.dim) }
        }
    }

    /** D-31：節日當天不播天氣日常動作、不播雷雨驚嚇；沒有節日時照舊 */
    @Test
    fun festivalDaySuppressesWeatherDaily() {
        for (c in ALL) {
            val s = c.resolve()
            assertEquals("$c", c.festival == null, s.weatherDaily)
            assertEquals("$c", c.festival == null && c.weather == Weather.THUNDER, s.thunderStartle)
        }
    }

    /** 插槽與「顯示」一致；不顯示的物件都有原因；每個物件只出現一次 */
    @Test
    fun slotsMatchObjectsAndEveryDecisionHasAReason() {
        for (c in ALL) {
            val s = c.resolve()
            assertEquals("$c", s.objects.size, s.objects.map { it.item }.toSet().size)
            for (o in s.objects) {
                assertTrue("$c ${o.item} 沒有原因", o.reason.isNotBlank())
                val slot = o.item.slot ?: continue
                assertEquals("$c ${o.item}", o.shown, s[slot] == o.item)
            }
            for ((slot, item) in s.slots) assertTrue("$c $slot=$item 沒有顯示", s.shown(item))
        }
    }

    /** 沒有節日：沒有任何節日物件 */
    @Test
    fun noFestivalNoFestivalObjects() {
        for (c in ALL.filter { it.festival == null }) {
            val s = c.resolve()
            assertTrue("$c", s.objects.none { it.item.kind in setOf(Kind.FESTIVAL_ENV, Kind.PROP, Kind.COSTUME) })
        }
    }
}
