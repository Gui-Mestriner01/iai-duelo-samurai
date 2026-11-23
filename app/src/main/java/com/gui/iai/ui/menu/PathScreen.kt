package com.gui.iai.ui.menu

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.domain.Opponent
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.SakuraPetals
import com.gui.iai.ui.components.ScreenHeader
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.duel.SpeedDots
import com.gui.iai.ui.theme.Iai

/** "O Caminho": os cinco oponentes em sequência. */
@Composable
fun PathScreen(
    onBack: () -> Unit,
    onChallenge: (Opponent) -> Unit,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    WashiBackground {
        SakuraPetals(Modifier.fillMaxSize(), count = 10)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenHeader(kanji = "道", title = "O Caminho", onBack = onBack)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Vença cada oponente para enfrentar o próximo. Cada duelo é melhor de 3 golpes.",
                style = MaterialTheme.typography.bodyMedium,
                color = Iai.SumiSoft
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .drawBehind {
                        // Linha de pincel ligando as etapas
                        val x = 32.dp.toPx()
                        drawLine(
                            color = Iai.Sumi.copy(alpha = 0.55f),
                            start = Offset(x, 30.dp.toPx()),
                            end = Offset(x, size.height - 30.dp.toPx()),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Opponent.entries.forEach { opponent ->
                    val unlocked = opponent.isUnlocked(stats.defeatedOpponents)
                    val defeated = opponent.id in stats.defeatedOpponents
                    PathStep(
                        opponent = opponent,
                        unlocked = unlocked,
                        defeated = defeated,
                        onClick = { if (unlocked) onChallenge(opponent) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PathStep(
    opponent: Opponent,
    unlocked: Boolean,
    defeated: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            HankoSeal(
                text = if (unlocked) opponent.kanji else "封",
                size = 54.dp,
                color = when {
                    defeated -> Iai.Shu
                    unlocked -> Iai.Sumi
                    else -> Iai.SumiFaded
                },
                rotation = if (opponent.ordinal % 2 == 0) -6f else 5f
            )
        }
        Spacer(Modifier.width(10.dp))
        PaperPanel(
            modifier = Modifier
                .weight(1f)
                .alpha(if (unlocked) 1f else 0.6f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (unlocked) opponent.displayName else "???",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Iai.Sumi,
                    modifier = Modifier.weight(1f)
                )
                if (defeated) {
                    Text("VENCIDO", style = MaterialTheme.typography.labelSmall, color = Iai.Shu)
                }
            }
            Text(
                text = if (unlocked) opponent.title.uppercase() else "SELADO",
                style = MaterialTheme.typography.labelSmall,
                color = Iai.Shu
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (unlocked) {
                    "“${opponent.quote}”"
                } else {
                    "Derrote ${Opponent.entries[opponent.ordinal - 1].displayName} para quebrar o selo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Iai.SumiSoft
            )
            Spacer(Modifier.height(8.dp))
            SpeedDots(level = opponent.speedLevel)
        }
    }
}
