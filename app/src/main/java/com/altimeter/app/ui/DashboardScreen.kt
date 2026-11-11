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
            item {
                Notice(
                    if (state.frame.hasBarometer) "启用精确定位以测量卫星高度。当前可使用气压测高。" else "无可用气压计，请启用精确定位。",
                    "启用精确定位",
                    onPermission,
                )
            }
        else if (!state.frame.gpsEnabled) item { Notice("定位已关闭。", "开启定位", onLocationSettings) }
        else if (state.satellite == null) item { Notice("等待卫星信号，请移至开阔处。") }
        if (state.calibrationExpired) item { Notice("气压校准已失效，请重新校准。") }
        if (state.frame.fix?.mock == true) item { Notice("当前为模拟位置。") }
        state.frame.issue?.let { issue -> item { Notice(issue) } }
        item {
            Panel {
                SectionLabel("高度曲线")
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
                        if (!state.frame.hasBarometer) "无气压计"
                        else if (state.barometer == null) "等待读数" else "实时",
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
        recording.trip?.let { trip ->
            item {
                Panel {
                    SectionLabel("记录中", duration(now - trip.startedElapsed))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Metric("爬升", height(trip.ascent, unit, 0), Modifier.weight(1f), unit.symbol)
                        Metric(
                            "下降",
                            height(trip.descent, unit, 0),
                            Modifier.weight(1f),
                            unit.symbol,
                        )
                    }
                    Text(
                        if (recording.waitingForSource) "等待信号 · 已保存 ${trip.sampleCount} 点"
                        else "已保存 ${trip.sampleCount} 点",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
                            unit.symbol + "  /  " + (reading?.source?.label() ?: "卫星 · 气压"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedGreen,
                        )
                    }
                }
                HorizontalDivider(color = MutedGreen.copy(alpha = .2f))
                Text(
                    when {
                        reading == null -> "等待定位或气压数据"
                        reading.reference == HeightReference.RELATIVE -> "以归零点为基准"
                        reading.reference == HeightReference.ELLIPSOID -> "WGS84 椭球高度，非海拔"
                        reading.source == Source.BAROMETER -> "已校准"
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
