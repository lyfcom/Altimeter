package com.altimeter.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.altimeter.app.ui.viewmodels.AltimeterViewModel
import com.altimeter.app.utils.GnssSystemInfo

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: AltimeterViewModel = viewModel()
) {
    // 当前更新间隔
    val currentInterval = viewModel.getCurrentUpdateInterval()
    var selectedInterval by remember { mutableIntStateOf(currentInterval.toInt()) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        // 更新间隔设置
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "实时更新间隔",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "调整实时测量的更新频率。更短的间隔会消耗更多电量。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                val intervals = listOf(
                    1000 to "1秒（高频率）",
                    3000 to "3秒（中等频率）",
                    5000 to "5秒（标准）",
                    10000 to "10秒（节能）",
                    30000 to "30秒（低频率）"
                )
                
                intervals.forEach { (interval, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedInterval == interval,
                                onClick = {
                                    selectedInterval = interval
                                    viewModel.setUpdateInterval(interval.toLong())
                                }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedInterval == interval,
                            onClick = {
                                selectedInterval = interval
                                viewModel.setUpdateInterval(interval.toLong())
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        
        // 数据源信息
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "数据源说明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                DataSourceInfo(
                    title = "GNSS卫星定位",
                    description = "通过GPS、北斗、GLONASS、Galileo等多种卫星系统获取海拔数据",
                    accuracy = "±10米",
                    reliability = "中等"
                )
                
                DataSourceInfo(
                    title = "气压传感器",
                    description = "通过大气压力计算海拔高度",
                    accuracy = "±3米",
                    reliability = "较高"
                )
                
                DataSourceInfo(
                    title = "海拔API",
                    description = "基于地理位置的海拔数据库",
                    accuracy = "±1米",
                    reliability = "最高"
                )
            }
        }
        
        // GNSS系统支持说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "GNSS系统支持",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = GnssSystemInfo.getSupportDescription(),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "支持的主要卫星系统：",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                GnssSystemInfo.supportedSystems.take(4).forEach { system ->
                    Text(
                        text = "• ${system.name}（${system.country}）",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
        
        // 使用提示
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "使用提示",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "• 在户外开阔地带测量精度更高\n" +
                          "• 多卫星系统同时工作可提高定位精度\n" +
                          "• 气压传感器受天气影响，建议综合参考多个数据源\n" +
                          "• 实时更新模式会持续消耗电量和网络流量\n" +
                          "• 海拔数据按可靠度排序，星标表示最佳数据",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun DataSourceInfo(
    title: String,
    description: String,
    accuracy: String,
    reliability: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "精度: $accuracy",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "可靠度: $reliability",
                style = MaterialTheme.typography.bodySmall
            )
        }
        
        Divider(
            modifier = Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}