package de.uekala.schichtwerk.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Schlanker Zugriff auf die REST-API von Home Assistant. Alle Aufrufe blockieren – nur im IO-Dispatcher nutzen. */
class HaClient(home: String, private val token: String, remote: String? = null) {

    private val urls: List<String> = listOfNotNull(normalize(home), remote?.takeIf { it.isNotBlank() }?.let(::normalize)).distinct()

    /** Adresse, über die zuletzt eine Verbindung geklappt hat. */
    val baseUrl: String get() = working[urls.first()]?.takeIf { it in urls } ?: urls.first()

    /** True, wenn gerade die Adresse für unterwegs benutzt wird. */
    val usingRemote: Boolean get() = urls.size > 1 && baseUrl == urls[1]

    private fun request(path: String): Request.Builder =
        Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")

    /**
     * Führt eine Anfrage aus; ist die aktuelle Adresse nicht erreichbar (z. B. Heimnetz-IP im
     * Mobilfunk), wird einmal mit der anderen Adresse wiederholt und diese gemerkt.
     */
    private fun exec(make: () -> Request): okhttp3.Response {
        try {
            return http.newCall(make()).execute()
        } catch (e: IOException) {
            if (urls.size < 2 || !unreachable(e)) throw e
            working[urls.first()] = urls.first { it != baseUrl }
            return http.newCall(make()).execute()
        }
    }

    private fun unreachable(e: IOException): Boolean =
        e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
            e is java.net.UnknownHostException || e is java.net.NoRouteToHostException ||
            e is javax.net.ssl.SSLException || e is java.io.InterruptedIOException

    private fun fail(code: Int): Nothing = throw IOException(
        when (code) {
            401 -> "Token wurde abgelehnt (401)"
            403 -> "Zugriff verweigert (403) – das Token braucht ein Admin-Konto"
            404 -> "Adresse nicht gefunden (404)"
            else -> "Home Assistant antwortet mit Fehler $code"
        }
    )

    /** Liest alle Drucker-Entitäten mit einer einzigen Anfrage. */
    fun snapshot(): Snapshot {
        val body = JSONObject().put("template", Snapshot.TEMPLATE).toString().toRequestBody(JSON)
        exec { request("/api/template").post(body).build() }.use { r ->
            if (!r.isSuccessful) fail(r.code)
            return Snapshot.parse(r.body.string())
        }
    }

    fun callService(domain: String, service: String, entityId: String, extra: JSONObject? = null) {
        val data = (extra ?: JSONObject()).put("entity_id", entityId)
        val body = data.toString().toRequestBody(JSON)
        exec { request("/api/services/$domain/$service").post(body).build() }.use { r ->
            if (!r.isSuccessful) fail(r.code)
        }
    }

    /** Dienst ohne Ziel-Entität, z. B. ein rest_command mit Variablen. */
    fun callPlainService(domain: String, service: String, data: JSONObject) {
        val body = data.toString().toRequestBody(JSON)
        exec { request("/api/services/$domain/$service").post(body).build() }.use { r ->
            if (r.code == 400 || r.code == 404) {
                throw IOException("Dienst $domain.$service fehlt in Home Assistant")
            }
            if (!r.isSuccessful) fail(r.code)
        }
    }

    /** Roh-JSON eines GET-Aufrufs. */
    private fun getText(path: String): String =
        exec { request(path).build() }.use { r ->
            if (!r.isSuccessful) fail(r.code)
            r.body.string()
        }

    /** Termine eines Kalenders, z. B. calendar.3d_drucke. */
    fun calendarEvents(calendar: String, start: java.time.OffsetDateTime, end: java.time.OffsetDateTime): org.json.JSONArray {
        val q = "start=" + enc(start.toString()) + "&end=" + enc(end.toString())
        return org.json.JSONArray(getText("/api/calendars/$calendar?$q"))
    }

    /** Verlauf mehrerer Entitäten als Liste von (Zeit in ms, Wert). */
    fun history(ids: List<String>, start: java.time.OffsetDateTime): Map<String, List<Pair<Long, Float>>> {
        val path = "/api/history/period/" + enc(start.toString()) +
            "?filter_entity_id=" + enc(ids.joinToString(",")) + "&minimal_response&no_attributes"
        val arr = org.json.JSONArray(getText(path))
        val out = HashMap<String, List<Pair<Long, Float>>>()
        for (i in 0 until arr.length()) {
            val list = arr.getJSONArray(i)
            if (list.length() == 0) continue
            val id = list.getJSONObject(0).optString("entity_id")
            val pts = ArrayList<Pair<Long, Float>>(list.length())
            for (j in 0 until list.length()) {
                val o = list.getJSONObject(j)
                val v = o.optString("state").toFloatOrNull() ?: continue
                val t = o.optString("last_changed").ifBlank { o.optString("last_updated") }
                val ms = runCatching { java.time.OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull() ?: continue
                pts += ms to v
            }
            out[id] = pts
        }
        return out
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    fun press(buttonId: String) = callService("button", "press", buttonId)

    fun cameraImage(entityId: String): Bitmap? = image("/api/camera_proxy/$entityId")
    fun entityImage(entityId: String): Bitmap? = image("/api/image_proxy/$entityId")

    private fun image(path: String): Bitmap? =
        exec { request(path).build() }.use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body.bytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val working = java.util.concurrent.ConcurrentHashMap<String, String>()

        private val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()

        fun normalize(url: String): String {
            var u = url.trim().trimEnd('/')
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            return u
        }
    }
}

data class PrinterAccess(val ip: String, val serial: String, val checkCode: String)

/** Lokale Schnittstelle des Druckers (Port 8898). Nur im Heimnetz oder über VPN erreichbar. */
object PrinterDirect {
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun setSpeed(a: PrinterAccess, percent: Int) {
        val body = JSONObject()
            .put("serialNumber", a.serial)
            .put("checkCode", a.checkCode)
            .put("payload", JSONObject().put("cmd", "printerCtl_cmd").put("args", JSONObject().put("speed", percent)))
            .toString().toRequestBody(JSON)
        val req = Request.Builder().url("http://${a.ip}:8898/control").post(body).build()
        val text = try {
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Drucker antwortet mit Fehler ${r.code}")
                r.body.string()
            }
        } catch (e: java.net.ConnectException) {
            throw IOException("Drucker nicht erreichbar – nur im Heimnetz möglich")
        } catch (e: java.net.SocketTimeoutException) {
            throw IOException("Drucker nicht erreichbar – nur im Heimnetz möglich")
        } catch (e: java.net.UnknownHostException) {
            throw IOException("Drucker nicht erreichbar – nur im Heimnetz möglich")
        }
        val code = runCatching { JSONObject(text).optInt("code", -1) }.getOrDefault(-1)
        if (code != 0) {
            val msg = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
            throw IOException("Drucker lehnt ab: ${msg.ifBlank { "Code $code" }} – Seriennummer und Prüfcode prüfen")
        }
    }
}
