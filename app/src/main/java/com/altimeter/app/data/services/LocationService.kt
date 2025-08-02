package com.altimeter.app.data.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.altimeter.app.data.models.LocationInfo
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 位置服务 - 管理GPS位置获取
 */
class LocationService(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
    private fun isGooglePlayServicesAvailable(): Boolean {
        return com.google.android.gms.common.GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
    }
    
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    /**
     * 检查位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 获取当前位置（一次性）
     */
    suspend fun getCurrentLocation(): Result<LocationInfo> = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            continuation.resume(Result.failure(SecurityException("缺少位置权限")))
            return@suspendCancellableCoroutine
        }
        
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(Result.success(location.toLocationInfo()))
                } else {
                    // 如果没有缓存位置，请求新的位置
                    requestNewLocation { result ->
                        if (result.isSuccess) {
                            continuation.resume(result)
                        } else {
                            // 如果FusedLocation失败，尝试使用系统LocationManager作为回退
                            val fallback = getLastKnownLocationFromManager()
                            if (fallback != null) {
                                continuation.resume(Result.success(fallback))
                            } else {
                                continuation.resume(result)
                            }
                        }
                    }
                }
            }.addOnFailureListener { exception ->
                // FusedLocation失败，使用LocationManager
                val fallback = getLastKnownLocationFromManager()
                if (fallback != null) {
                    continuation.resume(Result.success(fallback))
                } else {
                    continuation.resume(Result.failure(exception))
                }
            }
        } catch (e: SecurityException) {
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * 获取位置更新流（实时位置）
     */
    fun getLocationUpdates(updateIntervalMs: Long = 5000): Flow<LocationInfo> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("缺少位置权限"))
            return@callbackFlow
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            updateIntervalMs
        ).apply {
            setMinUpdateIntervalMillis(updateIntervalMs / 2)
            setMaxUpdateDelayMillis(updateIntervalMs * 2)
        }.build()
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    val locationInfo = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        timestamp = location.time
                    )
                    trySend(locationInfo)
                }
            }
        }
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }
        
        awaitClose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
    
    /**
     * 请求新的位置
     */
    private fun Location.toLocationInfo(): LocationInfo {
        return LocationInfo(
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            altitude = if (hasAltitude()) altitude else null,
            timestamp = time
        )
    }
    /**
     * 尝试从系统LocationManager获取最近的位置
     */
    private fun getLastKnownLocationFromManager(): LocationInfo? {
        if (!hasLocationPermission()) return null
        val providers = locationManager.allProviders
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        return bestLocation?.toLocationInfo()
    }

    private fun requestNewLocation(callback: (Result<LocationInfo>) -> Unit) {
        if (!hasLocationPermission()) {
            callback(Result.failure(SecurityException("缺少位置权限")))
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).setMaxUpdates(1).build()
        
        // 10秒超时处理，避免长时间等待
        val timeoutHandler = android.os.Handler(Looper.getMainLooper())
        var timeoutRunnable: Runnable = Runnable {}
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.locations.firstOrNull()
                timeoutHandler.removeCallbacks(timeoutRunnable)
                if (location != null) {
                    val locationInfo = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        timestamp = location.time
                    )
                    callback(Result.success(locationInfo))
                } else {
                    callback(Result.failure(Exception("无法获取位置信息")))
                }
                fusedLocationClient.removeLocationUpdates(this)
            }
        }
        
        // 设置超时回调
        timeoutRunnable = Runnable {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            callback(Result.failure(Exception("获取位置超时")))
        }
        timeoutHandler.postDelayed(timeoutRunnable, 10_000)
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            callback(Result.failure(e))
        }
        
    }
}