package com.altimeter.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.altimeter.app.data.Preferences
import com.altimeter.app.data.ThemeMode
import com.altimeter.app.measurement.*
import com.altimeter.app.recording.RecordingController
import com.altimeter.app.ui.*
import com.altimeter.core.*
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic UI fixture: checks actual controls while capturing the real Compose layout. */
@RunWith(AndroidJUnit4::class)
class DashboardUsabilityTest {
    @get:Rule val compose = createComposeRule()
    private val reading =
        Reading(128.4, Source.BAROMETER, HeightReference.SEA_LEVEL, null, 180_000, 1)
    private val fixture =
        MeasurementState(
            frame =
                SensorFrame(
                    nowElapsed = 180_000,
                    pressureHpa = 998.1,
                    pressureElapsed = 180_000,
                    hasBarometer = true,
                    locationPermission = true,
                    gpsEnabled = true,
                    satellitesUsed = 14,
                    satellitesVisible = 26,
                ),
            reading = reading,
            barometer = reading,
            satellite = Reading(131.0, Source.GNSS, HeightReference.SEA_LEVEL, 8.0, 180_000),
        )

    @Test
    fun recordingControlsAreReachableAndModeChangesAreExplicit() {
        var selected = MeasureMode.AUTO
        var started = false
        compose.setContent {
            FixtureTheme(ThemeMode.LIGHT) {
                Column {
                    Box(Modifier.weight(1f)) {
                        DashboardScreen(
                            fixture,
                            Preferences(),
                            RecordingController.State(),
                            (0..90).map {
                                reading.copy(
                                    metres = 120 + kotlin.math.sin(it / 12.0) * 4 + it / 12.0,
                                    elapsedMillis = it * 2000L,
                                )
                            },
                            180_000,
                            { selected = it },
                            {},
                            {},
                            {},
                            {},
                        )
                    }
                    RecordingBar(RecordingController.State(), true, 180_000) { started = true }
                }
            }
        }
        compose.onNode(hasText("气压") and hasClickAction()).performClick()
        org.junit.Assert.assertEquals(MeasureMode.BAROMETER, selected)
        capture("dashboard-light")
        compose.onNodeWithText("开始记录").assertIsDisplayed().performClick()
        org.junit.Assert.assertTrue(started)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("记录一段起伏"))
        capture("dashboard-controls")
    }

    @Test
    fun noSensorStateExplainsHowToProceed() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                FixtureTheme(ThemeMode.DARK) {
                    Column {
                        Box(Modifier.weight(1f)) {
                            DashboardScreen(
                                MeasurementState(),
                                Preferences(),
                                RecordingController.State(),
                                emptyList(),
                                0,
                                {},
                                {},
                                {},
                                {},
                                {},
                            )
                        }
                        RecordingBar(RecordingController.State(), false, 0) {}
                    }
                }
            }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("启用精确定位"))
        compose.onNodeWithText("启用精确定位").assertIsDisplayed()
        compose.onNodeWithText("开始记录").assertIsDisplayed().assertIsNotEnabled()
        capture("dashboard-large-text")
    }

    @Composable
    private fun FixtureTheme(mode: ThemeMode, content: @Composable () -> Unit) {
        AltimeterTheme(mode) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }

    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { stream ->
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
