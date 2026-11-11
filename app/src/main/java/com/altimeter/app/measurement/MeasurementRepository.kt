package com.altimeter.app.measurement

import android.os.SystemClock
import com.altimeter.app.data.PreferencesStore
import com.altimeter.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

interface Measurements {
    val state: StateFlow<MeasurementState>

    fun refresh()

    fun zero()
}

data class MeasurementState(
    val frame: SensorFrame = SensorFrame(),
    val reading: Reading? = null,
    val satellite: Reading? = null,
    val barometer: Reading? = null,
    val calibrationExpired: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementRepository(
    feed: SensorFeed,
    preferences: PreferencesStore,
    scope: CoroutineScope,
) : Measurements {
    private val refreshVersion = MutableStateFlow(0L)
    private val zeroPoint = MutableStateFlow<Pair<Double, Long>?>(null)
    private var initialPressure: Pair<Double, Long>? = null
    override val state: StateFlow<MeasurementState> =
        refreshVersion
            .flatMapLatest {
                combine(feed.frames(), preferences.values, zeroPoint) { frame, prefs, zero ->
                    val now = frame.nowElapsed
                    val pressure =
                        frame.pressureHpa?.takeIf {
                            now - frame.pressureElapsed in 0..ReadingPolicy.PRESSURE_MAX_AGE
                        }
                    if (initialPressure == null && pressure != null)
                        initialPressure = pressure to now
                    val calibration =
                        prefs.calibration?.takeIf { it.isValid(System.currentTimeMillis()) }
                    val baseline = zero ?: initialPressure
                    val barometer =
                        pressure?.let {
                            if (calibration != null)
                                Reading(
                                    Barometry.altitude(it, calibration.seaLevelPressureHpa),
                                    Source.BAROMETER,
                                    HeightReference.SEA_LEVEL,
                                    null,
                                    frame.pressureElapsed,
                                    calibration.createdAtMillis,
                                )
                            else
                                baseline?.let { base ->
                                    Reading(
                                        Barometry.altitude(it) - Barometry.altitude(base.first),
                                        Source.BAROMETER,
                                        HeightReference.RELATIVE,
                                        null,
                                        frame.pressureElapsed,
                                        base.second,
                                    )
                                }
                        }
                    val satellite =
                        if (frame.locationPermission && frame.gpsEnabled)
                            ReadingPolicy.satellite(frame.fix, now)
                        else null
                    MeasurementState(
                        frame,
                        ReadingPolicy.select(prefs.mode, satellite, barometer),
                        satellite,
                        barometer,
                        prefs.calibration != null && calibration == null,
                    )
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), MeasurementState())

    override fun refresh() {
        refreshVersion.value++
    }

    override fun zero() {
        val current = state.value
        if (current.barometer != null)
            current.frame.pressureHpa?.let { zeroPoint.value = it to SystemClock.elapsedRealtime() }
    }
}
