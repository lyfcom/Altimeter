package com.altimeter.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.altimeter.app.ui.viewmodels.AltimeterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    // 共享的ViewModel
    val sharedViewModel: AltimeterViewModel = viewModel()
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "altimeter",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("altimeter") {
                AltimeterScreen(viewModel = sharedViewModel)
            }
            composable("history") {
                HistoryScreen(viewModel = sharedViewModel)
            }
            composable("settings") {
                SettingsScreen(viewModel = sharedViewModel)
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    NavigationBar {
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "海拔计"
                )
            },
            label = { Text("海拔计") },
            selected = currentRoute == "altimeter",
            onClick = {
                if (currentRoute != "altimeter") {
                    navController.navigate("altimeter") {
                        popUpTo("altimeter") { inclusive = true }
                    }
                }
            }
        )
        
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "历史记录"
                )
            },
            label = { Text("历史记录") },
            selected = currentRoute == "history",
            onClick = {
                if (currentRoute != "history") {
                    navController.navigate("history") {
                        popUpTo("altimeter")
                    }
                }
            }
        )
        
        NavigationBarItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "设置"
                )
            },
            label = { Text("设置") },
            selected = currentRoute == "settings",
            onClick = {
                if (currentRoute != "settings") {
                    navController.navigate("settings") {
                        popUpTo("altimeter")
                    }
                }
            }
        )
    }
}