package com.altimeter.app.ui.viewmodels

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.altimeter.app.data.models.*
import com.altimeter.app.data.repository.AltitudeRecordRepository
import com.altimeter.app.data.services.LocationService
import com.altimeter.app.data.services.impl.*
import com.altimeter.app.services.AltitudeMonitoringService
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
    private val sharedPrefs = application.getSharedPreferences("altimeter_settings", Context.MODE_PRIVATE)
    
    // 前台服务相关
    private var monitoringService: AltitudeMonitoringService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AltitudeMonitoringService.LocalBinder
            monitoringService = binder.getService()
            isBound = true
            
            // 立即同步服务的当前状态
            monitoringService?.let { service ->
                _measurementState.value = _measurementState.value.copy(
                    isRealTimeEnabled = service.isMonitoring.value
                )
            }
            
            // 订阅服务状态
            viewModelScope.launch {
                monitoringService?.let { service ->
                    // 监听服务状态变化
                    service.isMonitoring.collect { isMonitoring ->
                        _measurementState.value = _measurementState.value.copy(
                            isRealTimeEnabled = isMonitoring
                        )
                    }
                }
            }
            
            viewModelScope.launch {
                monitoringService?.measurementStatus?.collect { status ->
                    _measurementState.value = _measurementState.value.copy(status = status)
                }
            }
            
            viewModelScope.launch {
                monitoringService?.currentLocation?.collect { location ->
                    _currentLocation.value = location
                }
            }
            
            viewModelScope.launch {
                monitoringService?.latestAltitudeData?.collect { data ->
                    _measurementState.value = _measurementState.value.copy(data = data)
                }
            }
            
            // 监听记录更新通知
            viewModelScope.launch {
                monitoringService?.recordAdded?.collect { timestamp ->
                    if (timestamp > 0) {
                        // 有新记录时，强制刷新repository数据
                        recordRepository.refreshData()
                    }
                }
            }
        }
        
        override fun onServiceDisconnected(className: ComponentName) {
            monitoringService = null
            isBound = false
        }
    }
    
    // 当前测量状态
    private val _measurementState = MutableStateFlow(
        AltitudeMeasurement(
            status = MeasurementStatus.IDLE,
            data = emptyList(),
            isRealTimeEnabled = null // null表示未知状态，等待服务绑定后同步
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
    
    companion object {
        private const val PREF_UPDATE_INTERVAL = "update_interval"
        private const val DEFAULT_UPDATE_INTERVAL = 5000L
    }
    
    init {
        initializeServices()
        checkPermissions()
        loadAutoRecordSetting()
        loadUpdateInterval()
        bindToMonitoringService()
    }
    
    override fun onCleared() {
        super.onCleared()
        // 取消实时更新任务
        realTimeUpdateJob?.cancel()
        // 解绑前台服务
        unbindFromMonitoringService()
        // 清理会话
        viewModelScope.launch {
            recordRepository.endCurrentSession()
        }
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
     * 绑定到前台服务
     */
    private fun bindToMonitoringService() {
        val intent = Intent(getApplication(), AltitudeMonitoringService::class.java)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * 解绑前台服务
     */
    private fun unbindFromMonitoringService() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
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
        
        // 如果状态未知（null），不执行操作，等待服务绑定
        if (currentState.isRealTimeEnabled == null) {
            return
        }
        
        if (currentState.isRealTimeEnabled == true) {
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
        
        // 使用前台服务进行后台监测
        monitoringService?.let { service ->
            service.setAutoRecordEnabled(_isAutoRecordEnabled.value)
            service.startMonitoring(updateInterval)
        } ?: run {
            // 如果服务未绑定，启动服务
            val intent = Intent(getApplication(), AltitudeMonitoringService::class.java).apply {
                action = AltitudeMonitoringService.ACTION_START_MONITORING
            }
            getApplication<Application>().startForegroundService(intent)
        }
    }
    
    /**
     * 停止实时测量
     */
    private fun stopRealTimeMeasurement() {
        // 停止前台服务
        monitoringService?.stopMonitoring() ?: run {
            // 如果服务未绑定，通过Intent停止
            val intent = Intent(getApplication(), AltitudeMonitoringService::class.java).apply {
                action = AltitudeMonitoringService.ACTION_STOP_MONITORING
            }
            getApplication<Application>().startService(intent)
        }
    }
    
    /**
     * 设置更新间隔
     */
    fun setUpdateInterval(intervalMs: Long) {
        updateInterval = intervalMs
        
        // 保存到 SharedPreferences
        sharedPrefs.edit().putLong(PREF_UPDATE_INTERVAL, intervalMs).apply()
        
        // 如果正在实时更新，更新服务间隔
        if (_measurementState.value.isRealTimeEnabled == true) {
            monitoringService?.setUpdateInterval(intervalMs)
        }
    }
    
    /**
     * 加载更新间隔设置
     */
    private fun loadUpdateInterval() {
        updateInterval = sharedPrefs.getLong(PREF_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL)
    }
    
    /**
     * 获取当前更新间隔
     */
    fun getCurrentUpdateInterval(): Long = updateInterval
    
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
        // 同步到监测服务
        monitoringService?.setAutoRecordEnabled(enabled)
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
    

}