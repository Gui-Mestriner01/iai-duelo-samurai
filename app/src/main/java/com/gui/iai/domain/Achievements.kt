package com.gui.iai.domain

/** Tudo o que as conquistas precisam saber sobre o jogador. */
data class ProgressSnapshot(
    val wins: Int = 0,
    val bestReactionMs: Long? = null,
    val defeated: Set<String> = emptySet(),
    val trainingStrikes: Int = 0,
    val perfects: Int = 0,
    val flawlessWins: Int = 0,
    val trickRoundsWon: Int = 0,
    val hordeBestWave: Int = 0,
    val hordeKills: Int = 0,
    val hordeBestCombo: Int = 0,
    val purchases: Int = 0
)

/** Conquistas, cada uma com o seu selo (hanko). */
enum class Achievement(
    val id: String,
    val kanji: String,
    val title: String,
    val description: String,
    val reward: Int,
    private val test: (ProgressSnapshot) -> Boolean
) {
    FIRST_BLOOD("first_blood", "初", "Primeiro duelo", "Vença seu primeiro duelo.", 20, { it.wins >= 1 }),
    PERFECT("perfect", "極", "Iai Perfeito", "Corte em até ${Rewards.PERFECT_MS} ms.", 30, { it.perfects >= 1 }),
    LIGHTNING("lightning", "雷", "Relâmpago", "Corte em até 220 ms.", 60, { (it.bestReactionMs ?: Long.MAX_VALUE) <= 220 }),
    FLAWLESS("flawless", "完", "Intocável", "Vença um duelo sem perder nenhum golpe.", 40, { it.flawlessWins >= 1 }),
    SHARP_EYES("sharp_eyes", "眼", "Olhos atentos", "Vença 5 golpes contra fintas ou 止.", 40, { it.trickRoundsWon >= 5 }),
    ONI_HUNTER("oni_hunter", "鬼", "Caçador de Oni", "Derrote o Oni de Kurogane.", 60, { Opponent.KUROGANE.id in it.defeated }),
    LEGEND("legend", "伝", "Lenda", "Derrote Kage, o Shogun das Sombras.", 150, { Opponent.KAGE.id in it.defeated }),
    SURVIVOR("survivor", "波", "Sobrevivente", "Chegue à onda 10 nas Hordas.", 60, { it.hordeBestWave >= 10 }),
    HUNDRED("hundred", "百", "Cem cortes", "Derrote 100 inimigos nas Hordas.", 80, { it.hordeKills >= 100 }),
    FURY("fury", "連", "Fúria", "Faça um combo de 10 nas Hordas.", 50, { it.hordeBestCombo >= 10 }),
    COLLECTOR("collector", "宝", "Colecionador", "Compre 5 itens na loja.", 50, { it.purchases >= 5 }),
    DISCIPLINE("discipline", "修", "Disciplina", "Faça 100 cortes no treino.", 40, { it.trainingStrikes >= 100 });

    fun isMet(snapshot: ProgressSnapshot): Boolean = test(snapshot)

    companion object {
        fun fromId(id: String): Achievement? = entries.firstOrNull { it.id == id }

        /** Conquistas que o jogador acabou de cumprir e ainda não tinha. */
        fun newlyUnlocked(snapshot: ProgressSnapshot, already: Set<String>): List<Achievement> =
            entries.filter { it.id !in already && it.isMet(snapshot) }
    }
}
