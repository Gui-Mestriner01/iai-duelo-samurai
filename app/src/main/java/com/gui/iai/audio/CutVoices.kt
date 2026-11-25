package com.gui.iai.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.gui.iai.R
import java.util.Collections

/** Quem foi cortado: cada um tem o seu som. */
enum class CutVoice {
    /** Você: o grito "faaah". */
    PLAYER,

    /** Taro, Ronin, Akane e Gensai. */
    OPPONENT,

    /** Oni de Kurogane: o corte metálico na armadura. */
    ONI
}

/**
 * Sons gravados que tocam quando um samurai é cortado.
 * Os outros sons do jogo continuam sintetizados no [SoundEngine].
 */
class CutVoices(context: Context) {

    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val loaded: MutableSet<Int> = Collections.synchronizedSet(HashSet())
    private val handler = Handler(Looper.getMainLooper())
    private val ids: Map<CutVoice, Int>

    init {
        // O listener vem antes do load para não perder nenhum aviso de "carregado"
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loaded += sampleId
        }
        val appContext = context.applicationContext
        ids = mapOf(
            CutVoice.PLAYER to pool.load(appContext, R.raw.corte_jogador, 1),
            CutVoice.OPPONENT to pool.load(appContext, R.raw.corte_oponente, 1),
            CutVoice.ONI to pool.load(appContext, R.raw.corte_oni, 1)
        )
    }

    /** Toca o som depois de [delayMs], para cair junto com a queda na tela. */
    fun play(voice: CutVoice, delayMs: Long = 0L) {
        val id = ids[voice] ?: return
        handler.postDelayed({
            if (id in loaded) pool.play(id, 1f, 1f, 1, 0, 1f)
        }, delayMs)
    }
}
