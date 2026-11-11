package com.altimeter.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altimeter.app.data.Preferences
import com.altimeter.app.measurement.MeasurementState
import com.altimeter.app.recording.RecordingController
import com.altimeter.core.*

@Composable
fun DashboardScreen(
    state: MeasurementState,
    preferences: Preferences,
    recording: RecordingController.State,
    trace: List<Reading>,
    now: Long,
    onMode: (MeasureMode) -> Unit,
    onPermission: () -> Unit,
    onLocationSettings: () -> Unit,
    onCalibrate: () -> Unit,
    onZero: () -> Unit,
) {
    val unit = preferences.unit
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("海拔计", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "ALTIMETER  /  FIELD NOTES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        Icons.Outlined.Terrain,
                        null,
                        modifier = Modifier.padding(12.dp).size(24.dp),
                    )
                }
            }
        }
        item { AltitudeHero(state, unit) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MeasureMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.mode == mode,
                        onClick = { onMode(mode) },
                        enabled = !recording.active,
                        label = {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(mode.label())
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (!state.frame.locationPermission)
            item { Notice("允许精确定位后可测量卫星高度、保存路线。气压相对测高可以先用。", "启用精确定位", onPermission) }
        else if (!state.frame.gpsEnabled)
            item { Notice("设备定位已关闭。开启后可获取卫星高度与行程位置。", "打开定位设置", onLocationSettings) }
        else if (state.satellite == null) item { Notice("等待有效卫星高度。请到天空开阔处，首次定位可能需要一段时间。") }
        if (state.calibrationExpired) item { Notice("气压校准已超过 12 小时，已停止将它作为海拔显示。可重新校准，或使用相对高度。") }
        if (state.frame.fix?.mock == true) item { Notice("当前定位由模拟位置提供，记录与导出会保留此标记。") }
        state.frame.issue?.let { issue -> item { Notice(issue) } }
        item {
            Panel {
                SectionLabel("高度变化", "最近 ${trace.size} 个采样")
                AltitudeChart(trace, unit)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Compress, null, tint = MaterialTheme.colorScheme.primary)
                    Metric(
                        "气压",
                        state.frame.pressureHpa
                            ?.takeIf { state.barometer != null }
                            ?.let { "%.1f".format(it) } ?: "—",
                        suffix = "hPa",
                    )
                    Text(
                        if (state.frame.hasBarometer) "平滑实时读数" else "设备无气压计",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Panel(Modifier.weight(1f)) {
                    Icon(
                        Icons.Outlined.SatelliteAlt,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Metric(
                        "卫星使用 / 可见",
                        "${state.frame.satellitesUsed}/${state.frame.satellitesVisible}",
                    )
                    Text(
                        state.satellite?.accuracyMetres?.let {
                            "垂直精度 ±${height(it, unit, 0)} ${unit.symbol}"
                        } ?: "垂直精度未知",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCalibrate,
                    enabled = state.barometer != null && !recording.active,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("校准高度")
                }
                OutlinedButton(
                    onClick = onZero,
                    enabled =
                        state.barometer?.reference == HeightReference.RELATIVE && !recording.active,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("相对归零")
                }
            }
        }
        item {
            Panel {
                val trip = recording.trip
                SectionLabel(
                    if (trip == null) "记录一段起伏" else "行程正在记录",
                    if (trip == null) "本地保存" else duration(now - trip.startedElapsed),
                )
                if (trip != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Metric(
                            "累计爬升",
                            height(trip.ascent, unit, 0),
                            modifier = Modifier.weight(1f),
                            suffix = unit.symbol,
                        )
                        Metric(
                            "累计下降",
                            height(trip.descent, unit, 0),
                            modifier = Modifier.weight(1f),
                            suffix = unit.symbol,
                        )
                    }
                    Text(
                        if (recording.waitingForSource) "等待原数据源恢复 · 已保存 ${trip.sampleCount} 点"
                        else "锁屏后继续记录 · ${trip.sampleCount} 个采样点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else
                    Text(
                        "记录高度曲线、爬升与下降，结束后导出 CSV 或 GPX。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                if (recording.active)
                    Text(
                        "为保持曲线可比，记录期间固定数据源与高度基准。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        item {
            Text(
                "卫星、气压各有误差。气压受天气影响，爬升值为滤噪后的估计。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AltitudeHero(state: MeasurementState, unit: DistanceUnit) {
    val reading = state.reading
    Surface(color = Forest, contentColor = Color(0xFFF3F8EA), shape = RoundedCornerShape(28.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val end = size.width - 20.dp.toPx()
                repeat(23) { index ->
                    val y = 28.dp.toPx() + index * (size.height - 56.dp.toPx()) / 22
                    drawLine(
                        MutedGreen.copy(alpha = if (index % 5 == 0) .55f else .22f),
                        Offset(end - (if (index % 5 == 0) 15.dp.toPx() else 7.dp.toPx()), y),
                        Offset(end, y),
                        1.dp.toPx(),
                    )
                }
            }
            Column(
                Modifier.padding(start = 24.dp, top = 24.dp, end = 48.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(7.dp)
                            .background(if (reading != null) Signal else MutedGreen, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(reading?.reference?.label() ?: "等待测量", fontSize = 14.sp, color = Signal)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (reading != null) "LIVE" else "READY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedGreen,
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val value =
                        height(reading?.metres, unit, if (reading?.source == Source.GNSS) 0 else 1)
                    val size =
                        (maxWidth.value / (value.length.coerceAtLeast(4) * .65f)).coerceIn(34f, 82f)
                    Column {
                        Text(
                            value,
                            fontSize = (size / LocalDensity.current.fontScale).sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-3).sp,
                            maxLines = 1,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            unit.symbol.uppercase() +
                                "  /  " +
                                (reading?.source?.label() ?: "卫星 · 气压"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGreen,
                        )
                    }
                }
                HorizontalDivider(color = MutedGreen.copy(alpha = .2f))
                Text(
                    when {
                        reading == null -> "开启定位，或等待设备气压读数"
                        reading.reference == HeightReference.RELATIVE -> "以起始气压为零点 · 可随时归零"
                        reading.reference == HeightReference.ELLIPSOID -> "WGS84 椭球基准 · 不等同于海拔"
                        reading.source == Source.BAROMETER -> "已校准 · 天气变化后请重新校准"
                        else ->
                            reading.accuracyMetres?.let {
                                "垂直精度 ±${height(it, unit, 0)} ${unit.symbol}"
                            } ?: "海平面基准 · 垂直精度未知"
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MutedGreen,
                )
            }
        }
    }
}
