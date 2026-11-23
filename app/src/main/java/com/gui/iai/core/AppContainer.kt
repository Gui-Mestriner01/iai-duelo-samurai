package com.gui.iai.core

import android.content.Context
import com.gui.iai.audio.ClapDetector
import com.gui.iai.audio.SoundEngine
import com.gui.iai.data.iaiDataStore
import com.gui.iai.data.settings.SettingsRepository
import com.gui.iai.data.stats.StatsRepository
import com.gui.iai.game.Feedback
import com.gui.iai.game.RoundRunner
import com.gui.iai.haptics.Haptics
import com.gui.iai.sensors.ScabbardSensor
import com.gui.iai.sensors.SwingDetector

/** Injeção de dependências manual: cada serviço é criado uma vez e compartilhado. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository by lazy { SettingsRepository(appContext.iaiDataStore) }
    val statsRepository by lazy { StatsRepository(appContext.iaiDataStore) }

    val swingDetector by lazy { SwingDetector(appContext) }
    val scabbardSensor by lazy { ScabbardSensor(appContext) }
    val clapDetector by lazy { ClapDetector(appContext) }

    val soundEngine by lazy { SoundEngine() }
    val haptics by lazy { Haptics(appContext) }
    val feedback by lazy { Feedback(soundEngine, haptics) }

    val roundRunner by lazy { RoundRunner(swingDetector, scabbardSensor, clapDetector) }
}
