package com.gui.iai.domain

import kotlin.random.Random

/** Onde o duelo acontece: muda céu, luz e partículas da cena. */
enum class Arena { DUSK, SNOW, NIGHT }

/**
 * Mecânicas especiais dos chefes. Cada uma muda o jeito de ler o sinal.
 */
enum class BossRule(val kanji: String, val title: String, val description: String) {
    FEINT(
        kanji = "偽",
        title = "Finta",
        description = "Antes do sinal verdadeiro aparece um sinal falso cinza. Quem corta na finta perde."
    ),
    HALT(
        kanji = "止",
        title = "Pare!",
        description = "Às vezes surge 止 em vez do sinal. Enquanto ele estiver na tela, não corte."
    ),
    SOUND_ONLY(
        kanji = "聴",
        title = "Só o som",
        description = "O sinal não aparece na tela: escute a madeira (e sinta a vibração)."
    ),
    QUICK_SIGNAL(
        kanji = "閃",
        title = "Relâmpago",
        description = "O sinal pisca por um instante e some. A espera também é mais curta."
    ),
    BLIZZARD(
        kanji = "吹",
        title = "Nevasca",
        description = "A neve cobre a tela e o sinal aparece fraco e rápido."
    ),
    ARMOR(
        kanji = "鎧",
        title = "Armadura",
        description = "Você precisa vencer 3 golpes; ele, só 2."
    )
}

/**
 * Os oito oponentes do Caminho, do mais lento ao mais rápido.
 * Todos os personagens são originais do IAI.
 */
enum class Opponent(
    val id: String,
    val displayName: String,
    val title: String,
    val kanji: String,
    val quote: String,
    /** Faixa de tempo de reação da CPU, em ms. */
    val minReactionMs: Long,
    val maxReactionMs: Long,
    /** Chance de o oponente se precipitar e perder o golpe. */
    val foulChance: Float,
    val rules: Set<BossRule> = emptySet(),
    val arena: Arena = Arena.DUSK
) {
    TARO(
        id = "taro",
        displayName = "Taro",
        title = "O Aprendiz",
        kanji = "見",
        quote = "Meu mestre disse que hoje eu estou pronto... eu acho.",
        minReactionMs = 560,
        maxReactionMs = 760,
        foulChance = 0.18f
    ),
    RONIN(
        id = "ronin",
        displayName = "Ronin Errante",
        title = "Lâmina sem senhor",
        kanji = "浪",
        quote = "Não luto por honra. Luto pelo arroz do jantar.",
        minReactionMs = 460,
        maxReactionMs = 560,
        foulChance = 0.10f,
        rules = setOf(BossRule.FEINT)
    ),
    AKANE(
        id = "akane",
        displayName = "Akane",
        title = "A Lâmina Rubra",
        kanji = "紅",
        quote = "O vento chega antes da minha espada. Escute.",
        minReactionMs = 390,
        maxReactionMs = 460,
        foulChance = 0.06f,
        rules = setOf(BossRule.QUICK_SIGNAL)
    ),
    GENSAI(
        id = "gensai",
        displayName = "Mestre Gensai",
        title = "Guardião do Dojo",
        kanji = "師",
        quote = "Quarenta anos esperando por um único golpe.",
        minReactionMs = 330,
        maxReactionMs = 390,
        foulChance = 0.03f,
        rules = setOf(BossRule.SOUND_ONLY)
    ),
    KUROGANE(
        id = "kurogane",
        displayName = "Oni de Kurogane",
        title = "O Demônio de Ferro",
        kanji = "鬼",
        quote = "Você já perdeu. Só ainda não percebeu.",
        minReactionMs = 300,
        maxReactionMs = 360,
        foulChance = 0.02f,
        rules = setOf(BossRule.ARMOR)
    ),
    YUKI(
        id = "yuki",
        displayName = "Yuki-Onna",
        title = "A Dama da Neve",
        kanji = "雪",
        quote = "Na nevasca, até o seu coração vai hesitar.",
        minReactionMs = 300,
        maxReactionMs = 350,
        foulChance = 0.02f,
        rules = setOf(BossRule.BLIZZARD, BossRule.FEINT),
        arena = Arena.SNOW
    ),
    TENGU(
        id = "tengu",
        displayName = "Tengu",
        title = "O Corvo da Montanha",
        kanji = "天",
        quote = "Kaa! Seus olhos veem, mas sua mão obedece?",
        minReactionMs = 285,
        maxReactionMs = 335,
        foulChance = 0.01f,
        rules = setOf(BossRule.HALT, BossRule.QUICK_SIGNAL),
        arena = Arena.DUSK
    ),
    KAGE(
        id = "kage",
        displayName = "Kage",
        title = "O Shogun das Sombras",
        kanji = "影",
        quote = "Todos os que você venceu treinaram para mim.",
        minReactionMs = 270,
        maxReactionMs = 320,
        foulChance = 0f,
        rules = setOf(BossRule.FEINT, BossRule.HALT, BossRule.ARMOR),
        arena = Arena.NIGHT
    );

    /** 1 (lento) a 8 (muito rápido). */
    val speedLevel: Int get() = ordinal + 1

    val isBoss: Boolean get() = rules.isNotEmpty()

    /** Golpes que o jogador precisa vencer (a armadura pede um a mais). */
    val winsToBeat: Int get() = if (BossRule.ARMOR in rules) 3 else 2

    fun randomReactionMs(random: Random = Random.Default): Long =
        random.nextLong(minReactionMs, maxReactionMs + 1)

    fun isUnlocked(defeated: Set<String>): Boolean {
        val previous = Opponent.entries.getOrNull(ordinal - 1) ?: return true
        return previous.id in defeated
    }

    val next: Opponent? get() = Opponent.entries.getOrNull(ordinal + 1)

    companion object {
        /** Golpes que a CPU precisa vencer. */
        const val OPPONENT_WINS_NEEDED = 2

        fun fromId(id: String?): Opponent? = Opponent.entries.firstOrNull { it.id == id }

        val final: Opponent get() = entries.last()
    }
}
