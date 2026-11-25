package com.gui.iai.data.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gui.iai.data.stats.PlayerStats
import com.gui.iai.domain.Achievement
import com.gui.iai.domain.ItemSlot
import com.gui.iai.domain.Loadout
import com.gui.iai.domain.ProgressSnapshot
import com.gui.iai.domain.Shop
import com.gui.iai.domain.ShopItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Moedas, loja, conquistas e recordes das Hordas. */
data class Progress(
    val coins: Int = 0,
    val owned: Set<String> = emptySet(),
    val loadout: Loadout = Loadout(),
    val achievements: Set<String> = emptySet(),
    val perfects: Int = 0,
    val flawlessWins: Int = 0,
    val trickRoundsWon: Int = 0,
    val hordeBestWave: Int = 0,
    val hordeBestScore: Int = 0,
    val hordeKills: Int = 0,
    val hordeBestCombo: Int = 0
) {
    fun owns(item: ShopItem): Boolean = Shop.isOwned(item, owned)
}

class ProgressRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val COINS = intPreferencesKey("progress_coins")
        val OWNED = stringSetPreferencesKey("progress_owned")
        val OBI = stringPreferencesKey("progress_obi")
        val BLADE = stringPreferencesKey("progress_blade")
        val ACHIEVEMENTS = stringSetPreferencesKey("progress_achievements")
        val PERFECTS = intPreferencesKey("progress_perfects")
        val FLAWLESS = intPreferencesKey("progress_flawless")
        val TRICK = intPreferencesKey("progress_trick_rounds")
        val HORDE_WAVE = intPreferencesKey("horde_best_wave")
        val HORDE_SCORE = intPreferencesKey("horde_best_score")
        val HORDE_KILLS = intPreferencesKey("horde_kills")
        val HORDE_COMBO = intPreferencesKey("horde_best_combo")
    }

    val progress: Flow<Progress> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            Progress(
                coins = prefs[Keys.COINS] ?: 0,
                owned = prefs[Keys.OWNED] ?: emptySet(),
                loadout = Loadout(
                    obi = ShopItem.fromId(prefs[Keys.OBI])?.takeIf { it.slot == ItemSlot.OBI } ?: ShopItem.defaultObi,
                    blade = ShopItem.fromId(prefs[Keys.BLADE])?.takeIf { it.slot == ItemSlot.BLADE } ?: ShopItem.defaultBlade
                ),
                achievements = prefs[Keys.ACHIEVEMENTS] ?: emptySet(),
                perfects = prefs[Keys.PERFECTS] ?: 0,
                flawlessWins = prefs[Keys.FLAWLESS] ?: 0,
                trickRoundsWon = prefs[Keys.TRICK] ?: 0,
                hordeBestWave = prefs[Keys.HORDE_WAVE] ?: 0,
                hordeBestScore = prefs[Keys.HORDE_SCORE] ?: 0,
                hordeKills = prefs[Keys.HORDE_KILLS] ?: 0,
                hordeBestCombo = prefs[Keys.HORDE_COMBO] ?: 0
            )
        }

    suspend fun addCoins(amount: Int) {
        if (amount <= 0) return
        dataStore.edit { it[Keys.COINS] = (it[Keys.COINS] ?: 0) + amount }
    }

    /** Compra um item. Devolve false se não deu (sem moedas ou já tinha). */
    suspend fun buy(item: ShopItem): Boolean {
        var bought = false
        dataStore.edit { prefs ->
            val purchase = Shop.buy(item, prefs[Keys.COINS] ?: 0, prefs[Keys.OWNED] ?: emptySet())
            if (purchase != null) {
                prefs[Keys.COINS] = purchase.coins
                prefs[Keys.OWNED] = purchase.owned
                bought = true
            }
        }
        return bought
    }

    suspend fun equip(item: ShopItem) {
        dataStore.edit { prefs ->
            if (!Shop.isOwned(item, prefs[Keys.OWNED] ?: emptySet())) return@edit
            when (item.slot) {
                ItemSlot.OBI -> prefs[Keys.OBI] = item.id
                ItemSlot.BLADE -> prefs[Keys.BLADE] = item.id
            }
        }
    }

    suspend fun recordPerfect() = increment(Keys.PERFECTS)

    suspend fun recordFlawlessWin() = increment(Keys.FLAWLESS)

    suspend fun recordTrickRoundWon() = increment(Keys.TRICK)

    /** Fim de uma partida das Hordas. Devolve true se foi um novo recorde de pontos. */
    suspend fun recordHorde(wave: Int, score: Int, kills: Int, bestCombo: Int): Boolean {
        var record = false
        dataStore.edit { prefs ->
            if (wave > (prefs[Keys.HORDE_WAVE] ?: 0)) prefs[Keys.HORDE_WAVE] = wave
            if (score > (prefs[Keys.HORDE_SCORE] ?: 0)) {
                prefs[Keys.HORDE_SCORE] = score
                record = true
            }
            prefs[Keys.HORDE_KILLS] = (prefs[Keys.HORDE_KILLS] ?: 0) + kills
            if (bestCombo > (prefs[Keys.HORDE_COMBO] ?: 0)) prefs[Keys.HORDE_COMBO] = bestCombo
        }
        return record
    }

    /**
     * Confere as conquistas com o estado atual e guarda as novas,
     * já somando a recompensa em moedas. Devolve as que acabaram de sair.
     */
    suspend fun checkAchievements(stats: PlayerStats): List<Achievement> {
        val current = progress.first()
        val snapshot = ProgressSnapshot(
            wins = stats.wins,
            bestReactionMs = stats.bestReactionMs,
            defeated = stats.defeatedOpponents,
            trainingStrikes = stats.trainingStrikes,
            perfects = current.perfects,
            flawlessWins = current.flawlessWins,
            trickRoundsWon = current.trickRoundsWon,
            hordeBestWave = current.hordeBestWave,
            hordeKills = current.hordeKills,
            hordeBestCombo = current.hordeBestCombo,
            purchases = Shop.purchasedCount(current.owned)
        )
        val fresh = Achievement.newlyUnlocked(snapshot, current.achievements)
        if (fresh.isNotEmpty()) {
            dataStore.edit { prefs ->
                prefs[Keys.ACHIEVEMENTS] = (prefs[Keys.ACHIEVEMENTS] ?: emptySet()) + fresh.map { it.id }
                prefs[Keys.COINS] = (prefs[Keys.COINS] ?: 0) + fresh.sumOf { it.reward }
            }
        }
        return fresh
    }

    suspend fun reset() {
        dataStore.edit { prefs ->
            listOf(
                Keys.COINS, Keys.PERFECTS, Keys.FLAWLESS, Keys.TRICK,
                Keys.HORDE_WAVE, Keys.HORDE_SCORE, Keys.HORDE_KILLS, Keys.HORDE_COMBO
            ).forEach { prefs.remove(it) }
            prefs.remove(Keys.OWNED)
            prefs.remove(Keys.OBI)
            prefs.remove(Keys.BLADE)
            prefs.remove(Keys.ACHIEVEMENTS)
        }
    }

    private suspend fun increment(key: Preferences.Key<Int>) {
        dataStore.edit { prefs: MutablePreferences -> prefs[key] = (prefs[key] ?: 0) + 1 }
    }
}
