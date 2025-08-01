package com.altimeter.app.data.services

import com.altimeter.app.data.models.AltitudeData
import com.altimeter.app.data.models.LocationInfo

/**
 * 海拔高度服务接口
 */
interface AltitudeService {
    
    /**
     * 根据位置信息获取海拔高度
     */
    suspend fun getAltitude(location: LocationInfo): Result<AltitudeData>
    
    /**
     * 获取服务名称
     */
    fun getServiceName(): String
    
    /**
     * 检查服务是否可用
     */
    suspend fun isAvailable(): Boolean
}

/**
 * 复合海拔服务 - 管理多个海拔数据源
 */
interface CompositeAltitudeService {
    
    /**
     * 获取所有可用来源的海拔数据
     */
    suspend fun getAllAltitudeData(location: LocationInfo): List<AltitudeData>
    
    /**
     * 获取最可靠的海拔数据
     */
    suspend fun getBestAltitudeData(location: LocationInfo): AltitudeData?
    
    /**
     * 添加海拔服务
     */
    fun addService(service: AltitudeService)
    
    /**
     * 移除海拔服务
     */
    fun removeService(service: AltitudeService)
    
    /**
     * 获取所有注册的服务
     */
    fun getServices(): List<AltitudeService>
}