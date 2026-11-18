package de.uekala.schichtwerk.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.uekala.schichtwerk.BuildConfig
import de.uekala.schichtwerk.MainViewModel
import de.uekala.schichtwerk.data.Ids
import de.uekala.schichtwerk.data.Snapshot
import de.uekala.schichtwerk.data.fmtDurationShort
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: MainViewModel) {
    if (!vm.configured) {
        SetupScreen(vm)
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val owner = LocalLifecycleOwner.current

    // Status alle 5 Sekunden, solange die App sichtbar ist.
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refresh()
                delay(5_000)
            }
        }
    }
    // Kamerabild nur auf dem Druck-Tab nachladen.
    LaunchedEffect(owner, tab) {
        if (tab == 0) {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    vm.refreshCamera()
                    delay(3_000)
                }
            }
        }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Surface) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Druck") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Build, contentDescription = null) }, label = { Text("Material") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = { Text("Einstellungen") },
                )
            }
        },
    ) { pad ->
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = vm::pullRefresh,
            modifier = Modifier.fillMaxSize().padding(pad),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Header(vm.snap, vm.error, vm.lastUpdate)
                val s = vm.snap
                when {
                    tab == 2 -> SettingsTab(vm)
                    s == null -> Panel { Text(if (vm.error == null) "Lade Daten …" else "Keine Daten", color = TextDim) }
                    tab == 0 -> PrintTab(vm, s)
                    else -> MaterialTab(vm, s)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Bausteine

@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) {
            Text(
                title.uppercase(Locale.GERMANY),
                color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            )
        }
        content()
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, hot: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = if (hot) Accent else TextMain,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (sub != null) Text(sub, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Header(s: Snapshot?, error: String?, lastUpdate: Long?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Schichtwerk", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            val stand = lastUpdate?.let {
                " · Stand " + java.text.SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(java.util.Date(it))
            }.orEmpty()
            Text("Creator 5 Pro$stand", color = TextDim, fontSize = 13.sp)
        }
        val (label, color) = when {
            error != null -> "Keine Verbindung" to Bad
            s == null -> "Verbinde …" to TextDim
            !s.online -> "Offline" to Bad
            s.error -> "Fehler" to Bad
            s.paused -> "Pausiert" to Warn
            s.printing -> "Druckt" to Accent
            else -> s.statusLabel to Good
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceHigh)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(label, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
    if (error != null) {
        Text(error, color = Bad, fontSize = 13.sp)
    }
}

private fun temp(s: Snapshot, id: String, targetId: String): Pair<String, String?> {
    val now = s.num(id)?.let { "${it.toInt()}°" } ?: "–"
    val target = s.num(targetId)?.takeIf { it > 0 }?.let { "Ziel ${it.toInt()}°" }
    return now to target
}

// ---------------------------------------------------------------- Druck

@Composable
private fun PrintTab(vm: MainViewModel, s: Snapshot) {
    var confirmCancel by remember { mutableStateOf(false) }

    // Auftrag
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (s.active) s.progress / 100f else 0f },
                    modifier = Modifier.fillMaxSize(),
                    color = if (s.paused) Warn else Accent,
                    trackColor = Track,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round,
                )
                val thumb = vm.thumb
                if (thumb != null && s.active) {
                    Image(
                        thumb.asImageBitmap(), contentDescription = null,
                        modifier = Modifier.size(84.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        if (s.active) "${s.progress} %" else "–",
                        color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (s.active) s.title else s.statusLabel,
                    color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                if (s.active) {
                    Text("${s.progress} %", color = Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    val layer = s.raw(Ids.LAYER)
                    val layers = s.raw(Ids.LAYERS)
                    if (layer != null && layers != null) {
                        Text("Schicht $layer von $layers", color = TextDim, fontSize = 13.sp)
                    }
                } else {
                    s.raw(Ids.LAST_RESULT)?.let { Text("Letzter Druck: $it", color = TextDim, fontSize = 13.sp) }
                }
            }
        }
        if (s.active) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Restzeit", s.seconds(Ids.REMAINING)?.let(::fmtDurationShort) ?: "–", Modifier.weight(1f), hot = true)
                Tile("Fertig um", s.etaClock ?: "–", Modifier.weight(1f))
                Tile("Läuft seit", s.seconds(Ids.ELAPSED)?.let(::fmtDurationShort) ?: "–", Modifier.weight(1f))
            }
        }
        if (s.error) {
            Text(
                "Fehler${s.raw(Ids.ERROR_CODE)?.let { " · Code $it" }.orEmpty()}",
                color = Bad, fontWeight = FontWeight.SemiBold,
            )
        }
        if (s.on(Ids.DOOR)) Text("Tür ist offen", color = Warn, fontSize = 13.sp)
    }

    // Steuerung
    Panel(title = "Steuerung") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.paused) {
                Button(onClick = vm::resume, modifier = Modifier.weight(1f)) { Text("Fortsetzen") }
            } else {
                Button(onClick = vm::pause, enabled = s.printing, modifier = Modifier.weight(1f)) { Text("Pause") }
            }
            OutlinedButton(
                onClick = { confirmCancel = true },
                enabled = s.active,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Bad),
            ) { Text("Abbrechen") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LED", color = TextMain, modifier = Modifier.weight(1f))
            Switch(checked = s.on(Ids.LED), onCheckedChange = vm::setLed)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Geschwindigkeit", color = TextMain)
            val speed = s.num(Ids.SPEED)?.toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Der Drucker meldet seine Stufen nicht immer mit dem Wert zurück, der gesetzt wurde
                // (Maximalstufe z. B. als 140 %), deshalb wird die nächstliegende Stufe markiert.
                val current = when {
                    speed == null -> null
                    speed < 75 -> 50
                    speed < 125 -> 100
                    else -> 150
                }
                listOf(50 to "Leise", 100 to "Normal", 150 to "Max").forEach { (pct, label) ->
                    val selected = current == pct
                    OutlinedButton(
                        onClick = { if (!selected) vm.setSpeed(pct) },
                        enabled = s.active,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Color(0xFF3A2410) else Color.Transparent,
                            contentColor = if (selected) Accent else TextDim,
                        ),
                    ) { Text(label, maxLines = 1) }
                }
            }
            if (speed != null) {
                Text("Drucker meldet $speed %", color = TextDim, fontSize = 12.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Luftfilter", color = TextMain)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mode = s.raw(Ids.FILTER_MODE)
                listOf("Off" to "Aus", "Internal" to "Intern", "External" to "Extern").forEach { (opt, label) ->
                    val selected = mode == opt
                    OutlinedButton(
                        onClick = { if (!selected) vm.setFilterMode(opt) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) Color(0xFF3A2410) else Color.Transparent,
                            contentColor = if (selected) Accent else TextDim,
                        ),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
        if (!s.active && s.status.lowercase() !in setOf("ready", "idle")) {
            TextButton(onClick = vm::clearStatus) { Text("Status zurücksetzen") }
        }
    }

    // Kamera
    Panel(title = "Kamera") {
        val cam = vm.camera
        if (cam != null) {
            Image(
                cam.asImageBitmap(), contentDescription = "Kamerabild",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(cam.width.toFloat() / cam.height.coerceAtLeast(1))
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(SurfaceHigh),
                contentAlignment = Alignment.Center,
            ) { Text("Kein Kamerabild", color = TextDim) }
        }
        s.raw(Ids.AI_LAST_CHECK)?.let {
            Text("KI-Kontrolle: $it", color = TextDim, fontSize = 12.sp)
        }
    }

    // Temperaturen
    Panel(title = "Temperaturen") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val bed = temp(s, Ids.BED, Ids.BED_TARGET)
            val chamber = temp(s, Ids.CHAMBER, Ids.CHAMBER_TARGET)
            Tile("Bett", bed.first, Modifier.weight(1f), sub = bed.second, hot = bed.second != null)
            Tile("Kammer", chamber.first, Modifier.weight(1f), sub = chamber.second, hot = chamber.second != null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..4) {
                val t = temp(s, Ids.tool(i), Ids.toolTarget(i))
                Tile("Düse $i", t.first, Modifier.weight(1f), sub = t.second, hot = t.second != null)
            }
        }
    }

    // Details
    Panel(title = "Details") {
        KeyValue("Geschwindigkeit", s.value(Ids.SPEED))
        KeyValue("Bauteillüfter", s.value(Ids.FAN_PART))
        KeyValue("Kammerlüfter", s.value(Ids.FAN_CHAMBER))
        KeyValue("Filament", s.raw(Ids.FILAMENT_TYPE) ?: "–")
        KeyValue("TVOC", s.value(Ids.TVOC))
        KeyValue("Freier Speicher", s.value(Ids.DISK_FREE, 2))
        KeyValue("Firmware", s.raw(Ids.FIRMWARE) ?: "–")
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Druck abbrechen?") },
            text = { Text("${s.title} wird bei ${s.progress} % abgebrochen. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.cancel() }) { Text("Abbrechen", color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Weiterdrucken") }
            },
        )
    }
}

// ---------------------------------------------------------------- Material & Wartung

@Composable
private fun MaterialTab(vm: MainViewModel, s: Snapshot) {
    var edit by remember { mutableStateOf<NumberEdit?>(null) }
    Panel(title = "Materialstation") {
        val activeSlot = s.num(Ids.ACTIVE_SLOT)?.toInt()
        val warn = s.num(Ids.FILAMENT_WARN) ?: 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..4) {
                val rest = s.num(Ids.slotRest(i))
                Tile(
                    label = if (activeSlot == i) "Slot $i · aktiv" else "Slot $i",
                    value = rest?.let { "${it.toInt()} g" } ?: "–",
                    modifier = Modifier.weight(1f).clickable {
                        edit = NumberEdit(
                            "Restmenge · Slot $i", Ids.slotRest(i), "g", 0,
                            "Gewicht des Filaments auf der Spule. Home Assistant zieht davon jeden Verbrauch ab.",
                            preset = "Neue Spule (1000 g)" to 1000.0,
                        )
                    },
                    sub = if (rest != null && rest <= warn) "knapp" else null,
                    hot = activeSlot == i,
                )
            }
        }
        Text("Slot antippen, um die Restmenge einzutragen, z. B. nach einem Spulenwechsel.", color = TextDim, fontSize = 12.sp)
        KeyValue("Material", s.raw(Ids.FILAMENT_TYPE) ?: "–")
        val jobG = s.num(Ids.FILAMENT_JOB)
        val jobEur = s.num(Ids.FILAMENT_COST_JOB)
        KeyValue(
            "Aktueller Druck",
            if (jobG == null) "–" else String.format(Locale.GERMANY, "%.0f g", jobG) +
                (jobEur?.let { String.format(Locale.GERMANY, " · %.2f €", it) } ?: ""),
        )
        KeyValue("Warnschwelle", s.value(Ids.FILAMENT_WARN))
    }

    Panel(title = "Wartung") {
        val hours = s.lifetimeHours
        Ids.MAINTENANCE.forEach { (name, lastId, intervalId) ->
            val last = s.num(lastId)
            val interval = s.num(intervalId)
            if (hours != null && last != null && interval != null && interval > 0) {
                val used = (hours - last).coerceAtLeast(0.0)
                val left = interval - used
                val frac = (used / interval).toFloat().coerceIn(0f, 1f)
                val color = when {
                    left <= 0 -> Bad
                    frac > 0.85f -> Warn
                    else -> Good
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    KeyValue(name, if (left > 0) "noch ${left.toInt()} h" else "fällig seit ${(-left).toInt()} h")
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = color,
                        trackColor = Track,
                    )
                }
            } else {
                KeyValue(name, "–")
            }
        }
        KeyValue("Betriebsdauer", hours?.let { "${it.toInt()} h" } ?: "–")
    }

    Panel(title = "Kosten & Verbrauch") {
        KeyValue("Drucke", s.num(Ids.PRINT_COUNT)?.toInt()?.toString() ?: "–")
        val strom = s.num(Ids.COST_TOTAL)
        val filament = s.num(Ids.COST_FILAMENT)
        KeyValue(
            "Kosten gesamt",
            if (strom != null && filament != null) String.format(Locale.GERMANY, "%.2f €", strom + filament) else "–",
        )
        KeyValue("davon Strom", euro(s, Ids.COST_TOTAL))
        KeyValue("davon Filament", euro(s, Ids.COST_FILAMENT))
        KeyValue("Strom", s.value(Ids.ENERGY_TOTAL, 2))
        KeyValue("Filament gesamt", s.value(Ids.LIFETIME_FILAMENT, 0))
    }

    Panel(title = "Filamentpreise") {
        Text(
            "Home Assistant rechnet jeden Druck mit dem Preis des Slots, aus dem das Filament kommt.",
            color = TextDim, fontSize = 12.sp,
        )
        val rows = (1..4).map { "Slot $it" to Ids.slotPrice(it) } + ("Ohne Slot" to Ids.FILAMENT_PRICE)
        rows.forEach { (label, id) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = TextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    edit = NumberEdit("Filamentpreis · $label", id, "€/kg", 2, "Preis in € pro Kilogramm. Gilt für künftige Drucke.")
                }) {
                    Text((s.num(id)?.let { String.format(Locale.GERMANY, "%.2f €/kg", it) } ?: "–") + "  ·  ändern")
                }
            }
        }
    }

    edit?.let { e ->
        var input by remember(e.id) {
            mutableStateOf(s.num(e.id)?.let { String.format(Locale.GERMANY, "%.${e.decimals}f", it) }.orEmpty())
        }
        val parsed = input.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..10000.0 }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(e.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(e.hint)
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        singleLine = true,
                        isError = parsed == null,
                        suffix = { Text(e.unit) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    e.preset?.let { (label, value) ->
                        OutlinedButton(onClick = { edit = null; vm.setNumber(e.id, value) }) { Text(label) }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null,
                    onClick = { edit = null; parsed?.let { vm.setNumber(e.id, it) } },
                ) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { edit = null }) { Text("Abbrechen") }
            },
        )
    }
}

private data class NumberEdit(
    val title: String,
    val id: String,
    val unit: String,
    val decimals: Int,
    val hint: String,
    val preset: Pair<String, Double>? = null,
)

private fun euro(s: Snapshot, id: String): String =
    s.num(id)?.let { String.format(Locale.GERMANY, "%.2f €", it) } ?: "–"

// ---------------------------------------------------------------- Einstellungen

private fun ignoresBatteryOptimization(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
private fun SettingsTab(vm: MainViewModel) {
    val ctx = LocalContext.current
    var confirmDisconnect by remember { mutableStateOf(false) }

    Panel(title = "Benachrichtigungen") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live-Anzeige beim Druck", color = TextMain)
                Text("Dauerbenachrichtigung mit Fortschritt, nur solange gedruckt wird", color = TextDim, fontSize = 12.sp)
            }
            Switch(checked = vm.live, onCheckedChange = vm::updateLive)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Alarme", color = TextMain)
                Text("Fehler, Pause, Druck fertig oder gestoppt", color = TextDim, fontSize = 12.sp)
            }
            Switch(checked = vm.alerts, onCheckedChange = vm::updateAlerts)
        }
    }

    Panel(title = "Hintergrund") {
        val exempt = ignoresBatteryOptimization(ctx)
        Text(
            if (exempt) "Akku-Optimierung ist für Schichtwerk ausgeschaltet – die Live-Anzeige startet auch bei geschlossener App."
            else "Mit eingeschalteter Akku-Optimierung startet die Live-Anzeige erst, wenn du die App öffnest, und Alarme können sich verzögern.",
            color = TextDim, fontSize = 13.sp,
        )
        if (!exempt) {
            Button(onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            }) { Text("Akku-Optimierung ausschalten") }
        }
    }

    var editDirect by remember { mutableStateOf(false) }
    Panel(title = "Drucker direkt") {
        Text(
            if (vm.directConfigured) "Eingerichtet. Die Geschwindigkeit wird direkt am Drucker gesetzt – das geht nur im Heimnetz."
            else "Nötig, um die Druckgeschwindigkeit zu ändern: Home Assistant kann das nicht, die App schickt den Befehl deshalb direkt an den Drucker. Das geht nur im Heimnetz.",
            color = TextDim, fontSize = 13.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editDirect = true }) { Text(if (vm.directConfigured) "Ändern" else "Einrichten") }
            if (vm.directConfigured) {
                OutlinedButton(onClick = vm::removeDirect) { Text("Entfernen") }
            }
        }
    }
    if (editDirect) {
        var ip by remember { mutableStateOf(vm.snap?.raw(Ids.IP).orEmpty()) }
        var serial by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }
        val ok = ip.isNotBlank() && serial.isNotBlank() && code.isNotBlank()
        AlertDialog(
            onDismissRequest = { editDirect = false },
            title = { Text("Drucker direkt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seriennummer und Prüfcode stehen am Drucker im Menü für den LAN-Modus.")
                    OutlinedTextField(ip, { ip = it }, label = { Text("IP-Adresse") }, singleLine = true)
                    OutlinedTextField(serial, { serial = it }, label = { Text("Seriennummer") }, singleLine = true)
                    OutlinedTextField(code, { code = it }, label = { Text("Prüfcode") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = { editDirect = false; vm.saveDirect(ip, serial, code) }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editDirect = false }) { Text("Abbrechen") }
            },
        )
    }

    Panel(title = "Verbindung") {
        KeyValue("Home Assistant", vm.baseUrl.removePrefix("https://").removePrefix("http://"))
        KeyValue("App-Version", BuildConfig.VERSION_NAME)
        OutlinedButton(
            onClick = { confirmDisconnect = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Bad),
        ) { Text("Verbindung trennen") }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Verbindung trennen?") },
            text = { Text("Adresse und Token werden von diesem Gerät gelöscht.") },
            confirmButton = {
                TextButton(onClick = { confirmDisconnect = false; vm.disconnect() }) { Text("Trennen", color = Bad) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Zurück") }
            },
        )
    }
}

// ---------------------------------------------------------------- Einrichtung

@Composable
private fun SetupScreen(vm: MainViewModel) {
    var url by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }

    Scaffold(containerColor = Bg) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("Schichtwerk", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Verbinde die App mit deinem Home Assistant. Sie liest und steuert den Creator 5 Pro darüber.",
                color = TextDim,
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Adresse von Home Assistant") },
                placeholder = { Text("https://… oder http://192.168.x.x:8123") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token, onValueChange = { token = it },
                label = { Text("Langlebiges Zugriffstoken") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Adresse: die externe https-Adresse oder die IP im Heimnetz – homeassistant.local funktioniert auf Android oft nicht.\n\n" +
                    "Token erstellen: in Home Assistant auf dein Profil → Sicherheit → Langlebige Zugriffstoken. " +
                    "Es wird verschlüsselt nur auf diesem Gerät gespeichert.",
                color = TextDim, fontSize = 12.sp,
            )
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { vm.connect(url, token) },
                enabled = !vm.connecting,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.connecting) "Verbinde …" else "Verbinden") }
        }
    }
}
