package com.altimeter.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.altimeter.app.recording.RecordingService
import com.altimeter.app.ui.*
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private val container
        get() = (application as AltimeterApplication).container

    private val viewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T =
                    AltimeterViewModel(
                        container.measurements,
                        container.preferences,
                        container.tracks,
                        container.recording,
                    )
                        as T
            },
        )[AltimeterViewModel::class.java]
    }
    private var pendingRecording = false
    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.refresh()
            if (fineLocation()) {
                if (pendingRecording) requestNotificationThenRecord()
            } else viewModel.notify("卫星测高与后台记录需要精确位置；气压测高仍可使用")
            pendingRecording = false
        }
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) viewModel.notify("通知未获允许；可回到仪表页结束记录")
            startRecording()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AltimeterApp(
                viewModel,
                onPermission = { requestLocation(false) },
                onLocationSettings = {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
                onRecord = {
                    if (viewModel.recording.value.active) {
                        startService(
                            Intent(this, RecordingService::class.java)
                                .setAction(RecordingService.STOP)
                        )
                    } else if (!fineLocation()) requestLocation(true)
                    else requestNotificationThenRecord()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun fineLocation() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocation(record: Boolean) {
        pendingRecording = record
        val askedBefore = getPreferences(MODE_PRIVATE).getBoolean("asked_location", false)
        if (
            askedBefore &&
                !fineLocation() &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        ) {
            pendingRecording = false
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
            )
            viewModel.notify("请在权限设置中允许精确位置，然后返回应用")
        } else {
            getPreferences(MODE_PRIVATE).edit { putBoolean("asked_location", true) }
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }
    }

    private fun requestNotificationThenRecord() {
        if (
            !getSystemService(LocationManager::class.java)
                .isProviderEnabled(LocationManager.GPS_PROVIDER)
        ) {
            viewModel.notify("后台记录需要开启设备定位，请开启后再开始")
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else startRecording()
    }

    private fun startRecording() {
        try {
            ContextCompat.startForegroundService(
                this,
                Intent(this, RecordingService::class.java).setAction(RecordingService.START),
            )
        } catch (_: RuntimeException) {
            viewModel.notify("无法启动后台记录，请确认位置权限并在前台重试")
        }
    }
}
