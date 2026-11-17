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

    /** Kurze Rückmeldung für die Snackbar. */
    var message by mutableStateOf<String?>(null)

    val baseUrl: String get() = client?.baseUrl.orEmpty()

    fun connect(url: String, token: String) {
        if (url.isBlank() || token.isBlank()) {
            error = "Adresse und Token ausfüllen"
            return
        }
        connecting = true
        error = null
        viewModelScope.launch {
            try {
                val c = HaClient(url, token.trim())
                val s = withContext(Dispatchers.IO) { c.snapshot() }
                Prefs.save(ctx, url, token)
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
