package com.gui.iai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HordeEngineTest {

    @Test
    fun chefeACadaCincoOndas() {
        assertTrue(HordeEngine.isBossWave(5))
        assertTrue(HordeEngine.isBossWave(10))
        assertFalse(HordeEngine.isBossWave(4))
        assertEquals(1, HordeEngine.enemiesInWave(5))
        assertEquals(Opponent.RONIN, HordeEngine.bossFor(5))
        assertEquals(Opponent.KAGE, HordeEngine.bossFor(35))
        assertNotNull(HordeEngine.createEnemy(5, HordeModifier.NONE, 0, Random(1)).boss)
        assertNull(HordeEngine.createEnemy(4, HordeModifier.NONE, 0, Random(1)).boss)
    }

    @Test
    fun inimigosFicamMaisRapidosACadaOnda() {
        val (slowMin, _) = HordeEngine.speedFor(1, HordeModifier.NONE)
        val (fastMin, _) = HordeEngine.speedFor(12, HordeModifier.NONE)
        assertTrue(fastMin < slowMin)
        // Nunca abaixo do limite humano razoável
        assertTrue(HordeEngine.speedFor(99, HordeModifier.RUSH).first >= 250)
    }

    @Test
    fun comboMultiplicaOsPontosAteOLimite() {
        assertEquals(1f, HordeEngine.multiplier(0), 0.001f)
        assertEquals(1.5f, HordeEngine.multiplier(5), 0.001f)
        assertEquals(3f, HordeEngine.multiplier(100), 0.001f)
        val normal = HordeEngine.points(300, 500, combo = 0, perfect = false, boss = false)
        val perfect = HordeEngine.points(300, 500, combo = 0, perfect = true, boss = false)
        assertEquals(300, normal)
        assertEquals(600, perfect)
    }

    @Test
    fun matarAvancaParaOProximoInimigoEOnda() {
        var state = HordeEngine.start(Random(3))
        val first = state.enemiesInWave
        repeat(first) {
            state = HordeEngine.onKill(state, 300, 500, perfect = false, random = Random(it)).state
        }
        assertEquals(2, state.wave)
        assertEquals(0, state.enemyIndex)
        assertEquals(first, state.kills)
        assertEquals(first, state.combo)
    }

    @Test
    fun perderTiraVidaEZeraOCombo() {
        var state = HordeEngine.start(Random(4))
        state = HordeEngine.onKill(state, 300, 500, perfect = false, random = Random(1)).state
        state = HordeEngine.onHit(state).state
        assertEquals(HordeEngine.MAX_LIVES - 1, state.lives)
        assertEquals(0, state.combo)
        repeat(5) { state = HordeEngine.onHit(state).state }
        assertEquals(0, state.lives)
        assertTrue(state.gameOver)
    }

    @Test
    fun iaiPerfeitoRecuperaUmaVida() {
        var state = HordeEngine.onHit(HordeEngine.start(Random(5))).state
        val turn = HordeEngine.onKill(state, 200, 500, perfect = true, random = Random(2))
        assertTrue(turn.healed)
        assertEquals(HordeEngine.MAX_LIVES, turn.state.lives)
        // Com vida cheia não passa do máximo
        state = turn.state
        assertEquals(HordeEngine.MAX_LIVES, HordeEngine.onKill(state, 200, 500, true, Random(3)).state.lives)
    }
}
