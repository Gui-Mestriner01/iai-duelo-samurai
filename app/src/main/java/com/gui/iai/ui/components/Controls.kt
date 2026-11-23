package com.gui.iai.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.gui.iai.ui.theme.Iai

/** Painel de papel com moldura dupla de tinta. */
@Composable
fun PaperPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(Iai.Washi, Iai.WashiDeep)))
                drawRect(Iai.Sumi, style = Stroke(width = 2.dp.toPx()))
                val inset = 5.dp.toPx()
                drawRect(
                    color = Iai.Sumi.copy(alpha = 0.45f),
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Cantos vermelhos
                val corner = 10.dp.toPx()
                val w = 3.dp.toPx()
                drawLine(Iai.Shu, Offset(0f, 0f), Offset(corner, 0f), strokeWidth = w)
                drawLine(Iai.Shu, Offset(0f, 0f), Offset(0f, corner), strokeWidth = w)
                drawLine(Iai.Shu, Offset(size.width, size.height), Offset(size.width - corner, size.height), strokeWidth = w)
                drawLine(Iai.Shu, Offset(size.width, size.height), Offset(size.width, size.height - corner), strokeWidth = w)
            }
            .padding(contentPadding),
        content = content
    )
}

/** Botão redondo de voltar, em tinta. */
@Composable
fun InkBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inkColor: Color = Iai.Sumi,
    iconColor: Color = Iai.Washi
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(inkColor)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Voltar",
            tint = iconColor
        )
    }
}

/** Cabeçalho das telas: voltar + kanji vermelho + título em pincel. */
@Composable
fun ScreenHeader(
    kanji: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InkBackButton(onClick = onBack)
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = Iai.Sumi,
            modifier = Modifier.weight(1f)
        )
        HankoSeal(text = kanji, size = 48.dp, rotation = 5f)
    }
}

/** Opção selecionável em tinta (usada em grupos de escolha). */
@Composable
fun InkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val background by animateColorAsState(if (selected) Iai.Sumi else Color.Transparent, label = "chipBg")
    val content by animateColorAsState(if (selected) Iai.Washi else Iai.Sumi, label = "chipText")
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.5.dp, Iai.Sumi, RoundedCornerShape(4.dp))
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Iai.ShuBright)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = content)
        }
    }
}

/** Interruptor com "selo" vermelho quando ligado. */
@Composable
fun InkToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val view = LocalView.current
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 0.dp, label = "toggleThumb")
    val thumbColor by animateColorAsState(if (checked) Iai.Shu else Iai.SumiFaded, label = "toggleColor")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onCheckedChange(!checked)
            }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) Iai.Sumi else Iai.SumiFaded
            )
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = Iai.SumiSoft)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(50.dp)
                .height(28.dp)
                .border(1.5.dp, if (enabled) Iai.Sumi else Iai.SumiFaded, RoundedCornerShape(4.dp))
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .size(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(thumbColor)
            )
        }
    }
}

/** Linha divisória pintada. */
@Composable
fun InkDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .drawBehind {
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Iai.Sumi.copy(alpha = 0f), Iai.Sumi.copy(alpha = 0.6f), Iai.Sumi.copy(alpha = 0f))
                    ),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height
                )
            }
    )
}
