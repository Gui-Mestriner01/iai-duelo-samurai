package com.gui.iai.ui.horde

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.domain.HordeEngine
import com.gui.iai.domain.HordeModifier
import com.gui.iai.game.RoundPhase
import com.gui.iai.ui.components.BrushButton
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.InkBackButton
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.SamuraiPose
import com.gui.iai.ui.components.SceneState
import com.gui.iai.ui.components.accentColor
import com.gui.iai.ui.components.sceneStyle
import com.gui.iai.ui.duel.BossRules
import com.gui.iai.ui.duel.GroundButton
import com.gui.iai.ui.duel.NewAchievements
import com.gui.iai.ui.game.GameStage
import com.gui.iai.ui.game.GameplayLifecycle
import com.gui.iai.ui.game.GroundLabel
import com.gui.iai.ui.game.GroundTime
import com.gui.iai.ui.game.HoldStillHint
import com.gui.iai.ui.game.PhasePrompt
import com.gui.iai.ui.game.formatSeconds
import com.gui.iai.ui.game.rememberStrikeEffects
import com.gui.iai.ui.theme.BrushFont
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HordeScreen(
    onExit: () -> Unit,
    viewModel: HordeViewModel = viewModel(factory = HordeViewModel.Factory)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GameplayLifecycle(onStop = viewModel::pause)

    val effects = rememberStrikeEffects()
    val playerFall = remember { Animatable(0f) }
    val enemyFall = remember { Animatable(0f) }
    val stage = state.stage

    LaunchedEffect(stage) {
        when (stage) {
            is HordeStage.RoundOver -> {
                launch { effects.play() }
                delay(140)
                val target = if (stage.result.playerWon) enemyFall else playerFall
                // Armadura rachada: o chefe só cambaleia, não cai
                val amount = if (stage.result.outcome == HordeOutcome.ARMOR_CRACKED) 0.16f else 1f
                target.animateTo(amount, tween(1_100, easing = LinearEasing))
            }

            HordeStage.GameOver -> Unit
            else -> {
                playerFall.snapTo(0f)
                enemyFall.snapTo(0f)
                effects.reset()
            }
        }
    }

    val phase = (stage as? HordeStage.InRound)?.phase
    val waiting = phase == RoundPhase.WAITING
    val tension by animateFloatAsState(
        targetValue = if (waiting) 1f else 0f,
        animationSpec = tween(if (waiting) 3_500 else 300),
        label = "hordeTension"
    )

    val result = (stage as? HordeStage.RoundOver)?.result
    val enemy = state.shownEnemy
    val scene = SceneState(
        leftPose = if (result?.playerWon == true) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        rightPose = if (result != null && !result.playerWon) SamuraiPose.STRIKE else SamuraiPose.GUARD,
        leftFall = playerFall.value,
        rightFall = enemyFall.value,
        leftObi = Color(state.loadout.obi.argb),
        leftTrail = Color(state.loadout.blade.argb),
        rightStyle = enemy?.boss?.sceneStyle() ?: enemy?.look?.sceneStyle() ?: com.gui.iai.ui.components.SamuraiStyle.ASHIGARU,
        rightObi = enemy?.boss?.accentColor() ?: enemy?.let { Color(it.accentArgb) } ?: Iai.Kin,
        rightSerial = enemy?.serial ?: 0,
        tension = tension,
        arena = state.arena,
        blizzard = state.blizzard && stage is HordeStage.InRound,
        perfect = result?.perfect == true
    )

    GameStage(
        onHoldChange = viewModel::setTouchHold,
        holdIndicator = state.scabbardActive && state.scabbardTouch && stage is HordeStage.InRound,
        scene = scene,
        effects = effects,
        signalVisible = phase == RoundPhase.SIGNAL && !state.soundOnly,
        feintVisible = phase == RoundPhase.FEINT,
        haltVisible = phase == RoundPhase.HALT,
        signalAlpha = if (state.blizzard) 0.5f else 1f,
        header = { HordeHeader(state, onExit) },
        sky = {
            when (stage) {
                HordeStage.Lobby -> HordeLobby(state)
                is HordeStage.WaveBanner -> WaveBanner(state, stage.wave)
                HordeStage.Paused -> Text("Pausa", style = MaterialTheme.typography.displaySmall, color = Iai.Sumi)
                is HordeStage.InRound -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PhasePrompt(
                        phase = stage.phase,
                        signalMode = state.activeSignal,
                        touchScabbard = state.scabbardActive && state.scabbardTouch,
                        soundOnly = state.soundOnly
                    )
                }
                is HordeStage.RoundOver -> RoundSky(stage.result, state)
                HordeStage.GameOver -> Unit
            }
        },
        ground = {
            when (stage) {
                HordeStage.Lobby -> GroundButton("Começar a horda", "戦", viewModel::startGame)
                HordeStage.Paused -> GroundButton("Continuar", "続", viewModel::resume)
                is HordeStage.WaveBanner -> GroundLabel(enemy?.name ?: "", alpha = 0.9f)
                is HordeStage.InRound -> HoldStillHint(touchScabbard = state.scabbardActive && state.scabbardTouch)
                is HordeStage.RoundOver -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GroundTime("Você", stage.result.playerMs?.let(::formatSeconds) ?: "—", highlight = stage.result.playerWon)
                    GroundTime(enemy?.name ?: "Inimigo", stage.result.enemyMs?.let(::formatSeconds) ?: "—", highlight = false)
                }
                HordeStage.GameOver -> Unit
            }
        },
        overlay = {
            if (stage == HordeStage.GameOver) {
                GameOverOverlay(state, onRetry = viewModel::startGame, onExit = onExit)
            }
        }
    )
}

@Composable
private fun HordeHeader(state: HordeUiState, onExit: () -> Unit) {
    val game = state.game
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InkBackButton(onClick = onExit)
        Spacer(Modifier.width(12.dp))
        // Vidas: selos 命 vermelhos (cheios) e apagados
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(HordeEngine.MAX_LIVES) { index ->
                val alive = game != null && index < game.lives
                HankoSeal(
                    text = "命",
                    size = 26.dp,
                    color = if (alive) Iai.Shu else Iai.SumiFaded.copy(alpha = 0.35f),
                    rotation = if (index % 2 == 0) -6f else 6f
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (game != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "ONDA ${game.wave}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Iai.Sumi
                )
                Text(
                    text = "%,d".format(game.score).replace(',', '.'),
                    style = MaterialTheme.typography.titleMedium,
                    color = Iai.Shu
                )
                if (game.combo >= 2) {
                    Text(
                        text = "COMBO ${game.combo}  ×${"%.1f".format(game.multiplier).replace('.', ',')}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Iai.SumiSoft
                    )
                }
            }
        }
    }
}

@Composable
private fun HordeLobby(state: HordeUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HankoSeal(text = "波", size = 64.dp, stampIn = true)
        Spacer(Modifier.height(8.dp))
        Text("Hordas", style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
        Text(
            text = "Inimigos sem fim, cada onda mais rápida. Você tem 3 vidas.\nIai Perfeito recupera uma vida. A cada 5 ondas, um chefe.",
            style = MaterialTheme.typography.bodyMedium,
            color = Iai.SumiSoft,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Recorde: ${state.bestScore} pontos · onda ${state.bestWave}",
            style = MaterialTheme.typography.titleSmall,
            color = Iai.Shu
        )
    }
}

@Composable
private fun WaveBanner(state: HordeUiState, wave: Int) {
    val game = state.game
    val boss = state.shownEnemy?.boss
    val scale = remember(wave) { Animatable(1.8f) }
    LaunchedEffect(wave) { scale.animateTo(1f, tween(260)) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "第${wave}波",
            fontFamily = BrushFont,
            fontSize = (46 * scale.value).sp,
            color = Iai.Sumi
        )
        Text(
            text = if (boss != null) "ONDA $wave · CHEFE" else "ONDA $wave",
            style = MaterialTheme.typography.labelLarge,
            color = Iai.Shu
        )
        Spacer(Modifier.height(8.dp))
        when {
            boss != null -> {
                Text(boss.displayName, style = MaterialTheme.typography.headlineSmall, color = Iai.Sumi)
                Text(boss.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iai.Shu)
                Spacer(Modifier.height(6.dp))
                BossRules(boss.rules)
            }

            game != null && game.modifier != HordeModifier.NONE -> Row(verticalAlignment = Alignment.CenterVertically) {
                HankoSeal(text = game.modifier.kanji, size = 40.dp, color = Iai.Sumi, stampIn = true)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(game.modifier.title.uppercase(), style = MaterialTheme.typography.labelLarge, color = Iai.Shu)
                    Text(game.modifier.description, style = MaterialTheme.typography.bodySmall, color = Iai.SumiSoft)
                }
            }

            game != null -> Text(
                text = "${game.enemiesInWave} inimigos",
                style = MaterialTheme.typography.bodyMedium,
                color = Iai.SumiSoft
            )
        }
    }
}

@Composable
private fun RoundSky(result: HordeRoundResult, state: HordeUiState) {
    val (seal, title, body) = when (result.outcome) {
        HordeOutcome.KILL -> Triple(if (result.perfect) "極" else "斬", if (result.perfect) "IAI PERFEITO!" else "Derrubado!", "")
        HordeOutcome.ENEMY_FOUL -> Triple("勝", "Ele se precipitou!", "")
        HordeOutcome.ARMOR_CRACKED -> Triple("鎧", "Armadura rachada!", "Mais um golpe e ele cai.")
        HordeOutcome.SLOWER -> Triple("傷", "Ferido!", "Ele foi mais rápido.")
        HordeOutcome.TOO_SLOW -> Triple("傷", "Ferido!", "Você hesitou.")
        HordeOutcome.EARLY -> Triple("傷", "Precipitado!", "Você sacou antes do sinal.")
        HordeOutcome.FEINT -> Triple("偽", "Caiu na finta!", "Aquele não era o sinal.")
        HordeOutcome.HALT -> Triple("止", "Desobedeceu o 止!", "Com o selo na tela, não se corta.")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HankoSeal(
            text = seal,
            size = 70.dp,
            color = when {
                result.perfect -> Iai.Coin
                result.playerWon -> Iai.Shu
                else -> Iai.Sumi
            },
            stampIn = true
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = Iai.Sumi)
        if (body.isNotEmpty()) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Iai.SumiSoft, textAlign = TextAlign.Center)
        }
        if (result.points > 0) {
            Text("+${result.points}", fontFamily = BrushFont, fontSize = 34.sp, color = Iai.Shu)
        }
        if (result.healed) {
            Text("+1 vida 命", style = MaterialTheme.typography.titleSmall, color = Iai.Shu)
        }
        val combo = state.game?.combo ?: 0
        if (result.playerWon && combo >= 3) {
            Text("Combo $combo!", style = MaterialTheme.typography.titleSmall, color = Iai.SumiSoft)
        }
    }
}

@Composable
private fun GameOverOverlay(state: HordeUiState, onRetry: () -> Unit, onExit: () -> Unit) {
    val game = state.game ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Iai.Sumi.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        PaperPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HankoSeal(text = "終", size = 92.dp, color = Iai.Sumi, stampIn = true)
                Spacer(Modifier.height(10.dp))
                Text("Fim da horda", style = MaterialTheme.typography.displaySmall, color = Iai.Sumi)
                if (state.newRecord) {
                    Text("NOVO RECORDE!", style = MaterialTheme.typography.titleMedium, color = Iai.Shu)
                }
                Spacer(Modifier.height(8.dp))
                Text("${game.score} pontos", fontFamily = BrushFont, fontSize = 40.sp, color = Iai.Shu)
                Text(
                    text = "Onda ${game.wave} · ${game.kills} inimigos · combo máximo ${game.bestCombo}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Iai.SumiSoft,
                    textAlign = TextAlign.Center
                )
                if (state.coinsEarned > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text("+${state.coinsEarned} 文", style = MaterialTheme.typography.headlineSmall, color = Iai.Coin)
                }
                if (state.newAchievements.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    NewAchievements(state.newAchievements)
                }
                Spacer(Modifier.height(18.dp))
                BrushButton(
                    text = "Jogar de novo",
                    kanji = "再",
                    onClick = onRetry,
                    height = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                BrushButton(
                    text = "Menu",
                    kanji = "戻",
                    onClick = onExit,
                    height = 64.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
