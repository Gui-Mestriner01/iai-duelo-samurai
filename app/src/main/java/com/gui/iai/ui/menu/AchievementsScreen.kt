package com.gui.iai.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.domain.Achievement
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.ScreenHeader
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.theme.Iai

/** Conquistas: cada selo carimbado é uma façanha do jogador. */
@Composable
fun AchievementsScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshAchievements() }
    val done = progress.achievements

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenHeader(kanji = "誉", title = "Conquistas", onBack = onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${done.size} de ${Achievement.entries.size} selos carimbados",
                style = MaterialTheme.typography.titleSmall,
                color = Iai.Shu
            )
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Achievement.entries.forEach { achievement ->
                    val unlocked = achievement.id in done
                    PaperPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (unlocked) 1f else 0.62f),
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HankoSeal(
                                text = if (unlocked) achievement.kanji else "？",
                                size = 52.dp,
                                color = if (unlocked) Iai.Shu else Iai.SumiFaded,
                                rotation = if (achievement.ordinal % 2 == 0) -6f else 5f
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(achievement.title, style = MaterialTheme.typography.titleMedium, color = Iai.Sumi)
                                Text(achievement.description, style = MaterialTheme.typography.bodySmall, color = Iai.SumiSoft)
                            }
                            Text(
                                text = if (unlocked) "✓" else "+${achievement.reward} 文",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (unlocked) Iai.Shu else Iai.Coin
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
