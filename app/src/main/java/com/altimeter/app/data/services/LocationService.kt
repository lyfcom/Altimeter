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
                    val locationInfo = LocationInfo(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        timestamp = location.time
                    )
                    continuation.resume(Result.success(locationInfo))
                } else {
                    // 如果没有缓存位置，请求新的位置
                    requestNewLocation { result ->
                        continuation.resume(result)
                    }
                }
            }.addOnFailureListener { exception ->
                continuation.resume(Result.failure(exception))
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
    private fun requestNewLocation(callback: (Result<LocationInfo>) -> Unit) {
        if (!hasLocationPermission()) {
            callback(Result.failure(SecurityException("缺少位置权限")))
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000
        ).setMaxUpdates(1).build()
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.locations.firstOrNull()
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
        
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            callback(Result.failure(e))
        }
    }
}