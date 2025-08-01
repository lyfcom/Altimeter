package com.altimeter.app.data.services.impl

import com.altimeter.app.data.models.AltitudeData
import com.altimeter.app.data.models.LocationInfo
import com.altimeter.app.data.services.AltitudeService
import com.altimeter.app.data.services.CompositeAltitudeService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 复合海拔服务实现
 */
class CompositeAltitudeServiceImpl : CompositeAltitudeService {
    
    private val services = CopyOnWriteArrayList<AltitudeService>()
    
    override suspend fun getAllAltitudeData(location: LocationInfo): List<AltitudeData> {
        return coroutineScope {
            services.map { service ->
                async {
                    try {
                        service.getAltitude(location).getOrNull()
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }
    
    override suspend fun getBestAltitudeData(location: LocationInfo): AltitudeData? {
        val allData = getAllAltitudeData(location)
        return allData.maxByOrNull { calculateScore(it) }
    }
    
    override fun addService(service: AltitudeService) {
        if (!services.contains(service)) {
            services.add(service)
        }
    }
    
    override fun removeService(service: AltitudeService) {
        services.remove(service)
    }
    
    override fun getServices(): List<AltitudeService> = services.toList()
    
    /**
     * 计算海拔数据的综合评分
     * 综合考虑可靠度和精度
     */
    private fun calculateScore(data: AltitudeData): Double {
        // 可靠度权重70%，精度权重30%
        val reliabilityScore = data.reliability * 0.7
        
        // 精度越高（数值越小），评分越高
        val accuracyScore = when {
            data.accuracy <= 1.0 -> 30.0
            data.accuracy <= 3.0 -> 25.0
            data.accuracy <= 5.0 -> 20.0
            data.accuracy <= 10.0 -> 15.0
            data.accuracy <= 20.0 -> 10.0
            else -> 5.0
        } * 0.3
        
        return reliabilityScore + accuracyScore
    }
}