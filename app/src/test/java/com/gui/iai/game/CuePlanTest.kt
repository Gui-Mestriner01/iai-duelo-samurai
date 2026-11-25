package com.gui.iai.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CuePlanTest {

    @Test
    fun semFintasNemPareNaoHaAvisos() {
        assertTrue(planCues(5_000, feints = 0, halt = false, random = Random(1)).isEmpty())
    }

    @Test
    fun avisosFicamDentroDaEsperaSemSobreposicao() {
        repeat(200) { seed ->
            val wait = minimumWaitFor(feints = 2, halt = true) + 1_500
            val cues = planCues(wait, feints = 2, halt = true, random = Random(seed))
            var lastEnd = 0L
            cues.forEach { cue ->
                assertTrue("começa depois do anterior", cue.startMs >= lastEnd)
                assertTrue("termina antes do sinal", cue.endMs < wait)
                assertTrue(cue.endMs > cue.startMs)
                lastEnd = cue.endMs
            }
        }
    }

    @Test
    fun duracaoDeCadaTipoDeAviso() {
        repeat(100) { seed ->
            planCues(minimumWaitFor(1, true) + 2_000, feints = 1, halt = true, random = Random(seed)).forEach { cue ->
                val expected = if (cue.phase == RoundPhase.HALT) HALT_MS else FEINT_MS
                assertEquals(expected, cue.endMs - cue.startMs)
            }
        }
    }

    @Test
    fun esperaMinimaCabeTodasAsFintas() {
        repeat(100) { seed ->
            val cues = planCues(minimumWaitFor(feints = 2, halt = false), feints = 2, halt = false, random = Random(seed))
            assertEquals(2, cues.count { it.phase == RoundPhase.FEINT })
        }
    }
}
