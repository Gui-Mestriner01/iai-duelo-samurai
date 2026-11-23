package com.gui.iai.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gui.iai.core.iaiFactory
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.data.settings.ScabbardSource
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.data.stats.StatsRepository
import com.gui.iai.game.Feedback
import com.gui.iai.game.RoundConfig
import com.gui.iai.game.RoundOutcome
import com.gui.iai.game.RoundPhase
import com.gui.iai.game.RoundRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TrainingStage {
    data object Ready : TrainingStage
    data class InRound(val phase: RoundPhase) : TrainingStage
    data class Result(val outcome: RoundOutcome) : TrainingStage
}

data class TrainingUiState(
    val settings: GameSettings = GameSettings(),
    val stage: TrainingStage = TrainingStage.Ready,
    /** Tempos válidos desta sessão, do mais antigo ao mais recente. */
    val times: List<Long> = emptyList(),
    val fouls: Int = 0,
    val recordMs: Long? = null,
    val activeSignal: SignalMode = SignalMode.VISUAL,
    val scabbardActive: Boolean = false,
    val scabbardTouch: Boolean = false
) {
    val sessionBest: Long? get() = times.minOrNull()
    val sessionAverage: Long? get() = if (times.isEmpty()) null else times.sum() / times.size
}

/** Treino livre contra o makiwara: mede só o tempo de reação. */
class TrainingViewModel(
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val runner: RoundRunner,
    private val feedback: Feedback
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingUiState())
    val state: StateFlow<TrainingUiState> = _state.asStateFlow()

    private var roundJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            statsRepository.stats.collect { stats ->
                _state.update { it.copy(recordMs = stats.bestReactionMs) }
            }
        }
    }

    fun strike() {
        if (roundJob?.isActive == true) return
        val settings = _state.value.settings
        val signal = if (settings.signalMode == SignalMode.CLAP && runner.canUseClap()) SignalMode.CLAP else SignalMode.VISUAL
        val touch = settings.scabbardSource == ScabbardSource.TOUCH
        val scabbard = settings.scabbardMode && (touch || runner.hasScabbardSensor)
        val config = RoundConfig(
            signalMode = signal,
            scabbardMode = scabbard,
            scabbardTouch = touch,
            sensitivity = settings.sensitivity,
            reactionLimitMs = REACTION_LIMIT_MS,
            minWaitMs = 1_800,
            maxWaitMs = 4_800
        )
        _state.update { it.copy(activeSignal = signal, scabbardActive = scabbard, scabbardTouch = touch) }

        roundJob = viewModelScope.launch {
            feedback.roundStart(settings)
            val outcome = runner.run(config) { phase ->
                _state.update { it.copy(stage = TrainingStage.InRound(phase)) }
                when (phase) {
                    RoundPhase.WAITING -> feedback.tension(settings)
                    RoundPhase.SIGNAL -> feedback.signal(settings, signal)
                    RoundPhase.SHEATHING, RoundPhase.PREPARE -> Unit
                }
            }
            feedback.stopAmbient()

            when (outcome) {
                is RoundOutcome.Strike -> {
                    feedback.strike(settings)
                    statsRepository.recordReaction(outcome.reactionMs)
                    statsRepository.recordTrainingStrike()
                    _state.update {
                        it.copy(stage = TrainingStage.Result(outcome), times = it.times + outcome.reactionMs)
                    }
                }

                is RoundOutcome.PlayerFoul -> {
                    feedback.foul(settings)
                    _state.update { it.copy(stage = TrainingStage.Result(outcome), fouls = it.fouls + 1) }
                }

                else -> {
                    feedback.defeat(settings)
                    _state.update { it.copy(stage = TrainingStage.Result(outcome)) }
                }
            }
        }
    }

    fun abort() {
        if (roundJob?.isActive != true) return
        roundJob?.cancel()
        feedback.stopAmbient()
        _state.update { it.copy(stage = TrainingStage.Ready) }
    }

    /** Polegar na tela (bainha por toque). */
    fun setTouchHold(pressed: Boolean) {
        runner.setTouchHold(pressed)
    }

    override fun onCleared() {
        feedback.stopAmbient()
    }

    companion object {
        const val REACTION_LIMIT_MS = 1_500L

        val Factory = iaiFactory { container ->
            TrainingViewModel(
                container.settingsRepository,
                container.statsRepository,
                container.roundRunner,
                container.feedback
            )
        }
    }
}
