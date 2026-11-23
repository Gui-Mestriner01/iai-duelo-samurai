package com.gui.iai.ui.versus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.game.RoundPhase
import com.gui.iai.ui.components.BrushButton
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.InkBackButton
import com.gui.iai.ui.components.InkDivider
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.SamuraiPose
import com.gui.iai.ui.components.SceneState
import com.gui.iai.ui.duel.GroundButton
import com.gui.iai.ui.game.GameStage
import com.gui.iai.ui.game.GameplayLifecycle
import com.gui.iai.ui.game.GroundLabel
import com.gui.iai.ui.game.GroundTime
import com.gui.iai.ui.game.HoldStillHint
import com.gui.iai.ui.game.PhasePrompt
import com.gui.iai.ui.game.formatG
import com.gui.iai.ui.game.formatSeconds
import com.gui.iai.ui.game.rememberStrikeEffects
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.launch

private fun playerColor(player: Int): Color = if (player == 1) Iai.Shu else Iai.Kin
private fun playerKanji(player: Int): String = if (player == 1) "一" else "二"

@Composable
fun VersusScreen(
    onExit: () -> Unit,
    viewModel: VersusViewModel = viewModel(factory = VersusViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameplayLifecycle(onStop = viewModel::abortTurn)

    val effects = rememberStrikeEffects()
    val leftFall = remember { Animatable(0f) }
    val rightFall = remember { Animatable(0f) }
    val stage = state.stage

    LaunchedEffect(stage) {
        when (stage) {
            is VersusStage.TurnOver -> {
                val result = stage.result
                if (result.reactionMs != null) launch { effects.play() }
                if (result.foul) {
                    // Desonra: quem se precipitou cai
                    val fallen = if (result.player == 1) leftFall else rightFall
                    fallen.animateTo(1f, tween(1_100, easing = LinearEasing))
                }
            }

            VersusStage.Final -> Unit
            else -> {
                leftFall.snapTo(0f)
                rightFall.snapTo(0f)
                effects.reset()
            }
        }
    }

    val waiting = stage is VersusStage.InRound && stage.phase == RoundPhase.WAITING
    val tension by animateFloatAsState(
        targetValue = if (waiting) 1f else 0f,
        animationSpec = tween(if (waiting) 4_000 else 300),
        label = "tension"
    )

    val turnResult = (stage as? VersusStage.TurnOver)?.result
    val striking = turnResult?.takeIf { it.reactionMs != null }?.player
    val scene = SceneState(
        leftPose = if (striking == 1) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        rightPose = if (striking == 2) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        leftFall = leftFall.value,
        rightFall = rightFall.value,
        leftObi = playerColor(1),
        rightObi = playerColor(2),
        tension = tension
    )

    GameStage(
        onHoldChange = viewModel::setTouchHold,
        holdIndicator = state.scabbardActive && state.scabbardTouch && stage is VersusStage.InRound,
        scene = scene,
        effects = effects,
        signalVisible = stage is VersusStage.InRound && stage.phase == RoundPhase.SIGNAL,
        header = { VersusHeader(state, onExit) },
        sky = {
            when (stage) {
                VersusStage.Handoff -> HandoffSky(state)
                is VersusStage.InRound -> PhasePrompt(
                    phase = stage.phase,
                    signalMode = state.activeSignal,
                    touchScabbard = state.scabbardActive && state.scabbardTouch
                )
                is VersusStage.TurnOver -> TurnSky(stage.result)
                VersusStage.Final -> Unit
            }
        },
        ground = {
            when (stage) {
                VersusStage.Handoff -> GroundButton("Pronto", playerKanji(state.currentPlayer), viewModel::startTurn)
                is VersusStage.InRound -> HoldStillHint(touchScabbard = state.scabbardActive && state.scabbardTouch)
                is VersusStage.TurnOver -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    stage.result.peakG?.let { GroundLabel("Força do corte: ${formatG(it)}") }
                    val last = state.turnIndex + 1 >= VersusUiState.TOTAL_TURNS
                    GroundButton(
                        text = if (last) "Ver resultado" else "Passar a vez",
                        kanji = if (last) "終" else "次",
                        onClick = viewModel::nextTurn
                    )
                }

                VersusStage.Final -> Unit
            }
        },
        overlay = {
            if (stage is VersusStage.Final) {
                VersusFinal(state = state, onRestart = viewModel::restart, onExit = onExit)
            }
        }
    )
}

@Composable
private fun VersusHeader(state: VersusUiState, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InkBackButton(onClick = onExit)
        Spacer(Modifier.width(12.dp))
        PlayerBest(1, state.bestOf(1), Modifier.weight(1f), Alignment.Start)
        Text(
            text = "${state.attemptOfPlayer.coerceAtMost(VersusUiState.TURNS_PER_PLAYER)}/${VersusUiState.TURNS_PER_PLAYER}",
            style = MaterialTheme.typography.labelMedium,
            color = Iai.SumiSoft
        )
        PlayerBest(2, state.bestOf(2), Modifier.weight(1f), Alignment.End)
    }
}

@Composable
private fun PlayerBest(player: Int, bestMs: Long?, modifier: Modifier, alignment: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = alignment) {
        Text("JOGADOR $player", style = MaterialTheme.typography.labelMedium, color = playerColor(player))
        Text(
            text = bestMs?.let(::formatSeconds) ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = Iai.Sumi
        )
    }
}

@Composable
private fun HandoffSky(state: VersusUiState) {
    val player = state.currentPlayer
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HankoSeal(text = playerKanji(player), size = 72.dp, color = playerColor(player), stampIn = true)
        Spacer(Modifier.height(10.dp))
        Text("Vez do Jogador $player", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
        Text(
            text = "Tentativa ${state.attemptOfPlayer} de ${VersusUiState.TURNS_PER_PLAYER}. " +
                "Pegue o celular, segure firme e toque em Pronto.",
            style = MaterialTheme.typography.bodyMedium,
            color = Iai.SumiSoft,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TurnSky(result: TurnResult) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            result.reactionMs != null -> {
                Text("Jogador ${result.player}", style = MaterialTheme.typography.labelLarge, color = playerColor(result.player))
                Text(formatSeconds(result.reactionMs), style = MaterialTheme.typography.displayMedium, color = Iai.Sumi)
                Text("Corte registrado.", style = MaterialTheme.typography.bodyMedium, color = Iai.SumiSoft)
            }

            result.foul -> {
                HankoSeal(text = "反", size = 72.dp, color = Iai.Sumi, stampIn = true)
                Spacer(Modifier.height(8.dp))
                Text("Precipitado!", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
                Text("Sacou antes do sinal. Tentativa perdida.", style = MaterialTheme.typography.bodyMedium, color = Iai.SumiSoft)
            }

            else -> {
                Text("Lento demais", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
                Text("Nenhum corte depois do sinal.", style = MaterialTheme.typography.bodyMedium, color = Iai.SumiSoft)
            }
        }
    }
}

@Composable
private fun VersusFinal(state: VersusUiState, onRestart: () -> Unit, onExit: () -> Unit) {
    val winner = state.winner
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
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HankoSeal(
                    text = if (winner != null) "勝" else "引",
                    size = 96.dp,
                    color = if (winner != null) playerColor(winner) else Iai.Sumi,
                    stampIn = true
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (winner != null) "Jogador $winner vence!" else "Empate",
                    style = MaterialTheme.typography.displaySmall,
                    color = Iai.Sumi,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                InkDivider()
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(1, 2).forEach { player ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("JOGADOR $player", style = MaterialTheme.typography.labelMedium, color = playerColor(player))
                            state.results.filter { it.player == player }.forEach { result ->
                                Text(
                                    text = when {
                                        result.reactionMs != null -> formatSeconds(result.reactionMs)
                                        result.foul -> "precipitado"
                                        else -> "lento"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (result.reactionMs != null && result.reactionMs == state.bestOf(player)) {
                                        Iai.Shu
                                    } else {
                                        Iai.SumiSoft
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                BrushButton(
                    text = "Jogar de novo",
                    kanji = "再",
                    onClick = onRestart,
                    height = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                BrushButton(
                    text = "Início",
                    kanji = "門",
                    onClick = onExit,
                    height = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
