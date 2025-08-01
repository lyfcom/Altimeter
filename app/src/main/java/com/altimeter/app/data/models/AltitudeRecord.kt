package com.altimeter.app.data.models

import java.time.LocalDateTime

/**
 * 海拔记录数据模型
 */
data class AltitudeRecord(
    val id: String,                     // 记录唯一ID
    val timestamp: LocalDateTime,       // 记录时间
    val altitude: Double,               // 海拔高度（米）
    val source: AltitudeSource,         // 数据来源
    val accuracy: Double,               // 精度（米）
    val reliability: Double,            // 可靠度（0-100）
    val latitude: Double,               // 纬度
    val longitude: Double,              // 经度
    val sessionId: String? = null,      // 测量会话ID（用于分组连续测量）
    val description: String = ""        // 描述信息
)

/**
 * 海拔记录会话
 * 用于将连续的测量记录分组
 */
data class AltitudeMeasurementSession(
    val sessionId: String,              // 会话ID
    val startTime: LocalDateTime,       // 开始时间
    val endTime: LocalDateTime?,        // 结束时间
    val totalRecords: Int,              // 总记录数
    val averageAltitude: Double,        // 平均海拔
    val maxAltitude: Double,            // 最高海拔
    val minAltitude: Double,            // 最低海拔
    val altitudeRange: Double,          // 海拔范围
    val sessionType: SessionType        // 会话类型
)

/**
 * 测量会话类型
 */
enum class SessionType(val displayName: String) {
    SINGLE_MEASUREMENT("单次测量"),
    REAL_TIME_SESSION("实时测量"),
    MANUAL_SESSION("手动记录")
}

/**
 * 图表数据点
 */
data class ChartDataPoint(
    val timestamp: LocalDateTime,
    val altitude: Double,
    val source: AltitudeSource,
    val reliability: Double
)

/**
 * 统计数据
 */
data class AltitudeStatistics(
    val totalRecords: Int,              // 总记录数
    val totalSessions: Int,             // 总会话数
    val averageAltitude: Double,        // 平均海拔
    val maxAltitude: Double,            // 最高海拔
    val minAltitude: Double,            // 最低海拔
    val mostReliableSource: AltitudeSource, // 最可靠的数据源
    val firstRecordTime: LocalDateTime?, // 首次记录时间
    val lastRecordTime: LocalDateTime?   // 最近记录时间
)