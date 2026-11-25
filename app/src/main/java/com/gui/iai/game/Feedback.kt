package com.gui.iai.game

import com.gui.iai.audio.CutVoice
import com.gui.iai.audio.CutVoices
import com.gui.iai.audio.Sfx
import com.gui.iai.audio.SoundEngine
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.haptics.Haptics

/** Som + vibração, sempre respeitando os ajustes do jogador. */
class Feedback(
    private val sound: SoundEngine,
    private val haptics: Haptics,
    private val voices: CutVoices
) {

    fun roundStart(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.TAIKO)
        if (settings.vibrationEnabled) haptics.tick()
    }

    fun tension(settings: GameSettings) {
        if (settings.soundEnabled) sound.startWind()
    }

    /**
     * @param soundOnly regra do Mestre Gensai: o sinal é só som, então ele
     * toca mesmo com o som desligado e ainda vibra.
     */
    fun signal(settings: GameSettings, mode: SignalMode, soundOnly: Boolean = false) {
        sound.stopWind()
        // No modo palma o sinal já é o som real
        if (mode == SignalMode.VISUAL && (settings.soundEnabled || soundOnly)) sound.play(Sfx.SIGNAL)
        if (soundOnly) haptics.tick()
    }

    /** Sinal falso: um som parecido, mas mais grave, para confundir. */
    fun feint(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.FEINT)
    }

    /** 止 apareceu: gongo. */
    fun halt(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.HALT)
    }

    /** Iai Perfeito. */
    fun perfect(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.PERFECT)
        if (settings.vibrationEnabled) haptics.victory()
    }

    fun strike(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.SLASH)
        if (settings.vibrationEnabled) haptics.strike()
    }

    /** Som de quem foi cortado, sincronizado com a queda na tela. */
    fun cut(settings: GameSettings, voice: CutVoice) {
        if (settings.soundEnabled) voices.play(voice, delayMs = CUT_VOICE_DELAY_MS)
    }

    fun foul(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.FOUL)
        if (settings.vibrationEnabled) haptics.foul()
    }

    fun victory(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.VICTORY)
        if (settings.vibrationEnabled) haptics.victory()
    }

    fun defeat(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.DEFEAT)
        if (settings.vibrationEnabled) haptics.defeat()
    }

    fun stopAmbient() {
        sound.stopWind()
    }

    private companion object {
        /** A reação de quem foi cortado entra quando a lâmina acerta, depois do hit-stop. */
        const val CUT_VOICE_DELAY_MS = 265L
    }
}
