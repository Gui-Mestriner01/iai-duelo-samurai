package com.gui.iai.ui.training

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.game.FoulReason
import com.gui.iai.game.RoundOutcome
import com.gui.iai.game.RoundPhase
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.InkBackButton
import com.gui.iai.ui.components.RightFigure
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

@Composable
fun TrainingScreen(
    onExit: () -> Unit,
    viewModel: TrainingViewModel = viewModel(factory = TrainingViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameplayLifecycle(onStop = viewModel::abort)

    val effects = rememberStrikeEffects()
    val cut = remember { Animatable(0f) }
    val stage = state.stage

    LaunchedEffect(stage) {
        when (stage) {
            is TrainingStage.Result -> if (stage.outcome is RoundOutcome.Strike) {
                launch { effects.play() }
                cut.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
            }

            else -> {
                cut.snapTo(0f)
                effects.reset()
            }
        }
    }

    val waiting = stage is TrainingStage.InRound && stage.phase == RoundPhase.WAITING
    val tension by animateFloatAsState(
        targetValue = if (waiting) 1f else 0f,
        animationSpec = tween(if (waiting) 3_500 else 300),
        label = "tension"
    )
    val struck = stage is TrainingStage.Result && stage.outcome is RoundOutcome.Strike

    GameStage(
        onHoldChange = viewModel::setTouchHold,
        holdIndicator = state.scabbardActive && state.scabbardTouch && stage is TrainingStage.InRound,
        scene = SceneState(
            leftPose = if (struck) SamuraiPose.STRIKE else SamuraiPose.GUARD,
            rightFigure = RightFigure.MAKIWARA,
            tension = tension,
            cut = cut.value
        ),
        effects = effects,
        signalVisible = stage is TrainingStage.InRound && stage.phase == RoundPhase.SIGNAL,
        header = { TrainingHeader(state, onExit) },
        sky = {
            when (stage) {
                TrainingStage.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HankoSeal(text = "修", size = 64.dp, stampIn = true)
                    Spacer(Modifier.height(10.dp))
                    Text("Treino no dojo", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
                    Text(
                        text = "Corte o makiwara assim que o sinal aparecer. Sem oponente, só você e o tempo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Iai.SumiSoft,
                        textAlign = TextAlign.Center
                    )
                }

                is TrainingStage.InRound -> PhasePrompt(
                    phase = stage.phase,
                    signalMode = state.activeSignal,
                    touchScabbard = state.scabbardActive && state.scabbardTouch
                )
                is TrainingStage.Result -> TrainingResultSky(stage.outcome, state.recordMs)
            }
        },
        ground = {
            when (stage) {
                is TrainingStage.InRound -> HoldStillHint(touchScabbard = state.scabbardActive && state.scabbardTouch)
                else -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GroundTime("Melhor", state.sessionBest?.let(::formatSeconds) ?: "—", highlight = true)
                        GroundTime("Média", state.sessionAverage?.let(::formatSeconds) ?: "—", highlight = false)
                        GroundTime("Cortes", state.times.size.toString(), highlight = false)
                    }
                    GroundButton(
                        text = if (stage is TrainingStage.Ready) "Começar" else "Golpear de novo",
                        kanji = "斬",
                        onClick = viewModel::strike,
                        height = 64
                    )
                }
            }
        }
    )
}

@Composable
private fun TrainingHeader(state: TrainingUiState, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InkBackButton(onClick = onExit)
        Spacer(Modifier.width(12.dp))
        Text("修行", style = MaterialTheme.typography.headlineMedium, color = Iai.Shu)
        Spacer(Modifier.width(8.dp))
        Text("TREINO", style = MaterialTheme.typography.labelLarge, color = Iai.Sumi, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("RECORDE", style = MaterialTheme.typography.labelSmall, color = Iai.SumiSoft)
            Text(
                text = state.recordMs?.let(::formatSeconds) ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = Iai.Sumi
            )
        }
    }
}

@Composable
private fun TrainingResultSky(outcome: RoundOutcome, recordMs: Long?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (outcome) {
            is RoundOutcome.Strike -> {
                val isRecord = recordMs != null && outcome.reactionMs <= recordMs
                Text(
                    text = if (isRecord) "NOVO RECORDE" else "TEMPO DE REAÇÃO",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isRecord) Iai.Shu else Iai.SumiSoft
                )
                Text(formatSeconds(outcome.reactionMs), style = MaterialTheme.typography.displayLarge, color = Iai.Sumi)
                Text(
                    text = "${ratingFor(outcome.reactionMs)} · força ${formatG(outcome.peakG)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Iai.SumiSoft
                )
            }

            is RoundOutcome.PlayerFoul -> {
                HankoSeal(text = "反", size = 64.dp, color = Iai.Sumi, stampIn = true)
                Spacer(Modifier.height(8.dp))
                Text("Precipitado!", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
                Text(
                    text = if (outcome.reason == FoulReason.EARLY_DRAW) {
                        "Você desembainhou antes do sinal."
                    } else {
                        "Você cortou antes do sinal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Iai.SumiSoft
                )
            }

            else -> {
                Text("Lento demais", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
                Text("O sinal passou e a lâmina ficou na bainha.", style = MaterialTheme.typography.bodyMedium, color = Iai.SumiSoft)
            }
        }
    }
}

/** Classificação divertida do tempo de reação. */
private fun ratingFor(ms: Long): String = when {
    ms < 260 -> "Reflexo de lenda"
    ms < 320 -> "Mestre da espada"
    ms < 400 -> "Samurai"
    ms < 500 -> "Ronin"
    else -> "Aprendiz"
}
