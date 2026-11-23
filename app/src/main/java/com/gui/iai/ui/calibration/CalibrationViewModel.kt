package com.gui.iai.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gui.iai.core.iaiFactory
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.Sensitivity
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.game.Feedback
import com.gui.iai.sensors.MotionSample
import com.gui.iai.sensors.ScabbardReading
import com.gui.iai.sensors.ScabbardSensor
import com.gui.iai.sensors.SwingDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SwingCounter(
    val count: Int = 0,
    val lastPeakG: Float? = null
)

/**
 * Tela de teste: mostra ao vivo a força e a rotação do celular
 * e conta os cortes que o detector reconhece.
 * Sensores ligados só enquanto a tela está visível.
 */
class CalibrationViewModel(
    private val settingsRepository: SettingsRepository,
    private val swingDetector: SwingDetector,
    scabbardSensor: ScabbardSensor,
    private val feedback: Feedback
) : ViewModel() {

    val settings: StateFlow<GameSettings> = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(1_000), GameSettings()
    )

    val motion: StateFlow<MotionSample> = swingDetector.motion().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(500), MotionSample()
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val swings: StateFlow<SwingCounter> = settingsRepository.settings
        .map { it.sensitivity }
        .distinctUntilChanged()
        .flatMapLatest { sensitivity -> swingDetector.swings(sensitivity) }
        .onEach { feedback.strike(settings.value) }
        .scan(SwingCounter()) { counter, swing ->
            SwingCounter(count = counter.count + 1, lastPeakG = swing.peakG)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), SwingCounter())

    val hasGyroscope: Boolean get() = swingDetector.hasGyroscope

    val hasScabbard: Boolean = scabbardSensor.isAvailable

    /** Leituras da bainha ao vivo (proximidade + luz). */
    val scabbard: StateFlow<ScabbardReading> = scabbardSensor.readings().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(500), ScabbardReading()
    )

    fun setSensitivity(sensitivity: Sensitivity) {
        viewModelScope.launch { settingsRepository.setSensitivity(sensitivity) }
    }

    companion object {
        val Factory = iaiFactory { container ->
            CalibrationViewModel(
                container.settingsRepository,
                container.swingDetector,
                container.scabbardSensor,
                container.feedback
            )
        }
    }
}
