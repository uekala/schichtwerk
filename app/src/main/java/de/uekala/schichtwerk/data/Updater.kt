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

data class Release(val tag: String, val version: String, val url: String)

/**
 * Prüft die öffentlichen GitHub-Releases auf eine neue Version – ohne Token, es werden nur die
 * Release-Angaben abgerufen. Installiert wird nicht von der App selbst, sondern über Obtainium oder
 * den Browser und den Paketinstaller von Android; so braucht die App kein Recht, Apps zu installieren.
 */
object Updater {
    private const val REPO = "uekala/schichtwerk"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Die Testversion (.test) sucht nicht nach Updates. */
    val supported: Boolean get() = !BuildConfig.DEBUG && parseVersion(BuildConfig.VERSION_NAME) != null

    /** Letztes Release; null, wenn das Tag keine Versionsnummer X.Y.Z enthält. */
    fun latest(): Release? {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Schichtwerk-App")
            .build()
        val body = http.newCall(req).execute().use {
            if (it.code == 404) return null
            if (!it.isSuccessful) throw IOException("GitHub antwortet mit Fehler ${it.code}")
            it.body.string()
        }
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        val version = versionFromTag(tag) ?: return null
        val url = o.optString("html_url").ifBlank { "https://github.com/$REPO/releases/latest" }
        return Release(tag, version, url)
    }

    fun isNewer(r: Release): Boolean = isNewerVersion(r.version, BuildConfig.VERSION_NAME)

    /** Öffnet die Release-Seite; dort die APK laden und antippen. */
    fun open(ctx: Context, r: Release) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** "mobile-v0.2.0" → "0.2.0"; ältere Tags "v0.1.42" → "0.1.42". */
    fun versionFromTag(tag: String): String? =
        tag.removePrefix("mobile-").removePrefix("v").takeIf { parseVersion(it) != null }

    /** "0.2.0" → [0, 2, 0]; Zusätze wie "-test" zählen nicht als Version. */
    fun parseVersion(value: String): List<Int>? =
        Regex("""^(\d+)\.(\d+)\.(\d+)$""").find(value.trim())?.groupValues?.drop(1)?.map(String::toInt)

    fun isNewerVersion(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0..2) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }
}
