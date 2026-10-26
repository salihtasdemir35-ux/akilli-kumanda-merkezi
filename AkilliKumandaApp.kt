package com.akillikumanda.app

import android.app.Application
import com.akillikumanda.app.core.database.AppDatabase
import com.akillikumanda.app.data.catalog.CandidateRanker
import com.akillikumanda.app.data.catalog.CatalogRepository
import com.akillikumanda.app.data.remote.BackupManager
import com.akillikumanda.app.data.remote.SavedRemoteRepository
import com.akillikumanda.app.data.settings.SettingsRepository
import com.akillikumanda.app.platform.capability.DeviceCapabilityRepository
import com.akillikumanda.app.platform.infrared.AndroidIrHardware
import com.akillikumanda.app.platform.infrared.DefaultInfraredTransmitter
import com.akillikumanda.app.platform.infrared.InfraredTransmitter
import kotlinx.coroutines.Dispatchers

class AkilliKumandaApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

/** Basit elle bağımlılık enjeksiyonu. */
class AppContainer(app: Application) {
    private val irHardware = AndroidIrHardware(app)
    val infrared: InfraredTransmitter = DefaultInfraredTransmitter(irHardware, Dispatchers.IO)
    val capabilities = DeviceCapabilityRepository(app, irHardware)
    val catalog = CatalogRepository(app)
    val ranker = CandidateRanker(infrared::isFrequencySupported)
    private val database = AppDatabase.build(app)
    val savedRemotes = SavedRemoteRepository(database.savedRemoteDao())
    val backup = BackupManager(database.savedRemoteDao())
    val settings = SettingsRepository(app)
}
