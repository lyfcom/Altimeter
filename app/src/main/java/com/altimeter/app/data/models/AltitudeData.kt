package com.altimeter.app.data.models

import java.time.LocalDateTime

/**
 * 海拔数据模型
 */
data class AltitudeData(
    val altitude: Double,           // 海拔高度（米）
    val source: AltitudeSource,     // 数据来源
    val accuracy: Double,           // 精度/误差范围（米）
    val reliability: Double,        // 可靠度评分（0-100）
    val timestamp: LocalDateTime,   // 获取时间
    val latitude: Double? = null,   // 纬度
    val longitude: Double? = null,  // 经度
    val description: String = ""    // 描述信息
)

/**
 * 海拔数据来源枚举
 */
enum class AltitudeSource(
    val displayName: String,
    val baseReliability: Double,    // 基础可靠度
    val typicalAccuracy: Double     // 典型精度（米）
) {
    GNSS("卫星定位", 40.0, 10.0),
    BAROMETER("气压传感器", 85.0, 3.0),
    ELEVATION_API("海拔API", 90.0, 1.0),
    NETWORK_ALTITUDE("网络海拔数据", 75.0, 5.0),
    MANUAL_INPUT("手动输入", 95.0, 0.5)
}

/**
 * 位置信息
 */
data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val timestamp: Long
)

/**
 * 海拔测量状态
 */
enum class MeasurementStatus {
    IDLE,           // 空闲
    LOCATING,       // 定位中
    MEASURING,      // 测量中
    SUCCESS,        // 成功
    ERROR           // 错误
}

/**
 * 海拔测量结果
 */
data class AltitudeMeasurement(
    val status: MeasurementStatus,
    val data: List<AltitudeData>,
    val errorMessage: String? = null,
    val isRealTimeEnabled: Boolean? = false
)