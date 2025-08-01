package com.altimeter.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.altimeter.app.data.models.AltitudeSource
import com.altimeter.app.data.models.ChartDataPoint
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun AltitudeChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier
) {
    var selectedPoint by remember { mutableStateOf<ChartDataPoint?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    
    // 动画进度
    val animationProgress by animateFloatAsState(
        targetValue = if (dataPoints.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic),
        label = "chartAnimation"
    )
    
    if (dataPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无数据",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    
    val sortedPoints = dataPoints.sortedBy { it.timestamp }
    val minAltitude = sortedPoints.minOfOrNull { it.altitude } ?: 0.0
    val maxAltitude = sortedPoints.maxOfOrNull { it.altitude } ?: 100.0
    val altitudeRange = maxAltitude - minAltitude
    val padding = if (altitudeRange > 0) altitudeRange * 0.1 else 10.0
    
    val adjustedMin = minAltitude - padding
    val adjustedMax = maxAltitude + padding
    val adjustedRange = adjustedMax - adjustedMin
    
    Column(modifier = modifier) {
        // 图表标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "海拔变化趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "单位：米",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 图表主体区域
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Y轴标签
            Column(
                modifier = Modifier.width(50.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                for (i in 4 downTo 0) {
                    val value = adjustedMin + (adjustedRange * i / 4)
                    Text(
                        text = "${String.format("%.0f", value)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.height(48.dp)
                    )
                }
            }
            
            // 图表画布
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .pointerInput(sortedPoints) {
                        detectTapGestures { offset ->
                            // 检测点击的数据点
                            val clickedPoint = findClickedDataPoint(
                                offset = offset,
                                dataPoints = sortedPoints,
                                adjustedMin = adjustedMin,
                                adjustedRange = adjustedRange,
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                            )
                            if (clickedPoint != null) {
                                selectedPoint = clickedPoint
                                showDialog = true
                            }
                        }
                    }
            ) {
                drawAltitudeChart(
                    dataPoints = sortedPoints,
                    adjustedMin = adjustedMin,
                    adjustedRange = adjustedRange,
                    canvasSize = size,
                    animationProgress = animationProgress
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // X轴标签（时间）
        if (sortedPoints.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 左侧空白对齐Y轴标签
                Spacer(modifier = Modifier.width(50.dp))
                
                // 时间标签区域
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sortedPoints.first().timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (sortedPoints.size > 1) {
                        Text(
                            text = "时间",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = sortedPoints.last().timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 数据点详情弹窗
        if (showDialog && selectedPoint != null) {
            DataPointDialog(
                dataPoint = selectedPoint!!,
                onDismiss = { showDialog = false }
            )
        }
    }
}

private fun DrawScope.drawAltitudeChart(
    dataPoints: List<ChartDataPoint>,
    adjustedMin: Double,
    adjustedRange: Double,
    canvasSize: Size,
    animationProgress: Float = 1f
) {
    if (dataPoints.isEmpty() || adjustedRange <= 0) return
    
    val width = canvasSize.width
    val height = canvasSize.height
    val padding = 20f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2
    
    // 绘制网格线
    drawGrid(chartWidth, chartHeight, padding)
    
    // 准备路径点（带动画）
    val pathPoints = mutableListOf<Offset>()
    val points = mutableListOf<Pair<Offset, ChartDataPoint>>()
    
    val visiblePointsCount = (dataPoints.size * animationProgress).toInt().coerceAtMost(dataPoints.size)
    val visibleDataPoints = dataPoints.take(visiblePointsCount)
    
    visibleDataPoints.forEachIndexed { index, point ->
        val x = padding + (index.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * chartWidth
        val normalizedAltitude = ((point.altitude - adjustedMin) / adjustedRange).toFloat()
        val y = padding + chartHeight - (normalizedAltitude * chartHeight)
        
        val offset = Offset(x, y)
        pathPoints.add(offset)
        points.add(offset to point)
    }
    
    // 绘制渐变填充区域
    if (pathPoints.size >= 2) {
        val fillPath = Path().apply {
            moveTo(pathPoints[0].x, height - padding)
            pathPoints.forEach { point ->
                lineTo(point.x, point.y)
            }
            lineTo(pathPoints.last().x, height - padding)
            close()
        }
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x664CAF50),
                    Color(0x00000000)
                ),
                startY = 0f,
                endY = height
            )
        )
    }
    
    // 绘制连接线
    if (pathPoints.size >= 2) {
        for (i in 0 until pathPoints.size - 1) {
            drawLine(
                color = Color(0xFF4CAF50),
                start = pathPoints[i],
                end = pathPoints[i + 1],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
    
    // 绘制数据点
    points.forEach { (offset, point) ->
        val pointColor = when (point.source) {
            AltitudeSource.GNSS -> Color(0xFF2196F3)
            AltitudeSource.BAROMETER -> Color(0xFF4CAF50)
            AltitudeSource.ELEVATION_API -> Color(0xFFFF9800)
            else -> Color(0xFF9C27B0)
        }
        
        // 绘制点的外圈
        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = offset
        )
        
        // 绘制点的内圈
        drawCircle(
            color = pointColor,
            radius = 4.dp.toPx(),
            center = offset
        )
    }
}

private fun DrawScope.drawGrid(
    chartWidth: Float,
    chartHeight: Float,
    padding: Float
) {
    val gridColor = Color(0x20000000)
    val strokeWidth = 0.5.dp.toPx()
    
    // 绘制水平网格线
    for (i in 0..4) {
        val y = padding + (i.toFloat() / 4) * chartHeight
        drawLine(
            color = gridColor,
            start = Offset(padding, y),
            end = Offset(padding + chartWidth, y),
            strokeWidth = strokeWidth
        )
    }
    
    // 绘制垂直网格线
    for (i in 0..4) {
        val x = padding + (i.toFloat() / 4) * chartWidth
        drawLine(
            color = gridColor,
            start = Offset(x, padding),
            end = Offset(x, padding + chartHeight),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 检测点击的数据点
 */
private fun findClickedDataPoint(
    offset: Offset,
    dataPoints: List<ChartDataPoint>,
    adjustedMin: Double,
    adjustedRange: Double,
    canvasSize: Size
): ChartDataPoint? {
    if (dataPoints.isEmpty() || adjustedRange <= 0) return null
    
    val width = canvasSize.width
    val height = canvasSize.height
    val padding = 20f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2
    
    dataPoints.forEachIndexed { index, point ->
        val x = padding + (index.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * chartWidth
        val normalizedAltitude = ((point.altitude - adjustedMin) / adjustedRange).toFloat()
        val y = padding + chartHeight - (normalizedAltitude * chartHeight)
        
        val pointOffset = Offset(x, y)
        val distance = kotlin.math.sqrt(
            (offset.x - pointOffset.x) * (offset.x - pointOffset.x) +
            (offset.y - pointOffset.y) * (offset.y - pointOffset.y)
        )
        
        // 如果点击距离小于20像素，认为是点击了该数据点
        if (distance < 20f) {
            return point
        }
    }
    
    return null
}

/**
 * 数据点详情弹窗
 */
@Composable
fun DataPointDialog(
    dataPoint: ChartDataPoint,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "数据点详情",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                HorizontalDivider()
                
                DetailRow("海拔高度", "${String.format("%.1f", dataPoint.altitude)} 米")
                DetailRow("数据来源", dataPoint.source.displayName)
                DetailRow("可靠度", "${String.format("%.0f", dataPoint.reliability)}%")
                DetailRow(
                    "记录时间", 
                    dataPoint.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

/**
 * 详情行组件
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}