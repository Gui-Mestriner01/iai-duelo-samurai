package com.gui.iai.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.EnumMap
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

enum class Sfx { TAIKO, SIGNAL, SLASH, VICTORY, DEFEAT, FOUL, WIND, FEINT, HALT, PERFECT }

/**
 * Sons do jogo sintetizados na hora (os sons de corte gravados ficam em [CutVoices]):
 * tambor taiko, batida de madeira (hyoshigi), corte da lâmina, sino e vento.
 */
class SoundEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracks = EnumMap<Sfx, AudioTrack>(Sfx::class.java)
    private val lengths = EnumMap<Sfx, Int>(Sfx::class.java)
    private val lock = Any()
    private var preparing = false

    fun prepare() {
        synchronized(lock) {
            if (preparing) return
            preparing = true
        }
        scope.launch {
            Sfx.entries.forEach { sfx ->
                val pcm = synthesize(sfx)
                val track = runCatching { buildTrack(pcm) }.getOrNull() ?: return@forEach
                synchronized(lock) {
                    tracks[sfx] = track
                    lengths[sfx] = pcm.size
                }
            }
        }
    }

    fun play(sfx: Sfx) {
        val track = synchronized(lock) { tracks[sfx] } ?: return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun startWind() {
        val (track, frames) = synchronized(lock) { tracks[Sfx.WIND] to lengths[Sfx.WIND] }
        if (track == null || frames == null) return
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.setLoopPoints(0, frames, -1)
            track.play()
        }
    }

    fun stopWind() {
        val track = synchronized(lock) { tracks[Sfx.WIND] } ?: return
        runCatching { track.stop() }
    }

    private fun buildTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    // ---------- Síntese ----------

    private fun synthesize(sfx: Sfx): ShortArray = when (sfx) {
        Sfx.TAIKO -> taiko()
        Sfx.SIGNAL -> hyoshigi()
        Sfx.SLASH -> slash()
        Sfx.VICTORY -> bell()
        Sfx.DEFEAT -> defeat()
        Sfx.FOUL -> foul()
        Sfx.WIND -> wind()
        Sfx.FEINT -> feint()
        Sfx.HALT -> gong()
        Sfx.PERFECT -> chime()
    }

    /** Batida abafada, parecida com o sinal mas grave (a finta). */
    private fun feint(): ShortArray {
        val noise = Random(5)
        val n = samples(0.25)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val env = exp(-t * 28)
            val tone = sin(2 * PI * 520 * t) + 0.4 * sin(2 * PI * 1040 * t)
            val click = (noise.nextDouble() * 2 - 1) * exp(-t * 120) * 0.4
            out[i] = (tone * env * 0.5 + click).toFloat()
        }
        return toPcm(out, 0.7f)
    }

    /** Gongo grave: o 止 apareceu. */
    private fun gong(): ShortArray {
        val n = samples(1.4)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val wobble = 1 + 0.004 * sin(2 * PI * 5 * t)
            val s = sin(2 * PI * 98 * wobble * t) + 0.6 * sin(2 * PI * 161 * t) * exp(-t * 2) +
                0.3 * sin(2 * PI * 263 * t) * exp(-t * 3.5)
            val attack = if (t < 0.01) t / 0.01 else 1.0
            out[i] = (s * exp(-t * 1.6) * attack * 0.55).toFloat()
        }
        return toPcm(out, 0.9f)
    }

    /** Brilho de sininhos subindo: Iai Perfeito. */
    private fun chime(): ShortArray {
        val n = samples(1.1)
        val out = FloatArray(n)
        val notes = doubleArrayOf(880.0, 1174.7, 1568.0, 2093.0)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            var s = 0.0
            for (k in notes.indices) {
                val start = k * 0.07
                if (t >= start) {
                    val tt = t - start
                    s += sin(2 * PI * notes[k] * tt) * exp(-tt * 4.5) * 0.4
                }
            }
            out[i] = s.toFloat()
        }
        return toPcm(out, 0.9f)
    }

    /** Tambor grave com o pitch caindo. */
    private fun taiko(): ShortArray {
        val noise = Random(1)
        val n = samples(0.9)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val freq = 52 + 70 * exp(-t * 16)
            phase += 2 * PI * freq / SAMPLE_RATE
            val body = sin(phase) * exp(-t * 5.0)
            val hit = (noise.nextDouble() * 2 - 1) * exp(-t * 70) * 0.35
            out[i] = (body + hit).toFloat()
        }
        return toPcm(out, 0.95f)
    }

    /** Duas tábuas de madeira batendo (o sinal do saque). */
    private fun hyoshigi(): ShortArray {
        val noise = Random(2)
        val n = samples(0.3)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val env = exp(-t * 34)
            val tone = sin(2 * PI * 1180 * t) + 0.55 * sin(2 * PI * 2350 * t) + 0.3 * sin(2 * PI * 3870 * t)
            val click = (noise.nextDouble() * 2 - 1) * exp(-t * 160) * 0.8
            out[i] = (tone * env * 0.6 + click).toFloat()
        }
        return toPcm(out, 0.9f)
    }

    /** Ruído "chhh" da lâmina + um brilho metálico. */
    private fun slash(): ShortArray {
        val noise = Random(3)
        val n = samples(0.75)
        val out = FloatArray(n)
        var previous = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val white = noise.nextDouble() * 2 - 1
            val bright = white - previous * 0.85 // passa-alta simples
            previous = white
            val swoosh = if (t < 0.025) t / 0.025 else exp(-(t - 0.025) * 11)
            val ring = (sin(2 * PI * 3150 * t) + 0.7 * sin(2 * PI * 4720 * t) + 0.4 * sin(2 * PI * 6230 * t)) *
                exp(-t * 7) * 0.22
            out[i] = (bright * swoosh * 0.55 + ring).toFloat()
        }
        return toPcm(out, 0.85f)
    }

    /** Sino de templo (vitória). */
    private fun bell(): ShortArray {
        val n = samples(2.6)
        val out = FloatArray(n)
        val partials = doubleArrayOf(1.0, 2.0, 2.76, 5.4, 8.93)
        val amps = doubleArrayOf(1.0, 0.55, 0.45, 0.22, 0.12)
        val decays = doubleArrayOf(1.4, 2.0, 2.6, 4.0, 6.0)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            var s = 0.0
            for (k in partials.indices) {
                s += amps[k] * sin(2 * PI * 196 * partials[k] * t) * exp(-t * decays[k])
            }
            val attack = if (t < 0.004) t / 0.004 else 1.0
            out[i] = (s * attack * 0.45).toFloat()
        }
        return toPcm(out, 0.9f)
    }

    /** Tom grave descendo (derrota). */
    private fun defeat(): ShortArray {
        val n = samples(1.5)
        val out = FloatArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val freq = 170 - 80 * (t / 1.5)
            phase += 2 * PI * freq / SAMPLE_RATE
            val tone = (sin(phase) + 0.35 * sin(phase * 2) + 0.15 * sin(phase * 3)) * exp(-t * 2.2)
            out[i] = (tone * 0.6).toFloat()
        }
        return toPcm(out, 0.9f)
    }

    /** Duas batidas secas (precipitado). */
    private fun foul(): ShortArray {
        val n = samples(0.4)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val first = sin(2 * PI * 420 * t) * exp(-t * 40)
            val t2 = t - 0.14
            val second = if (t2 > 0) sin(2 * PI * 300 * t2) * exp(-t2 * 30) else 0.0
            out[i] = ((first + second) * 0.7).toFloat()
        }
        return toPcm(out, 0.9f)
    }

    /** Vento suave em loop (tensão antes do sinal). */
    private fun wind(): ShortArray {
        val noise = Random(4)
        val n = samples(4.0)
        val out = FloatArray(n)
        var low = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            low += 0.025 * ((noise.nextDouble() * 2 - 1) - low)
            val gust = 0.55 + 0.45 * sin(2 * PI * 0.25 * t - PI / 2)
            out[i] = (low * gust * 3.2).toFloat()
        }
        // Suaviza as pontas para o loop não estalar
        val fade = samples(0.05)
        for (i in 0 until fade) {
            val g = i / fade.toFloat()
            out[i] *= g
            out[n - 1 - i] *= g
        }
        return toPcm(out, 0.35f)
    }

    private fun samples(seconds: Double): Int = (SAMPLE_RATE * seconds).toInt()

    private fun toPcm(data: FloatArray, gain: Float): ShortArray =
        ShortArray(data.size) { i ->
            (tanh(data[i] * gain.toDouble()) * Short.MAX_VALUE * 0.92).toInt().toShort()
        }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
