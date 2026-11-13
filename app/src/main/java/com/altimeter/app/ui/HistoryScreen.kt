package com.altimeter.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.altimeter.app.data.TrackPreview
import com.altimeter.app.data.TripEntity
import com.altimeter.app.data.TripStatus
import com.altimeter.core.*
import kotlinx.coroutines.CancellationException

@Composable
fun HistoryScreen(trips: List<TripEntity>, unit: DistanceUnit, onSelect: (TripEntity) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("行程记录", style = MaterialTheme.typography.headlineLarge)
            Text(
                "${trips.size} 段行程",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trips.isEmpty())
            item {
                Panel {
                    Spacer(Modifier.height(16.dp))
                    Icon(
                        Icons.Outlined.Route,
                        null,
                        Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text("暂无行程", style = MaterialTheme.typography.titleLarge)
                    Text("在仪表页开始记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                }
            }
        items(trips, key = { it.id }) { trip ->
            Panel(Modifier.clickable { onSelect(trip) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(trip.title, style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Outlined.ChevronRight, "查看行程")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(
                        "爬升",
                        height(trip.ascent, unit, 0),
                        modifier = Modifier.weight(1f),
                        suffix = unit.symbol,
                    )
                    Metric(
                        "下降",
                        height(trip.descent, unit, 0),
                        modifier = Modifier.weight(1f),
                        suffix = unit.symbol,
                    )
                }
                Text(
                    "${trip.sampleCount} 点 · ${trip.endedAt?.let { duration(trip.durationMillis) } ?: "记录中"}" +
                        if (trip.status == TripStatus.INTERRUPTED) " · 记录中断" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailSheet(
    trip: TripEntity,
    unit: DistanceUnit,
    load: suspend (String) -> TrackPreview,
    onDismiss: () -> Unit,
    onExport: (Boolean) -> Unit,
    onDelete: () -> Unit,
    busy: Boolean = false,
) {
    var points by remember(trip.id) { mutableStateOf<TrackPreview?>(null) }
    var error by remember(trip.id) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(trip.id, trip.sampleCount) {
        try {
            points = load(trip.id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "无法读取此行程，请关闭后重试"
        }
    }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Text(trip.title, style = MaterialTheme.typography.headlineMedium) }

            error?.let { item { Notice(it) } }
            item {
                if (points == null && error == null)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                else
                    AltitudeChart(
                        points?.points.orEmpty().map { it.reading },
                        unit,
                        breakBefore = points?.breakBefore,
                    )
            }
            item {
                Row(Modifier.fillMaxWidth()) {
                    Metric(
                        "最低",
                        height(trip.minimum, unit),
                        modifier = Modifier.weight(1f),
                        suffix = unit.symbol,
                    )
                    Metric(
                        "最高",
                        height(trip.maximum, unit),
                        modifier = Modifier.weight(1f),
                        suffix = unit.symbol,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth()) {
                    Metric(
                        "累计爬升",
                        height(trip.ascent, unit),
                        modifier = Modifier.weight(1f),
                        suffix = unit.symbol,
                    )
                    Metric(
                        "累计下降",
                        height(trip.descent, unit),
                        modifier = Modifier.weight(1f),
                        suffix = unit.symbol,
                    )
                }
            }
            points?.points?.firstOrNull()?.reading?.let { reading ->
                item {
                    Text(
                        "${reading.source.label()} · ${reading.reference.label()} · ${trip.sampleCount} 个采样点",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item {
                Text(
                    "CSV 包含全部采样；GPX 仅含定位点，非海平面高度写入扩展字段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onExport(false) },
                        enabled = !busy && trip.status != TripStatus.RECORDING,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("导出 CSV")
                    }
                    OutlinedButton(
                        onClick = { onExport(true) },
                        enabled =
                            !busy &&
                                trip.status != TripStatus.RECORDING &&
                                points?.hasCoordinates == true,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("导出 GPX")
                    }
                }
            }
            item {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !busy && trip.status != TripStatus.RECORDING,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除此行程")
                }
            }
        }
    }
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这段行程？") },
            text = { Text("此行程的所有测量点将一起删除，无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("保留") } },
        )
}
