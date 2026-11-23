package com.gui.iai.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Quanto movimento é preciso para o app considerar que a espada foi sacada. */
enum class Sensitivity(
    val label: String,
    val description: String,
    /** Aceleração linear mínima, em m/s². */
    val accelThreshold: Float,
    /** Rotação mínima, em rad/s. */
    val gyroThreshold: Float
) {
    LEVE("Leve", "Um movimento curto já conta", 7f, 2.2f),
    NORMAL("Normal", "Equilíbrio entre precisão e velocidade", 11f, 3.4f),
    FIRME("Firme", "Exige um corte forte de verdade", 17f, 5.0f)
}

/** Como o sinal de "saque" é dado. */
enum class SignalMode(val label: String, val description: String) {
    VISUAL("Visual", "Um ! aparece na tela"),
    CLAP("Palma", "Alguém bate palma de verdade (usa o microfone)")
}

/** Como a espada é "embainhada" no modo bainha. */
enum class ScabbardSource(val label: String, val description: String) {
    TOUCH("Dedo na tela", "Segure o polegar na tela, como a mão no cabo. Soltar antes do sinal é desonra."),
    SENSOR("Sensor", "Cubra o topo da tela (proximidade/luz). Depende do aparelho.")
}

data class GameSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val scabbardMode: Boolean = false,
    val scabbardSource: ScabbardSource = ScabbardSource.TOUCH,
    val signalMode: SignalMode = SignalMode.VISUAL,
    val sensitivity: Sensitivity = Sensitivity.NORMAL
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SOUND = booleanPreferencesKey("sound_enabled")
        val VIBRATION = booleanPreferencesKey("vibration_enabled")
        val SCABBARD = booleanPreferencesKey("scabbard_mode")
        val SCABBARD_SOURCE = stringPreferencesKey("scabbard_source")
        val SIGNAL = stringPreferencesKey("signal_mode")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
    }

    val settings: Flow<GameSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            GameSettings(
                soundEnabled = prefs[Keys.SOUND] ?: true,
                vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
                scabbardMode = prefs[Keys.SCABBARD] ?: false,
                scabbardSource = prefs[Keys.SCABBARD_SOURCE]
                    ?.let { name -> ScabbardSource.entries.firstOrNull { it.name == name } }
                    ?: ScabbardSource.TOUCH,
                signalMode = prefs[Keys.SIGNAL]
                    ?.let { name -> SignalMode.entries.firstOrNull { it.name == name } }
                    ?: SignalMode.VISUAL,
                sensitivity = prefs[Keys.SENSITIVITY]
                    ?.let { name -> Sensitivity.entries.firstOrNull { it.name == name } }
                    ?: Sensitivity.NORMAL
            )
        }

    suspend fun setSound(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND] = enabled }
    }

    suspend fun setVibration(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATION] = enabled }
    }

    suspend fun setScabbardMode(enabled: Boolean) {
        dataStore.edit { it[Keys.SCABBARD] = enabled }
    }

    suspend fun setScabbardSource(source: ScabbardSource) {
        dataStore.edit { it[Keys.SCABBARD_SOURCE] = source.name }
    }

    suspend fun setSignalMode(mode: SignalMode) {
        dataStore.edit { it[Keys.SIGNAL] = mode.name }
    }

    suspend fun setSensitivity(sensitivity: Sensitivity) {
        dataStore.edit { it[Keys.SENSITIVITY] = sensitivity.name }
    }
}
