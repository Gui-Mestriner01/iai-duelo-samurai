package com.gui.iai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import com.gui.iai.ui.theme.Iai
import kotlin.math.sin
import kotlin.random.Random

private class Petal(
    val x: Float,
    val speed: Float,
    val swayAmplitude: Float,
    val swayFrequency: Float,
    val phase: Float,
    val sizeDp: Float,
    val spin: Float,
    val color: Color,
    val offset: Float
)

/** Pétalas de cerejeira caindo com o vento. */
@Composable
fun SakuraPetals(
    modifier: Modifier = Modifier,
    count: Int = 16,
    wind: Float = 0.35f
) {
    val petals = remember(count) {
        val random = Random(3)
        List(count) {
            Petal(
                x = random.nextFloat(),
                speed = 0.035f + random.nextFloat() * 0.05f,
                swayAmplitude = 10f + random.nextFloat() * 22f,
                swayFrequency = 0.8f + random.nextFloat() * 1.4f,
                phase = random.nextFloat() * 6.28f,
                sizeDp = 4.5f + random.nextFloat() * 4.5f,
                spin = 0.6f + random.nextFloat() * 1.6f,
                color = (if (random.nextBoolean()) Iai.Sakura else Iai.SakuraDeep)
                    .copy(alpha = 0.65f + random.nextFloat() * 0.3f),
                offset = random.nextFloat()
            )
        }
    }
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame -> value = (frame - start) / 1_000_000_000f }
        }
    }
    val density = LocalDensity.current.density

    Canvas(modifier) {
        petals.forEach { petal ->
            val progress = ((petal.offset + time * petal.speed) % 1.15f) - 0.08f
            val y = progress * size.height
            val baseX = ((petal.x + wind * progress * 0.5f) % 1f) * size.width
            val x = baseX + sin(time * petal.swayFrequency + petal.phase) * petal.swayAmplitude * density
            val center = Offset(x, y)
            rotate(degrees = (time * petal.spin + petal.phase) * 57.3f, pivot = center) {
                drawPetal(center, petal.sizeDp * density, petal.color)
            }
        }
    }
}

private fun DrawScope.drawPetal(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - s)
        cubicTo(c.x + s * 0.95f, c.y - s * 0.75f, c.x + s * 0.7f, c.y + s * 0.65f, c.x, c.y + s)
        cubicTo(c.x - s * 0.7f, c.y + s * 0.65f, c.x - s * 0.95f, c.y - s * 0.75f, c.x, c.y - s)
        close()
    }
    drawPath(path, color)
}
