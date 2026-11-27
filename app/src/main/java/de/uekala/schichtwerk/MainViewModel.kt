package de.uekala.schichtwerk

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.uekala.schichtwerk.bg.Notifier
import de.uekala.schichtwerk.bg.PrintMonitorService
import de.uekala.schichtwerk.bg.PrinterWidget
import de.uekala.schichtwerk.data.HaClient
import de.uekala.schichtwerk.data.Ids
import de.uekala.schichtwerk.data.Prefs
import de.uekala.schichtwerk.data.PrinterAccess
import de.uekala.schichtwerk.data.PrintHistory
import de.uekala.schichtwerk.data.PrintRecord
import de.uekala.schichtwerk.data.Release
import de.uekala.schichtwerk.data.Updater
import de.uekala.schichtwerk.data.PrinterDirect
import kotlinx.coroutines.CancellationException
import de.uekala.schichtwerk.data.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private var client: HaClient? = Prefs.client(app)
    private var thumbFor: String? = null

    var configured by mutableStateOf(client != null)
        private set
    var snap by mutableStateOf<Snapshot?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var camera by mutableStateOf<Bitmap?>(null)
        private set
    var thumb by mutableStateOf<Bitmap?>(null)
        private set
    var connecting by mutableStateOf(false)
        private set
    var live by mutableStateOf(Prefs.live(app))
        private set
    var alerts by mutableStateOf(Prefs.alerts(app))
        private set

    var refreshing by mutableStateOf(false)
        private set
    var lastUpdate by mutableStateOf<Long?>(null)
        private set
    var directConfigured by mutableStateOf(Prefs.printerDirect(app) != null)
        private set

    var history by mutableStateOf<List<PrintRecord>?>(null)
        private set
    var historyError by mutableStateOf<String?>(null)
        private set
    var temps by mutableStateOf<Map<String, List<Pair<Long, Float>>>?>(null)
        private set
    var tempsFrom by mutableStateOf(0L)
        private set
    var release by mutableStateOf<Release?>(null)
        private set
    var updateBusy by mutableStateOf(false)
        private set

    /** Kurze Rückmeldung für die Snackbar. */
    var message by mutableStateOf<String?>(null)

    val baseUrl: String get() = client?.baseUrl.orEmpty()
    val usingRemote: Boolean get() = client?.usingRemote == true
    val homeUrl: String get() = Prefs.baseUrl(ctx).orEmpty()
    var remoteUrl by mutableStateOf(Prefs.remoteUrl(app))
        private set

    /** Zweite Adresse für unterwegs speichern; die App wechselt automatisch, wenn eine nicht erreichbar ist. */
    fun saveRemoteUrl(url: String) {
        Prefs.setRemoteUrl(ctx, url)
        remoteUrl = Prefs.remoteUrl(ctx)
        client = Prefs.client(ctx)
        message = if (url.isBlank()) "Adresse für unterwegs entfernt" else "Adresse für unterwegs gespeichert"
        pullRefresh()
    }

    init {
        Prefs.forgetGithubToken(app)
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck(app) > 6 * 3600_000L) checkUpdate(manual = false)
    }

    // ---------------------------------------------------------- Verlauf

    fun loadHistory() {
        val c = client ?: return
        viewModelScope.launch {
            try {
                val end = java.time.OffsetDateTime.now().plusDays(1)
                val arr = withContext(Dispatchers.IO) { c.calendarEvents(PrintHistory.CALENDAR, end.minusDays(400), end) }
                history = PrintHistory.parse(arr)
                historyError = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                historyError = describe(e)
            }
        }
    }

    /** Temperaturen seit Druckstart (höchstens 12 h), sonst der letzten 3 Stunden. */
    fun loadTemps() {
        val c = client ?: return
        val s = snap ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val startMs = if (s.active) {
                s.raw(Ids.PRINT_START)?.let {
                    runCatching {
                        java.time.LocalDateTime.parse(it.replace(' ', 'T'))
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.getOrNull()
                }
            } else null
            val from = (startMs ?: (now - 3 * 3600_000L)).coerceAtLeast(now - 12 * 3600_000L)
            val tool = (1..4).firstOrNull { (s.num(Ids.toolTarget(it)) ?: 0.0) > 0 } ?: 1
            val ids = listOf(Ids.tool(tool), Ids.BED, Ids.CHAMBER)
            try {
                val start = java.time.Instant.ofEpochMilli(from).atOffset(java.time.ZoneOffset.UTC)
                val data = withContext(Dispatchers.IO) { c.history(ids, start) }
                temps = mapOf(
                    "Düse $tool" to data[ids[0]].orEmpty(),
                    "Bett" to data[ids[1]].orEmpty(),
                    "Kammer" to data[ids[2]].orEmpty(),
                )
                tempsFrom = from
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Verlauf nicht geladen: ${describe(e)}"
            }
        }
    }

    // ---------------------------------------------------------- Schalter, Wartung

    fun setSwitch(entityId: String, on: Boolean) = act(if (on) "Eingeschaltet" else "Ausgeschaltet") {
        it.callService("input_boolean", if (on) "turn_on" else "turn_off", entityId)
    }

    fun setAutomation(entityId: String, on: Boolean) = act(if (on) "Automation eingeschaltet" else "Automation ausgeschaltet") {
        it.callService("automation", if (on) "turn_on" else "turn_off", entityId)
    }

    fun maintenanceDone(name: String, lastId: String, hours: Double) = act("$name als erledigt vermerkt") {
        it.callService("input_number", "set_value", lastId, JSONObject().put("value", Math.round(hours * 100) / 100.0))
    }

    // ---------------------------------------------------------- Updates

    val updateSupported: Boolean get() = Updater.supported

    fun checkUpdate(manual: Boolean) {
        if (!Updater.supported) return
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Updater.latest() }
                Prefs.setLastUpdateCheck(ctx, System.currentTimeMillis())
                release = r?.takeIf { Updater.isNewer(it) }
                if (manual) message = if (release != null) "Version ${r?.version} verfügbar" else "Schichtwerk ist aktuell"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) message = "Update-Prüfung fehlgeschlagen: ${describe(e)}"
            }
        }
    }

    fun installUpdate() {
        val r = release ?: return
        runCatching { Updater.open(ctx, r) }.onFailure { message = "Kein Browser gefunden" }
    }


    fun connect(url: String, token: String, remote: String = "") {
        if (url.isBlank() || token.isBlank()) {
            error = "Adresse und Token ausfüllen"
            return
        }
        connecting = true
        error = null
        viewModelScope.launch {
            try {
                val c = HaClient(url, token.trim(), remote)
                val s = withContext(Dispatchers.IO) { c.snapshot() }
                Prefs.save(ctx, url, token)
                Prefs.setRemoteUrl(ctx, remote)
                remoteUrl = Prefs.remoteUrl(ctx)
                client = c
                snap = s
                configured = true
            } catch (e: Exception) {
                error = describe(e)
            }
            connecting = false
        }
    }

    fun disconnect() {
        PrintMonitorService.stop(ctx)
        Notifier.cancelLive(ctx)
        Prefs.clear(ctx)
        client = null
        snap = null
        camera = null
        thumb = null
        thumbFor = null
        error = null
        live = true
        alerts = true
        directConfigured = false
        lastUpdate = null
        configured = false
    }

    suspend fun refresh() {
        val c = client ?: return
        try {
            val s = withContext(Dispatchers.IO) { c.snapshot() }
            snap = s
            error = null
            lastUpdate = System.currentTimeMillis()
            if (s.active && live) PrintMonitorService.start(ctx) else PrinterWidget.render(ctx, s)
            val file = if (s.active) s.file else null
            if (file != thumbFor) {
                thumbFor = file
                thumb = if (file == null) null else withContext(Dispatchers.IO) {
                    runCatching { c.entityImage(Ids.THUMBNAIL) }.getOrNull()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = describe(e)
        }
    }

    suspend fun refreshCamera() {
        val c = client ?: return
        val img = withContext(Dispatchers.IO) { runCatching { c.cameraImage(Ids.CAMERA) }.getOrNull() }
        if (img != null) camera = img
    }

    private fun act(done: String, block: (HaClient) -> Unit) {
        val c = client ?: return
        viewModelScope.launch {
            message = try {
                withContext(Dispatchers.IO) { block(c) }
                done
            } catch (e: Exception) {
                "Fehlgeschlagen: ${describe(e)}"
            }
            refresh()
        }
    }

    fun pause() = act("Pause angefordert") { it.press(Ids.BTN_PAUSE) }
    fun resume() = act("Fortsetzen angefordert") { it.press(Ids.BTN_RESUME) }
    fun cancel() = act("Abbruch angefordert") { it.press(Ids.BTN_CANCEL) }
    fun clearStatus() = act("Status zurückgesetzt") { it.press(Ids.BTN_CLEAR) }

    fun setLed(on: Boolean) = act(if (on) "LED an" else "LED aus") {
        it.callService("switch", if (on) "turn_on" else "turn_off", Ids.LED)
    }

    fun setFilterMode(option: String) = act("Filter: $option") {
        it.callService("select", "select_option", Ids.FILTER_MODE, JSONObject().put("option", option))
    }

    /** Geschwindigkeit in Prozent; läuft über rest_command.creator_5_pro_speed in Home Assistant. */
    /** Geschwindigkeit in Prozent: direkt am Drucker, sonst über rest_command in Home Assistant. */
    fun setSpeed(percent: Int) = act("Geschwindigkeit: $percent %") {
        val direct = Prefs.printerDirect(ctx)
        if (direct != null) {
            PrinterDirect.setSpeed(direct, percent)
        } else {
            try {
                it.callPlainService("rest_command", "creator_5_pro_speed", JSONObject().put("speed", percent))
            } catch (e: java.io.IOException) {
                if (e.message?.contains("fehlt") == true) {
                    throw java.io.IOException("erst unter Einstellungen → Drucker direkt einrichten")
                }
                throw e
            }
        }
    }

    fun saveDirect(ip: String, serial: String, code: String) {
        Prefs.setPrinterDirect(ctx, PrinterAccess(ip, serial, code))
        directConfigured = true
        message = "Direktzugang gespeichert"
    }

    fun removeDirect() {
        Prefs.setPrinterDirect(ctx, null)
        directConfigured = false
    }

    /** Wischen nach unten: Home Assistant fragt den Drucker sofort neu ab, danach wird alles neu geladen. */
    fun pullRefresh() {
        val c = client ?: return
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { c.callService("homeassistant", "update_entity", Ids.STATUS) }
            }
            refresh()
            refreshCamera()
            refreshing = false
        }
    }

    /** Setzt einen Zahlen-Helfer (Preis je Slot, Restmenge einer Spule). */
    fun setNumber(entityId: String, value: Double) = act("Gespeichert") {
        it.callService("input_number", "set_value", entityId, JSONObject().put("value", value))
    }

    fun updateLive(v: Boolean) {
        live = v
        Prefs.setLive(ctx, v)
        if (!v) {
            PrintMonitorService.stop(ctx)
            Notifier.cancelLive(ctx)
        } else if (snap?.active == true) {
            PrintMonitorService.start(ctx)
        }
    }

    fun updateAlerts(v: Boolean) {
        alerts = v
        Prefs.setAlerts(ctx, v)
    }

    private fun describe(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "Adresse nicht erreichbar"
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "Zeitüberschreitung – Home Assistant antwortet nicht"
        is javax.net.ssl.SSLException -> "Sichere Verbindung fehlgeschlagen"
        is org.json.JSONException -> "Unerwartete Antwort von Home Assistant"
        else -> e.message ?: "Unbekannter Fehler"
    }
}
