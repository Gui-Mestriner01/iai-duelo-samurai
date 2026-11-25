package com.gui.iai.ui.duel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import com.gui.iai.audio.CutVoice
import com.gui.iai.core.iaiFactory
import com.gui.iai.data.progress.ProgressRepository
import com.gui.iai.data.settings.GameSettings
import com.gui.iai.data.settings.ScabbardSource
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.data.stats.StatsRepository
import com.gui.iai.domain.Achievement
import com.gui.iai.domain.BossRule
import com.gui.iai.domain.Loadout
import com.gui.iai.domain.Opponent
import com.gui.iai.domain.Rewards
import com.gui.iai.domain.Story
import com.gui.iai.domain.StoryLine
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
    PLAYER_FEINT,
    PLAYER_HALT,
    OPPONENT_EARLY
}

data class RoundReport(
    val winner: Side,
    val reason: RoundReason,
    val playerMs: Long? = null,
    val opponentMs: Long? = null,
    val peakG: Float? = null,
    /** Iai Perfeito: vale dois golpes. */
    val perfect: Boolean = false,
    val coins: Int = 0
)

sealed interface DuelStage {
    /** Cena da história antes do duelo. */
    data class Story(val index: Int) : DuelStage

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
    val stage: DuelStage = DuelStage.Story(0),
    val roundNumber: Int = 1,
    val playerWins: Int = 0,
    val opponentWins: Int = 0,
    val playerLostRound: Boolean = false,
    val matchBestMs: Long? = null,
    val lastReport: RoundReport? = null,
    val unlocked: Opponent? = null,
    /** Modo de sinal realmente usado (palma sem permissão vira visual). */
    val activeSignal: SignalMode = SignalMode.VISUAL,
    val scabbardActive: Boolean = false,
    val scabbardTouch: Boolean = false,
    /** Regra do Gensai ativa neste golpe (o sinal não aparece na tela). */
    val soundOnly: Boolean = false,
    val loadout: Loadout = Loadout(),
    val coinsEarned: Int = 0,
    val newAchievements: List<Achievement> = emptyList()
) {
    val winsToBeat: Int get() = opponent.winsToBeat
    val isMatchDecided: Boolean
        get() = playerWins >= winsToBeat || opponentWins >= Opponent.OPPONENT_WINS_NEEDED
    val story: List<StoryLine> get() = Story.intro(opponent)
}

/** Duelo contra a CPU: melhor de 3 golpes (o chefe de armadura pede 3 vitórias). */
class DuelViewModel(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val progressRepository: ProgressRepository,
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
        viewModelScope.launch {
            progressRepository.progress.collect { progress ->
                _state.update { it.copy(loadout = progress.loadout) }
            }
        }
    }

    /** Próxima fala da história; depois da última, mostra o oponente. */
    fun advanceStory() {
        val stage = _state.value.stage as? DuelStage.Story ?: return
        val next = stage.index + 1
        _state.update {
            it.copy(stage = if (next < it.story.size) DuelStage.Story(next) else DuelStage.Intro)
        }
    }

    fun skipStory() {
        if (_state.value.stage is DuelStage.Story) _state.update { it.copy(stage = DuelStage.Intro) }
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
        val rules = opponent.rules
        val visual = signal == SignalMode.VISUAL
        val soundOnly = visual && BossRule.SOUND_ONLY in rules
        val quick = BossRule.QUICK_SIGNAL in rules
        val opponentMs = opponent.randomReactionMs()
        val config = RoundConfig(
            signalMode = signal,
            scabbardMode = scabbard,
            scabbardTouch = touch,
            sensitivity = settings.sensitivity,
            reactionLimitMs = opponentMs,
            opponentFoulChance = opponent.foulChance,
            minWaitMs = if (quick) 1_600 else 2_200,
            maxWaitMs = if (quick) 4_200 else 5_800,
            feints = if (visual && BossRule.FEINT in rules) (if (opponent == Opponent.KAGE) 2 else 1) else 0,
            halt = visual && BossRule.HALT in rules,
            signalVisibleMs = when {
                !visual -> null
                BossRule.BLIZZARD in rules -> 420L
                quick -> 330L
                else -> null
            }
        )
        _state.update {
            it.copy(activeSignal = signal, scabbardActive = scabbard, scabbardTouch = touch, soundOnly = soundOnly)
        }

        roundJob = viewModelScope.launch {
            feedback.roundStart(settings)
            val outcome = runner.run(config) { phase ->
                _state.update { it.copy(stage = DuelStage.InRound(phase)) }
                when (phase) {
                    RoundPhase.WAITING -> feedback.tension(settings)
                    RoundPhase.SIGNAL -> feedback.signal(settings, signal, soundOnly)
                    RoundPhase.FEINT -> feedback.feint(settings)
                    RoundPhase.HALT -> feedback.halt(settings)
                    else -> Unit
                }
            }
            feedback.stopAmbient()

            val perfect = outcome is RoundOutcome.Strike &&
                outcome.reactionMs < opponentMs && Rewards.isPerfect(outcome.reactionMs)
            val report = when (outcome) {
                is RoundOutcome.Strike ->
                    if (outcome.reactionMs < opponentMs) {
                        RoundReport(Side.PLAYER, RoundReason.FASTER, outcome.reactionMs, opponentMs, outcome.peakG, perfect)
                    } else {
                        RoundReport(Side.OPPONENT, RoundReason.SLOWER, outcome.reactionMs, opponentMs, outcome.peakG)
                    }

                RoundOutcome.TooSlow -> RoundReport(Side.OPPONENT, RoundReason.TOO_SLOW, opponentMs = opponentMs)
                is RoundOutcome.PlayerFoul -> RoundReport(
                    Side.OPPONENT,
                    when (outcome.reason) {
                        FoulReason.EARLY_DRAW -> RoundReason.PLAYER_EARLY_DRAW
                        FoulReason.EARLY_SWING -> RoundReason.PLAYER_EARLY_SWING
                        FoulReason.FEINT -> RoundReason.PLAYER_FEINT
                        FoulReason.HALT -> RoundReason.PLAYER_HALT
                    }
                )

                RoundOutcome.OpponentFoul -> RoundReport(Side.PLAYER, RoundReason.OPPONENT_EARLY)
            }

            when (report.reason) {
                RoundReason.PLAYER_EARLY_SWING, RoundReason.PLAYER_EARLY_DRAW,
                RoundReason.PLAYER_FEINT, RoundReason.PLAYER_HALT -> {
                    feedback.foul(settings)
                    statsRepository.recordFoul()
                }

                else -> feedback.strike(settings)
            }
            if (perfect) feedback.perfect(settings)
            // Quem perde o golpe cai: grito para você, corte metálico nos de armadura
            val voice = when {
                report.winner == Side.OPPONENT -> CutVoice.PLAYER
                BossRule.ARMOR in rules -> CutVoice.ONI
                else -> CutVoice.OPPONENT
            }
            feedback.cut(settings, voice)
            if (outcome is RoundOutcome.Strike) statsRepository.recordReaction(outcome.reactionMs)
            if (perfect) progressRepository.recordPerfect()
            val tricky = BossRule.FEINT in rules || BossRule.HALT in rules
            if (tricky && report.winner == Side.PLAYER && outcome is RoundOutcome.Strike) {
                progressRepository.recordTrickRoundWon()
            }
            val coins = Rewards.roundCoins(report.winner == Side.PLAYER, perfect)
            progressRepository.addCoins(coins)

            _state.update { s ->
                val best = listOfNotNull(s.matchBestMs, (outcome as? RoundOutcome.Strike)?.reactionMs).minOrNull()
                val gained = if (report.winner == Side.PLAYER) Rewards.winsForRound(perfect) else 0
                s.copy(
                    stage = DuelStage.RoundOver(report.copy(coins = coins)),
                    lastReport = report,
                    playerWins = (s.playerWins + gained).coerceAtMost(s.winsToBeat),
                    opponentWins = s.opponentWins + if (report.winner == Side.OPPONENT) 1 else 0,
                    playerLostRound = s.playerLostRound || report.winner == Side.OPPONENT,
                    matchBestMs = best,
                    coinsEarned = s.coinsEarned + coins
                )
            }
        }
    }

    /** Botão depois do resultado do golpe: próximo golpe ou fim do duelo. */
    fun continueAfterRound() {
        val current = _state.value
        if (current.stage !is DuelStage.RoundOver) return
        if (current.isMatchDecided) {
            val won = current.playerWins >= current.winsToBeat
            viewModelScope.launch {
                val defeatedBefore = statsRepository.stats.first().defeatedOpponents
                statsRepository.recordMatch(opponent.id, won)
                val flawless = won && !current.playerLostRound
                if (flawless) progressRepository.recordFlawlessWin()
                val duelCoins = Rewards.duelCoins(opponent, won, flawless)
                progressRepository.addCoins(duelCoins)
                val fresh = progressRepository.checkAchievements(statsRepository.stats.first())
                val unlocked = if (won && opponent.id !in defeatedBefore) opponent.next else null
                if (won) feedback.victory(current.settings) else feedback.defeat(current.settings)
                _state.update {
                    it.copy(
                        stage = DuelStage.MatchOver(won),
                        unlocked = unlocked,
                        coinsEarned = it.coinsEarned + duelCoins + fresh.sumOf { a -> a.reward },
                        newAchievements = fresh
                    )
                }
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
            DuelUiState(opponent = opponent, settings = it.settings, loadout = it.loadout, stage = DuelStage.Ready)
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
                progressRepository = container.progressRepository,
                runner = container.roundRunner,
                feedback = container.feedback
            )
        }
    }
}
