package com.gui.iai.ui.menu

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gui.iai.data.settings.ScabbardSource
import com.gui.iai.data.settings.Sensitivity
import com.gui.iai.data.settings.SignalMode
import com.gui.iai.ui.components.BrushButton
import com.gui.iai.ui.components.InkChip
import com.gui.iai.ui.components.InkDivider
import com.gui.iai.ui.components.InkToggle
import com.gui.iai.ui.components.PaperPanel
import com.gui.iai.ui.components.ScreenHeader
import com.gui.iai.ui.components.WashiBackground
import com.gui.iai.ui.theme.Iai

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var micDenied by remember { mutableStateOf(false) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micDenied = !granted
        if (granted) viewModel.setSignal(SignalMode.CLAP)
    }

    WashiBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ScreenHeader(kanji = "設", title = "Ajustes", onBack = onBack)

            // Sinal do duelo
            PaperPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Sinal do duelo")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SignalMode.entries.forEach { mode ->
                        InkChip(
                            text = mode.label,
                            selected = settings.signalMode == mode,
                            onClick = {
                                if (mode == SignalMode.CLAP && !viewModel.hasMicPermission()) {
                                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.setSignal(mode)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = settings.signalMode.description +
                        if (settings.signalMode == SignalMode.CLAP) {
                            ". Peça para alguém bater palma no momento que quiser. O microfone só fica ligado durante o golpe e nada é gravado."
                        } else {
                            "."
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = Iai.SumiSoft
                )
                if (micDenied) {
                    Text(
                        text = "Sem permissão do microfone, o modo Palma não funciona. Você pode liberar nas configurações do Android.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Iai.Shu
                    )
                }
            }

            // Golpe
            PaperPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Sensibilidade do golpe")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sensitivity.entries.forEach { sensitivity ->
                        InkChip(
                            text = sensitivity.label,
                            selected = settings.sensitivity == sensitivity,
                            onClick = { viewModel.setSensitivity(sensitivity) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(settings.sensitivity.description, style = MaterialTheme.typography.bodySmall, color = Iai.SumiSoft)
                Spacer(Modifier.height(12.dp))
                BrushButton(
                    text = "Testar golpe",
                    kanji = "試",
                    subtitle = "Veja ao vivo o que os sensores captam",
                    onClick = onCalibrate,
                    height = 66.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                InkDivider()
                InkToggle(
                    title = "Modo bainha",
                    description = "Antes do sinal, a espada fica \"embainhada\". Tirar antes da hora é desonra.",
                    checked = settings.scabbardMode,
                    onCheckedChange = viewModel::setScabbard
                )
                if (settings.scabbardMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScabbardSource.entries.forEach { source ->
                            val available = source == ScabbardSource.TOUCH || viewModel.sensors.scabbard
                            if (available) {
                                InkChip(
                                    text = source.label,
                                    selected = settings.scabbardSource == source,
                                    onClick = { viewModel.setScabbardSource(source) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = settings.scabbardSource.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Iai.SumiSoft
                    )
                }
            }

            // Som e vibração
            PaperPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Som e vibração")
                InkToggle(
                    title = "Sons",
                    description = "Taiko, vento, sinal de madeira e o corte da lâmina.",
                    checked = settings.soundEnabled,
                    onCheckedChange = viewModel::setSound
                )
                InkDivider()
                InkToggle(
                    title = "Vibração",
                    description = "Impacto do golpe, vitória e derrota.",
                    checked = settings.vibrationEnabled,
                    onCheckedChange = viewModel::setVibration
                )
            }

            // Sensores
            PaperPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Sensores do aparelho")
                SensorLine("Aceleração linear", viewModel.sensors.linearAcceleration, "detecta o corte")
                SensorLine("Giroscópio", viewModel.sensors.gyroscope, "confirma a rotação do golpe")
                SensorLine("Proximidade", viewModel.sensors.proximity, "modo bainha")
                SensorLine("Luz", viewModel.sensors.light, "modo bainha (reforço)")
                SensorLine("Microfone", viewModel.hasMicPermission(), "modo palma (permissão)")
                if (!viewModel.sensors.gyroscope) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sem giroscópio, o golpe é detectado só pela aceleração.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Iai.Shu
                    )
                }
            }

            PaperPanel(Modifier.fillMaxWidth()) {
                SectionTitle("Sobre")
                Text(
                    text = "IAI – Duelo Samurai v1.0\nFontes Yuji Boku e Shippori Mincho B1 (SIL Open Font License).\nOs tempos incluem o atraso da tela e dos sensores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Iai.SumiSoft
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = Iai.Sumi)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SensorLine(name: String, available: Boolean, use: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = if (available) "●" else "○",
            style = MaterialTheme.typography.bodyMedium,
            color = if (available) Iai.Shu else Iai.SumiFaded
        )
        Text(
            text = "  $name",
            style = MaterialTheme.typography.titleSmall,
            color = Iai.Sumi,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (available) use else "indisponível",
            style = MaterialTheme.typography.bodySmall,
            color = Iai.SumiSoft
        )
    }
}
