package de.uekala.schichtwerk.data

import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Entity-IDs des Creator 5 Pro in Home Assistant. */
object Ids {
    private const val S = "sensor.creator_5_pro_"
    private const val B = "binary_sensor.creator_5_pro_"
    private const val N = "input_number.creator_5_pro_"

    const val STATUS = S + "machine_status"
    const val PROGRESS = S + "print_progress"
    const val FILE = S + "current_file"
    const val LAYER = S + "current_layer"
    const val LAYERS = S + "total_layers"
    const val ELAPSED = S + "elapsed_time"
    const val REMAINING = S + "remaining_time"
    const val ETA = S + "print_completion_time"
    const val BED = S + "bed_temperature"
    const val BED_TARGET = S + "bed_target_temperature"
    const val CHAMBER = S + "chamber_temperature"
    const val CHAMBER_TARGET = S + "chamber_target_temperature"
    const val SPEED = S + "print_speed"
    const val FAN_PART = S + "cooling_fan_speed"
    const val FAN_CHAMBER = S + "chamber_fan_speed"
    const val FILAMENT_TYPE = S + "filament_type"
    const val ERROR_CODE = S + "error_code"
    const val TVOC = S + "tvoc"
    const val FIRMWARE = S + "firmware_version"
    const val LIFETIME_FILAMENT = S + "lifetime_filament_usage"
    const val LIFETIME_RUNTIME = S + "lifetime_runtime"
    const val DISK_FREE = S + "free_disk_space"
    const val IP = S + "ip_address"
    const val ACTIVE_SLOT = "sensor.technikraum_creator_5_pro_active_material_station_slot"

    fun tool(i: Int) = S + "tool_${i}_temperature"
    fun toolTarget(i: Int) = S + "tool_${i}_target_temperature"

    const val PRINTING = B + "printing"
    const val PAUSED = B + "paused"
    const val ERROR = B + "error"
    const val ONLINE = B + "online"
    const val DOOR = B + "door_open"

    const val LED = "switch.technikraum_creator_5_pro_led"
    const val FILTER_MODE = "select.creator_5_pro_filtration_mode"

    const val BTN_PAUSE = "button.creator_5_pro_pause_print"
    const val BTN_RESUME = "button.creator_5_pro_resume_print"
    const val BTN_CANCEL = "button.creator_5_pro_cancel_print"
    const val BTN_CLEAR = "button.creator_5_pro_clear_status"

    const val CAMERA = "camera.creator_5_pro_camera"
    const val THUMBNAIL = "image.creator_5_pro_current_file_thumbnail"

    fun slotRest(i: Int) = N + "filament_slot_${i}_rest"
    fun slotPrice(i: Int) = N + "filamentpreis_slot_$i"
    const val FILAMENT_WARN = N + "filament_warnschwelle"
    const val FILAMENT_JOB = N + "filament_aktueller_druck"
    const val FILAMENT_COST_JOB = N + "filamentkosten_aktueller_druck"
    const val COST_TOTAL = N + "gesamtkosten"
    const val COST_FILAMENT = N + "filamentkosten_gesamt"
    const val ENERGY_TOTAL = N + "gesamtverbrauch"
    const val PRINT_COUNT = N + "anzahl_drucke"
    const val FILAMENT_PRICE = "input_number.filamentpreis"

    val MAINTENANCE = listOf(
        Triple("Düsen", N + "wartung_duesen_zuletzt", N + "wartung_duesen_intervall"),
        Triple("Filter", N + "wartung_filter_zuletzt", N + "wartung_filter_intervall"),
        Triple("Schmieren", N + "wartung_schmieren_zuletzt", N + "wartung_schmieren_intervall"),
    )

    fun slotImage(i: Int) = "image.technikraum_creator_5_pro_material_station_slot_$i"
    const val PRINT_START = "input_datetime.creator_5_pro_druckstartzeit"

    const val AI_ON = "input_boolean.drucker_bot_ki_kontrolle"
    const val AI_PAUSE = "input_boolean.drucker_bot_ki_darf_pausieren"
    const val AI_MUTE = "input_boolean.drucker_bot_ki_stumm_fur_diesen_druck"
    const val AI_THRESHOLD = "input_number.drucker_bot_ki_schwelle"
    const val AI_AUTOMATION = "automation.drucker_bot_ki_druckkontrolle"

    const val LAST_RESULT = "input_text.creator_5_pro_letztes_druckergebnis"
    const val AI_LAST_CHECK = "input_text.drucker_bot_ki_letzte_prufung"

    /** Alles, was in einem Rutsch abgefragt wird. */
    val ALL: List<String> = buildList {
        addAll(
            listOf(
                STATUS, PROGRESS, FILE, LAYER, LAYERS, ELAPSED, REMAINING, ETA, BED, BED_TARGET,
                CHAMBER, CHAMBER_TARGET, SPEED, FAN_PART, FAN_CHAMBER, FILAMENT_TYPE, ERROR_CODE,
                TVOC, FIRMWARE, LIFETIME_FILAMENT, LIFETIME_RUNTIME, DISK_FREE, IP, ACTIVE_SLOT,
                PRINTING, PAUSED, ERROR, ONLINE, DOOR, LED, FILTER_MODE,
                FILAMENT_WARN, FILAMENT_JOB, FILAMENT_COST_JOB, COST_TOTAL, COST_FILAMENT, ENERGY_TOTAL, PRINT_COUNT,
                FILAMENT_PRICE, LAST_RESULT, AI_LAST_CHECK, PRINT_START,
                AI_ON, AI_PAUSE, AI_MUTE, AI_THRESHOLD, AI_AUTOMATION,
            )
        )
        for (i in 1..4) add(slotImage(i))
        for (i in 1..4) {
            add(tool(i)); add(toolTarget(i)); add(slotRest(i)); add(slotPrice(i))
        }
        MAINTENANCE.forEach { add(it.second); add(it.third) }
    }

    /** Attribute, die zusätzlich zum Zustand gebraucht werden. */
    val ATTRS: Map<String, List<String>> = buildMap {
        for (i in 1..4) put(slotImage(i), listOf("material", "material_color", "has_filament"))
        put(AI_THRESHOLD, listOf("min", "max", "step"))
    }
}

data class Ent(val state: String, val unit: String?, val attrs: Map<String, Any?> = emptyMap())

/** Momentaufnahme aller Drucker-Entitäten. */
class Snapshot(private val m: Map<String, Ent>) {

    fun raw(id: String): String? =
        m[id]?.state?.takeUnless { it == "unknown" || it == "unavailable" || it.isBlank() }

    fun num(id: String): Double? = raw(id)?.toDoubleOrNull()
    fun on(id: String): Boolean = m[id]?.state == "on"
    fun unit(id: String): String = m[id]?.unit.orEmpty()
    fun attr(id: String, key: String): Any? = m[id]?.attrs?.get(key)
    fun attrStr(id: String, key: String): String? = (attr(id, key) as? String)?.takeIf { it.isNotBlank() }
    fun attrNum(id: String, key: String): Double? = (attr(id, key) as? Number)?.toDouble()

    /** Wert mit Einheit, z. B. "150 g". */
    fun value(id: String, decimals: Int = 0): String {
        val n = num(id) ?: return raw(id) ?: "–"
        val u = unit(id)
        val txt = String.format(Locale.GERMANY, "%.${decimals}f", n)
        return if (u.isEmpty()) txt else "$txt $u"
    }

    val online: Boolean get() = on(Ids.ONLINE)
    val printing: Boolean get() = on(Ids.PRINTING)
    val paused: Boolean get() = on(Ids.PAUSED)
    val error: Boolean get() = on(Ids.ERROR)
    val active: Boolean get() = printing || paused
    val status: String get() = raw(Ids.STATUS) ?: "unbekannt"
    val progress: Int get() = (num(Ids.PROGRESS) ?: 0.0).roundToInt().coerceIn(0, 100)
    val file: String? get() = raw(Ids.FILE)

    /** Dateiname ohne Endungen wie .gcode.3mf. */
    val title: String
        get() = file?.replace(Regex("(\\.gcode)?\\.(3mf|gcode|gx|g)$", RegexOption.IGNORE_CASE), "")
            ?.replace('_', ' ') ?: "Kein Druckauftrag"

    val statusLabel: String
        get() = when {
            !online -> "Offline"
            error -> "Fehler"
            paused -> "Pausiert"
            else -> when (status.lowercase()) {
                "printing" -> "Druckt"
                "ready", "idle" -> "Bereit"
                "completed", "complete" -> "Fertig"
                "heating" -> "Heizt auf"
                "busy" -> "Beschäftigt"
                "pausing" -> "Pausiert gerade"
                "paused" -> "Pausiert"
                "cancel", "canceled", "cancelled" -> "Abgebrochen"
                "calibrate_doing" -> "Kalibriert"
                "error" -> "Fehler"
                else -> status.replaceFirstChar { it.uppercase() }
            }
        }

    /** Dauer-Sensor in Sekunden, egal ob HA Sekunden, Minuten oder Stunden liefert. */
    fun seconds(id: String): Long? {
        val n = num(id) ?: return null
        val f = when (unit(id).lowercase()) {
            "min" -> 60.0
            "h" -> 3600.0
            else -> 1.0
        }
        return (n * f).toLong()
    }

    val etaClock: String?
        get() = raw(Ids.ETA)?.let {
            runCatching {
                OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            }.getOrNull()
        }

    /** Gesamtlaufzeit des Druckers in Stunden; HA liefert z. B. "278h:36m". */
    val lifetimeHours: Double?
        get() {
            val r = raw(Ids.LIFETIME_RUNTIME) ?: return null
            r.toDoubleOrNull()?.let { return it }
            val h = Regex("(\\d+)\\s*h").find(r)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            val min = Regex("(\\d+)\\s*m").find(r)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            return h + min / 60.0
        }

    /** Eine Zeile für Benachrichtigung und Widget. */
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            val layer = raw(Ids.LAYER)
            val layers = raw(Ids.LAYERS)
            if (layer != null && layers != null) parts += "Schicht $layer/$layers"
            seconds(Ids.REMAINING)?.let { parts += "noch ${fmtDuration(it)}" }
            etaClock?.let { parts += "fertig $it" }
            return parts.joinToString(" · ")
        }

    companion object {
        /** Jinja-Vorlage, die alle Entitäten als ein JSON-Objekt zurückgibt. */
        val TEMPLATE: String = buildString {
            append("{% set ids = [")
            append(Ids.ALL.joinToString(",") { "'$it'" })
            append("] %}{% set aids = {")
            append(Ids.ATTRS.entries.joinToString(",") { (k, v) -> "'$k': [" + v.joinToString(",") { "'$it'" } + "]" })
            append("} %}{% set ns = namespace(d={}) %}{% for i in ids %}")
            append("{% set e = {'s': states(i), 'u': state_attr(i, 'unit_of_measurement')} %}")
            append("{% if i in aids %}{% set na = namespace(a={}) %}{% for k in aids[i] %}")
            append("{% set na.a = dict(na.a, **{k: state_attr(i, k)}) %}{% endfor %}{% set e = dict(e, a=na.a) %}{% endif %}")
            append("{% set ns.d = dict(ns.d, **{i: e}) %}{% endfor %}{{ ns.d | tojson }}")
        }

        fun parse(json: String): Snapshot {
            val o = JSONObject(json)
            val map = HashMap<String, Ent>()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                val a = e.optJSONObject("a")
                val attrs = HashMap<String, Any?>()
                if (a != null) for (ak in a.keys()) attrs[ak] = if (a.isNull(ak)) null else a.get(ak)
                map[k] = Ent(
                    state = e.optString("s", "unknown"),
                    unit = if (e.isNull("u")) null else e.optString("u"),
                    attrs = attrs,
                )
            }
            return Snapshot(map)
        }
    }
}

fun fmtDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 -> "$h h ${m.toString().padStart(2, '0')} min"
        else -> "$m min"
    }
}

/** Kurzform für schmale Kacheln, z. B. "4:00 h". */
fun fmtDurationShort(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')} h" else "$m min"
}
