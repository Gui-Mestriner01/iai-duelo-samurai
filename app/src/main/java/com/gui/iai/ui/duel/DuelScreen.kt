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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.domain.Opponent
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
    val scene = SceneState(
        leftPose = if (showResult && report?.winner == Side.PLAYER) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        rightPose = if (showResult && report?.winner == Side.OPPONENT) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        leftFall = playerFall.value,
        rightFall = opponentFall.value,
        rightObi = state.opponent.accentColor(),
        rightStyle = state.opponent.sceneStyle(),
        tension = tension
    )

    GameStage(
        onHoldChange = viewModel::setTouchHold,
        holdIndicator = state.scabbardActive && state.scabbardTouch && stage is DuelStage.InRound,
        scene = scene,
        effects = effects,
        signalVisible = stage is DuelStage.InRound && stage.phase == RoundPhase.SIGNAL,
        header = { DuelHeader(state, onExit) },
        sky = {
            when (stage) {
                DuelStage.Intro -> OpponentIntro(state.opponent)
                DuelStage.Ready -> ReadyPrompt(state.roundNumber)
                is DuelStage.InRound -> PhasePrompt(
                    phase = stage.phase,
                    signalMode = state.activeSignal,
                    touchScabbard = state.scabbardActive && state.scabbardTouch
                )
                is DuelStage.RoundOver -> RoundResultSky(stage.report, state.opponent)
                is DuelStage.MatchOver -> Unit
            }
        },
        ground = {
            when (stage) {
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
            RoundMarkers(wins = state.playerWins, filledColor = Iai.Shu)
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
            RoundMarkers(wins = state.opponentWins, filledColor = Iai.Sumi)
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
    }
}

@Composable
fun SpeedDots(level: Int, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = Iai.Sumi) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("VELOCIDADE ", style = MaterialTheme.typography.labelSmall, color = color)
        repeat(5) { index ->
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
    RoundReason.OPPONENT_EARLY -> "${opponent.displayName} se precipitou! O golpe é seu."
}

@Composable
private fun RoundResultSky(report: RoundReport, opponent: Opponent) {
    val won = report.winner == Side.PLAYER
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HankoSeal(
            text = if (won) "勝" else "敗",
            size = 72.dp,
            color = if (won) Iai.Shu else Iai.Sumi,
            stampIn = true
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (won) "Golpe vencido" else "Golpe perdido",
            style = MaterialTheme.typography.headlineLarge,
            color = Iai.Sumi
        )
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
                    won && state.unlocked != null ->
                        "Novo desafiante no Caminho: ${state.unlocked.displayName}, ${state.unlocked.title}."

                    won && state.opponent == Opponent.KUROGANE ->
                        "Você derrotou o Demônio de Ferro. O Caminho está completo."

                    won -> "A honra foi mantida."
                    else -> "Um samurai cai sete vezes e levanta oito."
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (won) Iai.Shu else Iai.SumiSoft,
                    textAlign = TextAlign.Center
                )
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
