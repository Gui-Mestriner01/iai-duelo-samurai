package com.gui.iai.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Padrões de vibração do jogo. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    fun tick() = oneShot(22, 110)

    fun strike() = oneShot(70, 255)

    fun victory() = waveform(
        timings = longArrayOf(0, 60, 70, 60, 70, 180),
        amplitudes = intArrayOf(0, 255, 0, 200, 0, 255)
    )

    fun defeat() = waveform(
        timings = longArrayOf(0, 380),
        amplitudes = intArrayOf(0, 170)
    )

    fun foul() = waveform(
        timings = longArrayOf(0, 90, 70, 90),
        amplitudes = intArrayOf(0, 220, 0, 220)
    )

    private fun oneShot(millis: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        v.vibrate(VibrationEffect.createOneShot(millis, amp))
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, amplitudes, -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        v.vibrate(effect)
    }
}
