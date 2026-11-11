package com.altimeter.app.measurement

import com.altimeter.core.Fix
import kotlinx.coroutines.flow.Flow

/** Platform boundary. Measurements and timestamps are in SI / elapsed realtime. */
interface SensorFeed {
    fun frames(): Flow<SensorFrame>
}

data class SensorFrame(
    val nowElapsed: Long = 0,
    val pressureHpa: Double? = null,
    val pressureElapsed: Long = 0,
    val fix: Fix? = null,
    val hasBarometer: Boolean = false,
    val locationPermission: Boolean = false,
    val gpsEnabled: Boolean = false,
    val satellitesUsed: Int = 0,
    val satellitesVisible: Int = 0,
    val issue: String? = null,
)
