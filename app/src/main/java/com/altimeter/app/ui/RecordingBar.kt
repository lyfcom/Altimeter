package com.altimeter.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.altimeter.app.recording.RecordingController

/** The primary action stays visible even when the instrument page is scrolled. */
@Composable
fun RecordingBar(
    state: RecordingController.State,
    hasReading: Boolean,
    now: Long,
    onRecord: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Button(
            onClick = onRecord,
            enabled = !state.starting && (state.active || hasReading),
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (state.active) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primary,
                    contentColor =
                        if (state.active) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onPrimary,
                ),
        ) {
            Icon(if (state.active) Icons.Outlined.StopCircle else Icons.Outlined.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    state.starting -> "正在开始…"
                    state.active ->
                        "结束并保存  ·  ${duration(now - (state.trip?.startedElapsed ?: now))}"
                    else -> "开始记录"
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
