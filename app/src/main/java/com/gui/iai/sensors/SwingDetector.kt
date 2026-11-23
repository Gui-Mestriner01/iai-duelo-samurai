package com.gui.iai.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.gui.iai.data.settings.Sensitivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Um "saque" detectado. Tempos na base de SystemClock.elapsedRealtimeNanos().
 */
data class SwingEvent(
    /** Início do movimento (último instante parado). */
    val onsetNanos: Long,
    /** Momento em que o golpe passou do limite. */
    val hitNanos: Long,
    /** Pico de aceleração do golpe, em G. */
    val peakG: Float
)

/** Leitura ao vivo para a tela de teste. */
data class MotionSample(
    /** Aceleração linear (sem gravidade), em G. */
    val accelG: Float = 0f,
    /** Rotação, em graus por segundo. */
    val gyroDps: Float = 0f
)

/**
 * Detecta o golpe de espada com acelerômetro linear + giroscópio.
 *
 * Um corte conta quando, dentro de uma janela curta:
 * - aceleração E rotação passam do limite, ou
 * - só uma delas passa do limite com bastante folga (corte de pulso ou estocada).
 *
 * O tempo registrado é o início do movimento (último instante em que o celular
 * estava parado), e não o momento em que o limite foi atingido.
 */
class SwingDetector(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val hasMotionSensor: Boolean get() = linearSensor != null || accelerometer != null
    val usesLinearAcceleration: Boolean get() = linearSensor != null
    val hasGyroscope: Boolean get() = gyroscope != null

    /** Calcula aceleração linear e rotação a partir dos eventos brutos. */
    private class MotionTracker {
        private val gravity = FloatArray(3)
        private var gravityReady = false
        var accel = 0f
            private set
        var gyro = 0f
            private set

        fun update(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> accel = sqrt(x * x + y * y + z * z)
                Sensor.TYPE_ACCELEROMETER -> {
                    // Sem sensor linear: remove a gravidade com filtro passa-baixa
                    if (!gravityReady) {
                        gravity[0] = x; gravity[1] = y; gravity[2] = z
                        gravityReady = true
                    }
                    gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * x
                    gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * y
                    gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * z
                    val lx = x - gravity[0]
                    val ly = y - gravity[1]
                    val lz = z - gravity[2]
                    accel = sqrt(lx * lx + ly * ly + lz * lz)
                }

                Sensor.TYPE_GYROSCOPE -> gyro = sqrt(x * x + y * y + z * z)
            }
        }

        private companion object {
            const val ALPHA = 0.9f
        }
    }

    fun swings(sensitivity: Sensitivity): Flow<SwingEvent> = callbackFlow {
        val manager = sensorManager
        val motionSensor = linearSensor ?: accelerometer
        if (manager == null || motionSensor == null) {
            close()
            return@callbackFlow
        }

        val thread = HandlerThread("iai-swing").apply { start() }
        val handler = Handler(thread.looper)
        val clock = SensorClock()
        val tracker = MotionTracker()
        val gyro = gyroscope

        val accelLimit = sensitivity.accelThreshold
        val gyroLimit = sensitivity.gyroThreshold
        val accelCalm = accelLimit * CALM_FRACTION
        val gyroCalm = gyroLimit * CALM_FRACTION

        var lastCalmNanos = 0L
        var firstHitNanos = 0L
        var accelHit = false
        var gyroHit = false
        var accelPeak = 0f
        var gyroPeak = 0f
        var lastEmitNanos = 0L
        val warmupUntil = SystemClock.elapsedRealtimeNanos() + WARMUP_NANOS

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ts = clock.toElapsedRealtime(event.timestamp)
                tracker.update(event)
                // Ignora os primeiros instantes: alguns sensores dão um pico ao ligar
                if (ts < warmupUntil) return
                val accel = tracker.accel
                val rotation = tracker.gyro

                // Celular parado: guarda o instante (será o início do próximo golpe)
                val calm = accel < accelCalm && (gyro == null || rotation < gyroCalm)
                if (calm && firstHitNanos == 0L) lastCalmNanos = ts

                // Janela do golpe expirou sem confirmação
                if (firstHitNanos != 0L && ts - firstHitNanos > WINDOW_NANOS) {
                    firstHitNanos = 0L
                    accelHit = false
                    gyroHit = false
                    accelPeak = 0f
                    gyroPeak = 0f
                }

                if (accel >= accelLimit) accelHit = true
                if (gyro != null && rotation >= gyroLimit) gyroHit = true
                if (accelHit || gyroHit) {
                    if (firstHitNanos == 0L) firstHitNanos = ts
                    accelPeak = max(accelPeak, accel)
                    gyroPeak = max(gyroPeak, rotation)
                }

                val confirmed = when {
                    gyro == null -> accelHit
                    accelHit && gyroHit -> true
                    accelPeak >= accelLimit * STRONG_FACTOR -> true
                    gyroPeak >= gyroLimit * STRONG_FACTOR -> true
                    else -> false
                }

                if (confirmed && firstHitNanos != 0L && ts - lastEmitNanos > COOLDOWN_NANOS) {
                    val onset = max(lastCalmNanos, firstHitNanos - MAX_ONSET_NANOS)
                    lastEmitNanos = ts
                    trySend(SwingEvent(onset, firstHitNanos, accelPeak / SensorManager.GRAVITY_EARTH))
                    firstHitNanos = 0L
                    accelHit = false
                    gyroHit = false
                    accelPeak = 0f
                    gyroPeak = 0f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // 200 Hz: maior taxa permitida sem a permissão HIGH_SAMPLING_RATE_SENSORS
        manager.registerListener(listener, motionSensor, SAMPLING_PERIOD_US, handler)
        gyro?.let { manager.registerListener(listener, it, SAMPLING_PERIOD_US, handler) }

        awaitClose {
            manager.unregisterListener(listener)
            thread.quitSafely()
        }
    }

    /** Leituras ao vivo (~30 por segundo) para a tela de teste do golpe. */
    fun motion(): Flow<MotionSample> = callbackFlow {
        val manager = sensorManager
        val motionSensor = linearSensor ?: accelerometer
        if (manager == null || motionSensor == null) {
            close()
            return@callbackFlow
        }

        val thread = HandlerThread("iai-motion").apply { start() }
        val handler = Handler(thread.looper)
        val tracker = MotionTracker()
        var peakAccel = 0f
        var peakGyro = 0f
        var lastSentNanos = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                tracker.update(event)
                peakAccel = max(peakAccel, tracker.accel)
                peakGyro = max(peakGyro, tracker.gyro)
                if (event.timestamp - lastSentNanos >= UI_INTERVAL_NANOS) {
                    lastSentNanos = event.timestamp
                    trySend(
                        MotionSample(
                            accelG = peakAccel / SensorManager.GRAVITY_EARTH,
                            gyroDps = Math.toDegrees(peakGyro.toDouble()).toFloat()
                        )
                    )
                    peakAccel = 0f
                    peakGyro = 0f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        manager.registerListener(listener, motionSensor, SAMPLING_PERIOD_US, handler)
        gyroscope?.let { manager.registerListener(listener, it, SAMPLING_PERIOD_US, handler) }

        awaitClose {
            manager.unregisterListener(listener)
            thread.quitSafely()
        }
    }

    private companion object {
        const val SAMPLING_PERIOD_US = 5_000
        const val WINDOW_NANOS = 220_000_000L
        const val COOLDOWN_NANOS = 600_000_000L
        const val MAX_ONSET_NANOS = 250_000_000L
        const val UI_INTERVAL_NANOS = 33_000_000L
        const val WARMUP_NANOS = 300_000_000L
        const val CALM_FRACTION = 0.3f
        const val STRONG_FACTOR = 1.6f
    }
}
