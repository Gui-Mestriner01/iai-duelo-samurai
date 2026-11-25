package com.gui.iai.ui.horde

import androidx.lifecycle.ViewModel
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
import com.gui.iai.domain.Arena
import com.gui.iai.domain.BossRule
import com.gui.iai.domain.HordeEnemy
import com.gui.iai.domain.HordeEngine
import com.gui.iai.domain.HordeModifier
import com.gui.iai.domain.HordeState
import com.gui.iai.domain.Loadout
import com.gui.iai.domain.Rewards
import com.gui.iai.game.Feedback
import com.gui.iai.game.FoulReason
import com.gui.iai.game.RoundConfig
import com.gui.iai.game.RoundOutcome
import com.gui.iai.game.RoundPhase
import com.gui.iai.game.RoundRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Como terminou um golpe nas Hordas. */
enum class HordeOutcome {
    KILL,
    ARMOR_CRACKED,
    ENEMY_FOUL,
    TOO_SLOW,
    SLOWER,
    EARLY,
    FEINT,
    HALT
}

data class HordeRoundResult(
    val outcome: HordeOutcome,
    val playerMs: Long? = null,
    val enemyMs: Long? = null,
    val points: Int = 0,
    val perfect: Boolean = false,
    val healed: Boolean = false
) {
    val playerWon: Boolean
        get() = outcome == HordeOutcome.KILL || outcome == HordeOutcome.ARMOR_CRACKED || outcome == HordeOutcome.ENEMY_FOUL
}

sealed interface HordeStage {
    /** Tela inicial do modo. */
    data object Lobby : HordeStage

    /** Faixa "Onda N" antes de começar a onda. */
    data class WaveBanner(val wave: Int) : HordeStage

    /** Pausado (app em segundo plano). */
    data object Paused : HordeStage

    data class InRound(val phase: RoundPhase) : HordeStage
    data class RoundOver(val result: HordeRoundResult) : HordeStage
    data object GameOver : HordeStage
}

data class HordeUiState(
    val stage: HordeStage = HordeStage.Lobby,
    val game: HordeState? = null,
    /** Inimigo mostrado na cena (o derrotado fica até cair). */
    val shownEnemy: HordeEnemy? = null,
    val armorCracked: Boolean = false,
    val settings: GameSettings = GameSettings(),
    val loadout: Loadout = Loadout(),
    val bestScore: Int = 0,
    val bestWave: Int = 0,
    val activeSignal: SignalMode = SignalMode.VISUAL,
    val scabbardActive: Boolean = false,
    val scabbardTouch: Boolean = false,
    val soundOnly: Boolean = false,
    val blizzard: Boolean = false,
    val coinsEarned: Int = 0,
    val newRecord: Boolean = false,
    val newAchievements: List<Achievement> = emptyList()
) {
    val arena: Arena
        get() {
            val game = game ?: return Arena.DUSK
            shownEnemy?.boss?.let { return it.arena }
            return when (game.modifier) {
                HordeModifier.NIGHT -> Arena.NIGHT
                HordeModifier.SNOWSTORM -> Arena.SNOW
                else -> Arena.DUSK
            }
        }
}

/**
 * Hordas: inimigos sem fim, cada onda mais rápida. 3 vidas, combo multiplicando
 * os pontos, Iai Perfeito recupera uma vida e a cada 5 ondas vem um chefe do Caminho.
 */
class HordeViewModel(
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val progressRepository: ProgressRepository,
    private val runner: RoundRunner,
    private val feedback: Feedback
) : ViewModel() {

    private val _state = MutableStateFlow(HordeUiState())
    val state: StateFlow<HordeUiState> = _state.asStateFlow()

    private var roundJob: Job? = null
    private var flowJob: Job? = null
    private val random = Random.Default

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            progressRepository.progress.collect { progress ->
                _state.update {
                    it.copy(
                        loadout = progress.loadout,
                        bestScore = progress.hordeBestScore,
                        bestWave = progress.hordeBestWave
                    )
                }
            }
        }
    }

    fun startGame() {
        roundJob?.cancel()
        flowJob?.cancel()
        val game = HordeEngine.start(random)
        _state.update {
            it.copy(
                game = game,
                shownEnemy = game.enemy,
                armorCracked = false,
                coinsEarned = 0,
                newRecord = false,
                newAchievements = emptyList(),
                stage = HordeStage.WaveBanner(1)
            )
        }
        flowJob = viewModelScope.launch {
            delay(BANNER_MS)
            startRound()
        }
    }

    /** Continua depois de uma pausa. */
    fun resume() {
        if (_state.value.stage == HordeStage.Paused) startRound()
    }

    private fun startRound() {
        if (roundJob?.isActive == true) return
        val current = _state.value
        val game = current.game ?: return
        if (game.gameOver) return
        val enemy = game.enemy

        val settings = current.settings
        val signal = if (settings.signalMode == SignalMode.CLAP && runner.canUseClap()) SignalMode.CLAP else SignalMode.VISUAL
        val visual = signal == SignalMode.VISUAL
        val touch = settings.scabbardSource == ScabbardSource.TOUCH
        val scabbard = settings.scabbardMode && (touch || runner.hasScabbardSensor)
        val rules = enemy.boss?.rules ?: emptySet()
        val modifier = game.modifier
        val blizzard = BossRule.BLIZZARD in rules || modifier == HordeModifier.SNOWSTORM
        val quick = BossRule.QUICK_SIGNAL in rules || modifier == HordeModifier.RUSH
        val soundOnly = visual && BossRule.SOUND_ONLY in rules
        val enemyMs = enemy.randomReactionMs(random)
        val config = RoundConfig(
            signalMode = signal,
            scabbardMode = scabbard,
            scabbardTouch = touch,
            sensitivity = settings.sensitivity,
            reactionLimitMs = enemyMs,
            opponentFoulChance = enemy.boss?.foulChance ?: 0.05f,
            minWaitMs = if (quick) 1_500 else 2_000,
            maxWaitMs = if (quick) 3_800 else 5_000,
            feints = if (visual && (BossRule.FEINT in rules || modifier == HordeModifier.FEINTS)) 1 else 0,
            halt = visual && (BossRule.HALT in rules || modifier == HordeModifier.HALT),
            signalVisibleMs = when {
                !visual -> null
                blizzard -> 420L
                BossRule.QUICK_SIGNAL in rules -> 330L
                else -> null
            }
        )
        _state.update {
            it.copy(
                shownEnemy = enemy,
                activeSignal = signal,
                scabbardActive = scabbard,
                scabbardTouch = touch,
                soundOnly = soundOnly,
                blizzard = blizzard
            )
        }

        roundJob = viewModelScope.launch {
            feedback.roundStart(settings)
            val outcome = runner.run(config) { phase ->
                _state.update { it.copy(stage = HordeStage.InRound(phase)) }
                when (phase) {
                    RoundPhase.WAITING -> feedback.tension(settings)
                    RoundPhase.SIGNAL -> feedback.signal(settings, signal, soundOnly)
                    RoundPhase.FEINT -> feedback.feint(settings)
                    RoundPhase.HALT -> feedback.halt(settings)
                    else -> Unit
                }
            }
            feedback.stopAmbient()
            resolve(outcome, enemyMs, settings)
        }
    }

    private suspend fun resolve(outcome: RoundOutcome, enemyMs: Long, settings: GameSettings) {
        val game = _state.value.game ?: return
        val armored = game.enemy.boss?.rules?.contains(BossRule.ARMOR) == true
        val strike = outcome as? RoundOutcome.Strike
        val won = (strike != null && strike.reactionMs < enemyMs) || outcome == RoundOutcome.OpponentFoul
        val perfect = strike != null && won && Rewards.isPerfect(strike.reactionMs)

        val result: HordeRoundResult
        val next: HordeState
        var cracked = _state.value.armorCracked
        if (won && armored && !cracked) {
            // Primeiro golpe só racha a armadura do chefe
            cracked = true
            next = game.copy(combo = game.combo + 1, bestCombo = maxOf(game.bestCombo, game.combo + 1))
            result = HordeRoundResult(HordeOutcome.ARMOR_CRACKED, strike?.reactionMs, enemyMs, perfect = perfect)
        } else if (won) {
            cracked = false
            val turn = HordeEngine.onKill(game, strike?.reactionMs, enemyMs, perfect, random)
            next = turn.state
            result = HordeRoundResult(
                outcome = if (strike != null) HordeOutcome.KILL else HordeOutcome.ENEMY_FOUL,
                playerMs = strike?.reactionMs,
                enemyMs = enemyMs,
                points = turn.points,
                perfect = perfect,
                healed = turn.healed
            )
        } else {
            next = HordeEngine.onHit(game).state
            val reason = when (outcome) {
                is RoundOutcome.Strike -> HordeOutcome.SLOWER
                RoundOutcome.TooSlow -> HordeOutcome.TOO_SLOW
                is RoundOutcome.PlayerFoul -> when (outcome.reason) {
                    FoulReason.FEINT -> HordeOutcome.FEINT
                    FoulReason.HALT -> HordeOutcome.HALT
                    else -> HordeOutcome.EARLY
                }
                RoundOutcome.OpponentFoul -> HordeOutcome.ENEMY_FOUL
            }
            result = HordeRoundResult(reason, strike?.reactionMs, enemyMs)
        }

        if (result.playerWon) feedback.strike(settings) else if (outcome is RoundOutcome.PlayerFoul) feedback.foul(settings) else feedback.strike(settings)
        if (perfect) {
            feedback.perfect(settings)
            progressRepository.recordPerfect()
        }
        val voice = when {
            !result.playerWon -> CutVoice.PLAYER
            armored -> CutVoice.ONI
            else -> CutVoice.OPPONENT
        }
        feedback.cut(settings, voice)
        if (strike != null) statsRepository.recordReaction(strike.reactionMs)

        _state.update { it.copy(game = next, armorCracked = cracked, stage = HordeStage.RoundOver(result)) }

        flowJob = viewModelScope.launch {
            delay(RESULT_MS)
            when {
                next.gameOver -> finish(next)
                next.wave != game.wave -> {
                    _state.update { it.copy(stage = HordeStage.WaveBanner(next.wave), shownEnemy = next.enemy) }
                    delay(BANNER_MS)
                    startRound()
                }
                else -> startRound()
            }
        }
    }

    private suspend fun finish(game: HordeState) {
        val coins = HordeEngine.coinsFor(game)
        progressRepository.addCoins(coins)
        val record = progressRepository.recordHorde(game.wave, game.score, game.kills, game.bestCombo)
        val fresh = progressRepository.checkAchievements(statsRepository.stats.first())
        if (record) feedback.victory(_state.value.settings) else feedback.defeat(_state.value.settings)
        _state.update {
            it.copy(
                stage = HordeStage.GameOver,
                coinsEarned = coins + fresh.sumOf { a -> a.reward },
                newRecord = record,
                newAchievements = fresh
            )
        }
    }

    /** App em segundo plano: interrompe o golpe sem perder vida. */
    fun pause() {
        val stage = _state.value.stage
        if (stage is HordeStage.InRound || stage is HordeStage.WaveBanner) {
            roundJob?.cancel()
            flowJob?.cancel()
            feedback.stopAmbient()
            _state.update { it.copy(stage = HordeStage.Paused) }
        }
    }

    fun setTouchHold(pressed: Boolean) {
        runner.setTouchHold(pressed)
    }

    override fun onCleared() {
        feedback.stopAmbient()
    }

    companion object {
        private const val BANNER_MS = 1_900L
        private const val RESULT_MS = 1_900L

        val Factory = iaiFactory { container ->
            HordeViewModel(
                settingsRepository = container.settingsRepository,
                statsRepository = container.statsRepository,
                progressRepository = container.progressRepository,
                runner = container.roundRunner,
                feedback = container.feedback
            )
        }
    }
}
