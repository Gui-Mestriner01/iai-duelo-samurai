package com.gui.iai.ui.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.sin

/*
 * Movimento dos samurais: a linha do tempo do golpe (poses-chave) e a física
 * simples de tecido e cabelo. Tudo em código, sem arquivos de animação.
 */

/** Momento em que a lâmina atravessa o oponente (ms desde o início do golpe). */
const val STRIKE_IMPACT_MS = 190f

/** Fim do golpe: depois disso o samurai já embainhou e voltou à guarda. */
const val STRIKE_TOTAL_MS = 2600f

/** Duração da animação de morte, em segundos de cena. */
const val DEATH_DURATION_S = 2.0f

/**
 * Uma pose do golpe.
 * @param draw progresso do saque (0 = na bainha, 1 = lâmina toda à frente)
 * @param arm posição da mão da frente (0 = no cabo, na cintura; 1 = braço estendido)
 * @param lunge avanço do corpo (base larga e baixa)
 * @param lean inclinação extra do tronco (negativo = para trás)
 * @param drop quanto o quadril abaixa
 * @param tilt giro extra da lâmina em radianos (usado no chiburi)
 * @param lift deslocamento vertical da mão (negativo = para cima)
 * @param sheathe noto: 0 = sem embainhar, 0..1 = leva a ponta até a boca da bainha,
 * 1..2 = a lâmina desliza para dentro
 */
class StrikePose(
    val draw: Float,
    val arm: Float,
    val lunge: Float,
    val lean: Float,
    val drop: Float,
    val tilt: Float,
    val lift: Float,
    val sheathe: Float = 0f
)

val GuardPose = StrikePose(0f, 0f, 0f, 0f, 0f, 0f, 0f)
private val ZanshinPose = StrikePose(1f, 1f, 1f, 0f, 0f, 0f, 0f)

/**
 * Linha do tempo do iai, inspirada no iaido de verdade:
 *  - 0–80 ms     antecipação: o corpo recua e abaixa
 *  - 80–220 ms   nukitsuke: saque e corte horizontal, passando um pouco do ponto
 *  - 220–340 ms  acomodação da pose
 *  - 340–950 ms  zanshin: pose congelada, atenção total
 *  - 950–1300 ms chiburi: ergue a lâmina e sacode o sangue com um golpe seco
 *  - 1300–1550   noto: a ponta da lâmina vai até a boca da bainha, por cima
 *  - 1550–2200   a lâmina desliza devagar para dentro
 *  - 2200–2600   volta à postura de pé
 */
fun strikePose(ms: Float): StrikePose {
    if (ms < 0f || ms >= STRIKE_TOTAL_MS) return GuardPose
    return when {
        ms < 80f -> {
            val e = easeOutCubic(ms / 80f)
            StrikePose(0f, 0f, 0f, -5f * e, 3f * e, 0f, 0f)
        }

        ms < 220f -> {
            val e = easeOutCubic((ms - 80f) / 140f)
            StrikePose(1.06f * e, 1.08f * e, 1.05f * e, mix(-5f, 2f, e), mix(3f, 0f, e), 0f, 0f)
        }

        ms < 340f -> {
            val k = easeInOut((ms - 220f) / 120f)
            StrikePose(mix(1.06f, 1f, k), mix(1.08f, 1f, k), mix(1.05f, 1f, k), mix(2f, 0f, k), 0f, 0f, 0f)
        }

        ms < 950f -> ZanshinPose

        ms < 1300f -> {
            val k = (ms - 950f) / 350f
            val tilt: Float
            val lift: Float
            when {
                k < 0.4f -> {
                    val e = easeOutCubic(k / 0.4f)
                    tilt = -0.7f * e
                    lift = -6f * e
                }

                k < 0.55f -> {
                    val e = easeInCubic((k - 0.4f) / 0.15f)
                    tilt = mix(-0.7f, 0.5f, e)
                    lift = mix(-6f, 3f, e)
                }

                else -> {
                    val e = 1f - easeInOut((k - 0.55f) / 0.45f)
                    tilt = 0.5f * e
                    lift = 3f * e
                }
            }
            StrikePose(1f, 1f, 1f, 0f, 0f, tilt, lift)
        }

        ms < 1550f -> {
            val k = (ms - 1300f) / 250f
            StrikePose(1f, 1f, mix(1f, 0.75f, easeInOut(k)), 0f, 0f, 0f, 0f, sheathe = k)
        }

        ms < 2200f -> {
            val k = (ms - 1550f) / 650f
            StrikePose(1f, 1f, mix(0.75f, 0.15f, easeInOut(k)), 0f, 0f, 0f, 0f, sheathe = 1f + k)
        }

        else -> {
            val k = easeInOut((ms - 2200f) / 400f)
            StrikePose(0f, 0f, 0.15f * (1f - k), 0f, 0f, 0f, 0f)
        }
    }
}

/** Brilho do rastro da lâmina: aparece no corte e some devagar. */
fun strikeTrail(ms: Float): Float = when {
    ms < 90f -> 0f
    ms < 320f -> 1f
    ms < 650f -> 1f - (ms - 320f) / 330f
    else -> 0f
}

// ---- Morte -----------------------------------------------------------------

/**
 * Pose de quem foi cortado, em cada instante da queda.
 * @param recoil tranco do corte (0..1, sobe e desce)
 * @param kneel pernas indo da posição de pé até os joelhos no chão (ou sentado, para trás)
 * @param lie pernas indo para o chão, com o corpo deitado
 * @param angle inclinação do tronco em graus (positivo = para a frente)
 * @param limp braços soltos, sem força (0..1)
 * @param droop cabeça caindo e olhos fechando (0..1)
 * @param landed progresso da poeira depois que o corpo bate no chão (0 = ainda no ar)
 */
class DeathPose(
    val recoil: Float,
    val kneel: Float,
    val lie: Float,
    val angle: Float,
    val limp: Float,
    val droop: Float,
    val landed: Float
) {
    companion object {
        val NONE = DeathPose(0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/** Instante (fração da queda) em que a espada escapa da mão. */
const val DEATH_DROP_AT = 0.07f

/** Instante em que o corpo bate no chão. */
const val DEATH_LAND_AT = 0.74f

/**
 * Linha do tempo da morte, no estilo dos filmes de samurai:
 *  - 0–0.1    tranco do corte, a espada escapa da mão
 *  - 0.1–0.24 um instante parado, cambaleando, com a cabeça baixando
 *  - 0.24–0.48 os joelhos cedem (ou, para trás, ele senta)
 *  - 0.46–0.74 o tronco tomba, acelerando como na gravidade
 *  - 0.74–0.9 bate no chão, quica um pouco e se acomoda
 *
 * @param backward true para quem cai de costas (os de armadura pesada)
 */
fun deathPose(fall: Float, backward: Boolean): DeathPose {
    if (fall <= 0f) return DeathPose.NONE
    val f = fall.coerceAtMost(1f)
    val recoil = sin(PI_FLOAT * (f / 0.09f).coerceIn(0f, 1f))
    val limp = easeOutCubic((f - 0.04f) / 0.18f)
    val droop = easeInOut((f - 0.08f) / 0.3f)
    val kneel = easeInOut((f - 0.24f) / 0.24f)
    val t = ((f - 0.46f) / (DEATH_LAND_AT - 0.46f)).coerceIn(0f, 1f)
    val lie = easeInOut(t)
    val gravity = t * t

    // Cambaleio enquanto ainda está de pé
    val wobble = sin((f - 0.08f) * 38f) * 3f * windowOf(f, 0.08f, 0.3f)
    val bounceK = ((f - DEATH_LAND_AT) / 0.16f).coerceIn(0f, 1f)
    val bounce = 7f * sin(PI_FLOAT * bounceK) * (1f - bounceK)

    val angle = if (backward) {
        -6f * recoil - wobble + mix(-12f * kneel, -88f, gravity) + bounce
    } else {
        -6f * recoil + wobble + mix(22f * kneel, 90f, gravity) - bounce
    }
    val landed = if (f < DEATH_LAND_AT) 0f else ((f - DEATH_LAND_AT) / (1f - DEATH_LAND_AT)).coerceIn(0f, 1f)
    return DeathPose(recoil, kneel, lie, angle, limp, droop, landed)
}

/** 1 dentro do intervalo, com bordas suaves; 0 fora. */
private fun windowOf(x: Float, start: Float, end: Float): Float {
    if (x <= start || x >= end) return 0f
    return sin(PI_FLOAT * (x - start) / (end - start))
}

// ---- Física de tecido e cabelo ---------------------------------------------

/**
 * Uma corrente de pontos presa pela ponta (fita, rabo de cavalo, laço).
 * Integração de Verlet + restrição de distância no estilo "segue o líder",
 * que é estável e barata para poucos pontos.
 */
class Chain(count: Int, val segment: Float) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    private val px = FloatArray(count)
    private val py = FloatArray(count)
    val size: Int get() = x.size

    fun reset(anchor: Offset, direction: Offset) {
        for (i in x.indices) {
            x[i] = anchor.x + direction.x * segment * i
            y[i] = anchor.y + direction.y * segment * i
            px[i] = x[i]
            py[i] = y[i]
        }
    }

    fun step(anchor: Offset, dt: Float, force: Offset, time: Float, damping: Float = 0.9f) {
        x[0] = anchor.x
        y[0] = anchor.y
        px[0] = anchor.x
        py[0] = anchor.y
        if (dt > 0f) {
            val dt2 = dt * dt
            for (i in 1 until x.size) {
                // Tremulação: cada ponto recebe uma rajada um pouco diferente
                val flutter = sin(time * 9f + i * 1.3f) * 45f
                val vx = (x[i] - px[i]) * damping
                val vy = (y[i] - py[i]) * damping
                px[i] = x[i]
                py[i] = y[i]
                x[i] += vx + force.x * dt2
                y[i] += vy + (force.y + flutter) * dt2
            }
        }
        repeat(3) {
            for (i in 1 until x.size) {
                val dx = x[i] - x[i - 1]
                val dy = y[i] - y[i - 1]
                val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.0001f)
                val k = segment / dist
                x[i] = x[i - 1] + dx * k
                y[i] = y[i - 1] + dy * k
            }
        }
    }

    fun point(i: Int) = Offset(x[i], y[i])
}

/**
 * Um ponto que segue outro com uma mola: atrasa, passa do ponto e volta.
 * Usado nas mangas e na barra da hakama para o tecido "chicotear" no saque.
 */
class Lag {
    private var x = 0f
    private var y = 0f
    private var vx = 0f
    private var vy = 0f
    private var ready = false

    /** Devolve o atraso atual (posição da mola − alvo), limitado a [limit]. */
    fun step(target: Offset, dt: Float, stiffness: Float = 170f, damping: Float = 12f, limit: Float = 9f): Offset {
        if (!ready) {
            x = target.x
            y = target.y
            ready = true
        }
        if (dt > 0f) {
            vx += (stiffness * (target.x - x) - damping * vx) * dt
            vy += (stiffness * (target.y - y) - damping * vy) * dt
            x += vx * dt
            y += vy * dt
        }
        return Offset((x - target.x).coerceIn(-limit, limit), (y - target.y).coerceIn(-limit, limit))
    }
}

/** Estado físico de um personagem (correntes e molas), guardado entre quadros. */
class FigurePhysics {
    private val chains = HashMap<String, Chain>()
    private val lags = HashMap<String, Lag>()
    private var lastTime = Float.NaN

    /** Intervalo desde o último quadro; 0 se o quadro se repete (cena desenhada duas vezes no corte). */
    var dt = 0f
        private set

    fun advance(time: Float) {
        dt = if (lastTime.isNaN() || time <= lastTime) 0f else (time - lastTime).coerceAtMost(1f / 30f)
        lastTime = time
    }

    fun chain(key: String, count: Int, segment: Float, anchor: Offset, restDirection: Offset): Chain =
        chains.getOrPut(key) { Chain(count, segment).also { it.reset(anchor, restDirection) } }

    fun existing(key: String): Chain? = chains[key]

    fun lag(key: String): Lag = lags.getOrPut(key) { Lag() }
}

// ---- Curvas de animação -----------------------------------------------------

internal const val PI_FLOAT = PI.toFloat()

internal fun easeOutCubic(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return 1f - (1f - c) * (1f - c) * (1f - c)
}

internal fun easeInCubic(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * c
}

internal fun easeInOut(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

internal fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t
