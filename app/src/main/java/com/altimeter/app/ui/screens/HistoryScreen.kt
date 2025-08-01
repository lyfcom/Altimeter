package com.altimeter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.altimeter.app.data.models.*
import com.altimeter.app.ui.components.AltitudeChart
import com.altimeter.app.ui.viewmodels.AltimeterViewModel
import com.altimeter.app.utils.ShareHelper
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: AltimeterViewModel = viewModel()
) {
    val statistics by viewModel.altitudeStatistics.collectAsState(
        initial = AltitudeStatistics(
            totalRecords = 0,
            totalSessions = 0,
            averageAltitude = 0.0,
            maxAltitude = 0.0,
            minAltitude = 0.0,
            mostReliableSource = AltitudeSource.GNSS,
            firstRecordTime = null,
            lastRecordTime = null
        )
    )
    val chartDataPoints by viewModel.chartDataPoints.collectAsState(initial = emptyList())
    val records by viewModel.altitudeRecords.collectAsState(initial = emptyList())
    val sessions by viewModel.altitudeSessions.collectAsState(initial = emptyList())
    val isAutoRecordEnabled by viewModel.isAutoRecordEnabled.collectAsState()
    
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showShareOptions by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 紧凑的标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                    text = "海拔历史",
                style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 自动记录开关
                    Text(
                        text = "自动记录",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Switch(
                        checked = isAutoRecordEnabled,
                        onCheckedChange = viewModel::setAutoRecordEnabled,
                        modifier = Modifier.scale(0.8f)
                    )
                
                // 分享按钮
                IconButton(
                    onClick = { showShareOptions = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "分享数据",
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // 清除按钮
                IconButton(
                    onClick = viewModel::clearAllRecords,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "清除记录",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        // 分享选项弹窗
        if (showShareOptions) {
            ShareOptionsDialog(
                onDismiss = { showShareOptions = false },
                onShareStatistics = {
                    ShareHelper.shareStatistics(context, statistics, records)
                    showShareOptions = false
                },
                onShareRecordsText = {
                    ShareHelper.shareRecordsAsText(context, records)
                    showShareOptions = false
                },
                onShareRecordsCSV = {
                    ShareHelper.shareRecordsAsCSV(context, records)
                    showShareOptions = false
                }
            )
        }
        
        // 标签页
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("图表") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("统计") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("记录") }
            )
        }
        
        // 内容区域
        when (selectedTab) {
            0 -> ChartTab(
                chartDataPoints = chartDataPoints,
                modifier = Modifier.fillMaxSize()
            )
            1 -> StatisticsTab(
                statistics = statistics,
                sessions = sessions,
                modifier = Modifier.fillMaxSize()
            )
            2 -> RecordsTab(
                records = records,
                onDeleteRecord = viewModel::deleteRecord,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ChartTab(
    chartDataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var selectedTimeRange by remember { mutableStateOf(TimeRange.ALL) }
    var showDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    
    // 根据时间范围过滤数据
    val filteredDataPoints = remember(chartDataPoints, selectedTimeRange, customStartDate, customEndDate) {
        val now = java.time.LocalDateTime.now()
        when (selectedTimeRange) {
            TimeRange.HOUR_24 -> chartDataPoints.filter { 
                it.timestamp.isAfter(now.minusHours(24)) 
            }
            TimeRange.DAYS_7 -> chartDataPoints.filter { 
                it.timestamp.isAfter(now.minusDays(7)) 
            }
            TimeRange.DAYS_30 -> chartDataPoints.filter { 
                it.timestamp.isAfter(now.minusDays(30)) 
            }
            TimeRange.ALL -> chartDataPoints
            TimeRange.CUSTOM -> {
                if (customStartDate != null && customEndDate != null) {
                    chartDataPoints.filter { dataPoint ->
                        val dataDate = dataPoint.timestamp.toLocalDate()
                        !dataDate.isBefore(customStartDate) && !dataDate.isAfter(customEndDate)
                    }
                } else {
                    chartDataPoints
                }
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 时间范围选择器
        TimeRangeSelector(
            selectedRange = selectedTimeRange,
            onRangeSelected = { range ->
                selectedTimeRange = range
                if (range == TimeRange.CUSTOM) {
                    showDatePicker = true
                }
            },
            customStartDate = customStartDate,
            customEndDate = customEndDate
        )
        
        // 自定义日期选择对话框
        if (showDatePicker) {
            CustomDateRangeDialog(
                startDate = customStartDate,
                endDate = customEndDate,
                onDateRangeSelected = { start, end ->
                    customStartDate = start
                    customEndDate = end
                    showDatePicker = false
                },
                onDismiss = { 
                    showDatePicker = false
                    if (customStartDate == null || customEndDate == null) {
                        selectedTimeRange = TimeRange.ALL
                    }
                }
            )
        }
        
        if (filteredDataPoints.isNotEmpty()) {
            // 图表卡片
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "海拔变化趋势",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 图表组件
                    AltitudeChart(
                        dataPoints = filteredDataPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 图例
                    ChartLegend()
                }
            }
            
            // 最近数据点信息
            filteredDataPoints.lastOrNull()?.let { lastPoint ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "最新记录",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = "${String.format("%.1f", lastPoint.altitude)} 米",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "来源: ${lastPoint.source.displayName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Text(
                            text = "时间: ${lastPoint.timestamp.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            // 空状态
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "暂无图表数据",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Text(
                        text = "开始测量海拔高度来生成图表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun StatisticsTab(
    statistics: AltitudeStatistics,
    sessions: List<AltitudeMeasurementSession>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 总体统计
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "总体统计",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    StatisticItem("总记录数", "${statistics.totalRecords}")
                    StatisticItem("测量会话", "${statistics.totalSessions}")
                    StatisticItem("平均海拔", "${String.format("%.1f", statistics.averageAltitude)} 米")
                    StatisticItem("最高海拔", "${String.format("%.1f", statistics.maxAltitude)} 米")
                    StatisticItem("最低海拔", "${String.format("%.1f", statistics.minAltitude)} 米")
                    StatisticItem("海拔范围", "${String.format("%.1f", statistics.maxAltitude - statistics.minAltitude)} 米")
                    StatisticItem("最可靠来源", statistics.mostReliableSource.displayName)
                }
            }
        }
        
        // 会话列表
        if (sessions.isNotEmpty()) {
            item {
                Text(
                    text = "测量会话",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(sessions.reversed()) { session ->
                SessionCard(session = session)
            }
        }
    }
}

@Composable
fun RecordsTab(
    records: List<AltitudeRecord>,
    onDeleteRecord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (records.isNotEmpty()) {
        LazyColumn(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(records.reversed()) { record ->
                RecordCard(
                    record = record,
                    onDelete = { onDeleteRecord(record.id) }
                )
            }
        }
    } else {
        // 空状态
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "暂无记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
            
            Text(
                text = "开始测量来创建海拔记录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(color = Color(0xFF2196F3), label = "卫星定位")
        LegendItem(color = Color(0xFF4CAF50), label = "气压传感器")
        LegendItem(color = Color(0xFFFF9800), label = "API数据")
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun StatisticItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SessionCard(session: AltitudeMeasurementSession) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = session.sessionType.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "${session.totalRecords} 条记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            StatisticItem("开始时间", session.startTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
            session.endTime?.let { endTime ->
                StatisticItem("结束时间", endTime.format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
            }
            StatisticItem("平均海拔", "${String.format("%.1f", session.averageAltitude)} 米")
            StatisticItem("海拔范围", "${String.format("%.1f", session.altitudeRange)} 米")
        }
    }
}

@Composable
fun RecordCard(
    record: AltitudeRecord,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.source.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除记录",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Text(
                text = "${String.format("%.1f", record.altitude)} 米",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "可靠度: ${String.format("%.0f", record.reliability)}%",
                    style = MaterialTheme.typography.bodySmall
                )
                
                Text(
                    text = "精度: ±${String.format("%.1f", record.accuracy)}m",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Text(
                text = record.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 时间范围枚举
 */
enum class TimeRange(val displayName: String) {
    HOUR_24("24小时"),
    DAYS_7("7天"),
    DAYS_30("30天"),
    ALL("全部"),
    CUSTOM("自定义")
}

/**
 * 时间范围选择器
 */
@Composable
fun TimeRangeSelector(
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    customStartDate: java.time.LocalDate? = null,
    customEndDate: java.time.LocalDate? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "时间范围",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TimeRange.values()) { timeRange ->
                    FilterChip(
                        onClick = { onRangeSelected(timeRange) },
                        label = { 
                            Text(
                                text = if (timeRange == TimeRange.CUSTOM && customStartDate != null && customEndDate != null) {
                                    "${customStartDate.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))} 至 ${customEndDate.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd"))}"
                                } else {
                                    timeRange.displayName
                                },
                                style = MaterialTheme.typography.bodySmall
                            ) 
                        },
                        selected = selectedRange == timeRange
                    )
                }
            }
            
            // 自定义日期范围显示
            if (selectedRange == TimeRange.CUSTOM && customStartDate != null && customEndDate != null) {
                Text(
                    text = "已选择: ${customStartDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))} 至 ${customEndDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * 自定义日期范围对话框
 */
@Composable
fun CustomDateRangeDialog(
    startDate: java.time.LocalDate?,
    endDate: java.time.LocalDate?,
    onDateRangeSelected: (java.time.LocalDate, java.time.LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val today = java.time.LocalDate.now()
    var selectedStartDate by remember { mutableStateOf(startDate ?: today.minusDays(7)) }
    var selectedEndDate by remember { mutableStateOf(endDate ?: today) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择日期范围",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 开始日期选择
                Column {
                    Text(
                        text = "开始日期",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 精确日期选择按钮
                    OutlinedButton(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedStartDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // 快捷日期选择
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(listOf(
                            "今天" to today,
                            "昨天" to today.minusDays(1),
                            "7天前" to today.minusDays(7),
                            "30天前" to today.minusDays(30),
                            "90天前" to today.minusDays(90)
                        )) { (label, date) ->
                            FilterChip(
                                onClick = { selectedStartDate = date },
                                label = { 
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                },
                                selected = selectedStartDate == date
                            )
                        }
                    }
                }
                
                // 结束日期选择
                Column {
                    Text(
                        text = "结束日期",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 精确日期选择按钮
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedEndDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    // 快捷日期选择
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(listOf(
                            "今天" to today,
                            "昨天" to today.minusDays(1),
                            "3天前" to today.minusDays(3),
                            "7天前" to today.minusDays(7)
                        ).filter { !it.second.isBefore(selectedStartDate) }) { (label, date) ->
                            FilterChip(
                                onClick = { selectedEndDate = date },
                                label = { 
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                },
                                selected = selectedEndDate == date
                            )
                        }
                    }
                }
                
                // 日期范围提示
                if (selectedEndDate.isBefore(selectedStartDate)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "结束日期不能早于开始日期",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(selectedStartDate, selectedEndDate)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "共选择 ${daysBetween + 1} 天的数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        if (!selectedEndDate.isBefore(selectedStartDate)) {
                            onDateRangeSelected(selectedStartDate, selectedEndDate)
                        }
                    },
                    enabled = !selectedEndDate.isBefore(selectedStartDate)
                ) {
                    Text("确定")
                }
            }
        }
    )
    
    // 开始日期选择器
    if (showStartDatePicker) {
        DatePickerModal(
            initialDate = selectedStartDate,
            onDateSelected = { date ->
                selectedStartDate = date
                // 如果开始日期晚于结束日期，自动调整结束日期
                if (date.isAfter(selectedEndDate)) {
                    selectedEndDate = date
                }
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
    
    // 结束日期选择器
    if (showEndDatePicker) {
        DatePickerModal(
            initialDate = selectedEndDate,
            minDate = selectedStartDate,
            onDateSelected = { date ->
                selectedEndDate = date
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
}

/**
 * 日期选择器模态框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    initialDate: java.time.LocalDate,
    minDate: java.time.LocalDate? = null,
    maxDate: java.time.LocalDate? = null,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
        yearRange = (initialDate.year - 10)..(initialDate.year + 1)
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            
                            // 检查日期范围
                            val isValidDate = (minDate == null || !selectedDate.isBefore(minDate)) &&
                                    (maxDate == null || !selectedDate.isAfter(maxDate))
                            
                            if (isValidDate) {
                                onDateSelected(selectedDate)
                            }
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("确定")
                }
            }
        },
        text = {
            Column {
                // 日期范围提示
                if (minDate != null || maxDate != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "日期范围限制",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (minDate != null) {
                                Text(
                                    text = "最早日期: ${minDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (maxDate != null) {
                                Text(
                                    text = "最晚日期: ${maxDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
                
                // 日期选择器
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier.fillMaxWidth(),
                    showModeToggle = true,
                    title = {
                        Text(
                            text = "选择日期",
                            modifier = Modifier.padding(16.dp)
                        )
                    },
                    headline = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate()
                            
                            val isValidDate = (minDate == null || !selectedDate.isBefore(minDate)) &&
                                    (maxDate == null || !selectedDate.isAfter(maxDate))
                            
                            Text(
                                text = if (isValidDate) {
                                    selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"))
                                } else {
                                    "请选择有效日期"
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = if (isValidDate) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                )
            }
        }
    )
}

/**
 * 分享选项对话框
 */
@Composable
fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onShareStatistics: () -> Unit,
    onShareRecordsText: () -> Unit,
    onShareRecordsCSV: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "分享数据",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "选择要分享的数据类型：",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // 分享统计数据
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShareStatistics() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "统计概览",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "分享海拔统计数据摘要",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // 分享记录文本
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShareRecordsText() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "详细记录（文本）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "分享可读的文本格式记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                // 分享CSV文件
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShareRecordsCSV() },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "详细记录（CSV）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "分享CSV文件，便于数据分析",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}