package com.gui.iai.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.gui.iai.ui.theme.Iai
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class SamuraiPose {
    /** Mão no cabo, espada na bainha. */
    GUARD,

    /** Espada sacada, braço estendido após o corte. */
    STRIKE
}

/**
 * Visual de cada personagem. Todos são silhuetas de tinta, mas cada um tem
 * cabelo, chapéu, armadura e proporções próprias para ser reconhecido de longe.
 */
enum class SamuraiStyle(val heightFactor: Float, val shoulderFactor: Float) {
    /** Você: coque e faixa na testa (hachimaki). */
    PLAYER(1f, 1f),

    /** Taro: cabelo espetado, mais baixo e magro. */
    APPRENTICE(0.94f, 0.92f),

    /** Ronin: chapéu de palha (kasa) e mangas rasgadas. */
    RONIN(1f, 1.03f),

    /** Akane: rabo de cavalo longo com laço. */
    AKANE(0.97f, 0.9f),

    /** Mestre Gensai: barba longa e casaco haori. */
    MASTER(0.98f, 1f),

    /** Oni de Kurogane: elmo com chifres dourados e armadura. */
    ONI(1.08f, 1.18f)
}

/** Cores usadas para desenhar um personagem. */
class SamuraiColors(
    val ink: Color,
    val detail: Color,
    val accent: Color,
    val rim: Color,
    val steel: Color,
    val steelEdge: Color,
    val gold: Color
)

private const val PI_F = PI.toFloat()

/**
 * Desenha um samurai em silhueta de tinta com contraluz do sol.
 * Coordenadas internas: pés em (0,0), altura ~150, frente para +x.
 *
 * @param draw 0 = guarda, 1 = golpe completo (o saque é interpolado)
 * @param crouch 0..1 tensão: o corpo abaixa e se inclina esperando o sinal
 * @param fall 0 = em pé, 0..0.45 cai de joelhos, depois tomba para frente
 * @param wind -1..1 vento nas mangas, fitas e cabelo
 * @param time segundos, para respiração e tremulação
 * @param trail 0..1 rastro brilhante da lâmina logo após o corte
 */
fun DrawScope.drawSamurai(
    feet: Offset,
    height: Float,
    facingRight: Boolean,
    style: SamuraiStyle,
    draw: Float,
    crouch: Float,
    fall: Float,
    wind: Float,
    time: Float,
    trail: Float,
    colors: SamuraiColors
) {
    val scale = height * style.heightFactor / 150f
    val direction = if (facingRight) 1f else -1f
    val kneel = easeOut((fall / 0.45f).coerceIn(0f, 1f))
    val topple = easeIn(((fall - 0.3f) / 0.7f).coerceIn(0f, 1f))
    val rig = buildRig(
        style = style,
        draw = draw,
        crouch = crouch * (1f - kneel),
        kneel = kneel,
        wind = wind,
        time = time + if (facingRight) 0f else 1.7f
    )
    val hatFlying = style == SamuraiStyle.RONIN && fall > 0.15f

    withTransform({
        translate(feet.x, feet.y)
        scale(scaleX = scale * direction, scaleY = scale, pivot = Offset.Zero)
    }) {
        // Sombra no chão
        drawOval(
            Color.Black.copy(alpha = 0.32f * (1f - topple * 0.5f)),
            topLeft = Offset(-36f, -2.6f),
            size = Size(76f + topple * 40f, 5.2f)
        )

        rotate(degrees = 80f * topple, pivot = Offset(rig.frontFoot, 0f)) {
            // Contraluz: a silhueta inteira em laranja, um pouco deslocada para o sol
            translate(1.6f, -0.5f) {
                drawFigure(rig, style, colors, silhouette = true, hatOn = !hatFlying)
            }
            drawFigure(rig, style, colors, silhouette = false, hatOn = !hatFlying)
            drawBlade(rig, colors, trail)
        }

        if (hatFlying) {
            val flight = easeOut(((fall - 0.15f) / 0.85f).coerceIn(0f, 1f))
            val start = rig.head + Offset(1f, -6f)
            val center = Offset(
                start.x - 48f * flight,
                start.y - 34f * sin(PI_F * flight) + (-4f - start.y) * flight
            )
            rotate(degrees = -180f * flight, pivot = center) {
                translate(1.4f, -0.4f) { drawKasa(center, colors.rim, colors.rim, details = false) }
                drawKasa(center, colors.ink, colors.detail, details = true)
            }
        }
    }
}

/** Posição de cada parte do corpo neste quadro. */
private class Rig(
    val l: Float,
    val draw: Float,
    val hipY: Float,
    val shoulderY: Float,
    val lean: Float,
    val frontFoot: Float,
    val backFoot: Float,
    val head: Offset,
    val frontShoulder: Offset,
    val backShoulder: Offset,
    val frontHand: Offset,
    val backHand: Offset,
    val frontElbow: Offset,
    val backElbow: Offset,
    val sayaMouth: Offset,
    val sayaEnd: Offset,
    val tsuba: Offset,
    val pommel: Offset,
    val bladeDir: Offset,
    val bladeVisible: Boolean,
    val sweep: Float,
    val wind: Float,
    val time: Float
)

private fun buildRig(
    style: SamuraiStyle,
    draw: Float,
    crouch: Float,
    kneel: Float,
    wind: Float,
    time: Float
): Rig {
    val l = easeOut(draw.coerceIn(0f, 1f))
    val breathe = sin(time * 2.1f) * (1f - kneel)
    val drop = 4f * crouch + 8f * l + 20f * kneel
    val hipY = -64f + drop
    val shoulderY = hipY - 33f - breathe * 0.7f
    val lean = 2f + 3f * crouch + 12f * l + 4f * kneel
    val sw = style.shoulderFactor
    val head = Offset(3f + lean, shoulderY - 11f - breathe * 0.2f)
    val frontShoulder = Offset(9f * sw + lean, shoulderY + 3f)
    val backShoulder = Offset(-9f * sw + lean, shoulderY + 3f)

    // Bainha: no saque ela é puxada para trás (saya-biki)
    val sayaMouth = lerp(Offset(7f, hipY - 3f), Offset(-1f, hipY + 1f), l)
    val sayaEnd = lerp(Offset(-46f, hipY + 13f), Offset(-56f, hipY + 5f), l)

    // Mão da frente: do cabo, na cintura, até o braço estendido; sobe num arco
    val guardHand = Offset(14f, hipY - 7.5f)
    val strikeHand = Offset(44f + lean * 0.3f, shoulderY + 1f)
    val frontHand = lerp(guardHand, strikeHand, l) + Offset(0f, -7f * sin(PI_F * l))
    val backHand = lerp(Offset(5f, hipY - 2f), Offset(-6f, hipY + 1f), l)

    // Espada: até 22% do saque ainda está na bainha; depois varre de trás para frente
    val bladeVisible = draw > 0.22f
    val sweep = ((draw - 0.22f) / 0.78f).coerceIn(0f, 1f)
    val bladeDir = if (bladeVisible) {
        val phi = PI_F * (1f - sweep)
        Offset(cos(phi), -0.13f - 0.12f * sin(phi))
    } else {
        Offset(-0.87f, 0.5f)
    }
    val axis = normalize(bladeDir)
    val tsuba = frontHand + axis * 5f
    val pommel = frontHand - axis * 14f

    return Rig(
        l = l,
        draw = draw,
        hipY = hipY,
        shoulderY = shoulderY,
        lean = lean,
        frontFoot = 22f + 12f * l - 6f * kneel,
        backFoot = -21f - 10f * l,
        head = head,
        frontShoulder = frontShoulder,
        backShoulder = backShoulder,
        frontHand = frontHand,
        backHand = backHand,
        frontElbow = elbow(frontShoulder, frontHand, bend = 6f * (1f - l) + 1f),
        backElbow = elbow(backShoulder, backHand, bend = 5f),
        sayaMouth = sayaMouth,
        sayaEnd = sayaEnd,
        tsuba = tsuba,
        pommel = pommel,
        bladeDir = bladeDir,
        bladeVisible = bladeVisible,
        sweep = sweep,
        wind = wind,
        time = time
    )
}

/** Cotovelo dobrado para baixo, entre o ombro e a mão. */
private fun elbow(shoulder: Offset, hand: Offset, bend: Float): Offset {
    val mid = (shoulder + hand) / 2f
    val v = hand - shoulder
    val len = v.getDistance().coerceAtLeast(0.001f)
    var perp = Offset(-v.y / len, v.x / len)
    if (perp.y < 0f) perp = -perp
    return mid + perp * bend
}

private fun DrawScope.drawFigure(
    rig: Rig,
    style: SamuraiStyle,
    c: SamuraiColors,
    silhouette: Boolean,
    hatOn: Boolean
) {
    val ink = if (silhouette) c.rim else c.ink
    val accent = if (silhouette) c.rim else c.accent
    val gold = if (silhouette) c.rim else c.gold
    val details = !silhouette
    val flutter = rig.wind * 3f + sin(rig.time * 4.3f) * 1.1f
    val hipY = rig.hipY
    val sy = rig.shoulderY

    // Manga e braço de trás
    drawSleeve(rig.backShoulder, rig.backElbow, flutter, ink, ragged = style == SamuraiStyle.RONIN)
    drawArm(rig.backShoulder, rig.backElbow, rig.backHand, ink)

    // Bainha na cintura, com o cordão (sageo)
    drawLine(ink, rig.sayaMouth, rig.sayaEnd, strokeWidth = 4.6f, cap = StrokeCap.Round)
    if (details) {
        val along = normalize(rig.sayaEnd - rig.sayaMouth)
        drawLine(c.detail, rig.sayaEnd - along * 4f + Offset(0f, -2f), rig.sayaEnd - along * 4f + Offset(0f, 2f), strokeWidth = 1f)
        drawLine(accent, rig.sayaMouth + along * 3f + Offset(0f, -2.2f), rig.sayaMouth + along * 3f + Offset(0f, 2.2f), strokeWidth = 1.2f)
        val cord = Path().apply {
            moveTo(rig.sayaMouth + along * 6f)
            cubicTo(
                rig.sayaMouth + along * 8f + Offset(0f, 8f),
                rig.sayaMouth + along * 14f + Offset(flutter, 12f),
                rig.sayaMouth + along * 16f + Offset(flutter * 1.5f, 8f)
            )
        }
        drawPath(cord, c.detail, style = Stroke(width = 1f, cap = StrokeCap.Round))
    }

    // Hakama (calça larga), com a barra de trás balançando no vento
    val ff = rig.frontFoot
    val bf = rig.backFoot
    val crotch = Offset(2f + 3f * rig.l, hipY + (0f - hipY) * 0.64f)
    val hakama = Path().apply {
        moveTo(-14f, hipY)
        lineTo(14f, hipY)
        cubicTo(20f, hipY + 20f, ff + 4f, -24f, ff + 12f + rig.wind * 2f, 0f)
        lineTo(ff - 15f, 0f)
        cubicTo(ff - 15f, -7f, crotch.x + 4f, crotch.y + 3f, crotch.x, crotch.y)
        cubicTo(crotch.x - 4f, crotch.y + 3f, bf + 15f, -7f, bf + 15f, 0f)
        lineTo(bf - 12f + flutter * 1.4f, 0f)
        cubicTo(bf - 9f, -24f, -21f, hipY + 18f, -14f, hipY)
        close()
    }
    drawPath(hakama, ink)
    if (details) {
        // Pregas
        drawLine(c.detail, Offset(6f, hipY + 4f), Offset(ff + 2f, -3f), strokeWidth = 1f)
        drawLine(c.detail, Offset(-1f, hipY + 4f), Offset(crotch.x + 1f, crotch.y - 2f), strokeWidth = 1f)
        drawLine(c.detail, Offset(-8f, hipY + 4f), Offset(bf - 2f + flutter, -3f), strokeWidth = 1f)
    }

    // Casaco haori do mestre
    if (style == SamuraiStyle.MASTER) {
        val haori = Path().apply {
            moveTo(rig.backShoulder.x - 2f, sy + 4f)
            cubicTo(-22f, hipY - 6f, -26f - flutter, hipY + 14f, -22f - flutter, hipY + 24f)
            lineTo(16f + rig.lean * 0.4f, hipY + 21f)
            cubicTo(18f + rig.lean * 0.4f, hipY + 6f, rig.frontShoulder.x + 5f, sy + 14f, rig.frontShoulder.x + 3f, sy + 5f)
            close()
        }
        drawPath(haori, ink)
        if (details) drawLine(c.detail, Offset(rig.head.x - 1f, sy + 2f), Offset(4f + rig.lean * 0.3f, hipY + 20f), strokeWidth = 1.1f)
    }

    // Tronco (kimono)
    val fs = rig.frontShoulder
    val bs = rig.backShoulder
    val torso = Path().apply {
        moveTo(-14f, hipY + 2f)
        lineTo(14f, hipY + 2f)
        cubicTo(16f + rig.lean * 0.5f, hipY - 10f, fs.x + 5f, sy + 12f, fs.x + 2f, sy + 1f)
        cubicTo(fs.x - 2f, sy - 3f, rig.head.x + 4f, sy - 3f, rig.head.x + 1f, sy - 2f)
        cubicTo(rig.head.x - 4f, sy - 3f, bs.x + 2f, sy - 3f, bs.x - 2f, sy + 1f)
        cubicTo(bs.x - 6f, sy + 10f, -18f, hipY - 8f, -14f, hipY + 2f)
        close()
    }
    drawPath(torso, ink)
    if (details) {
        // Gola cruzada do kimono
        val v = Offset(2f + rig.lean * 0.6f, sy + 15f)
        drawLine(c.detail, Offset(rig.head.x - 3f, sy - 1f), v, strokeWidth = 1.3f)
        drawLine(c.detail, Offset(rig.head.x + 4f, sy - 1f), v, strokeWidth = 1.3f)
    }

    // Faixa (obi) e as pontas do laço nas costas
    val obi = Path().apply {
        moveTo(-15f, hipY - 3f)
        lineTo(15f, hipY - 4f)
        lineTo(15f, hipY + 3f)
        lineTo(-15f, hipY + 3.5f)
        close()
    }
    drawPath(obi, accent)
    val knot = Path().apply {
        moveTo(-14f, hipY)
        cubicTo(-18f, hipY + 3f, -20f - flutter, hipY + 8f, -23f - flutter * 1.3f, hipY + 12f)
        moveTo(-14f, hipY + 1f)
        cubicTo(-17f, hipY + 5f, -17f - flutter * 0.6f, hipY + 10f, -18f - flutter, hipY + 15f)
    }
    drawPath(knot, accent, style = Stroke(width = 2.2f, cap = StrokeCap.Round))

    // Armadura do Oni: ombreiras e saiote de placas
    if (style == SamuraiStyle.ONI) {
        drawArmorPlate(bs, ink, c.detail, gold, details)
        val skirt = Path().apply {
            moveTo(-16f, hipY + 2f)
            lineTo(16f, hipY + 2f)
            lineTo(20f, hipY + 18f)
            lineTo(-21f, hipY + 18f)
            close()
        }
        drawPath(skirt, ink)
        if (details) {
            for (i in 1..2) {
                val y = hipY + 2f + 5.5f * i
                drawLine(gold, Offset(-17f - i, y), Offset(17f + i, y), strokeWidth = 0.8f)
            }
        }
    }

    // Cabo da espada (tsuka) com a guarda (tsuba)
    drawLine(ink, rig.tsuba, rig.pommel, strokeWidth = 4.2f, cap = StrokeCap.Round)
    drawCircle(ink, radius = 3.3f, center = rig.tsuba)
    if (details) {
        val axis = normalize(rig.pommel - rig.tsuba)
        val side = Offset(-axis.y, axis.x)
        for (i in 1..3) {
            val p = rig.tsuba + axis * (3.5f * i + 1f)
            drawLine(c.detail, p - side * 1.6f + axis * 1.2f, p + side * 1.6f - axis * 1.2f, strokeWidth = 0.7f)
            drawLine(c.detail, p - side * 1.6f - axis * 1.2f, p + side * 1.6f + axis * 1.2f, strokeWidth = 0.7f)
        }
        drawCircle(gold, radius = 3.3f, center = rig.tsuba, style = Stroke(width = 0.8f))
    }

    // Cabeça e pescoço
    drawLine(ink, Offset(rig.head.x - 1f, sy), rig.head + Offset(-1f, 4f), strokeWidth = 6.4f, cap = StrokeCap.Round)
    drawHead(rig, style, ink, accent, gold, c, details, hatOn)

    // Manga e braço da frente por cima de tudo
    if (style == SamuraiStyle.ONI) drawArmorPlate(fs, ink, c.detail, gold, details)
    drawSleeve(fs, rig.frontElbow, flutter, ink, ragged = style == SamuraiStyle.RONIN)
    drawArm(fs, rig.frontElbow, rig.frontHand, ink)
}

private fun DrawScope.drawArm(shoulder: Offset, elbow: Offset, hand: Offset, ink: Color) {
    drawLine(ink, shoulder, elbow, strokeWidth = 6.8f, cap = StrokeCap.Round)
    drawLine(ink, elbow, hand, strokeWidth = 5.8f, cap = StrokeCap.Round)
    drawCircle(ink, radius = 3.3f, center = hand)
}

/** Manga larga do kimono, pendurada no braço e balançando. */
private fun DrawScope.drawSleeve(shoulder: Offset, elbow: Offset, flutter: Float, ink: Color, ragged: Boolean) {
    val bottomA = elbow + Offset(-2f - flutter * 0.6f, 15f)
    val bottomB = shoulder + Offset(-9f - flutter, 15f)
    val sleeve = Path().apply {
        moveTo(shoulder.x - 4f, shoulder.y - 2f)
        lineTo(elbow.x + 1f, elbow.y - 2f)
        cubicTo(elbow.x + 2f, elbow.y + 6f, bottomA.x + 2f, bottomA.y - 3f, bottomA.x, bottomA.y)
        if (ragged) {
            val mid = (bottomA + bottomB) / 2f
            lineTo(mid.x + 2f, mid.y - 4f)
            lineTo(mid.x - 1f, mid.y + 2f)
            lineTo(mid.x - 3f, mid.y - 3f)
        }
        lineTo(bottomB.x, bottomB.y)
        cubicTo(bottomB.x - 1f, bottomB.y - 8f, shoulder.x - 9f, shoulder.y + 4f, shoulder.x - 4f, shoulder.y - 2f)
        close()
    }
    drawPath(sleeve, ink)
}

private fun DrawScope.drawArmorPlate(shoulder: Offset, ink: Color, detail: Color, gold: Color, details: Boolean) {
    val plate = Path().apply {
        moveTo(shoulder.x - 7f, shoulder.y - 3f)
        lineTo(shoulder.x + 8f, shoulder.y - 2f)
        lineTo(shoulder.x + 9f, shoulder.y + 14f)
        lineTo(shoulder.x - 6f, shoulder.y + 16f)
        close()
    }
    drawPath(plate, ink)
    if (details) {
        for (i in 1..3) {
            val y = shoulder.y - 2f + 4.2f * i
            drawLine(gold, Offset(shoulder.x - 6.5f, y + 0.3f * i), Offset(shoulder.x + 8.5f, y), strokeWidth = 0.7f)
        }
        drawLine(detail, Offset(shoulder.x + 0.5f, shoulder.y - 2f), Offset(shoulder.x + 1.5f, shoulder.y + 15f), strokeWidth = 0.6f)
    }
}

private fun DrawScope.drawHead(
    rig: Rig,
    style: SamuraiStyle,
    ink: Color,
    accent: Color,
    gold: Color,
    c: SamuraiColors,
    details: Boolean,
    hatOn: Boolean
) {
    val h = rig.head
    val flutter = rig.wind * 3f + sin(rig.time * 5.1f) * 1.4f

    // Cabeça com nariz e queixo de perfil
    drawCircle(ink, radius = 6.8f, center = h)
    val face = Path().apply {
        moveTo(h.x + 5.6f, h.y - 1.5f)
        lineTo(h.x + 8.4f, h.y + 1.6f)
        lineTo(h.x + 6.2f, h.y + 2.8f)
        lineTo(h.x + 5.4f, h.y + 6.2f)
        lineTo(h.x + 1f, h.y + 6.6f)
        close()
    }
    drawPath(face, ink)

    when (style) {
        SamuraiStyle.PLAYER -> {
            drawTopknot(h, ink)
            drawLine(accent, Offset(h.x - 6.8f, h.y - 3.2f), Offset(h.x + 6.4f, h.y - 4.4f), strokeWidth = 2.4f)
            drawRibbon(Offset(h.x - 6.5f, h.y - 3.2f), length = 17f, flutter = flutter, color = accent, width = 1.8f)
            drawRibbon(Offset(h.x - 6.5f, h.y - 2.4f), length = 12f, flutter = flutter * 1.3f, color = accent, width = 1.5f, drop = 5f)
        }

        SamuraiStyle.APPRENTICE -> {
            val hair = Path().apply {
                moveTo(h.x - 7f, h.y + 1f)
                lineTo(h.x - 12f - flutter * 0.4f, h.y - 2f)
                lineTo(h.x - 7f, h.y - 4f)
                lineTo(h.x - 10f - flutter * 0.3f, h.y - 9f)
                lineTo(h.x - 3f, h.y - 7f)
                lineTo(h.x - 3f, h.y - 12f)
                lineTo(h.x + 1f, h.y - 7.5f)
                lineTo(h.x + 5f, h.y - 10f)
                lineTo(h.x + 5f, h.y - 5f)
                lineTo(h.x + 8f, h.y - 4f)
                lineTo(h.x + 3f, h.y - 1f)
                close()
            }
            drawPath(hair, ink)
        }

        SamuraiStyle.RONIN -> {
            if (hatOn) {
                drawKasa(h + Offset(1f, -6f), ink, c.detail, details)
                drawRibbon(Offset(h.x - 17f, h.y - 4f), length = 22f, flutter = flutter, color = ink, width = 2.4f)
                if (details) drawLine(c.detail, Offset(h.x + 5f, h.y - 2f), Offset(h.x + 3f, h.y + 6f), strokeWidth = 0.8f)
            } else {
                drawTopknot(h, ink)
            }
        }

        SamuraiStyle.AKANE -> {
            drawCircle(ink, radius = 7.4f, center = h + Offset(-0.8f, -0.8f))
            // Rabo de cavalo longo, afinando e ondulando no vento
            val root = h + Offset(-4f, -7f)
            val steps = 10
            val upper = ArrayList<Offset>(steps + 1)
            val lower = ArrayList<Offset>(steps + 1)
            for (i in 0..steps) {
                val k = i / steps.toFloat()
                val wave = sin(rig.time * 4f - k * 5f) * 3.5f * k + flutter * k * 1.5f
                val center = root + Offset(-40f * k, 6f + 22f * k * k + wave)
                val thick = 3.6f * (1f - k) + 0.3f
                upper += center + Offset(0f, -thick)
                lower += center + Offset(0f, thick)
            }
            val tail = Path().apply {
                moveTo(upper[0])
                for (i in 1..steps) lineTo(upper[i])
                for (i in steps downTo 0) lineTo(lower[i])
                close()
            }
            drawPath(tail, ink)
            // Laço
            drawCircle(accent, radius = 2.2f, center = root + Offset(-1f, 1f))
            drawRibbon(root + Offset(-1f, 1f), length = 12f, flutter = flutter, color = accent, width = 1.6f, drop = 7f)
        }

        SamuraiStyle.MASTER -> {
            drawTopknot(h, ink)
            val beard = Path().apply {
                moveTo(h.x + 5.8f, h.y + 3f)
                cubicTo(h.x + 7f, h.y + 10f, h.x + 4f + flutter * 0.4f, h.y + 16f, h.x + 2f + flutter * 0.8f, h.y + 21f)
                cubicTo(h.x + 0.5f, h.y + 14f, h.x - 1f, h.y + 9f, h.x - 1f, h.y + 4f)
                close()
            }
            drawPath(beard, ink)
            if (details) {
                drawLine(c.detail, Offset(h.x + 3f, h.y + 7f), Offset(h.x + 2.5f + flutter * 0.6f, h.y + 17f), strokeWidth = 0.6f)
            }
        }

        SamuraiStyle.ONI -> {
            // Proteção do pescoço (shikoro), elmo e os chifres dourados (maedate)
            val shikoro = Path().apply {
                moveTo(h.x - 6f, h.y - 3f)
                lineTo(h.x - 15f, h.y + 6f)
                lineTo(h.x - 11f, h.y + 10f)
                lineTo(h.x - 1f, h.y + 3f)
                close()
            }
            drawPath(shikoro, ink)
            val helmet = Path().apply {
                moveTo(h.x - 8.5f, h.y - 1f)
                cubicTo(h.x - 8.5f, h.y - 10f, h.x + 7f, h.y - 13f, h.x + 8.5f, h.y - 2f)
                lineTo(h.x + 9.5f, h.y - 0.5f)
                lineTo(h.x - 9f, h.y + 0.5f)
                close()
            }
            drawPath(helmet, ink)
            val horns = Path().apply {
                moveTo(h.x + 3f, h.y - 8f)
                cubicTo(h.x - 2f, h.y - 13f, h.x - 7f, h.y - 18f, h.x - 7f, h.y - 25f)
                moveTo(h.x + 3f, h.y - 8f)
                cubicTo(h.x + 9f, h.y - 12f, h.x + 15f, h.y - 16f, h.x + 17f, h.y - 23f)
            }
            drawPath(horns, gold, style = Stroke(width = 2f, cap = StrokeCap.Round))
            if (details) {
                drawCircle(Iai.ShuBright, radius = 1f, center = Offset(h.x + 4.6f, h.y - 0.4f))
                drawLine(gold, Offset(h.x - 8.5f, h.y - 0.6f), Offset(h.x + 9f, h.y - 1.6f), strokeWidth = 0.8f)
            }
        }
    }
}

private fun DrawScope.drawTopknot(h: Offset, ink: Color) {
    drawOval(ink, topLeft = Offset(h.x - 6.5f, h.y - 11.5f), size = Size(7f, 5f))
    drawLine(ink, Offset(h.x - 3f, h.y - 9f), Offset(h.x + 1f, h.y - 6f), strokeWidth = 2.4f, cap = StrokeCap.Round)
}

/** Fita fina balançando para trás com o vento. */
private fun DrawScope.drawRibbon(
    root: Offset,
    length: Float,
    flutter: Float,
    color: Color,
    width: Float,
    drop: Float = 2f
) {
    val ribbon = Path().apply {
        moveTo(root)
        cubicTo(
            root + Offset(-length * 0.35f, -1f + flutter * 0.3f),
            root + Offset(-length * 0.7f, drop * 0.5f + flutter * 0.7f),
            root + Offset(-length, drop + flutter)
        )
    }
    drawPath(ribbon, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** Chapéu cônico de palha (kasa). */
private fun DrawScope.drawKasa(center: Offset, ink: Color, detail: Color, details: Boolean) {
    val apex = center + Offset(0f, -12f)
    val hat = Path().apply {
        moveTo(center.x - 26f, center.y + 3f)
        cubicTo(center.x - 14f, center.y - 3f, apex.x - 5f, apex.y + 1f, apex.x, apex.y)
        cubicTo(apex.x + 5f, apex.y + 1f, center.x + 16f, center.y - 3f, center.x + 28f, center.y + 3f)
        lineTo(center.x + 24f, center.y + 5f)
        lineTo(center.x - 22f, center.y + 5f)
        close()
    }
    drawPath(hat, ink)
    if (details) {
        for (i in -2..2) {
            drawLine(detail, apex + Offset(0f, 1f), center + Offset(i * 9.5f, 4f), strokeWidth = 0.7f)
        }
    }
}

/** Lâmina curva de aço e o rastro brilhante do corte horizontal. */
private fun DrawScope.drawBlade(rig: Rig, c: SamuraiColors, trail: Float) {
    if (!rig.bladeVisible) {
        // Lâmina começando a sair da bainha
        if (rig.draw > 0.02f) {
            drawLine(c.steel, rig.tsuba, rig.sayaMouth, strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
        return
    }

    if (trail > 0.01f) {
        val steps = 20
        val upper = ArrayList<Offset>(steps + 1)
        val lower = ArrayList<Offset>(steps + 1)
        for (i in 0..steps) {
            val q = rig.sweep * i / steps
            val phi = PI_F * (1f - q)
            val tip = rig.tsuba + Offset(cos(phi), -0.13f - 0.12f * sin(phi)) * 74f
            val thick = 7f * sin(phi) * (i / steps.toFloat()) + 0.4f
            upper += tip + Offset(0f, -thick * 0.5f)
            lower += tip + Offset(0f, thick * 0.5f)
        }
        val arc = Path().apply {
            moveTo(upper[0])
            for (i in 1..steps) lineTo(upper[i])
            for (i in steps downTo 0) lineTo(lower[i])
            close()
        }
        drawPath(arc, Color.White.copy(alpha = 0.55f * trail))
    }

    val d = rig.bladeDir * 72f
    val length = d.getDistance()
    if (length < 1f) return
    val u = d / length
    var spine = Offset(-u.y, u.x)
    if (spine.y > 0f) spine = -spine
    val tip = rig.tsuba + d
    val curve = length * 0.06f
    val blade = Path().apply {
        moveTo(rig.tsuba + spine * 1.5f)
        cubicTo(
            rig.tsuba + d * 0.35f + spine * (curve + 1.3f),
            rig.tsuba + d * 0.75f + spine * (curve + 0.8f),
            tip
        )
        cubicTo(
            rig.tsuba + d * 0.75f + spine * (curve - 1f),
            rig.tsuba + d * 0.35f + spine * (curve - 1.4f),
            rig.tsuba - spine * 1.3f
        )
        close()
    }
    drawPath(blade, c.steel)
    val spineLine = Path().apply {
        moveTo(rig.tsuba + spine * 1.4f)
        cubicTo(
            rig.tsuba + d * 0.35f + spine * (curve + 1.2f),
            rig.tsuba + d * 0.75f + spine * (curve + 0.7f),
            tip
        )
    }
    drawPath(spineLine, c.steelEdge, style = Stroke(width = 0.8f, cap = StrokeCap.Round))
}

/**
 * Makiwara: o poste de palha usado no treino.
 * @param cut 0 = inteiro, 1 = parte de cima cortada na diagonal e caída
 */
fun DrawScope.drawMakiwara(
    feet: Offset,
    height: Float,
    cut: Float,
    wind: Float,
    colors: SamuraiColors
) {
    val scale = height / 150f
    withTransform({
        translate(feet.x, feet.y)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        drawOval(Color.Black.copy(alpha = 0.32f), topLeft = Offset(-26f, -2.6f), size = Size(52f, 5.2f))
        translate(1.4f, -0.4f) { drawMakiwaraShape(cut, wind, colors, silhouette = true) }
        drawMakiwaraShape(cut, wind, colors, silhouette = false)

        // Lascas de palha voando do corte
        if (cut > 0f && cut < 1f) {
            val origin = Offset(0f, -96f)
            for (i in 0 until 12) {
                val angle = -2.6f + hash(i) * 2.4f
                val speed = 30f + 40f * hash(i + 40)
                val pos = origin + Offset(cos(angle), sin(angle)) * (speed * cut) + Offset(wind * 8f * cut, 70f * cut * cut)
                val spin = angle + cut * 9f * (hash(i + 80) - 0.5f)
                val half = Offset(cos(spin), sin(spin)) * 3f
                drawLine(colors.gold.copy(alpha = 1f - cut), pos - half, pos + half, strokeWidth = 1f, cap = StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawMakiwaraShape(cut: Float, wind: Float, c: SamuraiColors, silhouette: Boolean) {
    val ink = if (silhouette) c.rim else c.ink
    val rope = if (silhouette) c.rim else c.gold
    val details = !silhouette

    // Base em cruz e poste
    val base = Path().apply {
        moveTo(-22f, 0f)
        lineTo(-15f, -7f)
        lineTo(15f, -7f)
        lineTo(22f, 0f)
        close()
    }
    drawPath(base, ink)
    drawRect(ink, topLeft = Offset(-4f, -60f), size = Size(8f, 54f))
    if (details) {
        drawLine(c.detail, Offset(-1.5f, -58f), Offset(-1f, -10f), strokeWidth = 0.7f)
        drawLine(c.detail, Offset(1.8f, -50f), Offset(2.2f, -14f), strokeWidth = 0.7f)
    }

    // Feixe de palha de baixo, com o topo cortado na diagonal
    val lower = Path().apply {
        moveTo(-12f, -56f)
        lineTo(-12f, if (cut > 0f) -92f else -134f)
        lineTo(12f, if (cut > 0f) -100f else -134f)
        lineTo(12f, -56f)
        close()
    }
    drawPath(lower, ink)
    if (details) drawStraw(-56f, if (cut > 0f) -92f else -132f, c.detail)
    drawLine(rope, Offset(-12.5f, -64f), Offset(12.5f, -66f), strokeWidth = 2f)
    drawLine(rope, Offset(-12.5f, -80f), Offset(12.5f, -82f), strokeWidth = 2f)

    if (cut <= 0f) {
        drawLine(rope, Offset(-12.5f, -116f), Offset(12.5f, -118f), strokeWidth = 2f)
        drawLine(ink, Offset(0f, -134f), Offset(0f, -146f), strokeWidth = 3f, cap = StrokeCap.Round)
        return
    }

    // Parte de cima escorrega pelo corte e cai
    translate(28f * cut, 96f * cut * cut) {
        rotate(degrees = 82f * cut, pivot = Offset(12f, -100f)) {
            val upper = Path().apply {
                moveTo(-12f, -92f)
                lineTo(12f, -100f)
                lineTo(12f, -130f)
                cubicTo(12f, -134f, 9f, -136f, 6f, -136f)
                lineTo(-6f, -136f)
                cubicTo(-9f, -136f, -12f, -134f, -12f, -130f)
                close()
            }
            drawPath(upper, ink)
            if (details) drawStraw(-96f, -134f, c.detail)
            drawLine(rope, Offset(-12.5f, -116f), Offset(12.5f, -118f), strokeWidth = 2f)
            drawLine(ink, Offset(0f + wind * cut, -136f), Offset(0f, -146f), strokeWidth = 3f, cap = StrokeCap.Round)
            if (details) drawLine(c.steel.copy(alpha = 0.7f), Offset(-12f, -92f), Offset(12f, -100f), strokeWidth = 1.2f)
        }
    }
    if (details) drawLine(c.steel.copy(alpha = 0.7f), Offset(-12f, -92f), Offset(12f, -100f), strokeWidth = 1.2f)
}

private fun DrawScope.drawStraw(bottom: Float, top: Float, color: Color) {
    for (i in -2..2) {
        val x = i * 4.5f
        drawLine(color, Offset(x, bottom - 2f), Offset(x + 0.6f, top + 3f), strokeWidth = 0.6f)
    }
}

// ---- utilitários -----------------------------------------------------------

private fun easeOut(x: Float): Float = 1f - (1f - x) * (1f - x) * (1f - x)

private fun easeIn(x: Float): Float = x * x

private fun normalize(v: Offset): Offset {
    val len = v.getDistance()
    return if (len < 0.0001f) Offset(1f, 0f) else v / len
}

/** Número pseudoaleatório estável (0..1) a partir de um índice. */
private fun hash(i: Int): Float {
    val x = sin(i * 12.9898f + 78.233f) * 43758.547f
    return x - kotlin.math.floor(x)
}

private fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)

private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)

private fun Path.cubicTo(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
