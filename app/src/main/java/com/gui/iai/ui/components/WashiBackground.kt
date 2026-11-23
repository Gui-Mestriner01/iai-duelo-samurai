package com.gui.iai.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.gui.iai.ui.theme.Iai
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Fiber(val start: Offset, val end: Offset, val alpha: Float, val width: Float)
private data class Stain(val center: Offset, val radius: Float, val alpha: Float)

/** Fundo de papel washi: fibras, manchas suaves e vinheta. Desenho estático. */
@Composable
fun WashiBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val random = Random(42)
                val unit = size.minDimension / 400f
                val fibers = List(260) {
                    val start = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
                    val length = (6f + random.nextFloat() * 34f) * unit
                    val angle = random.nextFloat() * 6.283f
                    Fiber(
                        start = start,
                        end = Offset(start.x + cos(angle) * length, start.y + sin(angle) * length),
                        alpha = 0.03f + random.nextFloat() * 0.06f,
                        width = (0.5f + random.nextFloat()) * unit
                    )
                }
                val stains = List(7) {
                    Stain(
                        center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
                        radius = (60f + random.nextFloat() * 160f) * unit,
                        alpha = 0.25f + random.nextFloat() * 0.35f
                    )
                }
                onDrawBehind {
                    drawRect(Iai.Washi)
                    stains.forEach { stain ->
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Iai.WashiDeep.copy(alpha = stain.alpha), Color.Transparent),
                                center = stain.center,
                                radius = stain.radius
                            ),
                            radius = stain.radius,
                            center = stain.center
                        )
                    }
                    fibers.forEach { fiber ->
                        drawLine(
                            color = Iai.SumiSoft.copy(alpha = fiber.alpha),
                            start = fiber.start,
                            end = fiber.end,
                            strokeWidth = fiber.width,
                            cap = StrokeCap.Round
                        )
                    }
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.Transparent, Iai.WashiShadow.copy(alpha = 0.55f)),
                            center = center,
                            radius = size.maxDimension * 0.78f
                        )
                    )
                }
            },
        content = content
    )
}
