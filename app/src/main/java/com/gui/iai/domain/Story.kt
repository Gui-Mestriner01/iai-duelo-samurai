package com.gui.iai.domain

enum class Speaker { NARRATOR, HERO, OPPONENT }

data class StoryLine(val speaker: Speaker, val text: String)

/**
 * O Modo História: uma cena curta antes de cada duelo do Caminho e uma fala
 * depois da vitória. A trama leva até Kage, o Shogun das Sombras.
 */
object Story {

    fun intro(opponent: Opponent): List<StoryLine> = when (opponent) {
        Opponent.TARO -> listOf(
            narrator("Vila de Kiri, fim de tarde. Um garoto treme segurando uma espada maior que ele."),
            them("Você é o viajante que o Mestre Gensai mandou testar? Eu... eu estou pronto!"),
            hero("Respire, Taro. O golpe nasce do silêncio, não da pressa."),
            them("Certo! Silêncio! ...Já pode ser agora?")
        )

        Opponent.RONIN -> listOf(
            narrator("Ponte do vale. Um ronin come bolinhos de arroz encostado no corrimão."),
            them("Duelo? Custa três bolinhos. Ou a sua espada, se perder."),
            them("Cuidado com os meus olhos. Eu engano antes de cortar."),
            hero("Então vou esperar o sinal de verdade.")
        )

        Opponent.AKANE -> listOf(
            narrator("Campo de cerejeiras. As pétalas param no ar quando ela saca."),
            them("Um homem de máscara negra destruiu o meu clã. Você trabalha para ele?"),
            hero("Eu também procuro esse homem."),
            them("Então prove. Meu sinal é um relâmpago: pisque e ele some.")
        )

        Opponent.GENSAI -> listOf(
            narrator("Dojo da montanha. Velas apagadas. Só se ouve a madeira estalando."),
            them("Kage foi meu aluno. O melhor e o pior de todos."),
            them("Antes de enfrentá-lo, aprenda a ver com os ouvidos. Hoje, os olhos não vão te servir."),
            hero("Estou escutando, mestre.")
        )

        Opponent.KUROGANE -> listOf(
            narrator("Portão de Ferro. Um gigante de armadura bloqueia a estrada."),
            them("Kage me deu esta armadura. Nenhuma lâmina a atravessa duas vezes."),
            hero("Então eu corto três."),
            them("HA! Venha, pequeno samurai!")
        )

        Opponent.YUKI -> listOf(
            narrator("Passo nevado. O vento apaga as pegadas assim que elas nascem."),
            them("Tão quente... seu coração bate tão alto que eu escuto daqui."),
            them("Na minha nevasca você vai ver sinais que não existem."),
            hero("Vou cortar só o que for real.")
        )

        Opponent.TENGU -> listOf(
            narrator("Pico do Corvo. Asas negras cobrem o sol vermelho."),
            them("Kaa! Muitos chegaram até aqui. Todos tinham mãos impacientes."),
            them("Quando eu mostrar 止, você para. Se a sua mão obedecer, talvez mereça Kage."),
            hero("Minha mão obedece ao sinal. Só ao sinal.")
        )

        Opponent.KAGE -> listOf(
            narrator("Castelo das Sombras. A lua ilumina um trono vazio... e alguém atrás dele."),
            them("Gensai, Kurogane, Yuki, o Tengu... todos foram treinos para este momento."),
            them("Minhas fintas, meu 止 e minha armadura: tudo o que você aprendeu, contra você."),
            hero("Então tudo o que eu aprendi termina aqui.")
        )
    }

    fun epilogue(opponent: Opponent): String = when (opponent) {
        Opponent.TARO -> "Taro faz uma reverência: \"O Ronin está no vale. Ele aceita duelos por arroz.\""
        Opponent.RONIN -> "\"Ninguém resistia às minhas fintas...\" Ele joga um bilhete: a Lâmina Rubra procura quem destruiu o clã dela."
        Opponent.AKANE -> "Akane embainha devagar: \"O nome dele é Kage. O Mestre Gensai sabe onde ele está.\""
        Opponent.GENSAI -> "\"Siga para o Portão de Ferro. O general de Kage não deixa ninguém passar.\""
        Opponent.KUROGANE -> "A armadura racha. \"A Dama da Neve guarda o passo... ninguém volta da nevasca.\""
        Opponent.YUKI -> "Yuki-Onna se desfaz em neve: \"O Tengu vigia o último pico. Ele lê a sua mão antes de você.\""
        Opponent.TENGU -> "\"Kaa... O castelo das sombras te espera. Kage nunca perdeu um duelo.\""
        Opponent.KAGE -> "A máscara cai. Por trás dela, um rosto cansado: \"Finalmente... alguém mais rápido.\" O Caminho está completo."
    }

    private fun narrator(text: String) = StoryLine(Speaker.NARRATOR, text)
    private fun hero(text: String) = StoryLine(Speaker.HERO, text)
    private fun them(text: String) = StoryLine(Speaker.OPPONENT, text)
}
