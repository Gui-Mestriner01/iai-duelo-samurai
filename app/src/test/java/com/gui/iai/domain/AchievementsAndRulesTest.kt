package com.gui.iai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsAndRulesTest {

    @Test
    fun conquistasSaemSoUmaVez() {
        val snapshot = ProgressSnapshot(wins = 1, perfects = 1, bestReactionMs = 210)
        val fresh = Achievement.newlyUnlocked(snapshot, already = emptySet())
        assertTrue(Achievement.FIRST_BLOOD in fresh)
        assertTrue(Achievement.PERFECT in fresh)
        assertTrue(Achievement.LIGHTNING in fresh)
        val again = Achievement.newlyUnlocked(snapshot, already = fresh.map { it.id }.toSet())
        assertTrue(again.isEmpty())
    }

    @Test
    fun lendaPedeDerrotarKage() {
        assertFalse(Achievement.LEGEND.isMet(ProgressSnapshot(defeated = setOf(Opponent.KUROGANE.id))))
        assertTrue(Achievement.LEGEND.isMet(ProgressSnapshot(defeated = setOf(Opponent.KAGE.id))))
    }

    @Test
    fun iaiPerfeitoValeDoisGolpes() {
        assertTrue(Rewards.isPerfect(Rewards.PERFECT_MS))
        assertFalse(Rewards.isPerfect(Rewards.PERFECT_MS + 1))
        assertFalse(Rewards.isPerfect(null))
        assertEquals(2, Rewards.winsForRound(perfect = true))
        assertEquals(1, Rewards.winsForRound(perfect = false))
    }

    @Test
    fun armaduraPedeTresVitorias() {
        assertEquals(3, Opponent.KUROGANE.winsToBeat)
        assertEquals(3, Opponent.KAGE.winsToBeat)
        assertEquals(2, Opponent.TARO.winsToBeat)
    }

    @Test
    fun caminhoDesbloqueiaEmOrdem() {
        assertTrue(Opponent.TARO.isUnlocked(emptySet()))
        assertFalse(Opponent.RONIN.isUnlocked(emptySet()))
        assertTrue(Opponent.RONIN.isUnlocked(setOf(Opponent.TARO.id)))
        assertEquals(Opponent.KAGE, Opponent.final)
    }

    @Test
    fun todoOponenteTemHistoria() {
        Opponent.entries.forEach { opponent ->
            assertTrue(Story.intro(opponent).isNotEmpty())
            assertTrue(Story.epilogue(opponent).isNotBlank())
        }
    }
}
