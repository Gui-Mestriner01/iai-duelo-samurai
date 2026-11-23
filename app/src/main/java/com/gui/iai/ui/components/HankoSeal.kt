package com.gui.iai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gui.iai.ui.theme.BrushFont
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Carimbo hanko vermelho com kanji.
 * Com [stampIn], entra "batendo" no papel (grande → tamanho normal).
 */
@Composable
fun HankoSeal(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = Iai.Shu,
    textColor: Color = Iai.Washi,
    rotation: Float = -6f,
    stampIn: Boolean = false
) {
    val scale = remember(text, stampIn) { Animatable(if (stampIn) 2.6f else 1f) }
    val alpha = remember(text, stampIn) { Animatable(if (stampIn) 0f else 1f) }
    LaunchedEffect(text, stampIn) {
        if (stampIn) {
            launch { alpha.animateTo(1f, tween(110)) }
            scale.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    val density = LocalDensity.current
    val chars = text.toList()
    val fontPx = with(density) { size.toPx() } * (if (chars.size <= 1) 0.60f else 0.66f / chars.size)
    val fontSize = with(density) { fontPx.toSp() }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                rotationZ = rotation
            }
            .drawWithCache {
                val random = Random(text.hashCode())
                val spots = List(28) {
                    Offset(random.nextFloat() * this.size.width, random.nextFloat() * this.size.height) to
                        (this.size.width * (0.006f + random.nextFloat() * 0.022f))
                }
                val corner = CornerRadius(this.size.width * 0.12f)
                onDrawBehind {
                    drawRoundRect(color = color, cornerRadius = corner)
                    inset(this.size.width * 0.08f) {
                        drawRoundRect(
                            color = textColor.copy(alpha = 0.85f),
                            cornerRadius = CornerRadius(this.size.width * 0.08f),
                            style = Stroke(width = this.size.width * 0.035f)
                        )
                    }
                    // Falhas de tinta do carimbo
                    spots.forEach { (center, radius) ->
                        drawCircle(textColor.copy(alpha = 0.5f), radius = radius, center = center)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            chars.forEach { char ->
                Text(
                    text = char.toString(),
                    fontFamily = BrushFont,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    color = textColor
                )
            }
        }
    }
}
