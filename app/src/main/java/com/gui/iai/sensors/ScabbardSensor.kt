package com.gui.iai.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.math.max

/** Estado da bainha + leituras brutas (para a tela de teste). */
data class ScabbardReading(
    /** Espada embainhada (já filtrado contra oscilações). */
    val sheathed: Boolean = false,
    /** Proximidade: true = coberto, null = sem sensor/sem leitura. */
    val proximityNear: Boolean? = null,
    val proximityValue: Float? = null,
    /** Luz ambiente em lux. */
    val lux: Float? = null,
    /** Luz de referência (ambiente sem cobrir). */
    val luxBaseline: Float? = null
)

/**
 * "Bainha": o topo do celular coberto (mão, perna, bolso) = espada embainhada.
 *
 * Usa dois sensores, porque em muitos aparelhos (Samsung, por exemplo) a
 * proximidade fica sob a tela e oscila:
 * - proximidade: perto = coberto;
 * - luz: queda forte em relação à luz ambiente = coberto.
 * Coberto por qualquer um dos dois conta. Mudanças só valem depois de
 * se manterem por um instante (filtro contra piscadas).
 */
class ScabbardSensor(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

    val hasProximity: Boolean get() = proximity != null
    val hasLight: Boolean get() = light != null
    val isAvailable: Boolean get() = hasProximity || hasLight

    /** true = embainhada. */
    fun sheathed(): Flow<Boolean> = readings().map { it.sheathed }.distinctUntilChanged()

    fun readings(): Flow<ScabbardReading> = callbackFlow {
        val manager = sensorManager
        if (manager == null || (proximity == null && light == null)) {
            close()
            return@callbackFlow
        }

        val thread = HandlerThread("iai-scabbard").apply { start() }
        val handler = Handler(thread.looper)

        var proximityNear: Boolean? = null
        var proximityValue: Float? = null
        var lux: Float? = null
        var baseline = 0f
        var lastLuxNanos = 0L
        var stable: Boolean? = null

        fun lightCovered(): Boolean {
            val current = lux ?: return false
            // Em lugar muito escuro a luz não ajuda: depende só da proximidade
            if (baseline < MIN_BASELINE_LUX) return false
            return current < max(COVERED_MIN_LUX, baseline * COVERED_FRACTION)
        }

        fun raw(): Boolean = proximityNear == true || lightCovered()

        fun send() {
            trySend(
                ScabbardReading(
                    sheathed = stable ?: false,
                    proximityNear = proximityNear,
                    proximityValue = proximityValue,
                    lux = lux,
                    luxBaseline = if (light != null) baseline else null
                )
            )
        }

        val commit = Runnable {
            val value = raw()
            if (value != stable) {
                stable = value
                send()
            }
        }

        fun evaluate() {
            val value = raw()
            if (stable == null) {
                // Primeira leitura: vale na hora
                stable = value
            } else if (value != stable) {
                // Cobrir vale rápido; descobrir precisa durar um pouco mais
                handler.removeCallbacks(commit)
                handler.postDelayed(commit, if (value) SHEATH_DEBOUNCE_MS else DRAW_DEBOUNCE_MS)
            } else {
                handler.removeCallbacks(commit)
            }
            send()
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_PROXIMITY -> {
                        val value = event.values[0]
                        proximityValue = value
                        // Sensores binários mandam 0 (perto) ou o alcance máximo (longe)
                        proximityNear = value < minOf(event.sensor.maximumRange, NEAR_CM)
                    }

                    Sensor.TYPE_LIGHT -> {
                        val value = event.values[0]
                        val now = SystemClock.elapsedRealtime()
                        lux = value
                        // Referência: sobe na hora, desce devagar (só quando não está coberto)
                        if (value > baseline) {
                            baseline = value
                        } else if (!lightCovered() && lastLuxNanos != 0L) {
                            val dt = (now - lastLuxNanos).coerceAtMost(1_000L) / 1_000f
                            baseline += (value - baseline) * (dt / BASELINE_DECAY_SECONDS).coerceAtMost(1f)
                        }
                        lastLuxNanos = now
                    }
                }
                evaluate()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        proximity?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        light?.let { manager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }

        awaitClose {
            manager.unregisterListener(listener)
            handler.removeCallbacks(commit)
            thread.quitSafely()
        }
    }

    private companion object {
        const val NEAR_CM = 5f
        const val MIN_BASELINE_LUX = 12f
        const val COVERED_MIN_LUX = 3f
        const val COVERED_FRACTION = 0.15f
        const val BASELINE_DECAY_SECONDS = 4f
        const val SHEATH_DEBOUNCE_MS = 120L
        const val DRAW_DEBOUNCE_MS = 200L
    }
}
