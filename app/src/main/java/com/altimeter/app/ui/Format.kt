package com.altimeter.app.ui

import com.altimeter.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun height(value: Double?, unit: DistanceUnit, decimals: Int = 1): String =
    value?.let { String.format(Locale.getDefault(), "%.${decimals}f", unit.fromMetres(it)) } ?: "—"

fun Source.label() =
    when (this) {
        Source.GNSS -> "卫星"
        Source.BAROMETER -> "气压"
    }

fun HeightReference.label() =
    when (this) {
        HeightReference.SEA_LEVEL -> "海拔高度"
        HeightReference.ELLIPSOID -> "椭球高度"
        HeightReference.RELATIVE -> "相对高度"
    }

fun MeasureMode.label() =
    when (this) {
        MeasureMode.AUTO -> "自动"
        MeasureMode.GNSS -> "卫星"
        MeasureMode.BAROMETER -> "气压"
    }

fun timestamp(millis: Long) =
    DateTimeFormatter.ofPattern("MM月dd日 · HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))

fun duration(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) / 1000)
    return if (seconds >= 3600)
        "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
