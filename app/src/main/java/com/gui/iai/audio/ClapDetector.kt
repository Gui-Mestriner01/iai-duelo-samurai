package com.gui.iai.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Detecta uma palma (som curto e forte) pelo microfone.
 * Emite o instante da palma na base de SystemClock.elapsedRealtimeNanos().
 *
 * O microfone só fica aberto enquanto o Flow é coletado, e nada é gravado em arquivo.
 */
class ClapDetector(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun claps(): Flow<Long> = callbackFlow {
        if (!hasPermission()) {
            close()
            return@callbackFlow
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            close()
            return@callbackFlow
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer, CHUNK_SAMPLES * 8)
            )
        } catch (error: Exception) {
            null
        }
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release()
            close()
            return@callbackFlow
        }

        val job = launch(Dispatchers.IO) {
            val buffer = ShortArray(CHUNK_SAMPLES)
            var noiseFloor = 400.0
            var lastClapNanos = 0L
            try {
                record.startRecording()
                while (isActive) {
                    val read = record.read(buffer, 0, CHUNK_SAMPLES)
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (read <= 0) continue

                    var peak = 0
                    var peakIndex = 0
                    for (i in 0 until read) {
                        val amplitude = abs(buffer[i].toInt())
                        if (amplitude > peak) {
                            peak = amplitude
                            peakIndex = i
                        }
                    }

                    val isClap = peak >= MIN_PEAK && peak >= noiseFloor * PEAK_TO_NOISE
                    if (isClap && now - lastClapNanos > REARM_NANOS) {
                        lastClapNanos = now
                        val samplesAfterPeak = (read - peakIndex).toLong()
                        trySend(now - samplesAfterPeak * 1_000_000_000L / SAMPLE_RATE)
                    } else {
                        noiseFloor = noiseFloor * 0.92 + peak * 0.08
                    }
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }

        awaitClose { job.cancel() }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHUNK_SAMPLES = 441 // 10 ms
        const val MIN_PEAK = 11_000
        const val PEAK_TO_NOISE = 4.0
        const val REARM_NANOS = 500_000_000L
    }
}
