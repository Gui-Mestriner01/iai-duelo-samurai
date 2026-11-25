package com.gui.iai.domain

import kotlin.random.Random

/** Aparência dos inimigos comuns das Hordas. */
enum class EnemyLook { ASHIGARU, BANDIT, DESERTER }

/** Condição especial de uma onda. Muda a cena e o jeito de ler o sinal. */
enum class HordeModifier(val kanji: String, val title: String, val description: String) {
    NONE("", "", ""),
    NIGHT("夜", "Noite", "Ataque noturno: a cena escurece."),
    SNOWSTORM("吹", "Nevasca", "O sinal aparece fraco e some rápido."),
    FEINTS("偽", "Fintas", "Os inimigos fingem o sinal antes de atacar."),
    HALT("止", "Pare!", "Quando surgir 止, segure a lâmina."),
    RUSH("急", "Pressa", "Esperas curtas e inimigos mais rápidos.")
}

/** Um inimigo da onda. Se [boss] não for nulo, é um chefe do Caminho. */
data class HordeEnemy(
    val name: String,
    val look: EnemyLook,
    val accentArgb: Long,
    val minReactionMs: Long,
    val maxReactionMs: Long,
    val boss: Opponent? = null,
    /** Cada inimigo tem um id novo, para a cena mostrar ele entrando. */
    val serial: Int = 0
) {
    fun randomReactionMs(random: Random): Long = random.nextLong(minReactionMs, maxReactionMs + 1)
}

data class HordeState(
    val wave: Int = 1,
    /** Índice do inimigo atual dentro da onda (0 = primeiro). */
    val enemyIndex: Int = 0,
    val lives: Int = HordeEngine.MAX_LIVES,
    val score: Int = 0,
    val kills: Int = 0,
    val bossKills: Int = 0,
    val combo: Int = 0,
    val bestCombo: Int = 0,
    val modifier: HordeModifier = HordeModifier.NONE,
    val enemy: HordeEnemy,
    val nextSerial: Int = 1
) {
    val gameOver: Boolean get() = lives <= 0
    val enemiesInWave: Int get() = HordeEngine.enemiesInWave(wave)
    val multiplier: Float get() = HordeEngine.multiplier(combo)
}

/** O resultado de um golpe nas Hordas, para a tela mostrar o que aconteceu. */
data class HordeTurn(
    val state: HordeState,
    val points: Int = 0,
    val healed: Boolean = false,
    val waveCleared: Boolean = false
)

/**
 * As regras das Hordas, puras (sem Android): ondas cada vez mais rápidas,
 * 3 vidas, combo multiplicando os pontos, chefe a cada 5 ondas e
 * modificadores sorteados a partir da onda 3.
 */
object HordeEngine {
    const val MAX_LIVES = 3
    const val BOSS_EVERY = 5

    private val bossOrder = listOf(
        Opponent.RONIN, Opponent.AKANE, Opponent.GENSAI, Opponent.KUROGANE,
        Opponent.YUKI, Opponent.TENGU, Opponent.KAGE
    )

    private val names = mapOf(
        EnemyLook.ASHIGARU to listOf("Ashigaru", "Lanceiro do clã", "Sentinela"),
        EnemyLook.BANDIT to listOf("Bandido da estrada", "Ronin faminto", "Mercenário"),
        EnemyLook.DESERTER to listOf("Desertor", "Aprendiz rebelde", "Ladrão de templo")
    )

    private val accents = listOf(0xFF6B4E2E, 0xFF4F6B3A, 0xFF7A2E2E, 0xFF3E5C8A, 0xFF8A8F94, 0xFF5B3F8C)

    fun isBossWave(wave: Int): Boolean = wave % BOSS_EVERY == 0

    fun enemiesInWave(wave: Int): Int = if (isBossWave(wave)) 1 else (2 + (wave - 1) / 2).coerceAtMost(6)

    fun bossFor(wave: Int): Opponent = bossOrder[((wave / BOSS_EVERY) - 1).coerceAtLeast(0) % bossOrder.size]

    /** Reação dos inimigos comuns: começa lenta e acelera a cada onda. */
    fun speedFor(wave: Int, modifier: HordeModifier): Pair<Long, Long> {
        val rush = if (modifier == HordeModifier.RUSH) 40 else 0
        val center = (690 - 28 * (wave - 1) - rush).coerceAtLeast(300)
        return (center - 50L) to (center + 60L)
    }

    fun multiplier(combo: Int): Float = (1f + 0.1f * combo).coerceAtMost(3f)

    fun points(reactionMs: Long, opponentMs: Long, combo: Int, perfect: Boolean, boss: Boolean): Int {
        val speedBonus = (opponentMs - reactionMs).coerceAtLeast(0)
        val base = ((100 + speedBonus) * multiplier(combo)).toInt()
        return base * (if (perfect) 2 else 1) + (if (boss) 900 else 0)
    }

    fun modifierFor(wave: Int, random: Random): HordeModifier {
        if (wave < 3 || isBossWave(wave)) return HordeModifier.NONE
        if (random.nextFloat() < 0.3f) return HordeModifier.NONE
        val options = HordeModifier.entries.filter { it != HordeModifier.NONE }
        return options[random.nextInt(options.size)]
    }

    fun createEnemy(wave: Int, modifier: HordeModifier, serial: Int, random: Random): HordeEnemy {
        if (isBossWave(wave)) {
            val boss = bossFor(wave)
            // Na segunda volta da lista os chefes ficam um pouco mais rápidos
            val lap = (wave / BOSS_EVERY - 1) / bossOrder.size
            val faster = (lap * 20L)
            return HordeEnemy(
                name = boss.displayName,
                look = EnemyLook.BANDIT,
                accentArgb = 0xFFB8322A,
                minReactionMs = (boss.minReactionMs - faster).coerceAtLeast(240),
                maxReactionMs = (boss.maxReactionMs - faster).coerceAtLeast(280),
                boss = boss,
                serial = serial
            )
        }
        val look = EnemyLook.entries[random.nextInt(EnemyLook.entries.size)]
        val (min, max) = speedFor(wave, modifier)
        val pool = names.getValue(look)
        return HordeEnemy(
            name = pool[random.nextInt(pool.size)],
            look = look,
            accentArgb = accents[random.nextInt(accents.size)],
            minReactionMs = min,
            maxReactionMs = max,
            serial = serial
        )
    }

    fun start(random: Random): HordeState {
        val modifier = modifierFor(1, random)
        return HordeState(modifier = modifier, enemy = createEnemy(1, modifier, 0, random))
    }

    /** Você venceu o golpe: soma pontos, aumenta o combo e chama o próximo inimigo. */
    fun onKill(state: HordeState, reactionMs: Long?, opponentMs: Long, perfect: Boolean, random: Random): HordeTurn {
        val boss = state.enemy.boss != null
        val points = if (reactionMs != null) {
            points(reactionMs, opponentMs, state.combo, perfect, boss)
        } else {
            // O inimigo se precipitou: ponto simples, sem bônus de velocidade
            (100 * multiplier(state.combo)).toInt()
        }
        val combo = state.combo + 1
        val healed = perfect && state.lives < MAX_LIVES
        val lives = if (healed) state.lives + 1 else state.lives

        val lastOfWave = state.enemyIndex + 1 >= state.enemiesInWave
        val wave = if (lastOfWave) state.wave + 1 else state.wave
        val index = if (lastOfWave) 0 else state.enemyIndex + 1
        val modifier = if (lastOfWave) modifierFor(wave, random) else state.modifier
        val next = createEnemy(wave, modifier, state.nextSerial, random)

        return HordeTurn(
            state = state.copy(
                wave = wave,
                enemyIndex = index,
                lives = lives,
                score = state.score + points,
                kills = state.kills + 1,
                bossKills = state.bossKills + if (boss) 1 else 0,
                combo = combo,
                bestCombo = maxOf(state.bestCombo, combo),
                modifier = modifier,
                enemy = next,
                nextSerial = state.nextSerial + 1
            ),
            points = points,
            healed = healed,
            waveCleared = lastOfWave
        )
    }

    /** O inimigo venceu: perde uma vida e o combo; ele continua lá. */
    fun onHit(state: HordeState): HordeTurn =
        HordeTurn(state.copy(lives = (state.lives - 1).coerceAtLeast(0), combo = 0))

    /** Moedas ganhas no fim da partida. */
    fun coinsFor(state: HordeState): Int = state.kills / 2 + (state.wave - 1) * 2 + state.bossKills * 15
}
