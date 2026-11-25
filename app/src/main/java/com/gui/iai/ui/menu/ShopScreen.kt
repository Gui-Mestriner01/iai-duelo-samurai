package com.gui.iai.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.data.progress.Progress
import com.gui.iai.domain.ItemSlot
import com.gui.iai.domain.ShopItem
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.ScreenHeader
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.theme.Iai
import kotlinx.coroutines.delay

/** Loja: faixas (obi) e lâminas (rastro do corte), compradas com mon (文). */
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    viewModel: ProgressViewModel = viewModel(factory = ProgressViewModel.Factory)
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        if (message != null) {
            delay(2_600)
            viewModel.clearMessage()
        }
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            ScreenHeader(kanji = "店", title = "Loja", onBack = onBack)
            Spacer(Modifier.height(10.dp))
            CoinBalance(progress.coins)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Ganhe mon vencendo golpes, duelos, Hordas e conquistas.",
                style = MaterialTheme.typography.bodySmall,
                color = Iai.SumiSoft
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ItemSlot.entries.forEach { slot ->
                    Text(slot.title, style = MaterialTheme.typography.headlineSmall, color = Iai.Sumi)
                    ShopItem.ofSlot(slot).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { item ->
                                ShopCard(
                                    item = item,
                                    progress = progress,
                                    onClick = { viewModel.buyOrEquip(item) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Iai.Washi,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Iai.Sumi)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            )
        }
    }
}

/** Saldo de moedas com o selo 文. */
@Composable
fun CoinBalance(coins: Int, modifier: Modifier = Modifier, dark: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        HankoSeal(text = "文", size = 30.dp, color = Iai.Coin, rotation = -6f)
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$coins mon",
            style = MaterialTheme.typography.titleMedium,
            color = if (dark) Iai.Washi else Iai.Sumi
        )
    }
}

@Composable
private fun ShopCard(
    item: ShopItem,
    progress: Progress,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val owned = progress.owns(item)
    val equipped = progress.loadout.obi == item || progress.loadout.blade == item
    val affordable = progress.coins >= item.price
    PaperPanel(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(12.dp)
    ) {
        ItemPreview(item, Modifier.fillMaxWidth().height(64.dp))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HankoSeal(text = item.kanji, size = 26.dp, color = Color(item.argb).takeIf { item.slot == ItemSlot.OBI } ?: Iai.Sumi, rotation = 5f)
            Spacer(Modifier.width(6.dp))
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Iai.Sumi, maxLines = 1)
        }
        Spacer(Modifier.height(2.dp))
        Text(item.description, style = MaterialTheme.typography.bodySmall, color = Iai.SumiSoft, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        val (label, color) = when {
            equipped -> "EQUIPADO" to Iai.Shu
            owned -> "EQUIPAR" to Iai.Sumi
            affordable -> "${item.price} 文" to Iai.Coin
            else -> "${item.price} 文" to Iai.SumiFaded
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, color, RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Amostra do item: a faixa pintada ou a lâmina com o rastro colorido. */
@Composable
private fun ItemPreview(item: ShopItem, modifier: Modifier = Modifier) {
    val color = Color(item.argb)
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (item.slot == ItemSlot.BLADE) Iai.Sumi else Iai.WashiDeep)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (item.slot) {
                ItemSlot.OBI -> {
                    // Faixa amarrada: a tira e as duas pontas do laço
                    drawRect(color, topLeft = Offset(w * 0.08f, h * 0.38f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.26f))
                    drawLine(color, Offset(w * 0.30f, h * 0.5f), Offset(w * 0.18f, h * 0.92f), strokeWidth = h * 0.12f, cap = StrokeCap.Round)
                    drawLine(color, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.40f, h * 0.95f), strokeWidth = h * 0.10f, cap = StrokeCap.Round)
                    drawRect(Iai.Sumi, topLeft = Offset(w * 0.08f, h * 0.38f), size = androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.26f), style = Stroke(width = 1.5f))
                }

                ItemSlot.BLADE -> {
                    // Rastro do corte e a lâmina por cima
                    val arc = Path().apply {
                        moveTo(w * 0.06f, h * 0.70f)
                        cubicTo(w * 0.30f, h * 0.15f, w * 0.70f, h * 0.10f, w * 0.94f, h * 0.38f)
                    }
                    drawPath(arc, color.copy(alpha = 0.35f), style = Stroke(width = h * 0.30f, cap = StrokeCap.Round))
                    drawPath(arc, color, style = Stroke(width = h * 0.08f, cap = StrokeCap.Round))
                    drawLine(Iai.Steel, Offset(w * 0.30f, h * 0.62f), Offset(w * 0.88f, h * 0.44f), strokeWidth = h * 0.06f, cap = StrokeCap.Round)
                    drawLine(Iai.Kin, Offset(w * 0.24f, h * 0.70f), Offset(w * 0.36f, h * 0.55f), strokeWidth = h * 0.05f)
                }
            }
        }
    }
}
