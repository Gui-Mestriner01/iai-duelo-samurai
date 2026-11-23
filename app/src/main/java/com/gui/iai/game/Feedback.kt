package com.gui.iai.game

import com.gui.iai.audio.Sfx
import com.gui.iai.audio.SoundEngine
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.haptics.Haptics

/** Som + vibração, sempre respeitando os ajustes do jogador. */
class Feedback(
    private val sound: SoundEngine,
    private val haptics: Haptics
) {

    fun roundStart(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.TAIKO)
        if (settings.vibrationEnabled) haptics.tick()
    }

    fun tension(settings: GameSettings) {
        if (settings.soundEnabled) sound.startWind()
    }

    fun signal(settings: GameSettings, mode: SignalMode) {
        sound.stopWind()
        // No modo palma o sinal já é o som real
        if (settings.soundEnabled && mode == SignalMode.VISUAL) sound.play(Sfx.SIGNAL)
    }

    fun strike(settings: GameSettings) {
        if (settings.soundEnabled) sound.play(Sfx.SLASH)
        if (settings.vibrationEnabled) haptics.strike()
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
}
