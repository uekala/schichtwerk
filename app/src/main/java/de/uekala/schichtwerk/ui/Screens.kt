package de.uekala.schichtwerk.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import de.uekala.schichtwerk.data.fmtDuration
import kotlinx.coroutines.delay
import java.util.Locale

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
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(vm.snap, vm.error)
            val s = vm.snap
            when {
                tab == 2 -> SettingsTab(vm)
                s == null -> Panel { Text(if (vm.error == null) "Lade Daten …" else "Keine Daten", color = TextDim) }
                tab == 0 -> PrintTab(vm, s)
                else -> MaterialTab(s)
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
private fun Header(s: Snapshot?, error: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Schichtwerk", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Creator 5 Pro", color = TextDim, fontSize = 13.sp)
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
                Tile("Restzeit", s.seconds(Ids.REMAINING)?.let(::fmtDuration) ?: "–", Modifier.weight(1f), hot = true)
                Tile("Fertig um", s.etaClock ?: "–", Modifier.weight(1f))
                Tile("Läuft seit", s.seconds(Ids.ELAPSED)?.let(::fmtDuration) ?: "–", Modifier.weight(1f))
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
private fun MaterialTab(s: Snapshot) {
    Panel(title = "Materialstation") {
        val activeSlot = s.num(Ids.ACTIVE_SLOT)?.toInt()
        val warn = s.num(Ids.FILAMENT_WARN) ?: 0.0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..4) {
                val rest = s.num(Ids.slotRest(i))
                Tile(
                    label = if (activeSlot == i) "Slot $i · aktiv" else "Slot $i",
                    value = rest?.let { "${it.toInt()} g" } ?: "–",
                    modifier = Modifier.weight(1f),
                    sub = if (rest != null && rest <= warn) "knapp" else null,
                    hot = activeSlot == i,
                )
            }
        }
        KeyValue("Material", s.raw(Ids.FILAMENT_TYPE) ?: "–")
        KeyValue("Aktueller Druck", s.value(Ids.FILAMENT_JOB))
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
        KeyValue("Gesamtkosten", euro(s, Ids.COST_TOTAL))
        KeyValue("davon Filament", euro(s, Ids.COST_FILAMENT))
        KeyValue("Strom", s.value(Ids.ENERGY_TOTAL, 2))
        KeyValue("Filament gesamt", s.value(Ids.LIFETIME_FILAMENT, 0))
        KeyValue("Filamentpreis", s.value(Ids.FILAMENT_PRICE, 2))
    }
}

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
                placeholder = { Text("https://…") },
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
