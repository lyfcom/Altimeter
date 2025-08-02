package com.altimeter.app.utils

import com.altimeter.app.data.models.*
import com.altimeter.app.ui.screens.TimeRange
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 根据时间范围过滤记录
 */
fun filterRecordsForRange(
    all: List<AltitudeRecord>,
    range: TimeRange,
    customStart: LocalDate?,
    customEnd: LocalDate?
): List<AltitudeRecord> {
    val now = LocalDateTime.now()
    return when (range) {
        TimeRange.HOUR_24 -> all.filter { it.timestamp.isAfter(now.minusHours(24)) }
        TimeRange.DAYS_7 -> all.filter { it.timestamp.isAfter(now.minusDays(7)) }
        TimeRange.DAYS_30 -> all.filter { it.timestamp.isAfter(now.minusDays(30)) }
        TimeRange.ALL -> all
        TimeRange.CUSTOM -> {
            if (customStart != null && customEnd != null) {
                all.filter {
                    val d = it.timestamp.toLocalDate()
                    !d.isBefore(customStart) && !d.isAfter(customEnd)
                }
            } else all
        }
    }
}

/**
 * 根据记录列表计算统计信息
 */
fun computeStatistics(records: List<AltitudeRecord>): AltitudeStatistics {
    return if (records.isEmpty()) {
        AltitudeStatistics(
            totalRecords = 0,
            totalSessions = 0,
            averageAltitude = 0.0,
            maxAltitude = 0.0,
            minAltitude = 0.0,
            mostReliableSource = AltitudeSource.GNSS,
            firstRecordTime = null,
            lastRecordTime = null
        )
    } else {
        val altitudes = records.map { it.altitude }
        val sources = records.groupBy { it.source }
        val mostReliableSource = sources.maxByOrNull { (_, rs) -> rs.map { it.reliability }.average() }?.key ?: AltitudeSource.GNSS
        AltitudeStatistics(
            totalRecords = records.size,
            totalSessions = records.mapNotNull { it.sessionId }.distinct().size,
            averageAltitude = altitudes.average(),
            maxAltitude = altitudes.maxOrNull() ?: 0.0,
            minAltitude = altitudes.minOrNull() ?: 0.0,
            mostReliableSource = mostReliableSource,
            firstRecordTime = records.minByOrNull { it.timestamp }?.timestamp,
            lastRecordTime = records.maxByOrNull { it.timestamp }?.timestamp
        )
    }
}
