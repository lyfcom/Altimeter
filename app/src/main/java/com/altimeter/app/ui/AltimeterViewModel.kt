package com.altimeter.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.altimeter.app.data.*
import com.altimeter.app.measurement.Measurements
import com.altimeter.app.recording.RecordingController
import com.altimeter.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

/**
 * UI orchestration only; no sensors, Android service binding or database writes on the main thread.
 */
class AltimeterViewModel(
    private val measurements: Measurements,
    private val preferencesStore: PreferencesStore,
    private val tracks: TrackRepository,
    private val recorder: RecordingController,
) : ViewModel() {
    val measurement = measurements.state
    val recording = recorder.state
    val preferences =
        preferencesStore.values.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Preferences(),
        )
    val trips =
        tracks.trips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val messages = Channel<String>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    val trace =
        measurement
            .mapNotNull { it.reading }
            .runningFold(emptyList<Reading>()) { history, point ->
                val last = history.lastOrNull()
                when {
                    last != null &&
                        last.source == point.source &&
                        last.reference == point.reference &&
                        last.referenceId == point.referenceId &&
                        point.elapsedMillis - last.elapsedMillis < 1_000 -> history
                    last != null &&
                        (last.source != point.source ||
                            last.reference != point.reference ||
                            last.referenceId != point.referenceId) -> listOf(point)
                    else -> (history + point).takeLast(180)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() = measurements.refresh()

    fun zero() {
        if (!recording.value.active) measurements.zero()
    }

    fun setMode(value: MeasureMode) = change {
        if (!recording.value.active) preferencesStore.setMode(value)
    }

    fun setUnit(value: DistanceUnit) = change { preferencesStore.setUnit(value) }

    fun setTheme(value: ThemeMode) = change { preferencesStore.setTheme(value) }

    fun setInterval(value: Int) = change {
        if (!recording.value.active) preferencesStore.setInterval(value)
    }

    fun setScreen(value: Boolean) = change { preferencesStore.setScreen(value) }

    fun clearCalibration() = change {
        if (!recording.value.active) preferencesStore.setCalibration(null)
    }

    fun calibrate(metres: Double) = change {
        check(!recording.value.active) { "请先结束当前行程再校准" }
        val state = measurement.value
        check(state.barometer != null) { "请等待新的气压读数" }
        val pressure = state.frame.pressureHpa ?: error("没有气压读数")
        val reference =
            try {
                Barometry.calibrate(pressure, metres)
            } catch (_: IllegalArgumentException) {
                error("该海拔与当前气压不匹配，请确认单位和标高")
            }
        preferencesStore.setCalibration(Calibration(reference, System.currentTimeMillis()))
        messages.send("已校准气压高度，有效期 12 小时")
    }

    fun delete(id: String) = change { tracks.delete(id) }

    suspend fun points(id: String) = tracks.points(id)

    suspend fun export(id: String, gpx: Boolean, writer: java.io.Writer) =
        withContext(Dispatchers.IO) { tracks.export(id, gpx, writer) }

    fun clearRecordingError() = recorder.clearError()

    fun notify(message: String) = change { messages.send(message) }

    private fun change(block: suspend () -> Unit) =
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                messages.send(error.message ?: "操作未完成，请重试")
            }
        }
}
