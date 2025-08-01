package com.altimeter.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.altimeter.app.data.models.AltitudeRecord
import com.altimeter.app.data.models.AltitudeMeasurementSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 数据存储管理类
 * 使用SharedPreferences进行数据持久化
 */
class DataStorage(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "altimeter_data", 
        Context.MODE_PRIVATE
    )
    
    private val gson = Gson()
    
    companion object {
        private const val KEY_RECORDS = "altitude_records"
        private const val KEY_SESSIONS = "altitude_sessions"
        private const val KEY_AUTO_RECORD_ENABLED = "auto_record_enabled"
        
        // 数据量限制
        private const val MAX_RECORDS = 5000  // 最大记录数
        private const val MAX_SESSIONS = 500  // 最大会话数
        private const val CLEANUP_BATCH_SIZE = 1000  // 清理时每批保留的记录数
    }
    
    /**
     * 保存海拔记录列表
     */
    fun saveRecords(records: List<AltitudeRecord>) {
        try {
            // 数据量管理：如果记录过多，只保留最新的记录
            val recordsToSave = if (records.size > MAX_RECORDS) {
                records.sortedByDescending { it.timestamp }.take(CLEANUP_BATCH_SIZE)
            } else {
                records
            }
            
            val recordsJson = recordsToSave.map { record ->
                RecordJson(
                    id = record.id,
                    timestamp = record.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    altitude = record.altitude,
                    source = record.source.name,
                    accuracy = record.accuracy,
                    reliability = record.reliability,
                    latitude = record.latitude,
                    longitude = record.longitude,
                    sessionId = record.sessionId,
                    description = record.description
                )
            }
            
            // 压缩存储：移除描述为空的字段以减少存储空间
            val compactRecordsJson = recordsJson.map { record ->
                if (record.description.isEmpty()) {
                    record.copy(description = "")
                } else {
                    record
                }
            }
            
            val json = gson.toJson(compactRecordsJson)
            
            // 检查JSON大小，如果过大则进一步减少数据
            if (json.length > 1000000) { // 如果超过1MB
                val reducedRecords = recordsToSave.take(CLEANUP_BATCH_SIZE / 2)
                val reducedJson = gson.toJson(reducedRecords.map { record ->
                    RecordJson(
                        id = record.id,
                        timestamp = record.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        altitude = record.altitude,
                        source = record.source.name,
                        accuracy = record.accuracy,
                        reliability = record.reliability,
                        latitude = record.latitude,
                        longitude = record.longitude,
                        sessionId = record.sessionId,
                        description = ""  // 清空描述以节省空间
                    )
                })
                sharedPreferences.edit().putString(KEY_RECORDS, reducedJson).apply()
            } else {
                sharedPreferences.edit().putString(KEY_RECORDS, json).apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果保存失败，尝试只保存最少的数据
            try {
                val essentialRecords = records.sortedByDescending { it.timestamp }.take(100)
                val essentialJson = gson.toJson(essentialRecords.map { record ->
                    RecordJson(
                        id = record.id,
                        timestamp = record.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        altitude = record.altitude,
                        source = record.source.name,
                        accuracy = record.accuracy,
                        reliability = record.reliability,
                        latitude = record.latitude,
                        longitude = record.longitude,
                        sessionId = record.sessionId,
                        description = ""
                    )
                })
                sharedPreferences.edit().putString(KEY_RECORDS, essentialJson).apply()
            } catch (backupException: Exception) {
                backupException.printStackTrace()
            }
        }
    }
    
    /**
     * 加载海拔记录列表
     */
    fun loadRecords(): List<AltitudeRecord> {
        return try {
            val json = sharedPreferences.getString(KEY_RECORDS, null) ?: return emptyList()
            val type = object : TypeToken<List<RecordJson>>() {}.type
            val recordsJson: List<RecordJson> = gson.fromJson(json, type)
            
            recordsJson.mapNotNull { recordJson ->
                try {
                    AltitudeRecord(
                        id = recordJson.id,
                        timestamp = LocalDateTime.parse(recordJson.timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        altitude = recordJson.altitude,
                        source = com.altimeter.app.data.models.AltitudeSource.valueOf(recordJson.source),
                        accuracy = recordJson.accuracy,
                        reliability = recordJson.reliability,
                        latitude = recordJson.latitude,
                        longitude = recordJson.longitude,
                        sessionId = recordJson.sessionId,
                        description = recordJson.description
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 保存测量会话列表
     */
    fun saveSessions(sessions: List<AltitudeMeasurementSession>) {
        try {
            // 数据量管理：如果会话过多，只保留最新的会话
            val sessionsToSave = if (sessions.size > MAX_SESSIONS) {
                sessions.sortedByDescending { it.startTime }.take(MAX_SESSIONS / 2)
            } else {
                sessions
            }
            
            val sessionsJson = sessionsToSave.map { session ->
                SessionJson(
                    sessionId = session.sessionId,
                    startTime = session.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    endTime = session.endTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    totalRecords = session.totalRecords,
                    averageAltitude = session.averageAltitude,
                    maxAltitude = session.maxAltitude,
                    minAltitude = session.minAltitude,
                    altitudeRange = session.altitudeRange,
                    sessionType = session.sessionType.name
                )
            }
            val json = gson.toJson(sessionsJson)
            sharedPreferences.edit().putString(KEY_SESSIONS, json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果保存失败，尝试只保存最少的数据
            try {
                val essentialSessions = sessions.sortedByDescending { it.startTime }.take(50)
                val essentialJson = gson.toJson(essentialSessions.map { session ->
                    SessionJson(
                        sessionId = session.sessionId,
                        startTime = session.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        endTime = session.endTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        totalRecords = session.totalRecords,
                        averageAltitude = session.averageAltitude,
                        maxAltitude = session.maxAltitude,
                        minAltitude = session.minAltitude,
                        altitudeRange = session.altitudeRange,
                        sessionType = session.sessionType.name
                    )
                })
                sharedPreferences.edit().putString(KEY_SESSIONS, essentialJson).apply()
            } catch (backupException: Exception) {
                backupException.printStackTrace()
            }
        }
    }
    
    /**
     * 加载测量会话列表
     */
    fun loadSessions(): List<AltitudeMeasurementSession> {
        return try {
            val json = sharedPreferences.getString(KEY_SESSIONS, null) ?: return emptyList()
            val type = object : TypeToken<List<SessionJson>>() {}.type
            val sessionsJson: List<SessionJson> = gson.fromJson(json, type)
            
            sessionsJson.mapNotNull { sessionJson ->
                try {
                    AltitudeMeasurementSession(
                        sessionId = sessionJson.sessionId,
                        startTime = LocalDateTime.parse(sessionJson.startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        endTime = sessionJson.endTime?.let { 
                            LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) 
                        },
                        totalRecords = sessionJson.totalRecords,
                        averageAltitude = sessionJson.averageAltitude,
                        maxAltitude = sessionJson.maxAltitude,
                        minAltitude = sessionJson.minAltitude,
                        altitudeRange = sessionJson.altitudeRange,
                        sessionType = com.altimeter.app.data.models.SessionType.valueOf(sessionJson.sessionType)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 保存自动记录开关状态
     */
    fun saveAutoRecordEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_RECORD_ENABLED, enabled).apply()
    }
    
    /**
     * 加载自动记录开关状态
     */
    fun loadAutoRecordEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_RECORD_ENABLED, true)
    }
    
    /**
     * 清除所有数据
     */
    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }
    
    /**
     * 获取当前存储的数据大小估算
     */
    fun getStorageSizeEstimate(): StorageInfo {
        return try {
            val recordsJson = sharedPreferences.getString(KEY_RECORDS, null)
            val sessionsJson = sharedPreferences.getString(KEY_SESSIONS, null)
            
            val recordsSize = recordsJson?.length ?: 0
            val sessionsSize = sessionsJson?.length ?: 0
            val totalSize = recordsSize + sessionsSize
            
            val recordsCount = if (recordsJson != null) {
                try {
                    val type = object : TypeToken<List<RecordJson>>() {}.type
                    val records: List<RecordJson> = gson.fromJson(recordsJson, type)
                    records.size
                } catch (e: Exception) {
                    0
                }
            } else {
                0
            }
            
            val sessionsCount = if (sessionsJson != null) {
                try {
                    val type = object : TypeToken<List<SessionJson>>() {}.type
                    val sessions: List<SessionJson> = gson.fromJson(sessionsJson, type)
                    sessions.size
                } catch (e: Exception) {
                    0
                }
            } else {
                0
            }
            
            StorageInfo(
                totalSizeBytes = totalSize,
                recordsCount = recordsCount,
                sessionsCount = sessionsCount,
                isNearLimit = totalSize > 800000 || recordsCount > MAX_RECORDS * 0.8
            )
        } catch (e: Exception) {
            e.printStackTrace()
            StorageInfo(0, 0, 0, false)
        }
    }
    
    /**
     * 清理旧数据，保留最新的数据
     */
    fun cleanupOldData() {
        try {
            val records = loadRecords()
            val sessions = loadSessions()
            
            if (records.size > MAX_RECORDS * 0.8) {
                val recentRecords = records.sortedByDescending { it.timestamp }.take(CLEANUP_BATCH_SIZE)
                saveRecords(recentRecords)
            }
            
            if (sessions.size > MAX_SESSIONS * 0.8) {
                val recentSessions = sessions.sortedByDescending { it.startTime }.take(MAX_SESSIONS / 2)
                saveSessions(recentSessions)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * 存储信息数据类
 */
data class StorageInfo(
    val totalSizeBytes: Int,
    val recordsCount: Int,
    val sessionsCount: Int,
    val isNearLimit: Boolean
)

/**
 * 记录的JSON序列化数据类
 */
private data class RecordJson(
    val id: String,
    val timestamp: String,
    val altitude: Double,
    val source: String,
    val accuracy: Double,
    val reliability: Double,
    val latitude: Double,
    val longitude: Double,
    val sessionId: String?,
    val description: String
)

/**
 * 会话的JSON序列化数据类
 */
private data class SessionJson(
    val sessionId: String,
    val startTime: String,
    val endTime: String?,
    val totalRecords: Int,
    val averageAltitude: Double,
    val maxAltitude: Double,
    val minAltitude: Double,
    val altitudeRange: Double,
    val sessionType: String
)