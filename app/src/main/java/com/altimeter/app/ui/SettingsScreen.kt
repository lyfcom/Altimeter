package com.altimeter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.altimeter.app.data.*
import com.altimeter.app.measurement.MeasurementState
import com.altimeter.core.*

@Composable
fun SettingsScreen(
    preferences: Preferences,
    state: MeasurementState,
    recording: Boolean,
    onUnit: (DistanceUnit) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onInterval: (Int) -> Unit,
    onScreen: (Boolean) -> Unit,
    onCalibrate: () -> Unit,
    onClearCalibration: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Text("校准与设置", style = MaterialTheme.typography.headlineLarge) }
        item {
            Panel {
                SectionLabel(
                    "高度校准",
                    if (state.barometer?.reference == HeightReference.SEA_LEVEL) "已校准" else "未校准",
                )
                Text(
                    "输入已知海拔。校准有效期 12 小时，天气变化后请重新校准。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                preferences.calibration?.let { cal ->
                    Text(
                        "上次校准：${timestamp(cal.createdAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    onClick = onCalibrate,
                    enabled = state.barometer != null && !recording,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("输入已知海拔")
                }
                if (preferences.calibration != null)
                    TextButton(onClick = onClearCalibration, enabled = !recording) { Text("清除校准") }
            }
        }
        item {
            Panel {
                SectionLabel("显示单位")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DistanceUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = preferences.unit == unit,
                            onClick = { onUnit(unit) },
                            label = {
                                Text(if (unit == DistanceUnit.METRES) "米 · m" else "英尺 · ft")
                            },
                        )
                    }
                }
                SectionLabel("外观")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { theme ->
                        FilterChip(
                            selected = preferences.theme == theme,
                            onClick = { onTheme(theme) },
                            label = {
                                Text(
                                    when (theme) {
                                        ThemeMode.SYSTEM -> "跟随系统"
                                        ThemeMode.LIGHT -> "浅色"
                                        ThemeMode.DARK -> "深色"
                                    }
                                )
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("使用时保持亮屏")
                        Text(
                            "仅在应用前台生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = preferences.keepScreenOn, onCheckedChange = onScreen)
                }
            }
        }
        item {
            Panel {
                SectionLabel("记录间隔")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 5, 10).forEach { seconds ->
                        FilterChip(
                            selected = preferences.intervalSeconds == seconds,
                            onClick = { onInterval(seconds) },
                            enabled = !recording,
                            label = { Text("${seconds}秒") },
                        )
                    }
                }
                Text(
                    "短间隔保留更多采样点。记录会持续使用定位和传感器。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Panel {
                SectionLabel("读数说明")
                Text("卫星高度：未完成海平面修正时显示椭球高度，不能作为海拔。", style = MaterialTheme.typography.bodyMedium)
                Text("相对高度：用于观察短时高度变化，会受天气影响。", style = MaterialTheme.typography.bodyMedium)
                Text("累计爬升：已过滤小幅噪声，仅作估计。", style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            Text(
                "Altimeter 2.0 · 离线运行\n本地存储，系统备份遵循设备设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CalibrationDialog(
    unit: DistanceUnit,
    satellite: Reading?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val parsed = input.trim().replace(',', '.').toDoubleOrNull()?.let(unit::toMetres)
    val valid = parsed != null && parsed.isFinite() && parsed in -1000.0..10000.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校准气压高度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("输入当前位置的已知海拔，优先使用可靠标高。")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("已知海拔 (${unit.symbol})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = input.isNotBlank() && !valid,
                    supportingText = {
                        Text(
                            "范围：${height(-1000.0, unit, 0)} 至 ${height(10000.0, unit, 0)} ${unit.symbol}"
                        )
                    },
                )
                if (
                    satellite?.reference == HeightReference.SEA_LEVEL &&
                        (satellite.accuracyMetres ?: Double.MAX_VALUE) <= 20
                ) {
                    TextButton(onClick = { input = height(satellite.metres, unit) }) {
                        Text("使用卫星海拔 ${height(satellite.metres, unit)} ${unit.symbol}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) {
                        onConfirm(parsed!!)
                        onDismiss()
                    }
                },
                enabled = valid,
            ) {
                Text("应用校准")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
