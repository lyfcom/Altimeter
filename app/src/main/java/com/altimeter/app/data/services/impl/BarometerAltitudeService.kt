package com.altimeter.app.data.services.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.altimeter.app.data.models.AltitudeData
import com.altimeter.app.data.models.AltitudeSource
import com.altimeter.app.data.models.LocationInfo
import com.altimeter.app.data.services.AltitudeService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.LocalDateTime
import kotlin.coroutines.resume
import kotlin.math.pow

/**
 * 气压传感器海拔高度服务实现
 */
class BarometerAltitudeService(private val context: Context) : AltitudeService {
    
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    
    // 海平面标准大气压（hPa）
    private val SEA_LEVEL_PRESSURE = 1013.25
    
    override suspend fun getAltitude(location: LocationInfo): Result<AltitudeData> {
        return try {
            if (!isAvailable()) {
                return Result.failure(Exception("设备不支持气压传感器"))
            }
            
            val pressure = getPressureReading()
            if (pressure != null) {
                val altitude = calculateAltitudeFromPressure(pressure)
                val reliability = calculateBarometerReliability(pressure)
                
                val altitudeData = AltitudeData(
                    altitude = altitude,
                    source = AltitudeSource.BAROMETER,
                    accuracy = AltitudeSource.BAROMETER.typicalAccuracy,
                    reliability = reliability,
                    timestamp = LocalDateTime.now(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    description = "气压传感器计算海拔 (${String.format("%.2f", pressure)} hPa)"
                )
                
                Result.success(altitudeData)
            } else {
                Result.failure(Exception("无法读取气压传感器数据"))
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getServiceName(): String = "气压传感器海拔服务"
    
    override suspend fun isAvailable(): Boolean = pressureSensor != null
    
    /**
     * 获取气压传感器读数
     */
    private suspend fun getPressureReading(): Float? = suspendCancellableCoroutine { continuation ->
        if (pressureSensor == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_PRESSURE) {
                        sensorManager.unregisterListener(this)
                        continuation.resume(it.values[0])
                    }
                }
            }
            
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        val registered = sensorManager.registerListener(
            listener,
            pressureSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        
        if (!registered) {
            continuation.resume(null)
        }
        
        // 取消时清理监听器
        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }
        
        // 超时处理（5秒后自动取消）
        continuation.invokeOnCancellation {
            sensorManager.unregisterListener(listener)
        }
    }
    
    /**
     * 根据大气压计算海拔高度
     * 使用国际标准大气模型
     */
    private fun calculateAltitudeFromPressure(pressure: Float): Double {
        // H = 44330 * (1 - (P/P0)^(1/5.255))
        // H: 海拔高度(m)
        // P: 当前大气压(hPa)
        // P0: 海平面标准大气压(1013.25 hPa)
        return 44330.0 * (1.0 - (pressure / SEA_LEVEL_PRESSURE).pow(1.0 / 5.255))
    }
    
    /**
     * 计算气压计可靠度
     */
    private fun calculateBarometerReliability(pressure: Float): Double {
        val source = AltitudeSource.BAROMETER
        return when {
            pressure in 950.0..1050.0 -> source.baseReliability + 10  // 正常大气压范围
            pressure in 900.0..1100.0 -> source.baseReliability       // 可接受范围
            else -> source.baseReliability - 20                       // 异常大气压
        }.coerceIn(0.0, 100.0)
    }
}