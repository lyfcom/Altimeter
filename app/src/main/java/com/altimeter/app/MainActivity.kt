package com.altimeter.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.altimeter.app.ui.screens.MainScreen
import com.altimeter.app.ui.theme.AltimeterTheme
import com.altimeter.app.utils.PermissionHelper

class MainActivity : ComponentActivity() {

    // Android 13+ 通知权限请求 launcher
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // 这里无需处理结果，只需触发系统权限弹窗即可
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 在应用启动时主动请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this)) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AltimeterTheme {
                MainScreen()
            }
        }
    }
}