# IAI – Duelo Samurai (居合)

Um sinal. Um golpe. Uma chance.

O celular vira a sua katana: espere em silêncio, e quando o sinal aparecer, **corte o ar** o mais rápido que puder. Quem sacar primeiro vence.

## Modos
- **Duelo (決闘)**: "O Caminho" com 5 oponentes originais, do aprendiz Taro ao Oni de Kurogane. Cada duelo é melhor de 3 golpes, e vencer um oponente libera o próximo.
- **Dois jogadores (対戦)**: os dois jogadores alternam no mesmo celular, 3 tentativas cada. Vence o melhor tempo.
- **Treino (修行)**: cortar o makiwara e medir o tempo de reação, com recorde.
- **Registros (記録)**: vitórias, derrotas, precipitações, melhor reação, sequência, selos dos oponentes e gráfico dos últimos cortes.
- **Ajustes (設定)**: sinal visual ou por palma, sensibilidade, modo bainha, som e vibração.

## Sensores usados
| Sensor | Para quê |
|---|---|
| Aceleração linear | Detecta o golpe (movimento brusco sem a gravidade). Sem ele, usa o acelerômetro com filtro. |
| Giroscópio | Confirma que o celular girou no corte, para um tremor não contar como golpe |
| Toque ou proximidade + luz (opcional) | **Modo bainha**: segurar o polegar na tela (padrão) ou cobrir o topo da tela (proximidade OU queda de luz). Soltar antes do sinal é desonra |
| Microfone (opcional) | **Modo palma**: alguém bate palma de verdade e esse é o sinal |
| Vibração | Impacto, vitória, derrota e precipitação |

O tempo de reação é medido pelo **timestamp do próprio evento do sensor** (base `elapsedRealtimeNanos`), não pelo momento em que a tela atualiza. Os sensores e o microfone só ficam ligados durante um golpe.

## Tecnologia
Kotlin, Jetpack Compose, Material 3, Navigation Compose, MVVM, Coroutines/Flow e DataStore.
Todos os sons são **sintetizados no código** (taiko, sinal de madeira, corte, sino, vento), então o app não tem nenhum arquivo de áudio.
Toda a arte (samurais, cena, ensō, carimbos, pinceladas) é **desenhada com Canvas**.

Fontes: Yuji Boku e Shippori Mincho B1 (SIL Open Font License, em `licenses/`), recortadas apenas com os caracteres usados.

## Estrutura
```
com.gui.iai
├── core/        AppContainer (DI manual), fábrica de ViewModels
├── data/        DataStore: ajustes e registros
├── domain/      Oponentes
├── sensors/     SwingDetector (acel + giro), ScabbardSensor (proximidade)
├── audio/       ClapDetector (microfone), SoundEngine (síntese)
├── haptics/     Padrões de vibração
├── game/        RoundRunner (lógica de cada golpe), Feedback
└── ui/
    ├── theme/       cores, fontes
    ├── components/  papel washi, pincel, hanko, ensō, sakura, cena do duelo
    ├── game/        estrutura comum das telas de jogo
    ├── duel/ versus/ training/ menu/ navigation/
```

## Como testar no Galaxy S25 FE
1. Rodar pelo Android Studio (Run) com o celular conectado na Depuração USB.
2. **Treino**: segure o celular como o cabo de uma espada, espere o "!" e corte o ar. Se o corte não for detectado, mude para a sensibilidade **Leve**. Se o tremor da mão já contar, mude para **Firme**.
3. **Duelo**: comece pelo Taro, que às vezes se precipita.
4. **Modo bainha**: ative em Ajustes, segure o polegar na tela e, no sinal, solte e corte.
5. **Modo palma**: ative em Ajustes (o app pede o microfone) e peça para alguém bater palma.

> Os tempos incluem o atraso da tela (~16–30 ms) e dos sensores, então são comparáveis entre si, mas não são um tempo de reação "de laboratório".
