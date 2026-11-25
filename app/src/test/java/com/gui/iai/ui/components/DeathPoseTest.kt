package com.gui.iai.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeathPoseTest {

    @Test
    fun deEmPeAntesDoCorte() {
        val pose = deathPose(0f, backward = false)
        assertEquals(0f, pose.angle, 0f)
        assertEquals(0f, pose.kneel, 0f)
        assertEquals(0f, pose.lie, 0f)
    }

    @Test
    fun terminaDeitadoNoChao() {
        val forward = deathPose(1f, backward = false)
        assertEquals(1f, forward.lie, 0.001f)
        assertEquals(90f, forward.angle, 0.5f)
        val back = deathPose(1f, backward = true)
        assertEquals(-88f, back.angle, 0.5f)
    }

    @Test
    fun joelhosCedemAntesDoTombo() {
        val pose = deathPose(0.47f, backward = false)
        assertTrue(pose.kneel > 0.9f)
        assertTrue(pose.lie < 0.05f)
    }

    @Test
    fun bracosSoltamLogoNoTranco() {
        assertTrue(deathPose(0.2f, backward = false).limp > 0.9f)
        assertTrue(deathPose(0.05f, backward = false).recoil > 0.5f)
    }

    @Test
    fun poeiraSoDepoisDeBaterNoChao() {
        assertEquals(0f, deathPose(DEATH_LAND_AT - 0.01f, backward = false).landed, 0f)
        assertTrue(deathPose(DEATH_LAND_AT + 0.05f, backward = false).landed > 0f)
    }

    @Test
    fun notoEmDuasFases() {
        assertTrue(strikePose(1400f).sheathe in 0f..1f)
        assertTrue(strikePose(2000f).sheathe in 1f..2f)
        assertEquals(0f, strikePose(2300f).sheathe, 0f)
    }
}
