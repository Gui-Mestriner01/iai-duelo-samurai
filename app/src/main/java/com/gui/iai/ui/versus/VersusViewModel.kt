package com.gui.iai.ui.versus

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

data class TurnResult(
    val player: Int,
    val reactionMs: Long?,
    val foul: Boolean,
    val peakG: Float?
)

sealed interface VersusStage {
    /** "Passe o celular para o Jogador X". */
    data object Handoff : VersusStage
    data class InRound(val phase: RoundPhase) : VersusStage
    data class TurnOver(val result: TurnResult) : VersusStage
    data object Final : VersusStage
}

data class VersusUiState(
    val settings: GameSettings = GameSettings(),
    val turnIndex: Int = 0,
    val stage: VersusStage = VersusStage.Handoff,
    val results: List<TurnResult> = emptyList(),
    val activeSignal: SignalMode = SignalMode.VISUAL,
    val scabbardActive: Boolean = false,
    val scabbardTouch: Boolean = false
) {
    val currentPlayer: Int get() = if (turnIndex % 2 == 0) 1 else 2
    val attemptOfPlayer: Int get() = turnIndex / 2 + 1

    fun bestOf(player: Int): Long? =
        results.filter { it.player == player }.mapNotNull { it.reactionMs }.minOrNull()

    /** 1, 2 ou null (empate / ninguém acertou). */
    val winner: Int?
        get() {
            val p1 = bestOf(1)
            val p2 = bestOf(2)
            return when {
                p1 == null && p2 == null -> null
                p2 == null -> 1
                p1 == null -> 2
                p1 < p2 -> 1
                p2 < p1 -> 2
                else -> null
            }
        }

    companion object {
        const val TURNS_PER_PLAYER = 3
        const val TOTAL_TURNS = TURNS_PER_PLAYER * 2
    }
}

/**
 * Dois jogadores no mesmo celular: cada um tem 3 tentativas, alternando.
 * Vence o melhor tempo de reação.
 */
class VersusViewModel(
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val runner: RoundRunner,
    private val feedback: Feedback
) : ViewModel() {

    private val _state = MutableStateFlow(VersusUiState())
    val state: StateFlow<VersusUiState> = _state.asStateFlow()

    private var roundJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun startTurn() {
        if (roundJob?.isActive == true) return
        val current = _state.value
        if (current.stage !is VersusStage.Handoff) return

        val settings = current.settings
        val signal = if (settings.signalMode == SignalMode.CLAP && runner.canUseClap()) SignalMode.CLAP else SignalMode.VISUAL
        val touch = settings.scabbardSource == ScabbardSource.TOUCH
        val scabbard = settings.scabbardMode && (touch || runner.hasScabbardSensor)
        val config = RoundConfig(
            signalMode = signal,
            scabbardMode = scabbard,
            scabbardTouch = touch,
            sensitivity = settings.sensitivity,
            reactionLimitMs = REACTION_LIMIT_MS
        )
        _state.update { it.copy(activeSignal = signal, scabbardActive = scabbard, scabbardTouch = touch) }

        roundJob = viewModelScope.launch {
            feedback.roundStart(settings)
            val outcome = runner.run(config) { phase ->
                _state.update { it.copy(stage = VersusStage.InRound(phase)) }
                when (phase) {
                    RoundPhase.WAITING -> feedback.tension(settings)
                    RoundPhase.SIGNAL -> feedback.signal(settings, signal)
                    RoundPhase.SHEATHING, RoundPhase.PREPARE -> Unit
                }
            }
            feedback.stopAmbient()

            val player = _state.value.currentPlayer
            val result = when (outcome) {
                is RoundOutcome.Strike -> {
                    feedback.strike(settings)
                    statsRepository.recordReaction(outcome.reactionMs)
                    TurnResult(player, outcome.reactionMs, foul = false, peakG = outcome.peakG)
                }

                is RoundOutcome.PlayerFoul -> {
                    feedback.foul(settings)
                    TurnResult(player, null, foul = true, peakG = null)
                }

                else -> {
                    feedback.defeat(settings)
                    TurnResult(player, null, foul = false, peakG = null)
                }
            }
            _state.update { it.copy(stage = VersusStage.TurnOver(result), results = it.results + result) }
        }
    }

    fun nextTurn() {
        val current = _state.value
        if (current.stage !is VersusStage.TurnOver) return
        if (current.turnIndex + 1 >= VersusUiState.TOTAL_TURNS) {
            if (current.winner != null) feedback.victory(current.settings)
            _state.update { it.copy(stage = VersusStage.Final) }
        } else {
            _state.update { it.copy(turnIndex = it.turnIndex + 1, stage = VersusStage.Handoff) }
        }
    }

    fun restart() {
        roundJob?.cancel()
        feedback.stopAmbient()
        _state.update { VersusUiState(settings = it.settings) }
    }

    fun abortTurn() {
        if (roundJob?.isActive != true) return
        roundJob?.cancel()
        feedback.stopAmbient()
        _state.update { it.copy(stage = VersusStage.Handoff) }
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
            VersusViewModel(
                container.settingsRepository,
                container.statsRepository,
                container.roundRunner,
                container.feedback
            )
        }
    }
}
