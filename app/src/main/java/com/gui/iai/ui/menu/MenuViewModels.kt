package com.gui.iai.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gui.iai.audio.ClapDetector
import com.gui.iai.core.iaiFactory
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.ScabbardSource
import com.gui.iai.data.settings.Sensitivity
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.data.stats.PlayerStats
import com.gui.iai.data.stats.StatsRepository
import com.gui.iai.domain.Opponent
import com.gui.iai.sensors.ScabbardSensor
import com.gui.iai.sensors.SwingDetector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Registros do jogador: usado na tela inicial, no Caminho e em Registros. */
class StatsViewModel(
    private val statsRepository: StatsRepository
) : ViewModel() {

    val stats: StateFlow<PlayerStats> = statsRepository.stats.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerStats()
    )

    /** Primeiro oponente ainda não derrotado (ou o último, se todos foram). */
    fun nextOpponent(stats: PlayerStats): Opponent =
        Opponent.entries.firstOrNull { it.id !in stats.defeatedOpponents } ?: Opponent.KUROGANE

    fun reset() {
        viewModelScope.launch { statsRepository.reset() }
    }

    companion object {
        val Factory = iaiFactory { container -> StatsViewModel(container.statsRepository) }
    }
}

data class SensorInfo(
    val linearAcceleration: Boolean,
    val gyroscope: Boolean,
    val proximity: Boolean,
    val light: Boolean
) {
    /** A bainha funciona com proximidade OU luz. */
    val scabbard: Boolean get() = proximity || light
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    swingDetector: SwingDetector,
    scabbardSensor: ScabbardSensor,
    private val clapDetector: ClapDetector
) : ViewModel() {

    val settings: StateFlow<GameSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GameSettings()
    )

    val sensors = SensorInfo(
        linearAcceleration = swingDetector.usesLinearAcceleration,
        gyroscope = swingDetector.hasGyroscope,
        proximity = scabbardSensor.hasProximity,
        light = scabbardSensor.hasLight
    )

    fun hasMicPermission(): Boolean = clapDetector.hasPermission()

    fun setSound(enabled: Boolean) = save { settingsRepository.setSound(enabled) }
    fun setVibration(enabled: Boolean) = save { settingsRepository.setVibration(enabled) }
    fun setScabbard(enabled: Boolean) = save { settingsRepository.setScabbardMode(enabled) }
    fun setSignal(mode: SignalMode) = save { settingsRepository.setSignalMode(mode) }
    fun setScabbardSource(source: ScabbardSource) = save { settingsRepository.setScabbardSource(source) }
    fun setSensitivity(sensitivity: Sensitivity) = save { settingsRepository.setSensitivity(sensitivity) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = iaiFactory { container ->
            SettingsViewModel(
                container.settingsRepository,
                container.swingDetector,
                container.scabbardSensor,
                container.clapDetector
            )
        }
    }
}
