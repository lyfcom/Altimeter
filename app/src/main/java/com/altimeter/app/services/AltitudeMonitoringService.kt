package com.altimeter.app.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.altimeter.app.MainActivity
import com.altimeter.app.data.models.*
import com.altimeter.app.data.repository.AltitudeRecordRepository
import com.altimeter.app.data.services.CompositeAltitudeService
import com.altimeter.app.data.services.LocationService
import com.altimeter.app.data.services.impl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 海拔监测前台服务 - 支持后台持续测量
 */
class AltitudeMonitoringService : Service() {
    
    companion object {
        private const val TAG = "AltitudeMonitoringService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "altitude_monitoring"
        const val ACTION_START_MONITORING = "com.altimeter.app.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.altimeter.app.STOP_MONITORING"
        
        private const val DEFAULT_UPDATE_INTERVAL = 5000L // 5秒
    }
    
    private val binder = LocalBinder()
    private var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null
    
    // 服务组件
    private lateinit var locationService: LocationService
    private lateinit var compositeAltitudeService: CompositeAltitudeService
    private lateinit var recordRepository: AltitudeRecordRepository
    
    // 状态数据
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()
    
    private val _currentLocation = MutableStateFlow<LocationInfo?>(null)
    val currentLocation: StateFlow<LocationInfo?> = _currentLocation.asStateFlow()
    
    private val _latestAltitudeData = MutableStateFlow<List<AltitudeData>>(emptyList())
    val latestAltitudeData: StateFlow<List<AltitudeData>> = _latestAltitudeData.asStateFlow()
    
    private val _measurementStatus = MutableStateFlow(MeasurementStatus.IDLE)
    val measurementStatus: StateFlow<MeasurementStatus> = _measurementStatus.asStateFlow()
    
    // 数据更新通知
    private val _recordAdded = MutableStateFlow(0L) // 使用时间戳作为更新信号
    val recordAdded: StateFlow<Long> = _recordAdded.asStateFlow()
    
    private var updateInterval = DEFAULT_UPDATE_INTERVAL
    private var currentSessionId: String? = null
    private var isAutoRecordEnabled = true
    
    inner class LocalBinder : Binder() {
        fun getService(): AltitudeMonitoringService = this@AltitudeMonitoringService
    }
    
    override fun onCreate() {
        super.onCreate()
        initializeServices()
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
        }
        return START_STICKY // 服务被杀死后自动重启
    }
    
    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    /**
     * 初始化服务组件
     */
    private fun initializeServices() {
        locationService = LocationService(this)
        recordRepository = AltitudeRecordRepository(this)
        
        compositeAltitudeService = CompositeAltitudeServiceImpl().apply {
            addService(GnssAltitudeService())
            addService(BarometerAltitudeService(this@AltitudeMonitoringService))
            addService(ElevationApiService())
        }
        
        // 加载设置
        isAutoRecordEnabled = recordRepository.loadAutoRecordEnabled()
    }
    
    /**
     * 开始监测
     */
    fun startMonitoring(intervalMs: Long = DEFAULT_UPDATE_INTERVAL) {
        Log.d(TAG, "Starting monitoring with interval: ${intervalMs}ms")
        
        if (_isMonitoring.value) {
            Log.d(TAG, "Monitoring already running")
            return
        }
        
        // 检查通知权限
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission - service may be killed by system")
        }
        
        updateInterval = intervalMs
        _isMonitoring.value = true
        _measurementStatus.value = MeasurementStatus.LOCATING
        
        try {
            // 启动前台服务
            val initialNotification = createNotification("正在启动海拔监测...")
            startForeground(NOTIFICATION_ID, initialNotification)
            Log.d(TAG, "Foreground service started")
            
            monitoringJob = serviceScope.launch {
                try {
                    // 开始新的测量会话
                    currentSessionId = recordRepository.startNewSession(SessionType.REAL_TIME_SESSION)
                    Log.d(TAG, "New session started: $currentSessionId")
                    
                    locationService.getLocationUpdates(updateInterval)
                        .catch { error ->
                            Log.e(TAG, "Location updates failed", error)
                            _measurementStatus.value = MeasurementStatus.ERROR
                            updateNotification("定位失败: ${error.message}")
                        }
                        .collect { location ->
                            _currentLocation.value = location
                            processLocationUpdate(location)
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Monitoring failed", e)
                    _measurementStatus.value = MeasurementStatus.ERROR
                    updateNotification("监测出错: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            _measurementStatus.value = MeasurementStatus.ERROR
        }
    }
    
    /**
     * 停止监测
     */
    fun stopMonitoring() {
        if (!_isMonitoring.value) return
        
        monitoringJob?.cancel()
        monitoringJob = null
        
        serviceScope.launch {
            // 结束当前会话
            currentSessionId?.let {
                recordRepository.endCurrentSession()
                currentSessionId = null
            }
        }
        
        _isMonitoring.value = false
        _measurementStatus.value = MeasurementStatus.IDLE
        _latestAltitudeData.value = emptyList()
        _currentLocation.value = null
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    /**
     * 处理位置更新
     */
    private suspend fun processLocationUpdate(location: LocationInfo) {
        try {
            _measurementStatus.value = MeasurementStatus.MEASURING
            
            val altitudeData = compositeAltitudeService.getAllAltitudeData(location)
            _latestAltitudeData.value = altitudeData.sortedByDescending { it.reliability }
            
            if (altitudeData.isNotEmpty()) {
                _measurementStatus.value = MeasurementStatus.SUCCESS
                
                // 更新通知显示最佳海拔
                val bestAltitude = altitudeData.maxByOrNull { it.reliability }
                bestAltitude?.let { best ->
                    updateNotification("海拔: ${String.format("%.1f", best.altitude)}m (${best.source.displayName})")
                    
                    // 自动记录
                    if (isAutoRecordEnabled) {
                        recordRepository.addRecord(best, SessionType.REAL_TIME_SESSION)
                        // 通知ViewModel有新记录
                        _recordAdded.value = System.currentTimeMillis()
                        Log.d(TAG, "New record added, notifying ViewModels")
                    }
                }
            } else {
                _measurementStatus.value = MeasurementStatus.ERROR
                updateNotification("无法获取海拔数据")
            }
        } catch (e: Exception) {
            _measurementStatus.value = MeasurementStatus.ERROR
            updateNotification("测量失败: ${e.message}")
        }
    }
    
    /**
     * 设置更新间隔
     */
    fun setUpdateInterval(intervalMs: Long) {
        updateInterval = intervalMs
        if (_isMonitoring.value) {
            // 重启监测以应用新间隔
            serviceScope.launch {
                val wasMonitoring = _isMonitoring.value
                stopMonitoring()
                if (wasMonitoring) {
                    delay(1000) // 等待服务完全停止
                    startMonitoring(intervalMs)
                }
            }
        }
    }
    
    /**
     * 设置自动记录
     */
    fun setAutoRecordEnabled(enabled: Boolean) {
        isAutoRecordEnabled = enabled
        recordRepository.saveAutoRecordEnabled(enabled)
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "海拔监测",
                NotificationManager.IMPORTANCE_DEFAULT // 改为DEFAULT确保通知显示
            ).apply {
                description = "后台海拔高度实时监测"
                setShowBadge(true) // 显示徽章
                setSound(null, null) // 静音
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created: $CHANNEL_ID")
        }
    }
    
    /**
     * 检查通知权限
     */
    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(content: String): Notification {
        Log.d(TAG, "Creating notification with content: $content")
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, AltitudeMonitoringService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("海拔高度计 - 实时监测")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_more) // 使用更常见的图标
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "停止监测",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
    
    /**
     * 更新通知内容
     */
    private fun updateNotification(content: String) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, cannot update notification")
            return
        }
        
        try {
            val notification = createNotification(content)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }
}