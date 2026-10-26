package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.Mowan.Weather
import com.patty60723.tearoff.story.StoryEngine
import com.patty60723.tearoff.story.StoryParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot

/** P5-2：小尺寸的簡化畫法（D-07、D-25、D-26） */
class DetailTest {
    private val beats = File("src/main/assets/story").listFiles { f -> f.name.endsWith(".json") }!!
        .sorted().flatMap { StoryParser.parse(it.readText()) }

    private fun parts(page: Int) = Mowan.partsOf(StoryEngine.unlocked(beats, page))

    private val sizes = listOf(48.0, 72.0, 96.0, 120.0, 160.0)
    private val weathers = Weather.entries

    /** 小工具用的靜止代表姿勢（跟 App 關閉動畫時同一張） */
    private fun still(page: Int, w: Weather, detail: Detail, hatOn: Boolean = true, bundleOn: Boolean = true) =
        Mowan.frame(parts(page), 0, hatOn, w, MowanTimeline.stillClock(w), still = true, bundleOn = bundleOn, detail = detail)

    private fun Mowan.Frame.partSet() = ops.map { it.part }.toSet()
    private fun Mowan.Frame.count(part: String) = ops.count { it.part == part }

    // ---------- 門檻 ----------

    @Test
    fun defaultRuleIsTheTentative120px() {
        for (h in sizes) assertEquals("$h px", h < 120, Detail.of(h, 1.0).simplified)
        assertSame(Detail.FULL, Detail.of(120.0, 3.0))
    }

    /** 同一個視覺大小在不同密度：照 px 判斷會不一樣，照 dp 判斷每種密度一樣 */
    @Test
    fun pxRuleVersusDpRule() {
        val densities = listOf("mdpi" to 1.0, "hdpi" to 1.5, "xhdpi" to 2.0, "xxhdpi" to 3.0)
        // 小工具的角色高度 48–64 dp（寬 36–48 dp × 4/3）、App 日曆頁約 80 dp、故事卡 128 dp
        for (dp in listOf(48.0, 64.0, 80.0, 128.0)) {
            val byPx = densities.map { (_, d) -> Detail.of(dp * d, d, Detail.Rule.ByPx(120.0)).simplified }
            val byDp = densities.map { (_, d) -> Detail.of(dp * d, d, Detail.Rule.ByDp(72.0)).simplified }
            assertEquals("$dp dp 照 dp：每種密度一樣", 1, byDp.toSet().size)
            assertEquals("$dp dp 照 dp", dp < 72, byDp.first())
            when (dp) {
                // 照 px：48 dp 在 xxhdpi 是 144 px → 完整；在 mdpi–xhdpi 是簡化
                48.0 -> assertEquals(listOf(true, true, true, false), byPx)
                64.0 -> assertEquals(listOf(true, true, false, false), byPx)
                80.0 -> assertEquals(listOf(true, false, false, false), byPx)
                128.0 -> assertEquals(listOf(false, false, false, false), byPx)
            }
        }
    }

    @Test
    fun fullDetailIsUnchanged() {
        for (w in weathers) for (page in listOf(1, 5, 11, 14)) {
            val a = Mowan.frame(parts(page), 2, true, w, 1234.0, 3)
            val b = Mowan.frame(parts(page), 2, true, w, 1234.0, 3, detail = Detail.FULL)
            assertEquals("$w 第 $page 頁", a.ops.map { listOf(it.part, it.layer, it.path.commands, it.strokeWidth, it.alpha) },
                b.ops.map { listOf(it.part, it.layer, it.path.commands, it.strokeWidth, it.alpha) })
        }
    }

    // ---------- 留下來的部位 ----------

    private val body = setOf("head", "flick", "eyes", "body", "armL", "armR", "legL", "legR", "redToes", "scarf")
    private val removed = setOf("blush", "sweat", "breath", "ground", "heat", "bolt")

    @Test
    fun keepsTheWholeCharacterAndAccessories() {
        for (h in listOf(48.0, 72.0, 96.0, 119.0)) for (w in weathers) {
            val f = still(14, w, Detail.simplified(h))
            val at = "$w ${h.toInt()} px"
            val got = f.partSet()
            assertTrue("$at 少了 ${body - got}", got.containsAll(body))
            assertTrue("$at 紙帽", "paperHat" in got)
            assertTrue("$at 包袱", "bundle" in got)
            assertTrue("$at 不該有 ${got intersect removed}", (got intersect removed).isEmpty())
            if (w == Weather.RAINY || w == Weather.THUNDER) assertTrue("$at 雨傘", "umbrella" in got)
            if (w == Weather.THUNDER) assertTrue("$at 左手也握傘", "umbrellaGrip" in got)
            // 嘴巴：96 px 以上才畫
            assertEquals("$at 嘴巴", h >= Detail.MOUTH_MIN_PX, "mouth" in got)
        }
    }

    @Test
    fun accessoryDetailsAreDropped() {
        val full = still(14, Weather.RAINY, Detail.FULL)
        val small = still(14, Weather.RAINY, Detail.simplified(48.0))
        // 紙帽：完整版是帽身（填色＋輪廓）＋2 條摺線；簡化只剩帽身
        assertEquals(4, full.count("paperHat"))
        assertEquals(2, small.count("paperHat"))
        // 包袱：背帶、布包、2 條皺褶、2 個布角、結、碎花 → 只剩布包、2 個布角、結（各是填色＋輪廓）
        assertEquals(2 + 4 + 2, small.count("bundle"))
        assertTrue(full.count("bundle") > small.count("bundle"))
        // 圍巾：帶子、尾巴、分岔 → 帶子、尾巴
        assertEquals(3, full.count("scarf"))
        assertEquals(2, small.count("scarf"))
        // 雨傘：傘柄、傘面（填色＋輪廓）、傘骨、拳頭 → 沒有傘骨
        assertEquals(full.count("umbrella") - 1, small.count("umbrella"))
        // 下雨時的眉毛（表情細節）不畫：眼睛只剩兩個點
        assertEquals(2, small.count("eyes"))
    }

    // ---------- 天氣：只留一個環境元素 ----------

    private val env = setOf("sun", "clouds", "rain", "snow", "mist", "ground", "heat", "bolt")

    @Test
    fun oneEnvironmentElementPerWeather() {
        val expected = mapOf(
            Weather.NONE to null, Weather.COLD to null,
            Weather.SUNNY to "sun", Weather.HOT to "sun",
            Weather.CLOUDY to "clouds", Weather.THUNDER to "clouds",
            Weather.RAINY to "rain", Weather.SNOW to "snow", Weather.FOG to "mist",
        )
        for (w in weathers) {
            val f = still(14, w, Detail.simplified(48.0))
            val kinds = f.partSet() intersect env
            assertEquals("$w", setOfNotNull(expected[w]), kinds)
        }
        val small = { w: Weather -> still(14, w, Detail.simplified(48.0)) }
        assertEquals("一朵雲（填色＋輪廓）", 2, small(Weather.CLOUDY).count("clouds"))
        assertEquals("雷雨：一朵烏雲、沒有斜雨", 2, small(Weather.THUNDER).count("clouds"))
        assertEquals("雨：四條雨絲", 4, small(Weather.RAINY).count("rain"))
        assertEquals("雪：四片雪花", 4, small(Weather.SNOW).count("snow"))
        assertEquals("霧：一條霧帶", 1, small(Weather.FOG).count("mist"))
        // 完整版的數量（對照）
        assertTrue(still(14, Weather.RAINY, Detail.FULL).count("rain") > 4)
        assertTrue(still(14, Weather.SNOW, Detail.FULL).count("snow") > 4)
    }

    /** 靜態代表姿勢跟 W5 的 Reduced Motion 一樣：簡化前後頭的位置、傘的位置都一樣 */
    @Test
    fun samePoseAsReducedMotion() {
        for (w in weathers) {
            val full = still(14, w, Detail.FULL)
            val small = still(14, w, Detail.simplified(48.0))
            assertEquals("$w 頭", full.head, small.head)
            assertEquals("$w 傘", full.canopy, small.canopy)
            // 雷雨的靜止畫面沒有閃電、不驚嚇
            assertFalse("$w 閃電", "bolt" in small.partSet())
        }
    }

    // ---------- 成長與裝備 ----------

    @Test
    fun followsTheFourteenPages() {
        for (page in 1..14) for (w in weathers) {
            val full = still(page, w, Detail.FULL).partSet()
            val small = still(page, w, Detail.simplified(48.0)).partSet()
            val at = "第 $page 頁 $w"
            // 不會多出還沒解鎖的部位
            assertTrue("$at 多了 ${small - full}", full.containsAll(small))
            // 已經解鎖的身體部位都在（嘴巴、腮紅在 48 px 不畫）
            val unlocked = parts(page) intersect (body + "paperHat" + "bundle")
            val drawnUnlocked = full intersect unlocked
            assertTrue("$at 少了 ${drawnUnlocked - small}", small.containsAll(drawnUnlocked))
        }
    }

    @Test
    fun hatAndBundleFollowTheEquipmentState() {
        val d = Detail.simplified(48.0)
        for (w in weathers) {
            assertFalse("$w 脫下紙帽", "paperHat" in still(14, w, d, hatOn = false).partSet())
            assertFalse("$w 卸下包袱", "bundle" in still(14, w, d, bundleOn = false).partSet())
            assertTrue("$w 第 11 頁有紙帽", "paperHat" in still(11, w, d).partSet())
            assertFalse("$w 第 10 頁還沒有紙帽", "paperHat" in still(10, w, d).partSet())
            assertFalse("$w 第 13 頁還沒有包袱", "bundle" in still(13, w, d).partSet())
            assertTrue("$w 第 10 頁有圍巾", "scarf" in still(10, w, d).partSet())
            assertFalse("$w 第 9 頁還沒有圍巾", "scarf" in still(9, w, d).partSet())
        }
    }

    // ---------- 筆畫下限 ----------

    /** 一個子路徑的點（Move、Line），遇到弧或 Close 就分段 */
    private fun subpaths(path: PathData): List<List<PathData.Cmd>> {
        val out = mutableListOf<MutableList<PathData.Cmd>>()
        for (c in path.commands) {
            if (c is PathData.Cmd.MoveTo) out += mutableListOf<PathData.Cmd>()
            out.last() += c
        }
        return out
    }

    private fun PathData.Cmd.pt() = when (this) {
        is PathData.Cmd.MoveTo -> Pt(x, y)
        is PathData.Cmd.LineTo -> Pt(x, y)
        else -> null
    }

    /** 一條筆刷外形：左側的點、右側的點（同樣順序），見 [Geometry.outline] */
    private class Brush(val left: List<Pt>, val right: List<Pt>) {
        fun width(k: Int) = hypot(left[k].x - right[k].x, left[k].y - right[k].y)
        fun center(k: Int) = Pt((left[k].x + right[k].x) / 2, (left[k].y + right[k].y) / 2)
    }

    /** 外形是「左側 → 弧 → 右側倒回來 → 弧」 */
    private fun brush(sub: List<PathData.Cmd>): Brush {
        val i = sub.indexOfFirst { it is PathData.Cmd.ArcTo }
        val j = sub.indexOfLast { it is PathData.Cmd.ArcTo }
        val left = sub.subList(0, i).mapNotNull { it.pt() }
        val arc = sub[i] as PathData.Cmd.ArcTo
        val right = (listOf(Pt(arc.x, arc.y)) + sub.subList(i + 1, j).mapNotNull { it.pt() }).asReversed()
        return Brush(left, right)
    }

    /** 一筆畫出來最細的地方（viewBox 單位）：橢圓看直徑、封閉輪廓看外圈到內圈、筆刷看左右兩側 */
    private fun minWidth(path: PathData): Double {
        val subs = subpaths(path)
        val arcs = path.commands.filterIsInstance<PathData.Cmd.ArcTo>()
        if (subs.size == 1 && arcs.size == 2 && subs[0].none { it is PathData.Cmd.LineTo }) return 2 * minOf(arcs[0].rx, arcs[0].ry)
        if (arcs.isEmpty() && subs.size == 2) {
            val outer = subs[0].mapNotNull { it.pt() }
            val inner = subs[1].mapNotNull { it.pt() }.asReversed()
            return outer.indices.minOf { i -> hypot(outer[i].x - inner[i].x, outer[i].y - inner[i].y) }
        }
        return subs.minOf { sub -> brush(sub).let { b -> b.left.indices.minOf { b.width(it) } } }
    }

    private val mainInk = setOf("head", "flick", "body", "armL", "armR", "legL", "legR", "scarf")

    @Test
    fun mainLinesAreAtLeastOneAndAHalfPixels() {
        for (h in listOf(48.0, 72.0, 96.0)) for (w in weathers) {
            val d = Detail.simplified(h)
            val px = Detail.VIEW_H / h // 1 px 是幾個單位
            val f = still(14, w, d)
            for (op in f.ops) {
                val at = "$w ${h.toInt()} px ${op.part}"
                when {
                    // 頭的輪廓是 even-odd 的那一筆；填色那一筆不是線
                    op.part == "head" && !op.evenOdd -> Unit
                    op.part in mainInk && op.fill == Mowan.INK || op.part == "scarf" ->
                        assertTrue("$at 寬 ${minWidth(op.path) / px} px", minWidth(op.path) >= 1.5 * px - 1e-6)
                    op.part == "eyes" -> assertTrue("$at", minWidth(op.path) >= 1.5 * px - 1e-6)
                }
            }
            // 傘柄（填色的筆刷）也是主要墨線
            f.ops.filter { it.part == "umbrella" && it.layer == Mowan.Layer.SHAFT }.forEach {
                assertTrue("$w ${h.toInt()} px 傘柄", minWidth(it.path) >= 1.5 * px - 1e-6)
            }
        }
    }

    @Test
    fun accessoryOutlinesAreAtLeastOnePixelButNotMainWeight() {
        val h = 48.0
        val px = Detail.VIEW_H / h
        for (w in weathers) {
            val f = still(14, w, Detail.simplified(h))
            for (op in f.ops.filter { it.evenOdd && it.part in setOf("paperHat", "bundle", "clouds") }) {
                val width = minWidth(op.path)
                assertTrue("$w ${op.part} 至少 1 px：${width / px}", width >= 1.0 * px - 1e-6)
                assertTrue("$w ${op.part} 不該加粗到主要墨線：${width / px}", width < 1.5 * px)
            }
            // 雨絲、雪花描邊：只到 1 px（裝飾不加粗到主要墨線）
            for (op in f.ops.filter { it.part == "rain" }) assertEquals(1.0 * px, op.strokeWidth, 1e-9)
        }
    }

    @Test
    fun fullVersionKeepsItsThinLines() {
        // 完整版在 160 px 不加粗：一筆的尖端、腳尖照原型那麼細
        val f = still(14, Weather.NONE, Detail.FULL)
        val flick = f.ops.first { it.part == "flick" }
        assertTrue(minWidth(flick.path) < 1.0)
    }

    /** 48 px 時兩條腿的腳尖不會黏在一起：兩個腳尖的邊緣至少相隔 1 px */
    @Test
    fun legsStaySeparateAtFortyEightPixels() {
        val h = 48.0
        val px = Detail.VIEW_H / h
        for (w in weathers) {
            val f = still(14, w, Detail.simplified(h))
            val l = brush(subpaths(f.ops.first { it.part == "legL" }.path)[0])
            val r = brush(subpaths(f.ops.first { it.part == "legR" }.path)[0])
            val a = l.left.size - 1
            val b = r.left.size - 1
            val d = hypot(l.center(a).x - r.center(b).x, l.center(a).y - r.center(b).y)
            val gap = d - l.width(a) / 2 - r.width(b) / 2
            assertTrue("$w 腳尖相隔 ${gap / px} px", gap >= 1.0 * px)
        }
    }
}
