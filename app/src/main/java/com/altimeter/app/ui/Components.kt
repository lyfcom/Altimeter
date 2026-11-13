package com.altimeter.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altimeter.core.*

@Composable
fun SectionLabel(title: String, trailing: String = "") {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty())
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, suffix: String = "") {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val available =
                (maxWidth.value - if (suffix.isEmpty()) 0f else 32f * fontScale).coerceAtLeast(24f)
            val size =
                (available / (value.length.coerceAtLeast(1) * .62f) / fontScale).coerceIn(12f, 25f)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = size.sp,
                    maxLines = 1,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
                if (suffix.isNotEmpty())
                    Text(
                        " $suffix",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
            }
        }
    }
}

@Composable
fun Notice(text: String, action: String? = null, onClick: () -> Unit = {}) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (action != null)
                TextButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(top = 6.dp, end = 12.dp),
                ) {
                    Text(action)
                }
        }
    }
}

@Composable
fun AltitudeChart(
    readings: List<Reading>,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
    breakBefore: Set<Long>? = null,
) {
    val points = readings.filter { it.metres.isFinite() }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text(
                "等待高度数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val min = points.minOf { it.metres }
    val max = points.maxOf { it.metres }
    val span = (max - min).coerceAtLeast(4.0)
    val low = (min + max) / 2 - span * .65
    val high = (min + max) / 2 + span * .65
    Column(
        modifier.semantics {
            contentDescription =
                "高度曲线，最低 ${height(min, unit)} ${unit.symbol}，最高 ${height(max, unit)} ${unit.symbol}"
        }
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${height(max, unit)} ${unit.symbol}", style = MaterialTheme.typography.bodySmall)
            Text(
                "${duration(points.last().elapsedMillis - points.first().elapsedMillis)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(130.dp).padding(vertical = 12.dp)) {
            repeat(3) { i ->
                val y = size.height * i / 2
                drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val elapsed =
                (points.last().elapsedMillis - points.first().elapsedMillis).coerceAtLeast(1)
            fun x(p: Reading) =
                ((p.elapsedMillis - points.first().elapsedMillis).toFloat() / elapsed * size.width)
            fun y(p: Reading) = ((high - p.metres) / (high - low) * size.height).toFloat()
            var path = Path()
            var segment = mutableListOf<Reading>()
            fun drawSegment() {
                if (segment.isEmpty()) return
                val area =
                    Path().apply {
                        moveTo(x(segment.first()), size.height)
                        segment.forEach { lineTo(x(it), y(it)) }
                        lineTo(x(segment.last()), size.height)
                        close()
                    }
                drawPath(
                    area,
                    Brush.verticalGradient(listOf(line.copy(alpha = .18f), Color.Transparent)),
                )
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx()))
            }
            points.forEachIndexed { index, point ->
                val previous = points.getOrNull(index - 1)
                val gap =
                    previous == null ||
                        (breakBefore?.contains(point.elapsedMillis)
                            ?: (point.elapsedMillis - previous.elapsedMillis > 30_000)) ||
                        point.reference != previous.reference ||
                        point.source != previous.source ||
                        point.referenceId != previous.referenceId
                if (gap) {
                    drawSegment()
                    path = Path()
                    segment = mutableListOf()
                    path.moveTo(x(point), y(point))
                } else path.lineTo(x(point), y(point))
                segment.add(point)
            }
            drawSegment()
            drawCircle(line, 4.dp.toPx(), Offset(x(points.last()), y(points.last())))
        }
        Text(
            "${height(min, unit)} ${unit.symbol}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
