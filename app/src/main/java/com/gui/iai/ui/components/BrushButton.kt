package com.gui.iai.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gui.iai.ui.theme.Iai
import kotlin.random.Random

/**
 * Botão desenhado como uma pincelada de tinta (bordas irregulares,
 * ponta com cerdas e falhas de pincel seco).
 */
@Composable
fun BrushButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kanji: String? = null,
    subtitle: String? = null,
    inkColor: Color = Iai.Sumi,
    textColor: Color = Iai.Washi,
    kanjiColor: Color = Iai.ShuBright,
    height: Dp = 76.dp,
    seed: Int = text.hashCode(),
    enabled: Boolean = true,
    centered: Boolean = false
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(110),
        label = "brushButtonScale"
    )

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .drawWithCache {
                val path = brushStrokePath(size, seed)
                val random = Random(seed * 31)
                val streaks = List(6) {
                    val y = size.height * (0.22f + random.nextFloat() * 0.56f)
                    val startX = size.width * (0.30f + random.nextFloat() * 0.35f)
                    Triple(Offset(startX, y), Offset(size.width * 0.97f, y + (random.nextFloat() - 0.5f) * 4f), 0.6f + random.nextFloat() * 1.6f)
                }
                onDrawBehind {
                    drawPath(path, inkColor)
                    streaks.forEach { (start, end, width) ->
                        drawLine(
                            color = textColor.copy(alpha = 0.10f),
                            start = start,
                            end = end,
                            strokeWidth = width * density,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .padding(
                start = if (centered) 12.dp else 26.dp,
                end = if (centered) 16.dp else 30.dp
            ),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start
        ) {
            if (kanji != null) {
                Text(
                    text = kanji,
                    style = MaterialTheme.typography.headlineMedium,
                    color = kanjiColor,
                    maxLines = 1
                )
                Spacer(Modifier.width(14.dp))
            }
            Column {
                Text(
                    text = text.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Contorno de uma pincelada horizontal, sempre igual para a mesma semente. */
fun brushStrokePath(size: Size, seed: Int): Path {
    val random = Random(seed)
    val w = size.width
    val h = size.height
    val left = h * 0.20f
    val steps = 20
    val path = Path()

    path.moveTo(left, h * (0.11f + random.nextFloat() * 0.04f))
    // Borda de cima
    for (i in 1..steps) {
        val t = i / steps.toFloat()
        val x = left + (w * 0.93f - left) * t
        val y = h * (0.06f + random.nextFloat() * 0.06f + 0.05f * t)
        path.lineTo(x, y)
    }
    // Ponta direita com cerdas
    path.lineTo(w, h * (0.20f + random.nextFloat() * 0.06f))
    path.lineTo(w * 0.962f, h * 0.36f)
    path.lineTo(w * 0.992f, h * 0.48f)
    path.lineTo(w * 0.950f, h * 0.60f)
    path.lineTo(w * 0.985f, h * 0.76f)
    path.lineTo(w * 0.920f, h * 0.88f)
    // Borda de baixo
    for (i in steps downTo 0) {
        val t = i / steps.toFloat()
        val x = left * 0.9f + (w * 0.91f - left * 0.9f) * t
        val y = h * (0.86f + random.nextFloat() * 0.07f - 0.03f * t)
        path.lineTo(x, y)
    }
    // Cabeça arredondada da pincelada
    path.cubicTo(-h * 0.04f, h * 0.86f, -h * 0.06f, h * 0.16f, left, h * 0.11f)
    path.close()
    return path
}
