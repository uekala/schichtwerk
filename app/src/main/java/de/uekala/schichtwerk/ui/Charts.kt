package de.uekala.schichtwerk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private val SeriesColors = listOf(Accent, Color(0xFF5AA9FF), Color(0xFF3DDC97))

/**
 * Einfaches Liniendiagramm für Temperaturen. Werte sind Stufen (Zustand gilt bis zur nächsten
 * Änderung), deshalb wird jede Reihe als Treppe gezeichnet und bis „jetzt“ verlängert.
 */
@Composable
fun TempChart(series: Map<String, List<Pair<Long, Float>>>, from: Long) {
    val now = System.currentTimeMillis()
    val all = series.values.flatten().map { it.second }
    if (all.isEmpty()) {
        Text("Keine Verlaufsdaten für diesen Zeitraum", color = TextDim, fontSize = 13.sp)
        return
    }
    val yMax = (ceil((all.max() + 10) / 50.0) * 50).toFloat().coerceAtLeast(50f)
    val yMin = 0f
    val span = (now - from).coerceAtLeast(60_000L).toFloat()
    val fmt = SimpleDateFormat("HH:mm", Locale.GERMANY)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("${yMax.toInt()}°", color = TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val w = size.width
            val h = size.height
            fun x(t: Long) = ((t - from).coerceAtLeast(0) / span) * w
            fun y(v: Float) = h - ((v - yMin) / (yMax - yMin)).coerceIn(0f, 1f) * h

            val grid = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            for (k in 0..4) {
                val gy = h * k / 4f
                drawLine(Track, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f, pathEffect = grid)
            }
            series.values.forEachIndexed { idx, pts ->
                if (pts.isEmpty()) return@forEachIndexed
                val sorted = pts.sortedBy { it.first }
                // Erster Punkt liegt oft vor dem Zeitraum: am linken Rand beginnen.
                val path = Path()
                var lastY = y(sorted.first().second)
                path.moveTo(x(sorted.first().first), lastY)
                for (p in sorted.drop(1)) {
                    val px = x(p.first)
                    path.lineTo(px, lastY)
                    lastY = y(p.second)
                    path.lineTo(px, lastY)
                }
                path.lineTo(w, lastY)
                drawPath(
                    path, SeriesColors[idx % SeriesColors.size],
                    style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmt.format(Date(from)), color = TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("jetzt", color = TextDim, fontSize = 11.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            series.entries.forEachIndexed { idx, (name, pts) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(SeriesColors[idx % SeriesColors.size]))
                    Spacer(Modifier.width(6.dp))
                    val last = pts.maxByOrNull { it.first }?.second
                    Text(
                        name + (last?.let { " ${it.toInt()}°" } ?: ""),
                        color = TextMain, fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
