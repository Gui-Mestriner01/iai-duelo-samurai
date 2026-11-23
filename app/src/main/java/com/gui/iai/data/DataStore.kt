package com.gui.iai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/** Um único arquivo de preferências para ajustes e registros do jogador. */
val Context.iaiDataStore: DataStore<Preferences> by preferencesDataStore(name = "iai")
