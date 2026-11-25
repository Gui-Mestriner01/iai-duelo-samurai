package com.gui.iai.game

import kotlin.random.Random

/** Um aviso enganoso durante a espera: aparece em [startMs] e some em [endMs]. */
data class Cue(val phase: RoundPhase, val startMs: Long, val endMs: Long)

/**
 * Planeja as fintas e o 止 de um golpe, sem sobreposição e sempre antes do sinal.
 * Função pura (só depende do [random]), por isso é fácil de testar.
 *
 * @param waitMs tempo total da espera até o sinal verdadeiro
 */
fun planCues(waitMs: Long, feints: Int, halt: Boolean, random: Random): List<Cue> {
    val wanted = buildList {
        repeat(feints.coerceAtLeast(0)) { add(RoundPhase.FEINT) }
        // O 止 não aparece em todo golpe, senão vira rotina
        if (halt && random.nextFloat() < HALT_CHANCE) add(RoundPhase.HALT)
    }.shuffled(random)
    if (wanted.isEmpty()) return emptyList()

    val cues = mutableListOf<Cue>()
    val durations = wanted.map { if (it == RoundPhase.HALT) HALT_MS else FEINT_MS }
    // Os avisos ficam entre o primeiro segundo e um pouco antes do sinal
    var cursor = FIRST_CUE_MS
    val lastEnd = waitMs - GAP_BEFORE_SIGNAL_MS
    for ((index, phase) in wanted.withIndex()) {
        val duration = durations[index]
        // Reserva espaço para os avisos que ainda vêm depois deste
        val reserved = durations.drop(index + 1).sumOf { it + MIN_GAP_MS }
        val latestStart = lastEnd - duration - reserved
        if (latestStart < cursor) break
        val start = cursor + (random.nextFloat() * (latestStart - cursor) * 0.6f).toLong()
        cues += Cue(phase, start, start + duration)
        cursor = start + duration + MIN_GAP_MS
    }
    return cues
}

/** Espera mínima para caber os avisos planejados. */
fun minimumWaitFor(feints: Int, halt: Boolean): Long =
    FIRST_CUE_MS + GAP_BEFORE_SIGNAL_MS +
        feints.coerceAtLeast(0) * (FEINT_MS + MIN_GAP_MS) +
        (if (halt) HALT_MS + MIN_GAP_MS else 0L)

const val FEINT_MS = 450L
const val HALT_MS = 1_400L
private const val FIRST_CUE_MS = 900L
private const val GAP_BEFORE_SIGNAL_MS = 700L
private const val MIN_GAP_MS = 600L
private const val HALT_CHANCE = 0.65f
