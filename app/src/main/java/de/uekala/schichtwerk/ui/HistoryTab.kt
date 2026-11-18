package de.uekala.schichtwerk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.uekala.schichtwerk.MainViewModel
import de.uekala.schichtwerk.data.PrintHistory
import de.uekala.schichtwerk.data.PrintRecord
import de.uekala.schichtwerk.data.fmtDurationShort
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryTab(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.loadHistory() }
    val list = vm.history
    vm.historyError?.let { Panel { Text("Verlauf nicht geladen: $it", color = Bad) } }
    if (list == null) {
        Panel { Text("Lade Druckverlauf …", color = TextDim) }
        return
    }
    if (list.isEmpty()) {
        Panel { Text("Noch keine beendeten Drucke im Kalender „3D-Drucke“.", color = TextDim) }
        return
    }

    val months = PrintHistory.months(list)
    months.take(2).forEach { m ->
        val name = m.month.month.getDisplayName(TextStyle.FULL, Locale.GERMANY) + " " + m.month.year
        Panel(title = name) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Drucke", "${m.prints}", Modifier.weight(1f), sub = "${m.successful} fertig")
                Tile("Filament", String.format(Locale.GERMANY, "%.0f g", m.grams), Modifier.weight(1f))
                Tile("Kosten", String.format(Locale.GERMANY, "%.2f €", m.cost), Modifier.weight(1f), hot = true)
            }
            KeyValue("Druckzeit", String.format(Locale.GERMANY, "%.1f h", m.hours))
        }
    }

    var shown by remember { mutableIntStateOf(20) }
    Panel(title = "Letzte Drucke") {
        val fmt = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMANY)
        list.take(shown).forEach { r -> RecordRow(r, fmt) }
        if (list.size > shown) {
            TextButton(onClick = { shown += 20 }) { Text("Mehr anzeigen (${list.size - shown} weitere)") }
        }
    }
}

@Composable
private fun RecordRow(r: PrintRecord, fmt: DateTimeFormatter) {
    val color = when (r.result) {
        PrintRecord.Result.OK -> Good
        PrintRecord.Result.CANCELLED -> Warn
        PrintRecord.Result.ERROR -> Bad
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val result = when (r.result) {
                PrintRecord.Result.OK -> "fertig"
                PrintRecord.Result.CANCELLED -> "abgebrochen"
                PrintRecord.Result.ERROR -> "Fehler"
            }
            Text(
                r.start.atZoneSameInstant(ZoneId.systemDefault()).format(fmt) + " · " +
                    fmtDurationShort(r.durationSeconds) + " · " + result,
                color = TextDim, fontSize = 12.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(r.costTotal?.let { String.format(Locale.GERMANY, "%.2f €", it) } ?: "–", color = TextMain, fontSize = 14.sp)
            Text(r.grams?.let { String.format(Locale.GERMANY, "%.0f g", it) } ?: "", color = TextDim, fontSize = 12.sp)
        }
    }
}
