package com.gui.iai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gui.iai.ui.theme.Iai
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ensō: o círculo zen pintado em uma pincelada só.
 * Começa grosso, afina no fim e tem falhas de pincel seco.
 */
@Composable
fun Enso(
    modifier: Modifier = Modifier,
    color: Color = Iai.Sumi,
    strokeWidth: Dp = 16.dp,
    animate: Boolean = true
) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(1_500, easing = FastOutSlowInEasing))
    }

    Canvas(modifier) {
        val base = strokeWidth.toPx()
        val radius = size.minDimension / 2f - base
        val c = center
        val segments = 150
        val sweep = 322.0
        val startDeg = -62.0
        val drawn = (segments * progress.value).toInt()
        val random = Random(9)
        val jitter = FloatArray(segments + 1) { (random.nextFloat() - 0.5f) * base * 0.10f }

        fun point(index: Int, offset: Float = 0f): Offset {
            val t = index / segments.toFloat()
            val angle = Math.toRadians(startDeg + sweep * t)
            val r = radius + jitter[index] + offset
            return Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())
        }

        for (i in 0 until drawn) {
            val t = i / segments.toFloat()
            val thickness = base * (0.55f + 0.6f * sin(PI * (0.12 + 0.88 * t)).toFloat()) * (1f - 0.6f * t * t)
            drawLine(color, point(i), point(i + 1), strokeWidth = thickness, cap = StrokeCap.Round)
        }

        // Pincel seco: riscos de papel aparecendo na parte final
        val dryStart = (segments * 0.55f).toInt()
        for (k in -1..1) {
            for (i in dryStart until drawn) {
                if ((i + k * 7) % 9 < 3) continue
                drawLine(
                    color = Iai.Washi.copy(alpha = 0.45f),
                    start = point(i, k * base * 0.22f),
                    end = point(i + 1, k * base * 0.22f),
                    strokeWidth = base * 0.07f
                )
            }
        }
    }
}
