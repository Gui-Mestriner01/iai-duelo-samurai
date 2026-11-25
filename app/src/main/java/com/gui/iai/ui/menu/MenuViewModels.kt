package com.gui.iai.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gui.iai.audio.ClapDetector
import com.gui.iai.core.iaiFactory
import com.gui.iai.data.progress.Progress
import com.gui.iai.data.progress.ProgressRepository
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.ScabbardSource
import com.gui.iai.data.settings.Sensitivity
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.data.stats.PlayerStats
import com.gui.iai.data.stats.StatsRepository
import com.gui.iai.domain.Opponent
import com.gui.iai.domain.ShopItem
import com.gui.iai.sensors.ScabbardSensor
import com.gui.iai.sensors.SwingDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
        Opponent.entries.firstOrNull { it.id !in stats.defeatedOpponents } ?: Opponent.final

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

/** Moedas, loja e conquistas: usado no menu, na Loja, nas Conquistas e no Treino. */
class ProgressViewModel(
    private val progressRepository: ProgressRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    val progress: StateFlow<Progress> = progressRepository.progress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Progress()
    )

    /** Mensagem rápida depois de comprar (ou não conseguir comprar). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun buyOrEquip(item: ShopItem) {
        viewModelScope.launch {
            val current = progressRepository.progress.first()
            if (current.owns(item)) {
                progressRepository.equip(item)
                _message.value = "Equipado: ${item.title}."
                return@launch
            }
            if (progressRepository.buy(item)) {
                progressRepository.equip(item)
                val fresh = progressRepository.checkAchievements(statsRepository.stats.first())
                _message.value = buildString {
                    append("Comprou ${item.title}!")
                    if (fresh.isNotEmpty()) append(" Conquista: ${fresh.joinToString { it.title }}.")
                }
            } else {
                _message.value = "Faltam ${item.price - current.coins} 文 para ${item.title}."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Confere conquistas que dependem só dos registros (ex.: treino). */
    fun refreshAchievements() {
        viewModelScope.launch { progressRepository.checkAchievements(statsRepository.stats.first()) }
    }

    companion object {
        val Factory = iaiFactory { container ->
            ProgressViewModel(container.progressRepository, container.statsRepository)
        }
    }
}
