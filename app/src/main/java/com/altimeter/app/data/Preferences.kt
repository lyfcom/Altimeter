package com.altimeter.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.altimeter.core.*
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("preferences_v2")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class Preferences(
    val unit: DistanceUnit = DistanceUnit.METRES,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val mode: MeasureMode = MeasureMode.AUTO,
    val intervalSeconds: Int = 2,
    val keepScreenOn: Boolean = false,
    val calibration: Calibration? = null,
)

class PreferencesStore(private val context: Context) {
    private val unit = stringPreferencesKey("unit")
    private val theme = stringPreferencesKey("theme")
    private val mode = stringPreferencesKey("mode")
    private val interval = intPreferencesKey("interval")
    private val screen = booleanPreferencesKey("screen")
    private val pressure = doublePreferencesKey("calibration_pressure")
    private val calibratedAt = longPreferencesKey("calibrated_at")
    val values =
        context.dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { p ->
                Preferences(
                    unit = enumValue(p[unit], DistanceUnit.METRES),
                    theme = enumValue(p[theme], ThemeMode.SYSTEM),
                    mode = enumValue(p[mode], MeasureMode.AUTO),
                    intervalSeconds = (p[interval] ?: 2).takeIf { it in listOf(1, 2, 5, 10) } ?: 2,
                    keepScreenOn = p[screen] ?: false,
                    calibration = p[pressure]?.let { Calibration(it, p[calibratedAt] ?: 0) },
                )
            }

    suspend fun setUnit(value: DistanceUnit) {
        context.dataStore.edit { it[unit] = value.name }
    }

    suspend fun setTheme(value: ThemeMode) {
        context.dataStore.edit { it[theme] = value.name }
    }

    suspend fun setMode(value: MeasureMode) {
        context.dataStore.edit { it[mode] = value.name }
    }

    suspend fun setInterval(value: Int) {
        require(value in listOf(1, 2, 5, 10))
        context.dataStore.edit { it[interval] = value }
    }

    suspend fun setScreen(value: Boolean) {
        context.dataStore.edit { it[screen] = value }
    }

    suspend fun setCalibration(value: Calibration?) {
        context.dataStore.edit { p ->
            if (value == null) {
                p.remove(pressure)
                p.remove(calibratedAt)
            } else {
                p[pressure] = value.seaLevelPressureHpa
                p[calibratedAt] = value.createdAtMillis
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
