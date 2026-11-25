package com.gui.iai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
import com.gui.iai.domain.Arena
import com.gui.iai.domain.EnemyLook
import com.gui.iai.domain.Opponent
import com.gui.iai.ui.theme.Iai
import kotlin.math.PI
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
    val cut: Float = 0f,
    /** Cenário: entardecer, neve ou noite. */
    val arena: Arena = Arena.DUSK,
    /** Cor do rastro da lâmina do jogador (item da loja). */
    val leftTrail: Color = Color.White,
    /** Iai Perfeito: câmera lenta mais longa e corte dourado. */
    val perfect: Boolean = false,
    /** Nevasca cobrindo a cena. */
    val blizzard: Boolean = false,
    /** Muda a cada inimigo novo (Hordas): a cena mostra ele chegando. */
    val rightSerial: Int = 0
)

/** Visual de cada oponente do Caminho. */
fun Opponent.sceneStyle(): SamuraiStyle = when (this) {
    Opponent.TARO -> SamuraiStyle.APPRENTICE
    Opponent.RONIN -> SamuraiStyle.RONIN
    Opponent.AKANE -> SamuraiStyle.AKANE
    Opponent.GENSAI -> SamuraiStyle.MASTER
    Opponent.KUROGANE -> SamuraiStyle.ONI
    Opponent.YUKI -> SamuraiStyle.YUKI
    Opponent.TENGU -> SamuraiStyle.TENGU
    Opponent.KAGE -> SamuraiStyle.KAGE
}

/** Cor da faixa (e das fitas) de cada oponente. */
fun Opponent.accentColor(): Color = when (this) {
    Opponent.TARO -> Iai.Ai
    Opponent.RONIN -> Iai.Ash
    Opponent.AKANE -> Iai.ShuBright
    Opponent.GENSAI -> Iai.Washi
    Opponent.KUROGANE -> Iai.Kin
    Opponent.YUKI -> Iai.Ice
    Opponent.TENGU -> Iai.ShuBright
    Opponent.KAGE -> Iai.Yoru
}

/** Visual dos inimigos comuns das Hordas. */
fun EnemyLook.sceneStyle(): SamuraiStyle = when (this) {
    EnemyLook.ASHIGARU -> SamuraiStyle.ASHIGARU
    EnemyLook.BANDIT -> SamuraiStyle.RONIN
    EnemyLook.DESERTER -> SamuraiStyle.APPRENTICE
}

/** Proporção da altura onde fica o horizonte (o chão escuro começa aqui). */
const val HORIZON_FRACTION = 0.70f

private const val SLASH_START = 0.40f
private const val PI_F = PI.toFloat()
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
    val director = remember { SceneDirector() }
    LaunchedEffect(director) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                director.advance(((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f))
                last = now
            }
        }
    }
    LaunchedEffect(state.leftPose) { director.setStriking(LEFT, state.leftPose == SamuraiPose.STRIKE) }
    LaunchedEffect(state.rightPose) { director.setStriking(RIGHT, state.rightPose == SamuraiPose.STRIKE) }
    val decor = remember { SceneDecor() }

    Canvas(modifier.fillMaxSize()) {
        val t = director.time.floatValue
        director.perfect = state.perfect
        director.observeSerial(state.rightSerial)

        if (split > 0.01f) {
            val shift = split * 16.dp.toPx()
            val shake = Offset(sin(t * 83f), cos(t * 71f)) * (split * 4.dp.toPx())
            clipPath(upperHalf(size)) {
                translate(-shift + shake.x, -shift * 0.45f + shake.y) { drawWorld(state, director, decor) }
            }
            clipPath(lowerHalf(size)) {
                translate(shift + shake.x, shift * 0.45f + shake.y) { drawWorld(state, director, decor) }
            }
        } else {
            drawWorld(state, director, decor)
        }

        drawSlash(slash, decor, if (state.perfect) Iai.PerfectGold else state.leftTrail)
        if (flash > 0f) drawRect(FlashColor.copy(alpha = flash * 0.75f))
    }
}

private const val LEFT = 0
private const val RIGHT = 1

/** Hit-stop: a cena congela por um instante quando a lâmina acerta. */
private const val HIT_STOP_S = 0.075f

/** Câmera lenta logo depois do impacto, enquanto a lâmina termina o arco. */
private const val SLOW_END_MS = 320f
private const val SLOW_SCALE = 0.3f
private const val PERFECT_SLOW_END_MS = 560f
private const val WALK_IN_S = 0.9f

private const val FALL_S = 1.2f
private const val CUT_S = 0.7f

/**
 * O "diretor" da cena: tem o próprio relógio, que pode congelar (hit-stop) e
 * andar devagar (câmera lenta), e sincroniza a queda de quem perdeu com o
 * instante exato em que a lâmina passa. Também guarda a física de cada personagem.
 */
@Stable
private class SceneDirector {
    /** Tempo da cena em segundos. Lido no desenho, então cada quadro redesenha. */
    val time = mutableFloatStateOf(0f)
    val physics: Array<FigurePhysics> = arrayOf(FigurePhysics(), FigurePhysics())

    private val strikeStart = arrayOfNulls<Float>(2)
    private val hitStopDone = booleanArrayOf(false, false)
    private var hitStop = 0f
    private val downAt = arrayOfNulls<Float>(2)
    private var cutAt: Float? = null

    /** Iai Perfeito deixa a câmera lenta mais longa. */
    var perfect = false

    private var serial = Int.MIN_VALUE
    private var walkInAt = -10f

    /** Um inimigo novo entrou: começa a animação de chegada. */
    fun observeSerial(value: Int) {
        if (serial == Int.MIN_VALUE) {
            serial = value
            return
        }
        if (value != serial) {
            serial = value
            walkInAt = time.floatValue
            physics[RIGHT] = FigurePhysics()
        }
    }

    /** 0 = acabou de aparecer na borda, 1 = chegou na posição do duelo. */
    fun walkIn(): Float = ((time.floatValue - walkInAt) / WALK_IN_S).coerceIn(0f, 1f)

    fun setStriking(side: Int, striking: Boolean) {
        if (striking) {
            if (strikeStart[side] == null) {
                strikeStart[side] = time.floatValue
                hitStopDone[side] = false
            }
        } else {
            strikeStart[side] = null
        }
    }

    /** Milissegundos desde o início do golpe deste lado (negativo = em guarda). */
    fun strikeMs(side: Int): Float {
        val start = strikeStart[side] ?: return -1f
        return (time.floatValue - start) * 1000f
    }

    fun advance(dtWall: Float) {
        var scale = 1f
        if (hitStop > 0f) {
            hitStop -= dtWall
            scale = 0f
        } else {
            for (side in 0..1) {
                val ms = strikeMs(side)
                if (ms >= STRIKE_IMPACT_MS && !hitStopDone[side]) {
                    hitStopDone[side] = true
                    hitStop = HIT_STOP_S
                    scale = 0f
                } else if (ms >= STRIKE_IMPACT_MS && ms < (if (perfect) PERFECT_SLOW_END_MS else SLOW_END_MS)) {
                    scale = minOf(scale, SLOW_SCALE)
                }
            }
        }
        time.floatValue += dtWall * scale
    }

    /** Instante em que um golpe que já começou acerta (ou agora, se não há golpe). */
    private fun impactTime(attacker: Int): Float {
        val now = time.floatValue
        val start = strikeStart[attacker] ?: return now
        return maxOf(now, start + STRIKE_IMPACT_MS / 1000f)
    }

    /**
     * A tela pede a queda (valor > 0); aqui ela começa no momento do impacto
     * e segue o relógio da cena, então respeita a câmera lenta.
     */
    fun fall(side: Int, requested: Float): Float {
        if (requested <= 0f) {
            downAt[side] = null
            return 0f
        }
        val at = downAt[side] ?: impactTime(1 - side).also { downAt[side] = it }
        return ((time.floatValue - at) / FALL_S).coerceIn(0f, 1f)
    }

    /** Corte do makiwara, também sincronizado com a lâmina do jogador. */
    fun cut(requested: Float): Float {
        if (requested <= 0f) {
            cutAt = null
            return 0f
        }
        val at = cutAt ?: impactTime(LEFT).also { cutAt = it }
        return ((time.floatValue - at) / CUT_S).coerceIn(0f, 1f)
    }

    /** Empurrão de câmera logo após o impacto. */
    fun impactPunch(): Float {
        var punch = 0f
        for (side in 0..1) {
            val ms = strikeMs(side)
            if (ms >= STRIKE_IMPACT_MS) {
                val after = (ms - STRIKE_IMPACT_MS) / 1000f
                punch = maxOf(punch, kotlin.math.exp(-after * 7f))
            }
        }
        return punch
    }
}

/** Elementos aleatórios da cena, sorteados uma vez só. */
private class SceneDecor {
    class Grain(val x: Float, val y: Float, val r: Float, val alpha: Float)
    class Blade(val x: Float, val height: Float, val jitter: Float, val phase: Float)
    class Petal(val y: Float, val speed: Float, val offset: Float, val phase: Float, val size: Float, val spin: Float, val deep: Boolean)
    class Cloud(val dy: Float, val length: Float, val x0: Float, val speed: Float, val thick: Float, val tilt: Float, val alpha: Float)
    class Spark(val along: Float, val side: Float, val r: Float, val alpha: Float)
    class Mote(val x: Float, val speed: Float, val offset: Float, val phase: Float, val r: Float)
    class Star(val x: Float, val y: Float, val r: Float, val speed: Float, val phase: Float)
    class Flake(val x: Float, val speed: Float, val offset: Float, val phase: Float, val r: Float)

    val grain: List<Grain>
    val grass: List<Blade>
    val petals: List<Petal>
    val clouds: List<Cloud>
    val sparks: List<Spark>
    val motes: List<Mote>
    val stars: List<Star>
    val flakes: List<Flake>

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
        motes = List(22) {
            Mote(random.nextFloat(), 0.02f + random.nextFloat() * 0.03f, random.nextFloat(), random.nextFloat() * 6f, 0.8f + random.nextFloat() * 1.2f)
        }
        stars = List(60) {
            Star(random.nextFloat(), random.nextFloat(), 0.5f + random.nextFloat() * 1.1f, 1f + random.nextFloat() * 3f, random.nextFloat() * 6f)
        }
        flakes = List(90) {
            Flake(random.nextFloat(), 0.05f + random.nextFloat() * 0.07f, random.nextFloat(), random.nextFloat() * 6f, 0.8f + random.nextFloat() * 1.6f)
        }
        sparks = List(14) {
            Spark(random.nextFloat(), random.nextFloat() * 2f - 1f, 1f + random.nextFloat() * 1.8f, 0.5f + random.nextFloat() * 0.5f)
        }
    }
}

/** Partículas que o vento leva em cada cenário. */
private enum class Particles { PETALS, SNOW, FIREFLIES }

/** Cores e clima de cada arena. */
private class ScenePalette(
    val skyHigh: Color,
    val skyMid: Color,
    val skyLow: Color,
    val tenseHigh: Color,
    val tenseMid: Color,
    val tenseLow: Color,
    val glow: Color,
    val core: Color,
    val disc: Color,
    val discEdge: Color,
    val discScale: Float,
    val rays: Boolean,
    val cloud: Color,
    val far: Color,
    val near: Color,
    val groundTop: Color,
    val rim: Color,
    val mote: Color,
    val particles: Particles,
    val stars: Boolean
)

private val DuskPalette = ScenePalette(
    skyHigh = Iai.SkyHigh, skyMid = Iai.SkyMid, skyLow = Iai.SkyLow,
    tenseHigh = Iai.SkyTenseHigh, tenseMid = Iai.SkyTenseMid, tenseLow = Iai.SkyTenseLow,
    glow = Iai.SunGlow, core = Iai.SunCore, disc = Iai.Sun, discEdge = lerp(Iai.Sun, Iai.Shu, 0.6f),
    discScale = 0.30f, rays = true,
    cloud = Iai.Sumi, far = Iai.MountainFar, near = Iai.MountainNear, groundTop = Iai.GroundTop,
    rim = Iai.Rim, mote = Iai.Rim, particles = Particles.PETALS, stars = false
)

private val SnowPalette = ScenePalette(
    skyHigh = Color(0xFFF1F3F6), skyMid = Color(0xFFD9E0E8), skyLow = Color(0xFFB3BFCC),
    tenseHigh = Color(0xFFDCE0E6), tenseMid = Color(0xFFB8C2CE), tenseLow = Color(0xFF8E9BAA),
    glow = Color(0xFFFFFFFF), core = Color(0xFFFFFDF7), disc = Color(0xFFE9E5DD), discEdge = Color(0xFFCFC8BC),
    discScale = 0.22f, rays = false,
    cloud = Color(0xFF6E7884), far = Color(0xFF8D99A6), near = Color(0xFF5D6873), groundTop = Color(0xFF2A2F36),
    rim = Color(0xFFEAF3FF), mote = Color.White, particles = Particles.SNOW, stars = false
)

private val NightPalette = ScenePalette(
    skyHigh = Color(0xFFCFCBE3), skyMid = Color(0xFF9E9CC6), skyLow = Color(0xFF5E648F),
    tenseHigh = Color(0xFFB9B4D4), tenseMid = Color(0xFF84829F), tenseLow = Color(0xFF474B70),
    glow = Color(0xFFE9E6FF), core = Color(0xFFFFFBEA), disc = Color(0xFFF1E9CF), discEdge = Color(0xFFD9CFAE),
    discScale = 0.24f, rays = false,
    cloud = Color(0xFF2A2840), far = Color(0xFF4C4E72), near = Color(0xFF2F3150), groundTop = Color(0xFF1E2030),
    rim = Color(0xFFBFD4FF), mote = Color(0xFFD8F27A), particles = Particles.FIREFLIES, stars = true
)

private fun Arena.palette(): ScenePalette = when (this) {
    Arena.DUSK -> DuskPalette
    Arena.SNOW -> SnowPalette
    Arena.NIGHT -> NightPalette
}

private fun DrawScope.drawWorld(state: SceneState, director: SceneDirector, decor: SceneDecor) {
    val t = director.time.floatValue
    val w = size.width
    val h = size.height
    val horizon = h * HORIZON_FRACTION
    val tension = state.tension
    val p = state.arena.palette()
    // O vento acalma enquanto todos esperam o sinal (na nevasca ele não para)
    val calm = if (state.blizzard) 0.1f else 0.65f
    val wind = (sin(t * 1.15f) * 0.7f + sin(t * 2.7f + 1f) * 0.3f) * (1f - calm * tension) +
        (if (state.blizzard) 0.8f else 0f)
    val zoom = 1f + 0.06f * tension + 0.035f * director.impactPunch()

    scale(scaleX = zoom, scaleY = zoom, pivot = Offset(w * 0.5f, horizon)) {
        // Céu
        drawRect(
            brush = Brush.verticalGradient(
                0f to lerp(p.skyHigh, p.tenseHigh, tension),
                0.6f to lerp(p.skyMid, p.tenseMid, tension),
                1f to lerp(p.skyLow, p.tenseLow, tension),
                startY = 0f,
                endY = horizon
            ),
            size = Size(w, horizon + 4f)
        )
        // Textura de papel
        for (g in decor.grain) {
            drawCircle(Iai.Sumi.copy(alpha = g.alpha), radius = g.r * density, center = Offset(g.x * w, g.y * h))
        }
        // Estrelas piscando (noite)
        if (p.stars) {
            for (star in decor.stars) {
                val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * star.speed + star.phase))
                drawCircle(
                    Color.White.copy(alpha = 0.55f * twinkle * (1f - 0.4f * tension)),
                    radius = star.r * density,
                    center = Offset(star.x * w, star.y * horizon * 0.85f)
                )
            }
        }

        // Sol (ou lua) com brilho
        val sunRadius = w * p.discScale
        val sunCenter = Offset(w * 0.5f, horizon - h * 0.16f)
        if (p.rays) drawSunRays(sunCenter, sunRadius, t, (1f - tension) * 0.09f + 0.03f, p.glow)
        drawCircle(
            brush = Brush.radialGradient(
                0f to p.glow.copy(alpha = 0.45f + 0.15f * tension),
                0.55f to p.glow.copy(alpha = 0.18f),
                1f to Color.Transparent,
                center = sunCenter,
                radius = sunRadius * 1.9f
            ),
            radius = sunRadius * 1.9f,
            center = sunCenter
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(p.core, p.disc, tension),
                0.8f to p.disc,
                1f to p.discEdge,
                center = sunCenter + Offset(-sunRadius * 0.2f, -sunRadius * 0.25f),
                radius = sunRadius * 1.2f
            ),
            radius = sunRadius,
            center = sunCenter
        )

        // Nuvens de tinta passando devagar
        for (cloud in decor.clouds) {
            val x = (((cloud.x0 + t * cloud.speed) % 1.6f) - 0.4f) * w
            val y = sunCenter.y + cloud.dy * sunRadius * (0.30f / p.discScale)
            drawBrushStroke(
                start = Offset(x, y),
                end = Offset(x + cloud.length * w, y + cloud.tilt * h),
                thickness = cloud.thick * h,
                color = p.cloud.copy(alpha = cloud.alpha)
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
        val mist = lerp(p.skyLow, p.tenseLow, tension)
        drawPath(mountainPath(w, horizon, h * 0.20f, seed = 1), p.far.copy(alpha = 0.38f))
        if (state.arena == Arena.SNOW) drawSnowCaps(w, horizon, h * 0.20f, seed = 1)
        drawMist(horizon - h * 0.085f, h * 0.06f, w, mist)
        drawPath(mountainPath(w, horizon, h * 0.12f, seed = 2), lerp(p.far, p.near, 0.5f).copy(alpha = 0.75f))
        drawMist(horizon - h * 0.04f, h * 0.04f, w, mist)

        // Colina perto com um torii no alto
        val hill = Path().apply {
            moveTo(w * 0.55f, horizon)
            cubicTo(w * 0.70f, horizon - h * 0.01f, w * 0.76f, horizon - h * 0.052f, w * 0.86f, horizon - h * 0.055f)
            cubicTo(w * 0.94f, horizon - h * 0.057f, w * 0.98f, horizon - h * 0.03f, w * 1.05f, horizon - h * 0.02f)
            lineTo(w * 1.05f, horizon + 4f)
            close()
        }
        drawPath(hill, p.near)
        drawTorii(Offset(w * 0.86f, horizon - h * 0.055f), h * 0.028f, lerp(p.near, Iai.Sumi, 0.6f))

        // Brilho no horizonte, atrás de quem está no chão
        drawMist(horizon - h * 0.012f, h * 0.05f, w, p.glow.copy(alpha = 0.8f - 0.4f * tension))

        // Chão com a borda iluminada
        val ground = Path().apply {
            moveTo(0f, horizon)
            cubicTo(w * 0.3f, horizon - h * 0.012f, w * 0.7f, horizon + h * 0.012f, w, horizon - h * 0.004f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(ground, Brush.verticalGradient(0f to p.groundTop, 0.25f to Iai.Sumi, startY = horizon, endY = h))
        val edge = Path().apply {
            moveTo(0f, horizon)
            cubicTo(w * 0.3f, horizon - h * 0.012f, w * 0.7f, horizon + h * 0.012f, w, horizon - h * 0.004f)
        }
        val edgeWidth = if (state.arena == Arena.SNOW) 4.dp.toPx() else 1.5.dp.toPx()
        val edgeColor = if (state.arena == Arena.SNOW) Color.White.copy(alpha = 0.8f) else p.glow.copy(alpha = 0.35f * (1f - tension * 0.5f))
        drawPath(edge, edgeColor, style = Stroke(width = edgeWidth))

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
            strikeMs = director.strikeMs(LEFT),
            crouch = tension,
            fall = director.fall(LEFT, state.leftFall),
            wind = wind,
            time = t,
            colors = figureColors(state.leftObi, p.rim, state.leftTrail),
            physics = director.physics[LEFT]
        )
        when (state.rightFigure) {
            RightFigure.SAMURAI -> {
                // Inimigo novo chega andando pela direita, balançando a cada passo
                val walk = director.walkIn()
                val eased = easeOutCubic(walk)
                val bob = if (walk < 1f) -kotlin.math.abs(sin(walk * PI_F * 4f)) * h * 0.006f else 0f
                drawSamurai(
                    feet = Offset(w * 0.76f + (1f - eased) * w * 0.40f, feetY + bob),
                    height = figureHeight,
                    facingRight = false,
                    style = state.rightStyle,
                    strikeMs = director.strikeMs(RIGHT),
                    crouch = tension,
                    fall = director.fall(RIGHT, state.rightFall),
                    wind = wind,
                    time = t,
                    colors = figureColors(state.rightObi, p.rim, Color.White),
                    physics = director.physics[RIGHT]
                )
            }

            RightFigure.MAKIWARA -> drawMakiwara(
                feet = Offset(w * 0.72f, feetY),
                height = figureHeight,
                cut = director.cut(state.cut),
                wind = wind,
                colors = figureColors(Iai.Kin, p.rim, Color.White)
            )
        }

        // Pétalas no vento ou neve caindo
        when (p.particles) {
            Particles.PETALS -> drawPetals(decor, t, w, h)
            Particles.SNOW -> drawSnow(decor, t, w, h, wind, heavy = state.blizzard)
            Particles.FIREFLIES -> Unit
        }
        if (state.blizzard && p.particles != Particles.SNOW) drawSnow(decor, t, w, h, wind, heavy = true)
    }

    // Poeira dourada no fim de tarde, brilho branco na neve, vaga-lumes à noite
    for (mote in decor.motes) {
        val rise = (t * mote.speed + mote.offset) % 1f
        val center = Offset(
            mote.x * w + sin(t * 0.7f + mote.phase) * w * 0.02f + wind * w * 0.01f,
            horizon - rise * h * 0.22f
        )
        val pulse = if (p.particles == Particles.FIREFLIES) 0.5f + 0.5f * sin(t * 3f + mote.phase) else 1f
        val alpha = sin(rise * PI_F) * 0.55f * (1f - 0.5f * tension) * pulse
        drawCircle(p.mote.copy(alpha = alpha), radius = mote.r * density, center = center)
    }

    // Nevasca: névoa branca por cima de tudo, mais forte na tensão
    if (state.blizzard) {
        drawRect(Color.White.copy(alpha = 0.22f + 0.25f * tension))
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

private fun DrawScope.drawPetals(decor: SceneDecor, t: Float, w: Float, h: Float) {
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

/** Flocos de neve caindo; na nevasca são mais e voam de lado. */
private fun DrawScope.drawSnow(decor: SceneDecor, t: Float, w: Float, h: Float, wind: Float, heavy: Boolean) {
    val count = if (heavy) decor.flakes.size else decor.flakes.size / 2
    val sideways = if (heavy) 0.55f else 0.12f
    for (i in 0 until count) {
        val flake = decor.flakes[i]
        val fall = (t * flake.speed * (if (heavy) 2.2f else 1f) + flake.offset) % 1f
        val x = ((flake.x + fall * sideways + sin(t * 0.9f + flake.phase) * 0.02f + wind * 0.01f) % 1.1f) * w
        val y = fall * h * 0.95f
        drawCircle(Color.White.copy(alpha = 0.85f), radius = flake.r * density, center = Offset(x, y))
    }
}

/** Neve no topo das montanhas distantes. */
private fun DrawScope.drawSnowCaps(width: Float, baseY: Float, amplitude: Float, seed: Int) {
    val random = Random(seed)
    val peaks = 6
    val step = width / peaks
    for (i in 1..peaks) {
        val x = step * i
        val peakY = baseY - amplitude * (0.45f + 0.55f * random.nextFloat())
        random.nextFloat()
        val top = Offset(x - step * 0.62f, peakY + amplitude * 0.02f)
        val cap = Path().apply {
            moveTo(top.x - step * 0.16f, top.y + amplitude * 0.10f)
            lineTo(top.x, top.y - amplitude * 0.02f)
            lineTo(top.x + step * 0.16f, top.y + amplitude * 0.10f)
            lineTo(top.x + step * 0.05f, top.y + amplitude * 0.07f)
            lineTo(top.x - step * 0.04f, top.y + amplitude * 0.11f)
            close()
        }
        drawPath(cap, Color.White.copy(alpha = 0.7f))
    }
}

private fun figureColors(accent: Color, rim: Color, trail: Color) = SamuraiColors(
    ink = Iai.Sumi,
    detail = Iai.SumiLine,
    accent = accent,
    rim = rim,
    steel = Iai.Steel,
    steelEdge = Iai.SteelEdge,
    gold = Iai.Kin,
    trail = trail
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

/** Raios de luz saindo do sol, bem suaves. */
private fun DrawScope.drawSunRays(center: Offset, radius: Float, t: Float, alpha: Float, color: Color) {
    val rays = 9
    val reach = radius * 3.2f
    for (i in 0 until rays) {
        val a = (i / rays.toFloat()) * 2f * PI_F + t * 0.02f
        val spread = 0.07f + 0.04f * sin(i * 1.7f)
        val path = Path().apply {
            moveTo(center.x, center.y)
            lineTo(center.x + cos(a - spread) * reach, center.y + sin(a - spread) * reach)
            lineTo(center.x + cos(a + spread) * reach, center.y + sin(a + spread) * reach)
            close()
        }
        drawPath(
            path,
            Brush.radialGradient(
                0f to color.copy(alpha = alpha),
                1f to Color.Transparent,
                center = center,
                radius = reach
            )
        )
    }
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
private fun DrawScope.drawSlash(slash: Float, decor: SceneDecor, tint: Color) {
    if (slash <= 0f || slash >= 1.5f) return
    val start = Offset(0f, size.height * SLASH_START)
    val end = Offset(size.width, size.height * SLASH_END)
    val progress = slash.coerceAtMost(1f)
    val fade = (1.5f - slash).coerceIn(0f, 1f)
    val current = start + (end - start) * progress
    val d = end - start
    val length = d.getDistance()
    val normal = Offset(-d.y / length, d.x / length)

    drawLine(tint.copy(alpha = 0.28f * fade), start, current, strokeWidth = 18.dp.toPx(), cap = StrokeCap.Round)
    drawBrushStroke(start, current, thickness = 5.dp.toPx() * (0.4f + 0.6f * fade), color = Color.White.copy(alpha = fade))
    drawBrushStroke(start, current, thickness = 2.dp.toPx() * (0.4f + 0.6f * fade), color = tint.copy(alpha = fade * 0.9f))
    for (spark in decor.sparks) {
        if (spark.along > progress) continue
        val drift = (slash - spark.along).coerceAtLeast(0f)
        val position = start + d * spark.along + normal * (spark.side * drift * 26.dp.toPx())
        drawCircle(tint.copy(alpha = spark.alpha * fade), radius = spark.r * density, center = position)
    }
}
