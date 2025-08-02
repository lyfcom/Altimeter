package com.altimeter.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
    
    // 缩放与平移状态（仅 X 轴）
    var scaleX by remember { mutableFloatStateOf(1f) }
    var translateX by remember { mutableFloatStateOf(0f) }
    val minScale = 1f
    val maxScale = 10f

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
                    // 手势缩放/平移
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val newScale = (scaleX * zoom).coerceIn(minScale, maxScale)
                            val scaleChange = newScale / scaleX
                            translateX = (translateX + centroid.x - centroid.x * scaleChange + pan.x).coerceIn(
                                -(size.width * (newScale - 1f)),
                                0f
                            )
                            scaleX = newScale
                        }
                    }
                    // 轻点检测
                    .pointerInput(sortedPoints, scaleX, translateX) {
                        detectTapGestures { offset ->
                            val paddingPx = 20.dp.toPx()
                            val transformedOffset = Offset(
                                x = (offset.x - translateX - paddingPx) / scaleX + paddingPx,
                                y = offset.y
                            )
                            val clicked = findClickedDataPoint(
                                offset = transformedOffset,
                                dataPoints = sortedPoints,
                                adjustedMin = adjustedMin,
                                adjustedRange = adjustedRange,
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat())
                            )
                            if (clicked != null) {
                                selectedPoint = clicked
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
                    animationProgress = animationProgress,
                    scaleX = scaleX,
                    translateX = translateX
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // X轴标签（时间）
        if (sortedPoints.isNotEmpty()) {
            // 根据缩放和平移计算可见的时间范围
            val totalPoints = sortedPoints.size
            val density = LocalDensity.current
            val chartWidthPx = with(density) { 240.dp.toPx() } // 实际画布宽度（减去左右Y轴padding）
            val paddingPx = with(density) { 20.dp.toPx() }
            val effectiveChartWidth = chartWidthPx - paddingPx * 2
            
            // 计算当前可见范围在原始坐标系中的比例
            val leftVisibleX = -translateX  // 可见区域左边界在缩放坐标系中的位置
            val rightVisibleX = effectiveChartWidth - translateX  // 可见区域右边界在缩放坐标系中的位置
            
            // 转换为原始坐标系比例 (0-1)
            val leftRatio = (leftVisibleX / scaleX / effectiveChartWidth).coerceIn(0f, 1f)
            val rightRatio = (rightVisibleX / scaleX / effectiveChartWidth).coerceIn(0f, 1f)
            
            // 计算对应的数据点索引
            val startIndex = (leftRatio * (totalPoints - 1)).toInt().coerceIn(0, totalPoints - 1)
            val endIndex = (rightRatio * (totalPoints - 1)).toInt().coerceIn(0, totalPoints - 1)
            
            val startTime = sortedPoints[startIndex].timestamp
            val endTime = sortedPoints[endIndex].timestamp
            
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
                        text = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
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
                            text = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
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
    animationProgress: Float = 1f,
    scaleX: Float = 1f,
    translateX: Float = 0f
) {
    if (dataPoints.isEmpty() || adjustedRange <= 0) return
    
    val width = canvasSize.width
    val height = canvasSize.height
    val padding = 20f
    val chartWidth = width - padding * 2
    val chartHeight = height - padding * 2
    
    // 绘制网格线
    drawGrid(chartWidth, chartHeight, padding, scaleX, translateX)
    
    // 准备路径点（带动画）
    val pathPoints = mutableListOf<Offset>()
    val points = mutableListOf<Pair<Offset, ChartDataPoint>>()
    
    val visiblePointsCount = (dataPoints.size * animationProgress).toInt().coerceAtMost(dataPoints.size)
    val visibleDataPoints = dataPoints.take(visiblePointsCount)
    
    visibleDataPoints.forEachIndexed { index, point ->
        val baseX = (index.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * chartWidth
        val x = padding + baseX * scaleX + translateX
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
    
    // 绘制数据点（仅绘制可见区域内的点）
    points.forEach { (offset, point) ->
        // 检查点是否在可见区域内
        if (offset.x >= padding && offset.x <= padding + chartWidth) {
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
}

private fun DrawScope.drawGrid(
    chartWidth: Float,
    chartHeight: Float,
    padding: Float,
    scaleX: Float = 1f,
    translateX: Float = 0f
) {
    val gridColor = Color(0x20000000)
    val strokeWidth = 0.5.dp.toPx()
    
    // 绘制水平网格线（不受 X 轴缩放影响）
    for (i in 0..4) {
        val y = padding + (i.toFloat() / 4) * chartHeight
        drawLine(
            color = gridColor,
            start = Offset(padding, y),
            end = Offset(padding + chartWidth, y),
            strokeWidth = strokeWidth
        )
    }
    
    // 绘制垂直网格线（受 X 轴缩放和平移影响）
    val gridSpacing = chartWidth / 4f
    for (i in -4..8) { // 扩展范围以支持平移
        val baseX = i * gridSpacing
        val x = padding + baseX * scaleX + translateX
        
        // 只绘制在可见区域内的网格线
        if (x >= padding && x <= padding + chartWidth) {
            drawLine(
                color = gridColor,
                start = Offset(x, padding),
                end = Offset(x, padding + chartHeight),
                strokeWidth = strokeWidth
            )
        }
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
        val baseX = (index.toFloat() / (dataPoints.size - 1).coerceAtLeast(1)) * (width - padding * 2)
        val x = padding + baseX
        val normalizedAltitude = ((point.altitude - adjustedMin) / adjustedRange).toFloat()
        val y = padding + (height - padding * 2) - (normalizedAltitude * (height - padding * 2))
        
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