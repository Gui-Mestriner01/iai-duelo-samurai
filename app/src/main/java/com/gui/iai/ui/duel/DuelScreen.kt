package com.gui.iai.ui.duel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.domain.Achievement
import com.gui.iai.domain.BossRule
import com.gui.iai.domain.Opponent
import com.gui.iai.domain.Story
import com.gui.iai.ui.story.StoryOverlay
import com.gui.iai.game.RoundPhase
import com.gui.iai.ui.components.BrushButton
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.InkBackButton
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.SamuraiPose
import com.gui.iai.ui.components.SceneState
import com.gui.iai.ui.components.accentColor
import com.gui.iai.ui.components.sceneStyle
import com.gui.iai.ui.game.GameStage
import com.gui.iai.ui.game.GameplayLifecycle
import com.gui.iai.ui.game.GroundLabel
import com.gui.iai.ui.game.GroundTime
import com.gui.iai.ui.game.HoldStillHint
import com.gui.iai.ui.game.PhasePrompt
import com.gui.iai.ui.game.RoundMarkers
import com.gui.iai.ui.game.formatG
import com.gui.iai.ui.game.formatSeconds
import com.gui.iai.ui.game.rememberStrikeEffects
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DuelScreen(
    onExit: () -> Unit,
    onOpenPath: () -> Unit,
    viewModel: DuelViewModel = viewModel(factory = DuelViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameplayLifecycle(onStop = viewModel::abortRound)

    val effects = rememberStrikeEffects()
    val playerFall = remember { Animatable(0f) }
    val opponentFall = remember { Animatable(0f) }
    val stage = state.stage

    LaunchedEffect(stage) {
        when (stage) {
            is DuelStage.RoundOver -> {
                launch { effects.play() }
                delay(140)
                val loser = if (stage.report.winner == Side.PLAYER) opponentFall else playerFall
                loser.animateTo(1f, tween(1_100, easing = LinearEasing))
            }

            is DuelStage.MatchOver -> Unit
            else -> {
                playerFall.snapTo(0f)
                opponentFall.snapTo(0f)
                effects.reset()
            }
        }
    }

    val waiting = stage is DuelStage.InRound && stage.phase == RoundPhase.WAITING
    val tension by animateFloatAsState(
        targetValue = if (waiting) 1f else 0f,
        animationSpec = tween(if (waiting) 4_000 else 300),
        label = "tension"
    )

    val showResult = stage is DuelStage.RoundOver || stage is DuelStage.MatchOver
    val report = state.lastReport
    val phase = (stage as? DuelStage.InRound)?.phase
    val blizzard = BossRule.BLIZZARD in state.opponent.rules
    val scene = SceneState(
        leftPose = if (showResult && report?.winner == Side.PLAYER) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        rightPose = if (showResult && report?.winner == Side.OPPONENT) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        leftFall = playerFall.value,
        rightFall = opponentFall.value,
        leftObi = Color(state.loadout.obi.argb),
        leftTrail = Color(state.loadout.blade.argb),
        rightObi = state.opponent.accentColor(),
        rightStyle = state.opponent.sceneStyle(),
        tension = tension,
        arena = state.opponent.arena,
        blizzard = blizzard,
        perfect = showResult && report?.perfect == true
    )

    GameStage(
        onHoldChange = viewModel::setTouchHold,
        holdIndicator = state.scabbardActive && state.scabbardTouch && stage is DuelStage.InRound,
        scene = scene,
        effects = effects,
        signalVisible = phase == RoundPhase.SIGNAL && !state.soundOnly,
        feintVisible = phase == RoundPhase.FEINT,
        haltVisible = phase == RoundPhase.HALT,
        signalAlpha = if (blizzard) 0.5f else 1f,
        header = { if (stage !is DuelStage.Story) DuelHeader(state, onExit) },
        sky = {
            when (stage) {
                is DuelStage.Story -> Unit
                DuelStage.Intro -> OpponentIntro(state.opponent)
                DuelStage.Ready -> ReadyPrompt(state.roundNumber)
                is DuelStage.InRound -> PhasePrompt(
                    phase = stage.phase,
                    signalMode = state.activeSignal,
                    touchScabbard = state.scabbardActive && state.scabbardTouch,
                    soundOnly = state.soundOnly
                )
                is DuelStage.RoundOver -> RoundResultSky(stage.report, state.opponent)
                is DuelStage.MatchOver -> Unit
            }
        },
        ground = {
            when (stage) {
                is DuelStage.Story -> Unit
                DuelStage.Intro -> GroundButton("Aceitar o duelo", "決", viewModel::startRound)
                DuelStage.Ready -> GroundButton("Preparar golpe ${state.roundNumber}", "構", viewModel::startRound)
                is DuelStage.InRound -> HoldStillHint(touchScabbard = state.scabbardActive && state.scabbardTouch)
                is DuelStage.RoundOver -> RoundResultGround(
                    report = stage.report,
                    opponent = state.opponent,
                    matchDecided = state.isMatchDecided,
                    onContinue = viewModel::continueAfterRound
                )

                is DuelStage.MatchOver -> Unit
            }
        },
        overlay = {
            if (stage is DuelStage.Story) {
                val lines = state.story
                StoryOverlay(
                    opponent = state.opponent,
                    line = lines[stage.index.coerceIn(0, lines.lastIndex)],
                    index = stage.index,
                    total = lines.size,
                    heroObi = Color(state.loadout.obi.argb),
                    onNext = viewModel::advanceStory,
                    onSkip = viewModel::skipStory
                )
            }
            if (stage is DuelStage.MatchOver) {
                MatchOverOverlay(
                    state = state,
                    won = stage.playerWon,
                    onRematch = viewModel::rematch,
                    onPath = onOpenPath
                )
            }
        }
    )
}

@Composable
private fun DuelHeader(state: DuelUiState, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InkBackButton(onClick = onExit)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("VOCÊ", style = MaterialTheme.typography.labelMedium, color = Iai.Sumi)
            RoundMarkers(wins = state.playerWins, filledColor = Iai.Shu, total = state.winsToBeat)
        }
        Text(
            text = "GOLPE ${state.roundNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = Iai.SumiSoft
        )
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(
                text = state.opponent.displayName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = Iai.Sumi,
                maxLines = 1
            )
            RoundMarkers(wins = state.opponentWins, filledColor = Iai.Sumi, total = Opponent.OPPONENT_WINS_NEEDED)
        }
    }
}

@Composable
private fun OpponentIntro(opponent: Opponent) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HankoSeal(text = opponent.kanji, size = 60.dp, stampIn = true)
        Spacer(Modifier.height(8.dp))
        Text(opponent.displayName, style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
        Text(opponent.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Iai.Shu)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "“${opponent.quote}”",
            style = MaterialTheme.typography.bodyMedium,
            color = Iai.SumiSoft,
            textAlign = TextAlign.Center
        )
        if (opponent.rules.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BossRules(opponent.rules)
        }
    }
}

/** As regras especiais do chefe, cada uma com o seu selo. */
@Composable
fun BossRules(rules: Set<BossRule>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rules.forEach { rule ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                HankoSeal(text = rule.kanji, size = 30.dp, color = Iai.Sumi, rotation = -4f)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(rule.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Iai.Shu)
                    Text(rule.description, style = MaterialTheme.typography.bodySmall, color = Iai.SumiSoft)
                }
            }
        }
    }
}

@Composable
fun SpeedDots(level: Int, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Iai.Sumi) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("VELOCIDADE ", style = MaterialTheme.typography.labelSmall, color = color)
        repeat(Opponent.entries.size) { index ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (index < level) Iai.Shu else color.copy(alpha = 0.2f))
            )
        }
    }
}

@Composable
private fun ReadyPrompt(round: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Golpe $round", style = MaterialTheme.typography.displaySmall, color = Iai.Sumi)
        Text(
            text = "Quando estiver pronto, prepare a lâmina e fique imóvel.",
            style = MaterialTheme.typography.bodyMedium,
            color = Iai.SumiSoft,
            textAlign = TextAlign.Center
        )
    }
}

private fun reasonText(report: RoundReport, opponent: Opponent): String = when (report.reason) {
    RoundReason.FASTER -> "Seu corte foi mais rápido."
    RoundReason.SLOWER -> "Por um fio... ${opponent.displayName} foi mais rápido."
    RoundReason.TOO_SLOW -> "Você hesitou. A lâmina dele chegou primeiro."
    RoundReason.PLAYER_EARLY_SWING -> "Precipitado! Você sacou antes do sinal."
    RoundReason.PLAYER_EARLY_DRAW -> "Você desembainhou antes do sinal."
    RoundReason.PLAYER_FEINT -> "Caiu na finta! Aquele não era o sinal."
    RoundReason.PLAYER_HALT -> "O 止 estava na tela. A lâmina tinha que esperar."
    RoundReason.OPPONENT_EARLY -> "${opponent.displayName} se precipitou! O golpe é seu."
}

@Composable
private fun RoundResultSky(report: RoundReport, opponent: Opponent) {
    val won = report.winner == Side.PLAYER
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HankoSeal(
            text = when {
                report.perfect -> "極"
                won -> "勝"
                else -> "敗"
            },
            size = if (report.perfect) 86.dp else 72.dp,
            color = when {
                report.perfect -> Iai.Coin
                won -> Iai.Shu
                else -> Iai.Sumi
            },
            stampIn = true
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                report.perfect -> "IAI PERFEITO!"
                won -> "Golpe vencido"
                else -> "Golpe perdido"
            },
            style = MaterialTheme.typography.headlineLarge,
            color = if (report.perfect) Iai.Shu else Iai.Sumi
        )
        if (report.perfect) {
            Text(
                text = "Vale dois golpes!",
                style = MaterialTheme.typography.titleSmall,
                color = Iai.Shu
            )
        }
        Text(
            text = reasonText(report, opponent),
            style = MaterialTheme.typography.bodyMedium,
            color = Iai.SumiSoft,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BoxScope.RoundResultGround(
    report: RoundReport,
    opponent: Opponent,
    matchDecided: Boolean,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            GroundTime(
                label = "Você",
                value = report.playerMs?.let(::formatSeconds) ?: "—",
                highlight = report.winner == Side.PLAYER
            )
            GroundTime(
                label = opponent.displayName,
                value = report.opponentMs?.let(::formatSeconds) ?: "—",
                highlight = false
            )
        }
        report.peakG?.let { GroundLabel("Força do corte: ${formatG(it)}") }
        if (report.coins > 0) GroundLabel("+${report.coins} 文", alpha = 1f)
        GroundButton(
            text = if (matchDecided) "Ver resultado" else "Próximo golpe",
            kanji = if (matchDecided) "終" else "次",
            onClick = onContinue,
            height = 64
        )
    }
}

/** Botão claro para usar sobre o chão escuro. */
@Composable
fun GroundButton(text: String, kanji: String, onClick: () -> Unit, height: Int = 72) {
    BrushButton(
        text = text,
        kanji = kanji,
        onClick = onClick,
        inkColor = Iai.Washi,
        textColor = Iai.Sumi,
        kanjiColor = Iai.Shu,
        height = height.dp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MatchOverOverlay(
    state: DuelUiState,
    won: Boolean,
    onRematch: () -> Unit,
    onPath: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Iai.Sumi.copy(alpha = 0.55f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        PaperPanel(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HankoSeal(
                    text = if (won) "勝" else "敗",
                    size = 104.dp,
                    color = if (won) Iai.Shu else Iai.Sumi,
                    stampIn = true
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (won) "Vitória" else "Derrota",
                    style = MaterialTheme.typography.displayMedium,
                    color = Iai.Sumi
                )
                Text(
                    text = "${state.playerWins}  –  ${state.opponentWins}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Iai.Shu
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.matchBestMs?.let { "Melhor reação: ${formatSeconds(it)}" } ?: "Nenhum golpe válido",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Iai.SumiSoft
                )
                val message = when {
                    won -> Story.epilogue(state.opponent)
                    else -> "Um samurai cai sete vezes e levanta oito."
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (won) Iai.Shu else Iai.SumiSoft,
                    textAlign = TextAlign.Center
                )
                if (won && state.unlocked != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Novo desafiante: ${state.unlocked.displayName}, ${state.unlocked.title}.",
                        style = MaterialTheme.typography.labelMedium,
                        color = Iai.Shu,
                        textAlign = TextAlign.Center
                    )
                }
                if (state.coinsEarned > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "+${state.coinsEarned} 文",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Iai.Coin
                    )
                }
                if (state.newAchievements.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    NewAchievements(state.newAchievements)
                }
                Spacer(Modifier.height(18.dp))
                BrushButton(
                    text = "Revanche",
                    kanji = "再",
                    onClick = onRematch,
                    height = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                BrushButton(
                    text = "O Caminho",
                    kanji = "道",
                    onClick = onPath,
                    height = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Conquistas que acabaram de sair, com o selo carimbando. */
@Composable
fun NewAchievements(achievements: List<Achievement>, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CONQUISTA!", style = MaterialTheme.typography.labelMedium, color = Iai.Shu)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            achievements.take(4).forEach { achievement ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HankoSeal(text = achievement.kanji, size = 46.dp, stampIn = true)
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = Iai.Sumi,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
