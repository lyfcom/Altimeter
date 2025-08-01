package com.altimeter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.altimeter.app.ui.screens.MainScreen
import com.altimeter.app.ui.theme.AltimeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AltimeterTheme {
                MainScreen()
            }
        }
    }
}