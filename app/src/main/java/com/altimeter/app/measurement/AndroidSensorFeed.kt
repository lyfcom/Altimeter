package com.altimeter.app.measurement

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import androidx.core.content.ContextCompat
import com.altimeter.core.*
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

class AndroidSensorFeed(private val context: Context) : SensorFeed {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val locations = context.getSystemService(LocationManager::class.java)

    private fun permission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint(
        "MissingPermission"
    ) // Registration and callbacks both check permission; revocation is handled.
    override fun frames(): Flow<SensorFrame> =
        callbackFlow {
                val pressureSensor = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE)
                var frame =
                    SensorFrame(
                        hasBarometer = pressureSensor != null,
                        locationPermission = permission(),
                    )
                val filter = PressureFilter()
                fun publish() {
                    trySend(frame.copy(nowElapsed = SystemClock.elapsedRealtime()))
                }
                val pressureListener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val time = event.timestamp / 1_000_000
                            val pressure = filter.add(event.values[0].toDouble(), time) ?: return
                            frame = frame.copy(pressureHpa = pressure, pressureElapsed = time)
                            publish()
                        }

                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    }
                val locationListener =
                    object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            val copy = Location(location)
                            launch {
                                val fix = withContext(Dispatchers.IO) { convertLocation(copy) }
                                if (fix.elapsedMillis >= (frame.fix?.elapsedMillis ?: 0)) {
                                    frame = frame.copy(fix = fix, issue = null)
                                    publish()
                                }
                            }
                        }

                        override fun onProviderEnabled(provider: String) {
                            frame = frame.copy(gpsEnabled = true)
                            publish()
                        }

                        override fun onProviderDisabled(provider: String) {
                            frame =
                                frame.copy(
                                    gpsEnabled = false,
                                    fix = null,
                                    satellitesUsed = 0,
                                    satellitesVisible = 0,
                                )
                            publish()
                        }

                        @Suppress("OVERRIDE_DEPRECATION") // Required on Android 7–10.
                        override fun onStatusChanged(
                            provider: String?,
                            status: Int,
                            extras: Bundle?,
                        ) = Unit
                    }
                val gnssCallback =
                    object : GnssStatus.Callback() {
                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                            frame =
                                frame.copy(
                                    satellitesVisible = status.satelliteCount,
                                    satellitesUsed =
                                        (0 until status.satelliteCount).count(status::usedInFix),
                                )
                            publish()
                        }

                        override fun onStopped() {
                            frame = frame.copy(satellitesUsed = 0, satellitesVisible = 0)
                            publish()
                        }
                    }
                if (
                    pressureSensor != null &&
                        !sensors.registerListener(pressureListener, pressureSensor, 200_000)
                ) {
                    frame = frame.copy(hasBarometer = false, issue = "气压传感器暂时不可用")
                }
                if (permission()) {
                    try {
                        frame =
                            frame.copy(
                                gpsEnabled =
                                    locations.isProviderEnabled(LocationManager.GPS_PROVIDER)
                            )
                        if (locations.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                            locations.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                1000,
                                0f,
                                locationListener,
                                Looper.getMainLooper(),
                            )
                            locations.registerGnssStatusCallback(
                                gnssCallback,
                                Handler(Looper.getMainLooper()),
                            )
                        }
                    } catch (_: SecurityException) {
                        frame = frame.copy(locationPermission = false, issue = "请重新授予精确位置权限")
                    } catch (_: IllegalArgumentException) {
                        frame = frame.copy(issue = "此设备不提供卫星定位")
                    }
                }
                val ticker = launch {
                    while (isActive) {
                        val granted = permission()
                        frame =
                            frame.copy(
                                locationPermission = granted,
                                fix = if (granted) frame.fix else null,
                                gpsEnabled =
                                    granted &&
                                        runCatching {
                                                locations.isProviderEnabled(
                                                    LocationManager.GPS_PROVIDER
                                                )
                                            }
                                            .getOrDefault(false),
                            )
                        publish()
                        delay(1000)
                    }
                }
                publish()
                awaitClose {
                    ticker.cancel()
                    sensors.unregisterListener(pressureListener)
                    locations.removeUpdates(locationListener)
                    locations.unregisterGnssStatusCallback(gnssCallback)
                }
            }
            .conflate()

    private fun convertLocation(location: Location): Fix {
        if (Build.VERSION.SDK_INT >= 34 && location.hasAltitude() && !location.hasMslAltitude()) {
            try {
                Api34.addSeaLevel(context, location)
            } catch (_: IOException) {
                /* Keep the explicitly labelled ellipsoidal reading. */
            } catch (_: IllegalArgumentException) {
                /* Reject malformed coordinates below. */
            }
        }
        return Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            ellipsoidMetres = if (location.hasAltitude()) location.altitude else null,
            seaLevelMetres =
                if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitude())
                    location.mslAltitudeMeters
                else null,
            verticalAccuracyMetres =
                if (Build.VERSION.SDK_INT >= 26 && location.hasVerticalAccuracy())
                    location.verticalAccuracyMeters.toDouble()
                else null,
            seaLevelAccuracyMetres =
                if (Build.VERSION.SDK_INT >= 34 && location.hasMslAltitudeAccuracy())
                    location.mslAltitudeAccuracyMeters.toDouble()
                else null,
            horizontalAccuracyMetres =
                if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            elapsedMillis = location.elapsedRealtimeNanos / 1_000_000,
            mock =
                if (Build.VERSION.SDK_INT >= 31) location.isMock
                else @Suppress("DEPRECATION") location.isFromMockProvider,
        )
    }

    @androidx.annotation.RequiresApi(34)
    private object Api34 {
        private val converter = android.location.altitude.AltitudeConverter()

        fun addSeaLevel(context: Context, location: Location) =
            converter.addMslAltitudeToLocation(context, location)
    }
}
