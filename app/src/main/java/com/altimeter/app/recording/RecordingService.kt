package com.altimeter.app.recording

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.altimeter.app.AltimeterApplication
import com.altimeter.app.MainActivity
import com.altimeter.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/** Android owns notification/lifetime; RecordingController owns recording state and persistence. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller
        get() = (application as AltimeterApplication).container.recording

    private var wakeLock: PowerManager.WakeLock? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26)
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(CHANNEL, "行程记录", NotificationManager.IMPORTANCE_LOW)
                )
        scope.launch {
            controller.state.collectLatest { state ->
                if (started && !state.active) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (state.trip != null) {
                    getSystemService(NotificationManager::class.java)
                        .notify(
                            NOTIFICATION_ID,
                            notification(
                                if (state.waitingForSource) "信号中断 · 等待原数据源恢复"
                                else "${state.trip.sampleCount} 个采样点 · 正在记录"
                            ),
                        )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            scope.launch {
                controller.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (started) return START_NOT_STICKY
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification("正在开始记录"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            started = true
            scope.launch {
                if (controller.start()) {
                    wakeLock =
                        getSystemService(PowerManager::class.java)
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Altimeter:Recording")
                            .apply { setReferenceCounted(false) }
                    // Bounded, renewed only while this user-started session is active.
                    while (controller.state.value.active) {
                        wakeLock?.acquire(20 * 60 * 1000L)
                        delay(10 * 60 * 1000L)
                    }
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } catch (_: SecurityException) {
            controller.reportError("后台记录需要精确位置权限，请授权后重新开始")
            stopSelf()
        } catch (_: IllegalStateException) {
            controller.reportError("请返回应用前台后开始记录")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        scope.cancel()
        // Application scope outlives this service long enough to commit the final session state.
        if (controller.state.value.active) {
            controller.requestStop()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun notification(text: String): Notification {
        val open =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, RecordingService::class.java).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_altimeter)
            .setContentTitle("海拔计 · 行程进行中")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "结束并保存", stop)
            .build()
    }

    companion object {
        const val START = "com.altimeter.app.START"
        const val STOP = "com.altimeter.app.STOP"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42
    }
}
