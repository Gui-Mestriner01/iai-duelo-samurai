package com.gui.iai.sensors

import android.os.SystemClock
import kotlin.math.abs

/**
 * Converte o timestamp dos eventos de sensor para a base de
 * SystemClock.elapsedRealtimeNanos(). Na maioria dos aparelhos já é a mesma base;
 * se não for, calcula a diferença no primeiro evento.
 */
internal class SensorClock {

    private var offsetNanos: Long? = null

    fun toElapsedRealtime(eventTimestampNanos: Long): Long {
        val offset = offsetNanos ?: run {
            val diff = SystemClock.elapsedRealtimeNanos() - eventTimestampNanos
            val computed = if (abs(diff) > MAX_LATENCY_NANOS) diff else 0L
            offsetNanos = computed
            computed
        }
        return eventTimestampNanos + offset
    }

    private companion object {
        const val MAX_LATENCY_NANOS = 500_000_000L
    }
}
