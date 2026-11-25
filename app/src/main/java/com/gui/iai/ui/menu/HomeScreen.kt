package com.gui.iai.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.ui.components.BrushButton
import com.gui.iai.ui.components.Enso
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.SakuraPetals
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.game.formatSeconds
import com.gui.iai.ui.theme.BrushFont
import com.gui.iai.ui.theme.Iai

@Composable
fun HomeScreen(
    onDuel: () -> Unit,
    onVersus: () -> Unit,
    onTraining: () -> Unit,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
    onHorde: () -> Unit,
    onShop: () -> Unit,
    onAchievements: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
    progressViewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val progress by progressViewModel.progress.collectAsStateWithLifecycle()
    val next = viewModel.nextOpponent(stats)

    WashiBackground {
        SakuraPetals(Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HankoSeal(text = "侍", size = 40.dp, rotation = -8f)
                Spacer(Modifier.weight(1f))
                CoinBalance(progress.coins)
            }

            // Ensō com 居合 na vertical
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(230.dp),
                contentAlignment = Alignment.Center
            ) {
                Enso(Modifier.fillMaxSize(), strokeWidth = 15.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("居", fontFamily = BrushFont, fontSize = 74.sp, lineHeight = 78.sp, color = Iai.Sumi)
                    Text("合", fontFamily = BrushFont, fontSize = 74.sp, lineHeight = 78.sp, color = Iai.Sumi)
                }
                HankoSeal(
                    text = "斬",
                    size = 36.dp,
                    rotation = 10f,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 26.dp)
                )
            }

            Text(
                text = "IAI",
                fontFamily = BrushFont,
                fontSize = 46.sp,
                letterSpacing = 10.sp,
                color = Iai.Sumi
            )
            Text(
                text = "DUELO SAMURAI",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 7.sp,
                color = Iai.Shu
            )
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BrushButton(
                    text = "Duelo",
                    kanji = "決闘",
                    subtitle = "Próximo: ${next.displayName}, ${next.title}",
                    onClick = onDuel,
                    modifier = Modifier.fillMaxWidth()
                )
                BrushButton(
                    text = "Hordas",
                    kanji = "波",
                    subtitle = if (progress.hordeBestScore > 0) {
                        "Recorde: ${progress.hordeBestScore} pontos · onda ${progress.hordeBestWave}"
                    } else {
                        "Inimigos sem fim, chefes a cada 5 ondas"
                    },
                    onClick = onHorde,
                    modifier = Modifier.fillMaxWidth()
                )
                BrushButton(
                    text = "Dois jogadores",
                    kanji = "対戦",
                    subtitle = "Desafie alguém no mesmo celular",
                    onClick = onVersus,
                    modifier = Modifier.fillMaxWidth()
                )
                BrushButton(
                    text = "Treino",
                    kanji = "修行",
                    subtitle = "Afie seus reflexos no makiwara",
                    onClick = onTraining,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrushButton(
                        text = "Loja",
                        onClick = onShop,
                        centered = true,
                        height = 62.dp,
                        modifier = Modifier.weight(1f)
                    )
                    BrushButton(
                        text = "Conquistas",
                        onClick = onAchievements,
                        centered = true,
                        height = 62.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrushButton(
                        text = "Registros",
                        onClick = onRecords,
                        centered = true,
                        height = 62.dp,
                        modifier = Modifier.weight(1f)
                    )
                    BrushButton(
                        text = "Ajustes",
                        onClick = onSettings,
                        centered = true,
                        height = 62.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = buildString {
                    append("Vitórias ${stats.wins}")
                    append("  ·  Selos ${progress.achievements.size}")
                    append("  ·  Melhor reação ")
                    append(stats.bestReactionMs?.let(::formatSeconds) ?: "—")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Iai.SumiSoft,
                textAlign = TextAlign.Center
            )
        }
    }
}
