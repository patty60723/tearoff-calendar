package com.patty60723.tearoff.mowan

import com.patty60723.tearoff.mowan.FestivalActions.Action
import com.patty60723.tearoff.mowan.FestivalActions.Level
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * P6-3：節日演出跟原型比對。黃金數值由 docs/prototypes/tools/export-golden-festival-acts.js 匯出：
 * 368 個姿勢（3 節日 × 4 個成長階段 × 2 種天氣 × 主演出與待機 × 4–10 個時間點，含關閉動畫的代表姿勢）
 * 與 12 張完整畫面。原型沒有包袱，所以比對卸下包袱的畫面；頭飾用原型的 A、耳朵 A。
 */
class FestivalPerformanceGoldenTest {
    private val golden = JSONObject(File("src/test/resources/mowan/golden-festival-acts.json").readText())
    private val tol = 1e-6
    private val festivals = mapOf("spring" to Festival.SPRING, "xmas" to Festival.XMAS, "moon" to Festival.MOON)
    private val levels = mapOf("full" to Level.FULL, "rain" to Level.RAIN, "oneHand" to Level.ONE_HAND, "point" to Level.POINT, "head" to Level.HEAD, "line" to Level.LINE)
    private val art = ArtConfig(hat = ArtConfig.HatStyle.A, ears = ArtConfig.EarStyle.A)

    private fun pts(a: JSONArray) = List(a.length()) { a.getJSONArray(it).let { p -> Pt(p.getDouble(0), p.getDouble(1)) } }

    private fun assertPts(at: String, e: List<Pt>, a: List<Pt>) {
        assertEquals("$at 點數", e.size, a.size)
        e.indices.forEach { i ->
            assertEquals("$at[$i].x", e[i].x, a[i].x, tol)
            assertEquals("$at[$i].y", e[i].y, a[i].y, tol)
        }
    }

    private fun perf(g: JSONObject, f: Festival, scene: FestivalScene.Result): FestivalActions.Performance {
        val spec = FestivalScene.spec(f)
        val action = if (g.getString("kind") == "main") spec.main else spec.idle
        return FestivalActions.Performance(action, FestivalActions.levelFor(action, scene), g.getDouble("t"), g.optBoolean("held"))
    }

    @Test
    fun posesMatchPrototype() {
        val poses = golden.getJSONArray("poses")
        assertEquals(368, poses.length())
        for (i in 0 until poses.length()) {
            val g = poses.getJSONObject(i)
            val f = festivals.getValue(g.getString("holiday"))
            val page = g.getInt("page")
            val weather = Mowan.Weather.valueOf(g.getString("weather").uppercase())
            val scene = FestivalScene.resolve(FestivalFixtures.parts(page), f, weather, true, false, art)
            val p = perf(g, f, scene)
            val at = "$f 第 $page 頁 $weather ${g.getString("kind")} ${g.getDouble("t").toInt()}ms${if (p.held) "（關閉動畫）" else ""}"
            assertEquals("$at 層級", levels.getValue(g.getString("level")), p.level)

            var (pose, fx) = Performances.pose(scene, g.getDouble("t"), p)
            // 原型 buildFrame 在最後做的事：表情要有眼睛、視線要有眼睛、沒有腳落地
            if (pose.face == Face.SUNNY && !scene.caps.expression) pose = pose.copy(face = Face.NORMAL)
            if (!scene.caps.gaze) pose = pose.copy(gaze = Pt(0.0, 0.0))
            if (!scene.caps.stand) pose = pose.shifted(Mowan.GROUND_DY)

            val P = g.getJSONObject("P")
            // D-39（P6-3 動態審核方案 A）：完整版的聖誕襪改成「扶正歪掉的襪子」，扶的那隻手（左手）與襪子角度刻意跟原型不同，
            // 規則在 FestivalPerformanceTest.sock*；其他欄位照樣比對
            val sockA = p.action == Action.SOCK && p.level == Level.FULL
            for ((name, mine) in listOf("body" to pose.body, "armL" to pose.armL, "armR" to pose.armR, "legL" to pose.legL, "legR" to pose.legR, "tail" to pose.tail)) {
                if (sockA && name == "armL") continue
                assertPts("$at $name", pts(P.getJSONArray(name)), mine)
            }
            assertEquals("$at 頭的角度", P.optDouble("headAngle", 0.0), pose.headAngle, tol)
            assertEquals("$at 頭的位移", P.optDouble("headDy", 0.0), pose.headDy, tol)
            assertEquals("$at 脖子", P.optDouble("neckDy", 0.0), pose.neckDy, tol)
            val gz = P.getJSONArray("gaze")
            assertEquals("$at 視線", gz.getDouble(0), pose.gaze.x, tol)
            assertEquals("$at 視線", gz.getDouble(1), pose.gaze.y, tol)
            assertEquals("$at 表情", P.getString("face").uppercase(), pose.face.name)
            assertEquals("$at 一筆", P.getString("flick").uppercase(), pose.flick.name)
            if (P.has("hand")) {
                val h = P.getJSONArray("hand")
                assertNotNull(at, pose.hand)
                assertEquals("$at 握傘點", h.getDouble(0), pose.hand!!.x, tol)
                assertEquals("$at 握傘點", h.getDouble(1), pose.hand!!.y, tol)
                assertEquals("$at 傘的角度", P.getDouble("umbrellaAngle"), pose.umbrellaAngle, tol)
            } else assertNull(at, pose.hand)

            val props = g.getJSONObject("props")
            assertEquals("$at 襪子落下", props.getDouble("sockDy"), fx.sockDy, tol)
            if (!sockA) assertEquals("$at 襪子擺動", props.getDouble("sockSwing"), fx.sockSwing, tol)
            assertEquals("$at 月光", props.getDouble("glow"), fx.glow, tol)
            assertEquals("$at 線索淡入", props.getDouble("cueIn"), fx.cueIn, tol)
        }
    }

    @Test
    fun framesMatchPrototype() {
        val frames = golden.getJSONArray("frames")
        assertEquals(12, frames.length())
        for (i in 0 until frames.length()) {
            val g = frames.getJSONObject(i)
            val f = festivals.getValue(g.getString("holiday"))
            val page = g.getInt("page")
            val weather = Mowan.Weather.valueOf(g.getString("weather").uppercase())
            val scene = FestivalScene.resolve(FestivalFixtures.parts(page), f, weather, true, false, art)
            val p = perf(g, f, scene)
            val label = "$f 第 $page 頁 $weather ${g.getString("kind")} ${g.getDouble("t").toInt()}ms"
            val frame = Mowan.frame(
                FestivalFixtures.parts(page), g.getInt("variant"), true, weather, g.getDouble("t"), g.getInt("rainStep"),
                bundleOn = false, festival = f, art = art, performance = p,
            )
            val gaze = Performances.pose(scene, g.getDouble("t"), p).first.gaze
            val ht = Regex("""translate\(0 (\S+)\) rotate\((\S+) 60\.5 66\)""").find(g.getString("headTransform"))!!
            assertEquals("$label 頭的位移", ht.groupValues[1].toDouble(), frame.head.dy, 0.011)
            assertEquals("$label 頭的角度", ht.groupValues[2].toDouble(), frame.head.rotDeg, 0.011)
            val canopy = g.getString("canopyTransform")
            if (canopy.isEmpty()) assertNull(label, frame.canopy)
            else {
                val m = Regex("""translate\((\S+) (\S+)\) rotate\((\S+)\)""").find(canopy)!!
                assertEquals("$label 傘的角度", m.groupValues[3].toDouble(), frame.canopy!!.rotDeg, 0.011)
            }
            val ops = g.getJSONArray("ops")
            assertEquals("$label 的筆畫數", ops.length(), frame.ops.size)
            // D-39：完整版聖誕襪的襪子與左手刻意跟原型不同（見上面）
            val skip = if (p.action == Action.SOCK && p.level == Level.FULL) setOf("sock", "armL") else emptySet()
            for (k in 0 until ops.length()) {
                val o = ops.getJSONObject(k)
                val op = frame.ops[k]
                if (op.part in skip) continue
                val at = "$label 第 $k 筆（${op.part}）"
                assertEquals(at, o.getString("layer").uppercase(), op.layer.name)
                assertEquals(at, o.optDouble("opacity", 1.0), op.alpha.toDouble(), 1e-6)
                if (o.getString("fill").startsWith("url(")) assertNotNull(at, op.gradient)
                else assertEquals(at, o.getString("fill").uppercase(), op.fill?.let { "#%06X".format(it and 0xFFFFFF) } ?: "NONE")
                var expected = SvgGolden.parse(o.getString("d"))
                // 嘴巴：Android 照 weather.js（Phase 2B 定稿）跟著視線移一半；core.js 的嘴不移。其他筆畫照原型
                if (op.part == "mouth") expected = shift(expected, gaze.x * 0.5, gaze.y * 0.5)
                SvgGolden.assertPathEquals(at, expected, op.path.commands, 0.011)
            }
        }
    }

    private fun shift(cmds: List<PathData.Cmd>, dx: Double, dy: Double) = cmds.map { c ->
        when (c) {
            is PathData.Cmd.MoveTo -> PathData.Cmd.MoveTo(c.x + dx, c.y + dy)
            is PathData.Cmd.LineTo -> PathData.Cmd.LineTo(c.x + dx, c.y + dy)
            is PathData.Cmd.QuadTo -> PathData.Cmd.QuadTo(c.x1 + dx, c.y1 + dy, c.x + dx, c.y + dy)
            is PathData.Cmd.ArcTo -> c.copy(x = c.x + dx, y = c.y + dy)
            PathData.Cmd.Close -> c
        }
    }

    /** 動作的長度與代表姿勢時間跟原型 HOLIDAYS 一樣 */
    @Test
    fun durations() {
        val expect = mapOf(Action.BOW to (4200L to 2300L), Action.LOOK_ENVELOPE to (2200L to 900L), Action.SOCK to (4400L to 2300L),
            Action.PEEK_GIFT to (2000L to 900L), Action.TOAST to (4600L to 2900L), Action.GAZE_MOON to (2600L to 1200L))
        for ((a, d) in expect) { assertEquals("$a", d.first, a.durMs); assertEquals("$a", d.second, a.keyMs) }
    }
}
