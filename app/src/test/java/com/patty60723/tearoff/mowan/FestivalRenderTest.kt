package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalScene.Hand
import com.patty60723.tearoff.mowan.FestivalScene.Item
import com.patty60723.tearoff.mowan.Mowan.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P6-2：節日的靜態畫法。原型 T17（判定顯示 = 實際畫出）、T19（沒有腳時落地）擴充到 8 種天氣與裝備組合，
 * 加上 D-05、D-31、D-16 候選的規則。美術好不好看另外由截圖審查。
 */
class FestivalRenderTest {
    /** 物件 → 畫出來的部位名稱（[Mowan.Op.part]） */
    private val partOf = mapOf(
        Item.SUN to "sun", Item.HEAT to "heat", Item.CLOUDS to "clouds", Item.STORM_CLOUDS to "clouds", Item.RAIN to "rain",
        Item.MIST to "mist", Item.SNOW to "snow", Item.UMBRELLA to "umbrella",
        Item.COUPLET to "couplet", Item.SOCK to "sock", Item.GIFT to "gift", Item.MOON to "moon",
        Item.ENVELOPE to "envelope", Item.MOONCAKE to "mooncake",
        Item.CAISHEN to "caishen", Item.SANTA to "santa", Item.EARS to "ears", Item.PAPER_HAT to "paperHat", Item.BUNDLE to "bundle",
    )

    private fun frame(c: FestivalFixtures.Combo, detail: Detail = Detail.FULL, art: ArtConfig = ArtConfig(festivalHeadwear = c.headwear)) =
        Mowan.frame(c.parts, 0, c.hatOn, c.weather, 0.0, 0, bundleOn = c.bundleOn, detail = detail, festival = c.festival, art = art)

    private fun drawn(f: Mowan.Frame) = f.ops.map { it.part }.toSet()

    /** T17：FestivalScene 判定顯示的東西 = 實際畫出來的東西（節日 × 8 種天氣 × 14 頁 × 紙帽 × 包袱 × 節日頭飾開關） */
    @Test
    fun t17_shownEqualsDrawn() {
        for (c in FestivalFixtures.ALL.filter { it.festival != null }) {
            val s = c.resolve()
            val got = drawn(frame(c))
            for (o in s.objects) {
                val part = partOf.getValue(o.item)
                assertEquals("$c ${o.item}：${o.reason}", o.shown, part in got)
            }
        }
    }

    /** T19：沒有腳（第 1–5 頁）時整個角色落在地面，手上的道具跟著手一起落下 */
    @Test
    fun t19_noLegsStandsOnTheGround() {
        for (c in FestivalFixtures.ALL.filter { it.festival != null && it.page <= 5 && it.hatOn && it.bundleOn && it.headwear }) {
            val s = c.resolve()
            val pose = Poses.festival(s, 0.0).shifted(Mowan.GROUND_DY)
            assertEquals("$c", 127.6, pose.body.last().y, 1e-9)
            val f = frame(c)
            for ((h, item) in listOf(Hand.R to s.hand(Hand.R), Hand.L to s.hand(Hand.L))) {
                if (item == null || item == Item.UMBRELLA) continue
                val end = (if (h == Hand.R) pose.armR else pose.armL).last()
                val box = bounds(f.ops.filter { it.part == partOf[item] })
                assertTrue("$c $item 不在${h}手上：$end vs $box", end.x in box[0]..box[2] && end.y in box[1]..box[3])
            }
        }
    }

    /** 道具一定畫在拿著它的那隻手的末端（所有組合） */
    @Test
    fun propsAreAtTheHoldingHand() {
        for (c in FestivalFixtures.ALL.filter { it.festival != null && it.hatOn && it.headwear }) {
            val s = c.resolve()
            val f = frame(c)
            var pose = Poses.festival(s, 0.0)
            if (!s.caps.stand) pose = pose.shifted(Mowan.GROUND_DY)
            for (h in Hand.entries) {
                val item = s.hand(h) ?: continue
                if (item == Item.UMBRELLA) continue
                val end = (if (h == Hand.R) pose.armR else pose.armL).last()
                val box = bounds(f.ops.filter { it.part == partOf[item] })
                assertTrue("$c $item", end.x in box[0]..box[2] && end.y in box[1]..box[3])
            }
        }
    }

    /** D-31：節日當天沒有汗滴、白氣、雙手握傘、閃電；表情一般、圍巾不拉高；天氣環境照畫 */
    @Test
    fun noWeatherDailyDetailsOnFestivalDays() {
        for (c in FestivalFixtures.ALL.filter { it.festival != null && it.hatOn && it.bundleOn && it.headwear }) {
            val s = c.resolve()
            for (clock in listOf(0.0, 650.0, 1200.0, 2300.0)) {
                val pose = Poses.festival(s, clock)
                assertEquals("$c", Face.NORMAL, pose.face)
                assertEquals("$c", Flick.NORMAL, pose.flick)
                assertEquals("$c", Effects(), pose.effects)
                val got = drawn(Mowan.frame(c.parts, 0, true, c.weather, clock, 0, festival = c.festival))
                for (part in listOf("sweat", "breath", "umbrellaGrip", "bolt")) assertFalse("$c $clock $part", part in got)
            }
        }
        // 沒有節日時，同樣的天氣照樣有這些細節（確認測試有效）
        val hot = Mowan.frame(FestivalFixtures.parts(14), 0, true, Weather.HOT, 1200.0, 0)
        assertTrue("sweat" in drawn(hot))
        val cold = Mowan.frame(FestivalFixtures.parts(14), 0, true, Weather.COLD, 1200.0, 0)
        assertTrue("breath" in drawn(cold))
    }

    /** 雷雨：右手撐傘、左手拿節日道具（春節、中秋），撐傘姿勢跟雨天一樣 */
    @Test
    fun thunderUsesTheRainUmbrellaPose() {
        for (f in Festival.entries) {
            val s = FestivalFixtures.scene(14, f, Weather.THUNDER)
            val pose = Poses.festival(s, 1050.0)
            val rainy = Poses.rainy(1050.0)
            assertEquals("$f", rainy.hand, pose.hand)
            assertEquals("$f", rainy.umbrellaAngle, pose.umbrellaAngle, 0.0)
            assertEquals("$f 撐傘的右手不變", rainy.armR, pose.armR)
        }
    }

    /** D-05：紙帽與節日頭飾不會同時畫出來；節日頭飾關掉時紙帽照裝備狀態畫 */
    @Test
    fun paperHatAndFestivalHeadwear() {
        for (c in FestivalFixtures.ALL.filter { it.festival != null }) {
            val got = drawn(frame(c))
            val costume = partOf.getValue(FestivalScene.spec(c.festival!!).costume)
            assertFalse("$c", "paperHat" in got && costume in got)
            if (!c.headwear) assertEquals("$c", c.parts.contains("paperHat") && c.hatOn, "paperHat" in got)
        }
    }

    /** 頭飾 A／B／C、耳朵 A／B 都畫得出來，而且畫在頭的圖層（跟著頭轉） */
    @Test
    fun headwearCandidates() {
        val parts = FestivalFixtures.parts(14)
        for (f in Festival.entries) for (hat in ArtConfig.HatStyle.entries) for (ears in ArtConfig.EarStyle.entries) {
            val frame = Mowan.frame(parts, 0, true, Weather.NONE, festival = f, art = ArtConfig(hat = hat, ears = ears))
            val costume = partOf.getValue(FestivalScene.spec(f).costume)
            val ops = frame.ops.filter { it.part == costume }
            assertTrue("$f $hat $ears", ops.isNotEmpty())
            assertTrue("$f $hat $ears", ops.all { it.layer == Mowan.Layer.HEAD })
        }
        // 不同戴法真的不一樣
        val a = bounds(Mowan.frame(parts, 0, festival = Festival.SPRING, art = ArtConfig(hat = ArtConfig.HatStyle.A)).ops.filter { it.part == "caishen" })
        val c = bounds(Mowan.frame(parts, 0, festival = Festival.SPRING, art = ArtConfig(hat = ArtConfig.HatStyle.C)).ops.filter { it.part == "caishen" })
        assertFalse(a.contentEquals(c))
    }

    /** D-03 候選：雨傘縮小只改傘的大小 */
    @Test
    fun umbrellaScaleCandidate() {
        val parts = FestivalFixtures.parts(14)
        val big = Mowan.frame(parts, 0, true, Weather.RAINY, 1050.0, festival = Festival.SPRING)
        val small = Mowan.frame(parts, 0, true, Weather.RAINY, 1050.0, festival = Festival.SPRING, art = ArtConfig(umbrellaScale = ArtConfig.UMBRELLA_SMALL))
        assertEquals(1.0, big.canopy!!.scale, 0.0)
        assertEquals(0.95, small.canopy!!.scale, 0.0)
    }

    /** D-16 候選：小尺寸最多一個節日背景；FESTIVAL 時天氣環境讓位，WEATHER 時節日背景讓位；雨傘一定在 */
    @Test
    fun smallSizeBackdropCandidates() {
        val weatherParts = setOf("sun", "heat", "clouds", "rain", "mist", "snow")
        val festParts = setOf("couplet", "sock", "gift", "moon")
        for (c in FestivalFixtures.ALL.filter { it.festival != null && it.hatOn && it.bundleOn && it.headwear }) {
            val s = c.resolve()
            for (env in ArtConfig.SmallEnv.entries) {
                val got = drawn(frame(c, Detail.simplified(144.0), ArtConfig(smallEnv = env)))
                val fest = got intersect festParts
                assertTrue("$c $env 節日背景最多一個：$fest", fest.size <= 1)
                assertEquals("$c $env 雨傘", s.shown(Item.UMBRELLA), "umbrella" in got)
                val keep = Props.SMALL_KEEP.getValue(c.festival!!)
                val keepShown = s.shown(keep)
                when (env) {
                    ArtConfig.SmallEnv.FESTIVAL -> {
                        assertEquals("$c", if (keepShown) setOf(partOf[keep]) else emptySet<String>(), fest)
                        if (keepShown) assertTrue("$c 天氣環境讓位：$got", (got intersect weatherParts).isEmpty())
                    }
                    ArtConfig.SmallEnv.WEATHER -> assertTrue("$c", fest.isEmpty())
                    ArtConfig.SmallEnv.BOTH -> assertEquals("$c", if (keepShown) setOf(partOf[keep]) else emptySet<String>(), fest)
                    ArtConfig.SmallEnv.CUE -> {
                        assertEquals("$c", if (keepShown) setOf(partOf[keep]) else emptySet<String>(), fest)
                        val cues = got intersect weatherParts
                        assertTrue("$c 最多一個天氣線索：$cues", cues.size <= 1)
                        if (keepShown && keep == Item.MOON) assertTrue("$c 月亮與雲、太陽不同時：$cues", "clouds" !in cues && "sun" !in cues)
                        if (keepShown) for (part in listOf("rain", "snow")) {
                            val ops = frame(c, Detail.simplified(144.0), ArtConfig(smallEnv = env)).ops.filter { it.part == part }
                            if (ops.isNotEmpty()) assertTrue("$c $part 只留右邊", ops.size <= 2 && bounds(ops)[0] > 60)
                        }
                    }
                    ArtConfig.SmallEnv.MINIMAL -> {
                        assertTrue("$c 極簡：沒有節日背景", fest.isEmpty())
                        assertTrue("$c 極簡：沒有天氣環境", (got intersect weatherParts).isEmpty())
                    }
                }
            }
        }
    }

    /** P6-4c：小尺寸的春聯往角色靠 [Props.SMALL_COUPLET_DX]（離小工具上的日期數字遠一點），不碰到頭；完整版位置不變 */
    @Test
    fun smallCoupletMovesTowardsTheCharacter() {
        val parts = FestivalFixtures.parts(14)
        fun box(detail: Detail, part: String) =
            bounds(Mowan.frame(parts, 0, true, Weather.SUNNY, 0.0, detail = detail, festival = Festival.SPRING, art = ArtConfig(smallEnv = ArtConfig.SmallEnv.CUE)).ops.filter { it.part == part })
        val full = box(Detail.FULL, "couplet")
        val small = box(Detail.simplified(144.0), "couplet")
        // 簡化版的輪廓加粗到下限，外框差一點點
        assertEquals(Props.SMALL_COUPLET_DX, small[0] - full[0], 0.3)
        assertEquals(Props.SMALL_COUPLET_DX, small[2] - full[2], 0.3)
        assertEquals("高度不變", full[1], small[1], 0.3)
        // 菱形右角（y = 25）跟頭在同一高度的左緣（圓心約 (60, 47)、半徑約 24）至少 10 單位
        val head = box(Detail.simplified(144.0), "head")
        val r = (head[2] - head[0]) / 2
        val cx = (head[0] + head[2]) / 2
        val cy = (head[1] + head[3]) / 2
        val headLeftAt25 = cx - kotlin.math.sqrt(r * r - (25 - cy) * (25 - cy))
        assertTrue("春聯右角 ${small[2]}、頭的左緣 $headLeftAt25", headLeftAt25 - small[2] >= 10)
    }

    /** 沒有節日時畫法完全不變（既有黃金數值另外比對；這裡確認 festival = null 走原本的路） */
    @Test
    fun noFestivalIsUnchanged() {
        val parts = FestivalFixtures.parts(14)
        for (w in Weather.entries) {
            val a = Mowan.frame(parts, 2, true, w, 1300.0, 3)
            val b = Mowan.frame(parts, 2, true, w, 1300.0, 3, festival = null, art = ArtConfig(hat = ArtConfig.HatStyle.A, smallEnv = ArtConfig.SmallEnv.WEATHER))
            assertEquals("$w", a.ops.map { it.part to it.path.commands }, b.ops.map { it.part to it.path.commands })
        }
    }

    /** [minX, minY, maxX, maxY]（只看點，不含弧的外凸，夠用） */
    private fun bounds(ops: List<Mowan.Op>): DoubleArray {
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        for (op in ops) for (c in op.path.commands) {
            val (x, y) = when (c) {
                is PathData.Cmd.MoveTo -> c.x to c.y
                is PathData.Cmd.LineTo -> c.x to c.y
                is PathData.Cmd.QuadTo -> c.x to c.y
                is PathData.Cmd.ArcTo -> c.x to c.y
                PathData.Cmd.Close -> continue
            }
            x0 = minOf(x0, x); y0 = minOf(y0, y); x1 = maxOf(x1, x); y1 = maxOf(y1, y)
        }
        return doubleArrayOf(x0, y0, x1, y1)
    }
}
