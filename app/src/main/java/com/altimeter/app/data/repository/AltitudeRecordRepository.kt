package com.altimeter.app.data.repository

import android.content.Context
import com.altimeter.app.data.models.*
import com.altimeter.app.data.storage.DataStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.ArrayList

/**
 * 海拔记录仓库
 * 管理海拔数据的存储、检索和统计
 */
class AltitudeRecordRepository(context: Context) {
    
    private val dataStorage = DataStorage(context)
    
    private val _records = MutableStateFlow<List<AltitudeRecord>>(emptyList())
    val records: Flow<List<AltitudeRecord>> = _records.asStateFlow()
    
    private val _sessions = MutableStateFlow<List<AltitudeMeasurementSession>>(emptyList())
    val sessions: Flow<List<AltitudeMeasurementSession>> = _sessions.asStateFlow()
    
    private var currentSessionId: String? = null
    
    init {
        // 应用启动时加载持久化数据
        loadPersistedData()
        
        // 检查是否需要清理数据
        checkAndCleanupData()
    }
    
    /**
     * 加载持久化数据
     */
    private fun loadPersistedData() {
        try {
            val persistedRecords = dataStorage.loadRecords()
            val persistedSessions = dataStorage.loadSessions()
            
            _records.value = persistedRecords
            _sessions.value = persistedSessions
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 刷新数据（公开方法，用于外部触发数据重新加载）
     */
    fun refreshData() {
        loadPersistedData()
    }
    
    /**
     * 添加海拔记录
     */
    suspend fun addRecord(altitudeData: AltitudeData, sessionType: SessionType = SessionType.SINGLE_MEASUREMENT): AltitudeRecord {
        // 如果是单次测量且没有当前会话，创建新会话
        if (sessionType == SessionType.SINGLE_MEASUREMENT && currentSessionId == null) {
            startNewSession(sessionType)
        }
        
        val record = AltitudeRecord(
            id = UUID.randomUUID().toString(),
            timestamp = altitudeData.timestamp,
            altitude = altitudeData.altitude,
            source = altitudeData.source,
            accuracy = altitudeData.accuracy,
            reliability = altitudeData.reliability,
            latitude = altitudeData.latitude ?: 0.0,
            longitude = altitudeData.longitude ?: 0.0,
            sessionId = currentSessionId,
            description = altitudeData.description
        )
        
        val currentRecords = _records.value.toMutableList()
        currentRecords.add(record)
        _records.value = currentRecords
        
        // 持久化保存记录
        saveRecordsToStorage()
        
        // 更新会话信息
        updateCurrentSession(sessionType)
        
        // 如果是单次测量，立即结束会话
        if (sessionType == SessionType.SINGLE_MEASUREMENT) {
            endCurrentSession()
        }
        
        return record
    }
    
    /**
     * 开始新的测量会话
     */
    fun startNewSession(sessionType: SessionType): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId!!
    }
    
    /**
     * 结束当前测量会话
     */
    suspend fun endCurrentSession() {
        currentSessionId?.let { sessionId ->
            updateSessionComplete(sessionId)
            currentSessionId = null
        }
    }
    
    /**
     * 获取指定时间范围内的记录
     */
    fun getRecordsInTimeRange(startTime: LocalDateTime, endTime: LocalDateTime): Flow<List<AltitudeRecord>> {
        return records.map { allRecords ->
            allRecords.filter { record ->
                record.timestamp.isAfter(startTime) && record.timestamp.isBefore(endTime)
            }
        }
    }
    
    /**
     * 获取指定会话的记录
     */
    fun getRecordsBySession(sessionId: String): Flow<List<AltitudeRecord>> {
        return records.map { allRecords ->
            allRecords.filter { it.sessionId == sessionId }
        }
    }
    
    /**
     * 获取图表数据点
     */
    fun getChartDataPoints(maxPoints: Int = 100): Flow<List<ChartDataPoint>> {
        return records.map { allRecords ->
            allRecords
                .sortedBy { it.timestamp }
                .takeLast(maxPoints)
                .map { record ->
                    ChartDataPoint(
                        timestamp = record.timestamp,
                        altitude = record.altitude,
                        source = record.source,
                        reliability = record.reliability
                    )
                }
        }
    }
    
    /**
     * 获取统计数据
     */
    fun getStatistics(): Flow<AltitudeStatistics> {
        return records.map { allRecords ->
            if (allRecords.isEmpty()) {
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
                val altitudes = allRecords.map { it.altitude }
                val sources = allRecords.groupBy { it.source }
                val mostReliableSource = sources.maxByOrNull { (_, records) ->
                    records.map { it.reliability }.average()
                }?.key ?: AltitudeSource.GNSS
                
                AltitudeStatistics(
                    totalRecords = allRecords.size,
                    totalSessions = _sessions.value.size,
                    averageAltitude = altitudes.average(),
                    maxAltitude = altitudes.maxOrNull() ?: 0.0,
                    minAltitude = altitudes.minOrNull() ?: 0.0,
                    mostReliableSource = mostReliableSource,
                    firstRecordTime = allRecords.minByOrNull { it.timestamp }?.timestamp,
                    lastRecordTime = allRecords.maxByOrNull { it.timestamp }?.timestamp
                )
            }
        }
    }
    
    /**
     * 清除所有记录
     */
    suspend fun clearAllRecords() {
        _records.value = emptyList()
        _sessions.value = emptyList()
        currentSessionId = null
        
        // 清除持久化数据
        withContext(Dispatchers.IO) {
            dataStorage.clearAllData()
        }
    }
    
    /**
     * 删除指定记录
     */
    suspend fun deleteRecord(recordId: String) {
        val currentRecords = _records.value.toMutableList()
        currentRecords.removeAll { it.id == recordId }
        _records.value = currentRecords
        
        // 持久化保存更新后的记录
        saveRecordsToStorage()
    }
    
    /**
     * 更新当前会话
     */
    private suspend fun updateCurrentSession(sessionType: SessionType) {
        currentSessionId?.let { sessionId ->
            val sessionRecords = _records.value.filter { it.sessionId == sessionId }
            if (sessionRecords.isNotEmpty()) {
                val altitudes = sessionRecords.map { it.altitude }
                val session = AltitudeMeasurementSession(
                    sessionId = sessionId,
                    startTime = sessionRecords.minByOrNull { it.timestamp }?.timestamp ?: LocalDateTime.now(),
                    endTime = null, // 会话还在进行中
                    totalRecords = sessionRecords.size,
                    averageAltitude = altitudes.average(),
                    maxAltitude = altitudes.maxOrNull() ?: 0.0,
                    minAltitude = altitudes.minOrNull() ?: 0.0,
                    altitudeRange = (altitudes.maxOrNull() ?: 0.0) - (altitudes.minOrNull() ?: 0.0),
                    sessionType = sessionType
                )
                
                val currentSessions = _sessions.value.toMutableList()
                val existingIndex = currentSessions.indexOfFirst { it.sessionId == sessionId }
                if (existingIndex >= 0) {
                    currentSessions[existingIndex] = session
                } else {
                    currentSessions.add(session)
                }
                _sessions.value = currentSessions
                
                // 持久化保存会话
                saveSessionsToStorage()
            }
        }
    }
    
    /**
     * 完成会话更新
     */
    private suspend fun updateSessionComplete(sessionId: String) {
        val currentSessions = _sessions.value.toMutableList()
        val sessionIndex = currentSessions.indexOfFirst { it.sessionId == sessionId }
        if (sessionIndex >= 0) {
            val session = currentSessions[sessionIndex]
            currentSessions[sessionIndex] = session.copy(endTime = LocalDateTime.now())
            _sessions.value = currentSessions
            
            // 持久化保存会话
            saveSessionsToStorage()
        }
    }
    
    /**
     * 保存记录到持久化存储
     */
    private fun saveRecordsToStorage() {
        try {
            dataStorage.saveRecords(_records.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 保存会话到持久化存储
     */
    private fun saveSessionsToStorage() {
        try {
            dataStorage.saveSessions(_sessions.value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 保存自动记录开关状态
     */
    fun saveAutoRecordEnabled(enabled: Boolean) {
        dataStorage.saveAutoRecordEnabled(enabled)
    }
    
    /**
     * 加载自动记录开关状态
     */
    fun loadAutoRecordEnabled(): Boolean {
        return dataStorage.loadAutoRecordEnabled()
    }
    
    /**
     * 检查并清理数据
     */
    private fun checkAndCleanupData() {
        try {
            val storageInfo = dataStorage.getStorageSizeEstimate()
            if (storageInfo.isNearLimit) {
                dataStorage.cleanupOldData()
                // 重新加载清理后的数据
                loadPersistedData()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 获取存储信息
     */
    fun getStorageInfo(): com.altimeter.app.data.storage.StorageInfo {
        return dataStorage.getStorageSizeEstimate()
    }
}