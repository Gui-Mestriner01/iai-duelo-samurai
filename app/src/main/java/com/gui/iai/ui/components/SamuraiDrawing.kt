package com.gui.iai.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp as lerpColor
import com.gui.iai.ui.theme.Iai
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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
    ONI(1.08f, 1.18f),

    /** Yuki-Onna: cabelo longuíssimo solto ao vento e grampo de gelo. */
    YUKI(0.98f, 0.88f),

    /** Tengu: máscara de nariz comprido, chapéu tokin e asas de corvo. */
    TENGU(1.02f, 1.05f),

    /** Kage: capuz, olhos que brilham, cachecol longo e armadura escura. */
    KAGE(1.05f, 1.08f),

    /** Ashigaru (Hordas): chapéu jingasa achatado. */
    ASHIGARU(0.96f, 0.98f)
}

/** Cores usadas para desenhar um personagem. */
class SamuraiColors(
    val ink: Color,
    val detail: Color,
    val accent: Color,
    val rim: Color,
    val steel: Color,
    val steelEdge: Color,
    val gold: Color,
    /** Cor do rastro da lâmina no corte. */
    val trail: Color = Color.White
)

private const val PI_F = PI.toFloat()

/**
 * Desenha um samurai em silhueta de tinta com contraluz do sol.
 * Coordenadas internas: pés em (0,0), altura ~150, frente para +x.
 *
 * @param strikeMs tempo desde o início do golpe em ms (negativo = em guarda); a pose
 * vem da linha do tempo em [strikePose]
 * @param crouch 0..1 tensão: o corpo abaixa e se inclina esperando o sinal
 * @param fall 0 = em pé; tranco do corte, depois cai de joelhos e por fim tomba
 * @param wind -1..1 vento nas mangas, fitas e cabelo
 * @param time segundos da cena (respeita câmera lenta e hit-stop)
 * @param physics estado do tecido e do cabelo entre quadros; sem ele, tudo fica em repouso
 */
fun DrawScope.drawSamurai(
    feet: Offset,
    height: Float,
    facingRight: Boolean,
    style: SamuraiStyle,
    strikeMs: Float,
    crouch: Float,
    fall: Float,
    wind: Float,
    time: Float,
    colors: SamuraiColors,
    physics: FigurePhysics? = null
) {
    val phys = physics ?: FigurePhysics()
    val scale = height * style.heightFactor / 150f
    val direction = if (facingRight) 1f else -1f

    // Queda em três tempos: tranco do golpe, joelhos no chão, tombo
    val recoil = sin(PI_F * (fall / 0.18f).coerceIn(0f, 1f))
    val kneel = easeOutCubic((fall - 0.12f) / 0.4f)
    val topple = easeIn(((fall - 0.4f) / 0.6f).coerceIn(0f, 1f))
    // O Oni, pesado de armadura, cai para trás; os outros tombam para frente
    val backward = style == SamuraiStyle.ONI || style == SamuraiStyle.KAGE

    val figureTime = time + if (facingRight) 0f else 1.7f
    val rig = buildRig(style, strikePose(strikeMs), crouch * (1f - kneel), kneel, recoil, wind, figureTime)
    val hatFlying = style == SamuraiStyle.RONIN && fall > 0.15f
    phys.advance(time)
    val cloth = stepCloth(rig, style, phys, wind, figureTime, hatOn = !hatFlying)
    val trail = if (strikeMs >= 0f) strikeTrail(strikeMs) else 0f

    withTransform({
        translate(feet.x, feet.y)
        scale(scaleX = scale * direction, scaleY = scale, pivot = Offset.Zero)
    }) {
        // Sombra no chão
        drawOval(
            Color.Black.copy(alpha = 0.32f * (1f - topple * 0.5f)),
            topLeft = Offset(-36f - (if (backward) 30f * topple else 0f), -2.6f),
            size = Size(76f + topple * 40f, 5.2f)
        )

        val pivot = Offset(if (backward) rig.backFoot else rig.frontFoot, 0f)
        rotate(degrees = (if (backward) -72f else 80f) * topple, pivot = pivot) {
            // Rastros fantasma durante o saque, como num anime
            if (strikeMs in 80f..300f) {
                for (g in 2 downTo 1) {
                    val ghost = buildRig(style, strikePose(strikeMs - 26f * g), crouch, 0f, 0f, wind, figureTime)
                    drawGhost(ghost, style, colors, cloth, phys, alpha = 0.22f / g)
                }
            }

            // Contraluz em dois tons: borda laranja e uma faixa de meia-luz por dentro,
            // o que dá volume à silhueta sem perder o estilo de tinta
            translate(1.6f, -0.5f) {
                drawFigure(rig, style, colors, tint = colors.rim, hatOn = !hatFlying, cloth = cloth, phys = phys)
            }
            translate(0.7f, -0.2f) {
                drawFigure(rig, style, colors, tint = lerpColor(colors.ink, colors.rim, 0.38f), hatOn = !hatFlying, cloth = cloth, phys = phys)
            }
            drawFigure(rig, style, colors, tint = null, hatOn = !hatFlying, cloth = cloth, phys = phys)
            drawBlade(rig, colors, trail)
        }

        // Respingos no corte: tinta vermelha, ou faíscas na armadura do Oni
        drawCutSpray(rig, style, fall)

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

/** Cópia translúcida do corpo numa pose anterior (borrão de movimento). */
private fun DrawScope.drawGhost(
    rig: Rig,
    style: SamuraiStyle,
    colors: SamuraiColors,
    cloth: ClothState,
    phys: FigurePhysics,
    alpha: Float
) {
    // A camada junta todas as partes antes de aplicar a transparência,
    // assim as sobreposições não ficam mais escuras
    drawContext.canvas.saveLayer(Rect(-240f, -260f, 260f, 60f), Paint().apply { this.alpha = alpha })
    drawFigure(rig, style, colors, tint = colors.rim, hatOn = true, cloth = cloth, phys = phys)
    drawBlade(rig, colors, trail = 0f)
    drawContext.canvas.restore()
}

/** Atraso das mangas e da barra, calculado uma vez por quadro. */
private class ClothState(val frontSleeve: Offset, val backSleeve: Offset, val hem: Offset)

private const val KNOT_A = "obiA"
private const val KNOT_B = "obiB"
private const val BAND_A = "bandA"
private const val BAND_B = "bandB"
private const val KASA_RIBBON = "kasa"
private const val HAIR = "hair"
private const val BOW = "bow"
private const val SCARF = "scarf"

/** Avança a física de fitas, cabelo, laço, mangas e barra para este quadro. */
private fun stepCloth(
    rig: Rig,
    style: SamuraiStyle,
    phys: FigurePhysics,
    wind: Float,
    time: Float,
    hatOn: Boolean
): ClothState {
    val dt = phys.dt
    // O vento empurra para trás e a gravidade puxa para baixo; o cabelo é mais pesado
    val drag = 150f + 90f * wind
    val light = Offset(-drag, 70f)
    val heavy = Offset(-drag * 0.55f, 170f)
    val back = Offset(-0.9f, 0.45f)

    val knotA = Offset(-14f, rig.hipY)
    val knotB = Offset(-14f, rig.hipY + 1f)
    phys.chain(KNOT_A, 5, 3f, knotA, back).step(knotA, dt, light, time)
    phys.chain(KNOT_B, 5, 3.4f, knotB, Offset(-0.6f, 0.8f)).step(knotB, dt, light, time + 0.7f)

    val h = rig.head
    when (style) {
        SamuraiStyle.PLAYER -> {
            val a = Offset(h.x - 6.5f, h.y - 3.2f)
            val b = Offset(h.x - 6.5f, h.y - 2.4f)
            phys.chain(BAND_A, 6, 3.2f, a, back).step(a, dt, light, time + 0.3f)
            phys.chain(BAND_B, 5, 2.8f, b, back).step(b, dt, light, time + 1.1f)
        }

        SamuraiStyle.RONIN -> if (hatOn) {
            val a = Offset(h.x - 17f, h.y - 4f)
            phys.chain(KASA_RIBBON, 7, 3.4f, a, back).step(a, dt, light, time)
        }

        SamuraiStyle.AKANE -> {
            val root = h + Offset(-4f, -7f)
            phys.chain(HAIR, 9, 5.2f, root, Offset(-0.95f, 0.3f)).step(root, dt, heavy, time, damping = 0.93f)
            val bow = root + Offset(-1f, 1f)
            phys.chain(BOW, 5, 2.6f, bow, Offset(-0.5f, 0.85f)).step(bow, dt, light, time + 0.5f)
        }

        SamuraiStyle.YUKI -> {
            val root = h + Offset(-5f, -4f)
            phys.chain(HAIR, 11, 6.2f, root, Offset(-0.85f, 0.5f)).step(root, dt, heavy, time, damping = 0.94f)
        }

        SamuraiStyle.KAGE -> {
            val neck = Offset(h.x - 3f, h.y + 6f)
            phys.chain(SCARF, 10, 5.6f, neck, back).step(neck, dt, light, time + 0.4f)
        }

        else -> Unit
    }

    return ClothState(
        frontSleeve = phys.lag("sleeveF").step(rig.frontElbow, dt),
        backSleeve = phys.lag("sleeveB").step(rig.backElbow, dt),
        hem = phys.lag("hem").step(Offset(0f, rig.hipY), dt, stiffness = 120f, damping = 9f, limit = 7f)
    )
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

private const val UPPER_ARM = 14f
private const val FOREARM = 14f

private fun buildRig(
    style: SamuraiStyle,
    pose: StrikePose,
    crouch: Float,
    kneel: Float,
    recoil: Float,
    wind: Float,
    time: Float
): Rig {
    val l = pose.lunge
    val lc = l.coerceIn(0f, 1f)
    val breathe = sin(time * 2.1f) * (1f - kneel)
    val drop = 4f * crouch + 8f * l + 20f * kneel + pose.drop
    val hipY = -64f + drop
    val shoulderY = hipY - 33f - breathe * 0.7f
    val lean = 2f + 3f * crouch + 12f * l + 4f * kneel + pose.lean - 7f * recoil
    val sw = style.shoulderFactor
    val head = Offset(3f + lean - 2f * recoil, shoulderY - 11f - breathe * 0.2f + 1.5f * recoil)
    val frontShoulder = Offset(9f * sw + lean, shoulderY + 3f)
    val backShoulder = Offset(-9f * sw + lean, shoulderY + 3f)

    // Bainha: no saque ela é puxada para trás (saya-biki)
    val sayaMouth = lerp(Offset(7f, hipY - 3f), Offset(-1f, hipY + 1f), lc)
    val sayaEnd = lerp(Offset(-46f, hipY + 13f), Offset(-56f, hipY + 5f), lc)

    // Mão da frente: do cabo, na cintura, até o braço estendido; sobe num arco
    val guardHand = Offset(14f, hipY - 7.5f)
    val strikeHand = Offset(44f + lean * 0.3f, shoulderY + 1f)
    val arm = pose.arm
    val frontHand = lerp(guardHand, strikeHand, arm) +
        Offset(0f, -7f * sin(PI_F * arm.coerceIn(0f, 1f)) + pose.lift)
    val backHand = lerp(Offset(5f, hipY - 2f), Offset(-6f, hipY + 1f), lc)

    // Espada: até 22% do saque ainda está na bainha; depois varre de trás para frente.
    // No chiburi a lâmina ainda gira um pouco a mais (tilt).
    val bladeVisible = pose.draw > 0.22f
    val sweep = ((pose.draw - 0.22f) / 0.78f).coerceIn(0f, 1.1f)
    val bladeDir = if (bladeVisible) {
        val phi = PI_F * (1f - sweep)
        rotateVector(Offset(cos(phi), -0.13f - 0.12f * sin(phi)), pose.tilt)
    } else {
        Offset(-0.87f, 0.5f)
    }
    val axis = normalize(bladeDir)
    val tsuba = frontHand + axis * 5f
    val pommel = frontHand - axis * 14f

    return Rig(
        l = l,
        draw = pose.draw,
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
        frontElbow = solveElbow(frontShoulder, frontHand),
        backElbow = solveElbow(backShoulder, backHand),
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

/**
 * Cinemática inversa de dois ossos: com o ombro e a mão definidos, acha o cotovelo
 * pela lei dos cossenos, mantendo braço e antebraço do mesmo tamanho. O cotovelo
 * dobra sempre para baixo.
 */
private fun solveElbow(shoulder: Offset, hand: Offset): Offset {
    val d = hand - shoulder
    val rawDist = d.getDistance().coerceAtLeast(0.001f)
    val dir = d / rawDist
    val dist = rawDist.coerceIn(1f, UPPER_ARM + FOREARM - 0.05f)
    val along = (UPPER_ARM * UPPER_ARM - FOREARM * FOREARM + dist * dist) / (2f * dist)
    val out = sqrt((UPPER_ARM * UPPER_ARM - along * along).coerceAtLeast(0f))
    var perp = Offset(-dir.y, dir.x)
    if (perp.y < 0f) perp = -perp
    return shoulder + dir * along + perp * out
}

private fun rotateVector(v: Offset, angle: Float): Offset {
    if (angle == 0f) return v
    val c = cos(angle)
    val s = sin(angle)
    return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
}

/** @param tint quando definido, desenha só a silhueta nessa cor (camadas de contraluz). */
private fun DrawScope.drawFigure(
    rig: Rig,
    style: SamuraiStyle,
    c: SamuraiColors,
    tint: Color?,
    hatOn: Boolean,
    cloth: ClothState,
    phys: FigurePhysics
) {
    val ink = tint ?: c.ink
    val accent = tint ?: c.accent
    val gold = tint ?: c.gold
    val details = tint == null
    val flutter = rig.wind * 3f + sin(rig.time * 4.3f) * 1.1f
    val hipY = rig.hipY
    val sy = rig.shoulderY

    // Asas de corvo do Tengu, atrás do corpo, batendo devagar
    if (style == SamuraiStyle.TENGU) drawWings(rig, ink, c.detail, details)

    // Manga e braço de trás
    drawSleeve(rig.backShoulder, rig.backElbow, flutter, cloth.backSleeve, ink, ragged = style == SamuraiStyle.RONIN)
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
        lineTo(bf - 12f + flutter * 1.4f + cloth.hem.x * 1.6f, cloth.hem.y * 0.3f)
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
    phys.existing(KNOT_A)?.let { drawChain(it, accent, 2.3f) }
    phys.existing(KNOT_B)?.let { drawChain(it, accent, 2f) }

    // Armadura do Oni e do Kage: ombreiras e saiote de placas
    if (style == SamuraiStyle.ONI || style == SamuraiStyle.KAGE) {
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

    // Cachecol do Kage tremulando para trás
    if (style == SamuraiStyle.KAGE) phys.existing(SCARF)?.let { drawTaperedChain(it, ink, baseWidth = 2.6f) }

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
    drawHead(rig, style, ink, accent, gold, c, details, hatOn, phys)

    // Manga e braço da frente por cima de tudo
    if (style == SamuraiStyle.ONI || style == SamuraiStyle.KAGE) drawArmorPlate(fs, ink, c.detail, gold, details)
    drawSleeve(fs, rig.frontElbow, flutter, cloth.frontSleeve, ink, ragged = style == SamuraiStyle.RONIN)
    drawArm(fs, rig.frontElbow, rig.frontHand, ink)
}

private fun DrawScope.drawArm(shoulder: Offset, elbow: Offset, hand: Offset, ink: Color) {
    drawLine(ink, shoulder, elbow, strokeWidth = 6.8f, cap = StrokeCap.Round)
    drawLine(ink, elbow, hand, strokeWidth = 5.8f, cap = StrokeCap.Round)
    drawCircle(ink, radius = 3.3f, center = hand)
}

/** Manga larga do kimono, pendurada no braço e balançando. */
private fun DrawScope.drawSleeve(
    shoulder: Offset,
    elbow: Offset,
    flutter: Float,
    lag: Offset,
    ink: Color,
    ragged: Boolean
) {
    // O pano fica para trás quando o braço se move rápido e depois volta (mola)
    val bottomA = elbow + Offset(-2f - flutter * 0.6f, 15f) + lag * 1.2f
    val bottomB = shoulder + Offset(-9f - flutter, 15f) + lag * 1.5f
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
    hatOn: Boolean,
    phys: FigurePhysics
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
            phys.existing(BAND_A)?.let { drawChain(it, accent, 1.8f) }
            phys.existing(BAND_B)?.let { drawChain(it, accent, 1.5f) }
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
                phys.existing(KASA_RIBBON)?.let { drawChain(it, ink, 2.4f) }
                if (details) drawLine(c.detail, Offset(h.x + 5f, h.y - 2f), Offset(h.x + 3f, h.y + 6f), strokeWidth = 0.8f)
            } else {
                drawTopknot(h, ink)
            }
        }

        SamuraiStyle.AKANE -> {
            drawCircle(ink, radius = 7.4f, center = h + Offset(-0.8f, -0.8f))
            // Rabo de cavalo longo com física: chicoteia no saque e balança no vento
            phys.existing(HAIR)?.let { drawTaperedChain(it, ink, baseWidth = 3.6f) }
            val root = h + Offset(-4f, -7f)
            drawCircle(accent, radius = 2.2f, center = root + Offset(-1f, 1f))
            phys.existing(BOW)?.let { drawChain(it, accent, 1.6f) }
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

        SamuraiStyle.YUKI -> {
            // Cabelo solto com física (bem comprido) e um grampo de gelo
            drawCircle(ink, radius = 7.4f, center = h + Offset(-0.8f, -0.6f))
            phys.existing(HAIR)?.let { drawTaperedChain(it, ink, baseWidth = 4.4f) }
            drawLine(accent, Offset(h.x - 7f, h.y - 6f), Offset(h.x - 1f, h.y - 10f), strokeWidth = 1.4f, cap = StrokeCap.Round)
            drawCircle(accent, radius = 1.6f, center = Offset(h.x - 1f, h.y - 10f))
        }

        SamuraiStyle.TENGU -> {
            // Máscara vermelha de nariz comprido, chapéu tokin e cabelo selvagem
            val nose = Path().apply {
                moveTo(h.x + 4.5f, h.y - 2.5f)
                cubicTo(h.x + 10f, h.y - 3.5f, h.x + 16f, h.y - 2f, h.x + 19f, h.y + 0.5f)
                cubicTo(h.x + 15f, h.y + 2f, h.x + 9f, h.y + 2.8f, h.x + 5f, h.y + 2.5f)
                close()
            }
            drawPath(nose, if (details) Iai.ShuBright else ink)
            val tokin = Path().apply {
                moveTo(h.x - 2f, h.y - 6.5f)
                lineTo(h.x + 3.5f, h.y - 7.5f)
                lineTo(h.x + 2.5f, h.y - 12f)
                lineTo(h.x - 2.5f, h.y - 11f)
                close()
            }
            drawPath(tokin, ink)
            val hair = Path().apply {
                moveTo(h.x - 6f, h.y - 3f)
                lineTo(h.x - 14f - flutter * 0.5f, h.y - 1f)
                lineTo(h.x - 8f, h.y + 1f)
                lineTo(h.x - 15f - flutter * 0.6f, h.y + 5f)
                lineTo(h.x - 6f, h.y + 4f)
                close()
            }
            drawPath(hair, ink)
        }

        SamuraiStyle.KAGE -> {
            // Capuz que cobre a cabeça e cai nas costas, com olhos em brasa
            val hood = Path().apply {
                moveTo(h.x + 6.5f, h.y - 4f)
                cubicTo(h.x + 5f, h.y - 12f, h.x - 8f, h.y - 13f, h.x - 10f, h.y - 3f)
                cubicTo(h.x - 12f, h.y + 4f, h.x - 10f + flutter * 0.4f, h.y + 10f, h.x - 7f, h.y + 12f)
                lineTo(h.x + 2f, h.y + 7f)
                close()
            }
            drawPath(hood, ink)
            if (details) {
                val glow = 0.6f + 0.4f * sin(rig.time * 3.2f)
                drawLine(accent.copy(alpha = glow), Offset(h.x + 2.5f, h.y - 1.2f), Offset(h.x + 6.2f, h.y - 1.6f), strokeWidth = 1.2f, cap = StrokeCap.Round)
            }
        }

        SamuraiStyle.ASHIGARU -> {
            // Jingasa: chapéu de ferro largo e achatado, com a fita no queixo
            val hat = Path().apply {
                moveTo(h.x - 15f, h.y - 1f)
                cubicTo(h.x - 8f, h.y - 6f, h.x - 3f, h.y - 10f, h.x + 1f, h.y - 10.5f)
                cubicTo(h.x + 5f, h.y - 10f, h.x + 10f, h.y - 6f, h.x + 17f, h.y - 1f)
                lineTo(h.x + 15f, h.y + 0.5f)
                lineTo(h.x - 13f, h.y + 0.5f)
                close()
            }
            drawPath(hat, ink)
            if (details) {
                drawLine(accent, Offset(h.x - 13f, h.y - 0.5f), Offset(h.x + 15f, h.y - 0.5f), strokeWidth = 1f)
                drawLine(c.detail, Offset(h.x + 3f, h.y + 0.5f), Offset(h.x + 4f, h.y + 6f), strokeWidth = 0.7f)
            }
        }
    }
}

/** Asas de corvo do Tengu: penas longas que abrem e fecham devagar. */
private fun DrawScope.drawWings(rig: Rig, ink: Color, detail: Color, details: Boolean) {
    val base = Offset(rig.backShoulder.x + 2f, rig.shoulderY + 4f)
    val beat = sin(rig.time * 1.8f) * 0.18f
    for (side in 0..1) {
        val spread = (if (side == 0) 1f else 0.72f) + beat
        val tip = base + Offset(-38f * spread, -26f * spread + (if (side == 0) 0f else 10f))
        val wing = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(base.x - 12f, base.y - 22f * spread, tip.x + 8f, tip.y - 4f, tip.x, tip.y)
            // Pontas das penas
            lineTo(tip.x + 6f, tip.y + 8f)
            lineTo(tip.x + 2f, tip.y + 12f)
            lineTo(tip.x + 10f, tip.y + 16f)
            lineTo(tip.x + 7f, tip.y + 21f)
            cubicTo(tip.x + 18f, tip.y + 26f, base.x - 8f, base.y + 12f, base.x, base.y + 8f)
            close()
        }
        drawPath(wing, ink)
        if (details && side == 0) {
            for (i in 1..3) {
                val k = i / 4f
                drawLine(detail, base + Offset(-4f, 2f), Offset(mix(base.x, tip.x, k + 0.2f), mix(base.y, tip.y, k) + 10f), strokeWidth = 0.7f)
            }
        }
    }
}

private fun DrawScope.drawTopknot(h: Offset, ink: Color) {
    drawOval(ink, topLeft = Offset(h.x - 6.5f, h.y - 11.5f), size = Size(7f, 5f))
    drawLine(ink, Offset(h.x - 3f, h.y - 9f), Offset(h.x + 1f, h.y - 6f), strokeWidth = 2.4f, cap = StrokeCap.Round)
}

/** Fita ou cordão seguindo os pontos da física, suavizado entre os pontos. */
private fun DrawScope.drawChain(chain: Chain, color: Color, width: Float) {
    val path = Path()
    var lastX = chain.x[0]
    var lastY = chain.y[0]
    path.moveTo(lastX, lastY)
    for (i in 1 until chain.size) {
        val mx = (chain.x[i - 1] + chain.x[i]) / 2f
        val my = (chain.y[i - 1] + chain.y[i]) / 2f
        if (i == 1) {
            path.lineTo(mx, my)
        } else {
            // Curva quadrática (controle no ponto anterior) escrita como cúbica
            val cx = chain.x[i - 1]
            val cy = chain.y[i - 1]
            path.cubicTo(
                lastX + 2f / 3f * (cx - lastX), lastY + 2f / 3f * (cy - lastY),
                mx + 2f / 3f * (cx - mx), my + 2f / 3f * (cy - my),
                mx, my
            )
        }
        lastX = mx
        lastY = my
    }
    path.lineTo(chain.x[chain.size - 1], chain.y[chain.size - 1])
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Mecha de cabelo: grossa na raiz e afinando até a ponta. */
private fun DrawScope.drawTaperedChain(chain: Chain, color: Color, baseWidth: Float) {
    val n = chain.size
    val left = ArrayList<Offset>(n)
    val right = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val prev = chain.point(maxOf(i - 1, 0))
        val next = chain.point(minOf(i + 1, n - 1))
        val dir = normalize(next - prev)
        val normal = Offset(-dir.y, dir.x)
        val k = i / (n - 1f)
        val half = baseWidth * (1f - k) + 0.3f
        left += chain.point(i) + normal * half
        right += chain.point(i) - normal * half
    }
    val path = Path().apply {
        moveTo(left[0])
        for (i in 1 until n) lineTo(left[i])
        for (i in n - 1 downTo 0) lineTo(right[i])
        close()
    }
    drawPath(path, color)
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

/**
 * Respingos que saem do tronco logo que o samurai é cortado e caem com a gravidade.
 * Gotas de tinta vermelha para todos; no Oni, faíscas douradas batendo na armadura.
 */
private fun DrawScope.drawCutSpray(rig: Rig, style: SamuraiStyle, fall: Float) {
    val p = (fall / 0.5f).coerceIn(0f, 1f)
    if (p <= 0f || p >= 1f) return
    val armored = style == SamuraiStyle.ONI
    val origin = rig.frontShoulder + Offset(-3f, 16f)
    val fade = 1f - p
    for (i in 0 until 16) {
        val angle = (190f + 100f * hash(i + 7)) * PI_F / 180f
        val speed = (if (armored) 55f else 32f) + 40f * hash(i + 31)
        val dir = Offset(cos(angle), sin(angle))
        val gravity = if (armored) 50f else 95f
        val pos = origin + dir * (speed * p) + Offset(0f, gravity * p * p)
        val velocity = dir * speed + Offset(0f, 2f * gravity * p)
        val tail = normalize(velocity) * (if (armored) 4f else 2.4f)
        val big = hash(i + 55)
        val color = when {
            armored -> if (big > 0.5f) Iai.Kin else Iai.Steel
            big > 0.35f -> Iai.Blood
            else -> Iai.Sumi
        }
        val width = if (armored) 0.9f else 1.2f + 1.6f * big
        drawLine(color.copy(alpha = fade), pos - tail, pos, strokeWidth = width, cap = StrokeCap.Round)
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
        drawPath(arc, c.trail.copy(alpha = 0.6f * trail))
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
        translate(0.6f, -0.2f) {
            drawMakiwaraShape(cut, wind, SamuraiColors(lerpColor(colors.ink, colors.rim, 0.38f), colors.detail, colors.accent, lerpColor(colors.ink, colors.rim, 0.38f), colors.steel, colors.steelEdge, colors.gold), silhouette = true)
        }
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
