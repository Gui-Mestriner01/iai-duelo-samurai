package com.gui.iai.ui.calibration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.data.settings.Sensitivity
import com.gui.iai.ui.components.HankoSeal
import com.gui.iai.ui.components.InkChip
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.ScreenHeader
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.game.GameplayLifecycle
import com.gui.iai.ui.game.formatG
import com.gui.iai.ui.theme.Iai

private const val GRAVITY = 9.80665f

@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    viewModel: CalibrationViewModel = viewModel(factory = CalibrationViewModel.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val motion by viewModel.motion.collectAsStateWithLifecycle()
    val swings by viewModel.swings.collectAsStateWithLifecycle()
    val scabbard by viewModel.scabbard.collectAsStateWithLifecycle()
    GameplayLifecycle(onStop = {})

    val sensitivity = settings.sensitivity
    val accelLimitG = sensitivity.accelThreshold / GRAVITY
    val gyroLimitDps = Math.toDegrees(sensitivity.gyroThreshold.toDouble()).toFloat()

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader(kanji = "試", title = "Teste do golpe", onBack = onBack)

            Text(
                text = "Segure o celular como o cabo de uma espada e corte o ar. " +
                    "Quando a barra passar da marca vermelha, o corte conta.",
                style = MaterialTheme.typography.bodyMedium,
                color = Iai.SumiSoft
            )

            PaperPanel(Modifier.fillMaxWidth()) {
                Meter(
                    label = "Força do movimento",
                    value = motion.accelG,
                    max = 5f,
                    threshold = accelLimitG,
                    valueText = formatG(motion.accelG),
                    thresholdText = "limite ${formatG(accelLimitG)}"
                )
                Spacer(Modifier.height(18.dp))
                if (viewModel.hasGyroscope) {
                    Meter(
                        label = "Rotação",
                        value = motion.gyroDps,
                        max = 1_000f,
                        threshold = gyroLimitDps,
                        valueText = "${motion.gyroDps.toInt()} °/s",
                        thresholdText = "limite ${gyroLimitDps.toInt()} °/s"
                    )
                } else {
                    Text(
                        text = "Sem giroscópio: só a força conta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Iai.Shu
                    )
                }
            }

            PaperPanel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
                        if (swings.count > 0) {
                            // key: reinicia a animação do carimbo a cada corte
                            key(swings.count) {
                                HankoSeal(text = "斬", size = 72.dp, stampIn = true, rotation = (swings.count % 3 - 1) * 6f)
                            }
                        } else {
                            HankoSeal(text = "斬", size = 72.dp, color = Iai.SumiFaded.copy(alpha = 0.3f))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CORTES DETECTADOS", style = MaterialTheme.typography.labelMedium, color = Iai.SumiSoft)
                        Text(
                            text = swings.count.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            color = Iai.Sumi
                        )
                        Text(
                            text = swings.lastPeakG?.let { "Último corte: ${formatG(it)}" } ?: "Nenhum corte ainda",
                            style = MaterialTheme.typography.bodySmall,
                            color = Iai.SumiSoft
                        )
                    }
                }
            }

            if (viewModel.hasScabbard) {
                PaperPanel(Modifier.fillMaxWidth()) {
                    Text("BAINHA", style = MaterialTheme.typography.labelMedium, color = Iai.Sumi)
                    Text(
                        text = "Cubra o topo da tela com a mão ou encoste o celular na perna.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Iai.SumiSoft
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HankoSeal(
                            text = "鞘",
                            size = 56.dp,
                            color = if (scabbard.sheathed) Iai.Shu else Iai.SumiFaded.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = if (scabbard.sheathed) "Embainhada" else "Desembainhada",
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (scabbard.sheathed) Iai.Shu else Iai.Sumi
                            )
                            Text(
                                text = "Proximidade: " + when (scabbard.proximityNear) {
                                    true -> "coberto"
                                    false -> "livre"
                                    null -> "sem leitura"
                                } + (scabbard.proximityValue?.let { " (${it.toInt()})" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = Iai.SumiSoft
                            )
                            Text(
                                text = "Luz: " + (scabbard.lux?.let { "${it.toInt()} lux" } ?: "sem leitura") +
                                    (scabbard.luxBaseline?.let { " · ambiente ${it.toInt()} lux" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = Iai.SumiSoft
                            )
                        }
                    }
                }
            }

            PaperPanel(Modifier.fillMaxWidth()) {
                Text("SENSIBILIDADE", style = MaterialTheme.typography.labelMedium, color = Iai.Sumi)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sensitivity.entries.forEach { option ->
                        InkChip(
                            text = option.label,
                            selected = option == sensitivity,
                            onClick = { viewModel.setSensitivity(option) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Se cortes de verdade não contam, use Leve. Se a mão tremendo já conta, use Firme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Iai.SumiSoft,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

/** Barra horizontal com a marca vermelha do limite. */
@Composable
private fun Meter(
    label: String,
    value: Float,
    max: Float,
    threshold: Float,
    valueText: String,
    thresholdText: String
) {
    val fraction by animateFloatAsState(
        targetValue = (value / max).coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "meter"
    )
    val passed = value >= threshold
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = Iai.Sumi, modifier = Modifier.weight(1f))
            Text(
                text = valueText,
                style = MaterialTheme.typography.headlineSmall,
                color = if (passed) Iai.Shu else Iai.Sumi
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            val radius = CornerRadius(3.dp.toPx())
            drawRoundRect(
                color = Iai.Sumi,
                cornerRadius = radius,
                style = Stroke(width = 1.5.dp.toPx())
            )
            val inset = 3.dp.toPx()
            drawRoundRect(
                color = if (passed) Iai.Shu else Iai.Sumi,
                topLeft = Offset(inset, inset),
                size = Size((size.width - inset * 2) * fraction, size.height - inset * 2),
                cornerRadius = radius
            )
            val x = size.width * (threshold / max).coerceIn(0f, 1f)
            drawLine(
                color = Iai.Shu,
                start = Offset(x, -4.dp.toPx()),
                end = Offset(x, size.height + 4.dp.toPx()),
                strokeWidth = 3.dp.toPx()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = thresholdText,
            style = MaterialTheme.typography.bodySmall,
            color = Iai.Shu
        )
    }
}
