package com.altimeter.app

import android.app.Application
import androidx.room.Room
import com.altimeter.app.data.*
import com.altimeter.app.measurement.*
import com.altimeter.app.recording.RecordingController
import kotlinx.coroutines.*

/** Explicit composition root. No service locator inside features, no reflection-based DI. */
class AppContainer(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val database =
        Room.databaseBuilder(application, TrackDatabase::class.java, "altimeter-v2.db").build()
    val preferences = PreferencesStore(application)
    val tracks = TrackRepository(database)
    val measurements: Measurements =
        MeasurementRepository(AndroidSensorFeed(application), preferences, scope)
    val recording = RecordingController(measurements, preferences, tracks, scope)
}

class AltimeterApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
