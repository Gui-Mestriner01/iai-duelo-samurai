package com.gui.iai.data.stats

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class PlayerStats(
    val wins: Int = 0,
    val losses: Int = 0,
    val fouls: Int = 0,
    val bestReactionMs: Long? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val defeatedOpponents: Set<String> = emptySet(),
    /** Últimos tempos de reação válidos, do mais recente para o mais antigo. */
    val recentReactionsMs: List<Long> = emptyList(),
    val trainingStrikes: Int = 0
) {
    val totalDuels: Int get() = wins + losses
}

class StatsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val WINS = intPreferencesKey("stats_wins")
        val LOSSES = intPreferencesKey("stats_losses")
        val FOULS = intPreferencesKey("stats_fouls")
        val BEST = longPreferencesKey("stats_best_ms")
        val STREAK = intPreferencesKey("stats_streak")
        val BEST_STREAK = intPreferencesKey("stats_best_streak")
        val DEFEATED = stringSetPreferencesKey("stats_defeated")
        val RECENT = stringPreferencesKey("stats_recent")
        val TRAINING = intPreferencesKey("stats_training")
    }

    val stats: Flow<PlayerStats> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            PlayerStats(
                wins = prefs[Keys.WINS] ?: 0,
                losses = prefs[Keys.LOSSES] ?: 0,
                fouls = prefs[Keys.FOULS] ?: 0,
                bestReactionMs = prefs[Keys.BEST],
                currentStreak = prefs[Keys.STREAK] ?: 0,
                bestStreak = prefs[Keys.BEST_STREAK] ?: 0,
                defeatedOpponents = prefs[Keys.DEFEATED] ?: emptySet(),
                recentReactionsMs = decodeRecent(prefs[Keys.RECENT]),
                trainingStrikes = prefs[Keys.TRAINING] ?: 0
            )
        }

    /** Registra um golpe válido (duelo, treino ou dois jogadores). */
    suspend fun recordReaction(reactionMs: Long) {
        dataStore.edit { prefs ->
            val best = prefs[Keys.BEST]
            if (best == null || reactionMs < best) prefs[Keys.BEST] = reactionMs
            val recent = listOf(reactionMs) + decodeRecent(prefs[Keys.RECENT])
            prefs[Keys.RECENT] = recent.take(RECENT_LIMIT).joinToString(",")
        }
    }

    suspend fun recordFoul() {
        dataStore.edit { it[Keys.FOULS] = (it[Keys.FOULS] ?: 0) + 1 }
    }

    suspend fun recordTrainingStrike() {
        dataStore.edit { it[Keys.TRAINING] = (it[Keys.TRAINING] ?: 0) + 1 }
    }

    /** Registra o fim de um duelo contra a CPU. */
    suspend fun recordMatch(opponentId: String, won: Boolean) {
        dataStore.edit { prefs ->
            if (won) {
                prefs[Keys.WINS] = (prefs[Keys.WINS] ?: 0) + 1
                val streak = (prefs[Keys.STREAK] ?: 0) + 1
                prefs[Keys.STREAK] = streak
                if (streak > (prefs[Keys.BEST_STREAK] ?: 0)) prefs[Keys.BEST_STREAK] = streak
                prefs[Keys.DEFEATED] = (prefs[Keys.DEFEATED] ?: emptySet()) + opponentId
            } else {
                prefs[Keys.LOSSES] = (prefs[Keys.LOSSES] ?: 0) + 1
                prefs[Keys.STREAK] = 0
            }
        }
    }

    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.WINS)
            prefs.remove(Keys.LOSSES)
            prefs.remove(Keys.FOULS)
            prefs.remove(Keys.BEST)
            prefs.remove(Keys.STREAK)
            prefs.remove(Keys.BEST_STREAK)
            prefs.remove(Keys.DEFEATED)
            prefs.remove(Keys.RECENT)
            prefs.remove(Keys.TRAINING)
        }
    }

    private fun decodeRecent(raw: String?): List<Long> =
        raw?.split(",")?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    private companion object {
        const val RECENT_LIMIT = 15
    }
}
