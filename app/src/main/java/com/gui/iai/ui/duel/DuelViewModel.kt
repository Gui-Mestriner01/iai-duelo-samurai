package com.gui.iai.ui.duel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gui.iai.core.iaiFactory
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.data.settings.ScabbardSource
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.data.stats.StatsRepository
import com.gui.iai.domain.Opponent
import com.gui.iai.game.Feedback
import com.gui.iai.game.FoulReason
import com.gui.iai.game.RoundConfig
import com.gui.iai.game.RoundOutcome
import com.gui.iai.game.RoundPhase
import com.gui.iai.game.RoundRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Side { PLAYER, OPPONENT }

enum class RoundReason {
    FASTER,
    SLOWER,
    TOO_SLOW,
    PLAYER_EARLY_SWING,
    PLAYER_EARLY_DRAW,
    OPPONENT_EARLY
}

data class RoundReport(
    val winner: Side,
    val reason: RoundReason,
    val playerMs: Long? = null,
    val opponentMs: Long? = null,
    val peakG: Float? = null
)

sealed interface DuelStage {
    /** Apresentação do oponente. */
    data object Intro : DuelStage

    /** Entre golpes (ou golpe interrompido). */
    data object Ready : DuelStage

    data class InRound(val phase: RoundPhase) : DuelStage
    data class RoundOver(val report: RoundReport) : DuelStage
    data class MatchOver(val playerWon: Boolean) : DuelStage
}

data class DuelUiState(
    val opponent: Opponent,
    val settings: GameSettings = GameSettings(),
    val stage: DuelStage = DuelStage.Intro,
    val roundNumber: Int = 1,
    val playerWins: Int = 0,
    val opponentWins: Int = 0,
    val matchBestMs: Long? = null,
    val lastReport: RoundReport? = null,
    val unlocked: Opponent? = null,
    /** Modo de sinal realmente usado (palma sem permissão vira visual). */
    val activeSignal: SignalMode = SignalMode.VISUAL,
    val scabbardActive: Boolean = false,
    val scabbardTouch: Boolean = false
) {
    val isMatchDecided: Boolean get() = playerWins >= WINS_NEEDED || opponentWins >= WINS_NEEDED

    companion object {
        const val WINS_NEEDED = 2
    }
}

/** Duelo contra a CPU: melhor de 3 golpes. */
class DuelViewModel(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val runner: RoundRunner,
    private val feedback: Feedback
) : ViewModel() {

    private val opponent: Opponent =
        Opponent.fromId(savedStateHandle.get<String>(ARG_OPPONENT)) ?: Opponent.TARO

    private val _state = MutableStateFlow(DuelUiState(opponent = opponent))
    val state: StateFlow<DuelUiState> = _state.asStateFlow()

    private var roundJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    fun startRound() {
        if (roundJob?.isActive == true) return
        val current = _state.value
        if (current.isMatchDecided) return

        val settings = current.settings
        val signal = if (settings.signalMode == SignalMode.CLAP && runner.canUseClap()) {
            SignalMode.CLAP
        } else {
            SignalMode.VISUAL
        }
        val touch = settings.scabbardSource == ScabbardSource.TOUCH
        val scabbard = settings.scabbardMode && (touch || runner.hasScabbardSensor)
        val opponentMs = opponent.randomReactionMs()
        val config = RoundConfig(
            signalMode = signal,
            scabbardMode = scabbard,
            scabbardTouch = touch,
            sensitivity = settings.sensitivity,
            reactionLimitMs = opponentMs,
            opponentFoulChance = opponent.foulChance
        )
        _state.update { it.copy(activeSignal = signal, scabbardActive = scabbard, scabbardTouch = touch) }

        roundJob = viewModelScope.launch {
            feedback.roundStart(settings)
            val outcome = runner.run(config) { phase ->
                _state.update { it.copy(stage = DuelStage.InRound(phase)) }
                when (phase) {
                    RoundPhase.WAITING -> feedback.tension(settings)
                    RoundPhase.SIGNAL -> feedback.signal(settings, signal)
                    RoundPhase.SHEATHING, RoundPhase.PREPARE -> Unit
                }
            }
            feedback.stopAmbient()

            val report = when (outcome) {
                is RoundOutcome.Strike ->
                    if (outcome.reactionMs < opponentMs) {
                        RoundReport(Side.PLAYER, RoundReason.FASTER, outcome.reactionMs, opponentMs, outcome.peakG)
                    } else {
                        RoundReport(Side.OPPONENT, RoundReason.SLOWER, outcome.reactionMs, opponentMs, outcome.peakG)
                    }

                RoundOutcome.TooSlow -> RoundReport(Side.OPPONENT, RoundReason.TOO_SLOW, opponentMs = opponentMs)
                is RoundOutcome.PlayerFoul -> RoundReport(
                    Side.OPPONENT,
                    if (outcome.reason == FoulReason.EARLY_DRAW) RoundReason.PLAYER_EARLY_DRAW else RoundReason.PLAYER_EARLY_SWING
                )

                RoundOutcome.OpponentFoul -> RoundReport(Side.PLAYER, RoundReason.OPPONENT_EARLY)
            }

            when (report.reason) {
                RoundReason.PLAYER_EARLY_SWING, RoundReason.PLAYER_EARLY_DRAW -> {
                    feedback.foul(settings)
                    statsRepository.recordFoul()
                }

                else -> feedback.strike(settings)
            }
            if (outcome is RoundOutcome.Strike) statsRepository.recordReaction(outcome.reactionMs)

            _state.update { s ->
                val best = listOfNotNull(s.matchBestMs, (outcome as? RoundOutcome.Strike)?.reactionMs).minOrNull()
                s.copy(
                    stage = DuelStage.RoundOver(report),
                    lastReport = report,
                    playerWins = s.playerWins + if (report.winner == Side.PLAYER) 1 else 0,
                    opponentWins = s.opponentWins + if (report.winner == Side.OPPONENT) 1 else 0,
                    matchBestMs = best
                )
            }
        }
    }

    /** Botão depois do resultado do golpe: próximo golpe ou fim do duelo. */
    fun continueAfterRound() {
        val current = _state.value
        if (current.stage !is DuelStage.RoundOver) return
        if (current.isMatchDecided) {
            val won = current.playerWins >= DuelUiState.WINS_NEEDED
            viewModelScope.launch {
                val defeatedBefore = statsRepository.stats.first().defeatedOpponents
                statsRepository.recordMatch(opponent.id, won)
                val unlocked = if (won && opponent.id !in defeatedBefore) opponent.next else null
                if (won) feedback.victory(current.settings) else feedback.defeat(current.settings)
                _state.update { it.copy(stage = DuelStage.MatchOver(won), unlocked = unlocked) }
            }
        } else {
            _state.update { it.copy(roundNumber = it.roundNumber + 1) }
            startRound()
        }
    }

    fun rematch() {
        roundJob?.cancel()
        feedback.stopAmbient()
        _state.update {
            DuelUiState(opponent = opponent, settings = it.settings, stage = DuelStage.Ready)
        }
    }

    /** Interrompe o golpe atual (app em segundo plano, por exemplo). */
    fun abortRound() {
        if (roundJob?.isActive != true) return
        roundJob?.cancel()
        feedback.stopAmbient()
        _state.update { it.copy(stage = DuelStage.Ready) }
    }

    /** Polegar na tela (bainha por toque). */
    fun setTouchHold(pressed: Boolean) {
        runner.setTouchHold(pressed)
    }

    override fun onCleared() {
        feedback.stopAmbient()
    }

    companion object {
        const val ARG_OPPONENT = "opponentId"

        val Factory = iaiFactory { container ->
            DuelViewModel(
                savedStateHandle = createSavedStateHandle(),
                settingsRepository = container.settingsRepository,
                statsRepository = container.statsRepository,
                runner = container.roundRunner,
                feedback = container.feedback
            )
        }
    }
}
