package com.altimeter.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altimeter.app.data.models.*
import com.altimeter.app.ui.viewmodels.AltimeterViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AltimeterScreen(
    modifier: Modifier = Modifier,
    viewModel: AltimeterViewModel = viewModel()
) {
    val measurementState by viewModel.measurementState.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val hasPermission by viewModel.hasLocationPermission.collectAsState()
    
    // 权限处理
    val locationPermissions = rememberMultiplePermissionsState(
        permissions = listOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )
    
    LaunchedEffect(locationPermissions.allPermissionsGranted) {
        viewModel.checkPermissions()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Text(
            text = "海拔高度计",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        
        // 权限检查
        if (!hasPermission) {
            PermissionCard(
                onRequestPermissions = {
                    locationPermissions.launchMultiplePermissionRequest()
                }
            )
        } else {
            // 控制按钮
            ControlButtons(
                measurementState = measurementState,
                onSingleMeasurement = viewModel::startSingleMeasurement,
                onToggleRealTime = viewModel::toggleRealTimeMeasurement,
                onClear = viewModel::clearMeasurements
            )
            
            // 状态显示
            StatusCard(measurementState = measurementState)
            
            // 位置信息
            currentLocation?.let { location ->
                LocationCard(location = location)
            }
            
            // 海拔数据列表
            if (measurementState.data.isNotEmpty()) {
                AltitudeDataList(
                    altitudeData = measurementState.data,
                    bestAltitude = viewModel.getBestAltitude()
                )
            }
        }
    }
}

@Composable
fun PermissionCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = "权限",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "需要位置权限",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "海拔高度计需要访问位置信息来提供准确的海拔数据",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = onRequestPermissions) {
                Text("授予权限")
            }
        }
    }
}

@Composable
fun ControlButtons(
    measurementState: AltitudeMeasurement,
    onSingleMeasurement: () -> Unit,
    onToggleRealTime: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 单次测量按钮
        Button(
            onClick = onSingleMeasurement,
            enabled = measurementState.status != MeasurementStatus.LOCATING && 
                     measurementState.status != MeasurementStatus.MEASURING,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("测量")
        }
        
        // 实时更新切换按钮
        Button(
            onClick = onToggleRealTime,
            enabled = measurementState.isRealTimeEnabled != null, // 未知状态时禁用
            colors = if (measurementState.isRealTimeEnabled == true) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            } else {
                ButtonDefaults.buttonColors()
            },
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (measurementState.isRealTimeEnabled == true) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (measurementState.isRealTimeEnabled == null) "连接中..."
                else if (measurementState.isRealTimeEnabled == true) "停止" 
                else "实时"
            )
        }
        
        // 清除按钮
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("清除")
        }
    }
}

@Composable
fun StatusCard(measurementState: AltitudeMeasurement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (measurementState.status) {
                MeasurementStatus.IDLE -> MaterialTheme.colorScheme.surfaceVariant
                MeasurementStatus.LOCATING, MeasurementStatus.MEASURING -> MaterialTheme.colorScheme.primaryContainer
                MeasurementStatus.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
                MeasurementStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            when (measurementState.status) {
                MeasurementStatus.IDLE -> Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                MeasurementStatus.LOCATING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                MeasurementStatus.MEASURING -> CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                MeasurementStatus.SUCCESS -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                MeasurementStatus.ERROR -> Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = when (measurementState.status) {
                        MeasurementStatus.IDLE -> "就绪"
                        MeasurementStatus.LOCATING -> "定位中..."
                        MeasurementStatus.MEASURING -> "测量中..."
                        MeasurementStatus.SUCCESS -> "测量完成"
                        MeasurementStatus.ERROR -> "测量失败"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                
                measurementState.errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                if (measurementState.isRealTimeEnabled == true) {
                    Text(
                        text = "实时更新已启用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun LocationCard(location: LocationInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "当前位置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                Text("纬度: ", fontWeight = FontWeight.Medium)
                Text(String.format("%.6f°", location.latitude))
            }
            
            Row {
                Text("经度: ", fontWeight = FontWeight.Medium)
                Text(String.format("%.6f°", location.longitude))
            }
            
            Row {
                Text("定位精度: ", fontWeight = FontWeight.Medium)
                Text("±${String.format("%.1f", location.accuracy)}米")
            }
            
            location.altitude?.let { altitude ->
                Row {
                    Text("卫星海拔: ", fontWeight = FontWeight.Medium)
                    Text("${String.format("%.1f", altitude)}米")
                }
            }
        }
    }
}

@Composable
fun AltitudeDataList(
    altitudeData: List<AltitudeData>,
    bestAltitude: AltitudeData?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "海拔数据",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                bestAltitude?.let { best ->
                    Text(
                        text = "最佳: ${String.format("%.1f", best.altitude)}m",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(altitudeData) { data ->
                    AltitudeDataItem(
                        data = data,
                        isBest = data == bestAltitude
                    )
                }
            }
        }
    }
}

@Composable
fun AltitudeDataItem(
    data: AltitudeData,
    isBest: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isBest) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.source.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                if (isBest) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "最佳",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${String.format("%.1f", data.altitude)} 米",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isBest) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "可靠度: ${String.format("%.0f", data.reliability)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = getReliabilityColor(data.reliability)
                )
                
                Text(
                    text = "精度: ±${String.format("%.1f", data.accuracy)}m",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            if (data.description.isNotEmpty()) {
                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "更新时间: ${data.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun getReliabilityColor(reliability: Double): Color {
    return when {
        reliability >= 80 -> Color(0xFF4CAF50) // 绿色 - 高可靠度
        reliability >= 60 -> Color(0xFFFF9800) // 橙色 - 中等可靠度
        else -> Color(0xFFF44336) // 红色 - 低可靠度
    }
}