package com.altimeter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                    "在海拔已知的地点输入高度，将当地气压作为参考。气象变化会带来漂移，校准在 12 小时后失效。",
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
                    TextButton(onClick = onClearCalibration, enabled = !recording) {
                        Text("清除校准，恢复相对测高")
                    }
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
                    "短间隔保留更多细节，也会增加数据量。记录时保持卫星和气压测量，锁屏后仍会耗电。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Panel {
                SectionLabel("读数说明")
                Text(
                    "海拔高度\n相对于平均海平面。Android 14 及以上优先使用系统离线模型修正卫星高度；不可用时明确显示椭球高度。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "相对高度\n基于气压变化，适合观察楼层、电梯和短时间的高度变化。天气也会影响读数，不把它伪装成绝对海拔。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "累计爬升\n使用阈值过滤小幅噪声，数据中断不会连算。数值是估计，缓慢的小幅变化可能被忽略。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Text(
                "ALTIMETER  2.0\n离线运行 · 无广告 · 无账号 · 本地存储 · 系统备份遵循设备设置\nCSV 保留所有测量；GPX 只包含有坐标的点。",
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
    var input by remember { mutableStateOf("") }
    val parsed = input.trim().replace(',', '.').toDoubleOrNull()?.let(unit::toMetres)
    val valid = parsed != null && parsed.isFinite() && parsed in -1000.0..10000.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校准气压高度") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("输入当前位置的已知海拔。优先使用可靠标高；卫星高度也有误差。")
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
                        Text("填入当前卫星海拔 ${height(satellite.metres, unit)} ${unit.symbol}")
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
