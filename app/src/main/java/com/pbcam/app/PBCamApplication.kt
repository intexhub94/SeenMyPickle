package com.pbcam.app

import android.app.Application
import com.pbcam.app.data.RecordingRepository
import com.pbcam.app.data.SettingsStore
import com.pbcam.app.data.db.PBCamDatabase
import com.pbcam.app.worker.WorkerScheduler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PBCamApplication : Application() {
    val database: PBCamDatabase by lazy { PBCamDatabase.getInstance(this) }
    val recordingRepository: RecordingRepository by lazy { RecordingRepository(database) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Lazy schedule maintenance to prevent startup race
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                WorkerScheduler.scheduleMaintenance(this@PBCamApplication)
            }
        }
    }

    companion object {
        lateinit var instance: PBCamApplication
            private set
    }
}
