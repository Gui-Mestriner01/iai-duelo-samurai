package com.gui.iai.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrikePoseTest {

    @Test
    fun guardaAntesEDepoisDoGolpe() {
        assertEquals(0f, strikePose(-1f).draw, 0f)
        assertEquals(0f, strikePose(STRIKE_TOTAL_MS + 10f).draw, 0f)
    }

    @Test
    fun laminaJaEstaForaNoImpacto() {
        // No instante do corte a espada já saiu da bainha (draw > 0.22)
        assertTrue(strikePose(STRIKE_IMPACT_MS).draw > 0.22f)
    }

    @Test
    fun zanshinEPoseEstavel() {
        val pose = strikePose(600f)
        assertEquals(1f, pose.draw, 0.001f)
        assertEquals(1f, pose.arm, 0.001f)
    }

    @Test
    fun antecipacaoRecuaOCorpo() {
        assertTrue(strikePose(70f).lean < 0f)
    }

    @Test
    fun rastroSoAparecePertoDoCorte() {
        assertEquals(0f, strikeTrail(10f), 0f)
        assertEquals(1f, strikeTrail(200f), 0f)
        assertEquals(0f, strikeTrail(800f), 0f)
    }
}
