package com.gui.iai.game

import com.gui.iai.data.settings.Sensitivity
import com.gui.iai.data.settings.SignalMode

/** Etapas de um golpe, mostradas na tela. */
enum class RoundPhase {
    /** Modo bainha: esperando o jogador cobrir o sensor. */
    SHEATHING,

    /** Preparo: movimentos são ignorados até o celular ficar parado. */
    PREPARE,

    /** Silêncio antes do sinal. Mexer agora = precipitado. */
    WAITING,

    /** O sinal foi dado: saque! */
    SIGNAL
}

enum class FoulReason {
    /** Golpeou antes do sinal. */
    EARLY_SWING,

    /** Tirou a espada da bainha antes do sinal. */
    EARLY_DRAW
}

sealed interface RoundOutcome {
    /** Golpe válido depois do sinal. */
    data class Strike(val reactionMs: Long, val peakG: Float) : RoundOutcome

    /** Passou do tempo limite sem golpear. */
    data object TooSlow : RoundOutcome

    data class PlayerFoul(val reason: FoulReason) : RoundOutcome

    /** A CPU se precipitou. */
    data object OpponentFoul : RoundOutcome
}

data class RoundConfig(
    val signalMode: SignalMode,
    val scabbardMode: Boolean,
    /** Bainha pelo dedo na tela (true) ou pelo sensor (false). */
    val scabbardTouch: Boolean = false,
    val sensitivity: Sensitivity,
    /** Tempo máximo depois do sinal (no duelo, é o tempo da CPU). */
    val reactionLimitMs: Long,
    val opponentFoulChance: Float = 0f,
    val minWaitMs: Long = 2_200,
    val maxWaitMs: Long = 5_800
)
