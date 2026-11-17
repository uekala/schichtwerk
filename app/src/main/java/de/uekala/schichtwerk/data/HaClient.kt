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
class HaClient(baseUrl: String, private val token: String) {

    val baseUrl: String = normalize(baseUrl)

    private fun request(path: String): Request.Builder =
        Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")

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
        http.newCall(request("/api/template").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) fail(r.code)
            return Snapshot.parse(r.body?.string().orEmpty())
        }
    }

    fun callService(domain: String, service: String, entityId: String, extra: JSONObject? = null) {
        val data = (extra ?: JSONObject()).put("entity_id", entityId)
        val body = data.toString().toRequestBody(JSON)
        http.newCall(request("/api/services/$domain/$service").post(body).build()).execute().use { r ->
            if (!r.isSuccessful) fail(r.code)
        }
    }

    /** Dienst ohne Ziel-Entität, z. B. ein rest_command mit Variablen. */
    fun callPlainService(domain: String, service: String, data: JSONObject) {
        val body = data.toString().toRequestBody(JSON)
        http.newCall(request("/api/services/$domain/$service").post(body).build()).execute().use { r ->
            if (r.code == 400 || r.code == 404) {
                throw IOException("Dienst $domain.$service fehlt in Home Assistant")
            }
            if (!r.isSuccessful) fail(r.code)
        }
    }

    fun press(buttonId: String) = callService("button", "press", buttonId)

    fun cameraImage(entityId: String): Bitmap? = image("/api/camera_proxy/$entityId")
    fun entityImage(entityId: String): Bitmap? = image("/api/image_proxy/$entityId")

    private fun image(path: String): Bitmap? =
        http.newCall(request(path).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
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
                r.body?.string().orEmpty()
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
