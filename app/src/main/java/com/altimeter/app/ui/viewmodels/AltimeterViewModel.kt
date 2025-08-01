package com.altimeter.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.altimeter.app.data.models.*
import com.altimeter.app.data.repository.AltitudeRecordRepository
import com.altimeter.app.data.services.LocationService
import com.altimeter.app.data.services.impl.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 海拔计主ViewModel
 */
class AltimeterViewModel(application: Application) : AndroidViewModel(application) {
    
    private val locationService = LocationService(application)
    private val compositeAltitudeService = CompositeAltitudeServiceImpl()
    private val recordRepository = AltitudeRecordRepository(application)
    
    // 当前测量状态
    private val _measurementState = MutableStateFlow(
        AltitudeMeasurement(
            status = MeasurementStatus.IDLE,
            data = emptyList(),
            isRealTimeEnabled = false
        )
    )
    val measurementState: StateFlow<AltitudeMeasurement> = _measurementState.asStateFlow()
    
    // 当前位置信息
    private val _currentLocation = MutableStateFlow<LocationInfo?>(null)
    val currentLocation: StateFlow<LocationInfo?> = _currentLocation.asStateFlow()
    
    // 权限状态
    private val _hasLocationPermission = MutableStateFlow(false)
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()
    
    // 记录相关状态
    val altitudeRecords = recordRepository.records
    val altitudeSessions = recordRepository.sessions
    val altitudeStatistics = recordRepository.getStatistics()
    val chartDataPoints = recordRepository.getChartDataPoints()
    
    // 自动记录设置
    private val _isAutoRecordEnabled = MutableStateFlow(true)
    val isAutoRecordEnabled: StateFlow<Boolean> = _isAutoRecordEnabled.asStateFlow()
    
    // 实时更新作业
    private var realTimeUpdateJob: Job? = null
    private var currentSessionId: String? = null
    
    // 更新间隔（毫秒）
    private var updateInterval = 5000L
    
    init {
        initializeServices()
        checkPermissions()
        loadAutoRecordSetting()
    }
    
    /**
     * 初始化海拔服务
     */
    private fun initializeServices() {
        compositeAltitudeService.apply {
            addService(GnssAltitudeService())
            addService(BarometerAltitudeService(getApplication()))
            addService(ElevationApiService())
        }
    }
    
    /**
     * 检查权限状态
     */
    fun checkPermissions() {
        _hasLocationPermission.value = locationService.hasLocationPermission()
    }
    
    /**
     * 开始单次海拔测量
     */
    fun startSingleMeasurement() {
        if (!_hasLocationPermission.value) {
            _measurementState.value = _measurementState.value.copy(
                status = MeasurementStatus.ERROR,
                errorMessage = "需要位置权限才能进行测量"
            )
            return
        }
        
        viewModelScope.launch {
            try {
                _measurementState.value = _measurementState.value.copy(
                    status = MeasurementStatus.LOCATING,
                    errorMessage = null
                )
                
                // 获取当前位置
                val locationResult = locationService.getCurrentLocation()
                locationResult.fold(
                    onSuccess = { location ->
                        _currentLocation.value = location
                        _measurementState.value = _measurementState.value.copy(
                            status = MeasurementStatus.MEASURING
                        )
                        
                        // 获取所有来源的海拔数据
                        val altitudeData = compositeAltitudeService.getAllAltitudeData(location)
                        
                        if (altitudeData.isNotEmpty()) {
                            _measurementState.value = _measurementState.value.copy(
                                status = MeasurementStatus.SUCCESS,
                                data = altitudeData.sortedByDescending { it.reliability }
                            )
                            
                            // 自动记录最佳海拔数据
                            if (_isAutoRecordEnabled.value) {
                                val bestData = altitudeData.maxByOrNull { it.reliability }
                                bestData?.let { data ->
                                    viewModelScope.launch {
                                        recordRepository.addRecord(data, SessionType.SINGLE_MEASUREMENT)
                                    }
                                }
                            }
                        } else {
                            _measurementState.value = _measurementState.value.copy(
                                status = MeasurementStatus.ERROR,
                                errorMessage = "无法获取海拔数据"
                            )
                        }
                    },
                    onFailure = { error ->
                        _measurementState.value = _measurementState.value.copy(
                            status = MeasurementStatus.ERROR,
                            errorMessage = "定位失败: ${error.message}"
                        )
                    }
                )
            } catch (e: Exception) {
                _measurementState.value = _measurementState.value.copy(
                    status = MeasurementStatus.ERROR,
                    errorMessage = "测量失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 开始/停止实时测量
     */
    fun toggleRealTimeMeasurement() {
        val currentState = _measurementState.value
        
        if (currentState.isRealTimeEnabled) {
            stopRealTimeMeasurement()
        } else {
            startRealTimeMeasurement()
        }
    }
    
    /**
     * 开始实时测量
     */
    private fun startRealTimeMeasurement() {
        if (!_hasLocationPermission.value) {
            _measurementState.value = _measurementState.value.copy(
                status = MeasurementStatus.ERROR,
                errorMessage = "需要位置权限才能进行实时测量"
            )
            return
        }
        
        realTimeUpdateJob?.cancel()
        realTimeUpdateJob = viewModelScope.launch {
            _measurementState.value = _measurementState.value.copy(
                isRealTimeEnabled = true,
                status = MeasurementStatus.LOCATING
            )
            
            // 开始新的测量会话
            currentSessionId = recordRepository.startNewSession(SessionType.REAL_TIME_SESSION)
            
            locationService.getLocationUpdates(updateInterval)
                .catch { error ->
                    _measurementState.value = _measurementState.value.copy(
                        status = MeasurementStatus.ERROR,
                        errorMessage = "位置更新失败: ${error.message}",
                        isRealTimeEnabled = false
                    )
                }
                .collect { location ->
                    _currentLocation.value = location
                    
                    try {
                        _measurementState.value = _measurementState.value.copy(
                            status = MeasurementStatus.MEASURING
                        )
                        
                        val altitudeData = compositeAltitudeService.getAllAltitudeData(location)
                        
                        _measurementState.value = _measurementState.value.copy(
                            status = MeasurementStatus.SUCCESS,
                            data = altitudeData.sortedByDescending { it.reliability }
                        )
                        
                        // 实时测量中自动记录最佳数据
                        if (_isAutoRecordEnabled.value) {
                            val bestData = altitudeData.maxByOrNull { it.reliability }
                            bestData?.let { data ->
                                recordRepository.addRecord(data, SessionType.REAL_TIME_SESSION)
                            }
                        }
                    } catch (e: Exception) {
                        _measurementState.value = _measurementState.value.copy(
                            status = MeasurementStatus.ERROR,
                            errorMessage = "海拔测量失败: ${e.message}"
                        )
                    }
                }
        }
    }
    
    /**
     * 停止实时测量
     */
    private fun stopRealTimeMeasurement() {
        realTimeUpdateJob?.cancel()
        realTimeUpdateJob = null
        
        // 结束当前会话
        viewModelScope.launch {
            recordRepository.endCurrentSession()
            currentSessionId = null
        }
        
        _measurementState.value = _measurementState.value.copy(
            isRealTimeEnabled = false,
            status = if (_measurementState.value.data.isNotEmpty()) {
                MeasurementStatus.SUCCESS
            } else {
                MeasurementStatus.IDLE
            }
        )
    }
    
    /**
     * 设置更新间隔
     */
    fun setUpdateInterval(intervalMs: Long) {
        updateInterval = intervalMs
        
        // 如果正在实时更新，重启以应用新间隔
        if (_measurementState.value.isRealTimeEnabled) {
            startRealTimeMeasurement()
        }
    }
    
    /**
     * 清除测量结果
     */
    fun clearMeasurements() {
        stopRealTimeMeasurement()
        _measurementState.value = AltitudeMeasurement(
            status = MeasurementStatus.IDLE,
            data = emptyList(),
            isRealTimeEnabled = false
        )
        _currentLocation.value = null
    }
    
    /**
     * 获取最佳海拔数据
     */
    fun getBestAltitude(): AltitudeData? {
        return _measurementState.value.data.maxByOrNull { it.reliability }
    }
    
    /**
     * 设置自动记录开关
     */
    fun setAutoRecordEnabled(enabled: Boolean) {
        _isAutoRecordEnabled.value = enabled
        recordRepository.saveAutoRecordEnabled(enabled)
    }
    
    /**
     * 加载自动记录设置
     */
    private fun loadAutoRecordSetting() {
        _isAutoRecordEnabled.value = recordRepository.loadAutoRecordEnabled()
    }
    
    /**
     * 手动添加记录
     */
    fun addManualRecord(altitudeData: AltitudeData) {
        viewModelScope.launch {
            recordRepository.addRecord(altitudeData, SessionType.MANUAL_SESSION)
        }
    }
    
    /**
     * 清除所有记录
     */
    fun clearAllRecords() {
        viewModelScope.launch {
            recordRepository.clearAllRecords()
        }
    }
    
    /**
     * 删除指定记录
     */
    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            recordRepository.deleteRecord(recordId)
        }
    }
    
    /**
     * 获取指定时间范围的记录
     */
    fun getRecordsInTimeRange(startTime: java.time.LocalDateTime, endTime: java.time.LocalDateTime) = 
        recordRepository.getRecordsInTimeRange(startTime, endTime)
    
    override fun onCleared() {
        super.onCleared()
        realTimeUpdateJob?.cancel()
        // 清理会话
        viewModelScope.launch {
            recordRepository.endCurrentSession()
        }
    }
}