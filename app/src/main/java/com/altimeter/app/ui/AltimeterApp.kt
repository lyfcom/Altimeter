package com.altimeter.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*

@Composable
fun AltimeterApp(
    viewModel: AltimeterViewModel,
    onPermission: () -> Unit,
    onLocationSettings: () -> Unit,
    onRecord: () -> Unit,
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val state by viewModel.measurement.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val trace by viewModel.trace.collectAsStateWithLifecycle()
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var calibration by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    DisposableEffect(view, preferences.keepScreenOn) {
        view.keepScreenOn = preferences.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    val now by
        produceState(android.os.SystemClock.elapsedRealtime(), recording.active) {
            while (recording.active) {
                value = android.os.SystemClock.elapsedRealtime()
                delay(1000)
            }
        }
    LaunchedEffect(Unit) { viewModel.events.collect { snack.showSnackbar(it) } }
    LaunchedEffect(recording.error) {
        recording.error?.let {
            snack.showSnackbar(it)
            viewModel.clearRecordingError()
        }
    }
    fun export(uri: android.net.Uri?, gpx: Boolean) {
        val id = exportId ?: return
        if (uri == null) return
        exporting = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream =
                        context.contentResolver.openOutputStream(uri, "wt") ?: error("无法打开目标文件")
                    stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                        viewModel.export(id, gpx, writer)
                    }
                }
                snack.showSnackbar("已导出 ${if (gpx) "GPX" else "CSV"}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                snack.showSnackbar("导出失败，请选择可写入的位置重试")
            } finally {
                exporting = false
                exportId = null
            }
        }
    }
    val csvLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
            export(it, false)
        }
    val gpxLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gpx+xml")
        ) {
            export(it, true)
        }
    AltimeterTheme(preferences.theme) {
        val lightBars = MaterialTheme.colorScheme.background.luminance() > .5f
        SideEffect {
            (context as? android.app.Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                Column {
                    if (tab == 0) RecordingBar(recording, state.reading != null, now, onRecord)
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val icons =
                            listOf(
                                Icons.Outlined.Terrain,
                                Icons.Outlined.Route,
                                Icons.Outlined.Tune,
                            )
                        listOf("仪表", "行程", "设置").forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(icons[index], null) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                    when (tab) {
                        0 ->
                            DashboardScreen(
                                state,
                                preferences,
                                recording,
                                trace,
                                now,
                                viewModel::setMode,
                                onPermission,
                                onLocationSettings,
                                { calibration = true },
                                viewModel::zero,
                            )
                        1 -> HistoryScreen(trips, preferences.unit) { selectedId = it.id }
                        else ->
                            SettingsScreen(
                                preferences,
                                state,
                                recording.active,
                                viewModel::setUnit,
                                viewModel::setTheme,
                                viewModel::setInterval,
                                viewModel::setScreen,
                                { calibration = true },
                                viewModel::clearCalibration,
                            )
                    }
                }
                if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (calibration)
            CalibrationDialog(
                preferences.unit,
                state.satellite,
                { calibration = false },
                viewModel::calibrate,
            )
        trips
            .firstOrNull { it.id == selectedId }
            ?.let { trip ->
                TripDetailSheet(
                    trip,
                    preferences.unit,
                    viewModel::points,
                    { selectedId = null },
                    onExport = { gpx ->
                        if (!exporting) {
                            exportId = trip.id
                            val filename = "altimeter-${trip.startedAt}"
                            if (gpx) gpxLauncher.launch("$filename.gpx")
                            else csvLauncher.launch("$filename.csv")
                        }
                    },
                    onDelete = { if (!exporting) viewModel.delete(trip.id) },
                    busy = exporting,
                )
            }
    }
}
