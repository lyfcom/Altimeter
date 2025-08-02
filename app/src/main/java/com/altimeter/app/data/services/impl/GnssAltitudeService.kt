package com.altimeter.app.data.services.impl

import com.altimeter.app.data.models.AltitudeData
import com.altimeter.app.data.models.AltitudeSource
import com.altimeter.app.data.models.LocationInfo
import com.altimeter.app.data.services.AltitudeService
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * GNSS卫星定位海拔高度服务实现
 * 支持GPS、GLONASS、北斗、Galileo等多种卫星系统
 */
class GnssAltitudeService : AltitudeService {
    
    override suspend fun getAltitude(location: LocationInfo): Result<AltitudeData> {
        return try {
            val gnssAltitude = location.altitude
            
            if (gnssAltitude != null) {
                // 计算GNSS海拔的可靠度
                val reliability = calculateGnssReliability(location.accuracy)
                
                val altitudeData = AltitudeData(
                    altitude = gnssAltitude,
                    source = AltitudeSource.GNSS,
                    accuracy = location.accuracy.toDouble(),
                    reliability = reliability,
                    timestamp = LocalDateTime.now(),
                    latitude = location.latitude,
                    longitude = location.longitude,
                    description = "GNSS卫星定位获取的海拔高度"
                )
                
                Result.success(altitudeData)
            } else {
                Result.failure(Exception("GNSS卫星未提供海拔数据"))
            }
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getServiceName(): String = "GNSS卫星定位服务"
    
    override suspend fun isAvailable(): Boolean = true
    
    /**
     * 根据GNSS精度计算可靠度
     * GNSS精度越高（数值越小），可靠度越高
     */
    private fun calculateGnssReliability(accuracy: Float): Double {
        val source = AltitudeSource.GNSS
        return when {
            accuracy <= 5.0f -> source.baseReliability + 20  // 高精度
            accuracy <= 10.0f -> source.baseReliability + 10 // 中等精度
            accuracy <= 20.0f -> source.baseReliability      // 标准精度
            else -> source.baseReliability - 10              // 低精度
        }.coerceIn(0.0, 100.0)
    }
}