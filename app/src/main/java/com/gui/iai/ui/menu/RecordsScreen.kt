package com.gui.iai.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.data.stats.PlayerStats
import com.gui.iai.domain.Opponent
import com.gui.iai.ui.components.BrushButton
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.InkDivider
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.ScreenHeader
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.game.formatSeconds
import com.gui.iai.ui.theme.Iai
import com.gui.iai.ui.theme.SerifFont

@Composable
fun RecordsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader(kanji = "記", title = "Registros", onBack = onBack)

            PaperPanel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    StatBlock("Vitórias", stats.wins.toString(), Modifier.weight(1f))
                    StatBlock("Derrotas", stats.losses.toString(), Modifier.weight(1f))
                    StatBlock("Precipitações", stats.fouls.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                InkDivider()
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    StatBlock("Melhor reação", stats.bestReactionMs?.let(::formatSeconds) ?: "—", Modifier.weight(1f))
                    StatBlock("Sequência", "${stats.currentStreak} (recorde ${stats.bestStreak})", Modifier.weight(1f))
                }
            }

            PaperPanel(Modifier.fillMaxWidth()) {
                Text("SELOS DO CAMINHO", style = MaterialTheme.typography.labelMedium, color = Iai.Sumi)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Opponent.entries.forEach { opponent ->
                        val defeated = opponent.id in stats.defeatedOpponents
                        HankoSeal(
                            text = opponent.kanji,
                            size = 48.dp,
                            color = if (defeated) Iai.Shu else Iai.SumiFaded.copy(alpha = 0.35f),
                            rotation = if (opponent.ordinal % 2 == 0) -5f else 6f
                        )
                    }
                }
            }

            PaperPanel(Modifier.fillMaxWidth()) {
                Text("ÚLTIMOS CORTES", style = MaterialTheme.typography.labelMedium, color = Iai.Sumi)
                Text(
                    text = "Barras menores = reação mais rápida",
                    style = MaterialTheme.typography.bodySmall,
                    color = Iai.SumiSoft
                )
                Spacer(Modifier.height(12.dp))
                ReactionBars(stats)
            }

            BrushButton(
                text = "Apagar registros",
                kanji = "消",
                onClick = { confirmReset = true },
                inkColor = Iai.Shu,
                kanjiColor = Iai.Washi,
                height = 62.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = Iai.Washi,
            title = { Text("Apagar registros?", fontFamily = SerifFont) },
            text = { Text("Vitórias, recordes e selos do Caminho serão apagados. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    confirmReset = false
                }) { Text("Apagar", color = Iai.Shu) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar", color = Iai.Sumi) }
            }
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Iai.Sumi, maxLines = 1)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Iai.SumiSoft)
    }
}

@Composable
private fun ReactionBars(stats: PlayerStats) {
    val times = stats.recentReactionsMs.reversed()
    if (times.isEmpty()) {
        Text(
            text = "Nenhum corte ainda. Vá ao treino!",
            style = MaterialTheme.typography.bodyMedium,
            color = Iai.SumiSoft
        )
        return
    }
    val max = (times.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
    val best = times.minOrNull()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        val gap = 6.dp.toPx()
        val barWidth = (size.width - gap * (times.size - 1)) / times.size
        times.forEachIndexed { index, ms ->
            val barHeight = size.height * (ms / max).coerceIn(0.08f, 1f)
            drawRoundRect(
                color = if (ms == best) Iai.Shu else Iai.Sumi,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = "Melhor destes: ${best?.let(::formatSeconds) ?: "—"}",
        style = MaterialTheme.typography.bodySmall,
        color = Iai.Shu
    )
}
