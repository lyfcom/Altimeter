package com.altimeter.app.recording

import android.os.SystemClock
import com.altimeter.app.data.*
import com.altimeter.app.measurement.*
import com.altimeter.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes recording commands. Only this class writes an active trip. */
class RecordingController(
    private val measurements: Measurements,
    private val preferences: PreferencesStore,
    private val tracks: TrackRepository,
    private val scope: CoroutineScope,
) {
    data class State(
        val trip: TripEntity? = null,
        val starting: Boolean = false,
        val error: String? = null,
        val waitingForSource: Boolean = false,
    ) {
        val active
            get() = starting || trip != null
    }

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val commands = Mutex()
    private var job: Job? = null
    private val recovered = scope.async { tracks.recoverInterrupted() }

    suspend fun start(): Boolean =
        commands.withLock {
            if (mutable.value.active) return@withLock true
            mutable.value = State(starting = true)
            try {
                recovered.await()
                val prefs = preferences.values.first()
                val initial = measurements.state.value.reading ?: error("还没有有效高度，请等待测量后再开始")
                var trip =
                    TripEntity(
                        UUID.randomUUID().toString(),
                        "行程 · " +
                            DateTimeFormatter.ofPattern("MM/dd HH:mm")
                                .withZone(ZoneId.systemDefault())
                                .format(Instant.now()),
                        System.currentTimeMillis(),
                        startedElapsed = SystemClock.elapsedRealtime(),
                    )
                tracks.create(trip)
                mutable.value = State(trip)
                val accumulator = TrackAccumulator()
                job =
                    scope.launch {
                        try {
                            coroutineScope {
                                // Keeps the hardware subscription alive while the activity is
                                // stopped.
                                val subscription = launch { measurements.state.collect() }
                                var lastElapsed = -1L
                                while (isActive) {
                                    val state = measurements.state.value
                                    // Lock the source and datum for the trip. A lost source creates
                                    // a gap, not a height jump.
                                    val reading =
                                        when (initial.source) {
                                            Source.GNSS -> state.satellite
                                            Source.BAROMETER -> state.barometer
                                        }?.takeIf {
                                            it.reference == initial.reference &&
                                                it.referenceId == initial.referenceId
                                        }
                                    if (reading != null && reading.elapsedMillis > lastElapsed) {
                                        val fix =
                                            state.frame.fix?.takeIf {
                                                state.frame.nowElapsed - it.elapsedMillis in
                                                    0..ReadingPolicy.FIX_MAX_AGE
                                            }
                                        val point =
                                            TrackPoint(
                                                System.currentTimeMillis(),
                                                reading.elapsedMillis,
                                                reading,
                                                state.frame.pressureHpa?.takeIf {
                                                    state.barometer != null
                                                },
                                                fix?.latitude,
                                                fix?.longitude,
                                                fix?.mock ?: false,
                                            )
                                        accumulator.add(point)
                                        val stats = accumulator.stats
                                        val updated =
                                            trip.copy(
                                                sampleCount = stats.count,
                                                durationMillis =
                                                    SystemClock.elapsedRealtime() -
                                                        trip.startedElapsed,
                                                minimum = stats.minimum,
                                                maximum = stats.maximum,
                                                ascent = stats.ascent,
                                                descent = stats.descent,
                                            )
                                        tracks.append(updated, point)
                                        trip = updated
                                        mutable.value = State(trip)
                                        lastElapsed = reading.elapsedMillis
                                    }
                                    if (reading == null)
                                        mutable.value = State(trip, waitingForSource = true)
                                    delay(prefs.intervalSeconds * 1000L)
                                }
                                subscription.cancel()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            withContext(NonCancellable) {
                                runCatching {
                                    tracks.finish(
                                        trip.copy(
                                            endedAt = System.currentTimeMillis(),
                                            durationMillis =
                                                SystemClock.elapsedRealtime() - trip.startedElapsed,
                                            status = TripStatus.INTERRUPTED,
                                        )
                                    )
                                }
                                mutable.value = State(error = "记录中断：无法写入存储，已保留成功保存的数据")
                            }
                        }
                    }
                true
            } catch (cancelled: CancellationException) {
                mutable.value = State()
                throw cancelled
            } catch (error: Exception) {
                mutable.value = State(error = error.message ?: "无法开始记录")
                false
            }
        }

    suspend fun stop() =
        commands.withLock {
            job?.cancelAndJoin()
            job = null
            val trip = mutable.value.trip ?: return@withLock
            try {
                tracks.finish(
                    trip.copy(
                        endedAt = System.currentTimeMillis(),
                        durationMillis = SystemClock.elapsedRealtime() - trip.startedElapsed,
                        status = TripStatus.COMPLETED,
                    )
                )
                mutable.value = State()
            } catch (_: Exception) {
                mutable.value = State(error = "结束状态未能保存；下次启动将恢复为中断记录")
            }
        }

    fun requestStop() {
        scope.launch { stop() }
    }

    fun reportError(message: String) {
        mutable.value = mutable.value.copy(error = message)
    }

    fun clearError() {
        mutable.value = mutable.value.copy(error = null)
    }
}
