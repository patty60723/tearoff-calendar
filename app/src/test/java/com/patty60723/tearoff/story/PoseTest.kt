package com.patty60723.tearoff.story

import com.patty60723.tearoff.stickman.Actions
import com.patty60723.tearoff.stickman.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseTest {
    @Test
    fun actionsLoop() {
        for (action in Actions.all.values) {
            val start = action.at(0f)
            val end = action.at(1f)
            assertEquals(action.id, start.x(Pose.HEAD), end.x(Pose.HEAD), 0.001f)
            assertEquals(action.id, start.y(Pose.FOOT_L), end.y(Pose.FOOT_L), 0.001f)
        }
    }

    @Test
    fun staysInsideTheBox() {
        for (action in Actions.all.values) {
            for (i in 0 until 24) {
                val pose = action.at(i / 24f)
                for (joint in 0..10) {
                    assertTrue("${action.id} x", pose.x(joint) in 4f..44f)
                    assertTrue("${action.id} y", pose.y(joint) in 4f..Pose.GROUND)
                }
                // 頭加紙帽不能超出畫布上緣（renderer 往上多留了 8）
                assertTrue("${action.id} head", pose.y(Pose.HEAD) - 15 >= -8)
            }
        }
    }
}
