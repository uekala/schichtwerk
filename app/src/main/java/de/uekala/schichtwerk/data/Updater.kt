package de.uekala.schichtwerk.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import de.uekala.schichtwerk.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Release(val tag: String, val versionCode: Int, val url: String)

/**
 * Prüft die GitHub-Releases des (privaten) Repos auf eine neue Version. Installiert wird bewusst
 * nicht von der App selbst, sondern über den Browser und den Paketinstaller von Android – so braucht
 * die App keine Berechtigung, andere Apps zu installieren.
 */
object Updater {
    private const val REPO = "uekala/schichtwerk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Letztes Release; null, wenn das Tag keine Versionsnummer enthält. */
    fun latest(token: String): Release? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        val body = http.newCall(req).execute().use {
            when (it.code) {
                401 -> throw IOException("GitHub-Token wurde abgelehnt")
                403, 404 -> throw IOException("Kein Zugriff auf das Repo – Token braucht Leserecht auf $REPO")
            }
            if (!it.isSuccessful) throw IOException("GitHub antwortet mit Fehler ${it.code}")
            it.body?.string().orEmpty()
        }
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        val code = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val url = o.optString("html_url").ifBlank { "https://github.com/$REPO/releases/latest" }
        return Release(tag, code, url)
    }

    fun isNewer(r: Release): Boolean = r.versionCode > BuildConfig.VERSION_CODE

    /** Öffnet die Release-Seite; dort die APK laden und antippen. */
    fun open(ctx: Context, r: Release) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
