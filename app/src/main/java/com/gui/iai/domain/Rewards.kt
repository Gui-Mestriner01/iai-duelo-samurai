package com.gui.iai.domain

/** Moedas (mon, 文) ganhas jogando. Regras puras, sem Android. */
object Rewards {
    /** Abaixo disso o corte é um Iai Perfeito. */
    const val PERFECT_MS = 260L

    fun isPerfect(reactionMs: Long?): Boolean = reactionMs != null && reactionMs <= PERFECT_MS

    /** No duelo, um Iai Perfeito vale dois golpes. */
    fun winsForRound(perfect: Boolean): Int = if (perfect) 2 else 1

    fun roundCoins(won: Boolean, perfect: Boolean): Int =
        (if (won) 5 else 0) + (if (perfect) 10 else 0)

    fun duelCoins(opponent: Opponent, won: Boolean, flawless: Boolean): Int = when {
        !won -> 3
        else -> 20 + opponent.ordinal * 10 + (if (flawless) 15 else 0)
    }

    fun trainingCoins(perfect: Boolean): Int = if (perfect) 2 else 1
}
