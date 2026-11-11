package com.altimeter.core

import kotlin.math.pow

/** Height reference is part of every value; ellipsoidal height is not sea-level elevation. */
enum class HeightReference {
    SEA_LEVEL,
    ELLIPSOID,
    RELATIVE,
}

enum class Source {
    GNSS,
    BAROMETER,
}

enum class MeasureMode {
    AUTO,
    GNSS,
    BAROMETER,
}

enum class DistanceUnit {
    METRES,
    FEET;

    fun fromMetres(value: Double): Double = if (this == FEET) value * 3.280839895 else value

    fun toMetres(value: Double): Double = if (this == FEET) value / 3.280839895 else value

    val symbol: String
        get() = if (this == FEET) "ft" else "m"
}

data class Fix(
    val latitude: Double,
    val longitude: Double,
    val ellipsoidMetres: Double?,
    val seaLevelMetres: Double?,
    val verticalAccuracyMetres: Double?,
    val seaLevelAccuracyMetres: Double?,
    val horizontalAccuracyMetres: Double?,
    val elapsedMillis: Long,
    val mock: Boolean = false,
)

data class Calibration(val seaLevelPressureHpa: Double, val createdAtMillis: Long) {
    fun isValid(now: Long): Boolean =
        seaLevelPressureHpa.isFinite() &&
            seaLevelPressureHpa in 800.0..1200.0 &&
            now - createdAtMillis in 0..MAX_AGE

    companion object {
        const val MAX_AGE = 12 * 60 * 60 * 1000L
    }
}

data class Reading(
    val metres: Double,
    val source: Source,
    val reference: HeightReference,
    val accuracyMetres: Double?,
    val elapsedMillis: Long,
    val referenceId: Long = 0,
)

object Barometry {
    fun altitude(pressureHpa: Double, referencePressureHpa: Double = 1013.25): Double {
        require(pressureHpa.isFinite() && pressureHpa in 100.0..1200.0)
        require(referencePressureHpa.isFinite() && referencePressureHpa > 0)
        return 44330.0 * (1 - (pressureHpa / referencePressureHpa).pow(1 / 5.255))
    }

    fun calibrate(pressureHpa: Double, knownMetres: Double): Double {
        require(knownMetres.isFinite() && knownMetres in -1000.0..10000.0)
        val result = pressureHpa / (1 - knownMetres / 44330).pow(5.255)
        require(pressureHpa.isFinite() && pressureHpa in 100.0..1200.0 && result in 800.0..1200.0)
        return result
    }
}

object ReadingPolicy {
    const val FIX_MAX_AGE = 15_000L
    const val PRESSURE_MAX_AGE = 5_000L

    fun satellite(fix: Fix?, nowElapsed: Long): Reading? {
        fix ?: return null
        if (nowElapsed - fix.elapsedMillis !in 0..FIX_MAX_AGE) return null
        val msl = fix.seaLevelMetres?.takeIf { it.isFinite() }
        val height = msl ?: fix.ellipsoidMetres?.takeIf { it.isFinite() } ?: return null
        val accuracy =
            (if (msl != null) fix.seaLevelAccuracyMetres else fix.verticalAccuracyMetres)?.takeIf {
                it.isFinite() && it >= 0
            }
        return Reading(
            height,
            Source.GNSS,
            if (msl != null) HeightReference.SEA_LEVEL else HeightReference.ELLIPSOID,
            accuracy,
            fix.elapsedMillis,
        )
    }

    fun select(mode: MeasureMode, satellite: Reading?, pressure: Reading?): Reading? =
        when (mode) {
            MeasureMode.GNSS -> satellite
            MeasureMode.BAROMETER -> pressure
            MeasureMode.AUTO ->
                when {
                    pressure?.reference == HeightReference.SEA_LEVEL -> pressure
                    satellite != null -> satellite
                    else -> pressure
                }
        }
}

/** Time-aware low-pass filter: event frequency cannot change the smoothing time constant. */
class PressureFilter {
    private var lastTime: Long? = null
    private var value: Double? = null

    fun add(pressure: Double, elapsed: Long): Double? {
        if (!pressure.isFinite() || pressure !in 100.0..1200.0) return null
        val previousTime = lastTime
        if (previousTime != null && elapsed <= previousTime) return value
        val previous = value
        value =
            if (previous == null || previousTime == null || elapsed - previousTime > 5_000) pressure
            else
                previous +
                    (pressure - previous) *
                        ((elapsed - previousTime) / (800.0 + elapsed - previousTime))
        lastTime = elapsed
        return value
    }

    fun reset() {
        lastTime = null
        value = null
    }
}
