package com.gui.iai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.gui.iai.domain.Opponent
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
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
    val leftStyle: SamuraiStyle = SamuraiStyle.PLAYER,
    val rightStyle: SamuraiStyle = SamuraiStyle.RONIN,
    /** 0..1: escurece o céu, acalma o vento e aproxima a câmera durante a espera. */
    val tension: Float = 0f,
    /** 0..1: corte do makiwara. */
    val cut: Float = 0f
)

/** Visual de cada oponente do Caminho. */
fun Opponent.sceneStyle(): SamuraiStyle = when (this) {
    Opponent.TARO -> SamuraiStyle.APPRENTICE
    Opponent.RONIN -> SamuraiStyle.RONIN
    Opponent.AKANE -> SamuraiStyle.AKANE
    Opponent.GENSAI -> SamuraiStyle.MASTER
    Opponent.KUROGANE -> SamuraiStyle.ONI
}

/** Cor da faixa (e das fitas) de cada oponente. */
fun Opponent.accentColor(): Color = when (this) {
    Opponent.TARO -> Iai.Ai
    Opponent.RONIN -> Iai.Ash
    Opponent.AKANE -> Iai.ShuBright
    Opponent.GENSAI -> Iai.Washi
    Opponent.KUROGANE -> Iai.Kin
}

/** Proporção da altura onde fica o horizonte (o chão escuro começa aqui). */
const val HORIZON_FRACTION = 0.70f

private const val SLASH_START = 0.40f
private const val SLASH_END = 0.60f
private val FlashColor = Color(0xFFFFF7EA)

/** A cena do duelo sem efeito de corte. */
@Composable
fun DuelScene(
    state: SceneState,
    modifier: Modifier = Modifier
) {
    SlashableScene(state = state, split = 0f, slash = 0f, flash = 0f, modifier = modifier)
}

/**
 * A cena do duelo ao entardecer: céu com textura de papel, sol com brilho,
 * nuvens de tinta e pássaros cruzando, montanhas na névoa, um torii ao longe,
 * pétalas no vento e os dois samurais em silhueta com contraluz.
 *
 * No corte a tela "racha" na diagonal, treme, a pincelada branca atravessa
 * e um clarão pisca.
 *
 * @param split 0..1 deslocamento das metades
 * @param slash 0..1.5 progresso da linha do corte
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
    val clock = rememberSceneClock()
    val leftDraw = remember { Animatable(0f) }
    val rightDraw = remember { Animatable(0f) }
    val leftTrail = remember { Animatable(0f) }
    val rightTrail = remember { Animatable(0f) }
    LaunchedEffect(state.leftPose) { swing(state.leftPose, leftDraw, leftTrail) }
    LaunchedEffect(state.rightPose) { swing(state.rightPose, rightDraw, rightTrail) }
    val decor = remember { SceneDecor() }

    Canvas(modifier.fillMaxSize()) {
        val t = clock.floatValue
        val motion = FigureMotion(leftDraw.value, rightDraw.value, leftTrail.value, rightTrail.value)

        if (split > 0.01f) {
            val shift = split * 16.dp.toPx()
            val shake = Offset(sin(t * 83f), cos(t * 71f)) * (split * 4.dp.toPx())
            clipPath(upperHalf(size)) {
                translate(-shift + shake.x, -shift * 0.45f + shake.y) { drawWorld(state, t, motion, decor) }
            }
            clipPath(lowerHalf(size)) {
                translate(shift + shake.x, shift * 0.45f + shake.y) { drawWorld(state, t, motion, decor) }
            }
        } else {
            drawWorld(state, t, motion, decor)
        }

        drawSlash(slash, decor)
        if (flash > 0f) drawRect(FlashColor.copy(alpha = flash * 0.75f))
    }
}

/** Saque rápido com rastro da lâmina; ao voltar para a guarda, embainha devagar. */
private suspend fun swing(
    pose: SamuraiPose,
    draw: Animatable<Float, AnimationVector1D>,
    trail: Animatable<Float, AnimationVector1D>
) = coroutineScope {
    if (pose == SamuraiPose.STRIKE) {
        launch {
            trail.snapTo(1f)
            delay(140)
            trail.animateTo(0f, tween(520))
        }
        draw.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
    } else {
        trail.snapTo(0f)
        draw.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
    }
}

/** Relógio em segundos para tudo que se mexe sozinho (vento, respiração, pássaros). */
@Composable
private fun rememberSceneClock(): MutableFloatState {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time.floatValue = ((now - start) % 3_600_000_000_000L) / 1_000_000_000f
            }
        }
    }
    return time
}

private class FigureMotion(
    val leftDraw: Float,
    val rightDraw: Float,
    val leftTrail: Float,
    val rightTrail: Float
)

/** Elementos aleatórios da cena, sorteados uma vez só. */
private class SceneDecor {
    class Grain(val x: Float, val y: Float, val r: Float, val alpha: Float)
    class Blade(val x: Float, val height: Float, val jitter: Float, val phase: Float)
    class Petal(val y: Float, val speed: Float, val offset: Float, val phase: Float, val size: Float, val spin: Float, val deep: Boolean)
    class Cloud(val dy: Float, val length: Float, val x0: Float, val speed: Float, val thick: Float, val tilt: Float, val alpha: Float)
    class Spark(val along: Float, val side: Float, val r: Float, val alpha: Float)

    val grain: List<Grain>
    val grass: List<Blade>
    val petals: List<Petal>
    val clouds: List<Cloud>
    val sparks: List<Spark>

    init {
        val random = Random(11)
        grain = List(240) {
            Grain(random.nextFloat(), random.nextFloat() * HORIZON_FRACTION, 0.4f + random.nextFloat() * 1.1f, 0.03f + random.nextFloat() * 0.05f)
        }
        grass = List(90) {
            Blade(random.nextFloat(), 0.010f + random.nextFloat() * 0.028f, random.nextFloat() - 0.5f, random.nextFloat() * 6f)
        }
        petals = List(12) {
            Petal(
                y = 0.46f + random.nextFloat() * 0.3f,
                speed = 0.035f + random.nextFloat() * 0.04f,
                offset = random.nextFloat(),
                phase = random.nextFloat() * 6f,
                size = 3f + random.nextFloat() * 3f,
                spin = 1.5f + random.nextFloat() * 3f,
                deep = random.nextBoolean()
            )
        }
        clouds = listOf(
            Cloud(dy = -0.36f, length = 0.55f, x0 = 0.05f, speed = 0.0035f, thick = 0.010f, tilt = -0.002f, alpha = 0.45f),
            Cloud(dy = 0.06f, length = 0.62f, x0 = 0.55f, speed = 0.0025f, thick = 0.007f, tilt = 0.001f, alpha = 0.40f),
            Cloud(dy = 0.44f, length = 0.38f, x0 = 0.30f, speed = 0.0045f, thick = 0.005f, tilt = 0f, alpha = 0.35f),
            Cloud(dy = -0.70f, length = 0.30f, x0 = 0.85f, speed = 0.003f, thick = 0.004f, tilt = 0.001f, alpha = 0.22f)
        )
        sparks = List(14) {
            Spark(random.nextFloat(), random.nextFloat() * 2f - 1f, 1f + random.nextFloat() * 1.8f, 0.5f + random.nextFloat() * 0.5f)
        }
    }
}

private fun DrawScope.drawWorld(state: SceneState, t: Float, motion: FigureMotion, decor: SceneDecor) {
    val w = size.width
    val h = size.height
    val horizon = h * HORIZON_FRACTION
    val tension = state.tension
    // O vento acalma enquanto todos esperam o sinal
    val wind = (sin(t * 1.15f) * 0.7f + sin(t * 2.7f + 1f) * 0.3f) * (1f - 0.65f * tension)
    val zoom = 1f + 0.06f * tension

    scale(scaleX = zoom, scaleY = zoom, pivot = Offset(w * 0.5f, horizon)) {
        // Céu
        drawRect(
            brush = Brush.verticalGradient(
                0f to lerp(Iai.SkyHigh, Iai.SkyTenseHigh, tension),
                0.6f to lerp(Iai.SkyMid, Iai.SkyTenseMid, tension),
                1f to lerp(Iai.SkyLow, Iai.SkyTenseLow, tension),
                startY = 0f,
                endY = horizon
            ),
            size = Size(w, horizon + 4f)
        )
        // Textura de papel
        for (g in decor.grain) {
            drawCircle(Iai.Sumi.copy(alpha = g.alpha), radius = g.r * density, center = Offset(g.x * w, g.y * h))
        }

        // Sol com brilho
        val sunRadius = w * 0.30f
        val sunCenter = Offset(w * 0.5f, horizon - h * 0.16f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Iai.SunGlow.copy(alpha = 0.45f + 0.15f * tension),
                0.55f to Iai.SunGlow.copy(alpha = 0.18f),
                1f to Color.Transparent,
                center = sunCenter,
                radius = sunRadius * 1.9f
            ),
            radius = sunRadius * 1.9f,
            center = sunCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(Iai.SunCore, Iai.Sun, tension),
                0.8f to Iai.Sun,
                1f to lerp(Iai.Sun, Iai.Shu, 0.6f),
                center = sunCenter + Offset(-sunRadius * 0.2f, -sunRadius * 0.25f),
                radius = sunRadius * 1.2f
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // Nuvens de tinta passando devagar na frente do sol
        for (cloud in decor.clouds) {
            val x = (((cloud.x0 + t * cloud.speed) % 1.6f) - 0.4f) * w
            val y = sunCenter.y + cloud.dy * sunRadius
            drawBrushStroke(
                start = Offset(x, y),
                end = Offset(x + cloud.length * w, y + cloud.tilt * h),
                thickness = cloud.thick * h,
                color = Iai.Sumi.copy(alpha = cloud.alpha)
            )
        }

        // Corvos cruzando o céu
        for (i in 0 until 3) {
            val progress = (t * 0.022f + i * 0.11f) % 1.4f
            val center = Offset(
                w * (1.15f - progress),
                h * (0.455f + i * 0.022f) + sin(t * 0.8f + i) * h * 0.006f
            )
            drawBird(center, w * (0.014f - i * 0.002f), sin(t * 8.5f + i * 2.1f), Iai.Sumi.copy(alpha = 0.8f))
        }

        // Montanhas em camadas com névoa entre elas
        drawPath(mountainPath(w, horizon, h * 0.20f, seed = 1), Iai.MountainFar.copy(alpha = 0.38f))
        drawMist(horizon - h * 0.085f, h * 0.06f, w, lerp(Iai.SkyLow, Iai.SkyTenseLow, tension))
        drawPath(mountainPath(w, horizon, h * 0.12f, seed = 2), lerp(Iai.MountainFar, Iai.MountainNear, 0.5f).copy(alpha = 0.75f))
        drawMist(horizon - h * 0.04f, h * 0.04f, w, lerp(Iai.SkyLow, Iai.SkyTenseLow, tension))

        // Colina perto com um torii no alto
        val hill = Path().apply {
            moveTo(w * 0.55f, horizon)
            cubicTo(w * 0.70f, horizon - h * 0.01f, w * 0.76f, horizon - h * 0.052f, w * 0.86f, horizon - h * 0.055f)
            cubicTo(w * 0.94f, horizon - h * 0.057f, w * 0.98f, horizon - h * 0.03f, w * 1.05f, horizon - h * 0.02f)
            lineTo(w * 1.05f, horizon + 4f)
            close()
        }
        drawPath(hill, Iai.MountainNear)
        drawTorii(Offset(w * 0.86f, horizon - h * 0.055f), h * 0.028f, lerp(Iai.MountainNear, Iai.Sumi, 0.6f))

        // Chão com a borda iluminada pelo sol
        val ground = Path().apply {
            moveTo(0f, horizon)
            cubicTo(w * 0.3f, horizon - h * 0.012f, w * 0.7f, horizon + h * 0.012f, w, horizon - h * 0.004f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ground, Brush.verticalGradient(0f to Iai.GroundTop, 0.25f to Iai.Sumi, startY = horizon, endY = h))
        val edge = Path().apply {
            moveTo(0f, horizon)
            cubicTo(w * 0.3f, horizon - h * 0.012f, w * 0.7f, horizon + h * 0.012f, w, horizon - h * 0.004f)
        }
        drawPath(edge, Iai.SunGlow.copy(alpha = 0.35f * (1f - tension * 0.5f)), style = Stroke(width = 1.5.dp.toPx()))

        // Capim balançando
        for (blade in decor.grass) {
            val x = blade.x * w
            val bladeHeight = h * blade.height
            val base = Offset(x, horizon + h * 0.006f)
            val gust = wind + sin(t * 3f + blade.phase) * 0.15f
            val tip = Offset(x + gust * bladeHeight * 0.5f + blade.jitter * bladeHeight * 0.3f, base.y - bladeHeight)
            drawLine(Iai.Sumi, base, tip, strokeWidth = 1.3.dp.toPx(), cap = StrokeCap.Round)
        }

        // Personagens
        val figureHeight = h * 0.24f
        val feetY = horizon + h * 0.016f
        drawSamurai(
            feet = Offset(w * 0.24f, feetY),
            height = figureHeight,
            facingRight = true,
            style = state.leftStyle,
            draw = motion.leftDraw,
            crouch = tension,
            fall = state.leftFall,
            wind = wind,
            time = t,
            trail = motion.leftTrail,
            colors = figureColors(state.leftObi)
        )
        when (state.rightFigure) {
            RightFigure.SAMURAI -> drawSamurai(
                feet = Offset(w * 0.76f, feetY),
                height = figureHeight,
                facingRight = false,
                style = state.rightStyle,
                draw = motion.rightDraw,
                crouch = tension,
                fall = state.rightFall,
                wind = wind,
                time = t,
                trail = motion.rightTrail,
                colors = figureColors(state.rightObi)
            )

            RightFigure.MAKIWARA -> drawMakiwara(
                feet = Offset(w * 0.72f, feetY),
                height = figureHeight,
                cut = state.cut,
                wind = wind,
                colors = figureColors(Iai.Kin)
            )
        }

        // Pétalas de cerejeira no vento
        for (petal in decor.petals) {
            val progress = (t * petal.speed + petal.offset) % 1f
            val center = Offset(
                -0.1f * w + progress * 1.2f * w,
                petal.y * h + sin(t * 1.3f + petal.phase) * h * 0.018f + progress * h * 0.05f
            )
            val petalSize = petal.size * density
            rotate(degrees = t * petal.spin * 57f + petal.phase * 30f, pivot = center) {
                drawOval(
                    color = (if (petal.deep) Iai.SakuraDeep else Iai.Sakura).copy(alpha = 0.85f),
                    topLeft = center - Offset(petalSize, petalSize * 0.45f),
                    size = Size(petalSize * 2f, petalSize * 0.9f)
                )
            }
        }
    }

    // Vinheta nas laterais, mais forte na tensão
    val edgeAlpha = 0.16f + 0.24f * tension
    drawRect(
        Brush.horizontalGradient(
            0f to Iai.Sumi.copy(alpha = edgeAlpha),
            0.16f to Color.Transparent,
            0.84f to Color.Transparent,
            1f to Iai.Sumi.copy(alpha = edgeAlpha)
        )
    )
}

private fun figureColors(accent: Color) = SamuraiColors(
    ink = Iai.Sumi,
    detail = Iai.SumiLine,
    accent = accent,
    rim = Iai.Rim,
    steel = Iai.Steel,
    steelEdge = Iai.SteelEdge,
    gold = Iai.Kin
)

/** Pincelada: mais grossa no começo e afinando até a ponta. */
private fun DrawScope.drawBrushStroke(start: Offset, end: Offset, thickness: Float, color: Color) {
    val d = end - start
    val length = d.getDistance()
    if (length < 1f) return
    val n = Offset(-d.y / length, d.x / length)
    val path = Path().apply {
        moveTo(start.x, start.y)
        val a1 = start + d * 0.15f + n * thickness * 1.1f
        val a2 = start + d * 0.6f + n * thickness * 0.7f
        cubicTo(a1.x, a1.y, a2.x, a2.y, end.x, end.y)
        val b1 = start + d * 0.6f - n * thickness * 0.4f
        val b2 = start + d * 0.15f - n * thickness * 0.9f
        cubicTo(b1.x, b1.y, b2.x, b2.y, start.x, start.y)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawBird(center: Offset, span: Float, flap: Float, color: Color) {
    val lift = span * 0.55f * flap
    val path = Path().apply {
        moveTo(center.x - span, center.y - lift)
        cubicTo(center.x - span * 0.6f, center.y - lift * 0.4f - span * 0.2f, center.x - span * 0.2f, center.y - span * 0.1f, center.x, center.y)
        cubicTo(center.x + span * 0.2f, center.y - span * 0.1f, center.x + span * 0.6f, center.y - lift * 0.4f - span * 0.2f, center.x + span, center.y - lift)
    }
    drawPath(path, color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawMist(centerY: Float, height: Float, width: Float, color: Color) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to color.copy(alpha = 0.55f),
            1f to Color.Transparent,
            startY = centerY - height / 2f,
            endY = centerY + height / 2f
        ),
        topLeft = Offset(0f, centerY - height / 2f),
        size = Size(width, height)
    )
}

/** Portal torii visto de longe. */
private fun DrawScope.drawTorii(base: Offset, height: Float, color: Color) {
    val pillar = height * 0.09f
    val halfSpan = height * 0.42f
    drawRect(color, topLeft = Offset(base.x - halfSpan, base.y - height), size = Size(pillar, height))
    drawRect(color, topLeft = Offset(base.x + halfSpan - pillar, base.y - height), size = Size(pillar, height))
    drawRect(color, topLeft = Offset(base.x - halfSpan - pillar, base.y - height * 0.78f), size = Size(halfSpan * 2f + pillar * 2f, pillar * 0.9f))
    val top = Path().apply {
        moveTo(base.x - halfSpan * 1.45f, base.y - height * 1.12f)
        cubicTo(base.x - halfSpan * 0.6f, base.y - height * 0.98f, base.x + halfSpan * 0.6f, base.y - height * 0.98f, base.x + halfSpan * 1.45f, base.y - height * 1.12f)
        lineTo(base.x + halfSpan * 1.3f, base.y - height * 0.97f)
        cubicTo(base.x + halfSpan * 0.5f, base.y - height * 0.9f, base.x - halfSpan * 0.5f, base.y - height * 0.9f, base.x - halfSpan * 1.3f, base.y - height * 0.97f)
        close()
    }
    drawPath(top, color)
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
            cubicTo(x - step * 0.2f, valleyY, x - step * 0.1f, valleyY, x, valleyY)
        }
        lineTo(width, baseY)
        close()
    }
}

private fun upperHalf(size: Size) = Path().apply {
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height * SLASH_END)
    lineTo(0f, size.height * SLASH_START)
    close()
}

private fun lowerHalf(size: Size) = Path().apply {
    moveTo(0f, size.height * SLASH_START)
    lineTo(size.width, size.height * SLASH_END)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** O corte que atravessa a tela: brilho, pincelada branca afiada e faíscas. */
private fun DrawScope.drawSlash(slash: Float, decor: SceneDecor) {
    if (slash <= 0f || slash >= 1.5f) return
    val start = Offset(0f, size.height * SLASH_START)
    val end = Offset(size.width, size.height * SLASH_END)
    val progress = slash.coerceAtMost(1f)
    val fade = (1.5f - slash).coerceIn(0f, 1f)
    val current = start + (end - start) * progress
    val d = end - start
    val length = d.getDistance()
    val normal = Offset(-d.y / length, d.x / length)

    drawLine(Color.White.copy(alpha = 0.22f * fade), start, current, strokeWidth = 18.dp.toPx(), cap = StrokeCap.Round)
    drawBrushStroke(start, current, thickness = 5.dp.toPx() * (0.4f + 0.6f * fade), color = Color.White.copy(alpha = fade))
    for (spark in decor.sparks) {
        if (spark.along > progress) continue
        val drift = (slash - spark.along).coerceAtLeast(0f)
        val position = start + d * spark.along + normal * (spark.side * drift * 26.dp.toPx())
        drawCircle(Color.White.copy(alpha = spark.alpha * fade), radius = spark.r * density, center = position)
    }
}
