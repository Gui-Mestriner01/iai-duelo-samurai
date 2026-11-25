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
import kotlin.math.atan2
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

/** Área desenhada por um personagem (para a camada de transparência ao se desfazer). */
private val FIGURE_BOUNDS = Rect(-280f, -300f, 300f, 80f)

/**
 * Desenha um samurai em silhueta de tinta com contraluz do sol.
 * Coordenadas internas: pés em (0,0), altura ~150, frente para +x.
 *
 * @param strikeMs tempo desde o início do golpe em ms (negativo = em guarda); a pose
 * vem da linha do tempo em [strikePose]
 * @param crouch 0..1 tensão: o corpo abaixa e se inclina esperando o sinal
 * @param fall 0..1 morte: tranco do corte, joelhos cedendo, tombo e o corpo no chão
 * (linha do tempo em [deathPose])
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

    // O Oni e o Kage, pesados de armadura, caem de costas; os outros tombam para a frente
    val backward = style == SamuraiStyle.ONI || style == SamuraiStyle.KAGE
    val death = deathPose(fall, backward)

    // Quem é cortado no meio do próprio golpe congela no corte (não embainha depois de morto)
    val ms = if (fall > 0f && strikeMs >= 0f) minOf(strikeMs, 240f) else strikeMs
    val figureTime = time + if (facingRight) 0f else 1.7f
    val rig = buildRig(style, strikePose(ms), crouch * (1f - death.kneel), death, wind, figureTime)
    val legs = buildLegs(rig, death, backward)
    val body = BodyFrame(death.angle, Offset(0f, rig.hipY), legs.hip - Offset(0f, rig.hipY))
    val hatFlying = style == SamuraiStyle.RONIN && fall > 0.15f
    phys.advance(time)
    val cloth = stepCloth(rig, style, phys, wind, figureTime, hatOn = !hatFlying, bodyAngle = death.angle)
    val trail = if (ms >= 0f && fall <= 0f) strikeTrail(ms) else 0f
    val swordDropped = fall >= DEATH_DROP_AT && rig.bladeVisible
    val eyes = eyesFor(ms, death, figureTime)
    val fade = dissolve(style, fall)

    withTransform({
        translate(feet.x, feet.y)
        scale(scaleX = scale * direction, scaleY = scale, pivot = Offset.Zero)
    }) {
        // Sombra no chão: se alonga quando o corpo deita
        val chest = body.toWorld(Offset(rig.lean * 0.5f, rig.hipY - 18f))
        val feetMid = (legs.frontAnkle.x + legs.backAnkle.x) / 2f
        val shadowX = mix(2f, (chest.x + feetMid) / 2f, death.lie)
        val shadowW = 76f + 58f * death.lie
        drawOval(
            Color.Black.copy(alpha = 0.32f * (0.35f + 0.65f * fade)),
            topLeft = Offset(shadowX - shadowW / 2f, -2.6f),
            size = Size(shadowW, 5.2f)
        )

        val layered = fade < 1f
        if (layered) drawContext.canvas.saveLayer(FIGURE_BOUNDS, Paint().apply { alpha = fade.coerceIn(0f, 1f) })

        // Rastros fantasma durante o saque, como num anime
        if (ms in 80f..300f && fall <= 0f) {
            for (g in 2 downTo 1) {
                val ghost = buildRig(style, strikePose(ms - 26f * g), crouch, DeathPose.NONE, wind, figureTime)
                drawGhost(ghost, buildLegs(ghost, DeathPose.NONE, backward), style, colors, cloth, phys, alpha = 0.22f / g)
            }
        }

        // Contraluz em dois tons: borda laranja e uma faixa de meia-luz por dentro,
        // o que dá volume à silhueta sem perder o estilo de tinta
        translate(1.6f, -0.5f) {
            drawFigure(rig, legs, body, style, colors, colors.rim, !hatFlying, cloth, phys, eyes, !swordDropped)
        }
        translate(0.7f, -0.2f) {
            drawFigure(rig, legs, body, style, colors, lerpColor(colors.ink, colors.rim, 0.38f), !hatFlying, cloth, phys, eyes, !swordDropped)
        }
        drawFigure(rig, legs, body, style, colors, null, !hatFlying, cloth, phys, eyes, !swordDropped)
        if (!swordDropped) withBody(body) { drawBlade(rig, colors, trail, ms) }
        if (style == SamuraiStyle.ONI && fall > 0.03f) withBody(body) { drawArmorCracks(rig, colors, fall) }

        if (layered) drawContext.canvas.restore()

        // A espada que escapou da mão rodopia, quica e fica no chão
        if (swordDropped) drawDroppedSword(rig, colors, fall)

        // Respingos no corte: tinta vermelha, ou faíscas na armadura do Oni
        drawCutSpray(rig, style, fall)

        // Poeira quando o corpo bate no chão
        if (death.landed > 0f) drawDust(chest.x, death.landed, heavy = backward, c = colors)

        // Cada chefe sobrenatural some do seu jeito
        if (fall > 0f) {
            val points = listOf(
                body.toWorld(rig.head),
                body.toWorld(Offset(rig.lean * 0.6f, rig.shoulderY + 8f)),
                legs.hip,
                legs.frontKnee,
                legs.backKnee,
                body.toWorld(rig.frontShoulder)
            )
            when (style) {
                SamuraiStyle.KAGE -> drawSmoke(points, fall, colors, time)
                SamuraiStyle.YUKI -> drawSnowBurst(points, fall)
                SamuraiStyle.TENGU -> drawFeathers(points, fall, colors)
                SamuraiStyle.ONI -> drawArmorDebris(rig, fall, colors)
                else -> Unit
            }
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

/** Cópia translúcida do corpo numa pose anterior (borrão de movimento). */
private fun DrawScope.drawGhost(
    rig: Rig,
    legs: Legs,
    style: SamuraiStyle,
    colors: SamuraiColors,
    cloth: ClothState,
    phys: FigurePhysics,
    alpha: Float
) {
    // A camada junta todas as partes antes de aplicar a transparência,
    // assim as sobreposições não ficam mais escuras
    drawContext.canvas.saveLayer(FIGURE_BOUNDS, Paint().apply { this.alpha = alpha })
    drawFigure(rig, legs, BodyFrame(0f, Offset(0f, rig.hipY), Offset.Zero), style, colors, colors.rim, true, cloth, phys, Eyes.OPEN, true)
    drawBlade(rig, colors, trail = 0f, ms = -1f)
    drawContext.canvas.restore()
}

/** Expressão dos olhos. */
private enum class Eyes { OPEN, FOCUS, SHOCK, CLOSED }

private fun eyesFor(ms: Float, death: DeathPose, time: Float): Eyes = when {
    death.droop > 0.55f -> Eyes.CLOSED
    death.recoil > 0.02f || death.limp > 0f -> Eyes.SHOCK
    ms in 60f..1300f -> Eyes.FOCUS
    // Piscada de vez em quando
    (time * 0.31f) % 1f < 0.035f -> Eyes.CLOSED
    else -> Eyes.OPEN
}

/** Opacidade de quem some ao morrer: Kage vira fumaça, Yuki vira neve, Tengu vira penas. */
private fun dissolve(style: SamuraiStyle, fall: Float): Float = when (style) {
    SamuraiStyle.KAGE -> 1f - easeInCubic((fall - 0.62f) / 0.3f)
    SamuraiStyle.YUKI -> 1f - easeInOut((fall - 0.55f) / 0.35f)
    SamuraiStyle.TENGU -> 1f - easeInOut((fall - 0.5f) / 0.22f)
    else -> 1f
}

/**
 * Referencial do tronco: gira em volta do quadril e acompanha o quadril quando ele
 * desce (joelhos) ou vai ao chão. Braços, cabeça e tecido são desenhados nele;
 * as pernas são desenhadas fora, ligando o quadril aos pés.
 */
private class BodyFrame(val angle: Float, val pivot: Offset, val shift: Offset) {
    private val rad = angle * PI_F / 180f
    val identity = angle == 0f && shift == Offset.Zero

    fun toWorld(p: Offset): Offset = rotateVector(p - pivot, rad) + pivot + shift
}

private inline fun DrawScope.withBody(body: BodyFrame, crossinline block: DrawScope.() -> Unit) {
    if (body.identity) {
        block()
    } else {
        withTransform({
            translate(body.shift.x, body.shift.y)
            rotate(body.angle, body.pivot)
        }) { block() }
    }
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

/**
 * Avança a física de fitas, cabelo, laço, mangas e barra para este quadro.
 * As correntes vivem no referencial do tronco; a gravidade e o vento são girados
 * para dentro dele, então o cabelo continua caindo para baixo quando o corpo tomba.
 */
private fun stepCloth(
    rig: Rig,
    style: SamuraiStyle,
    phys: FigurePhysics,
    wind: Float,
    time: Float,
    hatOn: Boolean,
    bodyAngle: Float
): ClothState {
    val dt = phys.dt
    // O vento empurra para trás e a gravidade puxa para baixo; o cabelo é mais pesado
    val drag = (150f + 90f * wind) * (1f - rig.limp * 0.6f)
    val toBody = -bodyAngle * PI_F / 180f
    val light = rotateVector(Offset(-drag, 70f), toBody)
    val heavy = rotateVector(Offset(-drag * 0.55f, 170f), toBody)
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

/** Posição de cada parte do tronco e dos braços neste quadro (referencial do tronco). */
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
    val bladeLength: Float,
    val bladeVisible: Boolean,
    val sweep: Float,
    /** Guarda e direção da espada ainda na mão (de onde ela escapa ao morrer). */
    val gripTsuba: Offset,
    val gripAxis: Offset,
    val limp: Float,
    val wind: Float,
    val time: Float
)

private const val UPPER_ARM = 14f
private const val FOREARM = 14f
private const val THIGH = 33f
private const val SHIN = 33f

private fun buildRig(
    style: SamuraiStyle,
    pose: StrikePose,
    crouch: Float,
    death: DeathPose,
    wind: Float,
    time: Float
): Rig {
    val l = pose.lunge
    val lc = l.coerceIn(0f, 1f)
    val alive = 1f - death.limp
    val breathe = sin(time * 2.1f) * alive
    // O peso passa devagar de um pé para o outro enquanto ele espera
    val sway = sin(time * 0.83f) * 1.4f * (1f - crouch) * (1f - lc) * alive
    val drop = 4f * crouch + 8f * l + pose.drop
    val hipY = -64f + drop
    val shoulderY = hipY - 33f - breathe * 0.7f
    val lean = 2f + 3f * crouch + 12f * l + pose.lean - 7f * death.recoil + sway
    val sw = style.shoulderFactor
    val head = Offset(
        3f + lean - 2f * death.recoil + sway * 0.4f + 2.5f * death.droop,
        shoulderY - 11f - breathe * 0.2f + 1.5f * death.recoil + 3.5f * death.droop
    )
    val frontShoulder = Offset(9f * sw + lean, shoulderY + 3f)
    val backShoulder = Offset(-9f * sw + lean, shoulderY + 3f)

    // Bainha: no saque ela é puxada para trás (saya-biki)
    val sayaMouth = lerp(Offset(7f, hipY - 3f), Offset(-1f, hipY + 1f), lc)
    // Deitado, a bainha acompanha as pernas em vez de ficar espetada para cima
    val sayaEnd = lerp(lerp(Offset(-46f, hipY + 13f), Offset(-56f, hipY + 5f), lc), sayaMouth + Offset(-14f, 48f), death.lie)
    val sayaAxis = normalize(sayaEnd - sayaMouth)

    // Mão da frente: do cabo, na cintura, até o braço estendido; sobe num arco
    val guardHand = Offset(14f, hipY - 7.5f)
    val strikeHand = Offset(44f + lean * 0.3f, shoulderY + 1f)
    val arm = pose.arm
    var frontHand = lerp(guardHand, strikeHand, arm) +
        Offset(0f, -7f * sin(PI_F * arm.coerceIn(0f, 1f)) + pose.lift)
    var backHand = lerp(Offset(5f, hipY - 2f), Offset(-6f, hipY + 1f), lc)

    // Espada: até 22% do saque ainda está na bainha; depois varre de trás para frente.
    // No chiburi a lâmina ainda gira um pouco a mais (tilt).
    var bladeVisible = pose.draw > 0.22f
    val sweep = ((pose.draw - 0.22f) / 0.78f).coerceIn(0f, 1.1f)
    var bladeDir = if (bladeVisible) {
        val phi = PI_F * (1f - sweep)
        rotateVector(Offset(cos(phi), -0.13f - 0.12f * sin(phi)), pose.tilt)
    } else {
        Offset(-0.87f, 0.5f)
    }
    var bladeLength = 72f

    // Noto: a ponta passa por cima até a boca da bainha e a lâmina desliza para dentro
    if (pose.sheathe > 0f) {
        val notoHand = Offset(34f + lean * 0.3f, shoulderY + 12f)
        if (pose.sheathe <= 1f) {
            val e = easeInOut(pose.sheathe)
            frontHand = lerp(strikeHand, notoHand, e)
            val entry = sayaMouth - frontHand
            val from = atan2(bladeDir.y, bladeDir.x)
            var to = atan2(entry.y, entry.x)
            while (to > from) to -= 2f * PI_F
            val angle = mix(from, to, e)
            bladeDir = Offset(cos(angle), sin(angle))
            val reach = entry.getDistance() - 5f
            bladeLength = mix(72f, reach, easeInOut((pose.sheathe - 0.55f) / 0.45f))
        } else {
            val e = easeInOut(pose.sheathe - 1f)
            frontHand = lerp(notoHand, guardHand, e)
            val toMouth = sayaMouth - frontHand
            bladeDir = normalize(toMouth)
            bladeLength = (toMouth.getDistance() - 5f).coerceAtLeast(0f)
            bladeVisible = bladeLength > 1.5f
        }
    }

    val axis = normalize(bladeDir)
    val gripTsuba = frontHand + axis * 5f

    // Morto: os braços perdem a força e pendem com a gravidade; no chão, ficam esparramados
    if (death.limp > 0f) {
        val down = rotateVector(Offset(0f, 1f), -death.angle * PI_F / 180f)
        val hangFront = frontShoulder + down * 25f + Offset(2f, 0f)
        val hangBack = backShoulder + down * 25f
        val sprawlFront = frontShoulder + Offset(5f, -25f)
        val sprawlBack = backShoulder + Offset(5f, 22f)
        // Durante o tombo os braços já vão se abrindo, assim não atravessam o chão
        val sprawl = easeOutCubic(death.kneel * 0.35f + death.lie * 1.4f)
        frontHand = lerp(frontHand, lerp(hangFront, sprawlFront, sprawl), death.limp)
        backHand = lerp(backHand, lerp(hangBack, sprawlBack, sprawl), death.limp)
    }

    var tsuba = frontHand + axis * 5f
    var pommel = frontHand - axis * 14f
    if (!bladeVisible) {
        // Espada na bainha: o cabo fica na cintura mesmo quando a mão solta
        val sheathTsuba = sayaMouth - sayaAxis * 2.5f
        tsuba = lerp(tsuba, sheathTsuba, death.limp)
        pommel = lerp(pommel, sheathTsuba - sayaAxis * 14f, death.limp)
    }

    return Rig(
        l = l,
        draw = pose.draw,
        hipY = hipY,
        shoulderY = shoulderY,
        lean = lean,
        frontFoot = 22f + 12f * l,
        backFoot = -21f - 10f * l,
        head = head,
        frontShoulder = frontShoulder,
        backShoulder = backShoulder,
        frontHand = frontHand,
        backHand = backHand,
        frontElbow = solveJoint(frontShoulder, frontHand, UPPER_ARM, FOREARM, kneeForward = false),
        backElbow = solveJoint(backShoulder, backHand, UPPER_ARM, FOREARM, kneeForward = false),
        sayaMouth = sayaMouth,
        sayaEnd = sayaEnd,
        tsuba = tsuba,
        pommel = pommel,
        bladeDir = bladeDir,
        bladeLength = bladeLength,
        bladeVisible = bladeVisible,
        sweep = sweep,
        gripTsuba = gripTsuba,
        gripAxis = axis,
        limp = death.limp,
        wind = wind,
        time = time
    )
}

/** Pernas no referencial do mundo: quadril, joelhos e tornozelos. */
private class Legs(
    val hip: Offset,
    val frontHip: Offset,
    val backHip: Offset,
    val frontKnee: Offset,
    val backKnee: Offset,
    val frontAnkle: Offset,
    val backAnkle: Offset,
    val waistFront: Offset,
    val waistBack: Offset
)

/** Pose-chave das pernas: quadril, joelhos e tornozelos. */
private class LegKey(val hip: Offset, val frontKnee: Offset, val backKnee: Offset, val frontAnkle: Offset, val backAnkle: Offset)

/** De joelhos, com as canelas no chão. */
private val KneelForward = LegKey(Offset(3f, -37f), Offset(10f, -4f), Offset(2f, -4f), Offset(-21f, -3f), Offset(-28f, -3f))

/** De bruços, com as pernas esticadas para trás. */
private val LieForward = LegKey(Offset(18f, -13f), Offset(-14f, -7f), Offset(-15f, -11f), Offset(-46f, -5f), Offset(-46f, -10f))

/** Sentado no chão, joelhos para cima. */
private val SitBackward = LegKey(Offset(-8f, -21f), Offset(18f, -34f), Offset(12f, -30f), Offset(32f, -3f), Offset(27f, -3f))

/** De costas no chão, joelhos dobrados. */
private val LieBackward = LegKey(Offset(-16f, -13f), Offset(10f, -30f), Offset(6f, -24f), Offset(30f, -3f), Offset(26f, -3f))

private fun buildLegs(rig: Rig, death: DeathPose, backward: Boolean): Legs {
    val rad = death.angle * PI_F / 180f
    val standHip = Offset(0f, rig.hipY)
    val standFrontAnkle = Offset(rig.frontFoot, -3f)
    val standBackAnkle = Offset(rig.backFoot, -3f)
    val standFrontKnee = solveJoint(standHip + Offset(4.5f, 3f), standFrontAnkle, THIGH, SHIN, kneeForward = true)
    val standBackKnee = solveJoint(standHip + Offset(-4.5f, 3f), standBackAnkle, THIGH, SHIN, kneeForward = true)

    val k = if (backward) SitBackward else KneelForward
    val g = if (backward) LieBackward else LieForward
    fun blend(stand: Offset, kneel: Offset, lie: Offset) = lerp(lerp(stand, kneel, death.kneel), lie, death.lie)

    val hip = blend(standHip, k.hip, g.hip)
    return Legs(
        hip = hip,
        frontHip = hip + rotateVector(Offset(4.5f, 3f), rad),
        backHip = hip + rotateVector(Offset(-4.5f, 3f), rad),
        frontKnee = blend(standFrontKnee, k.frontKnee, g.frontKnee),
        backKnee = blend(standBackKnee, k.backKnee, g.backKnee),
        frontAnkle = blend(standFrontAnkle, k.frontAnkle, g.frontAnkle),
        backAnkle = blend(standBackAnkle, k.backAnkle, g.backAnkle),
        waistFront = hip + rotateVector(Offset(14f, -1f), rad),
        waistBack = hip + rotateVector(Offset(-14f, -1f), rad)
    )
}

/**
 * Cinemática inversa de dois ossos: com a raiz e a ponta definidas, acha a junta do
 * meio pela lei dos cossenos, mantendo os dois ossos do mesmo tamanho. Cotovelos
 * dobram para baixo; joelhos, para a frente.
 */
private fun solveJoint(root: Offset, end: Offset, first: Float, second: Float, kneeForward: Boolean): Offset {
    val d = end - root
    val rawDist = d.getDistance().coerceAtLeast(0.001f)
    val dir = d / rawDist
    val dist = rawDist.coerceIn(1f, first + second - 0.05f)
    val along = (first * first - second * second + dist * dist) / (2f * dist)
    val out = sqrt((first * first - along * along).coerceAtLeast(0f))
    var perp = Offset(-dir.y, dir.x)
    if (kneeForward) {
        if (perp.x < 0f) perp = -perp
    } else if (perp.y < 0f) {
        perp = -perp
    }
    return root + dir * along + perp * out
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
    legs: Legs,
    body: BodyFrame,
    style: SamuraiStyle,
    c: SamuraiColors,
    tint: Color?,
    hatOn: Boolean,
    cloth: ClothState,
    phys: FigurePhysics,
    eyes: Eyes,
    hiltInHand: Boolean
) {
    val ink = tint ?: c.ink
    val accent = tint ?: c.accent
    val gold = tint ?: c.gold
    val details = tint == null
    val flutter = (rig.wind * 3f + sin(rig.time * 4.3f) * 1.1f) * (1f - rig.limp * 0.7f)
    val hipY = rig.hipY
    val sy = rig.shoulderY

    withBody(body) {
        // Asas de corvo do Tengu, atrás do corpo, batendo devagar
        if (style == SamuraiStyle.TENGU) drawWings(rig, ink, c.detail, details)

        // Manga e braço de trás
        drawSleeve(rig.backShoulder, rig.backElbow, flutter, cloth.backSleeve, ink, ragged = style == SamuraiStyle.RONIN)
        drawArm(rig.backShoulder, rig.backElbow, rig.backHand, ink, c.detail, details, open = rig.limp)

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
    }

    // Hakama: pernas articuladas, presas ao quadril
    drawLegs(legs, ink, c.detail, details, flutter, cloth.hem, rig.wind)

    withBody(body) {
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
            // Gola cruzada do kimono e as dobras do tecido no peito
            val v = Offset(2f + rig.lean * 0.6f, sy + 15f)
            drawLine(c.detail, Offset(rig.head.x - 3f, sy - 1f), v, strokeWidth = 1.3f)
            drawLine(c.detail, Offset(rig.head.x + 4f, sy - 1f), v, strokeWidth = 1.3f)
            drawLine(c.detail, Offset(fs.x - 1f, sy + 8f), Offset(9f + rig.lean * 0.3f, hipY - 6f), strokeWidth = 0.7f)
            drawLine(c.detail, Offset(bs.x + 3f, sy + 9f), Offset(-8f + rig.lean * 0.2f, hipY - 5f), strokeWidth = 0.7f)
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
        if (details) drawLine(c.detail.copy(alpha = 0.5f), Offset(-15f, hipY), Offset(15f, hipY - 0.5f), strokeWidth = 0.6f)
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
                for (i in -1..1) drawLine(c.detail, Offset(i * 9f, hipY + 3f), Offset(i * 10.5f, hipY + 17f), strokeWidth = 0.6f)
            }
        }

        // Cachecol do Kage tremulando para trás
        if (style == SamuraiStyle.KAGE) phys.existing(SCARF)?.let { drawTaperedChain(it, ink, baseWidth = 2.6f) }

        // Cabo da espada (tsuka) com a guarda (tsuba)
        if (hiltInHand) {
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
                drawCircle(gold, radius = 1.2f, center = rig.pommel)
                drawCircle(gold, radius = 3.3f, center = rig.tsuba, style = Stroke(width = 0.8f))
            }
        }

        // Cabeça e pescoço
        drawLine(ink, Offset(rig.head.x - 1f, sy), rig.head + Offset(-1f, 4f), strokeWidth = 6.4f, cap = StrokeCap.Round)
        drawHead(rig, style, ink, accent, gold, c, details, hatOn, phys, eyes)

        // Manga e braço da frente por cima de tudo
        if (style == SamuraiStyle.ONI || style == SamuraiStyle.KAGE) drawArmorPlate(fs, ink, c.detail, gold, details)
        drawSleeve(fs, rig.frontElbow, flutter, cloth.frontSleeve, ink, ragged = style == SamuraiStyle.RONIN)
        drawArm(fs, rig.frontElbow, rig.frontHand, ink, c.detail, details, open = if (hiltInHand && rig.bladeVisible) 0f else rig.limp)
    }
}

/** Hakama larga: cada perna segue coxa e canela, com a barra abrindo em sino. */
private fun DrawScope.drawLegs(
    legs: Legs,
    ink: Color,
    detail: Color,
    details: Boolean,
    flutter: Float,
    hem: Offset,
    wind: Float
) {
    // Pés com sandália (waraji), por baixo da barra
    drawFoot(legs.backKnee, legs.backAnkle, ink)
    drawFoot(legs.frontKnee, legs.frontAnkle, ink)

    val backFlare = Offset(flutter * 1.4f + hem.x * 1.6f, hem.y * 0.3f)
    val frontFlare = Offset(wind * 2f, 0f)
    drawPath(hakamaLeg(legs.backHip, legs.backKnee, legs.backAnkle, backFlare), ink)
    drawPath(hakamaLeg(legs.frontHip, legs.frontKnee, legs.frontAnkle, frontFlare), ink)

    // Parte de cima, inteira como uma saia, até perto dos joelhos
    val waist = Path().apply {
        moveTo(legs.waistBack)
        lineTo(legs.waistFront)
        lineTo(lerp(legs.frontHip, legs.frontKnee, 0.72f))
        lineTo(lerp(legs.backHip, legs.backKnee, 0.72f))
        close()
    }
    drawPath(waist, ink)

    if (details) {
        // Pregas que descem pelas pernas
        for ((hip, knee, ankle) in listOf(
            Triple(legs.frontHip, legs.frontKnee, legs.frontAnkle),
            Triple(legs.backHip, legs.backKnee, legs.backAnkle)
        )) {
            val n = perpendicular(normalize(knee - hip))
            val m = perpendicular(normalize(ankle - knee))
            drawLine(detail.copy(alpha = 0.7f), hip + n * 2.5f, knee + n * 3f, strokeWidth = 0.7f)
            drawLine(detail.copy(alpha = 0.7f), knee + n * 3f, ankle + m * 5f, strokeWidth = 0.7f)
        }
    }
}

private fun hakamaLeg(hip: Offset, knee: Offset, ankle: Offset, flare: Offset): Path {
    val t = normalize(knee - hip)
    val s = normalize(ankle - knee)
    val n = perpendicular(t)
    val m = perpendicular(s)
    val kneeNormal = normalize(n + m)
    val hemCenter = ankle + s * 1.5f + flare
    val hipOut = hip + n * 8.5f
    val hipIn = hip - n * 8.5f
    val kneeOut = knee + kneeNormal * 8.5f
    val kneeIn = knee - kneeNormal * 8.5f
    val hemOut = hemCenter + m * 11f
    val hemIn = hemCenter - m * 11f
    return Path().apply {
        moveTo(hipOut)
        // Linhas levemente curvas: o tecido se acomoda em volta do joelho
        cubicTo(lerp(hipOut, kneeOut, 0.4f), lerp(hipOut, kneeOut, 0.8f), kneeOut)
        cubicTo(lerp(kneeOut, hemOut, 0.35f) + m * 1f, lerp(kneeOut, hemOut, 0.75f) + m * 2f, hemOut)
        lineTo(hemIn)
        cubicTo(lerp(hemIn, kneeIn, 0.25f) - m * 1f, lerp(hemIn, kneeIn, 0.65f), kneeIn)
        cubicTo(lerp(kneeIn, hipIn, 0.2f), lerp(kneeIn, hipIn, 0.6f), hipIn)
        close()
    }
}

/** Pé com sandália, apontando para a frente da canela. */
private fun DrawScope.drawFoot(knee: Offset, ankle: Offset, ink: Color) {
    val s = normalize(ankle - knee)
    val forward = Offset(s.y, -s.x)
    drawLine(ink, ankle + s * 1f - forward * 2f, ankle + s * 1f + forward * 7.5f, strokeWidth = 4.2f, cap = StrokeCap.Round)
}

/**
 * Braço com a mão: fechada no cabo, com os dedos marcados, ou aberta e solta
 * quando o personagem morre.
 */
private fun DrawScope.drawArm(
    shoulder: Offset,
    elbow: Offset,
    hand: Offset,
    ink: Color,
    detail: Color,
    details: Boolean,
    open: Float
) {
    drawLine(ink, shoulder, elbow, strokeWidth = 6.8f, cap = StrokeCap.Round)
    drawLine(ink, elbow, hand, strokeWidth = 5.8f, cap = StrokeCap.Round)
    drawCircle(ink, radius = 3.4f, center = hand)
    val f = normalize(hand - elbow)
    val side = perpendicular(f)
    if (open > 0.5f) {
        for (i in -1..1) {
            drawLine(ink, hand, hand + f * 4.4f + side * (i * 1.5f), strokeWidth = 1.3f, cap = StrokeCap.Round)
        }
        drawLine(ink, hand, hand + side * 3.2f - f * 0.5f, strokeWidth = 1.2f, cap = StrokeCap.Round)
    } else if (details) {
        for (i in 0..2) {
            val p = hand + f * 0.8f + side * (-1.7f + 1.7f * i)
            drawLine(detail, p, p + f * 1.8f, strokeWidth = 0.55f)
        }
    }
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
    phys: FigurePhysics,
    eyes: Eyes
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

    // Olhos: um brilho fino que dá vida à silhueta (foco no golpe, susto no corte, fechados ao morrer)
    val hasEyes = style != SamuraiStyle.ONI && style != SamuraiStyle.KAGE
    if (details && hasEyes) drawEye(h + Offset(3.5f, -1.4f), eyes, lerpColor(c.rim, Color.White, 0.45f))

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
                if (eyes != Eyes.CLOSED) drawCircle(Iai.ShuBright, radius = 1f, center = Offset(h.x + 4.6f, h.y - 0.4f))
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
            if (details && eyes != Eyes.CLOSED) {
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

private fun DrawScope.drawEye(e: Offset, eyes: Eyes, color: Color) {
    when (eyes) {
        Eyes.OPEN -> drawLine(color, e, e + Offset(2.2f, 0.2f), strokeWidth = 1.1f, cap = StrokeCap.Round)
        Eyes.FOCUS -> {
            drawLine(color, e + Offset(0f, 0.3f), e + Offset(2.4f, 0f), strokeWidth = 0.8f, cap = StrokeCap.Round)
            // Sobrancelha franzida
            drawLine(color.copy(alpha = 0.55f), e + Offset(-0.4f, -2.4f), e + Offset(2.6f, -1.3f), strokeWidth = 0.8f, cap = StrokeCap.Round)
        }
        Eyes.SHOCK -> drawCircle(color, radius = 1.05f, center = e + Offset(1.1f, 0f))
        Eyes.CLOSED -> drawLine(color.copy(alpha = 0.35f), e + Offset(0f, 0.7f), e + Offset(2.2f, 1.1f), strokeWidth = 0.7f, cap = StrokeCap.Round)
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
private fun DrawScope.drawBlade(rig: Rig, c: SamuraiColors, trail: Float, ms: Float) {
    // Koiguchi: o polegar empurra a guarda para soltar a espada e ela reluz
    if (ms in 10f..110f) drawGlint(rig.tsuba, 5f * sin(PI_F * (ms - 10f) / 100f), c.trail)

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

    val axis = normalize(rig.bladeDir)
    drawBladeShape(rig.tsuba, axis, rig.bladeLength, c)

    // Brilho que corre pela lâmina no saque, estrela na ponta no impacto
    // e um reflexo lento durante o zanshin
    if (ms in 100f..280f) {
        val k = (ms - 100f) / 180f
        drawGlint(rig.tsuba + axis * (rig.bladeLength * k), 4.5f * sin(PI_F * k), Color.White)
    }
    if (ms in 170f..340f) {
        val k = (ms - 170f) / 170f
        drawGlint(rig.tsuba + axis * rig.bladeLength, 9f * sin(PI_F * k), c.trail)
    }
    if (ms in 560f..900f) {
        val k = (ms - 560f) / 340f
        drawGlint(rig.tsuba + axis * (rig.bladeLength * k), 3.2f * sin(PI_F * k), Color.White)
    }
}

/** A lâmina em si: aço curvo com o fio claro no dorso. */
private fun DrawScope.drawBladeShape(tsuba: Offset, axis: Offset, length: Float, c: SamuraiColors) {
    if (length < 1f) return
    val d = axis * length
    var spine = perpendicular(axis)
    if (spine.y > 0f) spine = -spine
    val tip = tsuba + d
    val curve = length * 0.06f
    val blade = Path().apply {
        moveTo(tsuba + spine * 1.5f)
        cubicTo(tsuba + d * 0.35f + spine * (curve + 1.3f), tsuba + d * 0.75f + spine * (curve + 0.8f), tip)
        cubicTo(tsuba + d * 0.75f + spine * (curve - 1f), tsuba + d * 0.35f + spine * (curve - 1.4f), tsuba - spine * 1.3f)
        close()
    }
    drawPath(blade, c.steel)
    val spineLine = Path().apply {
        moveTo(tsuba + spine * 1.4f)
        cubicTo(tsuba + d * 0.35f + spine * (curve + 1.2f), tsuba + d * 0.75f + spine * (curve + 0.7f), tip)
    }
    drawPath(spineLine, c.steelEdge, style = Stroke(width = 0.8f, cap = StrokeCap.Round))
    // Habaki: a peça dourada logo depois da guarda
    drawLine(c.gold, tsuba + axis * 0.5f, tsuba + axis * 3.5f, strokeWidth = 2.6f)
}

/** Estrela de brilho de quatro pontas. */
private fun DrawScope.drawGlint(center: Offset, size: Float, color: Color) {
    if (size <= 0.2f) return
    drawCircle(color.copy(alpha = 0.25f), radius = size * 0.75f, center = center)
    drawLine(color.copy(alpha = 0.95f), center - Offset(size, 0f), center + Offset(size, 0f), strokeWidth = 0.9f, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.95f), center - Offset(0f, size), center + Offset(0f, size), strokeWidth = 0.9f, cap = StrokeCap.Round)
    val d = size * 0.4f
    drawLine(color.copy(alpha = 0.6f), center - Offset(d, d), center + Offset(d, d), strokeWidth = 0.6f, cap = StrokeCap.Round)
    drawLine(color.copy(alpha = 0.6f), center - Offset(d, -d), center + Offset(d, -d), strokeWidth = 0.6f, cap = StrokeCap.Round)
    drawCircle(Color.White, radius = size * 0.2f, center = center)
}

/**
 * A espada que escapa da mão no tranco do corte: sobe um pouco girando, cai,
 * quica uma vez e fica deitada no chão. Calculada direto do tempo da queda
 * (sem estado), então funciona igual na cena e nas prévias.
 */
private fun DrawScope.drawDroppedSword(rig: Rig, c: SamuraiColors, fall: Float) {
    val t = (fall - DEATH_DROP_AT) * DEATH_DURATION_S
    if (t < 0f) return
    val axis0 = rig.gripAxis
    val angle0 = atan2(axis0.y, axis0.x)
    val center0 = rig.gripTsuba + axis0 * 29f
    val vx = -26f
    val vy = -150f
    val spin = -9f
    val g = 900f
    val rest = -2.6f
    val disc = (vy * vy - 2f * g * (center0.y - rest)).coerceAtLeast(0f)
    val landAt = (-vy + sqrt(disc)) / g

    val center: Offset
    val angle: Float
    if (t < landAt) {
        center = center0 + Offset(vx * t, vy * t + 0.5f * g * t * t)
        angle = angle0 + spin * t
    } else {
        val tb = t - landAt
        val bounce = (vy + g * landAt) * 0.24f
        val hop = (bounce * tb - 0.5f * g * tb * tb).coerceAtLeast(0f)
        center = Offset(center0.x + vx * landAt + vx * 0.4f * minOf(tb, 0.45f), rest - hop)
        val impact = angle0 + spin * landAt
        val flat = kotlin.math.round(impact / PI_F) * PI_F
        angle = mix(impact, flat, easeOutCubic(tb / 0.3f))
    }
    val axis = Offset(cos(angle), sin(angle))
    val tsuba = center - axis * 29f
    drawBladeShape(tsuba, axis, 72f, c)
    drawLine(c.ink, tsuba, tsuba - axis * 14f, strokeWidth = 4.2f, cap = StrokeCap.Round)
    drawCircle(c.ink, radius = 3.3f, center = tsuba)
    drawCircle(c.gold, radius = 3.3f, center = tsuba, style = Stroke(width = 0.8f))
}

/** Nuvem de poeira quando o corpo bate no chão. */
private fun DrawScope.drawDust(x: Float, landed: Float, heavy: Boolean, c: SamuraiColors) {
    if (landed <= 0f || landed >= 1f) return
    val e = easeOutCubic(landed)
    val big = if (heavy) 1.4f else 1f
    val color = lerpColor(c.ink, c.rim, 0.45f)
    for (i in 0 until 12) {
        val side = if (i % 2 == 0) 1f else -1f
        val spread = (12f + 38f * hash(i + 3)) * big
        val pos = Offset(x + side * spread * e + (hash(i + 9) - 0.5f) * 12f, -2f - (4f + 12f * hash(i + 13)) * e)
        val r = (3f + 7f * e) * (0.7f + 0.6f * hash(i + 21)) * big
        drawCircle(color.copy(alpha = 0.34f * (1f - landed)), radius = r, center = pos)
    }
}

/** Kage se desfaz em fumaça escura que sobe, com um brilho violeta por dentro. */
private fun DrawScope.drawSmoke(points: List<Offset>, fall: Float, c: SamuraiColors, time: Float) {
    for (i in 0 until 34) {
        val p = (fall - 0.5f - hash(i + 101) * 0.15f) / 0.33f
        if (p <= 0f || p >= 1f) continue
        val origin = lerp(points[i % points.size], points[(i + 1) % points.size], hash(i + 5))
        val pos = origin + Offset(sin(time * 1.7f + i) * 6f * p - 20f * p * hash(i + 7), -70f * easeOutCubic(p))
        val r = 5f + 14f * p * (0.6f + 0.8f * hash(i + 11))
        val a = 0.5f * (1f - p) * minOf(1f, p * 6f)
        // Bordas suaves: três círculos sobrepostos, mais claros por fora
        drawCircle(c.ink.copy(alpha = a * 0.3f), radius = r, center = pos)
        drawCircle(c.ink.copy(alpha = a * 0.45f), radius = r * 0.72f, center = pos)
        drawCircle(c.ink.copy(alpha = a * 0.6f), radius = r * 0.45f, center = pos)
        drawCircle(c.accent.copy(alpha = a * 0.35f), radius = r * 0.3f, center = pos + Offset(0f, -r * 0.15f))
    }
}

/** Yuki vira neve: flocos que explodem do corpo e o vento leva. */
private fun DrawScope.drawSnowBurst(points: List<Offset>, fall: Float) {
    for (i in 0 until 34) {
        val p = (fall - 0.5f - hash(i + 201) * 0.12f) / 0.36f
        if (p <= 0f || p >= 1f) continue
        val origin = lerp(points[i % points.size], points[(i + 2) % points.size], hash(i + 3))
        val angle = hash(i + 17) * 2f * PI_F
        val speed = 26f + 44f * hash(i + 29)
        val pos = origin + Offset(cos(angle), sin(angle)) * (speed * easeOutCubic(p)) + Offset(-30f * p, -24f * p)
        val a = (1f - p) * minOf(1f, p * 8f)
        val color = if (i % 3 == 0) Iai.Ice else Color.White
        drawCircle(color.copy(alpha = a), radius = 1.2f + 1.4f * hash(i + 41), center = pos)
    }
}

/** Tengu explode em penas negras que rodopiam até o chão. */
private fun DrawScope.drawFeathers(points: List<Offset>, fall: Float, c: SamuraiColors) {
    for (i in 0 until 22) {
        val p = (fall - 0.44f - hash(i + 301) * 0.1f) / 0.44f
        if (p <= 0f || p >= 1f) continue
        val origin = lerp(points[i % points.size], points[(i + 1) % points.size], hash(i + 7))
        val angle = -PI_F * (0.1f + 0.8f * hash(i + 19))
        val burst = (24f + 34f * hash(i + 23)) * easeOutCubic(p)
        val pos = origin + Offset(cos(angle), sin(angle)) * burst +
            Offset(sin(p * 9f + i) * 5f, 52f * p * p)
        val spin = (hash(i + 31) - 0.5f) * 720f * p + i * 40f
        val a = 1f - easeInCubic(p)
        rotate(degrees = spin, pivot = pos) {
            val feather = Path().apply {
                moveTo(pos.x - 6.5f, pos.y)
                cubicTo(pos.x - 2.5f, pos.y - 3.2f, pos.x + 3f, pos.y - 2.6f, pos.x + 7f, pos.y)
                cubicTo(pos.x + 3f, pos.y + 2.6f, pos.x - 2.5f, pos.y + 3.2f, pos.x - 6.5f, pos.y)
                close()
            }
            drawPath(feather, c.ink.copy(alpha = a))
            drawLine(c.rim.copy(alpha = 0.7f * a), Offset(pos.x - 8f, pos.y), Offset(pos.x + 7f, pos.y), strokeWidth = 0.7f)
        }
    }
}

/** Rachaduras douradas brilhando na armadura do Oni depois do corte. */
private fun DrawScope.drawArmorCracks(rig: Rig, c: SamuraiColors, fall: Float) {
    val glow = 0.25f + 0.75f * (1f - easeInOut((fall - 0.05f) / 0.6f))
    val fs = rig.frontShoulder
    val sy = rig.shoulderY
    val crack = Path().apply {
        moveTo(fs.x - 1f, sy + 5f)
        lineTo(fs.x - 6f, sy + 12f)
        lineTo(fs.x - 3f, sy + 17f)
        lineTo(fs.x - 10f, sy + 25f)
        lineTo(fs.x - 8f, sy + 31f)
        moveTo(fs.x - 6f, sy + 12f)
        lineTo(fs.x - 13f, sy + 15f)
        moveTo(fs.x - 3f, sy + 17f)
        lineTo(fs.x + 3f, sy + 22f)
    }
    drawPath(crack, c.gold.copy(alpha = 0.35f * glow), style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(crack, c.gold.copy(alpha = glow), style = Stroke(width = 1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Pedaços da armadura do Oni que se soltam e caem no chão. */
private fun DrawScope.drawArmorDebris(rig: Rig, fall: Float, c: SamuraiColors) {
    val t = (fall - 0.05f) * DEATH_DURATION_S
    if (t <= 0f) return
    val starts = listOf(rig.frontShoulder + Offset(2f, 6f), rig.backShoulder + Offset(-2f, 4f), Offset(6f, rig.hipY + 12f))
    val velocities = listOf(Offset(55f, -190f), Offset(-40f, -230f), Offset(30f, -140f))
    val g = 900f
    for (i in starts.indices) {
        val p0 = starts[i]
        val v = velocities[i]
        val disc = (v.y * v.y - 2f * g * (p0.y + 2f)).coerceAtLeast(0f)
        val landAt = (-v.y + sqrt(disc)) / g
        val tt = minOf(t, landAt)
        val pos = Offset(p0.x + v.x * tt, p0.y + v.y * tt + 0.5f * g * tt * tt)
        val spin = tt * (300f + 200f * i)
        rotate(degrees = spin, pivot = pos) {
            val plate = Path().apply {
                moveTo(pos.x - 5f, pos.y - 3f)
                lineTo(pos.x + 5f, pos.y - 2.5f)
                lineTo(pos.x + 4f, pos.y + 3f)
                lineTo(pos.x - 4.5f, pos.y + 3.5f)
                close()
            }
            drawPath(plate, c.ink)
            drawLine(c.gold, Offset(pos.x - 4.5f, pos.y), Offset(pos.x + 4.5f, pos.y - 0.3f), strokeWidth = 0.7f)
        }
    }
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

private fun perpendicular(v: Offset): Offset = Offset(-v.y, v.x)

/** Número pseudoaleatório estável (0..1) a partir de um índice. */
private fun hash(i: Int): Float {
    val x = sin(i * 12.9898f + 78.233f) * 43758.547f
    return x - kotlin.math.floor(x)
}

private fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)

private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)

private fun Path.cubicTo(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)
