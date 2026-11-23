package com.gui.iai.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform

enum class SamuraiPose {
    /** Mão no cabo, espada na bainha. */
    GUARD,

    /** Espada sacada, braço estendido após o corte. */
    STRIKE
}

/**
 * Desenha um samurai em silhueta de tinta.
 * Coordenadas internas: pés em (0,0), altura ~150, frente para +x.
 *
 * @param feet posição dos pés na tela
 * @param height altura da figura em px
 * @param fall 0 = em pé, 1 = tombando para trás
 * @param sway balanço do vento (-1..1) na fita do chapéu
 */
fun DrawScope.drawSamurai(
    feet: Offset,
    height: Float,
    facingRight: Boolean,
    pose: SamuraiPose,
    fall: Float,
    sway: Float,
    ink: Color,
    obi: Color,
    highlight: Color
) {
    val scale = height / 150f
    val direction = if (facingRight) 1f else -1f
    withTransform({
        translate(feet.x, feet.y)
        rotate(degrees = -64f * fall * direction, pivot = Offset.Zero)
        scale(scaleX = scale * direction, scaleY = scale, pivot = Offset.Zero)
    }) {
        drawSamuraiShape(pose, sway, ink, obi, highlight)
    }
}

private fun DrawScope.drawSamuraiShape(
    pose: SamuraiPose,
    sway: Float,
    ink: Color,
    obi: Color,
    highlight: Color
) {
    val lean = if (pose == SamuraiPose.STRIKE) 7f else 0f

    // Hakama (calça larga)
    val hakama = Path().apply {
        moveTo(-15f, -62f)
        lineTo(15f, -62f)
        lineTo(31f, 0f)
        lineTo(9f, 0f)
        lineTo(2f, -30f)
        lineTo(-5f, 0f)
        lineTo(-30f, 0f)
        close()
    }
    drawPath(hakama, ink)

    // Tronco (kimono)
    val torso = Path().apply {
        moveTo(-15f, -60f)
        lineTo(14f, -60f)
        lineTo(19f + lean, -92f)
        lineTo(11f + lean, -99f)
        lineTo(-12f + lean, -99f)
        lineTo(-20f + lean, -90f)
        close()
    }
    drawPath(torso, ink)

    // Faixa (obi) colorida
    drawLine(obi, Offset(-15.5f, -64f), Offset(15.5f, -64f), strokeWidth = 5f)

    // Cabeça
    drawCircle(ink, radius = 7f, center = Offset(2f + lean, -105f))

    // Chapéu kasa
    val hat = Path().apply {
        moveTo(-25f + lean, -107f)
        lineTo(2f + lean, -125f)
        lineTo(29f + lean, -107f)
        lineTo(25f + lean, -104f)
        lineTo(-21f + lean, -104f)
        close()
    }
    drawPath(hat, ink)

    // Fita do chapéu balançando no vento
    val ribbon = Path().apply {
        moveTo(-17f + lean, -106f)
        cubicTo(
            -30f + lean, -110f + sway * 3f,
            -40f + lean, -101f + sway * 6f,
            -52f + lean, -97f + sway * 10f
        )
    }
    drawPath(ribbon, ink, style = Stroke(width = 2.6f, cap = StrokeCap.Round))

    // Saya (bainha) na cintura, apontando para trás
    drawLine(ink, Offset(-2f, -66f), Offset(-44f, -52f), strokeWidth = 4f, cap = StrokeCap.Round)

    when (pose) {
        SamuraiPose.GUARD -> {
            // Cabo para a frente, pronto para o saque
            drawLine(ink, Offset(4f, -68f), Offset(23f, -77f), strokeWidth = 4.5f, cap = StrokeCap.Round)
            drawCircle(ink, radius = 3.4f, center = Offset(5f, -68f))
            drawLine(obi, Offset(9f, -70f), Offset(20f, -75.5f), strokeWidth = 1.2f)
            // Braços
            drawLine(ink, Offset(11f, -93f), Offset(21f, -77f), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(ink, Offset(-11f, -93f), Offset(-3f, -69f), strokeWidth = 7f, cap = StrokeCap.Round)
        }

        SamuraiPose.STRIKE -> {
            // Braço da frente estendido, braço de trás aberto
            drawLine(ink, Offset(12f + lean, -93f), Offset(42f, -97f), strokeWidth = 7f, cap = StrokeCap.Round)
            drawLine(ink, Offset(-11f + lean, -93f), Offset(-32f, -80f), strokeWidth = 7f, cap = StrokeCap.Round)
            // Cabo e guarda
            drawLine(ink, Offset(37f, -96f), Offset(50f, -100f), strokeWidth = 4.5f, cap = StrokeCap.Round)
            drawCircle(ink, radius = 3.4f, center = Offset(50f, -100f))
            // Lâmina curva
            val blade = Path().apply {
                moveTo(50f, -100f)
                cubicTo(68f, -106f, 90f, -118f, 112f, -136f)
            }
            drawPath(blade, ink, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
            drawPath(blade, highlight, style = Stroke(width = 0.9f, cap = StrokeCap.Round))
        }
    }
}

/**
 * Makiwara: o poste de palha usado no treino.
 * @param cut 0 = inteiro, 1 = parte de cima cortada e caída
 */
fun DrawScope.drawMakiwara(
    feet: Offset,
    height: Float,
    cut: Float,
    ink: Color,
    rope: Color
) {
    val scale = height / 150f
    withTransform({
        translate(feet.x, feet.y)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        // Poste
        drawRect(ink, topLeft = Offset(-4f, -96f), size = Size(8f, 96f))
        // Base
        drawRect(ink, topLeft = Offset(-16f, -6f), size = Size(32f, 6f))
        // Feixe de palha (parte de baixo)
        drawRoundRect(ink, topLeft = Offset(-12f, -96f), size = Size(24f, 40f), cornerRadius = CornerRadius(4f))
        drawLine(rope, Offset(-12f, -66f), Offset(12f, -66f), strokeWidth = 2f)
        drawLine(rope, Offset(-12f, -80f), Offset(12f, -80f), strokeWidth = 2f)

        // Parte de cima: desliza e cai quando é cortada
        translate(cut * 22f, cut * 34f) {
            rotate(degrees = cut * 48f, pivot = Offset(0f, -96f)) {
                drawRoundRect(ink, topLeft = Offset(-12f, -134f), size = Size(24f, 38f), cornerRadius = CornerRadius(4f))
                drawLine(rope, Offset(-12f, -116f), Offset(12f, -116f), strokeWidth = 2f)
                drawLine(ink, Offset(0f, -134f), Offset(0f, -146f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
        }
        if (cut > 0f) {
            drawLine(rope, Offset(-14f, -95f), Offset(14f, -98f), strokeWidth = 1.5f)
        }
    }
}
