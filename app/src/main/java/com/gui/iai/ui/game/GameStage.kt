package com.gui.iai.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.game.RoundPhase
import com.gui.iai.ui.components.HORIZON_FRACTION
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.SceneState
import com.gui.iai.ui.components.SlashableScene
import com.gui.iai.ui.theme.BrushFont
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private val PtBr: Locale = Locale.forLanguageTag("pt-BR")

fun formatSeconds(ms: Long): String = String.format(PtBr, "%.3f s", ms / 1000.0)

fun formatG(g: Float): String = String.format(PtBr, "%.1f G", g)

/** Animações do corte: racha diagonal, linha da lâmina e clarão. */
@Stable
class StrikeEffects {
    val split = Animatable(0f)
    val slash = Animatable(0f)
    val flash = Animatable(0f)

    suspend fun play() = coroutineScope {
        launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(320))
        }
        launch {
            slash.snapTo(0f)
            slash.animateTo(1.5f, tween(560, easing = LinearOutSlowInEasing))
        }
        split.snapTo(0f)
        split.animateTo(1f, tween(70))
        split.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
    }

    suspend fun reset() {
        split.snapTo(0f)
        slash.snapTo(0f)
        flash.snapTo(0f)
    }
}

@Composable
fun rememberStrikeEffects(): StrikeEffects = remember { StrikeEffects() }

/**
 * Mantém a tela acesa durante o jogo e interrompe o golpe se o app
 * for para segundo plano (os sensores param junto).
 */
@Composable
fun GameplayLifecycle(onStop: () -> Unit) {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { onStop() }
}

/**
 * Estrutura comum das telas de jogo:
 * cena ao fundo, cabeçalho, área no céu (topo) e área no chão (base).
 */
@Composable
fun GameStage(
    scene: SceneState,
    effects: StrikeEffects,
    signalVisible: Boolean,
    header: @Composable () -> Unit,
    sky: @Composable BoxScope.() -> Unit,
    ground: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit = {},
    /** Bainha por toque: avisa quando há dedo pressionado na tela. */
    onHoldChange: ((Boolean) -> Unit)? = null,
    /** Mostra o selo 鞘 indicando se o polegar está na tela. */
    holdIndicator: Boolean = false
) {
    var held by remember { mutableStateOf(false) }
    val currentOnHold by rememberUpdatedState(onHoldChange)

    // Ao sair da tela, nenhum dedo fica "preso" como pressionado
    DisposableEffect(Unit) {
        onDispose { currentOnHold?.invoke(false) }
    }

    val holdModifier = if (onHoldChange != null) {
        Modifier.pointerInput(Unit) {
            // Observa TODOS os eventos de toque sem consumir (os botões continuam
            // funcionando) e recalcula a cada evento se há algum dedo na tela.
            awaitPointerEventScope {
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val pressed = event.changes.any { it.pressed }
                        if (pressed != held) {
                            held = pressed
                            currentOnHold?.invoke(pressed)
                        }
                    }
                } finally {
                    held = false
                    currentOnHold?.invoke(false)
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Iai.Sumi)
            .then(holdModifier)
    ) {
        SlashableScene(
            state = scene,
            split = effects.split.value,
            slash = effects.slash.value,
            flash = effects.flash.value
        )

        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            header()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.43f)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 76.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopCenter,
            content = sky
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(1f - HORIZON_FRACTION - 0.015f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
            content = ground
        )

        if (holdIndicator) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 72.dp, end = 18.dp)
            ) {
                HankoSeal(
                    text = "鞘",
                    size = 46.dp,
                    color = if (held) Iai.Shu else Iai.SumiFaded.copy(alpha = 0.35f),
                    rotation = 6f
                )
            }
        }

        if (signalVisible) SignalMark(Modifier.align(Alignment.Center))

        overlay()
    }
}

/** O grande "!" em pincel vermelho. */
@Composable
fun SignalMark(modifier: Modifier = Modifier) {
    val scale = remember { Animatable(2.2f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
    }
    Text(
        text = "!",
        fontFamily = BrushFont,
        fontSize = 220.sp,
        lineHeight = 220.sp,
        color = Iai.Shu,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            rotationZ = -4f
        }
    )
}

/** Texto de cada etapa do golpe, mostrado no céu. */
@Composable
fun PhasePrompt(
    phase: RoundPhase,
    signalMode: SignalMode,
    modifier: Modifier = Modifier,
    touchScabbard: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "prompt")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "promptPulse"
    )
    val (title, body) = when (phase) {
        RoundPhase.SHEATHING -> "Embainhe a lâmina" to
            if (touchScabbard) {
                "Segure o polegar na tela, como a mão no cabo. Só solte no sinal."
            } else {
                "Cubra o topo da tela com a mão ou encoste o celular na perna."
            }

        RoundPhase.PREPARE -> "Prepare-se" to
            "Posicione a lâmina e fique parado."

        RoundPhase.WAITING ->
            (if (signalMode == SignalMode.CLAP) "Aguarde a palma…" else "Aguarde…") to
                "Segure firme. Quem se mexer antes do sinal perde."

        RoundPhase.SIGNAL -> "SAQUE!" to ""
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (phase != RoundPhase.SIGNAL) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = Iai.Sumi.copy(alpha = if (phase == RoundPhase.WAITING) pulse else 1f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Iai.SumiSoft,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Quadradinhos de vitórias (melhor de 3). */
@Composable
fun RoundMarkers(
    wins: Int,
    filledColor: Color,
    modifier: Modifier = Modifier,
    total: Int = 2
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val filled = index < wins
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (filled) filledColor else Color.Transparent)
                    .border(1.5.dp, if (filled) filledColor else Iai.Sumi, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Texto claro para a área do chão (tinta escura). */
@Composable
fun GroundLabel(text: String, modifier: Modifier = Modifier, alpha: Float = 0.7f) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Iai.Washi.copy(alpha = alpha),
        modifier = modifier
    )
}

/** Tempo grande em destaque no chão. */
@Composable
fun GroundTime(label: String, value: String, highlight: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GroundLabel(label)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = if (highlight) Iai.ShuBright else Iai.Washi
        )
    }
}

/** Dica mostrada no chão enquanto o golpe acontece. */
@Composable
fun HoldStillHint(modifier: Modifier = Modifier, touchScabbard: Boolean = false) {
    Text(
        text = if (touchScabbard) {
            "Mantenha o polegar na tela.\nNo sinal, solte e corte o ar!"
        } else {
            "Segure o celular como o cabo de uma espada.\nNo sinal, corte o ar com força."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Iai.Washi.copy(alpha = 0.75f),
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}
