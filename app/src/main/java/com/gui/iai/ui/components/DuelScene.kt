package com.gui.iai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.gui.iai.ui.theme.Iai
import kotlin.random.Random

enum class RightFigure { SAMURAI, MAKIWARA }

/** Estado visual da cena, controlado pela tela. */
data class SceneState(
    val leftPose: SamuraiPose = SamuraiPose.GUARD,
    val rightPose: SamuraiPose = SamuraiPose.GUARD,
    val leftFall: Float = 0f,
    val rightFall: Float = 0f,
    val rightFigure: RightFigure = RightFigure.SAMURAI,
    val leftObi: Color = Iai.Shu,
    val rightObi: Color = Iai.Kin,
    /** 0..1: escurece o céu durante a espera. */
    val tension: Float = 0f,
    /** 0..1: corte do makiwara. */
    val cut: Float = 0f
)

/** Proporção da altura onde fica o horizonte (o chão escuro começa aqui). */
const val HORIZON_FRACTION = 0.70f

/**
 * A cena do duelo ao entardecer: céu, sol vermelho com nuvens de tinta,
 * montanhas em aguada, capim balançando e os dois samurais em silhueta.
 */
@Composable
fun DuelScene(
    state: SceneState,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wind")
    val wind by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2_600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "windSway"
    )

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val horizon = h * HORIZON_FRACTION

        // Céu
        drawRect(
            brush = Brush.verticalGradient(
                0f to lerp(Iai.SkyHigh, Iai.SkyTenseHigh, state.tension),
                1f to lerp(Iai.SkyLow, Iai.SkyTenseLow, state.tension),
                startY = 0f,
                endY = horizon
            )
        )

        // Sol
        val sunRadius = w * 0.30f
        val sunCenter = Offset(w * 0.5f, horizon - h * 0.16f)
        drawCircle(Iai.Sun.copy(alpha = 0.93f), radius = sunRadius, center = sunCenter)

        // Nuvens de tinta cruzando o sol
        val cloudInk = Iai.Sumi.copy(alpha = 0.5f)
        drawLine(
            cloudInk,
            Offset(sunCenter.x - sunRadius * 1.25f, sunCenter.y - sunRadius * 0.34f),
            Offset(sunCenter.x + sunRadius * 0.55f, sunCenter.y - sunRadius * 0.34f),
            strokeWidth = h * 0.010f,
            cap = StrokeCap.Round
        )
        drawLine(
            cloudInk,
            Offset(sunCenter.x - sunRadius * 0.35f, sunCenter.y + sunRadius * 0.08f),
            Offset(sunCenter.x + sunRadius * 1.35f, sunCenter.y + sunRadius * 0.08f),
            strokeWidth = h * 0.007f,
            cap = StrokeCap.Round
        )
        drawLine(
            cloudInk,
            Offset(sunCenter.x - sunRadius * 1.1f, sunCenter.y + sunRadius * 0.46f),
            Offset(sunCenter.x + sunRadius * 0.2f, sunCenter.y + sunRadius * 0.46f),
            strokeWidth = h * 0.005f,
            cap = StrokeCap.Round
        )

        // Montanhas em camadas
        drawPath(mountainPath(w, horizon, h * 0.17f, seed = 1), Iai.MountainFar.copy(alpha = 0.55f))
        drawPath(mountainPath(w, horizon, h * 0.09f, seed = 2), Iai.MountainNear.copy(alpha = 0.8f))

        // Chão
        val ground = Path().apply {
            moveTo(0f, horizon)
            cubicTo(w * 0.3f, horizon - h * 0.012f, w * 0.7f, horizon + h * 0.012f, w, horizon - h * 0.004f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ground, Iai.Sumi)

        // Capim balançando
        val random = Random(11)
        repeat(80) {
            val x = random.nextFloat() * w
            val bladeHeight = h * (0.010f + random.nextFloat() * 0.028f)
            val base = Offset(x, horizon + h * 0.006f)
            val tip = Offset(
                x + wind * bladeHeight * 0.45f + (random.nextFloat() - 0.5f) * bladeHeight * 0.3f,
                base.y - bladeHeight
            )
            drawLine(Iai.Sumi, base, tip, strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
        }

        // Personagens
        val figureHeight = h * 0.24f
        val feetY = horizon + h * 0.016f
        drawSamurai(
            feet = Offset(w * 0.24f, feetY),
            height = figureHeight,
            facingRight = true,
            pose = state.leftPose,
            fall = state.leftFall,
            sway = wind,
            ink = Iai.Sumi,
            obi = state.leftObi,
            highlight = Iai.Washi
        )
        when (state.rightFigure) {
            RightFigure.SAMURAI -> drawSamurai(
                feet = Offset(w * 0.76f, feetY),
                height = figureHeight,
                facingRight = false,
                pose = state.rightPose,
                fall = state.rightFall,
                sway = wind,
                ink = Iai.Sumi,
                obi = state.rightObi,
                highlight = Iai.Washi
            )

            RightFigure.MAKIWARA -> drawMakiwara(
                feet = Offset(w * 0.72f, feetY),
                height = figureHeight,
                cut = state.cut,
                ink = Iai.Sumi,
                rope = Iai.Kin
            )
        }
    }
}

private fun mountainPath(width: Float, baseY: Float, amplitude: Float, seed: Int): Path {
    val random = Random(seed)
    val peaks = 6
    val step = width / peaks
    return Path().apply {
        moveTo(0f, baseY)
        lineTo(0f, baseY - amplitude * 0.35f)
        for (i in 1..peaks) {
            val x = step * i
            val peakY = baseY - amplitude * (0.45f + 0.55f * random.nextFloat())
            val valleyY = baseY - amplitude * (0.15f + 0.3f * random.nextFloat())
            cubicTo(x - step * 0.75f, peakY, x - step * 0.45f, peakY, x - step * 0.3f, (peakY + valleyY) / 2f)
            lineTo(x, valleyY)
        }
        lineTo(width, baseY)
        close()
    }
}

private val UpperCut = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * SLASH_END)
    lineTo(0f, size.height * SLASH_START)
    close()
}

private val LowerCut = GenericShape { size, _ ->
    moveTo(0f, size.height * SLASH_START)
    lineTo(size.width, size.height * SLASH_END)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

private const val SLASH_START = 0.40f
private const val SLASH_END = 0.60f

/**
 * Cena com o efeito de corte: a tela "racha" na diagonal, a linha da
 * lâmina atravessa e um clarão branco pisca.
 *
 * @param split 0..1 deslocamento das metades
 * @param slash 0..1 progresso da linha do corte
 * @param flash 0..1 clarão
 */
@Composable
fun SlashableScene(
    state: SceneState,
    split: Float,
    slash: Float,
    flash: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (slash > 0f && slash < 1.5f) {
                    val start = Offset(0f, size.height * SLASH_START)
                    val end = Offset(size.width, size.height * SLASH_END)
                    val current = Offset(
                        start.x + (end.x - start.x) * slash.coerceAtMost(1f),
                        start.y + (end.y - start.y) * slash.coerceAtMost(1f)
                    )
                    val fade = (1.5f - slash).coerceIn(0f, 1f)
                    drawLine(Color.White.copy(alpha = 0.35f * fade), start, current, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = fade), start, current, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
                if (flash > 0f) drawRect(Color.White.copy(alpha = flash * 0.75f))
            }
    ) {
        if (split > 0.01f) {
            val shift = split * 16f
            DuelScene(
                state = state,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = -shift * density
                        translationY = -shift * 0.45f * density
                    }
                    .clip(UpperCut)
            )
            DuelScene(
                state = state,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = shift * density
                        translationY = shift * 0.45f * density
                    }
                    .clip(LowerCut)
            )
        } else {
            DuelScene(state = state)
        }
    }
}
