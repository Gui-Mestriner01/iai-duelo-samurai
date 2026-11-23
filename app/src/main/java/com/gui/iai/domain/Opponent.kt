package com.gui.iai.domain

import kotlin.random.Random

/**
 * Os cinco oponentes do Caminho, do mais lento ao mais rápido.
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
    val foulChance: Float
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
        foulChance = 0.10f
    ),
    AKANE(
        id = "akane",
        displayName = "Akane",
        title = "A Lâmina Rubra",
        kanji = "紅",
        quote = "O vento chega antes da minha espada. Escute.",
        minReactionMs = 390,
        maxReactionMs = 460,
        foulChance = 0.06f
    ),
    GENSAI(
        id = "gensai",
        displayName = "Mestre Gensai",
        title = "Guardião do Dojo",
        kanji = "師",
        quote = "Quarenta anos esperando por um único golpe.",
        minReactionMs = 330,
        maxReactionMs = 390,
        foulChance = 0.03f
    ),
    KUROGANE(
        id = "kurogane",
        displayName = "Oni de Kurogane",
        title = "O Demônio de Ferro",
        kanji = "鬼",
        quote = "Você já perdeu. Só ainda não percebeu.",
        minReactionMs = 270,
        maxReactionMs = 330,
        foulChance = 0f
    );

    /** 1 (lento) a 5 (muito rápido). */
    val speedLevel: Int get() = ordinal + 1

    fun randomReactionMs(random: Random = Random.Default): Long =
        random.nextLong(minReactionMs, maxReactionMs + 1)

    fun isUnlocked(defeated: Set<String>): Boolean {
        val previous = Opponent.entries.getOrNull(ordinal - 1) ?: return true
        return previous.id in defeated
    }

    val next: Opponent? get() = Opponent.entries.getOrNull(ordinal + 1)

    companion object {
        fun fromId(id: String?): Opponent? = Opponent.entries.firstOrNull { it.id == id }
    }
}
