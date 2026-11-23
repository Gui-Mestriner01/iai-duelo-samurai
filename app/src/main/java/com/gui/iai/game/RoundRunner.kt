package com.gui.iai.game

import android.os.SystemClock
import com.gui.iai.audio.ClapDetector
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.sensors.ScabbardSensor
import com.gui.iai.sensors.SwingDetector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Executa um golpe completo:
 * bainha (opcional) → espera em silêncio → sinal → mede o saque.
 *
 * Todos os tempos usam SystemClock.elapsedRealtimeNanos(), a mesma base
 * dos eventos de sensor, então a reação é medida pelo instante real do movimento.
 */
class RoundRunner(
    private val swingDetector: SwingDetector,
    private val scabbardSensor: ScabbardSensor,
    private val clapDetector: ClapDetector
) {

    val hasScabbardSensor: Boolean get() = scabbardSensor.isAvailable

    /** Bainha por toque: a tela informa se o polegar está pressionado. */
    private val touchHold = MutableStateFlow(false)

    fun setTouchHold(pressed: Boolean) {
        touchHold.value = pressed
    }

    fun canUseClap(): Boolean = clapDetector.hasPermission()

    private sealed interface Input {
        data class Swing(val onsetNanos: Long, val hitNanos: Long, val peakG: Float) : Input
        data class Scabbard(val sheathed: Boolean) : Input
        data class Clap(val timestampNanos: Long) : Input
    }

    suspend fun run(
        config: RoundConfig,
        onPhase: (RoundPhase) -> Unit
    ): RoundOutcome = coroutineScope {
        val inputs = Channel<Input>(Channel.UNLIMITED)
        val sources = buildList<Flow<Input>> {
            add(swingDetector.swings(config.sensitivity).map { Input.Swing(it.onsetNanos, it.hitNanos, it.peakG) })
            if (config.scabbardMode) {
                // A rodada sempre começa depois de um toque já solto
                if (config.scabbardTouch) touchHold.value = false
                val sheathed = if (config.scabbardTouch) touchHold else scabbardSensor.sheathed()
                add(sheathed.map { Input.Scabbard(it) })
            }
            if (config.signalMode == SignalMode.CLAP) add(clapDetector.claps().map { Input.Clap(it) })
        }
        val collector = launch { sources.merge().collect { inputs.trySend(it) } }

        try {
            play(config, inputs, onPhase)
        } finally {
            collector.cancel()
            inputs.close()
        }
    }

    private suspend fun play(
        config: RoundConfig,
        inputs: Channel<Input>,
        onPhase: (RoundPhase) -> Unit
    ): RoundOutcome {
        // 1) Bainha (opcional) e preparo: o celular precisa ficar parado
        //    antes da espera, para o movimento de se posicionar não contar como golpe.
        var sheathed = false
        while (true) {
            if (config.scabbardMode && !sheathed) {
                onPhase(RoundPhase.SHEATHING)
                while (!sheathed) {
                    val input = inputs.receive()
                    if (input is Input.Scabbard) sheathed = input.sheathed
                }
            }

            onPhase(RoundPhase.PREPARE)
            var steadyUntil = now() + PREPARE_MS * MILLIS
            var unsheathed = false
            while (true) {
                val input = receiveUntil(inputs, steadyUntil) ?: break
                when (input) {
                    // Mexeu: reinicia a contagem de "parado"
                    is Input.Swing -> steadyUntil = now() + STEADY_MS * MILLIS
                    is Input.Scabbard -> {
                        sheathed = input.sheathed
                        if (config.scabbardMode && !sheathed) {
                            unsheathed = true
                            break
                        }
                    }
                    is Input.Clap -> Unit
                }
            }
            if (!unsheathed) break
        }

        // 2) Espera em silêncio
        onPhase(RoundPhase.WAITING)
        val waitStart = now()
        val opponentFoulAtNanos: Long? =
            if (Random.nextFloat() < config.opponentFoulChance) {
                waitStart + Random.nextLong(900, 2_000) * MILLIS
            } else {
                null
            }

        val signalAt: Long
        if (config.signalMode == SignalMode.VISUAL) {
            val waitMs = Random.nextLong(config.minWaitMs, config.maxWaitMs + 1)
            val signalDeadline = waitStart + waitMs * MILLIS
            val deadline = opponentFoulAtNanos?.coerceAtMost(signalDeadline) ?: signalDeadline
            while (true) {
                val input = receiveUntil(inputs, deadline) ?: break
                foulFor(input, config)?.let { return RoundOutcome.PlayerFoul(it) }
            }
            if (opponentFoulAtNanos != null && opponentFoulAtNanos <= signalDeadline) {
                return RoundOutcome.OpponentFoul
            }
            signalAt = now()
        } else {
            // Modo palma: o sinal é a palma real
            var clapAt = -1L
            val foulDeadline = opponentFoulAtNanos ?: Long.MAX_VALUE
            while (clapAt < 0) {
                val input = receiveUntil(inputs, foulDeadline) ?: return RoundOutcome.OpponentFoul
                if (input is Input.Clap) {
                    clapAt = input.timestampNanos
                } else {
                    foulFor(input, config)?.let { return RoundOutcome.PlayerFoul(it) }
                }
            }
            signalAt = clapAt
        }

        // 3) Sinal: mede o saque
        onPhase(RoundPhase.SIGNAL)
        val limitNanos = config.reactionLimitMs * MILLIS
        val deadline = signalAt + limitNanos + GRACE_MS * MILLIS
        while (true) {
            val input = receiveUntil(inputs, deadline) ?: break
            if (input is Input.Swing) {
                // Corte confirmado antes do sinal = precipitado
                if (input.hitNanos < signalAt) return RoundOutcome.PlayerFoul(FoulReason.EARLY_SWING)
                // Conta do início do movimento; se ele começou antes do sinal (tremor),
                // conta a partir do corte, sem dar vantagem
                val start = if (input.onsetNanos >= signalAt) input.onsetNanos else input.hitNanos
                val reactionNanos = start - signalAt
                if (reactionNanos > limitNanos) break
                return RoundOutcome.Strike(reactionNanos / MILLIS, input.peakG)
            }
        }
        return RoundOutcome.TooSlow
    }

    private fun foulFor(input: Input, config: RoundConfig): FoulReason? = when {
        input is Input.Swing -> FoulReason.EARLY_SWING
        input is Input.Scabbard && config.scabbardMode && !input.sheathed -> FoulReason.EARLY_DRAW
        else -> null
    }

    private suspend fun receiveUntil(inputs: Channel<Input>, deadlineNanos: Long): Input? {
        if (deadlineNanos == Long.MAX_VALUE) return inputs.receive()
        val remainingMs = (deadlineNanos - now() + MILLIS - 1) / MILLIS
        if (remainingMs <= 0) return null
        return withTimeoutOrNull(remainingMs) { inputs.receive() }
    }

    private fun now(): Long = SystemClock.elapsedRealtimeNanos()

    private companion object {
        const val MILLIS = 1_000_000L
        const val PREPARE_MS = 1_200L
        const val STEADY_MS = 900L
        const val GRACE_MS = 60L
    }
}
